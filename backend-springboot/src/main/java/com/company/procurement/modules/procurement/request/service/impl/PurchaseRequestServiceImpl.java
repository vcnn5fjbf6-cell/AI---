package com.company.procurement.modules.procurement.request.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.company.procurement.common.exception.BizException;
import com.company.procurement.common.result.PageResult;
import com.company.procurement.common.security.AuthContextHolder;
import com.company.procurement.common.security.AuthUser;
import com.company.procurement.common.security.DataScopeHelper;
import com.company.procurement.modules.approval.record.entity.ApprovalInstance;
import com.company.procurement.modules.approval.record.entity.ApprovalRecord;
import com.company.procurement.modules.approval.record.mapper.ApprovalInstanceMapper;
import com.company.procurement.modules.approval.record.mapper.ApprovalRecordMapper;
import com.company.procurement.modules.approval.record.service.ApprovalTaskService;
import com.company.procurement.modules.procurement.assistant.service.ProcurementComplianceReviewService;
import com.company.procurement.modules.procurement.request.dto.PurchaseRequestCreateDTO;
import com.company.procurement.modules.procurement.request.dto.PurchaseRequestItemDTO;
import com.company.procurement.modules.procurement.request.dto.PurchaseRequestPageQueryDTO;
import com.company.procurement.modules.procurement.request.dto.PurchaseRequestUpdateDTO;
import com.company.procurement.modules.procurement.request.entity.PurchaseRequest;
import com.company.procurement.modules.procurement.request.entity.PurchaseRequestItem;
import com.company.procurement.modules.procurement.request.mapper.PurchaseRequestItemMapper;
import com.company.procurement.modules.procurement.request.mapper.PurchaseRequestMapper;
import com.company.procurement.modules.procurement.request.service.EcommerceUrlService;
import com.company.procurement.modules.procurement.request.service.PurchaseRequestNoService;
import com.company.procurement.modules.procurement.request.service.PurchaseRequestService;
import com.company.procurement.modules.procurement.request.vo.PurchaseRequestItemVO;
import com.company.procurement.modules.procurement.request.vo.PurchaseRequestVO;
import com.company.procurement.modules.org.scope.entity.OrgScope;
import com.company.procurement.modules.procurement.route.entity.ProcurementApprovalRoute;
import com.company.procurement.modules.procurement.route.service.ProcurementApprovalRouteResolver;
import com.company.procurement.modules.procurement.route.service.ProcurementDepartmentAccessService;
import com.company.procurement.modules.procurement.workflow.ProcurementWorkflow;
import com.company.procurement.modules.procurement.workflow.service.OverPriceReviewService;
import com.company.procurement.modules.procurement.workflow.service.OverPriceReviewService.ItemReview;
import com.company.procurement.modules.procurement.workflow.vo.WorkflowSubmitApproverVO;
import com.company.procurement.modules.procurement.workflow.vo.WorkflowSubmitResultVO;
import com.company.procurement.modules.system.user.entity.SysUser;
import com.company.procurement.modules.system.user.mapper.SysUserMapper;
import com.company.procurement.modules.workbench.entity.WorkbenchCopy;
import com.company.procurement.modules.workbench.mapper.WorkbenchCopyMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PurchaseRequestServiceImpl extends ServiceImpl<PurchaseRequestMapper, PurchaseRequest> implements PurchaseRequestService {

    private static final String WORKBENCH_SOURCE_PURCHASE = "PURCHASE";
    private static final String REQ_TYPE_ENGINEERING = "工程项目";
    private static final String REQ_TYPE_ASSET = "实物资产/无形资产/长摊等";
    private static final String REQ_TYPE_SERVICE = "服务/费用/低值易耗等";
    private static final String REQ_TYPE_MIXED = "混合采购";

    private final PurchaseRequestItemMapper itemMapper;
    private final ApprovalInstanceMapper approvalInstanceMapper;
    private final ApprovalRecordMapper approvalRecordMapper;
    private final ProcurementApprovalRouteResolver approvalRouteResolver;
    private final ApprovalTaskService approvalTaskService;
    private final SysUserMapper userMapper;
    private final PurchaseRequestNoService requestNoService;
    private final EcommerceUrlService ecommerceUrlService;
    private final WorkbenchCopyMapper workbenchCopyMapper;
    private final ProcurementDepartmentAccessService departmentAccessService;
    private final ProcurementComplianceReviewService complianceReviewService;
    private final OverPriceReviewService overPriceReviewService;

    @Override
    public PageResult<PurchaseRequestVO> page(PurchaseRequestPageQueryDTO queryDTO) {
        LambdaQueryWrapper<PurchaseRequest> wrapper = buildQuery(queryDTO);
        Page<PurchaseRequest> page = baseMapper.selectPage(new Page<>(queryDTO.getPageNo(), queryDTO.getPageSize()), wrapper);
        List<PurchaseRequestVO> records = page.getRecords().stream().map(this::toVOWithoutItems).toList();
        return PageResult.of(page.getCurrent(), page.getSize(), page.getTotal(), records);
    }

    @Override
    public List<PurchaseRequestVO> list(PurchaseRequestPageQueryDTO queryDTO) {
        return baseMapper.selectList(buildQuery(queryDTO)).stream().map(this::toVOWithoutItems).toList();
    }

    @Override
    public PurchaseRequestVO detail(Long id) {
        PurchaseRequest req = getById(id);
        if (req == null) {
            throw new BizException("PURCHASE_REQUEST_NOT_FOUND", "Purchase request not found");
        }
        PurchaseRequestVO vo = toVOWithoutItems(req);
        vo.setItems(listItems(req));
        vo.setCurrentApprovalDueAt(approvalTaskService.currentDueAt(req.getId(), req.getCurrentNodeNo()));
        vo.setOverPriceReviewRequired(vo.getItems().stream()
            .anyMatch(item -> Boolean.TRUE.equals(item.getOverPriceReviewRequired())));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(PurchaseRequestCreateDTO dto) {
        validateItems(dto.getItems(), dto.getTotalAmount(), dto.getReqType());
        dto.setReqType(resolveRequestReqType(dto.getReqType(), dto.getItems()));
        validateReqType(dto.getReqType());
        String reqNo = resolveCreateReqNo(dto);
        Long existingId = findExistingRequestId(reqNo, dto.getApplicantUserId());
        if (existingId != null) {
            replaceRequestCopies(existingId, dto.getCopyUserIds(), dto.getApplicantUserId());
            return existingId;
        }
        PurchaseRequest req = new PurchaseRequest();
        req.setReqNo(reqNo);
        applyCreate(dto, req);
        applySystemDerivedFields(req);
        req.setSubmitCount(0);
        req.setCurrentNodeNo(0);
        req.setApprovalStatus(ProcurementWorkflow.APPROVAL_DRAFT);
        req.setStatus(ProcurementWorkflow.STATUS_DRAFT);
        req.setSelectedQuoteId(null);
        req.setLeaderConfirmStatus(ProcurementWorkflow.LEADER_CONFIRM_STATUS_PENDING);
        req.setLeaderConfirmRemark(null);
        req.setLeaderConfirmedBy(null);
        req.setLeaderConfirmedAt(null);
        try {
            save(req);
        } catch (DuplicateKeyException duplicate) {
            Long duplicateId = findExistingRequestId(reqNo, dto.getApplicantUserId());
            if (duplicateId != null) {
                return duplicateId;
            }
            throw duplicate;
        }
        saveItems(req, dto.getItems());
        replaceRequestCopies(req.getId(), dto.getCopyUserIds(), req.getApplicantUserId());
        return req.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, PurchaseRequestUpdateDTO dto) {
        PurchaseRequest req = getById(id);
        if (req == null) {
            throw new BizException("PURCHASE_REQUEST_NOT_FOUND", "Purchase request not found");
        }
        boolean rejectedItemResubmitEdit = !isEditableForApplicant(req.getStatus()) && hasRejectedItems(id);
        if (!isEditableForApplicant(req.getStatus()) && !rejectedItemResubmitEdit) {
            throw new BizException("PURCHASE_REQUEST_STATUS_INVALID", "Only draft, returned or rejected requests can be edited");
        }
        validateItems(dto.getItems(), dto.getTotalAmount(), dto.getReqType());
        dto.setReqType(resolveRequestReqType(dto.getReqType(), dto.getItems()));
        validateReqType(dto.getReqType());
        applyUpdate(dto, req);
        applySystemDerivedFields(req);
        if (rejectedItemResubmitEdit) {
            req.setStatus(ProcurementWorkflow.STATUS_DRAFT);
            req.setApprovalStatus(ProcurementWorkflow.APPROVAL_DRAFT);
            req.setCurrentNodeNo(0);
            req.setSubmittedAt(null);
            req.setApprovedAt(null);
            req.setRejectedAt(null);
        }
        updateById(req);
        itemMapper.delete(new LambdaQueryWrapper<PurchaseRequestItem>().eq(PurchaseRequestItem::getPurchaseRequestId, id));
        saveItems(req, dto.getItems());
        replaceRequestCopies(id, dto.getCopyUserIds(), req.getApplicantUserId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long id) {
        itemMapper.delete(new LambdaQueryWrapper<PurchaseRequestItem>().eq(PurchaseRequestItem::getPurchaseRequestId, id));
        removeById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public WorkflowSubmitResultVO previewSubmit(Long id) {
        AuthUser authUser = AuthContextHolder.requireUser();
        PurchaseRequest req = getById(id);
        if (req == null) {
            throw new BizException("PURCHASE_REQUEST_NOT_FOUND", "Purchase request not found");
        }
        if (!DataScopeHelper.isSystemAdmin(authUser) && !authUser.getUserId().equals(req.getApplicantUserId())) {
            throw new BizException("FORBIDDEN", "Only the applicant can submit this request");
        }
        if (ProcurementWorkflow.STATUS_PENDING_APPROVAL.equals(req.getStatus())
            && ProcurementWorkflow.APPROVAL_PENDING.equals(req.getApprovalStatus())) {
            return buildCurrentSubmitResult(req);
        }
        if (!isEditableForApplicant(req.getStatus())) {
            throw new BizException("PURCHASE_REQUEST_STATUS_INVALID", "Current status does not allow submit");
        }

        int firstNodeNo = approvalRouteResolver.firstDemandNodeNo(req);
        ProcurementApprovalRouteResolver.ApprovalAssignment firstAssignment =
            approvalRouteResolver.resolveAssignmentForNode(req, firstNodeNo);
        return buildSubmitResult(req, firstAssignment);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkflowSubmitResultVO submit(Long id) {
        AuthUser authUser = AuthContextHolder.requireUser();
        PurchaseRequest req = getById(id);
        if (req == null) {
            throw new BizException("PURCHASE_REQUEST_NOT_FOUND", "Purchase request not found");
        }
        if (!DataScopeHelper.isSystemAdmin(authUser) && !authUser.getUserId().equals(req.getApplicantUserId())) {
            throw new BizException("FORBIDDEN", "Only the applicant can submit this request");
        }
        if (ProcurementWorkflow.STATUS_PENDING_APPROVAL.equals(req.getStatus())
            && ProcurementWorkflow.APPROVAL_PENDING.equals(req.getApprovalStatus())) {
            return buildCurrentSubmitResult(req);
        }
        if (!isEditableForApplicant(req.getStatus())) {
            throw new BizException("PURCHASE_REQUEST_STATUS_INVALID", "Current status does not allow submit");
        }
        List<PurchaseRequestItem> submitItems = validatePersistedItemsForSubmit(req);
        req.setAiReviewOpinion(complianceReviewService.review(req, submitItems));

        LocalDateTime submitTime = LocalDateTime.now();
        String previousApprovalStatus = req.getApprovalStatus();
        int submitRound = req.getSubmitCount() == null ? 1 : req.getSubmitCount() + 1;
        boolean emergencyRequest = isEmergencyRequest(req);
        int firstNodeNo = approvalRouteResolver.firstDemandNodeNo(req);
        int totalNodeNo = ProcurementWorkflow.NODE_FLOOR_LEADER_DEMAND_REVIEW;
        ProcurementApprovalRouteResolver.ApprovalAssignment firstAssignment =
            approvalRouteResolver.resolveAssignmentForNode(req, firstNodeNo);
        String submitComment = emergencyRequest
            ? "紧急采购提交后进入楼长审核，楼长通过后直接进入供应商报价"
            : "采购需求提交后进入楼长审核";

        submitComment = appendActingComment(submitComment, req);
        ApprovalInstance instance = ensureApprovalInstance(id, submitTime);
        instance.setCurrentNodeNo(firstNodeNo);
        instance.setCurrentLevelNo(firstAssignment.levelNo());
        instance.setCurrentLevelCode(firstAssignment.levelCode());
        instance.setCurrentPassRule(firstAssignment.passRule());
        instance.setTotalNodeNo(totalNodeNo);
        instance.setCurrentApproverId(firstAssignment.currentApproverId());
        instance.setLastAction(ProcurementWorkflow.ACTION_SUBMIT);
        instance.setLastActionBy(authUser.getUserId());
        instance.setLastActionAt(submitTime);
        instance.setStatus(ProcurementWorkflow.APPROVAL_PENDING);
        instance.setStartedAt(submitTime);
        instance.setEndedAt(null);
        instance.setRemark(submitComment);
        approvalInstanceMapper.updateById(instance);
        approvalInstanceMapper.clearEndedAt(instance.getId());
        req.setSubmitCount(submitRound);
        approvalTaskService.createTasks(instance, req, firstAssignment);

        ApprovalRecord submitRecord = new ApprovalRecord();
        submitRecord.setInstanceId(instance.getId());
        submitRecord.setPurchaseRequestId(id);
        submitRecord.setNodeNo(0);
        submitRecord.setNodeName("采购需求提交");
        submitRecord.setApproverUserId(authUser.getUserId());
        submitRecord.setApproverRoleCode(resolveSubmitterRoleCode(authUser));
        submitRecord.setAction(ProcurementWorkflow.ACTION_SUBMIT);
        submitRecord.setFromStatus(previousApprovalStatus);
        submitRecord.setToStatus(ProcurementWorkflow.APPROVAL_PENDING);
        submitRecord.setApprovalComment(submitComment);
        submitRecord.setOperatedAt(submitTime);
        submitRecord.setSubmitRound(submitRound);
        submitRecord.setIsFinalNode(0);
        submitRecord.setCanResubmit(0);
        submitRecord.setTransferToUserId(firstAssignment.currentApproverId());
        submitRecord.setStatus("ACTIVE");
        approvalRecordMapper.insert(submitRecord);

        req.setStatus(ProcurementWorkflow.STATUS_PENDING_APPROVAL);
        req.setApprovalStatus(ProcurementWorkflow.APPROVAL_PENDING);
        req.setCurrentNodeNo(firstNodeNo);
        req.setSubmittedAt(submitTime);
        req.setApprovedAt(null);
        req.setSelectedQuoteId(null);
        req.setLeaderConfirmStatus(ProcurementWorkflow.LEADER_CONFIRM_STATUS_PENDING);
        req.setLeaderConfirmRemark(null);
        req.setLeaderConfirmedBy(null);
        req.setLeaderConfirmedAt(null);
        req.setDirectAwardApplied(0);
        req.setDirectAwardSupplierId(null);
        req.setDirectAwardSupplierName(null);
        req.setDirectAwardThresholdAmount(null);
        req.setDirectAwardReason(null);
        req.setQuoteCountdownStartedAt(null);
        req.setQuoteCountdownDueAt(null);
        req.setReturnedAt(null);
        req.setRejectedAt(null);
        req.setSubmitCount(submitRound);
        updateById(req);
        clearNullableResubmissionState(req.getId());
        return buildSubmitResult(req, firstAssignment);
    }

    private void clearNullableResubmissionState(Long requestId) {
        baseMapper.update(null, new LambdaUpdateWrapper<PurchaseRequest>()
            .eq(PurchaseRequest::getId, requestId)
            .set(PurchaseRequest::getApprovedAt, null)
            .set(PurchaseRequest::getSelectedQuoteId, null)
            .set(PurchaseRequest::getLeaderConfirmRemark, null)
            .set(PurchaseRequest::getLeaderConfirmedBy, null)
            .set(PurchaseRequest::getLeaderConfirmedAt, null)
            .set(PurchaseRequest::getDirectAwardSupplierId, null)
            .set(PurchaseRequest::getDirectAwardSupplierName, null)
            .set(PurchaseRequest::getDirectAwardThresholdAmount, null)
            .set(PurchaseRequest::getDirectAwardReason, null)
            .set(PurchaseRequest::getQuoteCountdownStartedAt, null)
            .set(PurchaseRequest::getQuoteCountdownDueAt, null)
            .set(PurchaseRequest::getReturnedAt, null)
            .set(PurchaseRequest::getRejectedAt, null));
    }

    private WorkflowSubmitResultVO buildCurrentSubmitResult(PurchaseRequest request) {
        WorkflowSubmitResultVO result = new WorkflowSubmitResultVO();
        result.setRequestId(request.getId());
        result.setReqNo(request.getReqNo());
        result.setReqTitle(displayReqTitle(request.getReqTitle()));
        ApprovalInstance instance = approvalInstanceMapper.selectOne(new LambdaQueryWrapper<ApprovalInstance>()
            .eq(ApprovalInstance::getPurchaseRequestId, request.getId())
            .orderByDesc(ApprovalInstance::getId)
            .last("LIMIT 1"));
        if (instance != null) {
            result.setNextNodeNo(instance.getCurrentNodeNo());
            result.setNextLevelCode(instance.getCurrentLevelCode());
            result.setNextNodeName(instance.getCurrentNodeNo() == null ? null : ProcurementWorkflow.nodeNameForNode(instance.getCurrentNodeNo()));
            result.setPassRule(instance.getCurrentPassRule());
            result.setPassRuleName("ALL".equalsIgnoreCase(String.valueOf(instance.getCurrentPassRule())) ? "全部通过" : "任意一人通过");
            result.setCurrentApproverId(instance.getCurrentApproverId());
        }
        return result;
    }

    private boolean isEmergencyRequest(PurchaseRequest request) {
        return request.getUrgencyLevel() != null && request.getUrgencyLevel() >= 2;
    }

    private boolean isEditableForApplicant(String status) {
        return ProcurementWorkflow.STATUS_DRAFT.equals(status)
            || ProcurementWorkflow.STATUS_RETURNED.equals(status)
            || ProcurementWorkflow.STATUS_REJECTED.equals(status);
    }

    private boolean hasRejectedItems(Long requestId) {
        if (requestId == null) {
            return false;
        }
        return itemMapper.selectCount(new LambdaQueryWrapper<PurchaseRequestItem>()
            .eq(PurchaseRequestItem::getPurchaseRequestId, requestId)
            .eq(PurchaseRequestItem::getStatus, ProcurementWorkflow.ITEM_STATUS_REJECTED)) > 0;
    }

    private ApprovalInstance ensureApprovalInstance(Long requestId, LocalDateTime now) {
        ApprovalInstance instance = approvalInstanceMapper.selectOne(new LambdaQueryWrapper<ApprovalInstance>()
            .eq(ApprovalInstance::getPurchaseRequestId, requestId)
            .orderByDesc(ApprovalInstance::getId)
            .last("LIMIT 1"));
        if (instance != null) {
            return instance;
        }
        instance = new ApprovalInstance();
        instance.setInstanceNo(generateInstanceNo());
        instance.setBizType("PURCHASE_REQUEST");
        instance.setBizId(requestId);
        instance.setPurchaseRequestId(requestId);
        instance.setCurrentNodeNo(0);
        instance.setCurrentLevelNo(null);
        instance.setCurrentLevelCode(null);
        instance.setCurrentPassRule(null);
        instance.setTotalNodeNo(0);
        instance.setCurrentApproverId(null);
        instance.setStatus(ProcurementWorkflow.APPROVAL_PENDING);
        instance.setStartedAt(now);
        instance.setEndedAt(null);
        instance.setRemark("采购需求提交");
        approvalInstanceMapper.insert(instance);
        return instance;
    }

    private LambdaQueryWrapper<PurchaseRequest> buildQuery(PurchaseRequestPageQueryDTO queryDTO) {
        LambdaQueryWrapper<PurchaseRequest> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(queryDTO.getReqNo())) {
            wrapper.like(PurchaseRequest::getReqNo, queryDTO.getReqNo());
        }
        if (StringUtils.hasText(queryDTO.getReqTitle())) {
            wrapper.like(PurchaseRequest::getReqTitle, queryDTO.getReqTitle());
        }
        if (StringUtils.hasText(queryDTO.getStatus())) {
            wrapper.eq(PurchaseRequest::getStatus, queryDTO.getStatus());
        }
        if (StringUtils.hasText(queryDTO.getApprovalStatus())) {
            wrapper.eq(PurchaseRequest::getApprovalStatus, queryDTO.getApprovalStatus());
        }
        if (queryDTO.getApplicantUserId() != null) {
            wrapper.eq(PurchaseRequest::getApplicantUserId, queryDTO.getApplicantUserId());
        }
        if (queryDTO.getPurchaserUserId() != null) {
            wrapper.eq(PurchaseRequest::getPurchaserUserId, queryDTO.getPurchaserUserId());
        }
        if (queryDTO.getRegionId() != null) {
            wrapper.eq(PurchaseRequest::getRegionId, queryDTO.getRegionId());
        }
        wrapper.orderByDesc(PurchaseRequest::getCreatedAt);
        return wrapper;
    }

    private void validateItems(List<PurchaseRequestItemDTO> items, BigDecimal totalAmount, String fallbackReqType) {
        if (CollectionUtils.isEmpty(items)) {
            throw new BizException("ITEMS_EMPTY", "Purchase request items must not be empty");
        }
        BigDecimal sum = BigDecimal.ZERO;
        int lineNo = 1;
        for (PurchaseRequestItemDTO item : items) {
            if (item.getQty() == null || item.getQty().compareTo(BigDecimal.ZERO) <= 0 || item.getQty().stripTrailingZeros().scale() > 0) {
                throw new BizException("ITEM_QTY_INVALID", "\u7b2c " + lineNo + " \u884c\u6570\u91cf\u5fc5\u987b\u662f\u5927\u4e8e 0 \u7684\u6574\u6570");
            }
            if (item.getUnitPrice() == null || item.getUnitPrice().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BizException("ITEM_PRICE_INVALID", "第 " + lineNo + " 行单价必须大于 0");
            }
            if (!StringUtils.hasText(item.getPurpose())) {
                throw new BizException("ITEM_PURPOSE_REQUIRED", "Line " + lineNo + " purchase reason must not be blank");
            }
            item.setEcommerceUrl(ecommerceUrlService.normalizeForLine(item.getEcommerceUrl(), lineNo));
            item.setReqType(normalizeItemReqTypeOrThrow(item.getReqType(), fallbackReqType, lineNo));
            BigDecimal lineAmount = item.getQty().multiply(item.getUnitPrice()).setScale(2, RoundingMode.HALF_UP);
            if (item.getLineAmount() != null && lineAmount.compareTo(item.getLineAmount().setScale(2, RoundingMode.HALF_UP)) != 0) {
                throw new BizException("ITEM_AMOUNT_INVALID", "Line " + lineNo + " amount must equal qty * unit price");
            }
            sum = sum.add(lineAmount);
            lineNo++;
        }
        if (totalAmount != null && sum.compareTo(totalAmount.setScale(2, RoundingMode.HALF_UP)) != 0) {
            throw new BizException("TOTAL_AMOUNT_INVALID", "Total amount must equal the sum of all line amounts");
        }
    }

    private List<PurchaseRequestItem> validatePersistedItemsForSubmit(PurchaseRequest request) {
        List<PurchaseRequestItem> items = itemMapper.selectList(new LambdaQueryWrapper<PurchaseRequestItem>()
            .eq(PurchaseRequestItem::getPurchaseRequestId, request.getId())
            .and(wrapper -> wrapper.ne(PurchaseRequestItem::getStatus, ProcurementWorkflow.ITEM_STATUS_REJECTED)
                .or()
                .isNull(PurchaseRequestItem::getStatus))
            .orderByAsc(PurchaseRequestItem::getLineNo)
            .orderByAsc(PurchaseRequestItem::getId));
        if (items.isEmpty()) {
            throw new BizException("ITEMS_EMPTY", "采购申请没有有效明细，不能提交");
        }
        for (PurchaseRequestItem item : items) {
            int lineNo = item.getLineNo() == null ? items.indexOf(item) + 1 : item.getLineNo();
            if (item.getQty() == null || item.getQty().compareTo(BigDecimal.ZERO) <= 0 || item.getQty().stripTrailingZeros().scale() > 0) {
                throw new BizException("ITEM_QTY_INVALID", "第 " + lineNo + " 行数量必须是大于 0 的整数");
            }
            if (item.getUnitPrice() == null || item.getUnitPrice().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BizException("ITEM_PRICE_INVALID", "第 " + lineNo + " 行单价必须大于 0，请补充或更正价格后再提交");
            }
            if (!StringUtils.hasText(item.getPurpose())) {
                throw new BizException("ITEM_PURPOSE_REQUIRED", "第 " + lineNo + " 行采购用途不能为空");
            }
            String normalizedEcommerceUrl = ecommerceUrlService.normalizeForLine(item.getEcommerceUrl(), lineNo);
            if (!Objects.equals(normalizedEcommerceUrl, item.getEcommerceUrl())) {
                item.setEcommerceUrl(normalizedEcommerceUrl);
                itemMapper.updateById(item);
            }
            String normalizedReqType = normalizeItemReqTypeOrThrow(item.getReqType(), request.getReqType(), lineNo);
            if (!isAllowedItemReqType(normalizedReqType)) {
                throw new BizException("REQ_TYPE_INVALID", "第 " + lineNo + " 行采购类型只能是工程项目、实物资产/无形资产/长摊等、服务/费用/低值易耗等");
            }
            BigDecimal lineAmount = item.getQty().multiply(item.getUnitPrice()).setScale(2, RoundingMode.HALF_UP);
            if (lineAmount.compareTo(BigDecimal.ZERO) <= 0
                || item.getLineAmount() == null
                || lineAmount.compareTo(item.getLineAmount().setScale(2, RoundingMode.HALF_UP)) != 0) {
                throw new BizException("ITEM_AMOUNT_INVALID", "第 " + lineNo + " 行金额必须大于 0 且等于数量乘以单价");
            }
        }
        return items;
    }

    private void validateReqType(String reqType) {
        String normalized = normalizeReqTypeAlias(reqType);
        if (!isAllowedItemReqType(normalized) && !REQ_TYPE_MIXED.equals(normalized)) {
            throw new BizException("REQ_TYPE_INVALID", "采购类型只能是工程项目、实物资产/无形资产/长摊等、服务/费用/低值易耗等");
        }
    }

    private String resolveRequestReqType(String fallbackReqType, List<PurchaseRequestItemDTO> items) {
        LinkedHashSet<String> types = new LinkedHashSet<>();
        if (items != null) {
            for (PurchaseRequestItemDTO item : items) {
                String itemType = normalizeReqTypeAlias(item == null ? null : item.getReqType());
                if (isAllowedItemReqType(itemType)) {
                    types.add(itemType);
                }
            }
        }
        if (types.size() == 1) {
            return types.iterator().next();
        }
        if (types.size() > 1) {
            return REQ_TYPE_MIXED;
        }
        String fallback = normalizeReqTypeAlias(fallbackReqType);
        return isAllowedItemReqType(fallback) ? fallback : REQ_TYPE_SERVICE;
    }

    private String normalizeItemReqTypeOrThrow(String reqType, String fallbackReqType, int lineNo) {
        String normalized = normalizeReqTypeAlias(reqType);
        if (StringUtils.hasText(reqType) && !isAllowedItemReqType(normalized)) {
            throw new BizException("REQ_TYPE_INVALID", "第 " + lineNo + " 行采购类型只能是工程项目、实物资产/无形资产/长摊等、服务/费用/低值易耗等");
        }
        if (isAllowedItemReqType(normalized)) {
            return normalized;
        }
        String fallback = normalizeReqTypeAlias(fallbackReqType);
        return isAllowedItemReqType(fallback) ? fallback : REQ_TYPE_SERVICE;
    }

    private boolean isAllowedItemReqType(String reqType) {
        return REQ_TYPE_ENGINEERING.equals(reqType)
            || REQ_TYPE_ASSET.equals(reqType)
            || REQ_TYPE_SERVICE.equals(reqType);
    }

    private String normalizeReqTypeAlias(String reqType) {
        String value = reqType == null ? "" : reqType.trim();
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return switch (value) {
            case REQ_TYPE_ENGINEERING -> REQ_TYPE_ENGINEERING;
            case REQ_TYPE_ASSET, "资产", "实物资产", "无形资产", "长摊", "长期待摊" -> REQ_TYPE_ASSET;
            case REQ_TYPE_SERVICE, "低值易耗", "服务", "费用", "其他" -> REQ_TYPE_SERVICE;
            case REQ_TYPE_MIXED -> REQ_TYPE_MIXED;
            default -> value;
        };
    }

    private void applyCreate(PurchaseRequestCreateDTO dto, PurchaseRequest req) {
        ProcurementDepartmentAccessService.DepartmentSelection selection = departmentAccessService.resolveSelection(
            AuthContextHolder.requireUser(),
            dto.getApplicantUserId(),
            dto.getDeptCode(),
            dto.getDeptName(),
            dto.getActingReason()
        );
        req.setReqTitle(dto.getReqTitle());
        req.setReqType(dto.getReqType());
        req.setApplicantUserId(selection.applicant().getId());
        req.setPurchaserUserId(dto.getPurchaserUserId() == null ? selection.applicant().getId() : dto.getPurchaserUserId());
        req.setRegionId(selection.applicant().getRegionId() == null ? dto.getRegionId() : selection.applicant().getRegionId());
        req.setBuildingScopeId(dto.getBuildingScopeId());
        req.setApplicantSubjectId(dto.getApplicantSubjectId());
        req.setApprovalPolicyId(dto.getApprovalPolicyId());
        req.setSpecialtyCode(dto.getSpecialtyCode());
        req.setProfessionalReviewRequired(dto.getProfessionalReviewRequired() == null ? 0 : dto.getProfessionalReviewRequired());
        applyDepartmentSelection(req, selection, dto.getBudgetCode());
        req.setCurrency(dto.getCurrency());
        req.setTotalAmount(dto.getTotalAmount());
        req.setReferenceTotalAmount(dto.getTotalAmount());
        req.setPriceSource(ProcurementWorkflow.PRICE_SOURCE_REFERENCE);
        req.setUrgencyLevel(dto.getUrgencyLevel());
        req.setNeedDate(dto.getNeedDate());
        req.setReqReason(dto.getReqReason());
        req.setAiReviewOpinion(dto.getAiReviewOpinion());
        req.setAwardStrategy(ProcurementWorkflow.normalizeAwardStrategy(dto.getAwardStrategy()));
        req.setRemark(dto.getRemark());
    }

    private void applyUpdate(PurchaseRequestUpdateDTO dto, PurchaseRequest req) {
        ProcurementDepartmentAccessService.DepartmentSelection selection = departmentAccessService.resolveSelection(
            AuthContextHolder.requireUser(),
            dto.getApplicantUserId(),
            dto.getDeptCode(),
            dto.getDeptName(),
            dto.getActingReason()
        );
        req.setReqTitle(dto.getReqTitle());
        req.setReqType(dto.getReqType());
        req.setApplicantUserId(selection.applicant().getId());
        req.setPurchaserUserId(dto.getPurchaserUserId() == null ? selection.applicant().getId() : dto.getPurchaserUserId());
        req.setRegionId(selection.applicant().getRegionId() == null ? dto.getRegionId() : selection.applicant().getRegionId());
        req.setBuildingScopeId(dto.getBuildingScopeId());
        req.setApplicantSubjectId(dto.getApplicantSubjectId());
        req.setApprovalPolicyId(dto.getApprovalPolicyId());
        req.setSpecialtyCode(dto.getSpecialtyCode());
        req.setProfessionalReviewRequired(dto.getProfessionalReviewRequired() == null ? 0 : dto.getProfessionalReviewRequired());
        applyDepartmentSelection(req, selection, dto.getBudgetCode());
        req.setCurrency(dto.getCurrency());
        req.setTotalAmount(dto.getTotalAmount());
        req.setReferenceTotalAmount(dto.getTotalAmount());
        req.setPriceSource(ProcurementWorkflow.PRICE_SOURCE_REFERENCE);
        req.setUrgencyLevel(dto.getUrgencyLevel());
        req.setNeedDate(dto.getNeedDate());
        req.setReqReason(dto.getReqReason());
        if (StringUtils.hasText(dto.getAiReviewOpinion())) {
            req.setAiReviewOpinion(dto.getAiReviewOpinion());
        }
        req.setAwardStrategy(ProcurementWorkflow.normalizeAwardStrategy(dto.getAwardStrategy()));
        req.setRemark(dto.getRemark());
    }

    private void applyDepartmentSelection(PurchaseRequest req,
                                          ProcurementDepartmentAccessService.DepartmentSelection selection,
                                          String fallbackBudgetCode) {
        ProcurementApprovalRoute route = selection.route();
        req.setDeptCode(firstText(route.getDeptCode(), route.getCostCenterCode()));
        req.setDeptName(firstText(route.getDeptName(), route.getRoomName()));
        req.setBudgetCode(firstText(route.getCostCenterCode(), fallbackBudgetCode, route.getDeptCode()));
        if (selection.acting()) {
            req.setActingUserId(selection.actingUserId());
            req.setActingUserName(selection.actingUserName());
            req.setActingReason(selection.actingReason());
        } else {
            req.setActingUserId(null);
            req.setActingUserName(null);
            req.setActingReason(null);
        }
    }

    private String appendActingComment(String comment, PurchaseRequest req) {
        if (req == null || req.getActingUserId() == null) {
            return comment;
        }
        StringBuilder builder = new StringBuilder(StringUtils.hasText(comment) ? comment : "");
        if (builder.length() > 0) {
            builder.append("；");
        }
        builder.append("管理员代办：")
            .append(firstText(req.getActingUserName(), String.valueOf(req.getActingUserId())))
            .append(" 代申请人选择部门 ")
            .append(firstText(req.getDeptName(), req.getDeptCode(), "-"));
        if (StringUtils.hasText(req.getActingReason())) {
            builder.append("，原因：").append(req.getActingReason().trim());
        }
        return builder.toString();
    }

    private void applySystemDerivedFields(PurchaseRequest req) {
        ProcurementApprovalRoute route = approvalRouteResolver.findRoute(req);
        if (route != null && StringUtils.hasText(route.getCostCenterCode())) {
            req.setBudgetCode(route.getCostCenterCode().trim());
        }
        req.setAwardStrategy(ProcurementWorkflow.resolveAutoAwardStrategy(req.getTotalAmount()));
    }

    private WorkflowSubmitResultVO buildSubmitResult(PurchaseRequest request,
                                                     ProcurementApprovalRouteResolver.ApprovalAssignment assignment) {
        WorkflowSubmitResultVO result = new WorkflowSubmitResultVO();
        result.setRequestId(request.getId());
        result.setReqNo(request.getReqNo());
        result.setReqTitle(displayReqTitle(request.getReqTitle()));
        result.setNextNodeNo(assignment.levelNo());
        result.setNextLevelCode(assignment.levelCode());
        result.setNextNodeName(assignment.levelName());
        result.setPassRule(assignment.passRule());
        result.setPassRuleName("ALL".equalsIgnoreCase(String.valueOf(assignment.passRule())) ? "全部通过" : "任意一人通过");
        result.setCurrentApproverId(assignment.currentApproverId());
        result.setApprovers(buildSubmitApprovers(assignment.candidateUserIds()));
        return result;
    }

    private List<WorkflowSubmitApproverVO> buildSubmitApprovers(List<Long> candidateUserIds) {
        if (candidateUserIds == null || candidateUserIds.isEmpty()) {
            return List.of();
        }
        List<Long> orderedIds = new ArrayList<>(new LinkedHashSet<>(candidateUserIds));
        List<SysUser> users = userMapper.selectBatchIds(orderedIds);
        List<WorkflowSubmitApproverVO> approvers = new ArrayList<>();
        for (Long userId : orderedIds) {
            for (SysUser user : users) {
                if (Objects.equals(user.getId(), userId)) {
                    WorkflowSubmitApproverVO approver = new WorkflowSubmitApproverVO();
                    approver.setUserId(user.getId());
                    approver.setUsername(user.getUsername());
                    approver.setRealName(user.getRealName());
                    approver.setEmployeeNo(user.getEmployeeNo());
                    approver.setDisplayName(userDisplayName(user));
                    approvers.add(approver);
                    break;
                }
            }
        }
        return approvers;
    }

    private String userDisplayName(SysUser user) {
        String name = StringUtils.hasText(user.getRealName()) ? user.getRealName().trim() : user.getUsername();
        return name;
    }

    private String displayReqTitle(String title) {
        if (!StringUtils.hasText(title)) {
            return title;
        }
        return title
            .replace("全国月度需求汇总", "酒仙桥区域月度需求汇总")
            .replaceAll("[（(](\\d+)单/(\\d+)项[）)]", "（$1张申请/$2项明细）");
    }

    private void saveItems(PurchaseRequest req, List<PurchaseRequestItemDTO> itemDTOs) {
        String receivingCity = resolveReceivingCity(req);
        String deliveryAddress = resolveDeliveryAddress(req);
        int lineNo = 1;
        for (PurchaseRequestItemDTO itemDTO : itemDTOs) {
            PurchaseRequestItem item = new PurchaseRequestItem();
            item.setPurchaseRequestId(req.getId());
            item.setLineNo(lineNo++);
            item.setItemCode(StringUtils.hasText(itemDTO.getItemCode()) ? itemDTO.getItemCode() : generateMaterialCode());
            item.setReqType(normalizeItemReqTypeOrThrow(itemDTO.getReqType(), req.getReqType(), item.getLineNo()));
            item.setItemName(itemDTO.getItemName());
            item.setSpecModel(itemDTO.getSpecModel());
            item.setItemBrand(itemDTO.getItemBrand());
            item.setUnit(itemDTO.getUnit());
            item.setQty(itemDTO.getQty());
            item.setUnitPrice(itemDTO.getUnitPrice());
            item.setReferenceUnitPrice(itemDTO.getUnitPrice());
            item.setTaxRate(itemDTO.getTaxRate());
            BigDecimal lineAmount = itemDTO.getQty().multiply(itemDTO.getUnitPrice()).setScale(2, RoundingMode.HALF_UP);
            item.setLineAmount(lineAmount);
            item.setReferenceLineAmount(lineAmount);
            item.setNeedDate(itemDTO.getNeedDate());
            item.setPurpose(itemDTO.getPurpose());
            item.setEcommerceUrl(itemDTO.getEcommerceUrl());
            item.setReceivingCity(firstText(itemDTO.getReceivingCity(), receivingCity));
            item.setDeliveryAddress(firstText(itemDTO.getDeliveryAddress(), deliveryAddress));
            item.setStatus(StringUtils.hasText(itemDTO.getStatus()) ? itemDTO.getStatus() : "ACTIVE");
            item.setRemark(itemDTO.getRemark());
            itemMapper.insert(item);
        }
    }

    private List<PurchaseRequestItemVO> listItems(PurchaseRequest request) {
        Long reqId = request.getId();
        List<PurchaseRequestItem> items = itemMapper.selectList(new LambdaQueryWrapper<PurchaseRequestItem>()
            .eq(PurchaseRequestItem::getPurchaseRequestId, reqId)
            .orderByAsc(PurchaseRequestItem::getLineNo));
        var reviewByItemId = overPriceReviewService.reviewByRequestItemId(request);
        List<PurchaseRequestItemVO> result = new ArrayList<>();
        for (PurchaseRequestItem item : items) {
            PurchaseRequestItemVO vo = new PurchaseRequestItemVO();
            vo.setId(item.getId());
            vo.setLineNo(item.getLineNo());
            vo.setItemCode(item.getItemCode());
            vo.setReqType(item.getReqType());
            vo.setItemName(item.getItemName());
            vo.setSpecModel(item.getSpecModel());
            vo.setItemBrand(item.getItemBrand());
            vo.setUnit(item.getUnit());
            vo.setQty(item.getQty());
            vo.setUnitPrice(item.getUnitPrice());
            vo.setReferenceUnitPrice(item.getReferenceUnitPrice() == null ? item.getUnitPrice() : item.getReferenceUnitPrice());
            vo.setTaxRate(item.getTaxRate());
            vo.setLineAmount(item.getLineAmount());
            vo.setReferenceLineAmount(item.getReferenceLineAmount() == null ? item.getLineAmount() : item.getReferenceLineAmount());
            ItemReview review = reviewByItemId.get(item.getId());
            if (review != null) {
                vo.setAwardedSupplierName(review.supplierName());
                vo.setAwardedUnitPrice(review.awardedUnitPrice());
                vo.setOverPriceUnitDifference(review.unitDifference());
                vo.setOverPriceLineDifference(review.lineDifference());
                vo.setOverPriceRatePercent(review.ratePercent());
                vo.setOverPriceReviewRequired(review.reviewRequired());
                vo.setSourceRequestNo(review.sourceRequestNo());
                vo.setSourceApplicantName(review.applicantName());
                vo.setSourceApplicantDeptName(review.applicantDeptName());
            }
            vo.setNeedDate(item.getNeedDate());
            vo.setPurpose(item.getPurpose());
            vo.setEcommerceUrl(item.getEcommerceUrl());
            vo.setReceivingCity(item.getReceivingCity());
            vo.setDeliveryAddress(item.getDeliveryAddress());
            vo.setSourceRequestId(item.getSourceRequestId());
            vo.setSourceRequestItemId(item.getSourceRequestItemId());
            vo.setStatus(item.getStatus());
            vo.setRemark(item.getRemark());
            result.add(vo);
        }
        return result;
    }

    private PurchaseRequestVO toVOWithoutItems(PurchaseRequest req) {
        PurchaseRequestVO vo = new PurchaseRequestVO();
        vo.setId(req.getId());
        vo.setReqNo(req.getReqNo());
        vo.setReqTitle(displayReqTitle(req.getReqTitle()));
        vo.setReqType(req.getReqType());
        vo.setApplicantUserId(req.getApplicantUserId());
        fillApplicantInfo(vo, req.getApplicantUserId());
        vo.setPurchaserUserId(req.getPurchaserUserId());
        vo.setRegionId(req.getRegionId());
        vo.setBuildingScopeId(req.getBuildingScopeId());
        vo.setApplicantSubjectId(req.getApplicantSubjectId());
        vo.setApprovalPolicyId(req.getApprovalPolicyId());
        vo.setSpecialtyCode(req.getSpecialtyCode());
        vo.setProfessionalReviewRequired(req.getProfessionalReviewRequired());
        vo.setDeptCode(req.getDeptCode());
        vo.setDeptName(req.getDeptName());
        vo.setReceivingCity(resolveReceivingCity(req));
        vo.setBudgetCode(req.getBudgetCode());
        vo.setActingUserId(req.getActingUserId());
        vo.setActingUserName(req.getActingUserName());
        vo.setActingReason(req.getActingReason());
        vo.setActingDelegated(req.getActingUserId() != null);
        vo.setCurrency(req.getCurrency());
        vo.setTotalAmount(req.getTotalAmount());
        vo.setReferenceTotalAmount(req.getReferenceTotalAmount() == null ? req.getTotalAmount() : req.getReferenceTotalAmount());
        vo.setPriceSource(StringUtils.hasText(req.getPriceSource())
            ? req.getPriceSource()
            : ProcurementWorkflow.PRICE_SOURCE_REFERENCE);
        vo.setUrgencyLevel(req.getUrgencyLevel());
        vo.setNeedDate(req.getNeedDate());
        vo.setReqReason(req.getReqReason());
        vo.setAiReviewOpinion(req.getAiReviewOpinion());
        vo.setAwardStrategy(ProcurementWorkflow.normalizeAwardStrategy(req.getAwardStrategy()));
        vo.setSubmitCount(req.getSubmitCount());
        vo.setCurrentNodeNo(req.getCurrentNodeNo());
        vo.setCurrentNodeName(currentNodeName(req));
        vo.setApprovalStatus(req.getApprovalStatus());
        vo.setStatus(req.getStatus());
        vo.setSubmittedAt(req.getSubmittedAt());
        vo.setApprovedAt(req.getApprovedAt());
        vo.setSelectedQuoteId(req.getSelectedQuoteId());
        vo.setLeaderConfirmStatus(req.getLeaderConfirmStatus());
        vo.setLeaderConfirmRemark(req.getLeaderConfirmRemark());
        vo.setLeaderConfirmedBy(req.getLeaderConfirmedBy());
        vo.setLeaderConfirmedAt(req.getLeaderConfirmedAt());
        vo.setQuoteCountdownStartedAt(req.getQuoteCountdownStartedAt());
        vo.setQuoteCountdownDueAt(req.getQuoteCountdownDueAt());
        vo.setDirectAwardApplied(req.getDirectAwardApplied() != null && req.getDirectAwardApplied() == 1);
        vo.setDirectAwardSupplierId(req.getDirectAwardSupplierId());
        vo.setDirectAwardSupplierName(req.getDirectAwardSupplierName());
        vo.setDirectAwardThresholdAmount(req.getDirectAwardThresholdAmount());
        vo.setDirectAwardReason(req.getDirectAwardReason());
        vo.setRemark(req.getRemark());
        vo.setCreatedAt(req.getCreatedAt());
        vo.setUpdatedAt(req.getUpdatedAt());
        vo.setCopyUserIds(listRequestCopyUserIds(req.getId()));
        return vo;
    }

    private String currentNodeName(PurchaseRequest req) {
        if (req == null) {
            return "-";
        }
        String status = normalizeCode(req.getStatus());
        String approvalStatus = normalizeCode(req.getApprovalStatus());
        if (ProcurementWorkflow.STATUS_PENDING_APPROVAL.equals(status)
            && ProcurementWorkflow.APPROVAL_PENDING.equals(approvalStatus)) {
            return req.getCurrentNodeNo() == null ? "-" : ProcurementWorkflow.nodeNameForNode(req.getCurrentNodeNo());
        }
        if (ProcurementWorkflow.APPROVAL_DRAFT.equals(approvalStatus) || ProcurementWorkflow.STATUS_DRAFT.equals(status)) {
            return "提交申请";
        }
        if (ProcurementWorkflow.APPROVAL_APPROVED.equals(approvalStatus)
            || ProcurementWorkflow.STATUS_APPROVED.equals(status)
            || ProcurementWorkflow.STATUS_DEMAND_POOL.equals(status)
            || ProcurementWorkflow.STATUS_DEMAND_AGGREGATED.equals(status)
            || ProcurementWorkflow.STATUS_QUOTING.equals(status)) {
            return "完成";
        }
        if (ProcurementWorkflow.APPROVAL_REJECTED.equals(approvalStatus) || ProcurementWorkflow.STATUS_REJECTED.equals(status)) {
            return "已驳回";
        }
        if (ProcurementWorkflow.APPROVAL_RETURNED.equals(approvalStatus) || ProcurementWorkflow.STATUS_RETURNED.equals(status)) {
            return "已退回";
        }
        if (ProcurementWorkflow.STATUS_CANCELLED.equals(status)) {
            return "已关闭";
        }
        if (ProcurementWorkflow.APPROVAL_TERMINATED.equals(approvalStatus) || ProcurementWorkflow.STATUS_TERMINATED.equals(status)) {
            return "已终止";
        }
        return req.getCurrentNodeNo() == null ? "-" : ProcurementWorkflow.nodeNameForNode(req.getCurrentNodeNo());
    }

    private String normalizeCode(String code) {
        return StringUtils.hasText(code) ? code.trim().toUpperCase(Locale.ROOT) : "";
    }

    private List<Long> listRequestCopyUserIds(Long requestId) {
        if (requestId == null) {
            return List.of();
        }
        return workbenchCopyMapper.selectList(new LambdaQueryWrapper<WorkbenchCopy>()
                .eq(WorkbenchCopy::getSourceType, WORKBENCH_SOURCE_PURCHASE)
                .eq(WorkbenchCopy::getSourceId, requestId)
                .orderByAsc(WorkbenchCopy::getId))
            .stream()
            .map(WorkbenchCopy::getUserId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
    }

    private void replaceRequestCopies(Long requestId, List<Long> copyUserIds, Long applicantUserId) {
        if (requestId == null) {
            return;
        }
        workbenchCopyMapper.physicalDeleteBySource(WORKBENCH_SOURCE_PURCHASE, requestId);
        if (copyUserIds == null || copyUserIds.isEmpty()) {
            return;
        }
        List<Long> orderedIds = copyUserIds.stream()
            .filter(Objects::nonNull)
            .filter(userId -> !Objects.equals(userId, applicantUserId))
            .distinct()
            .toList();
        if (orderedIds.isEmpty()) {
            return;
        }
        List<SysUser> activeUsers = userMapper.selectBatchIds(orderedIds).stream()
            .filter(user -> user.getStatus() != null && user.getStatus() == 1)
            .filter(user -> !DataScopeHelper.USER_TYPE_SUPPLIER.equalsIgnoreCase(user.getUserType()))
            .toList();
        AuthUser authUser = AuthContextHolder.get();
        Long copiedBy = authUser == null ? applicantUserId : authUser.getUserId();
        LocalDateTime copiedAt = LocalDateTime.now();
        for (Long userId : orderedIds) {
            boolean exists = activeUsers.stream().anyMatch(user -> Objects.equals(user.getId(), userId));
            if (!exists) {
                continue;
            }
            WorkbenchCopy copy = new WorkbenchCopy();
            copy.setUserId(userId);
            copy.setSourceType(WORKBENCH_SOURCE_PURCHASE);
            copy.setSourceId(requestId);
            copy.setCopiedBy(copiedBy);
            copy.setCopiedAt(copiedAt);
            copy.setRemark("申请人填写抄送");
            workbenchCopyMapper.insert(copy);
        }
    }

    private String resolveReceivingCity(PurchaseRequest req) {
        String itemCityText = resolveItemReceivingCity(req);
        if (StringUtils.hasText(itemCityText)) {
            return itemCityText;
        }
        OrgScope scope = approvalRouteResolver.findScope(req);
        if (scope == null) {
            return "";
        }
        if (StringUtils.hasText(scope.getCity())) {
            return scope.getCity().trim();
        }
        if (StringUtils.hasText(scope.getProvince())) {
            return scope.getProvince().trim();
        }
        return "";
    }

    private String resolveItemReceivingCity(PurchaseRequest req) {
        if (req == null || req.getId() == null) {
            return "";
        }
        List<String> cities = itemMapper.selectList(new LambdaQueryWrapper<PurchaseRequestItem>()
                .eq(PurchaseRequestItem::getPurchaseRequestId, req.getId())
                .orderByAsc(PurchaseRequestItem::getLineNo))
            .stream()
            .map(PurchaseRequestItem::getReceivingCity)
            .filter(StringUtils::hasText)
            .map(String::trim)
            .distinct()
            .toList();
        if (cities.isEmpty()) {
            return "";
        }
        if (cities.size() == 1) {
            return cities.get(0);
        }
        if (cities.size() <= 3) {
            return String.join("/", cities);
        }
        return cities.get(0) + "等" + cities.size() + "城";
    }

    private String resolveDeliveryAddress(PurchaseRequest req) {
        OrgScope scope = approvalRouteResolver.findScope(req);
        if (scope == null) {
            return "";
        }
        if (StringUtils.hasText(scope.getAddress())) {
            return scope.getAddress().trim();
        }
        List<String> parts = new ArrayList<>();
        addIfPresent(parts, scope.getProvince());
        addIfPresent(parts, scope.getCity());
        addIfPresent(parts, scope.getDistrictName());
        addIfPresent(parts, scope.getTownName());
        addIfPresent(parts, scope.getRoomName());
        addIfPresent(parts, scope.getBuildingName());
        return String.join("", parts);
    }

    private void addIfPresent(List<String> parts, String value) {
        if (StringUtils.hasText(value)) {
            parts.add(value.trim());
        }
    }

    private String firstText(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private void fillApplicantInfo(PurchaseRequestVO vo, Long applicantUserId) {
        if (applicantUserId == null) {
            return;
        }
        SysUser applicant = userMapper.selectById(applicantUserId);
        if (applicant == null) {
            return;
        }
        vo.setApplicantName(applicant.getRealName());
        vo.setApplicantEmployeeNo(applicant.getEmployeeNo());
    }

    private String resolveCreateReqNo(PurchaseRequestCreateDTO dto) {
        String reqNo = requestNoService.normalize(dto.getReqNo());
        if (requestNoService.matchesUrgency(reqNo, dto.getUrgencyLevel())) {
            return reqNo;
        }
        return requestNoService.allocate(dto.getUrgencyLevel());
    }

    private Long findExistingRequestId(String reqNo, Long applicantUserId) {
        if (!StringUtils.hasText(reqNo)) {
            return null;
        }
        PurchaseRequest existing = baseMapper.selectOne(new LambdaQueryWrapper<PurchaseRequest>()
            .eq(PurchaseRequest::getReqNo, reqNo)
            .last("LIMIT 1"));
        if (existing == null) {
            return null;
        }
        if (applicantUserId != null && !applicantUserId.equals(existing.getApplicantUserId())) {
            throw new BizException("PURCHASE_REQUEST_NO_EXISTS", "Purchase request number already exists");
        }
        return existing.getId();
    }

    private String generateMaterialCode() {
        String randomPart = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        return "MAT-" + randomPart;
    }

    private String generateInstanceNo() {
        String timePart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String randomPart = UUID.randomUUID().toString().replace("-", "").substring(0, 4).toUpperCase();
        return "AP" + timePart + randomPart;
    }

    private String resolveSubmitterRoleCode(AuthUser authUser) {
        if (authUser.getRoleCodes() != null && authUser.getRoleCodes().contains(ProcurementWorkflow.ROLE_ENGINEER)) {
            return ProcurementWorkflow.ROLE_ENGINEER;
        }
        if (authUser.getRoleCodes() != null && !authUser.getRoleCodes().isEmpty()) {
            return authUser.getRoleCodes().get(0);
        }
        return authUser.getUserType();
    }
}
