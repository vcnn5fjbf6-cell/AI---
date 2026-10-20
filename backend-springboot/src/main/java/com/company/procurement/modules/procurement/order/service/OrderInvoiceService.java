package com.company.procurement.modules.procurement.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.company.procurement.common.exception.BizException;
import com.company.procurement.common.security.AuthUser;
import com.company.procurement.common.security.DataScopeHelper;
import com.company.procurement.modules.integration.feishu.service.FeishuApprovalCardService;
import com.company.procurement.modules.procurement.order.entity.OrderInvoiceAttachment;
import com.company.procurement.modules.procurement.order.entity.OrderInvoiceSubmission;
import com.company.procurement.modules.procurement.order.mapper.OrderInvoiceAttachmentMapper;
import com.company.procurement.modules.procurement.order.mapper.OrderInvoiceSubmissionMapper;
import com.company.procurement.modules.procurement.order.support.SupplierSoldOrderAccess;
import com.company.procurement.modules.procurement.order.vo.OrderInvoiceAttachmentVO;
import com.company.procurement.modules.procurement.order.vo.OrderInvoiceSubmissionVO;
import com.company.procurement.modules.procurement.order.vo.OrderInvoiceSummaryVO;
import com.company.procurement.modules.procurement.order.vo.ProcurementOrderVO;
import com.company.procurement.modules.procurement.request.entity.PurchaseRequest;
import com.company.procurement.modules.procurement.request.mapper.PurchaseRequestMapper;
import com.company.procurement.modules.procurement.route.service.ProcurementApprovalRouteResolver;
import com.company.procurement.modules.procurement.workflow.ProcurementWorkflow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderInvoiceService {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_ACCEPTED = "ACCEPTED";
    public static final String STATUS_RETURNED = "RETURNED";
    public static final String STATUS_SUPERSEDED = "SUPERSEDED";
    public static final String DOCUMENT_TYPE_INVOICE = "INVOICE";
    public static final String DOCUMENT_TYPE_DELIVERY_NOTE = "DELIVERY_NOTE";
    public static final String SUBMITTED_ROLE_SUPPLIER = "SUPPLIER";
    public static final String SUBMITTED_ROLE_ASSISTANT = "ASSISTANT";
    public static final String SUBMITTED_ROLE_ADMIN = "ADMIN";

    private static final int MAX_FILES = 5;
    private static final long MAX_FILE_SIZE = 20L * 1024L * 1024L;
    private static final int MAX_VERSIONS_PER_ORDER = 50;
    private static final long MAX_STORED_BYTES_PER_ORDER = 500L * 1024L * 1024L;
    private static final BigDecimal MAX_INVOICE_AMOUNT = new BigDecimal("9999999999999999.99");
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "ofd", "xml", "jpg", "jpeg", "png");
    private static final DateTimeFormatter STORAGE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final OrderInvoiceSubmissionMapper submissionMapper;
    private final OrderInvoiceAttachmentMapper attachmentMapper;
    private final PurchaseRequestMapper purchaseRequestMapper;
    private final ProcurementApprovalRouteResolver approvalRouteResolver;
    private final FeishuApprovalCardService feishuApprovalCardService;

    @Value("${procurement.order.invoice.storage-dir:data/order-invoices}")
    private String storageDir;

    public boolean decorate(ProcurementOrderVO order, AuthUser authUser) {
        return decorate(order, authUser, null);
    }

    public boolean decorate(ProcurementOrderVO order, AuthUser authUser, Boolean resolvedReviewer) {
        if (order == null) {
            return false;
        }
        boolean originalViewer = canViewOriginalOrder(authUser, order);
        boolean sourceAssistant = canOperateAsSourceAssistant(authUser, order);
        boolean reviewer = resolvedReviewer != null
            ? resolvedReviewer
            : canReviewInvoice(authUser, order, sourceAssistant);
        boolean supplierOperator = SupplierSoldOrderAccess.canOperate(authUser, order);
        boolean invoiceViewer = supplierOperator || reviewer;
        boolean internalOriginalViewer = originalViewer && !DataScopeHelper.isSupplierUser(authUser);
        boolean internalOrderOperator = internalOriginalViewer || sourceAssistant;
        OrderInvoiceSubmission latest = invoiceViewer
            ? latestSubmission(order.getQuoteItemId(), DOCUMENT_TYPE_INVOICE, false)
            : null;
        OrderInvoiceSubmission latestDeliveryNote = invoiceViewer
            ? latestSubmission(order.getQuoteItemId(), DOCUMENT_TYPE_DELIVERY_NOTE, false)
            : null;
        boolean documentUploader = canUploadDocumentRole(authUser, order);

        order.setLatestInvoice(latest == null ? null : toSummaryVO(latest));
        order.setLatestDeliveryNote(latestDeliveryNote == null ? null : toSummaryVO(latestDeliveryNote));
        order.setCanViewInvoice(invoiceViewer);
        order.setCanReviewInvoice(reviewer);
        order.setCanViewOrderDocuments(invoiceViewer);
        order.setCanReviewOrderDocuments(reviewer);
        order.setCanUploadInvoice(canUploadDocument(order, documentUploader, latest));
        order.setCanUploadDeliveryNote(canUploadDocument(order, documentUploader, latestDeliveryNote));
        order.setCanExportOrder(originalViewer || sourceAssistant);
        order.setCanConfirmReceipt(internalOrderOperator
            && "CONFIRMED".equals(order.getSupplierConfirmStatus())
            && !"RECEIVED".equals(order.getReceiptStatus()));
        order.setCanApplyAfterSale(internalOrderOperator
            && "CONFIRMED".equals(order.getSupplierConfirmStatus())
            && "RECEIVED".equals(order.getReceiptStatus())
            && Boolean.TRUE.equals(order.getAfterSaleAllowed()));
        order.setCanConfirmAfterSaleProposal(internalOrderOperator
            && "PROPOSAL_PENDING".equals(order.getLatestAfterSaleStatus()));
        return originalViewer || reviewer;
    }

    public boolean canViewInvoice(AuthUser authUser, ProcurementOrderVO order) {
        return SupplierSoldOrderAccess.canOperate(authUser, order) || canReviewInvoice(authUser, order);
    }

    public List<OrderInvoiceSubmissionVO> list(ProcurementOrderVO order, AuthUser authUser) {
        return list(order, authUser, DOCUMENT_TYPE_INVOICE);
    }

    public List<OrderInvoiceSubmissionVO> list(ProcurementOrderVO order,
                                               AuthUser authUser,
                                               String documentType) {
        ensureCanViewInvoice(authUser, order);
        String normalizedDocumentType = normalizeDocumentType(documentType);
        return submissionMapper.selectList(new LambdaQueryWrapper<OrderInvoiceSubmission>()
                .eq(OrderInvoiceSubmission::getQuoteItemId, order.getQuoteItemId())
                .eq(OrderInvoiceSubmission::getDocumentType, normalizedDocumentType)
                .orderByDesc(OrderInvoiceSubmission::getVersionNo)
                .orderByDesc(OrderInvoiceSubmission::getId))
            .stream()
            .map(submission -> toSubmissionVO(submission, true))
            .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public OrderInvoiceSubmissionVO upload(ProcurementOrderVO order,
                                           AuthUser authUser,
                                           List<MultipartFile> files,
                                           String invoiceNo,
                                           LocalDate invoiceDate,
                                           BigDecimal totalAmount,
                                           String invoiceType,
                                           String remark) {
        return upload(
            order,
            authUser,
            DOCUMENT_TYPE_INVOICE,
            files,
            invoiceNo,
            invoiceDate,
            totalAmount,
            invoiceType,
            remark
        );
    }

    @Transactional(rollbackFor = Exception.class)
    public OrderInvoiceSubmissionVO upload(ProcurementOrderVO order,
                                           AuthUser authUser,
                                           String documentType,
                                           List<MultipartFile> files,
                                           String invoiceNo,
                                           LocalDate invoiceDate,
                                           BigDecimal totalAmount,
                                           String invoiceType,
                                           String remark) {
        String normalizedDocumentType = normalizeDocumentType(documentType);
        String documentLabel = documentLabel(normalizedDocumentType);
        ensureCanUpload(authUser, order);
        List<MultipartFile> validFiles = validateFiles(files, documentLabel);
        if (DOCUMENT_TYPE_INVOICE.equals(normalizedDocumentType) && totalAmount != null) {
            try {
                totalAmount.setScale(2, java.math.RoundingMode.UNNECESSARY);
            } catch (ArithmeticException ex) {
                throw new BizException("ORDER_INVOICE_AMOUNT_INVALID", "发票金额最多保留2位小数");
            }
            if (totalAmount.compareTo(BigDecimal.ZERO) <= 0 || totalAmount.compareTo(MAX_INVOICE_AMOUNT) > 0) {
                throw new BizException("ORDER_INVOICE_AMOUNT_INVALID", "发票金额必须大于0且不超过9999999999999999.99");
            }
        }
        String normalizedInvoiceNo = validateOptionalText(
            invoiceNo,
            128,
            "ORDER_INVOICE_NO_TOO_LONG",
            documentLabel + "编号不能超过128个字符"
        );
        String normalizedInvoiceType = DOCUMENT_TYPE_INVOICE.equals(normalizedDocumentType)
            ? normalizeUpper(invoiceType)
            : null;
        if (normalizedInvoiceType != null && normalizedInvoiceType.length() > 32) {
            throw new BizException("ORDER_INVOICE_TYPE_TOO_LONG", "发票类型不能超过32个字符");
        }
        String normalizedRemark = validateOptionalText(
            remark,
            1000,
            "ORDER_INVOICE_REMARK_TOO_LONG",
            documentLabel + "备注不能超过1000个字符"
        );

        if (DOCUMENT_TYPE_DELIVERY_NOTE.equals(normalizedDocumentType)) {
            totalAmount = null;
        }

        OrderInvoiceSubmission latest = latestSubmission(order.getQuoteItemId(), normalizedDocumentType, true);
        if (latest != null && STATUS_ACCEPTED.equals(latest.getStatus())) {
            throw new BizException("ORDER_INVOICE_ACCEPTED", documentLabel + "已确认，不能重新上传");
        }
        if (latest != null && !STATUS_PENDING.equals(latest.getStatus()) && !STATUS_RETURNED.equals(latest.getStatus())) {
            throw new BizException("ORDER_INVOICE_STATUS_INVALID", "当前" + documentLabel + "状态不能重新上传");
        }
        ensureOrderStorageQuota(order.getQuoteItemId(), normalizedDocumentType, validFiles);
        if (latest != null && STATUS_PENDING.equals(latest.getStatus())) {
            latest.setStatus(STATUS_SUPERSEDED);
            if (submissionMapper.updateById(latest) != 1) {
                throw new BizException("ORDER_INVOICE_CONCURRENT_UPDATE", documentLabel + "已发生变化，请刷新后重试");
            }
        }

        LocalDateTime now = LocalDateTime.now();
        OrderInvoiceSubmission submission = new OrderInvoiceSubmission();
        submission.setQuoteItemId(order.getQuoteItemId());
        submission.setPurchaseRequestId(businessRequestId(order));
        submission.setSupplierId(order.getSupplierId());
        submission.setDocumentType(normalizedDocumentType);
        submission.setVersionNo(latest == null ? 1 : latest.getVersionNo() + 1);
        submission.setStatus(STATUS_PENDING);
        submission.setInvoiceNo(normalizedInvoiceNo);
        submission.setInvoiceDate(invoiceDate);
        submission.setTotalAmount(totalAmount);
        submission.setInvoiceType(normalizedInvoiceType);
        submission.setRemark(normalizedRemark);
        submission.setAttachmentCount(validFiles.size());
        submission.setSubmittedBy(authUser.getUserId());
        submission.setSubmittedByName(limit(displayName(authUser), 128));
        submission.setSubmittedByRole(submittedByRole(authUser));
        submission.setSubmittedAt(now);
        submission.setSupersedesSubmissionId(latest == null ? null : latest.getId());
        try {
            if (submissionMapper.insert(submission) != 1 || submission.getId() == null) {
                throw new BizException("ORDER_INVOICE_SAVE_FAILED", "保存" + documentLabel + "提交记录失败");
            }
        } catch (DuplicateKeyException ex) {
            throw new BizException("ORDER_INVOICE_CONCURRENT_UPDATE", documentLabel + "已被其他操作更新，请刷新后重试");
        }

        List<Path> storedFiles = new ArrayList<>();
        try {
            for (MultipartFile file : validFiles) {
                saveAttachment(submission.getId(), file, storedFiles);
            }
            registerRollbackCleanup(storedFiles);
            notifyAssistantsAfterSupplierUpload(order, authUser, submission);
            return toSubmissionVO(submission, true);
        } catch (RuntimeException ex) {
            deleteQuietly(storedFiles);
            throw ex;
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public OrderInvoiceSubmissionVO accept(ProcurementOrderVO order, Long submissionId, AuthUser authUser, String remark) {
        ensureCanReviewInvoice(authUser, order);
        OrderInvoiceSubmission submission = requireLatestSubmission(order, submissionId);
        if (STATUS_ACCEPTED.equals(submission.getStatus())) {
            return toSubmissionVO(submission, true);
        }
        if (!STATUS_PENDING.equals(submission.getStatus())) {
            throw new BizException("ORDER_INVOICE_STATUS_INVALID", "只有待确认" + documentLabel(submission.getDocumentType()) + "可以确认");
        }
        applyReview(submission, authUser, STATUS_ACCEPTED, remark);
        return toSubmissionVO(submission, true);
    }

    @Transactional(rollbackFor = Exception.class)
    public OrderInvoiceSubmissionVO returnForResubmission(ProcurementOrderVO order,
                                                          Long submissionId,
                                                          AuthUser authUser,
                                                          String remark) {
        ensureCanReviewInvoice(authUser, order);
        String normalizedRemark = normalizeBlank(remark);
        if (!StringUtils.hasText(normalizedRemark)) {
            throw new BizException("ORDER_INVOICE_RETURN_REASON_REQUIRED", "退回重传必须填写原因");
        }
        OrderInvoiceSubmission submission = requireLatestSubmission(order, submissionId);
        if (!STATUS_PENDING.equals(submission.getStatus())) {
            throw new BizException("ORDER_INVOICE_STATUS_INVALID", "只有待确认" + documentLabel(submission.getDocumentType()) + "可以退回重传");
        }
        applyReview(submission, authUser, STATUS_RETURNED, normalizedRemark);
        return toSubmissionVO(submission, true);
    }

    public AttachmentResource loadAttachment(ProcurementOrderVO order,
                                             Long attachmentId,
                                             AuthUser authUser) {
        ensureCanViewInvoice(authUser, order);
        OrderInvoiceAttachment attachment = attachmentMapper.selectById(attachmentId);
        if (attachment == null) {
            throw new BizException("ORDER_INVOICE_ATTACHMENT_NOT_FOUND", "订单资料附件不存在");
        }
        OrderInvoiceSubmission submission = submissionMapper.selectById(attachment.getSubmissionId());
        if (submission == null || !Objects.equals(submission.getQuoteItemId(), order.getQuoteItemId())) {
            throw new BizException("ORDER_INVOICE_ATTACHMENT_NOT_FOUND", "订单资料附件不存在");
        }
        Path root = storageRoot();
        Path file = root.resolve(attachment.getStoragePath()).normalize();
        if (!file.startsWith(root) || !Files.isRegularFile(file)) {
            throw new BizException("ORDER_INVOICE_ATTACHMENT_NOT_FOUND", "订单资料附件文件不存在");
        }
        try {
            long actualSize = Files.size(file);
            verifyAttachmentIntegrity(file, attachment, actualSize);
            return new AttachmentResource(
                new FileSystemResource(file),
                attachment.getFileName(),
                attachment.getContentType(),
                actualSize
            );
        } catch (IOException ex) {
            throw new BizException("ORDER_INVOICE_ATTACHMENT_READ_FAILED", "读取订单资料附件失败");
        }
    }

    public boolean canReviewInvoice(AuthUser authUser, ProcurementOrderVO order) {
        return canReviewInvoice(authUser, order, false);
    }

    private boolean canReviewInvoice(AuthUser authUser,
                                     ProcurementOrderVO order,
                                     boolean sourceAssistantResolved) {
        if (authUser == null || authUser.getUserId() == null || order == null || DataScopeHelper.isSupplierUser(authUser)) {
            return false;
        }
        if (DataScopeHelper.isSuperAdmin(authUser) || Objects.equals(authUser.getUserId(), order.getPurchaserUserId())) {
            return true;
        }
        if (DataScopeHelper.isSystemAdmin(authUser)) {
            return false;
        }
        if (sourceAssistantResolved) {
            return true;
        }
        PurchaseRequest request = businessRequest(order);
        if (request == null) {
            return false;
        }
        if (isSourceAssistant(authUser, request)) {
            return true;
        }
        try {
            return approvalRouteResolver.resolveAssignmentForNode(
                    request,
                    ProcurementWorkflow.NODE_FLOOR_LEADER_DEMAND_REVIEW)
                .candidateUserIds()
                .contains(authUser.getUserId());
        } catch (BizException ex) {
            if ("APPROVER_NOT_CONFIGURED".equals(ex.getCode()) || "ROUTE_APPROVER_NOT_CONFIGURED".equals(ex.getCode())) {
                return false;
            }
            throw ex;
        }
    }

    private void ensureCanViewInvoice(AuthUser authUser, ProcurementOrderVO order) {
        if (!canViewInvoice(authUser, order)) {
            throw new BizException("FORBIDDEN", "当前账号不能查看该订单资料");
        }
    }

    private void ensureCanReviewInvoice(AuthUser authUser, ProcurementOrderVO order) {
        if (!canReviewInvoice(authUser, order)) {
            throw new BizException("FORBIDDEN", "当前账号不能确认或退回该订单资料");
        }
    }

    public boolean canViewOriginalOrder(AuthUser authUser, ProcurementOrderVO order) {
        if (authUser == null || order == null) {
            return false;
        }
        if (DataScopeHelper.isSystemAdmin(authUser)) {
            return true;
        }
        if (DataScopeHelper.isSupplierUser(authUser)) {
            return SupplierSoldOrderAccess.canOperate(authUser, order);
        }
        return Objects.equals(authUser.getUserId(), order.getApplicantUserId())
            || Objects.equals(authUser.getUserId(), order.getPurchaserUserId());
    }

    public boolean canOperateAsSourceAssistant(AuthUser authUser, ProcurementOrderVO order) {
        if (authUser == null
            || authUser.getUserId() == null
            || order == null
            || DataScopeHelper.isSupplierUser(authUser)
            || DataScopeHelper.isSystemAdmin(authUser)) {
            return false;
        }
        PurchaseRequest request = businessRequest(order);
        return request != null && isSourceAssistant(authUser, request);
    }

    private void ensureCanUpload(AuthUser authUser, ProcurementOrderVO order) {
        if (!canUploadDocumentRole(authUser, order)) {
            throw new BizException("FORBIDDEN", "只有最高管理员、本订单供应商或跨供应商最高监督账号可以上传订单资料");
        }
        if (!"CONFIRMED".equals(order.getSupplierConfirmStatus())) {
            throw new BizException("ORDER_NOT_CONFIRMED", "确认中标后才能上传订单资料");
        }
    }

    private boolean canUploadDocumentRole(AuthUser authUser, ProcurementOrderVO order) {
        return SupplierSoldOrderAccess.canUploadOrderDocuments(authUser, order);
    }

    private boolean canUploadDocument(ProcurementOrderVO order,
                                      boolean documentUploader,
                                      OrderInvoiceSubmission latest) {
        return documentUploader
            && "CONFIRMED".equals(order.getSupplierConfirmStatus())
            && (latest == null || STATUS_PENDING.equals(latest.getStatus()) || STATUS_RETURNED.equals(latest.getStatus()));
    }

    private boolean isSourceAssistant(AuthUser authUser, PurchaseRequest request) {
        return approvalRouteResolver.routeCandidateUserIds(request, ProcurementWorkflow.ROLE_DEPARTMENT_ASSISTANT)
            .contains(authUser.getUserId());
    }

    private String submittedByRole(AuthUser authUser) {
        if (DataScopeHelper.isSupplierUser(authUser)) {
            return SUBMITTED_ROLE_SUPPLIER;
        }
        if (DataScopeHelper.isSuperAdmin(authUser)) {
            return SUBMITTED_ROLE_ADMIN;
        }
        return SUBMITTED_ROLE_ASSISTANT;
    }

    private String normalizeDocumentType(String documentType) {
        String normalized = normalizeUpper(documentType);
        if (normalized == null) {
            return DOCUMENT_TYPE_INVOICE;
        }
        if (!DOCUMENT_TYPE_INVOICE.equals(normalized) && !DOCUMENT_TYPE_DELIVERY_NOTE.equals(normalized)) {
            throw new BizException("ORDER_DOCUMENT_TYPE_INVALID", "订单资料类型不正确");
        }
        return normalized;
    }

    private String documentLabel(String documentType) {
        return DOCUMENT_TYPE_DELIVERY_NOTE.equals(normalizeDocumentType(documentType)) ? "送货单" : "发票";
    }

    private PurchaseRequest businessRequest(ProcurementOrderVO order) {
        Long requestId = businessRequestId(order);
        return requestId == null ? null : purchaseRequestMapper.selectById(requestId);
    }

    private Long businessRequestId(ProcurementOrderVO order) {
        return order.getSourceRequestId() == null ? order.getPurchaseRequestId() : order.getSourceRequestId();
    }

    private OrderInvoiceSubmission latestSubmission(Long quoteItemId, String documentType, boolean forUpdate) {
        LambdaQueryWrapper<OrderInvoiceSubmission> wrapper = new LambdaQueryWrapper<OrderInvoiceSubmission>()
            .eq(OrderInvoiceSubmission::getQuoteItemId, quoteItemId)
            .eq(OrderInvoiceSubmission::getDocumentType, normalizeDocumentType(documentType))
            .orderByDesc(OrderInvoiceSubmission::getVersionNo)
            .orderByDesc(OrderInvoiceSubmission::getId)
            .last(forUpdate ? "LIMIT 1 FOR UPDATE" : "LIMIT 1");
        return submissionMapper.selectOne(wrapper);
    }

    private OrderInvoiceSubmission requireLatestSubmission(ProcurementOrderVO order, Long submissionId) {
        OrderInvoiceSubmission submission = submissionMapper.selectById(submissionId);
        if (submission == null || !Objects.equals(submission.getQuoteItemId(), order.getQuoteItemId())) {
            throw new BizException("ORDER_INVOICE_NOT_FOUND", "订单资料提交记录不存在");
        }
        OrderInvoiceSubmission latest = latestSubmission(
            order.getQuoteItemId(),
            normalizeDocumentType(submission.getDocumentType()),
            false
        );
        if (latest == null || !Objects.equals(latest.getId(), submission.getId())) {
            throw new BizException("ORDER_INVOICE_NOT_LATEST", "只能处理该类订单资料的最新版本");
        }
        return submission;
    }

    private void applyReview(OrderInvoiceSubmission submission, AuthUser authUser, String status, String remark) {
        String previousStatus = submission.getStatus();
        Integer previousVersion = submission.getVersion();
        String reviewerName = limit(displayName(authUser), 128);
        LocalDateTime reviewedAt = LocalDateTime.now();
        String reviewRemark = validateOptionalText(
            remark,
            1000,
            "ORDER_INVOICE_REVIEW_REMARK_TOO_LONG",
            "审核意见不能超过1000个字符"
        );
        UpdateWrapper<OrderInvoiceSubmission> update = new UpdateWrapper<OrderInvoiceSubmission>()
            .eq("id", submission.getId())
            .eq("is_deleted", 0)
            .eq("status", previousStatus)
            .eq(previousVersion != null, "version", previousVersion)
            .set("status", status)
            .set("reviewed_by", authUser.getUserId())
            .set("reviewed_by_name", reviewerName)
            .set("reviewed_at", reviewedAt)
            .set("review_remark", reviewRemark)
            .setSql("version = version + 1");
        if (submissionMapper.update(null, update) != 1) {
            throw new BizException("ORDER_INVOICE_CONCURRENT_UPDATE", "订单资料已发生变化，请刷新后重试");
        }
        submission.setStatus(status);
        submission.setReviewedBy(authUser.getUserId());
        submission.setReviewedByName(reviewerName);
        submission.setReviewedAt(reviewedAt);
        submission.setReviewRemark(reviewRemark);
        if (previousVersion != null) {
            submission.setVersion(previousVersion + 1);
        }
    }

    private List<MultipartFile> validateFiles(List<MultipartFile> files, String documentLabel) {
        if (files == null || files.isEmpty()) {
            throw new BizException("ORDER_INVOICE_FILE_REQUIRED", "请至少上传1个" + documentLabel + "文件");
        }
        if (files.size() > MAX_FILES) {
            throw new BizException("ORDER_INVOICE_FILE_LIMIT", "每次最多上传5个" + documentLabel + "文件");
        }
        if (files.stream().anyMatch(file -> file == null || file.isEmpty())) {
            throw new BizException("ORDER_INVOICE_FILE_EMPTY", documentLabel + "文件不能为空");
        }
        List<MultipartFile> validFiles = List.copyOf(files);
        for (MultipartFile file : validFiles) {
            if (file.getSize() > MAX_FILE_SIZE) {
                throw new BizException("ORDER_INVOICE_FILE_TOO_LARGE", "单个" + documentLabel + "文件不能超过20MB");
            }
            String fileName = originalFileName(file);
            if (fileName.length() > 255) {
                throw new BizException("ORDER_INVOICE_FILE_NAME_TOO_LONG", documentLabel + "文件名不能超过255个字符");
            }
            String extension = extension(fileName);
            if (!ALLOWED_EXTENSIONS.contains(extension)) {
                throw new BizException("ORDER_INVOICE_FILE_TYPE_INVALID", "仅支持PDF、OFD、XML、JPG、JPEG、PNG格式");
            }
            validateFileSignature(file, extension);
        }
        return validFiles;
    }

    private void validateFileSignature(MultipartFile file, String extension) {
        byte[] head = new byte[16];
        int length;
        try (InputStream input = file.getInputStream()) {
            length = input.read(head);
        } catch (IOException ex) {
            throw new BizException("ORDER_INVOICE_FILE_READ_FAILED", "读取订单资料文件失败");
        }
        boolean valid = switch (extension) {
            case "pdf" -> length >= 4 && head[0] == '%' && head[1] == 'P' && head[2] == 'D' && head[3] == 'F';
            case "ofd" -> length >= 4 && head[0] == 'P' && head[1] == 'K';
            case "jpg", "jpeg" -> length >= 3 && (head[0] & 0xff) == 0xff && (head[1] & 0xff) == 0xd8 && (head[2] & 0xff) == 0xff;
            case "png" -> length >= 8 && (head[0] & 0xff) == 0x89 && head[1] == 'P' && head[2] == 'N' && head[3] == 'G';
            case "xml" -> looksLikeXml(head, length);
            default -> false;
        };
        if (!valid) {
            throw new BizException("ORDER_INVOICE_FILE_CONTENT_INVALID", "文件内容与扩展名不匹配");
        }
    }

    private boolean looksLikeXml(byte[] head, int length) {
        if (length <= 0) {
            return false;
        }
        int index = length >= 3 && (head[0] & 0xff) == 0xef && (head[1] & 0xff) == 0xbb && (head[2] & 0xff) == 0xbf ? 3 : 0;
        while (index < length && Character.isWhitespace((char) head[index])) {
            index++;
        }
        return index < length && head[index] == '<';
    }

    private void saveAttachment(Long submissionId, MultipartFile file, List<Path> storedFiles) {
        String originalName = originalFileName(file);
        String extension = extension(originalName);
        String relativePath = LocalDate.now().format(STORAGE_DATE) + "/" + UUID.randomUUID() + "." + extension;
        Path root = storageRoot();
        Path target = root.resolve(relativePath).normalize();
        if (!target.startsWith(root)) {
            throw new BizException("ORDER_INVOICE_FILE_PATH_INVALID", "订单资料文件路径不合法");
        }
        String sha256;
        long storedSize;
        Path temp = null;
        try {
            Files.createDirectories(target.getParent());
            temp = Files.createTempFile(target.getParent(), ".invoice-", ".tmp");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = file.getInputStream(); DigestInputStream digestInput = new DigestInputStream(input, digest)) {
                Files.copy(digestInput, temp, StandardCopyOption.REPLACE_EXISTING);
            }
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temp, target);
            }
            storedFiles.add(target);
            storedSize = Files.size(target);
            if (storedSize != file.getSize()) {
                throw new IOException("Stored document size does not match upload size");
            }
            sha256 = HexFormat.of().formatHex(digest.digest());
        } catch (Exception ex) {
            throw new BizException("ORDER_INVOICE_FILE_SAVE_FAILED", "保存订单资料文件失败");
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ex) {
                log.warn("Failed to clean up temporary order document file {}", temp, ex);
                }
            }
        }

        OrderInvoiceAttachment attachment = new OrderInvoiceAttachment();
        attachment.setSubmissionId(submissionId);
        attachment.setFileName(limit(originalName, 255));
        attachment.setFileExt(extension);
        attachment.setContentType(contentType(extension));
        attachment.setFileSize(storedSize);
        attachment.setFileSha256(sha256);
        attachment.setStoragePath(relativePath);
        if (attachmentMapper.insert(attachment) != 1) {
            throw new BizException("ORDER_INVOICE_FILE_SAVE_FAILED", "保存订单资料附件记录失败");
        }
    }

    private void ensureOrderStorageQuota(Long quoteItemId,
                                         String documentType,
                                         List<MultipartFile> files) {
        List<OrderInvoiceSubmission> submissions = submissionMapper.selectList(new LambdaQueryWrapper<OrderInvoiceSubmission>()
            .eq(OrderInvoiceSubmission::getQuoteItemId, quoteItemId));
        long typeVersions = submissions.stream()
            .filter(item -> normalizeDocumentType(item.getDocumentType()).equals(normalizeDocumentType(documentType)))
            .count();
        if (typeVersions >= MAX_VERSIONS_PER_ORDER) {
            throw new BizException(
                "ORDER_INVOICE_VERSION_LIMIT",
                "该订单" + documentLabel(documentType) + "重传版本已达到50个，请联系管理员处理"
            );
        }
        long storedBytes = 0L;
        List<Long> submissionIds = submissions.stream().map(OrderInvoiceSubmission::getId).toList();
        if (!submissionIds.isEmpty()) {
            storedBytes = attachmentMapper.selectList(new LambdaQueryWrapper<OrderInvoiceAttachment>()
                    .in(OrderInvoiceAttachment::getSubmissionId, submissionIds))
                .stream()
                .map(OrderInvoiceAttachment::getFileSize)
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue)
                .sum();
        }
        long incomingBytes = files.stream().mapToLong(MultipartFile::getSize).sum();
        if (storedBytes > MAX_STORED_BYTES_PER_ORDER - incomingBytes) {
            throw new BizException("ORDER_INVOICE_STORAGE_LIMIT", "该订单累计资料附件不能超过500MB");
        }
    }

    private void registerRollbackCleanup(List<Path> storedFiles) {
        if (storedFiles.isEmpty() || !TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        List<Path> rollbackFiles = List.copyOf(storedFiles);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == TransactionSynchronization.STATUS_ROLLED_BACK) {
                    deleteQuietly(rollbackFiles);
                }
            }
        });
    }

    private void notifyAssistantsAfterSupplierUpload(ProcurementOrderVO order,
                                                     AuthUser authUser,
                                                     OrderInvoiceSubmission submission) {
        if (!SupplierSoldOrderAccess.canOperate(authUser, order)) {
            return;
        }
        List<Long> assistantUserIds;
        try {
            PurchaseRequest request = businessRequest(order);
            if (request == null) {
                return;
            }
            assistantUserIds = approvalRouteResolver
                .routeCandidateUserIds(request, ProcurementWorkflow.ROLE_DEPARTMENT_ASSISTANT)
                .stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        } catch (RuntimeException ex) {
            log.warn("Resolve source assistants for order document notification failed, quoteItemId={}", order.getQuoteItemId(), ex);
            return;
        }
        if (assistantUserIds.isEmpty()) {
            log.info("No source assistant configured for order document notification, quoteItemId={}", order.getQuoteItemId());
            return;
        }
        String documentLabel = documentLabel(submission.getDocumentType());
        String title = "订单" + documentLabel + "已上传";
        String content = "**订单号**：" + notificationText(order.getOrderNo(), "-")
            + "\n**申请单号**：" + notificationText(order.getReqNo(), "-")
            + "\n**供应商**：" + notificationText(order.getSupplierName(), "-")
            + "\n**上传资料**：" + documentLabel + "（第 " + submission.getVersionNo() + " 版）"
            + "\n**上传人**：" + notificationText(submission.getSubmittedByName(), "供应商")
            + "\n**文件数量**：" + submission.getAttachmentCount()
            + "\n请到“采购管理 / 我的订单”查看，可继续补充另一类资料或完成确认。";
        Runnable notification = () -> assistantUserIds.forEach(userId -> {
            try {
                feishuApprovalCardService.sendBusinessNotification(userId, title, content);
            } catch (RuntimeException ex) {
                log.warn(
                    "Send order document notification failed, quoteItemId={}, documentType={}, assistantUserId={}",
                    order.getQuoteItemId(),
                    submission.getDocumentType(),
                    userId,
                    ex
                );
            }
        });
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    notification.run();
                }
            });
        } else {
            notification.run();
        }
    }

    private OrderInvoiceSubmissionVO toSubmissionVO(OrderInvoiceSubmission submission, boolean includeAttachments) {
        OrderInvoiceSubmissionVO vo = new OrderInvoiceSubmissionVO();
        copySummary(submission, vo);
        vo.setQuoteItemId(submission.getQuoteItemId());
        vo.setSupplierId(submission.getSupplierId());
        vo.setRemark(submission.getRemark());
        vo.setAttachments(includeAttachments
            ? attachmentMapper.selectList(new LambdaQueryWrapper<OrderInvoiceAttachment>()
                .eq(OrderInvoiceAttachment::getSubmissionId, submission.getId())
                .orderByAsc(OrderInvoiceAttachment::getId))
                .stream()
                .map(attachment -> toAttachmentVO(attachment, submission.getQuoteItemId()))
                .toList()
            : List.of());
        return vo;
    }

    private OrderInvoiceSummaryVO toSummaryVO(OrderInvoiceSubmission submission) {
        OrderInvoiceSummaryVO vo = new OrderInvoiceSummaryVO();
        copySummary(submission, vo);
        return vo;
    }

    private void copySummary(OrderInvoiceSubmission submission, OrderInvoiceSummaryVO vo) {
        vo.setSubmissionId(submission.getId());
        vo.setDocumentType(normalizeDocumentType(submission.getDocumentType()));
        vo.setStatus(submission.getStatus());
        vo.setVersionNo(submission.getVersionNo());
        vo.setInvoiceNo(submission.getInvoiceNo());
        vo.setInvoiceDate(submission.getInvoiceDate());
        vo.setTotalAmount(submission.getTotalAmount());
        vo.setInvoiceType(submission.getInvoiceType());
        vo.setAttachmentCount(submission.getAttachmentCount());
        vo.setSubmittedAt(submission.getSubmittedAt());
        vo.setSubmittedByName(submission.getSubmittedByName());
        vo.setSubmittedByRole(submission.getSubmittedByRole());
        vo.setReviewedAt(submission.getReviewedAt());
        vo.setReviewedByName(submission.getReviewedByName());
        vo.setReviewRemark(submission.getReviewRemark());
    }

    private OrderInvoiceAttachmentVO toAttachmentVO(OrderInvoiceAttachment attachment, Long quoteItemId) {
        OrderInvoiceAttachmentVO vo = new OrderInvoiceAttachmentVO();
        vo.setId(attachment.getId());
        vo.setFileName(attachment.getFileName());
        vo.setContentType(attachment.getContentType());
        vo.setFileSize(attachment.getFileSize());
        vo.setSha256(attachment.getFileSha256());
        vo.setDownloadUrl("/api/procurement/orders/my/" + quoteItemId
            + "/invoices/attachments/" + attachment.getId() + "/download");
        return vo;
    }

    private void verifyAttachmentIntegrity(Path file, OrderInvoiceAttachment attachment, long actualSize) {
        if (attachment.getFileSize() != null && attachment.getFileSize() != actualSize) {
            throw new BizException("ORDER_INVOICE_ATTACHMENT_INTEGRITY_FAILED", "订单资料附件大小校验失败，请联系管理员恢复文件");
        }
        if (!StringUtils.hasText(attachment.getFileSha256())) {
            return;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(file); DigestInputStream digestInput = new DigestInputStream(input, digest)) {
                digestInput.transferTo(java.io.OutputStream.nullOutputStream());
            }
            String actualSha256 = HexFormat.of().formatHex(digest.digest());
            if (!actualSha256.equalsIgnoreCase(attachment.getFileSha256())) {
                throw new BizException("ORDER_INVOICE_ATTACHMENT_INTEGRITY_FAILED", "订单资料附件摘要校验失败，请联系管理员恢复文件");
            }
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException("ORDER_INVOICE_ATTACHMENT_READ_FAILED", "读取订单资料附件失败");
        }
    }

    private Path storageRoot() {
        try {
            return Path.of(StringUtils.hasText(storageDir) ? storageDir : "data/order-invoices").toAbsolutePath().normalize();
        } catch (RuntimeException ex) {
            return Path.of("data/order-invoices").toAbsolutePath().normalize();
        }
    }

    private String originalFileName(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (!StringUtils.hasText(name)) {
            return "document";
        }
        String normalized = name.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        normalized = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        normalized = normalized.replaceAll("[\\r\\n\\t]", "").trim();
        return StringUtils.hasText(normalized) ? normalized : "document";
    }

    private String extension(String fileName) {
        int index = fileName.lastIndexOf('.');
        if (index < 0 || index == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(index + 1).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private String contentType(String extension) {
        return switch (extension) {
            case "pdf" -> "application/pdf";
            case "ofd" -> "application/ofd";
            case "xml" -> "application/xml";
            case "png" -> "image/png";
            default -> "image/jpeg";
        };
    }

    private String displayName(AuthUser authUser) {
        if (StringUtils.hasText(authUser.getRealName())) {
            return authUser.getRealName().trim();
        }
        return StringUtils.hasText(authUser.getUsername()) ? authUser.getUsername().trim() : "";
    }

    private String notificationText(String value, String fallback) {
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        return value.replace('\r', ' ').replace('\n', ' ').trim();
    }

    private String normalizeBlank(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String normalizeUpper(String value) {
        String normalized = normalizeBlank(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private String validateOptionalText(String value, int maxLength, String code, String message) {
        String normalized = normalizeBlank(value);
        if (normalized != null && normalized.length() > maxLength) {
            throw new BizException(code, message);
        }
        return normalized;
    }

    private String limit(String value, int maxLength) {
        return value == null || value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private void deleteQuietly(List<Path> files) {
        for (Path file : new LinkedHashSet<>(files)) {
            try {
                Files.deleteIfExists(file);
            } catch (IOException ex) {
                log.warn("Failed to clean up invoice file {}", file, ex);
            }
        }
    }

    public record AttachmentResource(Resource resource, String fileName, String contentType, long contentLength) {
    }
}
