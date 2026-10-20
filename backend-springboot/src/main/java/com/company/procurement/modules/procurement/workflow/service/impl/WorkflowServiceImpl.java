package com.company.procurement.modules.procurement.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.company.procurement.common.export.CsvExportUtils;
import com.company.procurement.common.export.ExportFile;
import com.company.procurement.common.exception.BizException;
import com.company.procurement.common.result.PageResult;
import com.company.procurement.common.security.AuthContextHolder;
import com.company.procurement.common.security.AuthUser;
import com.company.procurement.common.security.DataScopeHelper;
import com.company.procurement.modules.approval.record.vo.ApprovalRecordVO;
import com.company.procurement.modules.approval.record.entity.ApprovalInstance;
import com.company.procurement.modules.approval.record.entity.ApprovalRecord;
import com.company.procurement.modules.approval.record.entity.ApprovalTask;
import com.company.procurement.modules.approval.record.mapper.ApprovalInstanceMapper;
import com.company.procurement.modules.approval.record.mapper.ApprovalRecordMapper;
import com.company.procurement.modules.approval.record.service.ApprovalTaskService;
import com.company.procurement.modules.org.scope.entity.OrgScope;
import com.company.procurement.modules.procurement.request.dto.PurchaseRequestCreateDTO;
import com.company.procurement.modules.procurement.request.dto.PurchaseRequestPageQueryDTO;
import com.company.procurement.modules.procurement.request.dto.PurchaseRequestUpdateDTO;
import com.company.procurement.modules.procurement.request.entity.PurchaseRequest;
import com.company.procurement.modules.procurement.request.entity.PurchaseRequestItem;
import com.company.procurement.modules.procurement.request.mapper.PurchaseRequestItemMapper;
import com.company.procurement.modules.procurement.request.mapper.PurchaseRequestMapper;
import com.company.procurement.modules.procurement.request.service.PurchaseRequestNoService;
import com.company.procurement.modules.procurement.request.service.PurchaseRequestService;
import com.company.procurement.modules.procurement.request.vo.PurchaseRequestItemVO;
import com.company.procurement.modules.procurement.request.vo.PurchaseRequestVO;
import com.company.procurement.modules.procurement.order.entity.ProcurementAfterSale;
import com.company.procurement.modules.procurement.order.mapper.ProcurementAfterSaleMapper;
import com.company.procurement.modules.procurement.route.service.ProcurementApprovalRouteResolver;
import com.company.procurement.modules.procurement.settings.entity.ProcurementSetting;
import com.company.procurement.modules.procurement.settings.mapper.ProcurementSettingMapper;
import com.company.procurement.modules.procurement.settings.service.ProcurementSettingsService;
import com.company.procurement.modules.procurement.settings.vo.ProcurementSettingItemVO;
import com.company.procurement.modules.procurement.workflow.ProcurementWorkflow;
import com.company.procurement.modules.procurement.workflow.dto.WorkflowApprovalActionDTO;
import com.company.procurement.modules.procurement.workflow.dto.WorkflowQuoteConfirmDTO;
import com.company.procurement.modules.procurement.workflow.dto.WorkflowQuoteDeadlineExtendDTO;
import com.company.procurement.modules.procurement.workflow.dto.WorkflowReplacementDecisionDTO;
import com.company.procurement.modules.procurement.workflow.entity.ProcurementMessageRead;
import com.company.procurement.modules.procurement.workflow.mapper.ProcurementMessageReadMapper;
import com.company.procurement.modules.procurement.workflow.service.AwardPriceSyncService;
import com.company.procurement.modules.procurement.workflow.service.OverPriceReviewService;
import com.company.procurement.modules.procurement.workflow.service.WorkflowService;
import com.company.procurement.modules.procurement.workflow.vo.WorkflowDashboardVO;
import com.company.procurement.modules.procurement.workflow.vo.WorkflowSubmitApproverVO;
import com.company.procurement.modules.procurement.workflow.vo.WorkflowSubmitResultVO;
import com.company.procurement.modules.supplier.entity.Supplier;
import com.company.procurement.modules.supplier.entity.SupplierQuote;
import com.company.procurement.modules.supplier.entity.SupplierQuoteItem;
import com.company.procurement.modules.supplier.mapper.SupplierMapper;
import com.company.procurement.modules.supplier.mapper.SupplierQuoteItemMapper;
import com.company.procurement.modules.supplier.mapper.SupplierQuoteMapper;
import com.company.procurement.modules.supplier.service.SupplierAwardNotificationService;
import com.company.procurement.modules.supplier.service.TemporarySupplierRecommendationService;
import com.company.procurement.modules.supplier.support.SupplierQuoteAccess;
import com.company.procurement.modules.supplier.vo.SupplierQuoteItemVO;
import com.company.procurement.modules.supplier.vo.SupplierQuoteSummaryVO;
import com.company.procurement.modules.supplier.vo.SupplierQuoteVO;
import com.company.procurement.modules.system.user.entity.SysUser;
import com.company.procurement.modules.system.user.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkflowServiceImpl implements WorkflowService {

    private static final String QUOTE_ITEM_STATUS_ACTIVE = "ACTIVE";
    private static final String QUOTE_ITEM_STATUS_WAIVED = "WAIVED";
    private static final String NATIONAL_DEMAND_AGGREGATE_REASON = "需求池全国月度汇总生成";
    private static final int FAST_QUOTE_COUNTDOWN_HOURS = 3;
    private static final String URGENT_DIRECT_AWARD_ENABLED_KEY = "urgent_direct_award_enabled";
    private static final String URGENT_DIRECT_AWARD_THRESHOLD_KEY = "urgent_direct_award_threshold";
    private static final BigDecimal DEFAULT_URGENT_DIRECT_AWARD_THRESHOLD = new BigDecimal("1000");
    private static final BigDecimal URGENT_DIRECT_AWARD_QUOTE_LIMIT_MULTIPLIER = new BigDecimal("1.30");
    private static final String REPLACEMENT_CONFIRM_ROLE_CODE = "REPLACEMENT_CONFIRM";
    private static final String REPLACEMENT_REVIEW_PENDING = "PENDING";
    private static final String REPLACEMENT_REVIEW_APPROVED = "APPROVED";
    private static final String REPLACEMENT_REVIEW_REJECTED = "REJECTED";
    private static final String REPLACEMENT_REVIEW_AUTO_REJECTED = "AUTO_REJECTED";
    private static final String AFTER_SALE_STATUS_APPROVED = "APPROVED";
    private static final String AFTER_SALE_TYPE_NO_REASON_RETURN = "NO_REASON_RETURN";
    private static final String AFTER_SALE_TYPE_RETURN_EXCHANGE = "RETURN_EXCHANGE";
    private static final String AFTER_SALE_TYPE_REPLACE_ITEM = "REPLACE_ITEM";

    private final PurchaseRequestService purchaseRequestService;
    private final PurchaseRequestMapper purchaseRequestMapper;
    private final PurchaseRequestItemMapper purchaseRequestItemMapper;
    private final ApprovalInstanceMapper approvalInstanceMapper;
    private final ApprovalRecordMapper approvalRecordMapper;
    private final ApprovalTaskService approvalTaskService;
    private final ProcurementApprovalRouteResolver approvalRouteResolver;
    private final SysUserMapper userMapper;
    private final ProcurementSettingMapper procurementSettingMapper;
    private final ProcurementSettingsService procurementSettingsService;
    private final SupplierQuoteMapper supplierQuoteMapper;
    private final SupplierQuoteItemMapper supplierQuoteItemMapper;
    private final ProcurementAfterSaleMapper procurementAfterSaleMapper;
    private final SupplierMapper supplierMapper;
    private final SupplierAwardNotificationService awardNotificationService;
    private final TemporarySupplierRecommendationService temporarySupplierRecommendationService;
    private final PurchaseRequestNoService requestNoService;
    private final ProcurementMessageReadMapper messageReadMapper;
    private final AwardPriceSyncService awardPriceSyncService;
    private final OverPriceReviewService overPriceReviewService;

    @Override
    public PageResult<PurchaseRequestVO> pageVisibleRequests(PurchaseRequestPageQueryDTO queryDTO) {
        AuthUser authUser = AuthContextHolder.requireUser();
        ensureNonSupplier(authUser);

        LambdaQueryWrapper<PurchaseRequest> wrapper = buildRequestQuery(queryDTO);
        excludeNationalAggregates(wrapper);

        if (DataScopeHelper.isSystemAdmin(authUser)) {
            Page<PurchaseRequest> page = purchaseRequestMapper.selectPage(new Page<>(queryDTO.getPageNo(), queryDTO.getPageSize()), wrapper);
            List<PurchaseRequestVO> records = page.getRecords().stream()
                .map(request -> detailForAuth(request, authUser))
                .toList();
            return PageResult.of(page.getCurrent(), page.getSize(), page.getTotal(), records);
        }

        List<PurchaseRequest> visibleRequests = purchaseRequestMapper.selectList(wrapper).stream()
            .filter(request -> canViewRequest(request, authUser))
            .toList();
        List<PurchaseRequestVO> records = pageSlice(visibleRequests, queryDTO.getPageNo(), queryDTO.getPageSize()).stream()
            .map(request -> detailForAuth(request, authUser))
            .toList();
        return PageResult.of(queryDTO.getPageNo(), queryDTO.getPageSize(), visibleRequests.size(), records);
    }

    @Override
    public List<PurchaseRequestVO> rejectedItemMessages() {
        AuthUser authUser = AuthContextHolder.requireUser();
        ensureNonSupplier(authUser);
        if (authUser.getUserId() == null) {
            return List.of();
        }

        List<PurchaseRequestItem> rejectedItems = purchaseRequestItemMapper.selectList(new LambdaQueryWrapper<PurchaseRequestItem>()
            .eq(PurchaseRequestItem::getStatus, ProcurementWorkflow.ITEM_STATUS_REJECTED)
            .orderByDesc(PurchaseRequestItem::getUpdatedAt)
            .orderByDesc(PurchaseRequestItem::getId)
            .last("LIMIT 500"));
        if (rejectedItems.isEmpty()) {
            return List.of();
        }

        Map<Long, PurchaseRequest> requestCache = new LinkedHashMap<>();
        Map<Long, Map<Long, PurchaseRequestItem>> itemsBySourceRequestId = new LinkedHashMap<>();
        for (PurchaseRequestItem item : rejectedItems) {
            PurchaseRequest parentRequest = cachedRequest(requestCache, item.getPurchaseRequestId());
            Long sourceRequestId = item.getSourceRequestId() == null ? item.getPurchaseRequestId() : item.getSourceRequestId();
            PurchaseRequest sourceRequest = cachedRequest(requestCache, sourceRequestId);
            if (sourceRequest == null
                || ProcurementWorkflow.STATUS_CANCELLED.equals(sourceRequest.getStatus())
                || ProcurementWorkflow.STATUS_TERMINATED.equals(sourceRequest.getStatus())
                || !Objects.equals(sourceRequest.getApplicantUserId(), authUser.getUserId())) {
                continue;
            }
            if (parentRequest != null && !canReadRejectedItemMessage(parentRequest, sourceRequest, authUser)) {
                continue;
            }
            PurchaseRequestItem messageItem = messageRejectedItem(item);
            Long messageItemKey = messageItem.getId() == null ? item.getId() : messageItem.getId();
            itemsBySourceRequestId
                .computeIfAbsent(sourceRequestId, key -> new LinkedHashMap<>())
                .putIfAbsent(messageItemKey, messageItem);
            if (itemsBySourceRequestId.size() >= 20) {
                break;
            }
        }

        List<PurchaseRequestVO> messages = new ArrayList<>();
        for (Map.Entry<Long, Map<Long, PurchaseRequestItem>> entry : itemsBySourceRequestId.entrySet()) {
            PurchaseRequest sourceRequest = cachedRequest(requestCache, entry.getKey());
            if (sourceRequest == null) {
                continue;
            }
            PurchaseRequestVO vo = purchaseRequestService.detail(sourceRequest.getId());
            List<PurchaseRequestItem> messageItems = new ArrayList<>(entry.getValue().values());
            List<PurchaseRequestItemVO> rejectedItemVOs = messageItems.stream()
                .map(this::toPurchaseRequestItemVO)
                .toList();
            vo.setItems(rejectedItemVOs);
            vo.setTotalAmount(rejectedItemVOs.stream()
                .map(item -> item.getLineAmount() == null ? BigDecimal.ZERO : item.getLineAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP));
            messageItems.stream()
                .map(PurchaseRequestItem::getUpdatedAt)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .ifPresent(vo::setUpdatedAt);
            messages.add(vo);
        }
        return messages;
    }

    @Override
    public List<String> rejectedMessageReadKeys() {
        AuthUser authUser = AuthContextHolder.requireUser();
        ensureNonSupplier(authUser);
        if (authUser.getUserId() == null) {
            return List.of();
        }
        return messageReadMapper.selectList(new LambdaQueryWrapper<ProcurementMessageRead>()
                .eq(ProcurementMessageRead::getUserId, authUser.getUserId())
                .orderByDesc(ProcurementMessageRead::getUpdatedAt)
                .last("LIMIT 1000"))
            .stream()
            .map(ProcurementMessageRead::getMessageKey)
            .filter(StringUtils::hasText)
            .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markRejectedMessagesRead(List<String> messageKeys) {
        AuthUser authUser = AuthContextHolder.requireUser();
        ensureNonSupplier(authUser);
        if (authUser.getUserId() == null || messageKeys == null || messageKeys.isEmpty()) {
            return;
        }
        for (String messageKey : normalizeMessageKeys(messageKeys)) {
            messageReadMapper.upsertRead(authUser.getUserId(), messageKey);
        }
    }

    @Override
    public List<String> rejectedMessageClearedKeys() {
        AuthUser authUser = AuthContextHolder.requireUser();
        ensureNonSupplier(authUser);
        if (authUser.getUserId() == null) {
            return List.of();
        }
        return messageReadMapper.selectList(new LambdaQueryWrapper<ProcurementMessageRead>()
                .eq(ProcurementMessageRead::getUserId, authUser.getUserId())
                .eq(ProcurementMessageRead::getActionType, ProcurementMessageRead.ACTION_CLEARED)
                .orderByDesc(ProcurementMessageRead::getUpdatedAt)
                .last("LIMIT 1000"))
            .stream()
            .map(ProcurementMessageRead::getMessageKey)
            .filter(StringUtils::hasText)
            .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void clearRejectedMessages(List<String> messageKeys) {
        AuthUser authUser = AuthContextHolder.requireUser();
        ensureNonSupplier(authUser);
        if (authUser.getUserId() == null || messageKeys == null || messageKeys.isEmpty()) {
            return;
        }
        for (String messageKey : normalizeMessageKeys(messageKeys)) {
            messageReadMapper.upsertCleared(authUser.getUserId(), messageKey);
        }
    }

    private List<String> normalizeMessageKeys(List<String> messageKeys) {
        return messageKeys.stream()
            .filter(StringUtils::hasText)
            .map(String::trim)
            .filter(key -> key.length() <= 255)
            .distinct()
            .limit(100)
            .toList();
    }

    @Override
    public PurchaseRequestVO detailVisibleRequest(Long id) {
        AuthUser authUser = AuthContextHolder.requireUser();
        ensureNonSupplier(authUser);

        PurchaseRequest request = purchaseRequestMapper.selectById(id);
        if (request == null) {
            throw new BizException("PURCHASE_REQUEST_NOT_FOUND", "Purchase request not found");
        }
        ensureRequestVisible(request, authUser);
        return detailForAuth(request, authUser);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createRequest(PurchaseRequestCreateDTO dto) {
        AuthUser authUser = AuthContextHolder.requireUser();
        ensureRequestCreator(authUser);
        dto.setApplicantUserId(authUser.getUserId());
        dto.setPurchaserUserId(authUser.getUserId());
        SysUser applicant = userMapper.selectById(authUser.getUserId());
        applyApplicantRouting(dto, applicant, authUser);
        procurementSettingsService.validateRequest(authUser, dto.getDeptCode(), dto.getDeptName(), dto.getTotalAmount(), dto.getUrgencyLevel(), null);
        return purchaseRequestService.create(dto);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRequest(Long id, PurchaseRequestUpdateDTO dto) {
        AuthUser authUser = AuthContextHolder.requireUser();
        ensureRequestCreator(authUser);

        PurchaseRequest request = purchaseRequestMapper.selectById(id);
        if (request == null) {
            throw new BizException("PURCHASE_REQUEST_NOT_FOUND", "Purchase request not found");
        }
        if (!DataScopeHelper.isSystemAdmin(authUser) && !authUser.getUserId().equals(request.getApplicantUserId())) {
            throw new BizException("FORBIDDEN", "Only the applicant can edit this request");
        }

        dto.setApplicantUserId(request.getApplicantUserId());
        dto.setPurchaserUserId(request.getPurchaserUserId());
        SysUser applicant = userMapper.selectById(request.getApplicantUserId());
        applyApplicantRouting(dto, applicant, authUser, request);
        procurementSettingsService.validateRequest(authUser, dto.getDeptCode(), dto.getDeptName(), dto.getTotalAmount(), dto.getUrgencyLevel(), id);
        purchaseRequestService.update(id, dto);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void closeReturnedRequest(Long id) {
        AuthUser authUser = AuthContextHolder.requireUser();
        ensureNonSupplier(authUser);

        PurchaseRequest request = purchaseRequestMapper.selectById(id);
        if (request == null) {
            throw new BizException("PURCHASE_REQUEST_NOT_FOUND", "Purchase request not found");
        }
        if (!DataScopeHelper.isSystemAdmin(authUser) && !Objects.equals(authUser.getUserId(), request.getApplicantUserId())) {
            throw new BizException("FORBIDDEN", "Only the applicant can close this returned request");
        }
        if (!ProcurementWorkflow.STATUS_RETURNED.equals(request.getStatus())) {
            throw new BizException("PURCHASE_REQUEST_STATUS_INVALID", "Only returned requests can be closed");
        }

        ApprovalInstance instance = latestApprovalInstance(id);
        if (instance == null) {
            throw new BizException("APPROVAL_INSTANCE_NOT_FOUND", "Approval instance not found");
        }

        LocalDateTime closedAt = LocalDateTime.now();
        String closeReason = DataScopeHelper.isSystemAdmin(authUser)
            ? "管理员确认不再继续采购，关闭退回申请"
            : "申请人确认不再继续采购，关闭退回申请";
        int currentNodeNo = request.getCurrentNodeNo() == null ? 0 : request.getCurrentNodeNo();

        approvalTaskService.closePendingTasks(instance.getId(), "RETURNED_REQUEST_CLOSED");

        ApprovalRecord record = new ApprovalRecord();
        record.setInstanceId(instance.getId());
        record.setPurchaseRequestId(request.getId());
        record.setNodeNo(currentNodeNo);
        record.setNodeName("关闭退回申请");
        record.setApproverUserId(authUser.getUserId());
        record.setApproverRoleCode(resolveSubmitterRoleCode(authUser));
        record.setAction(ProcurementWorkflow.ACTION_CLOSE);
        record.setFromStatus(request.getApprovalStatus());
        record.setToStatus(ProcurementWorkflow.APPROVAL_TERMINATED);
        record.setApprovalComment(closeReason);
        record.setOperatedAt(closedAt);
        record.setSubmitRound(request.getSubmitCount() == null ? 0 : request.getSubmitCount());
        record.setIsFinalNode(1);
        record.setCanResubmit(0);
        record.setTransferToUserId(null);
        record.setStatus("ACTIVE");

        instance.setStatus(ProcurementWorkflow.APPROVAL_TERMINATED);
        instance.setCurrentApproverId(null);
        instance.setLastAction(ProcurementWorkflow.ACTION_CLOSE);
        instance.setLastActionBy(authUser.getUserId());
        instance.setLastActionAt(closedAt);
        instance.setEndedAt(closedAt);
        instance.setRemark(appendRemark(instance.getRemark(), closeReason));

        request.setApprovalStatus(ProcurementWorkflow.APPROVAL_TERMINATED);
        request.setStatus(ProcurementWorkflow.STATUS_CANCELLED);
        request.setCancelledAt(closedAt);
        request.setCancelReason(closeReason);
        request.setRemark(appendRemark(request.getRemark(), closeReason));

        approvalRecordMapper.insert(record);
        approvalInstanceMapper.updateById(instance);
        purchaseRequestMapper.updateById(request);
    }

    private void applyApplicantRouting(PurchaseRequestCreateDTO dto, SysUser applicant, AuthUser authUser) {
        Long regionId = firstLong(dto.getRegionId(), applicant == null ? null : applicant.getRegionId(), authUser.getRegionId());
        String deptCode = firstText(dto.getDeptCode(), applicant == null ? null : applicant.getDeptCode());
        String deptName = firstText(dto.getDeptName(), applicant == null ? null : applicant.getDeptName());
        dto.setRegionId(regionId);
        dto.setDeptCode(deptCode);
        dto.setDeptName(deptName);
        ensureValidDepartmentSelection(regionId, deptCode, deptName);
        dto.setBuildingScopeId(null);
        dto.setApplicantSubjectId(null);
        dto.setApprovalPolicyId(null);
        dto.setSpecialtyCode(null);
        dto.setProfessionalReviewRequired(null);
    }

    private void applyApplicantRouting(PurchaseRequestUpdateDTO dto, SysUser applicant, AuthUser authUser, PurchaseRequest request) {
        Long regionId = firstLong(dto.getRegionId(), request.getRegionId(), applicant == null ? null : applicant.getRegionId(), authUser.getRegionId());
        String deptCode = firstText(dto.getDeptCode(), request.getDeptCode(), applicant == null ? null : applicant.getDeptCode());
        String deptName = firstText(dto.getDeptName(), request.getDeptName(), applicant == null ? null : applicant.getDeptName());
        dto.setRegionId(regionId);
        dto.setDeptCode(deptCode);
        dto.setDeptName(deptName);
        ensureValidDepartmentSelection(regionId, deptCode, deptName);
        dto.setBuildingScopeId(null);
        dto.setApplicantSubjectId(null);
        dto.setApprovalPolicyId(null);
        dto.setSpecialtyCode(null);
        dto.setProfessionalReviewRequired(null);
    }

    private void ensureValidDepartmentSelection(Long regionId, String deptCode, String deptName) {
        if (!StringUtils.hasText(deptCode) && !StringUtils.hasText(deptName)) {
            return;
        }
        PurchaseRequest probe = new PurchaseRequest();
        probe.setRegionId(regionId);
        probe.setDeptCode(deptCode);
        probe.setDeptName(deptName);
        if (approvalRouteResolver.findScope(probe) == null && approvalRouteResolver.findRoute(probe) == null) {
            throw new BizException("PROCUREMENT_DEPARTMENT_INVALID", "Selected procurement department is not enabled");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public WorkflowSubmitResultVO previewSubmitRequest(Long id) {
        return purchaseRequestService.previewSubmit(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkflowSubmitResultVO submitRequest(Long id) {
        return purchaseRequestService.submit(id);
    }

    @Override
    public PageResult<PurchaseRequestVO> pageApprovalTasks(PurchaseRequestPageQueryDTO queryDTO) {
        AuthUser authUser = AuthContextHolder.requireUser();
        ensureNonSupplier(authUser);

        LambdaQueryWrapper<PurchaseRequest> wrapper = buildRequestQuery(queryDTO);
        wrapper.eq(PurchaseRequest::getStatus, ProcurementWorkflow.STATUS_PENDING_APPROVAL);
        wrapper.eq(PurchaseRequest::getApprovalStatus, ProcurementWorkflow.APPROVAL_PENDING);

        wrapper.last("LIMIT 1000");
        List<PurchaseRequest> visibleRequests = purchaseRequestMapper.selectList(wrapper).stream()
            .filter(request -> DataScopeHelper.isSystemAdmin(authUser) || canApproveRequest(authUser, request))
            .toList();
        List<PurchaseRequestVO> records = pageSlice(visibleRequests, queryDTO.getPageNo(), queryDTO.getPageSize()).stream()
            .map(request -> detailForAuth(request, authUser))
            .toList();
        return PageResult.of(queryDTO.getPageNo(), queryDTO.getPageSize(), visibleRequests.size(), records);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkflowSubmitResultVO approveRequest(Long purchaseRequestId, WorkflowApprovalActionDTO dto) {
        AuthUser authUser = AuthContextHolder.requireUser();
        ensureNonSupplier(authUser);

        PurchaseRequest request = purchaseRequestMapper.selectById(purchaseRequestId);
        if (request == null) {
            throw new BizException("PURCHASE_REQUEST_NOT_FOUND", "Purchase request not found");
        }
        ApprovalInstance instance = approvalInstanceMapper.selectOne(new LambdaQueryWrapper<ApprovalInstance>()
            .eq(ApprovalInstance::getPurchaseRequestId, purchaseRequestId)
            .orderByDesc(ApprovalInstance::getId)
            .last("LIMIT 1"));
        if (instance == null) {
            throw new BizException("APPROVAL_INSTANCE_NOT_FOUND", "Approval instance not found");
        }
        if (!ProcurementWorkflow.STATUS_PENDING_APPROVAL.equals(request.getStatus())) {
            throw new BizException("PURCHASE_REQUEST_STATUS_INVALID", "Current request is not pending approval");
        }

        String action = normalizeAction(dto.getAction());
        if (!ProcurementWorkflow.ACTION_APPROVE.equals(action)
            && !ProcurementWorkflow.ACTION_RETURN.equals(action)
            && !ProcurementWorkflow.ACTION_SAVE.equals(action)
            && !ProcurementWorkflow.ACTION_TERMINATE.equals(action)) {
            throw new BizException("APPROVAL_ACTION_INVALID", "Unsupported approval action");
        }
        if (ProcurementWorkflow.ACTION_RETURN.equals(action) && isNationalDemandAggregate(request)) {
            throw new BizException("APPROVAL_RETURN_NOT_ALLOWED", "系统发起的全国月度汇总单不支持退回，请选择通过或加签处理");
        }

        int currentNodeNo = request.getCurrentNodeNo() == null || request.getCurrentNodeNo() < 1
            ? approvalRouteResolver.firstDemandNodeNo(request)
            : request.getCurrentNodeNo();
        boolean overPriceFloorReview = isOverPriceFloorReview(instance, currentNodeNo);
        if (overPriceFloorReview && ProcurementWorkflow.ACTION_RETURN.equals(action)) {
            throw new BizException("OVER_PRICE_REVIEW_RETURN_NOT_ALLOWED", "超价复核只能通过或驳回超价商品，不能退回整单");
        }
        if (overPriceFloorReview && ProcurementWorkflow.ACTION_APPROVE.equals(action)) {
            validateOverPriceReviewDecision(request, dto);
        }
        String expectedRoleCode = ProcurementWorkflow.roleCodeForNode(currentNodeNo);
        if (ProcurementWorkflow.ACTION_TERMINATE.equals(action)) {
            ensureApplicantCanTerminate(request, authUser);
        } else if (!DataScopeHelper.isSystemAdmin(authUser)) {
            boolean taskAccess = approvalTaskService.hasPendingTaskForUser(instance, request, authUser);
            boolean routeAccess = approvalTaskService.canActOnCurrentTask(instance, request, authUser)
                && approvalRouteResolver.canApprove(request, authUser, currentNodeNo, instance.getCurrentApproverId());
            if (!taskAccess && !routeAccess) {
                throw new BizException("FORBIDDEN", "Current account is not configured as the approver for this building and stage");
            }
        }
        if (ProcurementWorkflow.ACTION_APPROVE.equals(action)
            && !hasRejectedItemDecision(dto)
            && !hasAwardSelection(request)) {
            validateActiveItemsReadyForApproval(request, activeRequestItems(request.getId()));
        }

        LocalDateTime actionTime = LocalDateTime.now();
        Long recordApproverUserId = approvalTaskService.resolveActionCandidateUserId(instance, request, authUser);
        ApprovalRecord record = new ApprovalRecord();
        record.setInstanceId(instance.getId());
        record.setPurchaseRequestId(request.getId());
        record.setNodeNo(currentNodeNo);
        record.setNodeName(overPriceFloorReview ? "楼长超价复核" : ProcurementWorkflow.nodeNameForNode(currentNodeNo));
        record.setApproverUserId(recordApproverUserId);
        record.setApproverRoleCode(expectedRoleCode);
        record.setAction(action);
        record.setFromStatus(request.getApprovalStatus());
        record.setApprovalComment(dto.getApprovalComment());
        record.setOperatedAt(actionTime);
        record.setSubmitRound(request.getSubmitCount() == null ? 1 : request.getSubmitCount());
        record.setStatus("ACTIVE");

        instance.setLastAction(action);
        instance.setLastActionBy(recordApproverUserId);
        instance.setLastActionAt(actionTime);

        if (ProcurementWorkflow.ACTION_SAVE.equals(action)) {
            record.setToStatus(ProcurementWorkflow.APPROVAL_PENDING);
            record.setIsFinalNode(0);
            record.setCanResubmit(0);
            record.setTransferToUserId(instance.getCurrentApproverId());
            approvalRecordMapper.insert(record);
            approvalInstanceMapper.updateById(instance);
            return emptySubmitResult(request);
        }

        if (ProcurementWorkflow.ACTION_TERMINATE.equals(action)) {
            approvalTaskService.closePendingTasks(instance.getId(), "FLOW_TERMINATED");
            record.setToStatus(ProcurementWorkflow.APPROVAL_TERMINATED);
            record.setIsFinalNode(1);
            record.setCanResubmit(0);
            record.setTransferToUserId(null);

            instance.setStatus(ProcurementWorkflow.APPROVAL_TERMINATED);
            instance.setCurrentApproverId(null);
            instance.setEndedAt(actionTime);

            request.setApprovalStatus(ProcurementWorkflow.APPROVAL_TERMINATED);
            request.setStatus(ProcurementWorkflow.STATUS_TERMINATED);
            request.setCurrentNodeNo(currentNodeNo);
            request.setRemark(appendRemark(request.getRemark(), "流程已终止"));

            approvalRecordMapper.insert(record);
            approvalInstanceMapper.updateById(instance);
            purchaseRequestMapper.updateById(request);
            return emptySubmitResult(request);
        }

        WorkflowSubmitResultVO nextSubmitResult = emptySubmitResult(request);

        if (overPriceFloorReview && ProcurementWorkflow.ACTION_APPROVE.equals(action)) {
            applyItemDecisions(request, dto, currentNodeNo, authUser);
        }

        boolean currentLevelCompleted = approvalTaskService.completeTask(
            instance,
            request,
            authUser,
            action,
            dto.getApprovalComment(),
            actionTime
        );

        if (ProcurementWorkflow.ACTION_APPROVE.equals(action) && !currentLevelCompleted) {
            record.setToStatus(ProcurementWorkflow.APPROVAL_PENDING);
            record.setIsFinalNode(0);
            record.setCanResubmit(0);
            record.setTransferToUserId(instance.getCurrentApproverId());
            approvalRecordMapper.insert(record);
            approvalInstanceMapper.updateById(instance);
            return nextSubmitResult;
        }

        if (ProcurementWorkflow.ACTION_APPROVE.equals(action)) {
            if (currentNodeNo == ProcurementWorkflow.NODE_APPLICANT_REPLACEMENT_REVIEW) {
                ProcurementApprovalRouteResolver.ApprovalAssignment nextAssignment =
                    startQuoteApprovalAfterReplacementConfirm(
                        request,
                        instance,
                        dto.getApprovalComment(),
                        authUser.getUserId(),
                        actionTime
                    );

                record.setToStatus(ProcurementWorkflow.APPROVAL_PENDING);
                record.setIsFinalNode(0);
                record.setCanResubmit(0);
                record.setTransferToUserId(nextAssignment.currentApproverId());
                nextSubmitResult = buildSubmitResult(request, nextAssignment);
            } else if (currentNodeNo < ProcurementWorkflow.NODE_FLOOR_LEADER_DEMAND_REVIEW) {
                int nextNodeNo = approvalRouteResolver.nextActiveNodeNo(
                    request,
                    currentNodeNo,
                    ProcurementWorkflow.NODE_FLOOR_LEADER_DEMAND_REVIEW,
                    authUser.getUserId()
                );
                if (nextNodeNo <= 0) {
                    nextNodeNo = ProcurementWorkflow.NODE_FLOOR_LEADER_DEMAND_REVIEW;
                }
                ProcurementApprovalRouteResolver.ApprovalAssignment nextAssignment =
                    approvalRouteResolver.resolveAssignmentForNode(request, nextNodeNo);

                record.setToStatus(ProcurementWorkflow.APPROVAL_PENDING);
                record.setIsFinalNode(0);
                record.setCanResubmit(0);
                record.setTransferToUserId(nextAssignment.currentApproverId());

                instance.setStatus(ProcurementWorkflow.APPROVAL_PENDING);
                instance.setCurrentNodeNo(nextNodeNo);
                instance.setCurrentLevelNo(nextAssignment.levelNo());
                instance.setCurrentLevelCode(nextAssignment.levelCode());
                instance.setCurrentPassRule(nextAssignment.passRule());
                instance.setTotalNodeNo(ProcurementWorkflow.NODE_FLOOR_LEADER_DEMAND_REVIEW);
                instance.setCurrentApproverId(nextAssignment.currentApproverId());
                instance.setEndedAt(null);

                request.setApprovalStatus(ProcurementWorkflow.APPROVAL_PENDING);
                request.setStatus(ProcurementWorkflow.STATUS_PENDING_APPROVAL);
                request.setCurrentNodeNo(nextNodeNo);
                approvalTaskService.createTasks(instance, request, nextAssignment);
                nextSubmitResult = buildSubmitResult(request, nextAssignment);
            } else if (currentNodeNo == ProcurementWorkflow.NODE_FLOOR_LEADER_DEMAND_REVIEW
                && !overPriceFloorReview) {
                applyItemDecisions(request, dto, currentNodeNo, authUser);
                List<PurchaseRequestItem> activeItems = activeRequestItems(request.getId());
                BigDecimal effectiveAmount = sumRequestItemsAmount(activeItems);
                if (activeItems.isEmpty()) {
                    record.setToStatus(ProcurementWorkflow.APPROVAL_REJECTED);
                    record.setIsFinalNode(1);
                    record.setCanResubmit(0);
                    record.setTransferToUserId(null);

                    instance.setStatus(ProcurementWorkflow.APPROVAL_REJECTED);
                    instance.setCurrentApproverId(null);
                    instance.setEndedAt(actionTime);

                    request.setApprovalStatus(ProcurementWorkflow.APPROVAL_REJECTED);
                    request.setStatus(ProcurementWorkflow.STATUS_REJECTED);
                    request.setRejectedAt(actionTime);
                    request.setRemark(appendRemark(request.getRemark(), "全部物品已被楼长驳回"));
                    approvalRecordMapper.insert(record);
                    approvalInstanceMapper.updateById(instance);
                    purchaseRequestMapper.updateById(request);
                    return nextSubmitResult;
                }
                validateActiveItemsReadyForApproval(request, activeItems);

                record.setToStatus(ProcurementWorkflow.APPROVAL_APPROVED);
                record.setIsFinalNode(1);
                record.setCanResubmit(0);
                record.setTransferToUserId(null);

                instance.setStatus(ProcurementWorkflow.APPROVAL_APPROVED);
                instance.setCurrentApproverId(null);
                instance.setEndedAt(actionTime);

                request.setApprovalStatus(ProcurementWorkflow.APPROVAL_APPROVED);
                request.setStatus(isEmergencyRequest(request)
                    ? ProcurementWorkflow.STATUS_QUOTING
                    : ProcurementWorkflow.STATUS_DEMAND_POOL);
                request.setTotalAmount(effectiveAmount);
                request.setCurrentNodeNo(currentNodeNo);
                boolean deferredToNextMonth = !isEmergencyRequest(request)
                    && approvalTaskService.hasOverdueApprovedTask(request.getId());
                request.setApprovedAt(deferredToNextMonth ? nextMonthStart(actionTime) : actionTime);
                if (deferredToNextMonth) {
                    request.setRemark(appendRemark(request.getRemark(), "审批超期后同意，采购执行顺延至下月需求池批量推送"));
                }
                applyUrgentDirectAwardIfEligible(request, effectiveAmount, actionTime);
            } else {
                boolean quoteReview = hasAwardSelection(request);
                if (!overPriceFloorReview) {
                    applyItemDecisions(request, dto, currentNodeNo, authUser);
                }
                if (quoteReview) {
                    syncAwardedQuoteItemsWithActiveRequestItems(request);
                }
                List<PurchaseRequestItem> activeItems = activeRequestItems(request.getId());
                BigDecimal effectiveAmount = sumRequestItemsAmount(activeItems);
                if (activeItems.isEmpty()) {
                    record.setToStatus(ProcurementWorkflow.APPROVAL_REJECTED);
                    record.setIsFinalNode(1);
                    record.setCanResubmit(0);
                    record.setTransferToUserId(null);

                    instance.setStatus(ProcurementWorkflow.APPROVAL_REJECTED);
                    instance.setCurrentApproverId(null);
                    instance.setEndedAt(actionTime);

                    request.setApprovalStatus(ProcurementWorkflow.APPROVAL_REJECTED);
                    request.setStatus(ProcurementWorkflow.STATUS_REJECTED);
                    request.setRejectedAt(actionTime);
                    request.setRemark(appendRemark(request.getRemark(), "全部物品已被审批驳回"));
                    approvalRecordMapper.insert(record);
                    approvalInstanceMapper.updateById(instance);
                    purchaseRequestMapper.updateById(request);
                    return nextSubmitResult;
                }
                if (!quoteReview) {
                    validateActiveItemsReadyForApproval(request, activeItems);
                } else {
                    awardPriceSyncService.syncAwardedPrices(request);
                }

                int totalNodeNo = quoteReview
                    ? totalNationalAggregateApprovalNodes(request, quoteApprovalAmount(request), true)
                    : totalNationalAggregateApprovalNodes(request, effectiveAmount, false);
                int nextNodeNo = isNationalDemandAggregate(request)
                    ? nextNationalAggregateApprovalNodeNo(request, currentNodeNo, totalNodeNo)
                    : approvalRouteResolver.nextActiveNodeNo(request, currentNodeNo, totalNodeNo, authUser.getUserId());
                boolean finalApproval = currentNodeNo >= totalNodeNo || nextNodeNo <= 0;
                if (finalApproval) {
                    record.setToStatus(ProcurementWorkflow.APPROVAL_APPROVED);
                    record.setIsFinalNode(1);
                    record.setCanResubmit(0);
                    record.setTransferToUserId(null);

                    instance.setStatus(ProcurementWorkflow.APPROVAL_APPROVED);
                    instance.setCurrentApproverId(null);
                    instance.setEndedAt(actionTime);

                    request.setApprovalStatus(ProcurementWorkflow.APPROVAL_APPROVED);
                    request.setCurrentNodeNo(currentNodeNo);
                    boolean deferredToNextMonth = !quoteReview && shouldDeferApprovedAggregateToNextMonth(request);
                    request.setApprovedAt(deferredToNextMonth ? nextMonthStart(actionTime) : actionTime);
                    if (!quoteReview) {
                        request.setTotalAmount(effectiveAmount);
                    }
                    if (quoteReview) {
                        request.setStatus(ProcurementWorkflow.STATUS_APPROVED);
                        request.setLeaderConfirmStatus(ProcurementWorkflow.LEADER_CONFIRM_STATUS_CONFIRMED);
                        request.setLeaderConfirmedBy(authUser.getUserId());
                        request.setLeaderConfirmedAt(actionTime);
                        markAwardedSuppliersPending(request);
                    } else if (deferredToNextMonth) {
                        request.setStatus(ProcurementWorkflow.STATUS_DEMAND_AGGREGATED);
                        request.setRemark(appendRemark(request.getRemark(), "审批超期后同意，采购执行顺延至下月，无需重新审批"));
                    } else {
                        request.setStatus(ProcurementWorkflow.STATUS_QUOTING);
                    }
                } else {
                    ProcurementApprovalRouteResolver.ApprovalAssignment nextAssignment =
                        resolveNationalAggregateApprovalAssignment(request, nextNodeNo, totalNodeNo);
                    nextNodeNo = nextAssignment.levelNo();
                    totalNodeNo = Math.max(totalNodeNo, nextNodeNo);

                    record.setToStatus(ProcurementWorkflow.APPROVAL_PENDING);
                    record.setIsFinalNode(0);
                    record.setCanResubmit(0);
                    record.setTransferToUserId(nextAssignment.currentApproverId());

                    instance.setStatus(ProcurementWorkflow.APPROVAL_PENDING);
                    instance.setCurrentNodeNo(nextNodeNo);
                    instance.setCurrentLevelNo(nextAssignment.levelNo());
                    instance.setCurrentLevelCode(nextAssignment.levelCode());
                    instance.setCurrentPassRule(nextAssignment.passRule());
                    instance.setTotalNodeNo(totalNodeNo);
                    instance.setCurrentApproverId(nextAssignment.currentApproverId());
                    instance.setEndedAt(null);

                    request.setApprovalStatus(ProcurementWorkflow.APPROVAL_PENDING);
                    request.setStatus(ProcurementWorkflow.STATUS_PENDING_APPROVAL);
                    request.setCurrentNodeNo(nextNodeNo);
                    approvalTaskService.createTasks(instance, request, nextAssignment);
                    nextSubmitResult = buildSubmitResult(request, nextAssignment);
                }
            }
        } else if (ProcurementWorkflow.ACTION_RETURN.equals(action)) {
            Integer targetNodeNo = dto.getTargetNodeNo();
            if (targetNodeNo != null && (targetNodeNo <= 0 || targetNodeNo >= currentNodeNo)) {
                throw new BizException("APPROVAL_TARGET_NODE_INVALID", "Return target must be a previous approval node");
            }
            if (targetNodeNo != null) {
                ProcurementApprovalRouteResolver.ApprovalAssignment targetAssignment =
                    approvalRouteResolver.resolveAssignmentForNode(request, targetNodeNo);

                record.setToStatus(ProcurementWorkflow.APPROVAL_PENDING);
                record.setIsFinalNode(0);
                record.setCanResubmit(1);
                record.setTransferToUserId(targetAssignment.currentApproverId());

                instance.setStatus(ProcurementWorkflow.APPROVAL_PENDING);
                instance.setCurrentNodeNo(targetNodeNo);
                instance.setCurrentLevelNo(targetAssignment.levelNo());
                instance.setCurrentLevelCode(targetAssignment.levelCode());
                instance.setCurrentPassRule(targetAssignment.passRule());
                instance.setCurrentApproverId(targetAssignment.currentApproverId());
                instance.setEndedAt(null);

                request.setApprovalStatus(ProcurementWorkflow.APPROVAL_PENDING);
                request.setStatus(ProcurementWorkflow.STATUS_PENDING_APPROVAL);
                request.setCurrentNodeNo(targetNodeNo);
                approvalTaskService.createTasks(instance, request, targetAssignment);
                nextSubmitResult = buildSubmitResult(request, targetAssignment);
            } else {
                record.setToStatus(ProcurementWorkflow.APPROVAL_RETURNED);
                record.setIsFinalNode(0);
                record.setCanResubmit(1);
                record.setTransferToUserId(null);

                instance.setStatus(ProcurementWorkflow.APPROVAL_RETURNED);
                instance.setCurrentApproverId(null);
                instance.setEndedAt(actionTime);

                request.setApprovalStatus(ProcurementWorkflow.APPROVAL_RETURNED);
                request.setStatus(ProcurementWorkflow.STATUS_RETURNED);
                request.setReturnedAt(actionTime);
            }
        }

        approvalRecordMapper.insert(record);
        updateApprovalInstance(instance);
        purchaseRequestMapper.updateById(request);
        return nextSubmitResult;
    }

    private void updateApprovalInstance(ApprovalInstance instance) {
        approvalInstanceMapper.updateById(instance);
        if (instance.getEndedAt() == null) {
            approvalInstanceMapper.clearEndedAt(instance.getId());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkflowSubmitResultVO confirmReplacementItems(Long purchaseRequestId, boolean replacementApproved, String comment) {
        return confirmReplacementItems(purchaseRequestId, null, replacementApproved, comment);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkflowSubmitResultVO confirmReplacementItems(Long purchaseRequestId,
                                                          List<WorkflowReplacementDecisionDTO> decisions,
                                                          String comment) {
        return confirmReplacementItems(purchaseRequestId, decisions, null, comment);
    }

    private WorkflowSubmitResultVO confirmReplacementItems(Long purchaseRequestId,
                                                           List<WorkflowReplacementDecisionDTO> decisions,
                                                           Boolean allReplacementApproved,
                                                           String comment) {
        AuthUser authUser = AuthContextHolder.requireUser();
        ensureNonSupplier(authUser);

        PurchaseRequest request = purchaseRequestMapper.selectById(purchaseRequestId);
        if (request == null) {
            throw new BizException("PURCHASE_REQUEST_NOT_FOUND", "Purchase request not found");
        }
        ApprovalInstance instance = latestApprovalInstance(purchaseRequestId);
        if (instance == null) {
            throw new BizException("APPROVAL_INSTANCE_NOT_FOUND", "Approval instance not found");
        }
        if (!ProcurementWorkflow.STATUS_PENDING_APPROVAL.equals(request.getStatus())
            || !ProcurementWorkflow.APPROVAL_PENDING.equals(request.getApprovalStatus())
            || !Objects.equals(request.getCurrentNodeNo(), ProcurementWorkflow.NODE_APPLICANT_REPLACEMENT_REVIEW)) {
            throw new BizException("PURCHASE_REQUEST_STATUS_INVALID", "当前申请不在申请人替换商品确认节点");
        }
        if (!DataScopeHelper.isSystemAdmin(authUser)) {
            boolean taskAccess = approvalTaskService.hasPendingTaskForUser(instance, request, authUser);
            boolean applicantAccess = Objects.equals(authUser.getUserId(), request.getApplicantUserId());
            boolean routeAccess = approvalTaskService.canActOnCurrentTask(instance, request, authUser)
                && approvalRouteResolver.canApprove(request, authUser, ProcurementWorkflow.NODE_APPLICANT_REPLACEMENT_REVIEW, instance.getCurrentApproverId());
            if (!taskAccess && !applicantAccess && !routeAccess) {
                throw new BizException("FORBIDDEN", "Only the purchase applicant can confirm replacement items");
            }
        }

        List<SupplierQuoteItem> replacementItems = awardedReplacementQuoteItems(request.getId());
        Map<Long, Boolean> replacementDecisionMap = resolveReplacementDecisionMap(replacementItems, decisions, allReplacementApproved);
        boolean everyReplacementApproved = replacementDecisionMap.values().stream().allMatch(Boolean.TRUE::equals);
        boolean everyReplacementRejected = replacementDecisionMap.values().stream().noneMatch(Boolean.TRUE::equals);
        LocalDateTime actionTime = LocalDateTime.now();
        String actionComment = replacementActionComment(comment, replacementItems, replacementDecisionMap);
        Long recordApproverUserId = approvalTaskService.resolveActionCandidateUserId(instance, request, authUser);
        boolean currentLevelCompleted = approvalTaskService.completeTask(
            instance,
            request,
            authUser,
            everyReplacementRejected ? ProcurementWorkflow.ACTION_REJECT : ProcurementWorkflow.ACTION_APPROVE,
            actionComment,
            actionTime
        );

        ApprovalRecord record = new ApprovalRecord();
        record.setInstanceId(instance.getId());
        record.setPurchaseRequestId(request.getId());
        record.setNodeNo(ProcurementWorkflow.NODE_APPLICANT_REPLACEMENT_REVIEW);
        record.setNodeName(ProcurementWorkflow.nodeNameForNode(ProcurementWorkflow.NODE_APPLICANT_REPLACEMENT_REVIEW));
        record.setApproverUserId(recordApproverUserId);
        record.setApproverRoleCode(REPLACEMENT_CONFIRM_ROLE_CODE);
        record.setAction(everyReplacementApproved
            ? "APPROVE_REPLACEMENT"
            : everyReplacementRejected ? "REJECT_REPLACEMENT" : "CONFIRM_REPLACEMENT_BY_ITEM");
        record.setFromStatus(request.getApprovalStatus());
        record.setApprovalComment(actionComment);
        record.setOperatedAt(actionTime);
        record.setSubmitRound(request.getSubmitCount() == null ? 1 : request.getSubmitCount());
        record.setStatus("ACTIVE");

        if (!everyReplacementRejected && !currentLevelCompleted) {
            record.setToStatus(ProcurementWorkflow.APPROVAL_PENDING);
            record.setIsFinalNode(0);
            record.setCanResubmit(0);
            record.setTransferToUserId(instance.getCurrentApproverId());
            approvalRecordMapper.insert(record);
            approvalInstanceMapper.updateById(instance);
            return emptySubmitResult(request);
        }

        updateAwardedReplacementReviewStatus(replacementItems, replacementDecisionMap, recordApproverUserId, actionTime, actionComment);
        ProcurementApprovalRouteResolver.ApprovalAssignment nextAssignment =
            startQuoteApprovalAfterReplacementConfirm(
                request,
                instance,
                actionComment,
                recordApproverUserId,
                actionTime
            );
        record.setToStatus(ProcurementWorkflow.APPROVAL_PENDING);
        record.setIsFinalNode(0);
        record.setCanResubmit(0);
        record.setTransferToUserId(nextAssignment.currentApproverId());

        approvalRecordMapper.insert(record);
        updateApprovalInstance(instance);
        purchaseRequestMapper.updateById(request);
        return buildSubmitResult(request, nextAssignment);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addSignRequest(Long purchaseRequestId, Long addSignUserId, String comment) {
        AuthUser authUser = AuthContextHolder.requireUser();
        ensureNonSupplier(authUser);

        PurchaseRequest request = purchaseRequestMapper.selectById(purchaseRequestId);
        if (request == null) {
            throw new BizException("PURCHASE_REQUEST_NOT_FOUND", "Purchase request not found");
        }
        if (!ProcurementWorkflow.STATUS_PENDING_APPROVAL.equals(request.getStatus())) {
            throw new BizException("PURCHASE_REQUEST_STATUS_INVALID", "Current request is not pending approval");
        }
        ApprovalInstance instance = latestApprovalInstance(purchaseRequestId);
        if (instance == null) {
            throw new BizException("APPROVAL_INSTANCE_NOT_FOUND", "Approval instance not found");
        }
        SysUser addSignUser = userMapper.selectById(addSignUserId);
        if (addSignUser == null || addSignUser.getStatus() == null || addSignUser.getStatus() != 1) {
            throw new BizException("ADD_SIGN_TARGET_INVALID", "Add-sign user does not exist or is disabled");
        }
        if (!DataScopeHelper.isSystemAdmin(authUser) && !canApproveRequest(authUser, request)) {
            throw new BizException("FORBIDDEN", "Only current approvers can add signers");
        }

        approvalTaskService.addSignTask(instance, request, addSignUserId, authUser.getUserId());

        LocalDateTime actionTime = LocalDateTime.now();
        int currentNodeNo = request.getCurrentNodeNo() == null || request.getCurrentNodeNo() < 1
            ? approvalRouteResolver.firstDemandNodeNo(request)
            : request.getCurrentNodeNo();
        ApprovalRecord record = new ApprovalRecord();
        record.setInstanceId(instance.getId());
        record.setPurchaseRequestId(request.getId());
        record.setNodeNo(currentNodeNo);
        record.setNodeName(ProcurementWorkflow.nodeNameForNode(currentNodeNo));
        record.setApproverUserId(authUser.getUserId());
        record.setApproverRoleCode(ProcurementWorkflow.roleCodeForNode(currentNodeNo));
        record.setAction("ADD_SIGN");
        record.setFromStatus(request.getApprovalStatus());
        record.setToStatus(ProcurementWorkflow.APPROVAL_PENDING);
        record.setApprovalComment(comment);
        record.setOperatedAt(actionTime);
        record.setSubmitRound(request.getSubmitCount() == null ? 1 : request.getSubmitCount());
        record.setIsFinalNode(0);
        record.setCanResubmit(0);
        record.setTransferToUserId(addSignUserId);
        record.setStatus("ACTIVE");
        approvalRecordMapper.insert(record);

        instance.setLastAction("ADD_SIGN");
        instance.setLastActionBy(authUser.getUserId());
        instance.setLastActionAt(actionTime);
        approvalInstanceMapper.updateById(instance);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int publishDemandPoolRequests() {
        ensureDemandPoolPublisher(AuthContextHolder.requireUser());
        return publishDemandPoolRequestsInternal(true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int publishDemandPoolRequestsForTest() {
        ensureDemandPoolPublisher(AuthContextHolder.requireUser());
        return publishDemandPoolRequestsInternal(false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long publishDemandPoolRequestForFlowTest(Long purchaseRequestId) {
        ensureDemandPoolPublisher(AuthContextHolder.requireUser());
        PurchaseRequest request = purchaseRequestMapper.selectById(purchaseRequestId);
        if (request == null) {
            throw new BizException("FLOW_TEST_REQUEST_NOT_FOUND", "未找到流程测试采购申请");
        }
        if (!ProcurementWorkflow.STATUS_DEMAND_POOL.equals(request.getStatus())
            || !ProcurementWorkflow.APPROVAL_APPROVED.equals(request.getApprovalStatus())
            || isNationalDemandAggregate(request)) {
            throw new BizException("FLOW_TEST_STAGE_INVALID", "流程测试采购申请尚未进入可归集的需求池状态");
        }
        List<PurchaseRequestItem> activeItems = activeDemandPoolItems(request.getId());
        if (activeItems.isEmpty()) {
            throw new BizException("FLOW_TEST_ITEMS_EMPTY", "流程测试采购申请没有可归集明细");
        }
        Map<Long, List<PurchaseRequestItem>> activeItemsByRequestId = new LinkedHashMap<>();
        activeItemsByRequestId.put(request.getId(), activeItems);
        return publishDemandPoolGroup(List.of(request), activeItemsByRequestId, LocalDateTime.now());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int publishDemandPoolRequestsOnSchedule() {
        return publishDemandPoolRequestsInternal(false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int processApprovalTimeoutsForTest() {
        AuthUser authUser = AuthContextHolder.requireUser();
        if (!DataScopeHelper.isSystemAdmin(authUser)) {
            throw new BizException("FORBIDDEN", "Only system administrators can run approval timeout tests");
        }
        return processApprovalStageTimeoutsInternal(false);
    }

    @Scheduled(cron = "0 */30 * * * ?", zone = "Asia/Shanghai")
    @Transactional(rollbackFor = Exception.class)
    public void processApprovalStageTimeouts() {
        processApprovalStageTimeoutsInternal(false);
    }

    private int publishDemandPoolRequestsInternal(boolean checkPublishDay) {
        if (checkPublishDay && !shouldPublishDemandPoolToday()) {
            throw new BizException("DEMAND_POOL_PUBLISH_DATE_INVALID", "需求池只能在配置的月度推送日统一进入供应商报价");
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime publishCutoff = nextMonthStart(now);
        int count = releaseApprovedDelayedDemandAggregates(publishCutoff);
        LambdaQueryWrapper<PurchaseRequest> publishWrapper = new LambdaQueryWrapper<PurchaseRequest>()
            .eq(PurchaseRequest::getStatus, ProcurementWorkflow.STATUS_DEMAND_POOL)
            .eq(PurchaseRequest::getApprovalStatus, ProcurementWorkflow.APPROVAL_APPROVED)
            .and(wrapper -> wrapper.isNull(PurchaseRequest::getApprovedAt)
                .or()
                .lt(PurchaseRequest::getApprovedAt, publishCutoff));
        excludeNationalAggregates(publishWrapper);
        publishWrapper
            .orderByAsc(PurchaseRequest::getSubmittedAt)
            .orderByAsc(PurchaseRequest::getId);
        List<PurchaseRequest> requests = purchaseRequestMapper.selectList(publishWrapper);

        Map<DemandPoolGroupKey, List<PurchaseRequest>> groupedRequests = new LinkedHashMap<>();
        Map<Long, List<PurchaseRequestItem>> activeItemsByRequestId = new LinkedHashMap<>();
        for (PurchaseRequest request : requests) {
            if (isNationalDemandAggregate(request)) {
                continue;
            }
            List<PurchaseRequestItem> activeItems = activeDemandPoolItems(request.getId());
            BigDecimal effectiveAmount = sumRequestItemsAmount(activeItems);
            if (activeItems.isEmpty()) {
                request.setStatus(ProcurementWorkflow.STATUS_REJECTED);
                request.setApprovalStatus(ProcurementWorkflow.APPROVAL_REJECTED);
                request.setRejectedAt(now);
                request.setRemark(appendRemark(request.getRemark(), "需求池推送前无有效物品"));
                purchaseRequestMapper.updateById(request);
                continue;
            }
            activeItemsByRequestId.put(request.getId(), activeItems);
            groupedRequests.computeIfAbsent(groupKeyForDemandPool(request), key -> new ArrayList<>()).add(request);
        }

        for (List<PurchaseRequest> group : groupedRequests.values()) {
            publishDemandPoolGroup(group, activeItemsByRequestId, now);
            count++;
        }
        return count;
    }

    private int processApprovalStageTimeoutsInternal(boolean force) {
        LocalDateTime now = LocalDateTime.now();
        Set<Long> timeoutRequestIds = new LinkedHashSet<>();
        Set<Long> processedReplacementRequestIds = new LinkedHashSet<>();
        for (ApprovalTask task : approvalTaskService.pendingTimeoutTasks(now, force)) {
            if (task.getPurchaseRequestId() == null) {
                continue;
            }
            PurchaseRequest request = purchaseRequestMapper.selectById(task.getPurchaseRequestId());
            if (!canKeepTimedOutApprovalOpen(request, task)) {
                continue;
            }
            if (Objects.equals(task.getLevelNo(), ProcurementWorkflow.NODE_APPLICANT_REPLACEMENT_REVIEW)) {
                if (processedReplacementRequestIds.add(task.getPurchaseRequestId())) {
                    autoRejectReplacementConfirmation(request, task, now);
                    timeoutRequestIds.add(task.getPurchaseRequestId());
                }
                continue;
            }
            timeoutRequestIds.add(task.getPurchaseRequestId());
        }
        return timeoutRequestIds.size();
    }

    private void autoRejectReplacementConfirmation(PurchaseRequest request, ApprovalTask timeoutTask, LocalDateTime actionTime) {
        if (request == null || request.getId() == null) {
            return;
        }
        ApprovalInstance instance = latestApprovalInstance(request.getId());
        if (instance == null || !Objects.equals(request.getCurrentNodeNo(), ProcurementWorkflow.NODE_APPLICANT_REPLACEMENT_REVIEW)) {
            return;
        }
        String comment = "申请人超过 2 天未确认替换商品，系统按不同意更换处理";
        Long actionUserId = timeoutTask == null ? null : timeoutTask.getCandidateUserId();
        updateAwardedReplacementReviewStatus(
            request.getId(),
            REPLACEMENT_REVIEW_AUTO_REJECTED,
            actionUserId,
            actionTime,
            comment
        );
        approvalTaskService.closePendingTasks(instance.getId(), "REPLACEMENT_CONFIRM_TIMEOUT");

        ProcurementApprovalRouteResolver.ApprovalAssignment nextAssignment =
            startQuoteApprovalAfterReplacementConfirm(request, instance, comment, actionUserId, actionTime);

        ApprovalRecord record = new ApprovalRecord();
        record.setInstanceId(instance.getId());
        record.setPurchaseRequestId(request.getId());
        record.setNodeNo(ProcurementWorkflow.NODE_APPLICANT_REPLACEMENT_REVIEW);
        record.setNodeName(ProcurementWorkflow.nodeNameForNode(ProcurementWorkflow.NODE_APPLICANT_REPLACEMENT_REVIEW));
        record.setApproverUserId(actionUserId);
        record.setApproverRoleCode(REPLACEMENT_CONFIRM_ROLE_CODE);
        record.setAction("AUTO_REJECT_REPLACEMENT");
        record.setFromStatus(ProcurementWorkflow.APPROVAL_PENDING);
        record.setToStatus(ProcurementWorkflow.APPROVAL_PENDING);
        record.setApprovalComment(comment);
        record.setOperatedAt(actionTime);
        record.setSubmitRound(request.getSubmitCount() == null ? 1 : request.getSubmitCount());
        record.setIsFinalNode(0);
        record.setCanResubmit(0);
        record.setTransferToUserId(nextAssignment.currentApproverId());
        record.setStatus("ACTIVE");
        approvalRecordMapper.insert(record);
        updateApprovalInstance(instance);
        purchaseRequestMapper.updateById(request);
    }

    private boolean canKeepTimedOutApprovalOpen(PurchaseRequest request, ApprovalTask timeoutTask) {
        return request != null
            && timeoutTask != null
            && ProcurementWorkflow.STATUS_PENDING_APPROVAL.equals(request.getStatus())
            && ProcurementWorkflow.APPROVAL_PENDING.equals(request.getApprovalStatus())
            && Objects.equals(request.getCurrentNodeNo(), timeoutTask.getLevelNo());
    }

    private int releaseApprovedDelayedDemandAggregates(LocalDateTime publishCutoff) {
        List<PurchaseRequest> delayedAggregates = purchaseRequestMapper.selectList(new LambdaQueryWrapper<PurchaseRequest>()
            .eq(PurchaseRequest::getReqReason, NATIONAL_DEMAND_AGGREGATE_REASON)
            .eq(PurchaseRequest::getStatus, ProcurementWorkflow.STATUS_DEMAND_AGGREGATED)
            .eq(PurchaseRequest::getApprovalStatus, ProcurementWorkflow.APPROVAL_APPROVED)
            .isNotNull(PurchaseRequest::getApprovedAt)
            .lt(PurchaseRequest::getApprovedAt, publishCutoff)
            .orderByAsc(PurchaseRequest::getApprovedAt)
            .orderByAsc(PurchaseRequest::getId));
        for (PurchaseRequest request : delayedAggregates) {
            request.setStatus(ProcurementWorkflow.STATUS_QUOTING);
            request.setRemark(appendRemark(request.getRemark(), "超期同意的全国汇总单已到下月执行期，进入供应商报价"));
            purchaseRequestMapper.updateById(request);
        }
        return delayedAggregates.size();
    }

    private LocalDateTime nextMonthStart(LocalDateTime baseTime) {
        LocalDate baseDate = baseTime == null ? LocalDate.now() : baseTime.toLocalDate();
        return baseDate.plusMonths(1).withDayOfMonth(1).atStartOfDay();
    }

    private void ensureDemandPoolPublisher(AuthUser authUser) {
        if (DataScopeHelper.isSystemAdmin(authUser)
            || DataScopeHelper.hasAnyRole(authUser, List.of("PURCHASER"))) {
            return;
        }
        throw new BizException("FORBIDDEN", "Current account cannot publish demand-pool requests");
    }

    private boolean shouldPublishDemandPoolToday() {
        LocalDate today = LocalDate.now();
        int configuredDay = clampDay(parseIntSetting("monthly_aggregate_day", 20));
        int effectiveDay = Math.min(configuredDay, today.lengthOfMonth());
        return today.getDayOfMonth() == effectiveDay;
    }

    private int parseIntSetting(String key, int defaultValue) {
        try {
            return Integer.parseInt(settingValue(key, String.valueOf(defaultValue)));
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private boolean parseBooleanSetting(String key, boolean defaultValue) {
        String value = settingValue(key, String.valueOf(defaultValue));
        if (!StringUtils.hasText(value)) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value.trim());
    }

    private BigDecimal parseAmountSetting(String key, BigDecimal defaultValue) {
        String value = settingValue(key, defaultValue == null ? "0" : defaultValue.toPlainString());
        try {
            return new BigDecimal(value.trim()).setScale(2, RoundingMode.HALF_UP);
        } catch (Exception ignored) {
            return defaultValue == null ? BigDecimal.ZERO : defaultValue;
        }
    }

    private String settingValue(String key, String defaultValue) {
        return procurementSettingsService.getSettings().getSettings().stream()
            .filter(setting -> key.equals(setting.getSettingKey()))
            .map(ProcurementSettingItemVO::getSettingValue)
            .filter(StringUtils::hasText)
            .findFirst()
            .orElse(defaultValue);
    }

    private int clampDay(int day) {
        return Math.min(31, Math.max(1, day));
    }

    private DemandPoolGroupKey groupKeyForDemandPool(PurchaseRequest request) {
        return new DemandPoolGroupKey("NATIONAL");
    }

    private List<PurchaseRequestItem> activeDemandPoolItems(Long requestId) {
        return activeRequestItems(requestId).stream()
            .sorted(Comparator
                .comparing(PurchaseRequestItem::getLineNo, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(PurchaseRequestItem::getId, Comparator.nullsLast(Comparator.naturalOrder())))
            .toList();
    }

    private List<PurchaseRequestItem> activeRequestItems(Long requestId) {
        if (requestId == null) {
            return List.of();
        }
        return purchaseRequestItemMapper.selectList(new LambdaQueryWrapper<PurchaseRequestItem>()
            .eq(PurchaseRequestItem::getPurchaseRequestId, requestId)
            .and(wrapper -> wrapper.ne(PurchaseRequestItem::getStatus, ProcurementWorkflow.ITEM_STATUS_REJECTED)
                .or()
                .isNull(PurchaseRequestItem::getStatus)));
    }

    private BigDecimal sumRequestItemsAmount(List<PurchaseRequestItem> items) {
        return items.stream()
            .map(PurchaseRequestItem::getLineAmount)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);
    }

    private boolean hasRejectedItemDecision(WorkflowApprovalActionDTO dto) {
        return dto != null && dto.getItemDecisions() != null && dto.getItemDecisions().stream()
            .anyMatch(decision -> decision != null && Boolean.TRUE.equals(decision.getRejected()));
    }

    private boolean isOverPriceFloorReview(ApprovalInstance instance, int currentNodeNo) {
        return currentNodeNo == ProcurementWorkflow.NODE_FLOOR_LEADER_DEMAND_REVIEW
            && instance != null
            && ProcurementWorkflow.LEVEL_CODE_OVER_PRICE_REVIEW.equals(instance.getCurrentLevelCode());
    }

    private void validateOverPriceReviewDecision(PurchaseRequest request, WorkflowApprovalActionDTO dto) {
        Set<Long> reviewItemIds = overPriceReviewService.requiredReviewItemIds(request);
        if (reviewItemIds.isEmpty()) {
            return;
        }
        Set<Long> rejectedItemIds = dto == null || dto.getItemDecisions() == null
            ? Set.of()
            : dto.getItemDecisions().stream()
                .filter(Objects::nonNull)
                .filter(decision -> Boolean.TRUE.equals(decision.getRejected()))
                .map(decision -> decision.getItemId())
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (!reviewItemIds.containsAll(rejectedItemIds)) {
            throw new BizException("OVER_PRICE_REVIEW_ITEM_INVALID", "楼长超价复核只能驳回被标记为超价的商品");
        }
        boolean hasApprovedOverPriceItem = reviewItemIds.stream().anyMatch(itemId -> !rejectedItemIds.contains(itemId));
        if (hasApprovedOverPriceItem && !meaningfulOverPriceApprovalComment(dto == null ? null : dto.getApprovalComment())) {
            throw new BizException("OVER_PRICE_REVIEW_COMMENT_REQUIRED", "通过超价商品时必须填写价格合理原因");
        }
    }

    private boolean meaningfulOverPriceApprovalComment(String comment) {
        if (!StringUtils.hasText(comment)) {
            return false;
        }
        String normalized = comment.trim().replaceAll("[\\s，。；、,.!！?？：:]+", "");
        return normalized.length() >= 4
            && !Set.of("同意", "通过", "确认", "价格合理", "同意通过").contains(normalized);
    }

    private void validateActiveItemsReadyForApproval(PurchaseRequest request, List<PurchaseRequestItem> activeItems) {
        if (activeItems == null || activeItems.isEmpty()) {
            return;
        }
        for (int i = 0; i < activeItems.size(); i++) {
            PurchaseRequestItem item = activeItems.get(i);
            int lineNo = item.getLineNo() == null ? i + 1 : item.getLineNo();
            if (item.getQty() == null || item.getQty().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BizException("ITEM_QTY_INVALID", "第 " + lineNo + " 行数量必须大于 0，不能通过审批");
            }
            if (item.getUnitPrice() == null || item.getUnitPrice().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BizException("ITEM_PRICE_INVALID", "第 " + lineNo + " 行单价必须大于 0，请退回申请人补充或使用单项驳回");
            }
            BigDecimal lineAmount = item.getQty().multiply(item.getUnitPrice()).setScale(2, RoundingMode.HALF_UP);
            if (lineAmount.compareTo(BigDecimal.ZERO) <= 0
                || item.getLineAmount() == null
                || lineAmount.compareTo(item.getLineAmount().setScale(2, RoundingMode.HALF_UP)) != 0) {
                throw new BizException("ITEM_AMOUNT_INVALID", "第 " + lineNo + " 行金额必须大于 0 且等于数量乘以单价，不能通过审批");
            }
        }
    }

    private Long publishDemandPoolGroup(List<PurchaseRequest> sourceRequests,
                                        Map<Long, List<PurchaseRequestItem>> activeItemsByRequestId,
                                        LocalDateTime now) {
        PurchaseRequest first = sourceRequests.get(0);
        List<PurchaseRequestItem> sourceItems = new ArrayList<>();
        for (PurchaseRequest request : sourceRequests) {
            sourceItems.addAll(activeItemsByRequestId.getOrDefault(request.getId(), List.of()));
        }
        BigDecimal effectiveAmount = sourceItems.stream()
            .map(PurchaseRequestItem::getLineAmount)
            .filter(amount -> amount != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);

        Long systemAdminTriggerUserId = systemAdminTriggerUserId();
        PurchaseRequest aggregate = buildDemandPoolAggregateRequest(
            first,
            sourceRequests,
            sourceItems,
            effectiveAmount,
            now,
            systemAdminTriggerUserId
        );
        purchaseRequestMapper.insert(aggregate);
        copyDemandPoolItemsToAggregate(aggregate.getId(), sourceRequests, activeItemsByRequestId);

        ApprovalInstance instance = new ApprovalInstance();
        instance.setInstanceNo(generateInstanceNo());
        instance.setBizType("PURCHASE_REQUEST");
        instance.setBizId(aggregate.getId());
        instance.setPurchaseRequestId(aggregate.getId());
        instance.setStartedAt(now);
        instance.setCurrentNodeNo(0);
        instance.setCurrentLevelNo(null);
        instance.setCurrentLevelCode("SUPPLIER_QUOTE");
        instance.setCurrentPassRule(null);
        instance.setTotalNodeNo(0);
        instance.setCurrentApproverId(null);
        instance.setLastAction(ProcurementWorkflow.ACTION_SUBMIT);
        instance.setLastActionBy(systemAdminTriggerUserId);
        instance.setLastActionAt(now);
        instance.setStatus(ProcurementWorkflow.APPROVAL_APPROVED);
        instance.setEndedAt(now);
        instance.setRemark("需求池全国汇总后直接进入供应商报价");
        approvalInstanceMapper.insert(instance);

        ApprovalRecord record = new ApprovalRecord();
        record.setInstanceId(instance.getId());
        record.setPurchaseRequestId(aggregate.getId());
        record.setNodeNo(0);
        record.setNodeName("需求池汇总直达报价");
        record.setApproverUserId(systemAdminTriggerUserId);
        record.setApproverRoleCode("SUPER_ADMIN");
        record.setAction(ProcurementWorkflow.ACTION_SUBMIT);
        record.setFromStatus(ProcurementWorkflow.APPROVAL_APPROVED);
        record.setToStatus(ProcurementWorkflow.APPROVAL_APPROVED);
        record.setApprovalComment("需求池全国汇总为一张供应商报价单后，按流程直接进入供应商报价");
        record.setOperatedAt(now);
        record.setSubmitRound(1);
        record.setIsFinalNode(1);
        record.setCanResubmit(0);
        record.setTransferToUserId(null);
        record.setStatus("ACTIVE");
        approvalRecordMapper.insert(record);

        for (PurchaseRequest source : sourceRequests) {
            source.setStatus(ProcurementWorkflow.STATUS_DEMAND_AGGREGATED);
            source.setApprovalStatus(ProcurementWorkflow.APPROVAL_APPROVED);
            source.setCurrentNodeNo(ProcurementWorkflow.NODE_FLOOR_LEADER_DEMAND_REVIEW);
            source.setRemark(appendRemark(source.getRemark(), "已汇总至全国月度汇总单：" + aggregate.getReqNo()));
            purchaseRequestMapper.updateById(source);
        }
        return aggregate.getId();
    }

    private PurchaseRequest buildDemandPoolAggregateRequest(PurchaseRequest first,
                                                            List<PurchaseRequest> sourceRequests,
                                                            List<PurchaseRequestItem> sourceItems,
                                                            BigDecimal effectiveAmount,
                                                            LocalDateTime now,
                                                            Long systemAdminTriggerUserId) {
        PurchaseRequest aggregate = new PurchaseRequest();
        Long applicantUserId = systemAdminTriggerUserId == null ? first.getApplicantUserId() : systemAdminTriggerUserId;
        aggregate.setReqNo(generateAggregateReqNo());
        aggregate.setReqTitle(buildDemandPoolAggregateTitle(first, sourceRequests.size(), sourceItems.size(), now));
        aggregate.setReqType(resolveAggregateReqType(sourceRequests));
        aggregate.setApplicantUserId(applicantUserId);
        aggregate.setPurchaserUserId(first.getPurchaserUserId() == null ? first.getApplicantUserId() : first.getPurchaserUserId());
        aggregate.setRegionId(first.getRegionId());
        aggregate.setBuildingScopeId(first.getBuildingScopeId());
        aggregate.setApplicantSubjectId(first.getApplicantSubjectId());
        aggregate.setApprovalPolicyId(first.getApprovalPolicyId());
        aggregate.setApprovalSnapshotJson(first.getApprovalSnapshotJson());
        aggregate.setDeptCode(first.getDeptCode());
        aggregate.setDeptName(first.getDeptName());
        aggregate.setSpecialtyCode(first.getSpecialtyCode());
        aggregate.setProfessionalReviewRequired(first.getProfessionalReviewRequired());
        aggregate.setBudgetCode(firstText(resolveAggregateBudgetCode(sourceRequests), routeCostCenterCode(first)));
        aggregate.setCurrency(StringUtils.hasText(first.getCurrency()) ? first.getCurrency() : "CNY");
        aggregate.setTotalAmount(effectiveAmount);
        aggregate.setUrgencyLevel(1);
        aggregate.setNeedDate(resolveAggregateNeedDate(sourceRequests, sourceItems));
        aggregate.setReqReason(NATIONAL_DEMAND_AGGREGATE_REASON);
        aggregate.setAwardStrategy(ProcurementWorkflow.resolveAutoAwardStrategy(effectiveAmount));
        aggregate.setSubmitCount(1);
        aggregate.setCurrentNodeNo(0);
        aggregate.setApprovalStatus(ProcurementWorkflow.APPROVAL_APPROVED);
        aggregate.setStatus(ProcurementWorkflow.STATUS_QUOTING);
        aggregate.setSubmittedAt(now);
        aggregate.setApprovedAt(now);
        aggregate.setSelectedQuoteId(null);
        aggregate.setLeaderConfirmStatus(ProcurementWorkflow.LEADER_CONFIRM_STATUS_PENDING);
        aggregate.setRemark("系统从全国需求池汇总生成，共 " + sourceRequests.size() + " 张来源单");
        return aggregate;
    }

    private void copyDemandPoolItemsToAggregate(Long aggregateRequestId,
                                                List<PurchaseRequest> sourceRequests,
                                                Map<Long, List<PurchaseRequestItem>> activeItemsByRequestId) {
        int lineNo = 1;
        for (PurchaseRequest request : sourceRequests) {
            if (isNationalDemandAggregate(request)) {
                continue;
            }
            String receivingCity = resolveReceivingCity(request);
            String deliveryAddress = resolveDeliveryAddress(request);
            for (PurchaseRequestItem sourceItem : activeItemsByRequestId.getOrDefault(request.getId(), List.of())) {
                PurchaseRequestItem item = new PurchaseRequestItem();
                item.setPurchaseRequestId(aggregateRequestId);
                item.setLineNo(lineNo++);
                item.setItemCode(sourceItem.getItemCode());
                item.setReqType(sourceItem.getReqType());
                item.setItemName(sourceItem.getItemName());
                item.setSpecModel(sourceItem.getSpecModel());
                item.setItemBrand(sourceItem.getItemBrand());
                item.setUnit(sourceItem.getUnit());
                item.setQty(sourceItem.getQty());
                item.setUnitPrice(sourceItem.getUnitPrice());
                item.setTaxRate(sourceItem.getTaxRate());
                item.setLineAmount(sourceItem.getLineAmount());
                item.setNeedDate(sourceItem.getNeedDate());
                item.setPurpose(sourceItem.getPurpose());
                item.setEcommerceUrl(sourceItem.getEcommerceUrl());
                item.setReceivingCity(firstText(sourceItem.getReceivingCity(), receivingCity));
                item.setDeliveryAddress(firstText(sourceItem.getDeliveryAddress(), deliveryAddress));
                item.setSourceRequestId(request.getId());
                item.setSourceRequestItemId(sourceItem.getId());
                item.setStatus(ProcurementWorkflow.ITEM_STATUS_ACTIVE);
                item.setRemark(appendRemark(sourceItem.getRemark(), "来源单：" + request.getReqNo() + "，来源行号：" + sourceItem.getLineNo()));
                purchaseRequestItemMapper.insert(item);
            }
        }
    }

    private String buildDemandPoolAggregateTitle(PurchaseRequest first, int sourceCount, int itemCount, LocalDateTime now) {
        return now.format(DateTimeFormatter.ofPattern("yyyy-MM")) + "酒仙桥区域月度需求汇总（" + sourceCount + "张申请/" + itemCount + "项明细）";
    }

    private String displayReqTitle(String title) {
        if (!StringUtils.hasText(title)) {
            return title;
        }
        return title
            .replace("全国月度需求汇总", "酒仙桥区域月度需求汇总")
            .replaceAll("[（(](\\d+)单/(\\d+)项[）)]", "（$1张申请/$2项明细）");
    }

    private String resolveDeliveryAddress(PurchaseRequest request) {
        OrgScope scope = approvalRouteResolver.findScope(request);
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

    private String resolveReceivingCity(PurchaseRequest request) {
        OrgScope scope = approvalRouteResolver.findScope(request);
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

    private void addIfPresent(List<String> parts, String value) {
        if (StringUtils.hasText(value)) {
            parts.add(value.trim());
        }
    }

    private String resolveAggregateReqType(List<PurchaseRequest> requests) {
        String reqType = requests.get(0).getReqType();
        boolean same = requests.stream().allMatch(request -> safe(reqType).equals(safe(request.getReqType())));
        return same && StringUtils.hasText(reqType) ? reqType : "混合采购";
    }

    private String resolveAggregateBudgetCode(List<PurchaseRequest> requests) {
        String budgetCode = requests.get(0).getBudgetCode();
        boolean same = requests.stream().allMatch(request -> safe(budgetCode).equals(safe(request.getBudgetCode())));
        return same ? budgetCode : null;
    }

    private java.time.LocalDate resolveAggregateNeedDate(List<PurchaseRequest> requests, List<PurchaseRequestItem> items) {
        return items.stream()
            .map(PurchaseRequestItem::getNeedDate)
            .filter(date -> date != null)
            .min(java.time.LocalDate::compareTo)
            .orElseGet(() -> requests.stream()
                .map(PurchaseRequest::getNeedDate)
                .filter(date -> date != null)
                .min(java.time.LocalDate::compareTo)
                .orElse(null));
    }

    private void applyItemDecisions(PurchaseRequest request, WorkflowApprovalActionDTO dto, int currentNodeNo, AuthUser authUser) {
        if (dto.getItemDecisions() == null || dto.getItemDecisions().isEmpty()) {
            return;
        }
        if (currentNodeNo < ProcurementWorkflow.NODE_FLOOR_LEADER_DEMAND_REVIEW) {
            throw new BizException("ITEM_REJECT_STAGE_INVALID", "Only floor leader and later stages can reject individual items");
        }

        Map<Long, PurchaseRequestItem> itemMap = new LinkedHashMap<>();
        for (PurchaseRequestItem item : purchaseRequestItemMapper.selectList(new LambdaQueryWrapper<PurchaseRequestItem>()
            .eq(PurchaseRequestItem::getPurchaseRequestId, request.getId()))) {
            itemMap.put(item.getId(), item);
        }
        Set<Long> visibleSourceIds = visibleNationalAggregateSourceIdsForManager(request, authUser, currentNodeNo);

        for (var decision : dto.getItemDecisions()) {
            if (!Boolean.TRUE.equals(decision.getRejected())) {
                continue;
            }
            if (decision.getItemId() == null || !itemMap.containsKey(decision.getItemId())) {
                throw new BizException("ITEM_DECISION_INVALID", "Rejected item is not in current request");
            }
            if (!StringUtils.hasText(decision.getRejectReason())) {
                throw new BizException("ITEM_REJECT_REASON_REQUIRED", "Rejected item reason is required");
            }
            PurchaseRequestItem item = itemMap.get(decision.getItemId());
            if (visibleSourceIds != null && !visibleSourceIds.contains(item.getSourceRequestId())) {
                throw new BizException("ITEM_DECISION_SCOPE_INVALID", "Rejected item is not visible to current approver");
            }
            item.setStatus(ProcurementWorkflow.ITEM_STATUS_REJECTED);
            item.setRemark(decision.getRejectReason().trim());
            purchaseRequestItemMapper.updateById(item);
            syncRejectedAggregateItemToSource(item);
        }
    }

    private PurchaseRequestItem messageRejectedItem(PurchaseRequestItem item) {
        PurchaseRequestItem sourceItem = ultimateRejectedSourceItem(item, new HashSet<>());
        if (sourceItem == null || sourceItem == item) {
            return item;
        }
        sourceItem.setStatus(ProcurementWorkflow.ITEM_STATUS_REJECTED);
        if (StringUtils.hasText(item.getRemark())) {
            sourceItem.setRemark(item.getRemark());
        }
        return sourceItem;
    }

    private void syncRejectedAggregateItemToSource(PurchaseRequestItem aggregateItem) {
        syncRejectedAggregateItemToSource(aggregateItem, new HashSet<>());
    }

    private void syncRejectedAggregateItemToSource(PurchaseRequestItem aggregateItem, Set<Long> visitedItemIds) {
        if (aggregateItem == null
            || aggregateItem.getSourceRequestId() == null
            || aggregateItem.getSourceRequestItemId() == null) {
            return;
        }
        if (aggregateItem.getId() != null && !visitedItemIds.add(aggregateItem.getId())) {
            return;
        }
        PurchaseRequestItem sourceItem = purchaseRequestItemMapper.selectById(aggregateItem.getSourceRequestItemId());
        if (sourceItem == null || !Objects.equals(sourceItem.getPurchaseRequestId(), aggregateItem.getSourceRequestId())) {
            return;
        }
        sourceItem.setStatus(ProcurementWorkflow.ITEM_STATUS_REJECTED);
        if (StringUtils.hasText(aggregateItem.getRemark())) {
            sourceItem.setRemark(aggregateItem.getRemark());
        }
        purchaseRequestItemMapper.updateById(sourceItem);
        refreshSourceRequestAfterAggregateItemDecision(aggregateItem.getSourceRequestId());
        syncRejectedAggregateItemToSource(sourceItem, visitedItemIds);
    }

    private void refreshSourceRequestAfterAggregateItemDecision(Long sourceRequestId) {
        PurchaseRequest sourceRequest = purchaseRequestMapper.selectById(sourceRequestId);
        if (sourceRequest == null) {
            return;
        }
        List<PurchaseRequestItem> sourceItems = purchaseRequestItemMapper.selectList(new LambdaQueryWrapper<PurchaseRequestItem>()
            .eq(PurchaseRequestItem::getPurchaseRequestId, sourceRequestId));
        List<PurchaseRequestItem> activeItems = sourceItems.stream()
            .filter(item -> !ProcurementWorkflow.ITEM_STATUS_REJECTED.equals(item.getStatus()))
            .toList();
        BigDecimal activeAmount = sumRequestItemsAmount(activeItems);
        sourceRequest.setTotalAmount(activeAmount);
        if (activeItems.isEmpty()) {
            sourceRequest.setApprovalStatus(ProcurementWorkflow.APPROVAL_REJECTED);
            sourceRequest.setStatus(ProcurementWorkflow.STATUS_REJECTED);
            sourceRequest.setRejectedAt(LocalDateTime.now());
            sourceRequest.setRemark(appendRemark(sourceRequest.getRemark(), "全国汇总审核中全部商品被驳回"));
        } else if (ProcurementWorkflow.APPROVAL_REJECTED.equals(sourceRequest.getApprovalStatus())
            || ProcurementWorkflow.STATUS_REJECTED.equals(sourceRequest.getStatus())) {
            sourceRequest.setApprovalStatus(ProcurementWorkflow.APPROVAL_APPROVED);
            sourceRequest.setStatus(ProcurementWorkflow.STATUS_DEMAND_AGGREGATED);
            sourceRequest.setRejectedAt(null);
            sourceRequest.setRemark(appendRemark(sourceRequest.getRemark(), "全国汇总审核中部分商品被驳回，剩余商品继续流转"));
        }
        purchaseRequestMapper.updateById(sourceRequest);
    }

    private BigDecimal activeRequestAmount(Long purchaseRequestId) {
        return sumRequestItemsAmount(activeRequestItems(purchaseRequestId));
    }

    private BigDecimal awardStrategyAmount(PurchaseRequest request) {
        if (request == null) {
            return BigDecimal.ZERO;
        }
        if (request.getId() == null) {
            return request.getTotalAmount();
        }
        BigDecimal activeAmount = activeRequestAmount(request.getId());
        return activeAmount.compareTo(BigDecimal.ZERO) > 0 ? activeAmount : request.getTotalAmount();
    }

    private boolean shouldDeferApprovedAggregateToNextMonth(PurchaseRequest request) {
        return isNationalDemandAggregate(request)
            && approvalTaskService.hasOverdueApprovedTask(request.getId());
    }

    private String routeCostCenterCode(PurchaseRequest request) {
        var route = approvalRouteResolver.findRoute(request);
        return route == null ? null : route.getCostCenterCode();
    }

    private boolean isEmergencyRequest(PurchaseRequest request) {
        return request.getUrgencyLevel() != null && request.getUrgencyLevel() >= 2;
    }

    private void applyUrgentDirectAwardIfEligible(PurchaseRequest request, BigDecimal effectiveAmount, LocalDateTime actionTime) {
        clearDirectAward(request);
        if (!ProcurementWorkflow.STATUS_QUOTING.equals(request.getStatus())
            || !isEmergencyRequest(request)
            || !parseBooleanSetting(URGENT_DIRECT_AWARD_ENABLED_KEY, true)) {
            return;
        }
        BigDecimal threshold = parseAmountSetting(URGENT_DIRECT_AWARD_THRESHOLD_KEY, DEFAULT_URGENT_DIRECT_AWARD_THRESHOLD);
        if (threshold.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        BigDecimal amount = effectiveAmount == null ? awardStrategyAmount(request) : effectiveAmount;
        if (amount == null || amount.compareTo(threshold) >= 0) {
            return;
        }
        DirectAwardCandidate candidate = resolveUrgentDirectAwardCandidate(request, actionTime);
        if (candidate == null) {
            request.setDirectAwardReason("低额紧急采购未找到可用供应商，继续按三方报价流程处理");
            return;
        }
        String supplierName = StringUtils.hasText(candidate.supplierName())
            ? candidate.supplierName()
            : "供应商" + candidate.supplierId();
        request.setDirectAwardApplied(1);
        request.setDirectAwardSupplierId(candidate.supplierId());
        request.setDirectAwardSupplierName(supplierName);
        request.setDirectAwardThresholdAmount(threshold);
        if (candidate.awardCount() > 0) {
            request.setDirectAwardReason("低额紧急采购金额 "
                + decimalText(amount)
                + " 低于阈值 "
                + decimalText(threshold)
                + "，定向推送历史单项中标次数最多的供应商："
                + supplierName
                + "（历史单项中标 "
                + candidate.awardCount()
                + " 次）");
        } else {
            request.setDirectAwardReason("低额紧急采购金额 "
                + decimalText(amount)
                + " 低于阈值 "
                + decimalText(threshold)
                + "，未找到历史中标记录，已定向推送当前可用供应商："
                + supplierName);
        }
    }

    private void clearDirectAward(PurchaseRequest request) {
        request.setDirectAwardApplied(0);
        request.setDirectAwardSupplierId(null);
        request.setDirectAwardSupplierName(null);
        request.setDirectAwardThresholdAmount(null);
        request.setDirectAwardReason(null);
        request.setQuoteCountdownStartedAt(null);
        request.setQuoteCountdownDueAt(null);
    }

    private DirectAwardCandidate resolveUrgentDirectAwardCandidate(PurchaseRequest request, LocalDateTime actionTime) {
        if (request == null || request.getId() == null) {
            return null;
        }
        Map<Long, DirectAwardCandidate> candidates = new LinkedHashMap<>();
        Set<String> itemKeys = new HashSet<>();
        for (PurchaseRequestItem item : activeDemandPoolItems(request.getId())) {
            String itemKey = directAwardItemKey(item);
            if (!StringUtils.hasText(itemKey) || !itemKeys.add(itemKey)) {
                continue;
            }
            List<SupplierQuoteItem> historyItems = supplierQuoteItemMapper.selectList(new LambdaQueryWrapper<SupplierQuoteItem>()
                .eq(SupplierQuoteItem::getAwarded, 1)
                .eq(SupplierQuoteItem::getStatus, QUOTE_ITEM_STATUS_ACTIVE)
                .eq(SupplierQuoteItem::getItemName, item.getItemName())
                .eq(SupplierQuoteItem::getSpecModel, item.getSpecModel())
                .eq(StringUtils.hasText(item.getItemCode()), SupplierQuoteItem::getItemCode, item.getItemCode())
                .ne(SupplierQuoteItem::getPurchaseRequestId, request.getId())
                .orderByDesc(SupplierQuoteItem::getUpdatedAt)
                .last("LIMIT 100"));
            for (SupplierQuoteItem historyItem : historyItems) {
                SupplierQuote quote = supplierQuoteMapper.selectById(historyItem.getQuoteId());
                if (quote == null || quote.getSupplierId() == null) {
                    continue;
                }
                Supplier supplier = supplierMapper.selectById(quote.getSupplierId());
                if (!isSupplierAvailableForDirectAward(supplier, actionTime)) {
                    continue;
                }
                LocalDateTime awardAt = latestDateTime(historyItem.getUpdatedAt(), quote.getSubmittedAt(), quote.getUpdatedAt());
                DirectAwardCandidate current = candidates.get(quote.getSupplierId());
                candidates.put(
                    quote.getSupplierId(),
                    new DirectAwardCandidate(
                        quote.getSupplierId(),
                        supplier.getSupplierName(),
                        current == null ? 1 : current.awardCount() + 1,
                        latestDateTime(awardAt, current == null ? null : current.latestAwardAt())
                    )
                );
            }
        }
        DirectAwardCandidate matchedCandidate = candidates.values().stream()
            .max(Comparator
                .comparingInt(DirectAwardCandidate::awardCount)
                .thenComparing(candidate -> candidate.latestAwardAt() == null ? LocalDateTime.MIN : candidate.latestAwardAt())
                .thenComparing(candidate -> candidate.supplierId() == null ? 0L : candidate.supplierId()))
            .orElse(null);
        if (matchedCandidate != null) {
            return matchedCandidate;
        }
        DirectAwardCandidate historyCandidate = resolveMostAwardedDirectAwardCandidate(actionTime);
        return historyCandidate != null ? historyCandidate : resolveAvailableDirectAwardCandidate(request, actionTime);
    }

    private DirectAwardCandidate resolveMostAwardedDirectAwardCandidate(LocalDateTime actionTime) {
        Map<Long, DirectAwardCandidate> candidates = new LinkedHashMap<>();
        List<SupplierQuoteItem> historyItems = supplierQuoteItemMapper.selectList(new LambdaQueryWrapper<SupplierQuoteItem>()
            .eq(SupplierQuoteItem::getAwarded, 1)
            .eq(SupplierQuoteItem::getStatus, QUOTE_ITEM_STATUS_ACTIVE)
            .orderByDesc(SupplierQuoteItem::getUpdatedAt)
            .last("LIMIT 500"));
        for (SupplierQuoteItem historyItem : historyItems) {
            SupplierQuote quote = supplierQuoteMapper.selectById(historyItem.getQuoteId());
            if (quote == null || quote.getSupplierId() == null) {
                continue;
            }
            Supplier supplier = supplierMapper.selectById(quote.getSupplierId());
            if (!isSupplierAvailableForDirectAward(supplier, actionTime)) {
                continue;
            }
            LocalDateTime awardAt = latestDateTime(historyItem.getUpdatedAt(), quote.getSubmittedAt(), quote.getUpdatedAt());
            DirectAwardCandidate current = candidates.get(quote.getSupplierId());
            candidates.put(
                quote.getSupplierId(),
                new DirectAwardCandidate(
                    quote.getSupplierId(),
                    supplier.getSupplierName(),
                    current == null ? 1 : current.awardCount() + 1,
                    latestDateTime(awardAt, current == null ? null : current.latestAwardAt())
                )
            );
        }
        return candidates.values().stream()
            .max(Comparator
                .comparingInt(DirectAwardCandidate::awardCount)
                .thenComparing(candidate -> candidate.latestAwardAt() == null ? LocalDateTime.MIN : candidate.latestAwardAt())
                .thenComparing(candidate -> candidate.supplierId() == null ? 0L : candidate.supplierId()))
            .orElse(null);
    }

    private DirectAwardCandidate resolveAvailableDirectAwardCandidate(PurchaseRequest request, LocalDateTime actionTime) {
        List<Supplier> suppliers = supplierMapper.selectList(new LambdaQueryWrapper<Supplier>()
            .eq(Supplier::getStatus, "ACTIVE")
            .orderByDesc(Supplier::getUpdatedAt)
            .orderByAsc(Supplier::getId));
        return suppliers.stream()
            .filter(supplier -> isSupplierAvailableForDirectAward(supplier, actionTime))
            .min(Comparator
                .comparing((Supplier supplier) -> Objects.equals(supplier.getRegionId(), request.getRegionId()) ? 0 : 1)
                .thenComparing(
                    supplier -> supplier.getUpdatedAt() == null ? LocalDateTime.MIN : supplier.getUpdatedAt(),
                    Comparator.reverseOrder()
                )
                .thenComparing(supplier -> supplier.getId() == null ? Long.MAX_VALUE : supplier.getId()))
            .map(supplier -> new DirectAwardCandidate(
                supplier.getId(),
                supplier.getSupplierName(),
                0,
                supplier.getUpdatedAt()
            ))
            .orElse(null);
    }

    private String directAwardItemKey(PurchaseRequestItem item) {
        if (item == null || !StringUtils.hasText(item.getItemName()) || !StringUtils.hasText(item.getSpecModel())) {
            return "";
        }
        return safe(item.getItemCode()).trim()
            + "|"
            + item.getItemName().trim()
            + "|"
            + item.getSpecModel().trim();
    }

    private boolean isSupplierAvailableForDirectAward(Supplier supplier, LocalDateTime now) {
        if (supplier == null || !"ACTIVE".equalsIgnoreCase(String.valueOf(supplier.getStatus()))) {
            return false;
        }
        if (supplier.getBlacklistFlag() != null && supplier.getBlacklistFlag() == 1) {
            return false;
        }
        return supplier.getBidSuspendedUntil() == null || !supplier.getBidSuspendedUntil().isAfter(now);
    }

    private boolean isDirectAwardRequest(PurchaseRequest request) {
        return request != null
            && request.getDirectAwardApplied() != null
            && request.getDirectAwardApplied() == 1
            && request.getDirectAwardSupplierId() != null;
    }

    private boolean hasAwardSelection(PurchaseRequest request) {
        if (request.getSelectedQuoteId() != null) {
            return true;
        }
        return supplierQuoteItemMapper.selectCount(new LambdaQueryWrapper<SupplierQuoteItem>()
            .eq(SupplierQuoteItem::getPurchaseRequestId, request.getId())
            .eq(SupplierQuoteItem::getAwarded, 1)) > 0;
    }

    private String appendRemark(String oldRemark, String append) {
        if (!StringUtils.hasText(oldRemark)) {
            return append;
        }
        if (!StringUtils.hasText(append)) {
            return oldRemark;
        }
        String value = oldRemark + "；" + append;
        return value.length() > 500 ? value.substring(0, 500) : value;
    }

    @Override
    public WorkflowDashboardVO getDashboard() {
        AuthUser authUser = AuthContextHolder.requireUser();
        WorkflowDashboardVO vo = new WorkflowDashboardVO();

        if (DataScopeHelper.isSupplierUser(authUser)) {
            long quotingTotal = countQuotingRequests(authUser);
            vo.setQuotingRequestTotal(quotingTotal);
            vo.setComparedRequestTotal(countComparedRequests(authUser));
            vo.setMySubmittedQuoteTotal(supplierQuoteMapper.countSubmittedBySupplierUserId(authUser.getUserId()));
            vo.setMetrics(List.of(
                dashboardMetric("quoting", "待报价", String.valueOf(quotingTotal), "供应商报价队列", "warning"),
                dashboardMetric("submittedQuotes", "已提交报价", String.valueOf(vo.getMySubmittedQuoteTotal()), "本账号报价记录", "primary"),
                dashboardMetric("compared", "已进入比价", String.valueOf(vo.getComparedRequestTotal()), "已参与比价项目", "success")
            ));
            vo.setStatusItems(List.of());
            vo.setDepartmentItems(List.of());
            vo.setFunnelItems(List.of());
            vo.setRecentRequests(List.of());
            vo.setQuoteRequests(List.of());
            return vo;
        }

        List<PurchaseRequest> visibleRequests = dashboardVisibleRequests(authUser);
        vo.setMyRequestTotal(visibleRequests.stream()
            .filter(request -> authUser.getUserId().equals(request.getApplicantUserId()))
            .count());
        vo.setPendingMyApprovalTotal(countMyApprovalTasks(authUser));
        vo.setPendingApprovalTotal(visibleRequests.stream()
            .filter(request -> ProcurementWorkflow.STATUS_PENDING_APPROVAL.equals(request.getStatus())
                && ProcurementWorkflow.APPROVAL_PENDING.equals(request.getApprovalStatus()))
            .count());
        vo.setQuotingRequestTotal(visibleRequests.stream()
            .filter(request -> ProcurementWorkflow.STATUS_QUOTING.equals(request.getStatus()))
            .count());
        vo.setComparedRequestTotal(countComparedVisibleRequests(visibleRequests));
        fillProcurementDashboard(vo, visibleRequests);
        return vo;
    }

    private List<PurchaseRequest> dashboardVisibleRequests(AuthUser authUser) {
        LambdaQueryWrapper<PurchaseRequest> wrapper = new LambdaQueryWrapper<>();
        excludeNationalAggregates(wrapper);
        wrapper.orderByDesc(PurchaseRequest::getCreatedAt);
        if (DataScopeHelper.isSystemAdmin(authUser)) {
            wrapper.last("LIMIT 1000");
            return purchaseRequestMapper.selectList(wrapper);
        }
        return purchaseRequestMapper.selectList(wrapper).stream()
            .filter(request -> canViewRequest(request, authUser))
            .toList();
    }

    private long countComparedVisibleRequests(List<PurchaseRequest> visibleRequests) {
        if (visibleRequests.isEmpty()) {
            return 0;
        }
        Set<Long> visibleIds = visibleRequests.stream()
            .map(PurchaseRequest::getId)
            .filter(Objects::nonNull)
            .collect(HashSet::new, HashSet::add, HashSet::addAll);
        if (visibleIds.isEmpty()) {
            return 0;
        }
        return supplierQuoteMapper.selectList(new LambdaQueryWrapper<SupplierQuote>()
                .eq(SupplierQuote::getStatus, ProcurementWorkflow.QUOTE_STATUS_SUBMITTED))
            .stream()
            .map(SupplierQuote::getPurchaseRequestId)
            .filter(visibleIds::contains)
            .distinct()
            .count();
    }

    private void fillProcurementDashboard(WorkflowDashboardVO vo, List<PurchaseRequest> requests) {
        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        List<Long> requestIds = requests.stream()
            .map(PurchaseRequest::getId)
            .filter(Objects::nonNull)
            .toList();
        Map<Long, PurchaseRequest> requestById = requests.stream()
            .filter(request -> request.getId() != null)
            .collect(LinkedHashMap::new, (map, request) -> map.put(request.getId(), request), Map::putAll);
        List<PurchaseRequestItem> requestItems = dashboardRequestItems(requestIds);
        List<SupplierQuote> quotes = dashboardQuotes(requestIds);
        Map<Long, SupplierQuote> quoteById = quotes.stream()
            .filter(quote -> quote.getId() != null)
            .collect(LinkedHashMap::new, (map, quote) -> map.put(quote.getId(), quote), Map::putAll);
        Map<Long, Supplier> supplierById = dashboardSuppliers(quotes);
        List<SupplierQuoteItem> awardedItems = dashboardAwardedItems(requestIds);
        List<ProcurementAfterSale> approvedAfterSales = dashboardApprovedAfterSales(requestIds);

        BigDecimal demandAmount = sumRequestItemAmount(requestItems);
        BigDecimal awardedAmount = sumQuoteItemAmount(awardedItems);
        BigDecimal totalAmount = awardedAmount;
        Set<Long> monthRequestIds = requests.stream()
            .filter(request -> request.getCreatedAt() != null && !request.getCreatedAt().isBefore(monthStart))
            .map(PurchaseRequest::getId)
            .filter(Objects::nonNull)
            .collect(HashSet::new, HashSet::add, HashSet::addAll);
        BigDecimal monthAmount = sumQuoteItemAmount(awardedItems.stream()
            .filter(item -> monthRequestIds.contains(item.getPurchaseRequestId()))
            .toList());
        BigDecimal awardedQty = sumQuoteItemQty(awardedItems);
        BigDecimal averageLeadDays = averageAwardLeadDays(awardedItems, quoteById);
        BigDecimal averageWinRate = averageSupplierWinRate(quotes, awardedItems, quoteById);
        long openCount = requests.stream().filter(this::isOpenDashboardRequest).count();
        long approvedCount = requests.stream()
            .filter(request -> ProcurementWorkflow.STATUS_APPROVED.equals(request.getStatus()))
            .count();
        long returnedCount = requests.stream()
            .filter(request -> ProcurementWorkflow.STATUS_RETURNED.equals(request.getStatus())
                || ProcurementWorkflow.STATUS_REJECTED.equals(request.getStatus())
                || ProcurementWorkflow.STATUS_CANCELLED.equals(request.getStatus())
                || ProcurementWorkflow.STATUS_TERMINATED.equals(request.getStatus()))
            .count();
        long monthCount = requests.stream()
            .filter(request -> request.getCreatedAt() != null && !request.getCreatedAt().isBefore(monthStart))
            .count();

        vo.setTotalRequestCount(requests.size());
        vo.setOpenRequestCount(openCount);
        vo.setApprovedRequestTotal(approvedCount);
        vo.setReturnedRequestTotal(returnedCount);
        vo.setMonthRequestTotal(monthCount);
        vo.setTotalAmount(totalAmount);
        vo.setMonthAmount(monthAmount);
        vo.setAverageApprovalHours(averageApprovalHours(requests));
        vo.setMetrics(List.of(
            dashboardMetric("awardedAmount", "中标金额", moneyCompact(awardedAmount), "已中标商品报价金额", "primary"),
            dashboardMetric("awardedQty", "采购数量", quantityText(awardedQty), "已中标商品数量", "success"),
            dashboardMetric("supplierCount", "成交供应商", String.valueOf(awardedSupplierCount(awardedItems, quoteById)), "有中标商品的供应商", "primary"),
            dashboardMetric("averageWinRate", "平均中单率", percentText(averageWinRate), "供应商中标单数 / 报价单数", "warning"),
            dashboardMetric("averageLeadDays", "平均交期", averageLeadDays.setScale(1, RoundingMode.HALF_UP).toPlainString() + "天", "中标报价承诺交付周期", "success")
        ));
        vo.setStatusItems(buildStatusItems(requests));
        vo.setDepartmentItems(buildDepartmentSpendItems(requests, awardedItems, requestById));
        vo.setCityItems(buildCitySpendItems(requests, awardedItems, requestById));
        vo.setFunnelItems(buildFunnelItems(requests));
        vo.setRecentRequests(requests.stream()
            .limit(8)
            .map(request -> dashboardRequest(request, false))
            .toList());
        vo.setQuoteRequests(requests.stream()
            .filter(request -> ProcurementWorkflow.STATUS_QUOTING.equals(request.getStatus()))
            .sorted(Comparator.comparing(this::dashboardQuoteDueAt, Comparator.nullsLast(Comparator.naturalOrder())))
            .limit(6)
            .map(request -> dashboardRequest(request, true))
            .toList());
        vo.setProductItems(buildProductItems(requestItems, awardedItems, quoteById));
        vo.setSupplierItems(buildSupplierItems(quotes, awardedItems, quoteById, supplierById, approvedAfterSales));
        vo.setPurchaseLines(buildPurchaseLines(requests, requestItems, awardedItems, requestById, quoteById, supplierById));
        if (BigDecimal.ZERO.compareTo(demandAmount) < 0 && BigDecimal.ZERO.compareTo(awardedAmount) == 0) {
            vo.setTotalAmount(demandAmount);
            vo.setMonthAmount(sumRequestItemAmount(requestItems.stream()
                .filter(item -> monthRequestIds.contains(item.getPurchaseRequestId()))
                .toList()));
        }
    }

    private List<PurchaseRequestItem> dashboardRequestItems(List<Long> requestIds) {
        if (requestIds.isEmpty()) {
            return List.of();
        }
        return purchaseRequestItemMapper.selectList(new LambdaQueryWrapper<PurchaseRequestItem>()
            .in(PurchaseRequestItem::getPurchaseRequestId, requestIds)
            .and(wrapper -> wrapper.ne(PurchaseRequestItem::getStatus, ProcurementWorkflow.ITEM_STATUS_REJECTED)
                .ne(PurchaseRequestItem::getStatus, ProcurementWorkflow.ITEM_STATUS_RETURNED)
                .or()
                .isNull(PurchaseRequestItem::getStatus)));
    }

    private List<SupplierQuote> dashboardQuotes(List<Long> requestIds) {
        if (requestIds.isEmpty()) {
            return List.of();
        }
        return supplierQuoteMapper.selectList(new LambdaQueryWrapper<SupplierQuote>()
            .in(SupplierQuote::getPurchaseRequestId, requestIds));
    }

    private List<SupplierQuoteItem> dashboardAwardedItems(List<Long> requestIds) {
        if (requestIds.isEmpty()) {
            return List.of();
        }
        return supplierQuoteItemMapper.selectList(new LambdaQueryWrapper<SupplierQuoteItem>()
            .in(SupplierQuoteItem::getPurchaseRequestId, requestIds)
            .eq(SupplierQuoteItem::getAwarded, 1)
            .and(wrapper -> wrapper.eq(SupplierQuoteItem::getStatus, QUOTE_ITEM_STATUS_ACTIVE)
                .or()
                .isNull(SupplierQuoteItem::getStatus)));
    }

    private List<ProcurementAfterSale> dashboardApprovedAfterSales(List<Long> requestIds) {
        if (requestIds.isEmpty()) {
            return List.of();
        }
        return procurementAfterSaleMapper.selectList(new LambdaQueryWrapper<ProcurementAfterSale>()
            .in(ProcurementAfterSale::getPurchaseRequestId, requestIds)
            .eq(ProcurementAfterSale::getStatus, AFTER_SALE_STATUS_APPROVED));
    }

    private Map<Long, Supplier> dashboardSuppliers(List<SupplierQuote> quotes) {
        List<Long> supplierIds = quotes.stream()
            .map(SupplierQuote::getSupplierId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        if (supplierIds.isEmpty()) {
            return Map.of();
        }
        return supplierMapper.selectBatchIds(supplierIds).stream()
            .filter(supplier -> supplier.getId() != null)
            .collect(LinkedHashMap::new, (map, supplier) -> map.put(supplier.getId(), supplier), Map::putAll);
    }

    private List<WorkflowDashboardVO.DashboardProductVO> buildProductItems(List<PurchaseRequestItem> requestItems,
                                                                           List<SupplierQuoteItem> awardedItems,
                                                                           Map<Long, SupplierQuote> quoteById) {
        Map<String, DashboardProductAccumulator> grouped = new LinkedHashMap<>();
        for (PurchaseRequestItem item : requestItems) {
            String key = dashboardProductKey(item.getItemCode(), item.getItemName(), item.getSpecModel(), item.getUnit());
            DashboardProductAccumulator acc = grouped.computeIfAbsent(key, ignored -> new DashboardProductAccumulator(
                item.getItemCode(),
                item.getItemName(),
                item.getSpecModel(),
                item.getUnit()
            ));
            acc.requestedQty = acc.requestedQty.add(defaultAmount(item.getQty()));
            acc.requestAmount = acc.requestAmount.add(defaultAmount(item.getLineAmount()));
            if (item.getPurchaseRequestId() != null) {
                acc.requestIds.add(item.getPurchaseRequestId());
            }
        }
        for (SupplierQuoteItem item : awardedItems) {
            String key = dashboardProductKey(item.getItemCode(), item.getItemName(), item.getSpecModel(), item.getUnit());
            DashboardProductAccumulator acc = grouped.computeIfAbsent(key, ignored -> new DashboardProductAccumulator(
                item.getItemCode(),
                item.getItemName(),
                item.getSpecModel(),
                item.getUnit()
            ));
            acc.awardedQty = acc.awardedQty.add(defaultAmount(item.getQty()));
            acc.awardedAmount = acc.awardedAmount.add(defaultAmount(item.getLineAmount()));
            SupplierQuote quote = quoteById.get(item.getQuoteId());
            if (quote != null && quote.getSupplierId() != null) {
                acc.supplierIds.add(quote.getSupplierId());
            }
        }
        return grouped.entrySet().stream()
            .map(entry -> {
                DashboardProductAccumulator acc = entry.getValue();
                WorkflowDashboardVO.DashboardProductVO row = new WorkflowDashboardVO.DashboardProductVO();
                row.setKey(entry.getKey());
                row.setItemCode(acc.itemCode);
                row.setItemName(defaultDashboardText(acc.itemName, "未命名商品"));
                row.setSpecModel(defaultText(acc.specModel));
                row.setUnit(defaultText(acc.unit));
                row.setRequestedQty(acc.requestedQty.setScale(2, RoundingMode.HALF_UP));
                row.setAwardedQty(acc.awardedQty.setScale(2, RoundingMode.HALF_UP));
                row.setRequestAmount(acc.requestAmount.setScale(2, RoundingMode.HALF_UP));
                row.setAwardedAmount(acc.awardedAmount.setScale(2, RoundingMode.HALF_UP));
                row.setRequestCount(acc.requestIds.size());
                row.setSupplierCount(acc.supplierIds.size());
                return row;
            })
            .sorted(Comparator.comparing(WorkflowDashboardVO.DashboardProductVO::getAwardedAmount, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(WorkflowDashboardVO.DashboardProductVO::getRequestAmount, Comparator.nullsLast(Comparator.reverseOrder())))
            .limit(200)
            .toList();
    }

    private List<WorkflowDashboardVO.DashboardSupplierVO> buildSupplierItems(List<SupplierQuote> quotes,
                                                                            List<SupplierQuoteItem> awardedItems,
                                                                            Map<Long, SupplierQuote> quoteById,
                                                                            Map<Long, Supplier> supplierById,
                                                                            List<ProcurementAfterSale> approvedAfterSales) {
        Map<Long, DashboardSupplierAccumulator> grouped = new LinkedHashMap<>();
        for (SupplierQuote quote : quotes) {
            if (quote.getSupplierId() == null || !ProcurementWorkflow.QUOTE_STATUS_SUBMITTED.equals(quote.getStatus())) {
                continue;
            }
            DashboardSupplierAccumulator acc = grouped.computeIfAbsent(quote.getSupplierId(), DashboardSupplierAccumulator::new);
            if (quote.getPurchaseRequestId() != null) {
                acc.quoteRequestIds.add(quote.getPurchaseRequestId());
            }
        }
        for (SupplierQuoteItem item : awardedItems) {
            SupplierQuote quote = quoteById.get(item.getQuoteId());
            if (quote == null || quote.getSupplierId() == null) {
                continue;
            }
            DashboardSupplierAccumulator acc = grouped.computeIfAbsent(quote.getSupplierId(), DashboardSupplierAccumulator::new);
            if (item.getPurchaseRequestId() != null) {
                acc.awardedRequestIds.add(item.getPurchaseRequestId());
            }
            acc.awardedItemCount++;
            acc.awardedAmount = acc.awardedAmount.add(defaultAmount(item.getLineAmount()));
            Integer leadTime = item.getLeadTimeDays() != null ? item.getLeadTimeDays() : quote.getLeadTimeDays();
            if (leadTime != null && leadTime >= 0) {
                acc.leadTimeDays += leadTime;
                acc.leadTimeCount++;
            }
        }
        for (ProcurementAfterSale sale : approvedAfterSales) {
            if (sale.getSupplierId() == null) {
                continue;
            }
            DashboardSupplierAccumulator acc = grouped.computeIfAbsent(sale.getSupplierId(), DashboardSupplierAccumulator::new);
            if (isReturnAfterSaleType(sale.getAfterSaleType())) {
                acc.returnAfterSaleCount++;
            } else if (AFTER_SALE_TYPE_REPLACE_ITEM.equals(sale.getAfterSaleType())) {
                acc.replacementAfterSaleCount++;
            }
        }
        return grouped.values().stream()
            .map(acc -> {
                Supplier supplier = supplierById.get(acc.supplierId);
                WorkflowDashboardVO.DashboardSupplierVO row = new WorkflowDashboardVO.DashboardSupplierVO();
                row.setSupplierId(acc.supplierId);
                row.setSupplierName(supplier == null ? "供应商 " + acc.supplierId : defaultDashboardText(supplier.getSupplierName(), "供应商 " + acc.supplierId));
                row.setQuoteRequestCount(acc.quoteRequestIds.size());
                row.setAwardedRequestCount(acc.awardedRequestIds.size());
                row.setAwardedItemCount(acc.awardedItemCount);
                row.setAwardedAmount(acc.awardedAmount.setScale(2, RoundingMode.HALF_UP));
                row.setReturnAfterSaleCount(acc.returnAfterSaleCount);
                row.setReplacementAfterSaleCount(acc.replacementAfterSaleCount);
                BigDecimal winRate = acc.quoteRequestIds.isEmpty()
                    ? BigDecimal.ZERO
                    : BigDecimal.valueOf(acc.awardedRequestIds.size() * 100L)
                        .divide(BigDecimal.valueOf(acc.quoteRequestIds.size()), 1, RoundingMode.HALF_UP);
                row.setWinRate(winRate);
                row.setReturnRate(percentOf(acc.returnAfterSaleCount, acc.awardedItemCount));
                row.setReplacementRate(percentOf(acc.replacementAfterSaleCount, acc.awardedItemCount));
                BigDecimal leadDays = acc.leadTimeCount == 0
                    ? BigDecimal.ZERO
                    : BigDecimal.valueOf(acc.leadTimeDays).divide(BigDecimal.valueOf(acc.leadTimeCount), 1, RoundingMode.HALF_UP);
                row.setAverageLeadTimeDays(leadDays);
                row.setEfficiencyName(efficiencyName(leadDays));
                row.setEfficiencyScore(efficiencyScore(leadDays));
                return row;
            })
            .sorted(Comparator.comparing(WorkflowDashboardVO.DashboardSupplierVO::getAwardedAmount, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(WorkflowDashboardVO.DashboardSupplierVO::getWinRate, Comparator.nullsLast(Comparator.reverseOrder())))
            .limit(200)
            .toList();
    }

    private List<WorkflowDashboardVO.DashboardBreakdownVO> buildDepartmentSpendItems(List<PurchaseRequest> requests,
                                                                                     List<SupplierQuoteItem> awardedItems,
                                                                                     Map<Long, PurchaseRequest> requestById) {
        Map<String, DashboardSpendAccumulator> grouped = new LinkedHashMap<>();
        for (SupplierQuoteItem item : awardedItems) {
            PurchaseRequest request = requestById.get(item.getPurchaseRequestId());
            String key = request == null || !StringUtils.hasText(request.getDeptName()) ? "未填写部门" : request.getDeptName().trim();
            DashboardSpendAccumulator acc = grouped.computeIfAbsent(key, DashboardSpendAccumulator::new);
            acc.amount = acc.amount.add(defaultAmount(item.getLineAmount()));
            if (item.getPurchaseRequestId() != null) {
                acc.requestIds.add(item.getPurchaseRequestId());
            }
        }
        if (grouped.isEmpty()) {
            return buildDepartmentItems(requests);
        }
        return buildSpendBreakdown(grouped, "primary");
    }

    private List<WorkflowDashboardVO.DashboardBreakdownVO> buildCitySpendItems(List<PurchaseRequest> requests,
                                                                               List<SupplierQuoteItem> awardedItems,
                                                                               Map<Long, PurchaseRequest> requestById) {
        Map<String, DashboardSpendAccumulator> grouped = new LinkedHashMap<>();
        for (SupplierQuoteItem item : awardedItems) {
            PurchaseRequest request = requestById.get(item.getPurchaseRequestId());
            String city = firstText(item.getReceivingCity(), request == null ? null : dashboardReceivingCityFromRequest(request), "未填写城市");
            DashboardSpendAccumulator acc = grouped.computeIfAbsent(city, DashboardSpendAccumulator::new);
            acc.amount = acc.amount.add(defaultAmount(item.getLineAmount()));
            if (item.getPurchaseRequestId() != null) {
                acc.requestIds.add(item.getPurchaseRequestId());
            }
        }
        if (grouped.isEmpty()) {
            for (PurchaseRequest request : requests) {
                String city = firstText(dashboardReceivingCityFromRequest(request), "未填写城市");
                DashboardSpendAccumulator acc = grouped.computeIfAbsent(city, DashboardSpendAccumulator::new);
                acc.amount = acc.amount.add(defaultAmount(request.getTotalAmount()));
                if (request.getId() != null) {
                    acc.requestIds.add(request.getId());
                }
            }
        }
        return buildSpendBreakdown(grouped, "success");
    }

    private List<WorkflowDashboardVO.DashboardBreakdownVO> buildSpendBreakdown(Map<String, DashboardSpendAccumulator> grouped, String tone) {
        return grouped.values().stream()
            .map(acc -> {
                WorkflowDashboardVO.DashboardBreakdownVO item = new WorkflowDashboardVO.DashboardBreakdownVO();
                item.setKey(acc.label);
                item.setLabel(acc.label);
                item.setCount(acc.requestIds.size());
                item.setAmount(acc.amount.setScale(2, RoundingMode.HALF_UP));
                item.setTone(tone);
                return item;
            })
            .sorted(Comparator.comparing(WorkflowDashboardVO.DashboardBreakdownVO::getAmount, Comparator.nullsLast(Comparator.reverseOrder())))
            .limit(12)
            .toList();
    }

    private List<WorkflowDashboardVO.DashboardPurchaseLineVO> buildPurchaseLines(List<PurchaseRequest> requests,
                                                                                List<PurchaseRequestItem> requestItems,
                                                                                List<SupplierQuoteItem> awardedItems,
                                                                                Map<Long, PurchaseRequest> requestById,
                                                                                Map<Long, SupplierQuote> quoteById,
                                                                                Map<Long, Supplier> supplierById) {
        if (!awardedItems.isEmpty()) {
            return awardedItems.stream()
                .map(item -> {
                    SupplierQuote quote = quoteById.get(item.getQuoteId());
                    PurchaseRequest request = requestById.get(item.getPurchaseRequestId());
                    Supplier supplier = quote == null ? null : supplierById.get(quote.getSupplierId());
                    WorkflowDashboardVO.DashboardPurchaseLineVO row = new WorkflowDashboardVO.DashboardPurchaseLineVO();
                    row.setRequestId(item.getPurchaseRequestId());
                    row.setReqNo(request == null ? "" : request.getReqNo());
                    row.setReqTitle(request == null ? "" : displayReqTitle(request.getReqTitle()));
                    row.setDeptName(request == null ? "" : request.getDeptName());
                    row.setReceivingCity(firstText(item.getReceivingCity(), request == null ? null : dashboardReceivingCityFromRequest(request), ""));
                    row.setItemCode(item.getItemCode());
                    row.setItemName(defaultDashboardText(item.getItemName(), "未命名商品"));
                    row.setSpecModel(defaultText(item.getSpecModel()));
                    row.setQty(defaultAmount(item.getQty()));
                    row.setUnit(defaultText(item.getUnit()));
                    row.setAmount(defaultAmount(item.getLineAmount()));
                    row.setSupplierName(supplier == null ? "" : defaultText(supplier.getSupplierName()));
                    row.setLeadTimeDays(item.getLeadTimeDays() != null ? item.getLeadTimeDays() : quote == null ? null : quote.getLeadTimeDays());
                    row.setStatusName("已中标");
                    row.setOccurredAt(quote == null ? null : quote.getSubmittedAt());
                    return row;
                })
                .sorted(Comparator.comparing(WorkflowDashboardVO.DashboardPurchaseLineVO::getOccurredAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(300)
                .toList();
        }
        Map<Long, PurchaseRequest> fallbackRequestById = requests.stream()
            .filter(request -> request.getId() != null)
            .collect(LinkedHashMap::new, (map, request) -> map.put(request.getId(), request), Map::putAll);
        return requestItems.stream()
            .map(item -> {
                PurchaseRequest request = fallbackRequestById.get(item.getPurchaseRequestId());
                WorkflowDashboardVO.DashboardPurchaseLineVO row = new WorkflowDashboardVO.DashboardPurchaseLineVO();
                row.setRequestId(item.getPurchaseRequestId());
                row.setReqNo(request == null ? "" : request.getReqNo());
                row.setReqTitle(request == null ? "" : displayReqTitle(request.getReqTitle()));
                row.setDeptName(request == null ? "" : request.getDeptName());
                row.setReceivingCity(firstText(item.getReceivingCity(), request == null ? null : dashboardReceivingCityFromRequest(request), ""));
                row.setItemCode(item.getItemCode());
                row.setItemName(defaultDashboardText(item.getItemName(), "未命名商品"));
                row.setSpecModel(defaultText(item.getSpecModel()));
                row.setQty(defaultAmount(item.getQty()));
                row.setUnit(defaultText(item.getUnit()));
                row.setAmount(defaultAmount(item.getLineAmount()));
                row.setSupplierName("");
                row.setLeadTimeDays(null);
                row.setStatusName("需求");
                row.setOccurredAt(request == null ? null : request.getCreatedAt());
                return row;
            })
            .sorted(Comparator.comparing(WorkflowDashboardVO.DashboardPurchaseLineVO::getOccurredAt, Comparator.nullsLast(Comparator.reverseOrder())))
            .limit(300)
            .toList();
    }

    private BigDecimal sumRequestItemAmount(List<PurchaseRequestItem> items) {
        return items.stream()
            .map(PurchaseRequestItem::getLineAmount)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal sumQuoteItemAmount(List<SupplierQuoteItem> items) {
        return items.stream()
            .map(SupplierQuoteItem::getLineAmount)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal sumQuoteItemQty(List<SupplierQuoteItem> items) {
        return items.stream()
            .map(SupplierQuoteItem::getQty)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);
    }

    private long awardedSupplierCount(List<SupplierQuoteItem> awardedItems, Map<Long, SupplierQuote> quoteById) {
        return awardedItems.stream()
            .map(item -> quoteById.get(item.getQuoteId()))
            .filter(Objects::nonNull)
            .map(SupplierQuote::getSupplierId)
            .filter(Objects::nonNull)
            .distinct()
            .count();
    }

    private BigDecimal averageAwardLeadDays(List<SupplierQuoteItem> awardedItems, Map<Long, SupplierQuote> quoteById) {
        List<Integer> days = awardedItems.stream()
            .map(item -> {
                if (item.getLeadTimeDays() != null) {
                    return item.getLeadTimeDays();
                }
                SupplierQuote quote = quoteById.get(item.getQuoteId());
                return quote == null ? null : quote.getLeadTimeDays();
            })
            .filter(Objects::nonNull)
            .filter(value -> value >= 0)
            .toList();
        if (days.isEmpty()) {
            return BigDecimal.ZERO;
        }
        int total = days.stream().mapToInt(Integer::intValue).sum();
        return BigDecimal.valueOf(total).divide(BigDecimal.valueOf(days.size()), 1, RoundingMode.HALF_UP);
    }

    private BigDecimal averageSupplierWinRate(List<SupplierQuote> quotes,
                                              List<SupplierQuoteItem> awardedItems,
                                              Map<Long, SupplierQuote> quoteById) {
        List<WorkflowDashboardVO.DashboardSupplierVO> supplierItems = buildSupplierItems(quotes, awardedItems, quoteById, Map.of(), List.of());
        if (supplierItems.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal total = supplierItems.stream()
            .map(WorkflowDashboardVO.DashboardSupplierVO::getWinRate)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.divide(BigDecimal.valueOf(supplierItems.size()), 1, RoundingMode.HALF_UP);
    }

    private String dashboardProductKey(String itemCode, String itemName, String specModel, String unit) {
        return safe(itemCode).trim() + "|" + safe(itemName).trim() + "|" + safe(specModel).trim() + "|" + safe(unit).trim();
    }

    private String dashboardReceivingCityFromRequest(PurchaseRequest request) {
        return request == null ? null : dashboardReceivingCity(request);
    }

    private String quantityText(BigDecimal value) {
        BigDecimal qty = value == null ? BigDecimal.ZERO : value;
        return qty.stripTrailingZeros().toPlainString();
    }

    private String percentText(BigDecimal value) {
        BigDecimal percent = value == null ? BigDecimal.ZERO : value;
        return percent.setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + "%";
    }

    private BigDecimal percentOf(long numerator, long denominator) {
        if (denominator <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(numerator * 100L)
            .divide(BigDecimal.valueOf(denominator), 1, RoundingMode.HALF_UP);
    }

    private boolean isReturnAfterSaleType(String type) {
        return AFTER_SALE_TYPE_NO_REASON_RETURN.equals(type) || AFTER_SALE_TYPE_RETURN_EXCHANGE.equals(type);
    }

    private String defaultDashboardText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String efficiencyName(BigDecimal leadDays) {
        if (leadDays == null || BigDecimal.ZERO.compareTo(leadDays) == 0) {
            return "暂无数据";
        }
        if (leadDays.compareTo(BigDecimal.valueOf(3)) <= 0) {
            return "很快";
        }
        if (leadDays.compareTo(BigDecimal.valueOf(7)) <= 0) {
            return "正常";
        }
        if (leadDays.compareTo(BigDecimal.valueOf(14)) <= 0) {
            return "偏慢";
        }
        return "需关注";
    }

    private Integer efficiencyScore(BigDecimal leadDays) {
        if (leadDays == null || BigDecimal.ZERO.compareTo(leadDays) == 0) {
            return 0;
        }
        if (leadDays.compareTo(BigDecimal.valueOf(3)) <= 0) {
            return 95;
        }
        if (leadDays.compareTo(BigDecimal.valueOf(7)) <= 0) {
            return 85;
        }
        if (leadDays.compareTo(BigDecimal.valueOf(14)) <= 0) {
            return 70;
        }
        return 55;
    }

    private WorkflowDashboardVO.DashboardMetricVO dashboardMetric(String key,
                                                                  String label,
                                                                  String value,
                                                                  String note,
                                                                  String tone) {
        WorkflowDashboardVO.DashboardMetricVO metric = new WorkflowDashboardVO.DashboardMetricVO();
        metric.setKey(key);
        metric.setLabel(label);
        metric.setValue(value);
        metric.setNote(note);
        metric.setTone(tone);
        return metric;
    }

    private List<WorkflowDashboardVO.DashboardBreakdownVO> buildStatusItems(List<PurchaseRequest> requests) {
        List<WorkflowDashboardVO.DashboardBreakdownVO> items = new ArrayList<>();
        items.add(dashboardBreakdown("draft", "草稿", requests, ProcurementWorkflow.STATUS_DRAFT, "info"));
        items.add(dashboardBreakdown("pending", "审批中", requests, ProcurementWorkflow.STATUS_PENDING_APPROVAL, "warning"));
        items.add(dashboardBreakdown("pool", "需求池", requests, ProcurementWorkflow.STATUS_DEMAND_POOL, "primary"));
        items.add(dashboardBreakdown("quoting", "供应商报价", requests, ProcurementWorkflow.STATUS_QUOTING, "success"));
        items.add(dashboardBreakdown("approved", "已归档", requests, ProcurementWorkflow.STATUS_APPROVED, "success"));
        WorkflowDashboardVO.DashboardBreakdownVO returned = new WorkflowDashboardVO.DashboardBreakdownVO();
        returned.setKey("returned");
        returned.setLabel("退回/关闭/终止");
        returned.setCount(requests.stream()
            .filter(request -> ProcurementWorkflow.STATUS_RETURNED.equals(request.getStatus())
                || ProcurementWorkflow.STATUS_REJECTED.equals(request.getStatus())
                || ProcurementWorkflow.STATUS_CANCELLED.equals(request.getStatus())
                || ProcurementWorkflow.STATUS_TERMINATED.equals(request.getStatus()))
            .count());
        returned.setAmount(sumRequestAmount(requests.stream()
            .filter(request -> ProcurementWorkflow.STATUS_RETURNED.equals(request.getStatus())
                || ProcurementWorkflow.STATUS_REJECTED.equals(request.getStatus())
                || ProcurementWorkflow.STATUS_CANCELLED.equals(request.getStatus())
                || ProcurementWorkflow.STATUS_TERMINATED.equals(request.getStatus()))
            .toList()));
        returned.setTone("danger");
        items.add(returned);
        return items;
    }

    private WorkflowDashboardVO.DashboardBreakdownVO dashboardBreakdown(String key,
                                                                        String label,
                                                                        List<PurchaseRequest> requests,
                                                                        String status,
                                                                        String tone) {
        List<PurchaseRequest> matched = requests.stream()
            .filter(request -> status.equals(request.getStatus()))
            .toList();
        WorkflowDashboardVO.DashboardBreakdownVO item = new WorkflowDashboardVO.DashboardBreakdownVO();
        item.setKey(key);
        item.setLabel(label);
        item.setCount(matched.size());
        item.setAmount(sumRequestAmount(matched));
        item.setTone(tone);
        return item;
    }

    private List<WorkflowDashboardVO.DashboardBreakdownVO> buildFunnelItems(List<PurchaseRequest> requests) {
        return List.of(
            dashboardFunnel("submitted", "已提交", requests.stream()
                .filter(request -> request.getSubmittedAt() != null || !ProcurementWorkflow.STATUS_DRAFT.equals(request.getStatus()))
                .count(), sumRequestAmount(requests), "primary"),
            dashboardFunnel("approval", "审批中", requests.stream()
                .filter(request -> ProcurementWorkflow.STATUS_PENDING_APPROVAL.equals(request.getStatus()))
                .count(), sumRequestAmountByStatus(requests, ProcurementWorkflow.STATUS_PENDING_APPROVAL), "warning"),
            dashboardFunnel("pool", "需求池", requests.stream()
                .filter(request -> ProcurementWorkflow.STATUS_DEMAND_POOL.equals(request.getStatus()))
                .count(), sumRequestAmountByStatus(requests, ProcurementWorkflow.STATUS_DEMAND_POOL), "primary"),
            dashboardFunnel("quote", "供应商报价", requests.stream()
                .filter(request -> ProcurementWorkflow.STATUS_QUOTING.equals(request.getStatus()))
                .count(), sumRequestAmountByStatus(requests, ProcurementWorkflow.STATUS_QUOTING), "success"),
            dashboardFunnel("archive", "已归档", requests.stream()
                .filter(request -> ProcurementWorkflow.STATUS_APPROVED.equals(request.getStatus()))
                .count(), sumRequestAmountByStatus(requests, ProcurementWorkflow.STATUS_APPROVED), "success")
        );
    }

    private WorkflowDashboardVO.DashboardBreakdownVO dashboardFunnel(String key,
                                                                     String label,
                                                                     long count,
                                                                     BigDecimal amount,
                                                                     String tone) {
        WorkflowDashboardVO.DashboardBreakdownVO item = new WorkflowDashboardVO.DashboardBreakdownVO();
        item.setKey(key);
        item.setLabel(label);
        item.setCount(count);
        item.setAmount(amount);
        item.setTone(tone);
        return item;
    }

    private List<WorkflowDashboardVO.DashboardBreakdownVO> buildDepartmentItems(List<PurchaseRequest> requests) {
        Map<String, List<PurchaseRequest>> grouped = requests.stream()
            .collect(LinkedHashMap::new, (map, request) -> {
                String key = StringUtils.hasText(request.getDeptName()) ? request.getDeptName().trim() : "未填写部门";
                map.computeIfAbsent(key, ignored -> new ArrayList<>()).add(request);
            }, Map::putAll);
        return grouped.entrySet().stream()
            .map(entry -> {
                WorkflowDashboardVO.DashboardBreakdownVO item = new WorkflowDashboardVO.DashboardBreakdownVO();
                item.setKey(entry.getKey());
                item.setLabel(entry.getKey());
                item.setCount(entry.getValue().size());
                item.setAmount(sumRequestAmount(entry.getValue()));
                item.setTone("primary");
                return item;
            })
            .sorted(Comparator.comparing(WorkflowDashboardVO.DashboardBreakdownVO::getAmount, Comparator.nullsLast(Comparator.reverseOrder())))
            .limit(6)
            .toList();
    }

    private WorkflowDashboardVO.DashboardRequestVO dashboardRequest(PurchaseRequest request, boolean includeQuoteCount) {
        WorkflowDashboardVO.DashboardRequestVO row = new WorkflowDashboardVO.DashboardRequestVO();
        row.setId(request.getId());
        row.setReqNo(request.getReqNo());
        row.setReqTitle(displayReqTitle(request.getReqTitle()));
        row.setDeptName(request.getDeptName());
        row.setApplicantName(userDisplayNameById(request.getApplicantUserId()));
        row.setReceivingCity(dashboardReceivingCity(request));
        row.setTotalAmount(defaultAmount(request.getTotalAmount()));
        row.setStatus(request.getStatus());
        row.setStatusName(dashboardStatusName(request));
        row.setCurrentNodeName(dashboardNodeName(request));
        row.setCurrentNodeNo(request.getCurrentNodeNo());
        row.setCreatedAt(request.getCreatedAt());
        row.setSubmittedAt(request.getSubmittedAt());
        row.setDueAt(includeQuoteCount ? dashboardQuoteDueAt(request) : request.getNeedDate() == null ? null : request.getNeedDate().atStartOfDay());
        if (includeQuoteCount) {
            row.setQuoteCount(supplierQuoteMapper.selectCount(new LambdaQueryWrapper<SupplierQuote>()
                .eq(SupplierQuote::getPurchaseRequestId, request.getId())
                .eq(SupplierQuote::getStatus, ProcurementWorkflow.QUOTE_STATUS_SUBMITTED)));
        }
        return row;
    }

    private boolean isOpenDashboardRequest(PurchaseRequest request) {
        return ProcurementWorkflow.STATUS_PENDING_APPROVAL.equals(request.getStatus())
            || ProcurementWorkflow.STATUS_DEMAND_POOL.equals(request.getStatus())
            || ProcurementWorkflow.STATUS_QUOTING.equals(request.getStatus())
            || ProcurementWorkflow.STATUS_RETURNED.equals(request.getStatus());
    }

    private BigDecimal averageApprovalHours(List<PurchaseRequest> requests) {
        List<BigDecimal> hours = requests.stream()
            .filter(request -> request.getSubmittedAt() != null && request.getApprovedAt() != null)
            .map(request -> BigDecimal.valueOf(Duration.between(request.getSubmittedAt(), request.getApprovedAt()).toMinutes())
                .divide(BigDecimal.valueOf(60), 1, RoundingMode.HALF_UP))
            .toList();
        if (hours.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal total = hours.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.divide(BigDecimal.valueOf(hours.size()), 1, RoundingMode.HALF_UP);
    }

    private BigDecimal sumRequestAmountByStatus(List<PurchaseRequest> requests, String status) {
        return sumRequestAmount(requests.stream()
            .filter(request -> status.equals(request.getStatus()))
            .toList());
    }

    private BigDecimal sumRequestAmount(List<PurchaseRequest> requests) {
        return requests.stream()
            .map(PurchaseRequest::getTotalAmount)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal defaultAmount(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount.setScale(2, RoundingMode.HALF_UP);
    }

    private String moneyCompact(BigDecimal amount) {
        BigDecimal value = amount == null ? BigDecimal.ZERO : amount;
        if (value.compareTo(new BigDecimal("10000")) >= 0) {
            return value.divide(new BigDecimal("10000"), 1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + "万";
        }
        return value.setScale(0, RoundingMode.HALF_UP).toPlainString();
    }

    private String dashboardStatusName(PurchaseRequest request) {
        return switch (request.getStatus()) {
            case ProcurementWorkflow.STATUS_DRAFT -> "草稿";
            case ProcurementWorkflow.STATUS_PENDING_APPROVAL -> "审批中";
            case ProcurementWorkflow.STATUS_DEMAND_POOL -> "需求池";
            case ProcurementWorkflow.STATUS_DEMAND_AGGREGATED -> "已汇总";
            case ProcurementWorkflow.STATUS_QUOTING -> "询价中";
            case ProcurementWorkflow.STATUS_APPROVED -> "已归档";
            case ProcurementWorkflow.STATUS_RETURNED -> "已退回";
            case ProcurementWorkflow.STATUS_REJECTED -> "已驳回";
            case ProcurementWorkflow.STATUS_CANCELLED -> "已关闭";
            case ProcurementWorkflow.STATUS_TERMINATED -> "已终止";
            default -> defaultText(request.getStatus());
        };
    }

    private String dashboardNodeName(PurchaseRequest request) {
        if (ProcurementWorkflow.STATUS_PENDING_APPROVAL.equals(request.getStatus()) && request.getCurrentNodeNo() != null) {
            return ProcurementWorkflow.nodeNameForNode(request.getCurrentNodeNo());
        }
        return switch (request.getStatus()) {
            case ProcurementWorkflow.STATUS_DRAFT -> "申请草稿";
            case ProcurementWorkflow.STATUS_DEMAND_POOL -> "需求池";
            case ProcurementWorkflow.STATUS_DEMAND_AGGREGATED -> "需求汇总";
            case ProcurementWorkflow.STATUS_QUOTING -> "供应商报价";
            case ProcurementWorkflow.STATUS_APPROVED -> "归档";
            case ProcurementWorkflow.STATUS_RETURNED -> "申请人补充";
            case ProcurementWorkflow.STATUS_REJECTED -> "流程结束";
            case ProcurementWorkflow.STATUS_CANCELLED -> "申请已关闭";
            case ProcurementWorkflow.STATUS_TERMINATED -> "流程终止";
            default -> "采购流程";
        };
    }

    private LocalDateTime dashboardQuoteDueAt(PurchaseRequest request) {
        return quoteDeadlineAt(request, quoteOpenDays());
    }

    private String dashboardReceivingCity(PurchaseRequest request) {
        PurchaseRequestVO detail = purchaseRequestService.detail(request.getId());
        return detail.getReceivingCity();
    }

    @Override
    public List<ApprovalRecordVO> listApprovalRecords(Long purchaseRequestId) {
        AuthUser authUser = AuthContextHolder.requireUser();
        ensureNonSupplier(authUser);

        PurchaseRequest request = purchaseRequestMapper.selectById(purchaseRequestId);
        if (request == null) {
            throw new BizException("PURCHASE_REQUEST_NOT_FOUND", "Purchase request not found");
        }
        ensureRequestVisible(request, authUser);

        return approvalRecordMapper.selectList(new LambdaQueryWrapper<ApprovalRecord>()
                .eq(ApprovalRecord::getPurchaseRequestId, purchaseRequestId)
                .eq(ApprovalRecord::getStatus, "ACTIVE")
                .orderByDesc(ApprovalRecord::getOperatedAt)
                .orderByDesc(ApprovalRecord::getId))
            .stream()
            .filter(record -> shouldExposeApprovalRecord(request, record))
            .map(this::toApprovalRecordVO)
            .toList();
    }

    @Override
    public SupplierQuoteSummaryVO getQuoteSummary(Long purchaseRequestId) {
        AuthUser authUser = AuthContextHolder.requireUser();
        if (DataScopeHelper.isSupplierUser(authUser)
            && !SupplierQuoteAccess.canViewAllSubmissions(authUser)) {
            throw new BizException("FORBIDDEN", "Supplier account cannot access all supplier quote submissions");
        }

        PurchaseRequest request = purchaseRequestMapper.selectById(purchaseRequestId);
        if (request == null) {
            throw new BizException("PURCHASE_REQUEST_NOT_FOUND", "Purchase request not found");
        }
        if (!SupplierQuoteAccess.canViewAllSubmissions(authUser)) {
            ensureRequestVisible(request, authUser);
        }
        return buildQuoteSummary(request, authUser);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SupplierQuoteSummaryVO confirmWinningQuote(Long purchaseRequestId, WorkflowQuoteConfirmDTO dto) {
        AuthUser authUser = AuthContextHolder.requireUser();
        ensureNonSupplier(authUser);

        PurchaseRequest request = purchaseRequestMapper.selectById(purchaseRequestId);
        if (request == null) {
            throw new BizException("PURCHASE_REQUEST_NOT_FOUND", "Purchase request not found");
        }
        ensureRequestVisible(request, authUser);
        if (!DataScopeHelper.isSystemAdmin(authUser) && !authUser.getUserId().equals(request.getApplicantUserId())) {
            throw new BizException("FORBIDDEN", "Only the original applicant can confirm replacement items before quote approval");
        }
        if (!ProcurementWorkflow.STATUS_QUOTING.equals(request.getStatus())) {
            throw new BizException("PURCHASE_REQUEST_STATUS_INVALID", "Only quoting requests can be submitted for quote approval");
        }

        submitQuoteReview(
            request,
            dto == null ? null : dto.getAwardStrategy(),
            dto == null ? null : dto.getLeaderConfirmRemark(),
            authUser.getUserId(),
            resolveSubmitterRoleCode(authUser)
        );
        return buildQuoteSummary(request, authUser);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PurchaseRequestVO extendQuoteDeadline(Long purchaseRequestId, WorkflowQuoteDeadlineExtendDTO dto) {
        AuthUser authUser = AuthContextHolder.requireUser();
        ensureNonSupplier(authUser);
        if (!DataScopeHelper.isSystemAdmin(authUser)) {
            throw new BizException("FORBIDDEN", "Only system administrators can extend quote deadlines");
        }
        PurchaseRequest request = purchaseRequestMapper.selectById(purchaseRequestId);
        if (request == null) {
            throw new BizException("PURCHASE_REQUEST_NOT_FOUND", "Purchase request not found");
        }
        if (!ProcurementWorkflow.STATUS_QUOTING.equals(request.getStatus())) {
            throw new BizException("PURCHASE_REQUEST_STATUS_INVALID", "Only quoting requests can be extended");
        }
        int days = dto == null || dto.getDays() == null ? 0 : dto.getDays();
        if (days <= 0 || days > 60) {
            throw new BizException("QUOTE_DEADLINE_EXTENSION_INVALID", "Extension days must be between 1 and 60");
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime currentDeadline = quoteDeadlineAt(request, quoteOpenDays());
        LocalDateTime baseTime = currentDeadline != null && currentDeadline.isAfter(now) ? currentDeadline : now;
        LocalDateTime extendedDeadline = baseTime.plusDays(days);
        request.setQuoteCountdownStartedAt(null);
        request.setQuoteCountdownDueAt(extendedDeadline);
        request.setRemark(appendRemark(request.getRemark(), "管理员延长报价截止时间至 " + extendedDeadline));
        purchaseRequestMapper.updateById(request);

        PurchaseRequestVO vo = purchaseRequestService.detail(purchaseRequestId);
        vo.setQuoteCountdownStartedAt(request.getQuoteCountdownStartedAt());
        vo.setQuoteCountdownDueAt(extendedDeadline);
        return vo;
    }

    @Scheduled(cron = "0 */5 * * * ?", zone = "Asia/Shanghai")
    public void autoSubmitDueQuoteReviews() {
        int openDays = quoteOpenDays();
        LocalDateTime now = LocalDateTime.now();
        List<PurchaseRequest> requests = purchaseRequestMapper.selectList(new LambdaQueryWrapper<PurchaseRequest>()
            .eq(PurchaseRequest::getStatus, ProcurementWorkflow.STATUS_QUOTING)
            .eq(PurchaseRequest::getApprovalStatus, ProcurementWorkflow.APPROVAL_APPROVED));
        for (PurchaseRequest request : requests) {
            try {
                QuoteReviewDue due = quoteReviewDue(request, openDays, now);
                if (due == null || !StringUtils.hasText(due.remark)) {
                    continue;
                }
                submitQuoteReview(
                    request,
                    request.getAwardStrategy(),
                    due.remark,
                    request.getApplicantUserId(),
                    "SYSTEM",
                    due.allowQuoteShortage
                );
            } catch (BizException ex) {
                log.warn(
                    "Automatic quote review skipped for request {} ({}): {} - {}",
                    request.getReqNo(),
                    request.getId(),
                    ex.getCode(),
                    ex.getMessage()
                );
            } catch (RuntimeException ex) {
                log.error(
                    "Automatic quote review failed for request {} ({})",
                    request.getReqNo(),
                    request.getId(),
                    ex
                );
            }
        }
    }

    private void submitQuoteReview(
        PurchaseRequest request,
        String requestedAwardStrategy,
        String leaderConfirmRemark,
        Long actionUserId,
        String actionRoleCode
    ) {
        submitQuoteReview(request, requestedAwardStrategy, leaderConfirmRemark, actionUserId, actionRoleCode, false);
    }

    private void submitQuoteReview(
        PurchaseRequest request,
        String requestedAwardStrategy,
        String leaderConfirmRemark,
        Long actionUserId,
        String actionRoleCode,
        boolean allowQuoteShortage
    ) {
        Long purchaseRequestId = request.getId();
        reconcileQuoteItemsWithActiveRequestItems(request);
        List<SupplierQuote> requestQuotes = supplierQuoteMapper.selectList(new LambdaQueryWrapper<SupplierQuote>()
            .eq(SupplierQuote::getPurchaseRequestId, purchaseRequestId)
            .eq(SupplierQuote::getStatus, ProcurementWorkflow.QUOTE_STATUS_SUBMITTED));
        LocalDateTime now = LocalDateTime.now();
        boolean directAwardRequest = isDirectAwardRequest(request);
        if (directAwardRequest) {
            SupplierQuote directQuote = resolveDirectAwardQuote(request, requestQuotes);
            if (directAwardQuoteExceedsLimit(request, directQuote)) {
                downgradeDirectAwardToThreeQuotes(request, directQuote, "定向供应商报价超过低额紧急采购上限，转入三方比价");
                directAwardRequest = false;
            }
        }
        boolean directAwardScoped = directAwardRequest;
        List<SupplierQuote> eligibleQuotes = requestQuotes.stream()
            .filter(quote -> !isSupplierBiddingBlocked(quote.getSupplierId(), now))
            .filter(temporarySupplierRecommendationService::isQuoteAwardEligible)
            .filter(quote -> !directAwardScoped || Objects.equals(quote.getSupplierId(), request.getDirectAwardSupplierId()))
            .toList();
        if (!directAwardRequest && eligibleQuotes.isEmpty()) {
            throw new BizException("SUPPLIER_QUOTE_NOT_FOUND", "No available supplier quote");
        }
        if (!directAwardRequest && !allowQuoteShortage && eligibleQuotes.size() < 3) {
            throw new BizException("QUOTE_COUNT_NOT_ENOUGH", "At least three supplier quotes are required before quote review");
        }
        if (hasConfirmedAward(request)) {
            throw new BizException("SUPPLIER_ALREADY_CONFIRMED", "Awarded supplier has already confirmed and cannot be changed");
        }
        if (!directAwardRequest) {
            adjustReplacementOriginalPrices(purchaseRequestId, eligibleQuotes);
        }

        String awardStrategy = ProcurementWorkflow.resolveAutoAwardStrategy(awardStrategyAmount(request));
        SupplierQuote targetQuote = null;
        BigDecimal reviewAmount;

        resetAwardMarkers(purchaseRequestId, requestQuotes);
        if (directAwardRequest) {
            targetQuote = resolveDirectAwardQuote(request, eligibleQuotes);
            if (targetQuote == null) {
                throw new BizException("DIRECT_AWARD_QUOTE_NOT_READY", "低额紧急定向供应商尚未完成有效报价");
            }
            awardStrategy = ProcurementWorkflow.AWARD_STRATEGY_TOTAL_LOWEST;
            markQuoteItemsAwarded(request, targetQuote.getId());
            reviewAmount = targetQuote.getTotalAmount();
        } else if (ProcurementWorkflow.isItemLowestAwardStrategy(awardStrategy)) {
            if (!allowQuoteShortage) {
                ensureEveryActiveItemHasQuote(request, eligibleQuotes);
            }
            reviewAmount = applyItemLowestAwards(request, eligibleQuotes);
        } else {
            if (!allowQuoteShortage) {
                ensureEveryActiveItemHasQuote(request, eligibleQuotes);
            }
            targetQuote = resolveTotalLowestQuote(request, eligibleQuotes);
            if (targetQuote == null) {
                throw new BizException("SUPPLIER_QUOTE_NOT_FOUND", "No available supplier quote");
            }
            markQuoteItemsAwarded(request, targetQuote.getId());
            reviewAmount = targetQuote.getTotalAmount();
        }
        for (SupplierQuote quote : requestQuotes) {
            quote.setSupplierConfirmStatus(ProcurementWorkflow.SUPPLIER_CONFIRM_STATUS_UNSELECTED);
            quote.setSupplierConfirmRemark(null);
            quote.setSupplierConfirmedBy(null);
            quote.setSupplierConfirmedAt(null);
            quote.setSupplierConfirmDueAt(null);
            quote.setSupplierConfirmNotifiedAt(null);
            supplierQuoteMapper.updateById(quote);
        }

        LocalDateTime actionTime = LocalDateTime.now();
        request.setAwardStrategy(awardStrategy);
        request.setSelectedQuoteId(targetQuote == null ? null : targetQuote.getId());
        if (hasAwardedReplacementItems(purchaseRequestId) && !REPLACEMENT_CONFIRM_ROLE_CODE.equals(actionRoleCode)) {
            ApprovalInstance instance = ensureQuoteReviewInstance(purchaseRequestId, actionTime);
            ProcurementApprovalRouteResolver.ApprovalAssignment replacementAssignment =
                replacementConfirmationAssignment(request);
            int replacementNodeNo = ProcurementWorkflow.NODE_APPLICANT_REPLACEMENT_REVIEW;

            instance.setCurrentNodeNo(replacementNodeNo);
            instance.setCurrentLevelNo(replacementAssignment.levelNo());
            instance.setCurrentLevelCode(replacementAssignment.levelCode());
            instance.setCurrentPassRule(replacementAssignment.passRule());
            instance.setTotalNodeNo(replacementNodeNo);
            instance.setCurrentApproverId(replacementAssignment.currentApproverId());
            instance.setLastAction(ProcurementWorkflow.ACTION_APPROVE);
            instance.setLastActionBy(actionUserId);
            instance.setLastActionAt(actionTime);
            instance.setStatus(ProcurementWorkflow.APPROVAL_PENDING);
            instance.setEndedAt(null);

            request.setStatus(ProcurementWorkflow.STATUS_PENDING_APPROVAL);
            request.setApprovalStatus(ProcurementWorkflow.APPROVAL_PENDING);
            request.setCurrentNodeNo(replacementNodeNo);
            request.setLeaderConfirmStatus(ProcurementWorkflow.LEADER_CONFIRM_STATUS_PENDING);
            request.setLeaderConfirmRemark(leaderConfirmRemark);
            request.setLeaderConfirmedBy(actionUserId);
            request.setLeaderConfirmedAt(actionTime);

            updateApprovalInstance(instance);
            purchaseRequestMapper.updateById(request);
            approvalTaskService.createTasks(instance, request, replacementAssignment);
            return;
        }
        reviewAmount = awardPriceSyncService.syncAwardedPrices(request);
        int totalNodeNo = approvalRouteResolver.totalQuoteApprovalNodes(request, reviewAmount);
        ProcurementApprovalRouteResolver.ApprovalAssignment firstAssignment =
            resolveInitialQuoteReviewAssignment(request, totalNodeNo);
        int nextNodeNo = firstAssignment.levelNo();
        totalNodeNo = Math.max(totalNodeNo, nextNodeNo);

        ApprovalInstance instance = approvalInstanceMapper.selectOne(new LambdaQueryWrapper<ApprovalInstance>()
            .eq(ApprovalInstance::getPurchaseRequestId, purchaseRequestId)
            .orderByDesc(ApprovalInstance::getId)
            .last("LIMIT 1"));
        if (instance == null) {
            instance = new ApprovalInstance();
            instance.setInstanceNo(generateInstanceNo());
            instance.setBizType("PURCHASE_REQUEST");
            instance.setBizId(purchaseRequestId);
            instance.setPurchaseRequestId(purchaseRequestId);
            instance.setStartedAt(actionTime);
            instance.setRemark("报价审核流程");
            approvalInstanceMapper.insert(instance);
        }
        instance.setCurrentNodeNo(nextNodeNo);
        instance.setCurrentLevelNo(firstAssignment.levelNo());
        instance.setCurrentLevelCode(firstAssignment.levelCode());
        instance.setCurrentPassRule(firstAssignment.passRule());
        instance.setTotalNodeNo(totalNodeNo);
        instance.setCurrentApproverId(firstAssignment.currentApproverId());
        instance.setLastAction(ProcurementWorkflow.ACTION_APPROVE);
        instance.setLastActionBy(actionUserId);
        instance.setLastActionAt(actionTime);
        instance.setStatus(ProcurementWorkflow.APPROVAL_PENDING);
        instance.setEndedAt(null);
        updateApprovalInstance(instance);
        approvalTaskService.createTasks(instance, request, firstAssignment);

        request.setAwardStrategy(awardStrategy);
        request.setSelectedQuoteId(targetQuote == null ? null : targetQuote.getId());
        request.setStatus(ProcurementWorkflow.STATUS_PENDING_APPROVAL);
        request.setApprovalStatus(ProcurementWorkflow.APPROVAL_PENDING);
        request.setCurrentNodeNo(nextNodeNo);
        request.setLeaderConfirmStatus(ProcurementWorkflow.LEADER_CONFIRM_STATUS_PENDING);
        request.setLeaderConfirmRemark(leaderConfirmRemark);
        request.setLeaderConfirmedBy(actionUserId);
        request.setLeaderConfirmedAt(actionTime);
        purchaseRequestMapper.updateById(request);
    }

    private ApprovalInstance ensureQuoteReviewInstance(Long purchaseRequestId, LocalDateTime actionTime) {
        ApprovalInstance instance = approvalInstanceMapper.selectOne(new LambdaQueryWrapper<ApprovalInstance>()
            .eq(ApprovalInstance::getPurchaseRequestId, purchaseRequestId)
            .orderByDesc(ApprovalInstance::getId)
            .last("LIMIT 1"));
        if (instance != null) {
            return instance;
        }
        instance = new ApprovalInstance();
        instance.setInstanceNo(generateInstanceNo());
        instance.setBizType("PURCHASE_REQUEST");
        instance.setBizId(purchaseRequestId);
        instance.setPurchaseRequestId(purchaseRequestId);
        instance.setStartedAt(actionTime);
        instance.setRemark("报价审核流程");
        approvalInstanceMapper.insert(instance);
        return instance;
    }

    private ProcurementApprovalRouteResolver.ApprovalAssignment replacementConfirmationAssignment(PurchaseRequest request) {
        Long applicantUserId = request == null ? null : request.getApplicantUserId();
        if (applicantUserId == null) {
            throw new BizException("APPROVER_NOT_CONFIGURED", "Replacement item applicant confirmer is not configured");
        }
        SysUser applicant = userMapper.selectById(applicantUserId);
        if (applicant == null || applicant.getStatus() == null || applicant.getStatus() != 1) {
            throw new BizException("APPROVER_NOT_CONFIGURED", "Replacement item applicant confirmer is disabled or missing");
        }
        List<Long> applicantIds = List.of(applicantUserId);
        return new ProcurementApprovalRouteResolver.ApprovalAssignment(
            applicantUserId,
            applicantIds,
            userDisplayNames(applicantIds),
            true,
            null,
            null,
            ProcurementWorkflow.NODE_APPLICANT_REPLACEMENT_REVIEW,
            REPLACEMENT_CONFIRM_ROLE_CODE,
            ProcurementWorkflow.nodeNameForNode(ProcurementWorkflow.NODE_APPLICANT_REPLACEMENT_REVIEW),
            ProcurementApprovalRouteResolver.PASS_RULE_ANY_ONE,
            true
        );
    }

    private List<SupplierQuoteItem> awardedReplacementQuoteItems(Long purchaseRequestId) {
        if (purchaseRequestId == null) {
            return List.of();
        }
        return supplierQuoteItemMapper.selectList(new LambdaQueryWrapper<SupplierQuoteItem>()
            .eq(SupplierQuoteItem::getPurchaseRequestId, purchaseRequestId)
            .eq(SupplierQuoteItem::getStatus, QUOTE_ITEM_STATUS_ACTIVE)
            .eq(SupplierQuoteItem::getAwarded, 1)
            .eq(SupplierQuoteItem::getReplacementFlag, 1));
    }

    private Map<Long, Boolean> resolveReplacementDecisionMap(List<SupplierQuoteItem> awardedItems,
                                                             List<WorkflowReplacementDecisionDTO> decisions,
                                                             Boolean allReplacementApproved) {
        if (awardedItems == null || awardedItems.isEmpty()) {
            throw new BizException("REPLACEMENT_QUOTE_NOT_FOUND", "当前申请没有待确认的中标替换商品");
        }
        Map<Long, SupplierQuoteItem> itemById = awardedItems.stream()
            .filter(item -> item.getId() != null)
            .collect(Collectors.toMap(SupplierQuoteItem::getId, item -> item, (left, right) -> left, LinkedHashMap::new));
        if (itemById.isEmpty()) {
            throw new BizException("REPLACEMENT_QUOTE_NOT_FOUND", "当前申请没有待确认的中标替换商品");
        }
        Map<Long, Boolean> result = new LinkedHashMap<>();
        if (allReplacementApproved != null) {
            itemById.keySet().forEach(id -> result.put(id, allReplacementApproved));
            return result;
        }
        if (decisions == null || decisions.isEmpty()) {
            throw new BizException("REPLACEMENT_DECISION_REQUIRED", "请为每个中标替换商品选择使用原商品报价或替换商品报价");
        }
        for (WorkflowReplacementDecisionDTO decision : decisions) {
            Long quoteItemId = decision == null ? null : decision.getQuoteItemId();
            Boolean approved = decision == null ? null : decision.getReplacementApproved();
            if (quoteItemId == null || approved == null) {
                throw new BizException("REPLACEMENT_DECISION_INVALID", "替换商品选择数据不完整");
            }
            if (!itemById.containsKey(quoteItemId)) {
                throw new BizException("REPLACEMENT_DECISION_INVALID", "存在不属于当前申请的替换商品报价");
            }
            if (result.putIfAbsent(quoteItemId, approved) != null) {
                throw new BizException("REPLACEMENT_DECISION_DUPLICATED", "同一组替换商品报价只能选择一次");
            }
        }
        List<SupplierQuoteItem> missingItems = itemById.entrySet().stream()
            .filter(entry -> !result.containsKey(entry.getKey()))
            .map(Map.Entry::getValue)
            .toList();
        if (!missingItems.isEmpty()) {
            throw new BizException(
                "REPLACEMENT_DECISION_REQUIRED",
                "请补充选择：" + missingItems.stream().map(this::replacementDecisionItemText).collect(Collectors.joining("、"))
            );
        }
        return result;
    }

    private String replacementActionComment(String comment,
                                            List<SupplierQuoteItem> awardedItems,
                                            Map<Long, Boolean> replacementDecisionMap) {
        String decisionSummary = trimToLength(replacementDecisionSummary(awardedItems, replacementDecisionMap), 350);
        if (!StringUtils.hasText(comment)) {
            return decisionSummary;
        }
        return trimToLength(comment.trim() + "；" + decisionSummary, 500);
    }

    private String replacementDecisionSummary(List<SupplierQuoteItem> awardedItems, Map<Long, Boolean> replacementDecisionMap) {
        List<String> parts = awardedItems == null ? List.of() : awardedItems.stream()
            .filter(item -> item.getId() != null && replacementDecisionMap.containsKey(item.getId()))
            .map(item -> replacementDecisionItemText(item)
                + "："
                + (Boolean.TRUE.equals(replacementDecisionMap.get(item.getId())) ? "使用替换商品报价" : "使用原商品报价"))
            .toList();
        return parts.isEmpty() ? "确认替换商品报价" : "替换商品确认结果：" + String.join("；", parts);
    }

    private String replacementDecisionItemText(SupplierQuoteItem item) {
        if (item == null) {
            return "替换商品";
        }
        String line = item.getLineNo() == null ? "" : "第" + item.getLineNo() + "项";
        String name = firstText(item.getReplacementItemName(), item.getItemName(), item.getItemCode(), "替换商品");
        return StringUtils.hasText(line) ? line + " " + name : name;
    }

    private void updateAwardedReplacementReviewStatus(Long purchaseRequestId,
                                                       String reviewStatus,
                                                       Long reviewedBy,
                                                       LocalDateTime reviewedAt,
                                                       String reviewRemark) {
        for (SupplierQuoteItem item : awardedReplacementQuoteItems(purchaseRequestId)) {
            item.setReplacementReviewStatus(reviewStatus);
            item.setReplacementReviewedBy(reviewedBy);
            item.setReplacementReviewedAt(reviewedAt);
            item.setReplacementReviewRemark(reviewRemark);
            supplierQuoteItemMapper.updateById(item);
        }
    }

    private void updateAwardedReplacementReviewStatus(List<SupplierQuoteItem> replacementItems,
                                                       Map<Long, Boolean> replacementDecisionMap,
                                                       Long reviewedBy,
                                                       LocalDateTime reviewedAt,
                                                       String reviewRemark) {
        for (SupplierQuoteItem item : replacementItems == null ? List.<SupplierQuoteItem>of() : replacementItems) {
            if (item == null || item.getId() == null || !replacementDecisionMap.containsKey(item.getId())) {
                continue;
            }
            item.setReplacementReviewStatus(Boolean.TRUE.equals(replacementDecisionMap.get(item.getId()))
                ? REPLACEMENT_REVIEW_APPROVED
                : REPLACEMENT_REVIEW_REJECTED);
            item.setReplacementReviewedBy(reviewedBy);
            item.setReplacementReviewedAt(reviewedAt);
            item.setReplacementReviewRemark(reviewRemark);
            supplierQuoteItemMapper.updateById(item);
        }
    }

    private ProcurementApprovalRouteResolver.ApprovalAssignment startQuoteApprovalAfterReplacementConfirm(
        PurchaseRequest request,
        ApprovalInstance instance,
        String leaderConfirmRemark,
        Long actionUserId,
        LocalDateTime actionTime
    ) {
        BigDecimal reviewAmount = awardPriceSyncService.syncAwardedPrices(request);
        int totalNodeNo = approvalRouteResolver.totalQuoteApprovalNodes(request, reviewAmount);
        ProcurementApprovalRouteResolver.ApprovalAssignment firstAssignment =
            resolveInitialQuoteReviewAssignment(request, totalNodeNo);
        int nextNodeNo = firstAssignment.levelNo();
        totalNodeNo = Math.max(totalNodeNo, nextNodeNo);

        instance.setStatus(ProcurementWorkflow.APPROVAL_PENDING);
        instance.setCurrentNodeNo(nextNodeNo);
        instance.setCurrentLevelNo(firstAssignment.levelNo());
        instance.setCurrentLevelCode(firstAssignment.levelCode());
        instance.setCurrentPassRule(firstAssignment.passRule());
        instance.setTotalNodeNo(totalNodeNo);
        instance.setCurrentApproverId(firstAssignment.currentApproverId());
        instance.setEndedAt(null);

        request.setApprovalStatus(ProcurementWorkflow.APPROVAL_PENDING);
        request.setStatus(ProcurementWorkflow.STATUS_PENDING_APPROVAL);
        request.setCurrentNodeNo(nextNodeNo);
        request.setLeaderConfirmStatus(ProcurementWorkflow.LEADER_CONFIRM_STATUS_PENDING);
        request.setLeaderConfirmRemark(leaderConfirmRemark);
        request.setLeaderConfirmedBy(actionUserId);
        request.setLeaderConfirmedAt(actionTime);

        approvalTaskService.createTasks(instance, request, firstAssignment);
        return firstAssignment;
    }

    private ProcurementApprovalRouteResolver.ApprovalAssignment resolveInitialQuoteReviewAssignment(
        PurchaseRequest request,
        int totalNodeNo
    ) {
        if (overPriceReviewService.requiresReview(request)) {
            return resolveOverPriceFloorReviewAssignment(request);
        }
        return resolveQuoteReviewAssignment(request, ProcurementWorkflow.NODE_MANAGER_QUOTE_REVIEW, totalNodeNo);
    }

    private ProcurementApprovalRouteResolver.ApprovalAssignment resolveOverPriceFloorReviewAssignment(
        PurchaseRequest request
    ) {
        if (isNationalDemandAggregate(request)) {
            List<Long> floorLeaderUserIds = nationalAggregateApproverUserIds(
                request,
                ProcurementWorkflow.NODE_FLOOR_LEADER_DEMAND_REVIEW
            );
            if (floorLeaderUserIds.isEmpty()) {
                throw new BizException("APPROVER_NOT_CONFIGURED", "超价商品对应机房未配置有效楼长");
            }
            return new ProcurementApprovalRouteResolver.ApprovalAssignment(
                floorLeaderUserIds.size() == 1 ? floorLeaderUserIds.get(0) : null,
                floorLeaderUserIds,
                userDisplayNames(floorLeaderUserIds),
                true,
                null,
                null,
                ProcurementWorkflow.NODE_FLOOR_LEADER_DEMAND_REVIEW,
                ProcurementWorkflow.LEVEL_CODE_OVER_PRICE_REVIEW,
                "楼长超价复核",
                ProcurementApprovalRouteResolver.PASS_RULE_ALL,
                true
            );
        }
        ProcurementApprovalRouteResolver.ApprovalAssignment floorAssignment =
            approvalRouteResolver.resolveAssignmentForNode(
                request,
                ProcurementWorkflow.NODE_FLOOR_LEADER_DEMAND_REVIEW
            );
        return new ProcurementApprovalRouteResolver.ApprovalAssignment(
            floorAssignment.currentApproverId(),
            floorAssignment.candidateUserIds(),
            floorAssignment.candidateNames(),
            floorAssignment.routeMatched(),
            floorAssignment.policyId(),
            floorAssignment.levelId(),
            ProcurementWorkflow.NODE_FLOOR_LEADER_DEMAND_REVIEW,
            ProcurementWorkflow.LEVEL_CODE_OVER_PRICE_REVIEW,
            "楼长超价复核",
            ProcurementApprovalRouteResolver.PASS_RULE_ANY_ONE,
            floorAssignment.hasConfiguredLevel()
        );
    }

    private ProcurementApprovalRouteResolver.ApprovalAssignment resolveQuoteReviewAssignment(
        PurchaseRequest request,
        int nextNodeNo,
        int totalNodeNo
    ) {
        if (!isNationalDemandAggregate(request)) {
            return approvalRouteResolver.resolveAvailableAssignmentFromNode(request, nextNodeNo, totalNodeNo);
        }
        List<Long> managerUserIds = nationalAggregateManagerUserIds(request);
        if (managerUserIds.isEmpty()) {
            return approvalRouteResolver.resolveAvailableAssignmentFromNode(request, nextNodeNo, totalNodeNo);
        }
        return new ProcurementApprovalRouteResolver.ApprovalAssignment(
            null,
            managerUserIds,
            userDisplayNames(managerUserIds),
            true,
            null,
            null,
            ProcurementWorkflow.NODE_MANAGER_QUOTE_REVIEW,
            "V" + ProcurementWorkflow.NODE_MANAGER_QUOTE_REVIEW,
            "全国拆分经理确认",
            ProcurementApprovalRouteResolver.PASS_RULE_ALL,
            true
        );
    }

    private int totalNationalAggregateApprovalNodes(PurchaseRequest request,
                                                    BigDecimal amount,
                                                    boolean quoteReview) {
        if (!isNationalDemandAggregate(request)) {
            return quoteReview
                ? approvalRouteResolver.totalQuoteApprovalNodes(request, amount)
                : approvalRouteResolver.totalDemandApprovalNodes(request, amount);
        }
        return totalNationalAggregateApprovalNodes(nationalAggregateSourceRequests(request), amount, quoteReview);
    }

    private int totalNationalAggregateApprovalNodes(List<PurchaseRequest> sourceRequests,
                                                    BigDecimal amount,
                                                    boolean quoteReview) {
        if (!quoteReview) {
            return ProcurementWorkflow.NODE_MANAGER_REVIEW;
        }
        int totalNodeNo = ProcurementWorkflow.NODE_MANAGER_REVIEW;
        if (sourceRequests == null || sourceRequests.isEmpty()) {
            return totalNodeNo;
        }
        for (PurchaseRequest sourceRequest : sourceRequests) {
            if (sourceRequest == null) {
                continue;
            }
            int sourceTotalNodeNo = quoteReview
                ? approvalRouteResolver.totalQuoteApprovalNodes(sourceRequest, amount)
                : approvalRouteResolver.totalDemandApprovalNodes(sourceRequest, amount);
            totalNodeNo = Math.max(totalNodeNo, sourceTotalNodeNo);
        }
        return totalNodeNo;
    }

    private int nextNationalAggregateApprovalNodeNo(PurchaseRequest request,
                                                    int currentNodeNo,
                                                    int totalNodeNo) {
        if (!isNationalDemandAggregate(request)) {
            return approvalRouteResolver.nextActiveNodeNo(request, currentNodeNo, totalNodeNo);
        }
        for (int nodeNo = currentNodeNo + 1; nodeNo <= totalNodeNo; nodeNo++) {
            ProcurementApprovalRouteResolver.ApprovalAssignment assignment =
                resolveNationalAggregateApprovalAssignment(request, nodeNo, totalNodeNo);
            if (assignment.candidateUserIds() != null && !assignment.candidateUserIds().isEmpty()) {
                return assignment.levelNo();
            }
        }
        return 0;
    }

    private ProcurementApprovalRouteResolver.ApprovalAssignment resolveNationalAggregateApprovalAssignment(
        PurchaseRequest request,
        int nodeNo,
        int totalNodeNo
    ) {
        if (!isNationalDemandAggregate(request)) {
            return approvalRouteResolver.resolveAvailableAssignmentFromNode(request, nodeNo, totalNodeNo);
        }
        List<Long> approverUserIds = nationalAggregateApproverUserIds(request, nodeNo);
        if (approverUserIds.isEmpty()) {
            return approvalRouteResolver.resolveAvailableAssignmentFromNode(request, nodeNo, totalNodeNo);
        }
        return new ProcurementApprovalRouteResolver.ApprovalAssignment(
            approverUserIds.size() == 1 ? approverUserIds.get(0) : null,
            approverUserIds,
            userDisplayNames(approverUserIds),
            true,
            null,
            null,
            nodeNo,
            "V" + nodeNo,
            ProcurementWorkflow.nodeNameForNode(nodeNo),
            ProcurementApprovalRouteResolver.PASS_RULE_ALL,
            true
        );
    }

    private List<Long> nationalAggregateApproverUserIds(PurchaseRequest request, int nodeNo) {
        LinkedHashSet<Long> approverUserIds = new LinkedHashSet<>();
        for (PurchaseRequest sourceRequest : nationalAggregateSourceRequests(request)) {
            if (sourceRequest == null) {
                continue;
            }
            ProcurementApprovalRouteResolver.ApprovalAssignment assignment =
                approvalRouteResolver.resolveAssignmentForNode(sourceRequest, nodeNo);
            if (assignment.candidateUserIds() != null) {
                approverUserIds.addAll(assignment.candidateUserIds());
            }
        }
        return new ArrayList<>(approverUserIds);
    }

    private List<PurchaseRequest> nationalAggregateSourceRequests(PurchaseRequest request) {
        if (!isNationalDemandAggregate(request)) {
            return List.of();
        }
        List<PurchaseRequest> sourceRequests = new ArrayList<>();
        for (Long sourceRequestId : nationalAggregateSourceRequestIds(request)) {
            PurchaseRequest sourceRequest = purchaseRequestMapper.selectById(sourceRequestId);
            if (sourceRequest != null) {
                sourceRequests.add(sourceRequest);
            }
        }
        return sourceRequests;
    }

    private boolean isNationalDemandAggregate(PurchaseRequest request) {
        return request != null
            && (NATIONAL_DEMAND_AGGREGATE_REASON.equals(request.getReqReason())
                || (StringUtils.hasText(request.getReqNo()) && request.getReqNo().startsWith("HZCG-")));
    }

    private PurchaseRequestItem ultimateRejectedSourceItem(PurchaseRequestItem item, Set<Long> visitedItemIds) {
        if (item == null || item.getSourceRequestItemId() == null) {
            return item;
        }
        if (item.getId() != null && !visitedItemIds.add(item.getId())) {
            return item;
        }
        PurchaseRequestItem sourceItem = purchaseRequestItemMapper.selectById(item.getSourceRequestItemId());
        if (sourceItem == null) {
            return item;
        }
        PurchaseRequestItem ultimateItem = ultimateRejectedSourceItem(sourceItem, visitedItemIds);
        return ultimateItem == null ? sourceItem : ultimateItem;
    }

    private PurchaseRequest cachedRequest(Map<Long, PurchaseRequest> requestCache, Long requestId) {
        if (requestId == null) {
            return null;
        }
        return requestCache.computeIfAbsent(requestId, purchaseRequestMapper::selectById);
    }

    private boolean canReadRejectedItemMessage(PurchaseRequest parentRequest,
                                               PurchaseRequest sourceRequest,
                                               AuthUser authUser) {
        if (DataScopeHelper.isSystemAdmin(authUser)) {
            return true;
        }
        if (parentRequest == null || sourceRequest == null) {
            return false;
        }
        if (Objects.equals(sourceRequest.getApplicantUserId(), authUser.getUserId())) {
            return true;
        }
        if (!isNationalDemandAggregate(parentRequest)) {
            return Objects.equals(parentRequest.getApplicantUserId(), authUser.getUserId());
        }
        return canAccessNationalAggregateSource(authUser, sourceRequest);
    }

    private List<Long> nationalAggregateSourceRequestIds(PurchaseRequest request) {
        if (request == null || request.getId() == null) {
            return List.of();
        }
        return purchaseRequestItemMapper.selectList(new LambdaQueryWrapper<PurchaseRequestItem>()
                .eq(PurchaseRequestItem::getPurchaseRequestId, request.getId())
                .isNotNull(PurchaseRequestItem::getSourceRequestId)
                .orderByAsc(PurchaseRequestItem::getLineNo))
            .stream()
            .map(PurchaseRequestItem::getSourceRequestId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
    }

    private List<Long> nationalAggregateManagerUserIds(PurchaseRequest request) {
        LinkedHashSet<Long> managerUserIds = new LinkedHashSet<>();
        for (Long sourceRequestId : nationalAggregateSourceRequestIds(request)) {
            PurchaseRequest sourceRequest = purchaseRequestMapper.selectById(sourceRequestId);
            if (sourceRequest == null) {
                continue;
            }
            managerUserIds.addAll(nationalAggregateManagerUserIds(List.of(sourceRequest)));
        }
        return new ArrayList<>(managerUserIds);
    }

    private List<Long> nationalAggregateManagerUserIds(List<PurchaseRequest> sourceRequests) {
        if (sourceRequests == null || sourceRequests.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Long> managerUserIds = new LinkedHashSet<>();
        for (PurchaseRequest sourceRequest : sourceRequests) {
            if (sourceRequest == null) {
                continue;
            }
            ProcurementApprovalRouteResolver.ApprovalAssignment assignment =
                approvalRouteResolver.resolveAvailableAssignmentFromNode(
                    sourceRequest,
                    ProcurementWorkflow.NODE_MANAGER_REVIEW,
                    ProcurementWorkflow.NODE_MANAGER_REVIEW
                );
            if (assignment.candidateUserIds() != null) {
                managerUserIds.addAll(assignment.candidateUserIds());
            }
        }
        return new ArrayList<>(managerUserIds);
    }

    private String userDisplayNames(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return "";
        }
        List<String> names = new ArrayList<>();
        for (Long userId : userIds) {
            SysUser user = userMapper.selectById(userId);
            names.add(user == null ? String.valueOf(userId) : userDisplayName(user));
        }
        return String.join(",", names);
    }

    @Override
    public ExportFile exportQuoteComparison(Long purchaseRequestId) {
        AuthUser authUser = AuthContextHolder.requireUser();
        ensureNonSupplier(authUser);

        PurchaseRequest request = purchaseRequestMapper.selectById(purchaseRequestId);
        if (request == null) {
            throw new BizException("PURCHASE_REQUEST_NOT_FOUND", "Purchase request not found");
        }
        ensureRequestVisible(request, authUser);
        ensureQuoteDetailsVisible(authUser, request);

        List<List<String>> rows = buildQuoteComparisonExportRows(request);
        String fileName = sanitizeFileName("quote-comparison-" + request.getReqNo() + ".csv");
        return new ExportFile(fileName, "text/csv;charset=UTF-8", CsvExportUtils.toCsvBytes(rows));
    }

    private LambdaQueryWrapper<PurchaseRequest> buildRequestQuery(PurchaseRequestPageQueryDTO queryDTO) {
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

    private PurchaseRequestVO detailForAuth(PurchaseRequest request, AuthUser authUser) {
        PurchaseRequestVO detail = purchaseRequestService.detail(request.getId());
        Set<Long> visibleSourceIds = visibleNationalAggregateSourceIdsForUser(request, authUser);
        if (visibleSourceIds == null || detail.getItems() == null) {
            return detail;
        }
        var visibleItems = detail.getItems().stream()
            .filter(item -> visibleSourceIds.contains(item.getSourceRequestId()))
            .toList();
        detail.setItems(visibleItems);
        detail.setTotalAmount(visibleItems.stream()
            .map(item -> item.getLineAmount() == null ? BigDecimal.ZERO : item.getLineAmount())
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP));
        detail.setDeptName(joinVisibleSourceDepartments(visibleSourceIds));
        detail.setReceivingCity(joinDistinctText(visibleItems.stream()
            .map(item -> firstText(item.getReceivingCity()))
            .toList()));
        return detail;
    }

    private Set<Long> visibleNationalAggregateSourceIdsForManager(PurchaseRequest request,
                                                                  AuthUser authUser,
                                                                  int nodeNo) {
        if (!isNationalDemandAggregate(request)
            || authUser == null
            || authUser.getUserId() == null) {
            return null;
        }
        if (DataScopeHelper.isSystemAdmin(authUser) || DataScopeHelper.hasAnyRole(authUser, List.of("PURCHASER"))) {
            return null;
        }
        return visibleNationalAggregateSourceIdsForApprovalNodes(request, authUser, Set.of(nodeNo));
    }

    private Set<Long> visibleNationalAggregateSourceIdsForUser(PurchaseRequest request,
                                                               AuthUser authUser) {
        if (!isNationalDemandAggregate(request)
            || authUser == null
            || authUser.getUserId() == null) {
            return null;
        }
        if (DataScopeHelper.isSystemAdmin(authUser) || DataScopeHelper.hasAnyRole(authUser, List.of("PURCHASER"))) {
            return null;
        }
        Set<Long> visibleSourceIds = new LinkedHashSet<>();
        Set<Integer> approvalNodeNos = nationalAggregateApprovalNodeNosForUser(request, authUser);
        for (Long sourceRequestId : nationalAggregateSourceRequestIds(request)) {
            PurchaseRequest sourceRequest = purchaseRequestMapper.selectById(sourceRequestId);
            if (sourceRequest == null) {
                continue;
            }
            if (canAccessNationalAggregateSource(authUser, sourceRequest)
                || canAccessNationalAggregateSourceAtAnyNode(authUser, sourceRequest, approvalNodeNos)) {
                visibleSourceIds.add(sourceRequestId);
            }
        }
        return visibleSourceIds;
    }

    private Set<Long> visibleNationalAggregateSourceIdsForApprovalNodes(PurchaseRequest request,
                                                                        AuthUser authUser,
                                                                        Set<Integer> nodeNos) {
        Set<Long> visibleSourceIds = new LinkedHashSet<>();
        if (nodeNos == null || nodeNos.isEmpty()) {
            return visibleSourceIds;
        }
        for (Long sourceRequestId : nationalAggregateSourceRequestIds(request)) {
            PurchaseRequest sourceRequest = purchaseRequestMapper.selectById(sourceRequestId);
            if (sourceRequest != null && canAccessNationalAggregateSourceAtAnyNode(authUser, sourceRequest, nodeNos)) {
                visibleSourceIds.add(sourceRequestId);
            }
        }
        return visibleSourceIds;
    }

    private Set<Integer> nationalAggregateApprovalNodeNosForUser(PurchaseRequest request, AuthUser authUser) {
        Set<Integer> nodeNos = new LinkedHashSet<>();
        if (request == null || request.getId() == null || authUser == null || authUser.getUserId() == null) {
            return nodeNos;
        }
        ApprovalInstance instance = latestApprovalInstance(request.getId());
        if (approvalTaskService.hasPendingTaskForUser(instance, request, authUser)
            && request.getCurrentNodeNo() != null
            && request.getCurrentNodeNo() > 0) {
            nodeNos.add(request.getCurrentNodeNo());
        }
        approvalRecordMapper.selectList(new LambdaQueryWrapper<ApprovalRecord>()
                .eq(ApprovalRecord::getPurchaseRequestId, request.getId())
                .eq(ApprovalRecord::getApproverUserId, authUser.getUserId())
                .eq(ApprovalRecord::getStatus, "ACTIVE")
                .in(ApprovalRecord::getAction, List.of(
                    ProcurementWorkflow.ACTION_APPROVE,
                    ProcurementWorkflow.ACTION_REJECT,
                    ProcurementWorkflow.ACTION_RETURN,
                    ProcurementWorkflow.ACTION_TERMINATE,
                    "ADD_SIGN"
                )))
            .stream()
            .map(ApprovalRecord::getNodeNo)
            .filter(nodeNo -> nodeNo != null && nodeNo > 0)
            .forEach(nodeNos::add);
        return nodeNos;
    }

    private boolean canAccessNationalAggregateSourceAtAnyNode(AuthUser authUser,
                                                              PurchaseRequest sourceRequest,
                                                              Set<Integer> nodeNos) {
        if (nodeNos == null || nodeNos.isEmpty()) {
            return false;
        }
        return nodeNos.stream().anyMatch(nodeNo -> canAccessNationalAggregateSourceAtNode(authUser, sourceRequest, nodeNo));
    }

    private boolean canAccessNationalAggregateSourceAtNode(AuthUser authUser,
                                                           PurchaseRequest sourceRequest,
                                                           Integer nodeNo) {
        if (authUser == null || authUser.getUserId() == null || sourceRequest == null || nodeNo == null || nodeNo <= 0) {
            return false;
        }
        try {
            ProcurementApprovalRouteResolver.ApprovalAssignment assignment =
                nationalAggregateSourceAssignmentForNode(sourceRequest, nodeNo);
            return assignment.candidateUserIds() != null && assignment.candidateUserIds().contains(authUser.getUserId());
        } catch (BizException ex) {
            if ("APPROVER_NOT_CONFIGURED".equals(ex.getCode())
                || "ROUTE_APPROVER_NOT_CONFIGURED".equals(ex.getCode())) {
                return false;
            }
            throw ex;
        }
    }

    private ProcurementApprovalRouteResolver.ApprovalAssignment nationalAggregateSourceAssignmentForNode(PurchaseRequest sourceRequest,
                                                                                                        int nodeNo) {
        if (nodeNo == ProcurementWorkflow.NODE_MANAGER_REVIEW) {
            return approvalRouteResolver.resolveAvailableAssignmentFromNode(
                sourceRequest,
                ProcurementWorkflow.NODE_MANAGER_REVIEW,
                ProcurementWorkflow.NODE_MANAGER_REVIEW
            );
        }
        return approvalRouteResolver.resolveAssignmentForNode(sourceRequest, nodeNo);
    }

    private boolean canAccessNationalAggregateSource(AuthUser authUser, PurchaseRequest sourceRequest) {
        if (authUser == null || authUser.getUserId() == null || sourceRequest == null) {
            return false;
        }
        if (Objects.equals(sourceRequest.getApplicantUserId(), authUser.getUserId())) {
            return true;
        }
        List<Long> assistantUserIds = approvalRouteResolver.routeCandidateUserIds(
            sourceRequest,
            ProcurementWorkflow.ROLE_DEPARTMENT_ASSISTANT
        );
        return assistantUserIds != null && assistantUserIds.contains(authUser.getUserId());
    }

    private String joinVisibleSourceDepartments(Set<Long> visibleSourceIds) {
        if (visibleSourceIds == null || visibleSourceIds.isEmpty()) {
            return "";
        }
        List<String> deptNames = new ArrayList<>();
        for (Long sourceRequestId : visibleSourceIds) {
            PurchaseRequest sourceRequest = purchaseRequestMapper.selectById(sourceRequestId);
            if (sourceRequest != null) {
                deptNames.add(firstText(sourceRequest.getDeptName()));
            }
        }
        return joinDistinctText(deptNames);
    }

    private String joinDistinctText(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        List<String> distinct = values.stream()
            .filter(StringUtils::hasText)
            .map(String::trim)
            .distinct()
            .toList();
        return String.join(" / ", distinct);
    }

    private void excludeNationalAggregates(LambdaQueryWrapper<PurchaseRequest> wrapper) {
        wrapper.and(visible -> visible
            .and(reason -> reason
                .isNull(PurchaseRequest::getReqReason)
                .or()
                .ne(PurchaseRequest::getReqReason, NATIONAL_DEMAND_AGGREGATE_REASON))
            .and(reqNo -> reqNo
                .isNull(PurchaseRequest::getReqNo)
                .or()
                .notLikeRight(PurchaseRequest::getReqNo, "HZCG-")));
    }

    private void ensureRequestVisible(PurchaseRequest request, AuthUser authUser) {
        if (!canViewRequest(request, authUser)) {
            throw new BizException("FORBIDDEN", "Current account cannot access this request");
        }
    }

    private boolean canViewRequest(PurchaseRequest request, AuthUser authUser) {
        if (request == null || authUser == null) {
            return false;
        }
        if (DataScopeHelper.isSystemAdmin(authUser)) {
            return true;
        }
        Long userId = authUser.getUserId();
        if (userId == null) {
            return false;
        }
        if (Objects.equals(userId, request.getApplicantUserId()) || Objects.equals(userId, request.getPurchaserUserId())) {
            return true;
        }
        ApprovalInstance instance = latestApprovalInstance(request.getId());
        if (approvalTaskService.hasPendingTaskForUser(instance, request, authUser)) {
            return true;
        }
        if (isNationalDemandAggregate(request)) {
            Set<Long> visibleSourceIds = visibleNationalAggregateSourceIdsForUser(request, authUser);
            return visibleSourceIds == null || !visibleSourceIds.isEmpty();
        }
        return approvalRouteResolver.canAccessRelatedApproval(request, authUser);
    }

    private void ensureNonSupplier(AuthUser authUser) {
        if (DataScopeHelper.isSupplierUser(authUser)) {
            throw new BizException("FORBIDDEN", "Supplier account cannot access this API");
        }
    }

    private void ensureRequestCreator(AuthUser authUser) {
        if (DataScopeHelper.isSystemAdmin(authUser)) {
            return;
        }
        if (!authUser.getRoleCodes().contains(ProcurementWorkflow.ROLE_ENGINEER)) {
            throw new BizException("FORBIDDEN", "Only engineer accounts can create or edit requests");
        }
    }

    private List<Integer> resolveNodeNos(AuthUser authUser) {
        List<Integer> nodeNos = new ArrayList<>();
        if (authUser.getRoleCodes() == null) {
            return nodeNos;
        }
        for (String roleCode : authUser.getRoleCodes()) {
            if (!StringUtils.hasText(roleCode)) {
                continue;
            }
            int nodeNo = ProcurementWorkflow.nodeNoForRole(roleCode.trim().toUpperCase(Locale.ROOT));
            if (nodeNo > 0) {
                nodeNos.add(nodeNo);
            }
        }
        return nodeNos;
    }

    private boolean canApproveRequest(AuthUser authUser, PurchaseRequest request) {
        ApprovalInstance instance = latestApprovalInstance(request.getId());
        Long currentApproverId = instance == null ? null : instance.getCurrentApproverId();
        if (approvalTaskService.hasPendingTaskForUser(instance, request, authUser)) {
            return true;
        }
        try {
            return approvalTaskService.canActOnCurrentTask(instance, request, authUser)
                && approvalRouteResolver.canApprove(request, authUser, request.getCurrentNodeNo(), currentApproverId);
        } catch (BizException ex) {
            if ("APPROVER_NOT_CONFIGURED".equals(ex.getCode())) {
                return false;
            }
            throw ex;
        }
    }

    private void ensureApplicantCanTerminate(PurchaseRequest request, AuthUser authUser) {
        if (authUser == null || !Objects.equals(request.getApplicantUserId(), authUser.getUserId())) {
            throw new BizException("FORBIDDEN", "Only the applicant can terminate this workflow");
        }
        if (request.getCurrentNodeNo() != null && request.getCurrentNodeNo() > 0) {
            throw new BizException("FORBIDDEN", "Only requests still at the submit stage can be terminated");
        }
    }

    private ApprovalInstance latestApprovalInstance(Long purchaseRequestId) {
        return approvalInstanceMapper.selectOne(new LambdaQueryWrapper<ApprovalInstance>()
            .eq(ApprovalInstance::getPurchaseRequestId, purchaseRequestId)
            .orderByDesc(ApprovalInstance::getId)
            .last("LIMIT 1"));
    }

    private List<PurchaseRequest> pageSlice(List<PurchaseRequest> requests, long pageNo, long pageSize) {
        if (requests.isEmpty()) {
            return List.of();
        }
        int fromIndex = (int) Math.min(requests.size(), Math.max(0, (pageNo - 1) * pageSize));
        int toIndex = (int) Math.min(requests.size(), fromIndex + pageSize);
        return requests.subList(fromIndex, toIndex);
    }

    private Long auditUserId(ProcurementApprovalRouteResolver.ApprovalAssignment assignment) {
        if (assignment.currentApproverId() != null) {
            return assignment.currentApproverId();
        }
        if (assignment.candidateUserIds() != null && !assignment.candidateUserIds().isEmpty()) {
            return assignment.candidateUserIds().get(0);
        }
        throw new BizException("APPROVER_NOT_CONFIGURED", "No approver configured");
    }

    private Long systemAdminTriggerUserId() {
        SysUser admin = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
            .eq(SysUser::getStatus, 1)
            .and(wrapper -> wrapper.eq(SysUser::getRealName, "系统管理员")
                .or()
                .eq(SysUser::getUsername, "admin")
                .or()
                .eq(SysUser::getUsername, "系统管理员"))
            .orderByAsc(SysUser::getId)
            .last("LIMIT 1"));
        if (admin != null) {
            return admin.getId();
        }
        AuthUser authUser = AuthContextHolder.get();
        return authUser == null ? null : authUser.getUserId();
    }

    private String normalizeAction(String action) {
        if (!StringUtils.hasText(action)) {
            return "";
        }
        return action.trim().toUpperCase(Locale.ROOT);
    }

    private BigDecimal quoteApprovalAmount(PurchaseRequest request) {
        if (ProcurementWorkflow.isItemLowestAwardStrategy(request.getAwardStrategy())) {
            BigDecimal awardedAmount = supplierQuoteItemMapper.selectList(new LambdaQueryWrapper<SupplierQuoteItem>()
                .eq(SupplierQuoteItem::getPurchaseRequestId, request.getId())
                .eq(SupplierQuoteItem::getAwarded, 1))
                .stream()
                .map(this::effectiveQuoteLineAmount)
                .filter(amount -> amount != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (awardedAmount.compareTo(BigDecimal.ZERO) > 0) {
                return awardedAmount;
            }
        }
        if (request.getSelectedQuoteId() == null) {
            return request.getTotalAmount();
        }
        SupplierQuote selectedQuote = supplierQuoteMapper.selectById(request.getSelectedQuoteId());
        if (selectedQuote == null) {
            return request.getTotalAmount();
        }
        BigDecimal effectiveAmount = supplierQuoteItemMapper.selectList(new LambdaQueryWrapper<SupplierQuoteItem>()
                .eq(SupplierQuoteItem::getQuoteId, selectedQuote.getId())
                .eq(SupplierQuoteItem::getStatus, QUOTE_ITEM_STATUS_ACTIVE))
            .stream()
            .map(this::effectiveQuoteLineAmount)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return effectiveAmount.compareTo(BigDecimal.ZERO) > 0 ? effectiveAmount : selectedQuote.getTotalAmount();
    }

    private BigDecimal effectiveQuoteLineAmount(SupplierQuoteItem item) {
        if (item == null) {
            return null;
        }
        if (Objects.equals(item.getReplacementReviewStatus(), REPLACEMENT_REVIEW_APPROVED)
            && item.getReplacementLineAmount() != null) {
            return item.getReplacementLineAmount();
        }
        return item.getLineAmount();
    }

    private boolean hasConfirmedAward(PurchaseRequest request) {
        return supplierQuoteMapper.selectList(new LambdaQueryWrapper<SupplierQuote>()
                .eq(SupplierQuote::getPurchaseRequestId, request.getId())
                .eq(SupplierQuote::getSupplierConfirmStatus, ProcurementWorkflow.SUPPLIER_CONFIRM_STATUS_CONFIRMED))
            .stream()
            .findAny()
            .isPresent();
    }

    private boolean hasAwardResult(PurchaseRequest request) {
        if (request == null || request.getId() == null) {
            return false;
        }
        if (request.getSelectedQuoteId() != null) {
            return true;
        }
        return supplierQuoteItemMapper.selectCount(new LambdaQueryWrapper<SupplierQuoteItem>()
            .eq(SupplierQuoteItem::getPurchaseRequestId, request.getId())
            .eq(SupplierQuoteItem::getStatus, QUOTE_ITEM_STATUS_ACTIVE)
            .eq(SupplierQuoteItem::getAwarded, 1)) > 0;
    }

    private boolean quoteDetailsVisible(AuthUser authUser, PurchaseRequest request) {
        return SupplierQuoteAccess.canViewAllSubmissions(authUser) || hasAwardResult(request);
    }

    private void ensureQuoteDetailsVisible(AuthUser authUser, PurchaseRequest request) {
        if (!quoteDetailsVisible(authUser, request)) {
            throw new BizException("FORBIDDEN", "未产生中标结果前，只有管理员可查看或导出供应商报价");
        }
    }

    private boolean hasAwardedReplacementItems(Long purchaseRequestId) {
        if (purchaseRequestId == null) {
            return false;
        }
        return supplierQuoteItemMapper.selectCount(new LambdaQueryWrapper<SupplierQuoteItem>()
            .eq(SupplierQuoteItem::getPurchaseRequestId, purchaseRequestId)
            .eq(SupplierQuoteItem::getStatus, QUOTE_ITEM_STATUS_ACTIVE)
            .eq(SupplierQuoteItem::getAwarded, 1)
            .eq(SupplierQuoteItem::getReplacementFlag, 1)) > 0;
    }

    private boolean shouldExposeApprovalRecord(PurchaseRequest request, ApprovalRecord record) {
        if (record == null || !Objects.equals(record.getNodeNo(), ProcurementWorkflow.NODE_APPLICANT_REPLACEMENT_REVIEW)) {
            return true;
        }
        return request != null && hasAwardedReplacementItems(request.getId());
    }

    private void resetAwardMarkers(Long purchaseRequestId, List<SupplierQuote> requestQuotes) {
        for (SupplierQuoteItem item : supplierQuoteItemMapper.selectList(new LambdaQueryWrapper<SupplierQuoteItem>()
            .eq(SupplierQuoteItem::getPurchaseRequestId, purchaseRequestId))) {
            if (item.getAwarded() != null && item.getAwarded() == 1) {
                item.setAwarded(0);
            }
            if (item.getReplacementFlag() != null && item.getReplacementFlag() == 1) {
                item.setReplacementReviewStatus(null);
                item.setReplacementReviewedBy(null);
                item.setReplacementReviewedAt(null);
                item.setReplacementReviewRemark(null);
            }
            supplierQuoteItemMapper.updateById(item);
        }
        for (SupplierQuote quote : requestQuotes) {
            quote.setSupplierConfirmStatus(ProcurementWorkflow.SUPPLIER_CONFIRM_STATUS_UNSELECTED);
            quote.setSupplierConfirmRemark(null);
            quote.setSupplierConfirmedBy(null);
            quote.setSupplierConfirmedAt(null);
            supplierQuoteMapper.updateById(quote);
        }
    }

    private SupplierQuote resolveTotalLowestQuote(PurchaseRequest request, List<SupplierQuote> quotes) {
        return quotes.stream()
            .filter(quote -> hasCompleteActiveQuoteItems(request, quote))
            .min(Comparator
                .comparing((SupplierQuote quote) -> quote.getTotalAmount() == null ? BigDecimal.ZERO : quote.getTotalAmount())
                .thenComparing(quote -> quote.getSubmittedAt() == null ? LocalDateTime.MAX : quote.getSubmittedAt())
                .thenComparing(SupplierQuote::getId))
            .orElse(null);
    }

    private SupplierQuote resolveDirectAwardQuote(PurchaseRequest request, List<SupplierQuote> quotes) {
        return quotes.stream()
            .filter(quote -> Objects.equals(quote.getSupplierId(), request.getDirectAwardSupplierId()))
            .filter(quote -> hasCompleteActiveQuoteItems(request, quote))
            .min(Comparator
                .comparing((SupplierQuote quote) -> quote.getSubmittedAt() == null ? LocalDateTime.MAX : quote.getSubmittedAt())
                .thenComparing(SupplierQuote::getId))
            .orElse(null);
    }

    private boolean directAwardQuoteExceedsLimit(PurchaseRequest request, SupplierQuote quote) {
        if (!isDirectAwardRequest(request) || quote == null || quote.getTotalAmount() == null) {
            return false;
        }
        return quote.getTotalAmount().compareTo(directAwardQuoteLimit(request)) > 0;
    }

    private BigDecimal directAwardQuoteLimit(PurchaseRequest request) {
        BigDecimal threshold = request == null ? null : request.getDirectAwardThresholdAmount();
        if (threshold == null || threshold.compareTo(BigDecimal.ZERO) <= 0) {
            threshold = parseAmountSetting(URGENT_DIRECT_AWARD_THRESHOLD_KEY, DEFAULT_URGENT_DIRECT_AWARD_THRESHOLD);
        }
        return threshold.multiply(URGENT_DIRECT_AWARD_QUOTE_LIMIT_MULTIPLIER).setScale(2, RoundingMode.HALF_UP);
    }

    private void downgradeDirectAwardToThreeQuotes(PurchaseRequest request, SupplierQuote quote, String reason) {
        if (request == null) {
            return;
        }
        BigDecimal limit = directAwardQuoteLimit(request);
        String supplierName = firstText(request.getDirectAwardSupplierName(), "定向供应商");
        String remark = reason
            + "（"
            + supplierName
            + "报价 "
            + decimalText(quote == null ? null : quote.getTotalAmount())
            + "，上限 "
            + decimalText(limit)
            + "）";
        clearDirectAward(request);
        request.setRemark(appendRemark(request.getRemark(), remark));
        purchaseRequestMapper.updateById(request);
    }

    private boolean hasCompleteActiveQuoteItems(PurchaseRequest request, SupplierQuote quote) {
        Set<Long> activeRequestItemIds = activeRequestItemIds(request == null ? null : request.getId());
        if (activeRequestItemIds.isEmpty() || quote == null || quote.getId() == null) {
            return false;
        }
        List<SupplierQuoteItem> activeQuoteItems = supplierQuoteItemMapper.selectList(new LambdaQueryWrapper<SupplierQuoteItem>()
            .eq(SupplierQuoteItem::getQuoteId, quote.getId())
            .eq(SupplierQuoteItem::getStatus, QUOTE_ITEM_STATUS_ACTIVE));
        Set<Long> linkedRequestItemIds = activeQuoteItems.stream()
            .map(SupplierQuoteItem::getPurchaseRequestItemId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        return activeQuoteItems.size() == activeRequestItemIds.size()
            && linkedRequestItemIds.size() == activeQuoteItems.size()
            && linkedRequestItemIds.equals(activeRequestItemIds);
    }

    private void reconcileQuoteItemsWithActiveRequestItems(PurchaseRequest request) {
        if (request == null || request.getId() == null) {
            return;
        }
        List<PurchaseRequestItem> activeItems = activeRequestItems(request.getId());
        Set<Long> activeItemIds = activeItems.stream()
            .map(PurchaseRequestItem::getId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Map<String, List<PurchaseRequestItem>> activeItemsByCode = activeItems.stream()
            .filter(item -> StringUtils.hasText(item.getItemCode()))
            .collect(Collectors.groupingBy(
                item -> normalizedQuoteItemText(item.getItemCode()),
                LinkedHashMap::new,
                Collectors.toList()
            ));
        if (activeItemsByCode.isEmpty()) {
            return;
        }

        List<SupplierQuoteItem> quoteItems = supplierQuoteItemMapper.selectList(new LambdaQueryWrapper<SupplierQuoteItem>()
            .eq(SupplierQuoteItem::getPurchaseRequestId, request.getId())
            .eq(SupplierQuoteItem::getStatus, QUOTE_ITEM_STATUS_ACTIVE));
        for (SupplierQuoteItem quoteItem : quoteItems) {
            if (quoteItem.getPurchaseRequestItemId() != null
                && activeItemIds.contains(quoteItem.getPurchaseRequestItemId())) {
                continue;
            }
            List<PurchaseRequestItem> matches = activeItemsByCode
                .getOrDefault(normalizedQuoteItemText(quoteItem.getItemCode()), List.of())
                .stream()
                .filter(item -> unchangedQuotedItem(quoteItem, item))
                .toList();
            if (matches.size() != 1) {
                quoteItem.setStatus("INACTIVE");
                clearQuoteItemAwardMarker(quoteItem);
                supplierQuoteItemMapper.updateById(quoteItem);
                continue;
            }
            quoteItem.setPurchaseRequestItemId(matches.get(0).getId());
            supplierQuoteItemMapper.updateById(quoteItem);
        }
        reconcileSubmittedQuoteTotals(request.getId(), activeItemIds, quoteItems);
    }

    private void reconcileSubmittedQuoteTotals(
        Long purchaseRequestId,
        Set<Long> activeRequestItemIds,
        List<SupplierQuoteItem> quoteItems
    ) {
        List<SupplierQuote> submittedQuotes = supplierQuoteMapper.selectList(new LambdaQueryWrapper<SupplierQuote>()
            .eq(SupplierQuote::getPurchaseRequestId, purchaseRequestId)
            .eq(SupplierQuote::getStatus, ProcurementWorkflow.QUOTE_STATUS_SUBMITTED));
        for (SupplierQuote quote : submittedQuotes) {
            List<SupplierQuoteItem> activeItems = quoteItems.stream()
                .filter(item -> Objects.equals(item.getQuoteId(), quote.getId()))
                .filter(item -> QUOTE_ITEM_STATUS_ACTIVE.equals(item.getStatus()))
                .filter(item -> activeRequestItemIds.contains(item.getPurchaseRequestItemId()))
                .toList();
            BigDecimal totalAmount = activeItems.stream()
                .map(SupplierQuoteItem::getLineAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
            int leadTimeDays = activeItems.stream()
                .map(SupplierQuoteItem::getLeadTimeDays)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(0);
            if (!sameAmount(quote.getTotalAmount(), totalAmount)
                || !Objects.equals(quote.getLeadTimeDays(), leadTimeDays)) {
                quote.setTotalAmount(totalAmount);
                quote.setLeadTimeDays(leadTimeDays);
                supplierQuoteMapper.updateById(quote);
            }
        }
    }

    private boolean unchangedQuotedItem(SupplierQuoteItem quoteItem, PurchaseRequestItem requestItem) {
        return Objects.equals(normalizedQuoteItemText(quoteItem.getItemCode()), normalizedQuoteItemText(requestItem.getItemCode()))
            && Objects.equals(normalizedQuoteItemText(quoteItem.getItemName()), normalizedQuoteItemText(requestItem.getItemName()))
            && Objects.equals(normalizedQuoteItemText(quoteItem.getSpecModel()), normalizedQuoteItemText(requestItem.getSpecModel()))
            && Objects.equals(normalizedQuoteItemText(quoteItem.getUnit()), normalizedQuoteItemText(requestItem.getUnit()))
            && sameAmount(quoteItem.getQty(), requestItem.getQty());
    }

    private String normalizedQuoteItemText(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private boolean sameAmount(BigDecimal left, BigDecimal right) {
        return left != null && right != null && left.compareTo(right) == 0;
    }

    private void ensureEveryActiveItemHasQuote(PurchaseRequest request, List<SupplierQuote> eligibleQuotes) {
        List<Long> quoteIds = eligibleQuotes.stream()
            .map(SupplierQuote::getId)
            .filter(Objects::nonNull)
            .toList();
        if (quoteIds.size() < 3) {
            throw new BizException("QUOTE_COUNT_NOT_ENOUGH", "At least three supplier quotes are required before quote review");
        }
        for (PurchaseRequestItem requestItem : activeRequestItems(request.getId())) {
            long activeSupplierCount = supplierQuoteItemMapper.selectList(new LambdaQueryWrapper<SupplierQuoteItem>()
                    .eq(SupplierQuoteItem::getPurchaseRequestItemId, requestItem.getId())
                    .eq(SupplierQuoteItem::getStatus, QUOTE_ITEM_STATUS_ACTIVE)
                    .in(SupplierQuoteItem::getQuoteId, quoteIds))
                .stream()
                .map(SupplierQuoteItem::getQuoteId)
                .filter(Objects::nonNull)
                .distinct()
                .count();
            if (activeSupplierCount == 0) {
                throw new BizException("QUOTE_ITEM_MISSING", "Every active purchase item must have at least one supplier quote");
            }
        }
    }

    private boolean isSupplierBiddingBlocked(Long supplierId, LocalDateTime now) {
        Supplier supplier = supplierMapper.selectById(supplierId);
        return supplier != null
            && ((supplier.getBlacklistFlag() != null && supplier.getBlacklistFlag() == 1)
            || (supplier.getBidSuspendedUntil() != null && supplier.getBidSuspendedUntil().isAfter(now)));
    }

    private void markQuoteItemsAwarded(PurchaseRequest request, Long quoteId) {
        Set<Long> activeRequestItemIds = activeRequestItemIds(request == null ? null : request.getId());
        for (SupplierQuoteItem item : supplierQuoteItemMapper.selectList(new LambdaQueryWrapper<SupplierQuoteItem>()
            .eq(SupplierQuoteItem::getQuoteId, quoteId)
            .eq(SupplierQuoteItem::getStatus, QUOTE_ITEM_STATUS_ACTIVE))) {
            if (!isQuoteItemForActiveRequestItem(item, activeRequestItemIds)) {
                clearQuoteItemAwardMarker(item);
                supplierQuoteItemMapper.updateById(item);
                continue;
            }
            item.setAwarded(1);
            if (item.getReplacementFlag() != null && item.getReplacementFlag() == 1) {
                item.setReplacementReviewStatus(REPLACEMENT_REVIEW_PENDING);
                item.setReplacementReviewedBy(null);
                item.setReplacementReviewedAt(null);
                item.setReplacementReviewRemark(null);
            }
            supplierQuoteItemMapper.updateById(item);
        }
    }

    private void syncAwardedQuoteItemsWithActiveRequestItems(PurchaseRequest request) {
        if (request == null || request.getId() == null) {
            return;
        }
        Set<Long> activeRequestItemIds = activeRequestItemIds(request.getId());
        for (SupplierQuoteItem item : supplierQuoteItemMapper.selectList(new LambdaQueryWrapper<SupplierQuoteItem>()
            .eq(SupplierQuoteItem::getPurchaseRequestId, request.getId())
            .eq(SupplierQuoteItem::getAwarded, 1))) {
            if (isQuoteItemForActiveRequestItem(item, activeRequestItemIds)) {
                continue;
            }
            clearQuoteItemAwardMarker(item);
            supplierQuoteItemMapper.updateById(item);
        }
    }

    private Set<Long> activeRequestItemIds(Long purchaseRequestId) {
        if (purchaseRequestId == null) {
            return Set.of();
        }
        return activeRequestItems(purchaseRequestId).stream()
            .map(PurchaseRequestItem::getId)
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private boolean isQuoteItemForActiveRequestItem(SupplierQuoteItem item, Set<Long> activeRequestItemIds) {
        return item != null
            && item.getPurchaseRequestItemId() != null
            && activeRequestItemIds != null
            && activeRequestItemIds.contains(item.getPurchaseRequestItemId());
    }

    private void clearQuoteItemAwardMarker(SupplierQuoteItem item) {
        if (item == null) {
            return;
        }
        item.setAwarded(0);
        if (item.getReplacementFlag() != null && item.getReplacementFlag() == 1) {
            item.setReplacementReviewStatus(null);
            item.setReplacementReviewedBy(null);
            item.setReplacementReviewedAt(null);
            item.setReplacementReviewRemark(null);
        }
    }

    private void adjustReplacementOriginalPrices(Long purchaseRequestId, List<SupplierQuote> eligibleQuotes) {
        List<Long> quoteIds = eligibleQuotes == null ? List.of() : eligibleQuotes.stream()
            .map(SupplierQuote::getId)
            .filter(Objects::nonNull)
            .toList();
        if (purchaseRequestId == null || quoteIds.isEmpty()) {
            return;
        }

        List<SupplierQuoteItem> quoteItems = supplierQuoteItemMapper.selectList(new LambdaQueryWrapper<SupplierQuoteItem>()
            .eq(SupplierQuoteItem::getPurchaseRequestId, purchaseRequestId)
            .eq(SupplierQuoteItem::getStatus, QUOTE_ITEM_STATUS_ACTIVE)
            .in(SupplierQuoteItem::getQuoteId, quoteIds));
        Map<Long, List<SupplierQuoteItem>> itemsByRequestItem = quoteItems.stream()
            .filter(item -> item.getPurchaseRequestItemId() != null)
            .collect(Collectors.groupingBy(
                SupplierQuoteItem::getPurchaseRequestItemId,
                LinkedHashMap::new,
                Collectors.toList()
            ));
        Set<Long> changedQuoteIds = new HashSet<>();
        for (List<SupplierQuoteItem> comparableItems : itemsByRequestItem.values()) {
            List<BigDecimal> originalProductPrices = comparableItems.stream()
                .filter(item -> !isReplacementQuoteItem(item))
                .map(this::supplierSubmittedUnitPrice)
                .filter(price -> price != null && price.compareTo(BigDecimal.ZERO) > 0)
                .sorted(Comparator.reverseOrder())
                .toList();
            if (originalProductPrices.isEmpty()) {
                continue;
            }
            BigDecimal benchmark = originalProductPrices.get(originalProductPrices.size() > 1 ? 1 : 0);
            BigDecimal adjustedUnitPrice = benchmark.add(BigDecimal.ONE).setScale(2, RoundingMode.HALF_UP);
            for (SupplierQuoteItem item : comparableItems) {
                if (!isReplacementQuoteItem(item)) {
                    continue;
                }
                boolean originalPriceMissing = item.getSupplierSubmittedUnitPrice() == null;
                if (originalPriceMissing) {
                    item.setSupplierSubmittedUnitPrice(item.getUnitPrice());
                }
                BigDecimal qty = item.getQty() == null ? BigDecimal.ZERO : item.getQty();
                BigDecimal adjustedLineAmount = qty.multiply(adjustedUnitPrice).setScale(2, RoundingMode.HALF_UP);
                if (!originalPriceMissing
                    && sameAmount(item.getUnitPrice(), adjustedUnitPrice)
                    && sameAmount(item.getLineAmount(), adjustedLineAmount)) {
                    continue;
                }
                item.setUnitPrice(adjustedUnitPrice);
                item.setLineAmount(adjustedLineAmount);
                supplierQuoteItemMapper.updateById(item);
                changedQuoteIds.add(item.getQuoteId());
            }
        }

        for (SupplierQuote quote : eligibleQuotes) {
            if (quote == null || !changedQuoteIds.contains(quote.getId())) {
                continue;
            }
            BigDecimal adjustedTotal = quoteItems.stream()
                .filter(item -> Objects.equals(item.getQuoteId(), quote.getId()))
                .map(SupplierQuoteItem::getLineAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
            quote.setTotalAmount(adjustedTotal);
            supplierQuoteMapper.updateById(quote);
        }
    }

    private boolean isReplacementQuoteItem(SupplierQuoteItem item) {
        return item != null && Integer.valueOf(1).equals(item.getReplacementFlag());
    }

    private BigDecimal supplierSubmittedUnitPrice(SupplierQuoteItem item) {
        if (item == null) {
            return null;
        }
        return item.getSupplierSubmittedUnitPrice() == null ? item.getUnitPrice() : item.getSupplierSubmittedUnitPrice();
    }

    private BigDecimal applyItemLowestAwards(PurchaseRequest request, List<SupplierQuote> requestQuotes) {
        List<Long> quoteIds = requestQuotes.stream().map(SupplierQuote::getId).toList();
        if (quoteIds.isEmpty()) {
            throw new BizException("SUPPLIER_QUOTE_NOT_FOUND", "No available supplier quote");
        }
        List<SupplierQuoteItem> quoteItems = supplierQuoteItemMapper.selectList(new LambdaQueryWrapper<SupplierQuoteItem>()
            .eq(SupplierQuoteItem::getPurchaseRequestId, request.getId())
            .eq(SupplierQuoteItem::getStatus, QUOTE_ITEM_STATUS_ACTIVE)
            .in(SupplierQuoteItem::getQuoteId, quoteIds));
        Map<Long, List<SupplierQuoteItem>> itemQuotes = new LinkedHashMap<>();
        for (SupplierQuoteItem quoteItem : quoteItems) {
            itemQuotes.computeIfAbsent(quoteItem.getPurchaseRequestItemId(), key -> new ArrayList<>()).add(quoteItem);
        }

        BigDecimal total = BigDecimal.ZERO;
        for (PurchaseRequestItem requestItem : purchaseRequestItemMapper.selectList(new LambdaQueryWrapper<PurchaseRequestItem>()
            .eq(PurchaseRequestItem::getPurchaseRequestId, request.getId())
            .ne(PurchaseRequestItem::getStatus, ProcurementWorkflow.ITEM_STATUS_REJECTED)
            .orderByAsc(PurchaseRequestItem::getLineNo))) {
            SupplierQuoteItem awardedItem = itemQuotes.getOrDefault(requestItem.getId(), List.of()).stream()
                .min(Comparator
                    .comparing((SupplierQuoteItem item) -> item.getLineAmount() == null ? BigDecimal.ZERO : item.getLineAmount())
                    .thenComparing(item -> item.getLeadTimeDays() == null ? Integer.MAX_VALUE : item.getLeadTimeDays())
                    .thenComparing(SupplierQuoteItem::getQuoteId))
                .orElse(null);
            if (awardedItem == null) {
                throw new BizException("QUOTE_ITEM_MISSING", "Every purchase item must have supplier quotes before item-level award");
            }
            awardedItem.setAwarded(1);
            if (awardedItem.getReplacementFlag() != null && awardedItem.getReplacementFlag() == 1) {
                awardedItem.setReplacementReviewStatus(REPLACEMENT_REVIEW_PENDING);
                awardedItem.setReplacementReviewedBy(null);
                awardedItem.setReplacementReviewedAt(null);
                awardedItem.setReplacementReviewRemark(null);
            }
            supplierQuoteItemMapper.updateById(awardedItem);
            if (awardedItem.getLineAmount() != null) {
                total = total.add(awardedItem.getLineAmount());
            }
        }
        return total;
    }

    private void markAwardedSuppliersPending(PurchaseRequest request) {
        Set<Long> awardedQuoteIds = resolveAwardedQuoteIds(request);
        LocalDateTime now = LocalDateTime.now();
        for (Long quoteId : awardedQuoteIds) {
            SupplierQuote quote = supplierQuoteMapper.selectById(quoteId);
            if (quote != null
                && !ProcurementWorkflow.SUPPLIER_CONFIRM_STATUS_CONFIRMED.equals(quote.getSupplierConfirmStatus())) {
                LocalDateTime dueAt = awardNotificationService.supplierConfirmDueAt(now);
                quote.setSupplierConfirmStatus(ProcurementWorkflow.SUPPLIER_CONFIRM_STATUS_PENDING);
                quote.setSupplierConfirmRemark(null);
                quote.setSupplierConfirmedBy(null);
                quote.setSupplierConfirmedAt(null);
                quote.setSupplierConfirmDueAt(dueAt);
                quote.setSupplierConfirmNotifiedAt(null);
                supplierQuoteMapper.updateById(quote);
                if (awardNotificationService.sendAwardConfirmationEmail(request, quote, dueAt)) {
                    quote.setSupplierConfirmNotifiedAt(LocalDateTime.now());
                    supplierQuoteMapper.updateById(quote);
                }
            }
        }
    }

    private Set<Long> resolveAwardedQuoteIds(PurchaseRequest request) {
        Set<Long> quoteIds = new HashSet<>();
        if (ProcurementWorkflow.isItemLowestAwardStrategy(request.getAwardStrategy())) {
            supplierQuoteItemMapper.selectList(new LambdaQueryWrapper<SupplierQuoteItem>()
                    .eq(SupplierQuoteItem::getPurchaseRequestId, request.getId())
                    .eq(SupplierQuoteItem::getAwarded, 1))
                .forEach(item -> quoteIds.add(item.getQuoteId()));
        } else if (request.getSelectedQuoteId() != null) {
            quoteIds.add(request.getSelectedQuoteId());
        }
        return quoteIds;
    }

    private boolean isQuoteAwarded(PurchaseRequest request, Long quoteId) {
        if (quoteId == null) {
            return false;
        }
        if (ProcurementWorkflow.isItemLowestAwardStrategy(request.getAwardStrategy())) {
            return supplierQuoteItemMapper.selectCount(new LambdaQueryWrapper<SupplierQuoteItem>()
                .eq(SupplierQuoteItem::getQuoteId, quoteId)
                .eq(SupplierQuoteItem::getAwarded, 1)) > 0;
        }
        return request.getSelectedQuoteId() != null && request.getSelectedQuoteId().equals(quoteId);
    }

    private String generateInstanceNo() {
        String timePart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String randomPart = UUID.randomUUID().toString().replace("-", "").substring(0, 4).toUpperCase(Locale.ROOT);
        return "AP" + timePart + randomPart;
    }

    private String generateAggregateReqNo() {
        return requestNoService.allocateAggregate();
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

    private long countMyApprovalTasks(AuthUser authUser) {
        if (DataScopeHelper.isSystemAdmin(authUser)) {
            return purchaseRequestMapper.selectCount(new LambdaQueryWrapper<PurchaseRequest>()
                .eq(PurchaseRequest::getStatus, ProcurementWorkflow.STATUS_PENDING_APPROVAL)
                .eq(PurchaseRequest::getApprovalStatus, ProcurementWorkflow.APPROVAL_PENDING));
        }

        LambdaQueryWrapper<PurchaseRequest> wrapper = new LambdaQueryWrapper<PurchaseRequest>()
            .eq(PurchaseRequest::getStatus, ProcurementWorkflow.STATUS_PENDING_APPROVAL)
            .eq(PurchaseRequest::getApprovalStatus, ProcurementWorkflow.APPROVAL_PENDING);
        return purchaseRequestMapper.selectList(wrapper).stream()
            .filter(request -> canApproveRequest(authUser, request))
            .count();
    }

    private long countQuotingRequests(AuthUser authUser) {
        LambdaQueryWrapper<PurchaseRequest> wrapper = new LambdaQueryWrapper<PurchaseRequest>()
            .eq(PurchaseRequest::getStatus, ProcurementWorkflow.STATUS_QUOTING);
        if (DataScopeHelper.isSupplierUser(authUser) || DataScopeHelper.SCOPE_REGION.equals(DataScopeHelper.resolveDataScope(authUser))) {
            if (authUser.getRegionId() != null) {
                wrapper.and(scope -> scope
                    .eq(PurchaseRequest::getRegionId, authUser.getRegionId())
                    .or()
                    .eq(PurchaseRequest::getReqReason, NATIONAL_DEMAND_AGGREGATE_REASON));
            }
        }
        return purchaseRequestMapper.selectCount(wrapper);
    }

    private long countComparedRequests(AuthUser authUser) {
        List<SupplierQuote> quotes = supplierQuoteMapper.selectList(new LambdaQueryWrapper<SupplierQuote>()
            .eq(SupplierQuote::getStatus, ProcurementWorkflow.QUOTE_STATUS_SUBMITTED));
        if (quotes.isEmpty()) {
            return 0;
        }
        if (DataScopeHelper.isSupplierUser(authUser)) {
            return quotes.stream()
                .filter(quote -> authUser.getUserId().equals(quote.getSupplierUserId()))
                .map(SupplierQuote::getPurchaseRequestId)
                .distinct()
                .count();
        }

        Map<Long, PurchaseRequest> requestMap = loadRequests(quotes);
        return quotes.stream()
            .map(SupplierQuote::getPurchaseRequestId)
            .distinct()
            .filter(requestId -> {
                PurchaseRequest request = requestMap.get(requestId);
                return request != null && (DataScopeHelper.isSystemAdmin(authUser)
                    || authUser.getRegionId() == null
                    || authUser.getRegionId().equals(request.getRegionId())
                    || isNationalDemandAggregate(request));
            })
            .count();
    }

    private Map<Long, PurchaseRequest> loadRequests(List<SupplierQuote> quotes) {
        List<Long> requestIds = quotes.stream().map(SupplierQuote::getPurchaseRequestId).distinct().toList();
        if (requestIds.isEmpty()) {
            return Map.of();
        }
        List<PurchaseRequest> requests = purchaseRequestMapper.selectList(new LambdaQueryWrapper<PurchaseRequest>()
            .in(PurchaseRequest::getId, requestIds));
        Map<Long, PurchaseRequest> requestMap = new LinkedHashMap<>();
        for (PurchaseRequest request : requests) {
            requestMap.put(request.getId(), request);
        }
        return requestMap;
    }

    private Map<Long, Supplier> loadSuppliers(List<SupplierQuote> quoteEntities) {
        List<Long> supplierIds = quoteEntities.stream().map(SupplierQuote::getSupplierId).distinct().toList();
        if (supplierIds.isEmpty()) {
            return Map.of();
        }
        List<Supplier> suppliers = supplierMapper.selectList(new LambdaQueryWrapper<Supplier>()
            .in(Supplier::getId, supplierIds));
        Map<Long, Supplier> supplierMap = new LinkedHashMap<>();
        for (Supplier supplier : suppliers) {
            supplierMap.put(supplier.getId(), supplier);
        }
        return supplierMap;
    }

    private SupplierQuoteVO toQuoteVO(SupplierQuote quote, Supplier supplier) {
        PurchaseRequest request = purchaseRequestMapper.selectById(quote.getPurchaseRequestId());
        return toQuoteVO(quote, supplier, request);
    }

    private SupplierQuoteVO toQuoteVO(SupplierQuote quote, Supplier supplier, PurchaseRequest request) {
        SupplierQuoteVO vo = new SupplierQuoteVO();
        vo.setId(quote.getId());
        vo.setQuoteNo(quote.getQuoteNo());
        vo.setPurchaseRequestId(quote.getPurchaseRequestId());
        vo.setSupplierId(quote.getSupplierId());
        vo.setSupplierName(supplier == null ? "供应商" : supplier.getSupplierName());
        vo.setSupplierUserId(quote.getSupplierUserId());
        vo.setQuoteSource(firstText(quote.getQuoteSource(), TemporarySupplierRecommendationService.QUOTE_SOURCE_NORMAL));
        vo.setRecommendationId(quote.getRecommendationId());
        vo.setAwardEligible(temporarySupplierRecommendationService.isQuoteAwardEligible(quote));
        vo.setAwardEligibilityText(temporarySupplierRecommendationService.quoteEligibilityText(quote));
        vo.setTotalAmount(quote.getTotalAmount());
        vo.setLeadTimeDays(quote.getLeadTimeDays());
        vo.setQuoteRemark(quote.getQuoteRemark());
        vo.setStatus(quote.getStatus());
        vo.setSelected(request != null && isQuoteAwarded(request, quote.getId()));
        vo.setSupplierConfirmStatus(quote.getSupplierConfirmStatus());
        vo.setSupplierConfirmRemark(quote.getSupplierConfirmRemark());
        vo.setSupplierConfirmedBy(quote.getSupplierConfirmedBy());
        vo.setSupplierConfirmedAt(quote.getSupplierConfirmedAt());
        vo.setSubmittedAt(quote.getSubmittedAt());
        vo.setCreatedAt(quote.getCreatedAt());
        vo.setUpdatedAt(quote.getUpdatedAt());
        vo.setItems(listQuoteItems(quote.getId()));
        return vo;
    }

    private List<SupplierQuoteItemVO> listQuoteItems(Long quoteId) {
        return supplierQuoteItemMapper.selectList(new LambdaQueryWrapper<SupplierQuoteItem>()
                .eq(SupplierQuoteItem::getQuoteId, quoteId)
                .orderByAsc(SupplierQuoteItem::getLineNo))
            .stream()
            .map(this::toQuoteItemVO)
            .toList();
    }

    private PurchaseRequestItemVO toPurchaseRequestItemVO(PurchaseRequestItem item) {
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
        vo.setNeedDate(item.getNeedDate());
        vo.setPurpose(item.getPurpose());
        vo.setEcommerceUrl(item.getEcommerceUrl());
        vo.setReceivingCity(item.getReceivingCity());
        vo.setDeliveryAddress(item.getDeliveryAddress());
        vo.setSourceRequestId(item.getSourceRequestId());
        vo.setSourceRequestItemId(item.getSourceRequestItemId());
        vo.setStatus(item.getStatus());
        vo.setRemark(item.getRemark());
        return vo;
    }

    private SupplierQuoteItemVO toQuoteItemVO(SupplierQuoteItem item) {
        SupplierQuoteItemVO vo = new SupplierQuoteItemVO();
        vo.setId(item.getId());
        vo.setQuoteId(item.getQuoteId());
        vo.setPurchaseRequestId(item.getPurchaseRequestId());
        vo.setPurchaseRequestItemId(item.getPurchaseRequestItemId());
        vo.setLineNo(item.getLineNo());
        vo.setItemCode(item.getItemCode());
        vo.setItemName(item.getItemName());
        vo.setSpecModel(item.getSpecModel());
        vo.setUnit(item.getUnit());
        vo.setQty(item.getQty());
        vo.setUnitPrice(item.getUnitPrice());
        vo.setSupplierSubmittedUnitPrice(item.getSupplierSubmittedUnitPrice());
        vo.setLineAmount(item.getLineAmount());
        vo.setLeadTimeDays(item.getLeadTimeDays());
        vo.setQuoteRemark(item.getQuoteRemark());
        vo.setReplacementFlag(item.getReplacementFlag() != null && item.getReplacementFlag() == 1);
        vo.setReplacementItemName(item.getReplacementItemName());
        vo.setReplacementSpecModel(item.getReplacementSpecModel());
        vo.setReplacementEcommerceUrl(item.getReplacementEcommerceUrl());
        vo.setReplacementUnitPrice(item.getReplacementUnitPrice());
        vo.setReplacementLineAmount(item.getReplacementLineAmount());
        vo.setReplacementReason(item.getReplacementReason());
        vo.setReplacementReviewStatus(item.getReplacementReviewStatus());
        vo.setReplacementReviewRemark(item.getReplacementReviewRemark());
        vo.setWaived(QUOTE_ITEM_STATUS_WAIVED.equals(item.getStatus()));
        vo.setAwarded(item.getAwarded() != null && item.getAwarded() == 1);
        vo.setReceivingCity(item.getReceivingCity());
        if (vo.getAwarded()) {
            vo.setDeliveryAddress(item.getDeliveryAddress());
        }
        vo.setStatus(item.getStatus());
        return vo;
    }

    private ApprovalRecordVO toApprovalRecordVO(ApprovalRecord record) {
        ApprovalRecordVO vo = new ApprovalRecordVO();
        vo.setId(record.getId());
        vo.setInstanceId(record.getInstanceId());
        vo.setPurchaseRequestId(record.getPurchaseRequestId());
        vo.setNodeNo(record.getNodeNo());
        vo.setNodeName(record.getNodeName());
        vo.setApproverUserId(record.getApproverUserId());
        vo.setApproverName(userDisplayNameById(record.getApproverUserId()));
        vo.setApproverRoleCode(record.getApproverRoleCode());
        vo.setAction(record.getAction());
        vo.setFromStatus(record.getFromStatus());
        vo.setToStatus(record.getToStatus());
        vo.setApprovalComment(record.getApprovalComment());
        vo.setOperatedAt(record.getOperatedAt());
        vo.setSubmitRound(record.getSubmitRound());
        vo.setIsFinalNode(record.getIsFinalNode());
        vo.setCanResubmit(record.getCanResubmit());
        vo.setTransferToUserId(record.getTransferToUserId());
        vo.setTransferToUserName(userDisplayNameById(record.getTransferToUserId()));
        vo.setStatus(record.getStatus());
        vo.setRemark(record.getRemark());
        vo.setCreatedAt(record.getCreatedAt());
        vo.setUpdatedAt(record.getUpdatedAt());
        return vo;
    }

    private String userDisplayNameById(Long userId) {
        if (userId == null) {
            return "";
        }
        SysUser user = userMapper.selectById(userId);
        return user == null ? "" : userDisplayName(user);
    }

    private SupplierQuoteSummaryVO buildQuoteSummary(PurchaseRequest request, AuthUser authUser) {
        List<SupplierQuote> quoteEntities = supplierQuoteMapper.selectList(new LambdaQueryWrapper<SupplierQuote>()
            .eq(SupplierQuote::getPurchaseRequestId, request.getId())
            .eq(SupplierQuote::getStatus, ProcurementWorkflow.QUOTE_STATUS_SUBMITTED)
            .orderByAsc(SupplierQuote::getTotalAmount)
            .orderByAsc(SupplierQuote::getSubmittedAt));

        SupplierQuoteSummaryVO summary = new SupplierQuoteSummaryVO();
        summary.setPurchaseRequestId(request.getId());
        summary.setAwardStrategy(ProcurementWorkflow.normalizeAwardStrategy(request.getAwardStrategy()));
        summary.setSupplierQuoteCount(quoteEntities.size());
        summary.setSelectedQuoteId(request.getSelectedQuoteId());
        summary.setLeaderConfirmStatus(request.getLeaderConfirmStatus());
        summary.setLeaderConfirmRemark(request.getLeaderConfirmRemark());
        summary.setLeaderConfirmedBy(request.getLeaderConfirmedBy());
        summary.setLeaderConfirmedAt(request.getLeaderConfirmedAt());
        boolean showQuoteDetails = quoteDetailsVisible(authUser, request);
        summary.setQuoteDetailsVisible(showQuoteDetails);
        if (!showQuoteDetails) {
            summary.setQuotes(List.of());
            return summary;
        }

        Map<Long, Supplier> supplierMap = loadSuppliers(quoteEntities);
        boolean itemLowestStrategy = ProcurementWorkflow.isItemLowestAwardStrategy(request.getAwardStrategy());
        Set<Long> completeQuoteIds = quoteEntities.stream()
            .filter(quote -> itemLowestStrategy || hasCompleteActiveQuoteItems(request, quote))
            .map(SupplierQuote::getId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        List<SupplierQuoteVO> quotes = quoteEntities.stream()
            .map(quote -> toQuoteVO(quote, supplierMap.get(quote.getSupplierId()), request))
            .toList();
        summary.setQuotes(quotes);
        quotes.stream()
            .filter(quote -> Boolean.TRUE.equals(quote.getAwardEligible()))
            .filter(quote -> quote.getId() != null && completeQuoteIds.contains(quote.getId()))
            .findFirst()
            .ifPresent(lowest -> {
            summary.setLowestAmount(lowest.getTotalAmount());
            summary.setLowestSupplierId(lowest.getSupplierId());
            summary.setLowestSupplierName(lowest.getSupplierName());
            });
        if (request.getSelectedQuoteId() != null) {
            quotes.stream()
                .filter(SupplierQuoteVO::getSelected)
                .findFirst()
                .ifPresent(selected -> {
                    summary.setSelectedSupplierId(selected.getSupplierId());
                    summary.setSelectedSupplierName(selected.getSupplierName());
                });
        } else if (ProcurementWorkflow.isItemLowestAwardStrategy(request.getAwardStrategy())) {
            summary.setSelectedSupplierName(resolveSelectedSupplierName(request, supplierMap));
        }
        return summary;
    }

    private List<List<String>> buildQuoteComparisonExportRows(PurchaseRequest request) {
        List<SupplierQuote> quoteEntities = supplierQuoteMapper.selectList(new LambdaQueryWrapper<SupplierQuote>()
            .eq(SupplierQuote::getPurchaseRequestId, request.getId())
            .eq(SupplierQuote::getStatus, ProcurementWorkflow.QUOTE_STATUS_SUBMITTED)
            .orderByAsc(SupplierQuote::getTotalAmount)
            .orderByAsc(SupplierQuote::getSubmittedAt));
        Map<Long, Supplier> supplierMap = loadSuppliers(quoteEntities);
        Map<Long, Map<Long, SupplierQuoteItem>> quoteItemMap = new LinkedHashMap<>();
        for (SupplierQuote quote : quoteEntities) {
            Map<Long, SupplierQuoteItem> itemMap = new LinkedHashMap<>();
            for (SupplierQuoteItem item : supplierQuoteItemMapper.selectList(new LambdaQueryWrapper<SupplierQuoteItem>()
                .eq(SupplierQuoteItem::getQuoteId, quote.getId())
                .orderByAsc(SupplierQuoteItem::getLineNo))) {
                itemMap.put(item.getPurchaseRequestItemId(), item);
            }
            quoteItemMap.put(quote.getId(), itemMap);
        }

        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of(
            "申请单号",
            "申请标题",
            "负责人确认状态",
            "负责人确认备注",
            "中标供应商",
            "供应商确认状态"
        ));
        rows.add(List.of(
            request.getReqNo(),
            defaultText(displayReqTitle(request.getReqTitle())),
            defaultText(request.getLeaderConfirmStatus()),
            defaultText(request.getLeaderConfirmRemark()),
            defaultText(resolveSelectedSupplierName(request, supplierMap)),
            defaultText(resolveSelectedSupplierConfirmStatus(request))
        ));
        rows.add(List.of());

        List<String> header = new ArrayList<>(List.of(
            "行号",
            "物料编码",
            "商品名称",
            "规格型号",
            "单位",
            "数量",
            "需求参考单价"
        ));
        for (SupplierQuote quote : quoteEntities) {
            String supplierName = supplierMap.containsKey(quote.getSupplierId())
                ? defaultText(supplierMap.get(quote.getSupplierId()).getSupplierName())
                : "供应商";
            header.add(supplierName + " 报价状态");
            header.add(supplierName + " 是否替换商品");
            header.add(supplierName + " 报价单价");
            header.add(supplierName + " 报价金额");
        }
        header.add("中标供应商");
        header.add("中标单价");
        header.add("中标金额");
        header.add("价格提示");
        header.add("供应商确认状态");
        rows.add(header);

        SupplierQuote selectedQuote = request.getSelectedQuoteId() == null ? null : supplierQuoteMapper.selectById(request.getSelectedQuoteId());
        Map<Long, SupplierQuoteItem> selectedQuoteItems = ProcurementWorkflow.isItemLowestAwardStrategy(request.getAwardStrategy())
            ? loadAwardedItemMap(request.getId())
            : selectedQuote == null ? Map.of() : quoteItemMap.getOrDefault(selectedQuote.getId(), Map.of());

        List<PurchaseRequestItem> requestItems = purchaseRequestItemMapper.selectList(new LambdaQueryWrapper<PurchaseRequestItem>()
            .eq(PurchaseRequestItem::getPurchaseRequestId, request.getId())
            .ne(PurchaseRequestItem::getStatus, ProcurementWorkflow.ITEM_STATUS_REJECTED)
            .orderByAsc(PurchaseRequestItem::getLineNo));
        for (PurchaseRequestItem requestItem : requestItems) {
            List<String> row = new ArrayList<>(List.of(
                String.valueOf(requestItem.getLineNo()),
                defaultText(requestItem.getItemCode()),
                defaultText(requestItem.getItemName()),
                defaultText(requestItem.getSpecModel()),
                defaultText(requestItem.getUnit()),
                decimalText(requestItem.getQty()),
                decimalText(requestItem.getUnitPrice())
            ));
            for (SupplierQuote quote : quoteEntities) {
                SupplierQuoteItem item = quoteItemMap.getOrDefault(quote.getId(), Map.of()).get(requestItem.getId());
                row.add(quoteItemStatusText(item));
                row.add(item != null && item.getReplacementFlag() != null && item.getReplacementFlag() == 1 ? "是" : "否");
                row.add(decimalText(item == null ? null : item.getUnitPrice()));
                row.add(decimalText(item == null ? null : item.getLineAmount()));
            }
            SupplierQuoteItem selectedItem = selectedQuoteItems.get(requestItem.getId());
            row.add(defaultText(ProcurementWorkflow.isItemLowestAwardStrategy(request.getAwardStrategy())
                ? resolveSupplierNameByQuoteItem(selectedItem, supplierMap)
                : resolveSelectedSupplierName(request, supplierMap)));
            row.add(decimalText(selectedItem == null ? null : selectedItem.getUnitPrice()));
            row.add(decimalText(selectedItem == null ? null : selectedItem.getLineAmount()));
            row.add(buildPriceHint(requestItem, selectedItem));
            row.add(defaultText(resolveSelectedSupplierConfirmStatus(request)));
            rows.add(row);
        }
        return rows;
    }

    private String resolveSelectedSupplierName(PurchaseRequest request, Map<Long, Supplier> supplierMap) {
        if (ProcurementWorkflow.isItemLowestAwardStrategy(request.getAwardStrategy())) {
            List<String> names = resolveAwardedQuoteIds(request).stream()
                .map(supplierQuoteMapper::selectById)
                .filter(quote -> quote != null)
                .map(quote -> supplierMap.get(quote.getSupplierId()))
                .filter(supplier -> supplier != null && StringUtils.hasText(supplier.getSupplierName()))
                .map(Supplier::getSupplierName)
                .distinct()
                .toList();
            return names.isEmpty() ? "" : String.join("/", names);
        }
        if (request.getSelectedQuoteId() == null) {
            return "";
        }
        SupplierQuote selectedQuote = supplierQuoteMapper.selectById(request.getSelectedQuoteId());
        if (selectedQuote == null) {
            return "";
        }
        Supplier supplier = supplierMap.get(selectedQuote.getSupplierId());
        return supplier == null ? "" : supplier.getSupplierName();
    }

    private String resolveSelectedSupplierConfirmStatus(PurchaseRequest request) {
        if (ProcurementWorkflow.isItemLowestAwardStrategy(request.getAwardStrategy())) {
            List<SupplierQuote> awardedQuotes = resolveAwardedQuoteIds(request).stream()
                .map(supplierQuoteMapper::selectById)
                .filter(quote -> quote != null)
                .toList();
            if (awardedQuotes.isEmpty()) {
                return "";
            }
            boolean allConfirmed = awardedQuotes.stream()
                .allMatch(quote -> ProcurementWorkflow.SUPPLIER_CONFIRM_STATUS_CONFIRMED.equals(quote.getSupplierConfirmStatus()));
            return allConfirmed ? ProcurementWorkflow.SUPPLIER_CONFIRM_STATUS_CONFIRMED : ProcurementWorkflow.SUPPLIER_CONFIRM_STATUS_PENDING;
        }
        if (request.getSelectedQuoteId() == null) {
            return "";
        }
        SupplierQuote selectedQuote = supplierQuoteMapper.selectById(request.getSelectedQuoteId());
        return selectedQuote == null ? "" : defaultText(selectedQuote.getSupplierConfirmStatus());
    }

    private Map<Long, SupplierQuoteItem> loadAwardedItemMap(Long purchaseRequestId) {
        Map<Long, SupplierQuoteItem> map = new LinkedHashMap<>();
        for (SupplierQuoteItem item : supplierQuoteItemMapper.selectList(new LambdaQueryWrapper<SupplierQuoteItem>()
            .eq(SupplierQuoteItem::getPurchaseRequestId, purchaseRequestId)
            .eq(SupplierQuoteItem::getAwarded, 1))) {
            map.put(item.getPurchaseRequestItemId(), item);
        }
        return map;
    }

    private String resolveSupplierNameByQuoteItem(SupplierQuoteItem item, Map<Long, Supplier> supplierMap) {
        if (item == null) {
            return "";
        }
        SupplierQuote quote = supplierQuoteMapper.selectById(item.getQuoteId());
        if (quote == null) {
            return "";
        }
        Supplier supplier = supplierMap.get(quote.getSupplierId());
        return supplier == null ? "" : supplier.getSupplierName();
    }

    private String buildPriceHint(PurchaseRequestItem requestItem,
                                  SupplierQuoteItem selectedItem) {
        if (selectedItem == null) {
            return "未选择报价明细";
        }
        if (requestItem.getUnitPrice() != null
            && selectedItem.getUnitPrice() != null
            && selectedItem.getUnitPrice().compareTo(requestItem.getUnitPrice()) > 0) {
            return "高于参考价，需确认";
        }
        if (StringUtils.hasText(selectedItem.getQuoteRemark())) {
            return selectedItem.getQuoteRemark();
        }
        return "正常";
    }

    private String quoteItemStatusText(SupplierQuoteItem item) {
        if (item == null) {
            return "未报价";
        }
        if (QUOTE_ITEM_STATUS_WAIVED.equals(item.getStatus())) {
            return "放弃报价";
        }
        return "已报价";
    }

    private int quoteOpenDays() {
        ProcurementSetting setting = procurementSettingMapper.selectOne(new LambdaQueryWrapper<ProcurementSetting>()
            .eq(ProcurementSetting::getSettingKey, "quote_open_days")
            .last("LIMIT 1"));
        if (setting == null || !StringUtils.hasText(setting.getSettingValue())) {
            return 7;
        }
        try {
            return Math.max(0, Integer.parseInt(setting.getSettingValue().trim()));
        } catch (NumberFormatException ignored) {
            return 7;
        }
    }

    private QuoteReviewDue quoteReviewDue(PurchaseRequest request, int openDays, LocalDateTime now) {
        reconcileQuoteItemsWithActiveRequestItems(request);
        LocalDateTime openedAt = quoteOpenedAt(request);
        if (openedAt == null) {
            return null;
        }
        if (isDirectAwardRequest(request)) {
            List<SupplierQuote> directQuotes = supplierQuoteMapper.selectList(new LambdaQueryWrapper<SupplierQuote>()
                .eq(SupplierQuote::getPurchaseRequestId, request.getId())
                .eq(SupplierQuote::getSupplierId, request.getDirectAwardSupplierId())
                .eq(SupplierQuote::getStatus, ProcurementWorkflow.QUOTE_STATUS_SUBMITTED)
                .orderByAsc(SupplierQuote::getSubmittedAt)
                .orderByAsc(SupplierQuote::getId));
            SupplierQuote directQuote = resolveDirectAwardQuote(request, directQuotes);
            if (directQuote == null) {
                return null;
            }
            if (directAwardQuoteExceedsLimit(request, directQuote)) {
                downgradeDirectAwardToThreeQuotes(request, directQuote, "系统自动比价：定向供应商报价超过低额紧急采购上限，转入三方比价");
                return null;
            }
            return new QuoteReviewDue("系统自动比价：低额紧急采购定向供应商报价未超过上限，免三方比价", false);
        }
        LocalDateTime quoteDeadline = quoteDeadlineAt(request, openDays);
        LocalDateTime countdownDueAt = startFastQuoteCountdownIfReady(request, quoteDeadline);
        if (countdownDueAt != null && request.getQuoteCountdownStartedAt() != null) {
            return now.isBefore(countdownDueAt)
                ? null
                : new QuoteReviewDue("系统自动比价：已满足3家报价并完成3小时倒计时", false);
        }
        return now.isBefore(quoteDeadline)
            ? null
            : new QuoteReviewDue("系统自动比价：报价期满 " + openDays + " 个自然日，未报价供应商视为放弃报价", true);
    }

    private LocalDateTime startFastQuoteCountdownIfReady(PurchaseRequest request, LocalDateTime quoteDeadline) {
        if (isDirectAwardRequest(request)) {
            return null;
        }
        if (request.getQuoteCountdownDueAt() != null && request.getQuoteCountdownStartedAt() == null) {
            return null;
        }
        if (request.getQuoteCountdownStartedAt() != null && request.getQuoteCountdownDueAt() != null) {
            return request.getQuoteCountdownDueAt();
        }
        LocalDateTime thirdSubmittedAt = thirdSubmittedQuoteAt(request.getId());
        if (thirdSubmittedAt == null || !thirdSubmittedAt.isBefore(quoteDeadline)) {
            return null;
        }
        LocalDateTime dueAt = thirdSubmittedAt.plusHours(FAST_QUOTE_COUNTDOWN_HOURS);
        if (!dueAt.isBefore(quoteDeadline)) {
            return null;
        }
        request.setQuoteCountdownStartedAt(thirdSubmittedAt);
        request.setQuoteCountdownDueAt(dueAt);
        purchaseRequestMapper.updateById(request);
        return dueAt;
    }

    private LocalDateTime quoteDeadlineAt(PurchaseRequest request, int openDays) {
        if (request == null) {
            return null;
        }
        if (request.getQuoteCountdownDueAt() != null) {
            return request.getQuoteCountdownDueAt();
        }
        LocalDateTime openedAt = quoteOpenedAt(request);
        return openedAt == null ? null : openedAt.plusDays(openDays);
    }

    private static class QuoteReviewDue {
        private final String remark;
        private final boolean allowQuoteShortage;

        private QuoteReviewDue(String remark, boolean allowQuoteShortage) {
            this.remark = remark;
            this.allowQuoteShortage = allowQuoteShortage;
        }
    }

    private LocalDateTime thirdSubmittedQuoteAt(Long purchaseRequestId) {
        List<SupplierQuote> submittedQuotes = supplierQuoteMapper.selectList(new LambdaQueryWrapper<SupplierQuote>()
            .eq(SupplierQuote::getPurchaseRequestId, purchaseRequestId)
            .eq(SupplierQuote::getStatus, ProcurementWorkflow.QUOTE_STATUS_SUBMITTED)
            .isNotNull(SupplierQuote::getSubmittedAt)
            .orderByAsc(SupplierQuote::getSubmittedAt)
            .orderByAsc(SupplierQuote::getId));
        return submittedQuotes.size() < 3 ? null : submittedQuotes.get(2).getSubmittedAt();
    }

    private LocalDateTime quoteOpenedAt(PurchaseRequest request) {
        if (request.getApprovedAt() != null) {
            return request.getApprovedAt();
        }
        return request.getUpdatedAt();
    }

    private WorkflowSubmitResultVO emptySubmitResult(PurchaseRequest request) {
        WorkflowSubmitResultVO result = new WorkflowSubmitResultVO();
        if (request != null) {
            result.setRequestId(request.getId());
            result.setReqNo(request.getReqNo());
            result.setReqTitle(displayReqTitle(request.getReqTitle()));
        }
        return result;
    }

    private WorkflowSubmitResultVO buildSubmitResult(PurchaseRequest request,
                                                     ProcurementApprovalRouteResolver.ApprovalAssignment assignment) {
        WorkflowSubmitResultVO result = emptySubmitResult(request);
        if (assignment == null || assignment.candidateUserIds() == null || assignment.candidateUserIds().isEmpty()) {
            return result;
        }
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
                if (user.getId() != null && user.getId().equals(userId)) {
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

    private String decimalText(BigDecimal value) {
        return value == null ? "" : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String defaultText(String value) {
        return value == null ? "" : value;
    }

    private LocalDateTime latestDateTime(LocalDateTime... values) {
        LocalDateTime latest = null;
        if (values == null) {
            return null;
        }
        for (LocalDateTime value : values) {
            if (value != null && (latest == null || value.isAfter(latest))) {
                latest = value;
            }
        }
        return latest;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String trimToLength(String value, int maxLength) {
        if (value == null || maxLength <= 0 || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 1)) + "…";
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

    private Long firstLong(Long... values) {
        if (values == null) {
            return null;
        }
        for (Long value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String sanitizeFileName(String fileName) {
        return fileName.replaceAll("[\\\\/:*?\"<>|\\s]+", "-");
    }

    private static final class DashboardProductAccumulator {
        private final String itemCode;
        private final String itemName;
        private final String specModel;
        private final String unit;
        private BigDecimal requestedQty = BigDecimal.ZERO;
        private BigDecimal awardedQty = BigDecimal.ZERO;
        private BigDecimal requestAmount = BigDecimal.ZERO;
        private BigDecimal awardedAmount = BigDecimal.ZERO;
        private final Set<Long> requestIds = new HashSet<>();
        private final Set<Long> supplierIds = new HashSet<>();

        private DashboardProductAccumulator(String itemCode, String itemName, String specModel, String unit) {
            this.itemCode = itemCode;
            this.itemName = itemName;
            this.specModel = specModel;
            this.unit = unit;
        }
    }

    private static final class DashboardSupplierAccumulator {
        private final Long supplierId;
        private final Set<Long> quoteRequestIds = new HashSet<>();
        private final Set<Long> awardedRequestIds = new HashSet<>();
        private long awardedItemCount;
        private long returnAfterSaleCount;
        private long replacementAfterSaleCount;
        private BigDecimal awardedAmount = BigDecimal.ZERO;
        private int leadTimeDays;
        private int leadTimeCount;

        private DashboardSupplierAccumulator(Long supplierId) {
            this.supplierId = supplierId;
        }
    }

    private static final class DashboardSpendAccumulator {
        private final String label;
        private BigDecimal amount = BigDecimal.ZERO;
        private final Set<Long> requestIds = new HashSet<>();

        private DashboardSpendAccumulator(String label) {
            this.label = label;
        }
    }

    private record DirectAwardCandidate(Long supplierId, String supplierName, int awardCount, LocalDateTime latestAwardAt) {
    }

    private record DemandPoolGroupKey(String groupCode) {
    }
}
