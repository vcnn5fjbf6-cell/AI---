package com.company.procurement.modules.workbench.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.procurement.common.exception.BizException;
import com.company.procurement.common.result.PageResult;
import com.company.procurement.common.security.AuthContextHolder;
import com.company.procurement.common.security.AuthUser;
import com.company.procurement.common.security.DataScopeHelper;
import com.company.procurement.modules.approval.record.entity.ApprovalRecord;
import com.company.procurement.modules.approval.record.entity.ApprovalTask;
import com.company.procurement.modules.approval.record.mapper.ApprovalRecordMapper;
import com.company.procurement.modules.approval.record.mapper.ApprovalTaskMapper;
import com.company.procurement.modules.approval.record.vo.ApprovalRecordVO;
import com.company.procurement.modules.platform.permission.dto.PlatformPermissionRequestQueryDTO;
import com.company.procurement.modules.platform.permission.dto.PlatformPermissionReviewDTO;
import com.company.procurement.modules.platform.permission.entity.PlatformPermissionRequest;
import com.company.procurement.modules.platform.permission.entity.PlatformUnit;
import com.company.procurement.modules.platform.permission.mapper.PlatformPermissionRequestMapper;
import com.company.procurement.modules.platform.permission.mapper.PlatformUnitMapper;
import com.company.procurement.modules.platform.permission.service.PlatformPermissionService;
import com.company.procurement.modules.platform.permission.vo.PlatformPermissionRequestVO;
import com.company.procurement.modules.procurement.monthly.dto.MonthlyDemandQueryDTO;
import com.company.procurement.modules.procurement.monthly.entity.MonthlyProcurementDemand;
import com.company.procurement.modules.procurement.monthly.mapper.MonthlyProcurementDemandMapper;
import com.company.procurement.modules.procurement.monthly.service.MonthlyProcurementService;
import com.company.procurement.modules.procurement.monthly.vo.MonthlyDemandVO;
import com.company.procurement.modules.procurement.order.dto.AfterSaleApprovalDTO;
import com.company.procurement.modules.procurement.order.entity.ProcurementAfterSale;
import com.company.procurement.modules.procurement.order.mapper.ProcurementAfterSaleMapper;
import com.company.procurement.modules.procurement.order.mapper.ProcurementOrderQueryMapper;
import com.company.procurement.modules.procurement.order.service.OrderInvoiceService;
import com.company.procurement.modules.procurement.order.support.SupplierSoldOrderAccess;
import com.company.procurement.modules.procurement.order.service.ProcurementOrderService;
import com.company.procurement.modules.procurement.order.vo.ProcurementOrderVO;
import com.company.procurement.modules.procurement.route.entity.ProcurementApprovalRoute;
import com.company.procurement.modules.procurement.route.mapper.ProcurementApprovalRouteMapper;
import com.company.procurement.modules.procurement.request.entity.PurchaseRequest;
import com.company.procurement.modules.procurement.request.entity.PurchaseRequestItem;
import com.company.procurement.modules.procurement.request.mapper.PurchaseRequestItemMapper;
import com.company.procurement.modules.procurement.request.mapper.PurchaseRequestMapper;
import com.company.procurement.modules.procurement.request.service.PurchaseRequestService;
import com.company.procurement.modules.procurement.request.vo.PurchaseRequestItemVO;
import com.company.procurement.modules.procurement.request.vo.PurchaseRequestVO;
import com.company.procurement.modules.procurement.workflow.ProcurementWorkflow;
import com.company.procurement.modules.procurement.workflow.dto.WorkflowApprovalActionDTO;
import com.company.procurement.modules.procurement.workflow.service.WorkflowService;
import com.company.procurement.modules.procurement.workflow.vo.WorkflowSubmitResultVO;
import com.company.procurement.modules.supplier.dto.SupplierQuotePageQueryDTO;
import com.company.procurement.modules.supplier.entity.Supplier;
import com.company.procurement.modules.supplier.entity.SupplierQuote;
import com.company.procurement.modules.supplier.entity.SupplierQuoteItem;
import com.company.procurement.modules.supplier.mapper.SupplierMapper;
import com.company.procurement.modules.supplier.mapper.SupplierQuoteItemMapper;
import com.company.procurement.modules.supplier.mapper.SupplierQuoteMapper;
import com.company.procurement.modules.supplier.service.SupplierQuoteService;
import com.company.procurement.modules.supplier.vo.SupplierQuoteRequestVO;
import com.company.procurement.modules.system.user.entity.SysUser;
import com.company.procurement.modules.system.user.mapper.SysUserMapper;
import com.company.procurement.modules.workbench.dto.WorkbenchApprovalDTO;
import com.company.procurement.modules.workbench.dto.WorkbenchFollowDTO;
import com.company.procurement.modules.workbench.dto.WorkbenchRequestQueryDTO;
import com.company.procurement.modules.workbench.entity.WorkbenchCopy;
import com.company.procurement.modules.workbench.entity.WorkbenchFollow;
import com.company.procurement.modules.workbench.mapper.WorkbenchCopyMapper;
import com.company.procurement.modules.workbench.mapper.WorkbenchFollowMapper;
import com.company.procurement.modules.workbench.service.WorkbenchService;
import com.company.procurement.modules.workbench.vo.WorkbenchDetailRowVO;
import com.company.procurement.modules.workbench.vo.WorkbenchFlowNodeVO;
import com.company.procurement.modules.workbench.vo.WorkbenchMetricVO;
import com.company.procurement.modules.workbench.vo.WorkbenchPageVO;
import com.company.procurement.modules.workbench.vo.WorkbenchRequestDetailVO;
import com.company.procurement.modules.workbench.vo.WorkbenchRequestVO;
import com.company.procurement.modules.workbench.vo.WorkbenchUserOptionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WorkbenchServiceImpl implements WorkbenchService {

    private static final String SOURCE_PURCHASE = "PURCHASE";
    private static final String SOURCE_PLATFORM = "PLATFORM";
    private static final String SOURCE_MONTHLY = "MONTHLY";
    private static final String SOURCE_SUPPLIER = "SUPPLIER";
    private static final String SOURCE_AFTER_SALE = "AFTER_SALE";
    private static final String APP_CODE_PLATFORM = "PLATFORM";
    private static final String APP_CODE_PROCUREMENT = "PROCUREMENT";
    private static final String APPROVAL_TASK_PENDING = "PENDING";
    private static final String AFTER_SALE_STATUS_PENDING = "PENDING";
    private static final String AFTER_SALE_STATUS_PROPOSAL_PENDING = "PROPOSAL_PENDING";
    private static final String AFTER_SALE_STATUS_APPROVED = "APPROVED";
    private static final String AFTER_SALE_STATUS_REJECTED = "REJECTED";
    private static final BigDecimal AFTER_SALE_UNIT_PRICE_INCREASE_LIMIT = new BigDecimal("400.00");
    private static final String NATIONAL_DEMAND_AGGREGATE_REASON = "需求池全国月度汇总生成";
    private static final String SYSTEM_ADMIN_NAME = "系统管理员";
    private static final String PLATFORM_PERMISSION_MANAGE = "platform_permission:manage";
    private static final String WILDCARD_PERMISSION_CODE = "*:*:*";

    private final WorkflowService workflowService;
    private final PurchaseRequestService purchaseRequestService;
    private final PlatformPermissionService platformPermissionService;
    private final MonthlyProcurementService monthlyProcurementService;
    private final SupplierQuoteService supplierQuoteService;
    private final ProcurementOrderService procurementOrderService;
    private final WorkbenchCopyMapper copyMapper;
    private final WorkbenchFollowMapper followMapper;
    private final PlatformPermissionRequestMapper platformRequestMapper;
    private final PlatformUnitMapper platformUnitMapper;
    private final MonthlyProcurementDemandMapper monthlyDemandMapper;
    private final PurchaseRequestMapper purchaseRequestMapper;
    private final PurchaseRequestItemMapper purchaseRequestItemMapper;
    private final ProcurementApprovalRouteMapper approvalRouteMapper;
    private final ProcurementAfterSaleMapper afterSaleMapper;
    private final ProcurementOrderQueryMapper orderQueryMapper;
    private final OrderInvoiceService orderInvoiceService;
    private final ApprovalRecordMapper approvalRecordMapper;
    private final ApprovalTaskMapper approvalTaskMapper;
    private final SysUserMapper userMapper;
    private final SupplierQuoteMapper supplierQuoteMapper;
    private final SupplierQuoteItemMapper supplierQuoteItemMapper;
    private final SupplierMapper supplierMapper;

    @Override
    public WorkbenchPageVO pageRequests(WorkbenchRequestQueryDTO queryDTO) {
        AuthUser authUser = AuthContextHolder.requireUser();
        List<WorkbenchRequestVO> allRows = loadRows(authUser);
        Set<String> followedKeys = followedKeys(authUser.getUserId());
        allRows.forEach(row -> row.setFollowed(followedKeys.contains(row.getId())));

        WorkbenchPageVO result = new WorkbenchPageVO();
        result.setMetrics(metrics(allRows));

        List<WorkbenchRequestVO> filtered = allRows.stream()
            .filter(row -> matchesQuery(row, queryDTO))
            .sorted(this::sortRow)
            .toList();
        result.setPage(PageResult.of(
            queryDTO.getPageNo(),
            queryDTO.getPageSize(),
            filtered.size(),
            pageSlice(filtered, queryDTO.getPageNo(), queryDTO.getPageSize())
        ));
        return result;
    }

    @Override
    public WorkbenchRequestDetailVO detail(String sourceType, Long sourceId) {
        AuthUser authUser = AuthContextHolder.requireUser();
        String normalizedSource = normalizeSourceType(sourceType);
        ensureSourceAppAccess(authUser, normalizedSource);
        Set<String> followedKeys = followedKeys(authUser.getUserId());
        return switch (normalizedSource) {
            case SOURCE_PURCHASE -> purchaseDetail(sourceId, followedKeys);
            case SOURCE_PLATFORM -> platformDetail(sourceId, followedKeys);
            case SOURCE_MONTHLY -> monthlyDetail(sourceId, authUser, followedKeys);
            case SOURCE_SUPPLIER -> supplierDetail(sourceId, authUser, followedKeys);
            case SOURCE_AFTER_SALE -> afterSaleDetail(sourceId, authUser, followedKeys);
            default -> throw new BizException("WORKBENCH_SOURCE_INVALID", "Unsupported workbench source");
        };
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkflowSubmitResultVO approve(String sourceType, Long sourceId, WorkbenchApprovalDTO dto) {
        AuthUser authUser = AuthContextHolder.requireUser();
        String normalizedSource = normalizeSourceType(sourceType);
        ensureSourceAppAccess(authUser, normalizedSource);
        String action = normalizeCode(dto.getAction());
        if (SOURCE_PURCHASE.equals(normalizedSource)) {
            if (isReplacementConfirmPurchase(sourceId) && ("APPROVE".equals(action) || "REJECT".equals(action))) {
                if (dto.getReplacementDecisions() != null && !dto.getReplacementDecisions().isEmpty()) {
                    return workflowService.confirmReplacementItems(sourceId, dto.getReplacementDecisions(), dto.getComment());
                }
                return workflowService.confirmReplacementItems(sourceId, "APPROVE".equals(action), dto.getComment());
            }
            if ("ADD_SIGN".equals(action)) {
                Long addSignUserId = resolveAddSignUserId(dto.getAddSignUser());
                workflowService.addSignRequest(sourceId, addSignUserId, dto.getComment());
                return null;
            }
            WorkflowApprovalActionDTO workflowDTO = new WorkflowApprovalActionDTO();
            workflowDTO.setAction(action);
            workflowDTO.setApprovalComment(dto.getComment());
            workflowDTO.setTargetNodeNo(dto.getTargetNodeNo());
            workflowDTO.setItemDecisions(dto.getItemDecisions());
            WorkflowSubmitResultVO result = workflowService.approveRequest(sourceId, workflowDTO);
            return result;
        }
        if (SOURCE_PLATFORM.equals(normalizedSource)) {
            ensurePlatformApprovalPermission();
            if (!"APPROVE".equals(action) && !"REJECT".equals(action)) {
                throw new BizException("WORKBENCH_ACTION_UNSUPPORTED", "Platform permission requests only support approve or reject");
            }
            PlatformPermissionReviewDTO reviewDTO = new PlatformPermissionReviewDTO();
            reviewDTO.setAction(action);
            reviewDTO.setComment(dto.getComment());
            platformPermissionService.reviewRequest(sourceId, reviewDTO);
            return null;
        }
        if (SOURCE_AFTER_SALE.equals(normalizedSource)) {
            if (!"APPROVE".equals(action) && !"REJECT".equals(action)) {
                throw new BizException("WORKBENCH_ACTION_UNSUPPORTED", "After-sale requests only support approve or reject");
            }
            AfterSaleApprovalDTO reviewDTO = new AfterSaleApprovalDTO();
            reviewDTO.setAction(action);
            reviewDTO.setApprovalRemark(dto.getComment());
            reviewDTO.setProposalRemark(dto.getComment());
            reviewDTO.setProposalType(dto.getAfterSaleProposalType());
            reviewDTO.setReplacementItemCode(dto.getAfterSaleReplacementItemCode());
            reviewDTO.setReplacementItemName(dto.getAfterSaleReplacementItemName());
            reviewDTO.setReplacementSpecModel(dto.getAfterSaleReplacementSpecModel());
            reviewDTO.setReplacementUnit(dto.getAfterSaleReplacementUnit());
            reviewDTO.setReplacementUnitPrice(dto.getAfterSaleReplacementUnitPrice());
            reviewDTO.setReplacementLineAmount(dto.getAfterSaleReplacementLineAmount());
            procurementOrderService.approveAfterSale(sourceId, reviewDTO);
            return null;
        }
        throw new BizException("WORKBENCH_ACTION_UNSUPPORTED", "This module is not connected to approval submission yet");
    }

    private boolean isReplacementConfirmPurchase(Long sourceId) {
        if (sourceId == null) {
            return false;
        }
        PurchaseRequest request = purchaseRequestMapper.selectById(sourceId);
        return request != null
            && Objects.equals(request.getCurrentNodeNo(), ProcurementWorkflow.NODE_APPLICANT_REPLACEMENT_REVIEW)
            && ProcurementWorkflow.STATUS_PENDING_APPROVAL.equals(request.getStatus())
            && ProcurementWorkflow.APPROVAL_PENDING.equals(request.getApprovalStatus());
    }

    private void ensurePlatformApprovalPermission() {
        if (!canReviewPlatformPermission(AuthContextHolder.requireUser())) {
            throw new BizException("FORBIDDEN", "No permission to review platform permission requests");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setFollow(WorkbenchFollowDTO dto) {
        AuthUser authUser = AuthContextHolder.requireUser();
        String sourceType = normalizeSourceType(dto.getSourceType());
        ensureSourceAppAccess(authUser, sourceType);
        LambdaQueryWrapper<WorkbenchFollow> wrapper = new LambdaQueryWrapper<WorkbenchFollow>()
            .eq(WorkbenchFollow::getUserId, authUser.getUserId())
            .eq(WorkbenchFollow::getSourceType, sourceType)
            .eq(WorkbenchFollow::getSourceId, dto.getSourceId());
        if (Boolean.TRUE.equals(dto.getFollowed())) {
            if (followMapper.selectCount(wrapper) <= 0) {
                WorkbenchFollow follow = new WorkbenchFollow();
                follow.setUserId(authUser.getUserId());
                follow.setSourceType(sourceType);
                follow.setSourceId(dto.getSourceId());
                followMapper.insert(follow);
            }
        } else {
            followMapper.delete(wrapper);
        }
    }

    @Override
    public List<WorkbenchUserOptionVO> userOptions(String keyword) {
        AuthUser authUser = AuthContextHolder.requireUser();
        if (DataScopeHelper.isSupplierUser(authUser)) {
            return List.of();
        }
        String text = StringUtils.hasText(keyword) ? keyword.trim() : "";
        if (!DataScopeHelper.isSystemAdmin(authUser) && text.length() < 2) {
            return List.of();
        }
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
            .eq(SysUser::getStatus, 1)
            .ne(SysUser::getUserType, DataScopeHelper.USER_TYPE_SUPPLIER)
            .orderByAsc(SysUser::getRealName)
            .orderByAsc(SysUser::getEmployeeNo)
            .last("LIMIT 50");
        if (StringUtils.hasText(text)) {
            wrapper.and(query -> query
                .like(SysUser::getRealName, text)
                .or()
                .like(SysUser::getEmployeeNo, text)
                .or()
                .like(SysUser::getUsername, text));
        }
        return userMapper.selectList(wrapper).stream()
            .map(this::userOption)
            .toList();
    }

    private List<WorkbenchRequestVO> loadRows(AuthUser authUser) {
        Map<String, WorkbenchRequestVO> rows = new LinkedHashMap<>();
        boolean canUseProcurement = hasAppAccess(authUser, APP_CODE_PROCUREMENT);
        boolean canUsePlatform = hasAppAccess(authUser, APP_CODE_PLATFORM);
        if (!DataScopeHelper.isSupplierUser(authUser) && canUseProcurement) {
            putRows(rows, loadPurchaseApprovalRows(authUser));
            putRows(rows, loadPurchaseDoneRows(authUser));
            putRows(rows, loadPurchaseMineRows(authUser));
            putRows(rows, loadAssistantPurchaseRows(authUser));
            putRows(rows, loadAllPurchaseRowsForAdmin(authUser));
            putRows(rows, loadMonthlyRows());
        }
        if (!DataScopeHelper.isSupplierUser(authUser) && (canUseProcurement || canUsePlatform)) {
            putRows(rows, loadCopiedRows(authUser));
        }
        if (!DataScopeHelper.isSupplierUser(authUser) && canUsePlatform) {
            putRows(rows, loadPlatformRows(authUser));
        }
        if (canUseProcurement) {
            putRows(rows, loadAfterSaleRows(authUser));
        }
        if (DataScopeHelper.isSupplierUser(authUser) && canUseProcurement) {
            putRows(rows, loadSupplierRows());
        }
        return new ArrayList<>(rows.values());
    }

    private List<WorkbenchRequestVO> loadPurchaseApprovalRows(AuthUser authUser) {
        if (authUser.getUserId() == null) {
            return List.of();
        }
        return approvalTaskMapper.selectList(new LambdaQueryWrapper<ApprovalTask>()
                .eq(ApprovalTask::getCandidateUserId, authUser.getUserId())
                .eq(ApprovalTask::getStatus, APPROVAL_TASK_PENDING)
                .orderByDesc(ApprovalTask::getCreatedAt)
                .last("LIMIT 300"))
            .stream()
            .filter(task -> task.getPurchaseRequestId() != null)
            .collect(Collectors.toMap(ApprovalTask::getPurchaseRequestId, task -> task, (left, right) -> left, LinkedHashMap::new))
            .values()
            .stream()
            .map(task -> safePurchaseRow(task.getPurchaseRequestId(), "todo", true, firstDateTime(task.getCreatedAt(), task.getUpdatedAt())))
            .filter(Objects::nonNull)
            .toList();
    }

    private List<WorkbenchRequestVO> loadPurchaseDoneRows(AuthUser authUser) {
        if (authUser.getUserId() == null) {
            return List.of();
        }
        return approvalRecordMapper.selectList(new LambdaQueryWrapper<ApprovalRecord>()
                .eq(ApprovalRecord::getApproverUserId, authUser.getUserId())
                .in(ApprovalRecord::getAction, List.of(
                    "APPROVE",
                    "REJECT",
                    "RETURN",
                    "TERMINATE",
                    "ADD_SIGN",
                    "APPROVE_REPLACEMENT",
                    "REJECT_REPLACEMENT",
                    "CONFIRM_REPLACEMENT_BY_ITEM"
                ))
                .isNotNull(ApprovalRecord::getPurchaseRequestId)
                .eq(ApprovalRecord::getStatus, "ACTIVE")
                .orderByDesc(ApprovalRecord::getOperatedAt)
                .last("LIMIT 300"))
            .stream()
            .collect(Collectors.toMap(ApprovalRecord::getPurchaseRequestId, record -> record, (left, right) -> left, LinkedHashMap::new))
            .values()
            .stream()
            .map(record -> safePurchaseRow(record.getPurchaseRequestId(), "done", false, record.getOperatedAt()))
            .filter(Objects::nonNull)
            .toList();
    }

    private List<WorkbenchRequestVO> loadPurchaseMineRows(AuthUser authUser) {
        if (authUser.getUserId() == null) {
            return List.of();
        }
        return purchaseRequestMapper.selectList(new LambdaQueryWrapper<PurchaseRequest>()
                .eq(PurchaseRequest::getApplicantUserId, authUser.getUserId())
                .orderByDesc(PurchaseRequest::getUpdatedAt)
                .orderByDesc(PurchaseRequest::getCreatedAt)
                .last("LIMIT 200"))
            .stream()
            .map(request -> safePurchaseRow(request.getId(), "mine", false, null))
            .filter(Objects::nonNull)
            .toList();
    }

    private List<WorkbenchRequestVO> loadCopiedRows(AuthUser authUser) {
        if (authUser.getUserId() == null) {
            return List.of();
        }
        return copyMapper.selectList(new LambdaQueryWrapper<WorkbenchCopy>()
                .eq(WorkbenchCopy::getUserId, authUser.getUserId())
            .orderByDesc(WorkbenchCopy::getCopiedAt)
            .last("LIMIT 200"))
            .stream()
            .filter(copy -> hasSourceAppAccess(authUser, copy.getSourceType()))
            .map(this::copyRow)
            .filter(Objects::nonNull)
            .toList();
    }

    private List<WorkbenchRequestVO> loadMonthlyRows() {
        MonthlyDemandQueryDTO query = new MonthlyDemandQueryDTO();
        query.setPageNo(1);
        query.setPageSize(100);
        return monthlyProcurementService.pageMyDemands(query).getRecords().stream()
            .map(this::monthlyRow)
            .toList();
    }

    private List<WorkbenchRequestVO> loadPlatformRows(AuthUser authUser) {
        List<WorkbenchRequestVO> rows = new ArrayList<>();
        platformPermissionService.myAccess().getRecentRequests()
            .forEach(request -> rows.add(platformRow(request, donePlatform(request.getStatus()) ? "done" : "mine", false)));
        if (canReviewPlatformPermission(authUser)) {
            PlatformPermissionRequestQueryDTO query = new PlatformPermissionRequestQueryDTO();
            query.setPageNo(1);
            query.setPageSize(100);
            query.setStatus("PENDING");
            platformPermissionService.requestPage(query).getRecords()
                .forEach(request -> rows.add(platformRow(request, "todo", true)));
        }
        return rows;
    }

    private List<WorkbenchRequestVO> loadAfterSaleRows(AuthUser authUser) {
        if (authUser.getUserId() == null) {
            return List.of();
        }
        return afterSaleMapper.selectList(new LambdaQueryWrapper<ProcurementAfterSale>()
                .orderByDesc(ProcurementAfterSale::getCreatedAt)
                .orderByDesc(ProcurementAfterSale::getId)
                .last("LIMIT 300"))
            .stream()
            .map(sale -> afterSaleVisibleRow(sale, authUser))
            .filter(Objects::nonNull)
            .toList();
    }

    private List<WorkbenchRequestVO> loadAssistantPurchaseRows(AuthUser authUser) {
        Set<String> visibleDeptNames = assistantVisibleDeptNames(authUser);
        Set<String> visibleDeptCodes = assistantVisibleDeptCodes(authUser);
        if (visibleDeptNames.isEmpty() && visibleDeptCodes.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<PurchaseRequest> wrapper = new LambdaQueryWrapper<PurchaseRequest>()
            .orderByDesc(PurchaseRequest::getUpdatedAt)
            .orderByDesc(PurchaseRequest::getSubmittedAt)
            .orderByDesc(PurchaseRequest::getCreatedAt)
            .last("LIMIT 300");
        wrapper.and(query -> {
            boolean hasCondition = false;
            if (!visibleDeptNames.isEmpty()) {
                query.in(PurchaseRequest::getDeptName, visibleDeptNames);
                hasCondition = true;
            }
            if (!visibleDeptCodes.isEmpty()) {
                if (hasCondition) {
                    query.or();
                }
                query.in(PurchaseRequest::getDeptCode, visibleDeptCodes);
            }
        });
        return purchaseRequestMapper.selectList(wrapper).stream()
            .map(request -> assistantPurchaseRow(request, authUser))
            .filter(Objects::nonNull)
            .toList();
    }

    private List<WorkbenchRequestVO> loadAllPurchaseRowsForAdmin(AuthUser authUser) {
        if (!DataScopeHelper.isSystemAdmin(authUser)) {
            return List.of();
        }
        return purchaseRequestMapper.selectList(new LambdaQueryWrapper<PurchaseRequest>()
                .orderByDesc(PurchaseRequest::getUpdatedAt)
                .orderByDesc(PurchaseRequest::getSubmittedAt)
                .orderByDesc(PurchaseRequest::getCreatedAt)
                .orderByDesc(PurchaseRequest::getId))
            .stream()
            .map(request -> safePurchaseRow(
                request.getId(),
                adminPurchaseRelation(request, authUser),
                isActivePurchaseApproval(request),
                firstDateTime(request.getUpdatedAt(), request.getSubmittedAt(), request.getCreatedAt())
            ))
            .filter(Objects::nonNull)
            .toList();
    }

    private List<WorkbenchRequestVO> loadSupplierRows() {
        SupplierQuotePageQueryDTO query = new SupplierQuotePageQueryDTO();
        query.setPageNo(1);
        query.setPageSize(100);
        return supplierQuoteService.pageQuoteRequests(query).getRecords().stream()
            .map(this::supplierRow)
            .toList();
    }

    private void putRows(Map<String, WorkbenchRequestVO> rows, List<WorkbenchRequestVO> nextRows) {
        for (WorkbenchRequestVO row : nextRows) {
            WorkbenchRequestVO existing = rows.get(row.getId());
            if (existing == null || relationPriority(row.getRelation()) < relationPriority(existing.getRelation()) || Boolean.TRUE.equals(row.getActionable())) {
                rows.put(row.getId(), row);
            }
        }
    }

    private WorkbenchRequestDetailVO purchaseDetail(Long sourceId, Set<String> followedKeys) {
        AuthUser authUser = AuthContextHolder.requireUser();
        boolean copied = isCopiedToUser(SOURCE_PURCHASE, sourceId, authUser.getUserId());
        boolean canApprove = canApprovePurchase(sourceId, authUser);
        boolean handled = hasHandledPurchase(sourceId, authUser.getUserId());
        boolean assistantVisible = false;
        PurchaseRequestVO detail;
        try {
            detail = workflowService.detailVisibleRequest(sourceId);
        } catch (BizException ex) {
            assistantVisible = canAssistantViewPurchase(sourceId, authUser);
            if (!copied && !canApprove && !handled && !assistantVisible) {
                throw ex;
            }
            detail = purchaseRequestService.detail(sourceId);
        }
        List<ApprovalRecordVO> records = safeApprovalRecords(sourceId);
        if (!canViewPurchaseInWorkbench(detail, authUser, copied, canApprove, handled, assistantVisible)) {
            throw new BizException("FORBIDDEN", "Current account cannot access this workbench request");
        }
        WorkbenchRequestVO summary = purchaseRow(detail, canApprove ? "todo" : handled ? "done" : copied ? "related" : assistantVisible ? "department" : "mine", canApprove);
        if (assistantVisible && !canApprove && !handled && !copied) {
            summary.setViewerRole("助理");
        }
        summary.setFollowed(followedKeys.contains(summary.getId()));

        WorkbenchRequestDetailVO vo = new WorkbenchRequestDetailVO();
        vo.setSummary(summary);
        vo.setApprovalRecords(records);
        vo.setFlowNodes(purchaseFlow(detail, records));
        vo.setDetailRows(isReplacementConfirmNode(detail)
            ? replacementQuoteDetailRows(detail)
            : purchaseApprovalDetailRows(detail));
        vo.setCurrentApprovalDueAt(currentPurchaseApprovalDueAt(sourceId, authUser));
        return vo;
    }

    private WorkbenchRequestDetailVO platformDetail(Long sourceId, Set<String> followedKeys) {
        AuthUser authUser = AuthContextHolder.requireUser();
        PlatformPermissionRequest request = platformRequestMapper.selectById(sourceId);
        if (request == null) {
            throw new BizException("REQUEST_NOT_FOUND", "Permission request not found");
        }
        boolean copied = isCopiedToUser(SOURCE_PLATFORM, sourceId, authUser.getUserId());
        boolean applicant = Objects.equals(request.getApplicantUserId(), authUser.getUserId());
        boolean handled = Objects.equals(request.getReviewerUserId(), authUser.getUserId());
        boolean canReview = "PENDING".equals(request.getStatus()) && canReviewPlatformPermission(authUser);
        if (!copied && !applicant && !handled && !canReview) {
            throw new BizException("FORBIDDEN", "Current account cannot access this workbench request");
        }
        String relation = canReview ? "todo" : handled ? "done" : copied ? "related" : "mine";
        WorkbenchRequestVO summary = platformRow(toPlatformVO(request), relation, canReview);
        summary.setFollowed(followedKeys.contains(summary.getId()));

        WorkbenchRequestDetailVO vo = new WorkbenchRequestDetailVO();
        vo.setSummary(summary);
        vo.setFlowNodes(platformFlow(request));
        vo.setDetailRows(List.of(platformDetailRow(request)));
        return vo;
    }

    private WorkbenchRequestDetailVO monthlyDetail(Long sourceId, AuthUser authUser, Set<String> followedKeys) {
        MonthlyProcurementDemand demand = monthlyDemandMapper.selectById(sourceId);
        if (demand == null) {
            throw new BizException("DEMAND_NOT_FOUND", "Monthly demand not found");
        }
        if (!Objects.equals(authUser.getUserId(), demand.getApplicantUserId())) {
            throw new BizException("FORBIDDEN", "Current account cannot access this demand");
        }
        WorkbenchRequestVO summary = monthlyRow(toMonthlyVO(demand));
        summary.setFollowed(followedKeys.contains(summary.getId()));

        WorkbenchRequestDetailVO vo = new WorkbenchRequestDetailVO();
        vo.setSummary(summary);
        vo.setFlowNodes(monthlyFlow(demand));
        vo.setDetailRows(List.of(monthlyDetailRow(demand)));
        return vo;
    }

    private WorkbenchRequestDetailVO supplierDetail(Long sourceId, AuthUser authUser, Set<String> followedKeys) {
        PurchaseRequestVO detail = supplierQuoteService.quoteRequestDetail(sourceId);
        boolean supplierUser = DataScopeHelper.isSupplierUser(authUser);
        List<ApprovalRecordVO> records = supplierUser ? List.of() : safeApprovalRecords(sourceId);
        WorkbenchRequestVO summary = purchaseRow(detail, "related", false);
        summary.setId(sourceKey(SOURCE_SUPPLIER, sourceId));
        summary.setSourceType(SOURCE_SUPPLIER);
        summary.setModule("SUPPLIER");
        summary.setModuleName("供应商");
        summary.setDetailKind("supplier");
        summary.setCurrentNode("供应商报价");
        if (supplierUser) {
            summary.setApplicant("-");
            summary.setAmount(null);
            summary.setAmountText("-");
        }
        summary.setFollowed(followedKeys.contains(summary.getId()));

        WorkbenchRequestDetailVO vo = new WorkbenchRequestDetailVO();
        vo.setSummary(summary);
        vo.setApprovalRecords(records);
        vo.setFlowNodes(supplierUser ? List.of() : purchaseFlow(detail, records));
        PurchaseRequestVO detailForRows = detail;
        vo.setDetailRows(detail.getItems() == null ? List.of() : detail.getItems().stream().map(item -> purchaseDetailRow(detailForRows, item)).toList());
        return vo;
    }

    private WorkbenchRequestDetailVO afterSaleDetail(Long sourceId, AuthUser authUser, Set<String> followedKeys) {
        ProcurementAfterSale sale = afterSaleMapper.selectById(sourceId);
        if (sale == null) {
            throw new BizException("AFTER_SALE_NOT_FOUND", "After-sale request not found");
        }
        ProcurementOrderVO order = orderQueryMapper.selectOrderRowByQuoteItemId(sale.getQuoteItemId());
        WorkbenchRequestVO summary = afterSaleVisibleRow(sale, authUser);
        if (summary == null) {
            throw new BizException("FORBIDDEN", "Current account cannot access this after-sale request");
        }
        summary.setFollowed(followedKeys.contains(summary.getId()));

        WorkbenchRequestDetailVO vo = new WorkbenchRequestDetailVO();
        vo.setSummary(summary);
        vo.setFlowNodes(afterSaleFlow(sale, order));
        vo.setDetailRows(List.of(afterSaleDetailRow(sale, order)));
        return vo;
    }

    private List<ApprovalRecordVO> safeApprovalRecords(Long sourceId) {
        try {
            return workflowService.listApprovalRecords(sourceId);
        } catch (BizException ex) {
            return List.of();
        }
    }

    private WorkbenchRequestVO safePurchaseRow(Long sourceId, String relation, boolean actionable, LocalDateTime arrivedAt) {
        if (sourceId == null) {
            return null;
        }
        try {
            WorkbenchRequestVO row = purchaseRow(workflowService.detailVisibleRequest(sourceId), relation, actionable);
            if (arrivedAt != null) {
                row.setArrivedAt(arrivedAt);
            }
            return row;
        } catch (BizException ex) {
            return null;
        }
    }

    private boolean canApprovePurchase(Long sourceId, AuthUser authUser) {
        if (sourceId == null || authUser == null || authUser.getUserId() == null) {
            return false;
        }
        if (DataScopeHelper.isSystemAdmin(authUser)) {
            PurchaseRequest request = purchaseRequestMapper.selectById(sourceId);
            return isActivePurchaseApproval(request);
        }
        return approvalTaskMapper.selectCount(new LambdaQueryWrapper<ApprovalTask>()
            .eq(ApprovalTask::getPurchaseRequestId, sourceId)
            .eq(ApprovalTask::getCandidateUserId, authUser.getUserId())
            .eq(ApprovalTask::getStatus, APPROVAL_TASK_PENDING)) > 0;
    }

    private LocalDateTime currentPurchaseApprovalDueAt(Long sourceId, AuthUser authUser) {
        if (sourceId == null || authUser == null || authUser.getUserId() == null) {
            return null;
        }
        ApprovalTask task = approvalTaskMapper.selectOne(new LambdaQueryWrapper<ApprovalTask>()
            .eq(ApprovalTask::getPurchaseRequestId, sourceId)
            .eq(ApprovalTask::getCandidateUserId, authUser.getUserId())
            .eq(ApprovalTask::getStatus, APPROVAL_TASK_PENDING)
            .isNotNull(ApprovalTask::getDueAt)
            .orderByAsc(ApprovalTask::getDueAt)
            .last("LIMIT 1"));
        return task == null ? null : task.getDueAt();
    }

    private boolean hasHandledPurchase(Long sourceId, Long userId) {
        if (sourceId == null || userId == null) {
            return false;
        }
        return approvalRecordMapper.selectCount(new LambdaQueryWrapper<ApprovalRecord>()
            .eq(ApprovalRecord::getPurchaseRequestId, sourceId)
            .eq(ApprovalRecord::getApproverUserId, userId)
            .eq(ApprovalRecord::getStatus, "ACTIVE")
            .in(ApprovalRecord::getAction, List.of("APPROVE", "REJECT", "RETURN", "TERMINATE", "ADD_SIGN"))) > 0;
    }

    private boolean canViewPurchaseInWorkbench(PurchaseRequestVO request,
                                               AuthUser authUser,
                                               boolean copied,
                                               boolean canApprove,
                                               boolean handled,
                                               boolean assistantVisible) {
        return canApprove
            || handled
            || copied
            || assistantVisible
            || DataScopeHelper.isSystemAdmin(authUser)
            || Objects.equals(request.getApplicantUserId(), authUser.getUserId());
    }

    private String adminPurchaseRelation(PurchaseRequest request, AuthUser authUser) {
        if (isActivePurchaseApproval(request)) {
            return "todo";
        }
        if (request != null && Objects.equals(request.getApplicantUserId(), authUser.getUserId())) {
            return "mine";
        }
        return "done";
    }

    private boolean canTerminatePurchase(PurchaseRequestVO request, AuthUser authUser) {
        return request != null
            && authUser != null
            && Objects.equals(request.getApplicantUserId(), authUser.getUserId())
            && "PENDING_APPROVAL".equals(normalizeCode(request.getStatus()))
            && "PENDING".equals(normalizeCode(request.getApprovalStatus()))
            && (request.getCurrentNodeNo() == null || request.getCurrentNodeNo() <= 0);
    }

    private WorkbenchRequestVO purchaseRow(PurchaseRequestVO request, String relation, boolean actionable) {
        WorkbenchRequestVO row = baseRow(SOURCE_PURCHASE, request.getId());
        row.setModule("PROCUREMENT");
        row.setModuleName("采购管理");
        row.setType(requestTypeText(request.getReqType()));
        row.setRelation(relation);
        row.setDetailKind("purchase");
        row.setReqNo(request.getReqNo());
        row.setTitle(request.getReqTitle());
        row.setApplicant(purchaseApplicantName(request));
        row.setCurrentNode(purchaseCurrentNode(request));
        row.setCurrentApproverNames(isActivePurchaseApproval(request) ? currentApproverNames(request, AuthContextHolder.get()) : "");
        row.setCurrentNodeNo(request.getCurrentNodeNo());
        row.setStatus(firstText(request.getApprovalStatus(), request.getStatus()));
        row.setStatusText(purchaseStatusText(request, row.getStatus(), relation, actionable));
        row.setArrivedAt(firstDateTime(request.getSubmittedAt(), request.getUpdatedAt(), request.getCreatedAt()));
        row.setAmount(request.getTotalAmount());
        row.setAmountText(formatMoney(request.getTotalAmount()));
        row.setReferenceAmount(request.getReferenceTotalAmount());
        row.setReferenceAmountText(formatMoney(request.getReferenceTotalAmount()));
        row.setPriceSource(request.getPriceSource());
        row.setDeptName(request.getDeptName());
        row.setActionable(actionable);
        row.setTerminable(canTerminatePurchase(request, AuthContextHolder.get()));
        row.setReturnable(!isSystemTriggeredAggregate(request));
        return row;
    }

    private String purchaseApplicantName(PurchaseRequestVO request) {
        if (isSystemTriggeredAggregate(request)) {
            return SYSTEM_ADMIN_NAME;
        }
        return firstText(request.getApplicantName(), text(request.getApplicantUserId()));
    }

    private boolean isSystemTriggeredAggregate(PurchaseRequestVO request) {
        if (request == null) {
            return false;
        }
        if (NATIONAL_DEMAND_AGGREGATE_REASON.equals(request.getReqReason())) {
            return true;
        }
        return request.getItems() != null && request.getItems().stream()
            .anyMatch(item -> item != null && item.getSourceRequestId() != null);
    }

    private WorkbenchRequestVO platformRow(PlatformPermissionRequestVO request, String relation, boolean actionable) {
        WorkbenchRequestVO row = baseRow(SOURCE_PLATFORM, request.getId());
        row.setModule("PLATFORM");
        row.setModuleName("平台管理");
        row.setType("权限申请");
        row.setRelation(relation);
        row.setDetailKind("platform");
        row.setReqNo("PERM-" + request.getId());
        row.setTitle(firstText(request.getAppName(), request.getAppCode()) + " " + firstText(request.getRoleCode(), ""));
        row.setApplicant(firstText(request.getApplicantName(), request.getApplicantEmail(), text(request.getApplicantUserId())));
        row.setCurrentNode("PENDING".equals(request.getStatus()) ? "管理员审核" : "权限生效");
        row.setCurrentNodeNo("PENDING".equals(request.getStatus()) ? 1 : 2);
        row.setStatus(request.getStatus());
        row.setStatusText(platformStatusText(request.getStatus()));
        row.setArrivedAt(firstDateTime(request.getReviewedAt(), request.getCreatedAt()));
        row.setDeptName(request.getUnitName());
        row.setActionable(actionable);
        return row;
    }

    private WorkbenchRequestVO monthlyRow(MonthlyDemandVO demand) {
        WorkbenchRequestVO row = baseRow(SOURCE_MONTHLY, demand.getId());
        row.setModule("PROCUREMENT");
        row.setModuleName("采购管理");
        row.setType("月度需求");
        row.setRelation("mine");
        row.setDetailKind("monthly");
        row.setReqNo(demand.getDemandNo());
        row.setTitle(demand.getReqTitle());
        row.setApplicant(currentUserName());
        row.setCurrentNode(demand.getPurchaseRequestId() == null ? "需求池待汇总" : "已生成采购申请");
        row.setCurrentNodeNo(demand.getPurchaseRequestId() == null ? 1 : 2);
        row.setStatus(demand.getStatus());
        row.setStatusText(genericStatusText(demand.getStatus()));
        row.setArrivedAt(demand.getCreatedAt());
        row.setAmount(demand.getLineAmount());
        row.setAmountText(formatMoney(demand.getLineAmount()));
        row.setDeptName(demand.getDeptName());
        row.setActionable(false);
        return row;
    }

    private WorkbenchRequestVO supplierRow(SupplierQuoteRequestVO request) {
        WorkbenchRequestVO row = baseRow(SOURCE_SUPPLIER, request.getId());
        row.setModule("SUPPLIER");
        row.setModuleName("供应商管理");
        row.setType("询价/报价");
        row.setDetailKind("supplier");
        row.setReqNo(request.getReqNo());
        row.setTitle(request.getReqTitle());
        row.setApplicant("-");
        boolean awardConfirmed = Boolean.TRUE.equals(request.getMyAwarded())
            && ProcurementWorkflow.SUPPLIER_CONFIRM_STATUS_CONFIRMED.equals(request.getMySupplierConfirmStatus());
        boolean awardPending = Boolean.TRUE.equals(request.getMyAwarded())
            && ProcurementWorkflow.SUPPLIER_CONFIRM_STATUS_PENDING.equals(request.getMySupplierConfirmStatus());
        boolean awardWaived = ProcurementWorkflow.SUPPLIER_CONFIRM_STATUS_WAIVED.equals(request.getMySupplierConfirmStatus());
        row.setRelation(awardPending || !StringUtils.hasText(request.getMyQuoteStatus()) ? "todo" : "done");
        row.setCurrentNode(awardPending ? "中标待确认" : awardConfirmed ? "供应商中标" : StringUtils.hasText(request.getMyQuoteStatus()) ? "比价/定标" : "供应商报价");
        row.setCurrentNodeNo(2);
        row.setStatus(awardPending ? "AWARD_PENDING" : awardConfirmed ? "AWARD_CONFIRMED" : awardWaived ? "AWARD_WAIVED" : firstText(request.getMyQuoteStatus(), request.getStatus()));
        row.setStatusText(supplierStatusText(row.getStatus()));
        row.setArrivedAt(awardPending && request.getMySupplierConfirmDueAt() != null
            ? request.getMySupplierConfirmDueAt()
            : request.getMySubmittedAt() == null ? dateStart(request.getNeedDate()) : request.getMySubmittedAt());
        row.setAmount(request.getRequestAmount());
        row.setAmountText(formatMoney(request.getRequestAmount()));
        row.setDeptName(request.getDeptName());
        row.setActionable(awardPending);
        return row;
    }

    private WorkbenchRequestVO afterSaleVisibleRow(ProcurementAfterSale sale, AuthUser authUser) {
        if (sale == null) {
            return null;
        }
        ProcurementOrderVO order = orderQueryMapper.selectOrderRowByQuoteItemId(sale.getQuoteItemId());
        String status = normalizeCode(sale.getStatus());
        boolean canSupplierPropose = AFTER_SALE_STATUS_PENDING.equals(status)
            && canApproveAfterSale(authUser, order);
        boolean canRequesterConfirm = AFTER_SALE_STATUS_PROPOSAL_PENDING.equals(status)
            && canConfirmAfterSaleProposal(authUser, sale, order);
        boolean canApprove = canSupplierPropose || canRequesterConfirm;
        boolean requester = Objects.equals(sale.getRequesterUserId(), authUser.getUserId());
        boolean handled = Objects.equals(sale.getApproverUserId(), authUser.getUserId());
        boolean requesterHandled = Objects.equals(sale.getRequesterConfirmUserId(), authUser.getUserId());
        boolean adminVisible = DataScopeHelper.isSystemAdmin(authUser);
        if (!canApprove && !requester && !handled && !requesterHandled && !adminVisible) {
            return null;
        }
        String relation = canApprove ? "todo" : handled || requesterHandled || adminVisible ? "done" : "mine";
        return afterSaleRow(sale, order, relation, canApprove);
    }

    private WorkbenchRequestVO afterSaleRow(ProcurementAfterSale sale, ProcurementOrderVO order, String relation, boolean actionable) {
        WorkbenchRequestVO row = baseRow(SOURCE_AFTER_SALE, sale.getId());
        row.setModule("PROCUREMENT");
        row.setModuleName("采购管理");
        row.setType(afterSaleTypeText(sale.getAfterSaleType()));
        row.setRelation(relation);
        row.setDetailKind("afterSale");
        row.setReqNo(sale.getAfterSaleNo());
        row.setTitle(firstText(
            order == null ? "" : order.getItemName(),
            order == null ? "" : order.getReqTitle(),
            "售后申请"
        ));
        row.setApplicant(afterSaleRequesterName(sale));
        String status = normalizeCode(sale.getStatus());
        row.setCurrentNode(afterSaleCurrentNode(status));
        row.setCurrentApproverNames(afterSaleCurrentApproverNames(sale, order, status));
        row.setCurrentNodeNo(afterSaleCurrentNodeNo(status));
        row.setStatus(sale.getStatus());
        row.setStatusText(afterSaleStatusText(sale.getStatus()));
        row.setArrivedAt(firstDateTime(sale.getRequesterConfirmedAt(), sale.getSupplierProposedAt(), sale.getApprovedAt(), sale.getCreatedAt(), sale.getUpdatedAt()));
        row.setAmount(order == null ? null : order.getLineAmount());
        row.setAmountText(formatMoney(order == null ? null : order.getLineAmount()));
        row.setDeptName(order == null ? "" : order.getDeptName());
        row.setActionable(actionable);
        return row;
    }

    private WorkbenchRequestVO copyRow(WorkbenchCopy copy) {
        if (copy == null || copy.getSourceId() == null || !StringUtils.hasText(copy.getSourceType())) {
            return null;
        }
        try {
            String sourceType = normalizeSourceType(copy.getSourceType());
            if (SOURCE_PURCHASE.equals(sourceType)) {
                WorkbenchRequestVO row = purchaseRow(purchaseRequestService.detail(copy.getSourceId()), "related", false);
                row.setArrivedAt(firstDateTime(copy.getCopiedAt(), row.getArrivedAt()));
                return row;
            }
            if (SOURCE_PLATFORM.equals(sourceType)) {
                PlatformPermissionRequest request = platformRequestMapper.selectById(copy.getSourceId());
                if (request == null) {
                    return null;
                }
                WorkbenchRequestVO row = platformRow(toPlatformVO(request), "related", false);
                row.setArrivedAt(firstDateTime(copy.getCopiedAt(), row.getArrivedAt()));
                return row;
            }
            return null;
        } catch (BizException ex) {
            return null;
        }
    }

    private WorkbenchRequestVO assistantPurchaseRow(PurchaseRequest request, AuthUser authUser) {
        if (request == null || request.getId() == null) {
            return null;
        }
        if (Objects.equals(request.getApplicantUserId(), authUser.getUserId())
            || hasHandledPurchase(request.getId(), authUser.getUserId())
            || canApprovePurchase(request.getId(), authUser)
            || isCopiedToUser(SOURCE_PURCHASE, request.getId(), authUser.getUserId())) {
            return null;
        }
        try {
            WorkbenchRequestVO row = purchaseRow(purchaseRequestService.detail(request.getId()), "department", false);
            row.setViewerRole("助理");
            row.setArrivedAt(firstDateTime(request.getUpdatedAt(), request.getSubmittedAt(), request.getCreatedAt()));
            return row;
        } catch (BizException ex) {
            return null;
        }
    }

    private boolean canAssistantViewPurchase(Long sourceId, AuthUser authUser) {
        if (sourceId == null || authUser == null || authUser.getUserId() == null) {
            return false;
        }
        PurchaseRequest request = purchaseRequestMapper.selectById(sourceId);
        if (request == null) {
            return false;
        }
        return canAssistantViewPurchase(request, authUser);
    }

    private boolean canAssistantViewPurchase(PurchaseRequest request, AuthUser authUser) {
        if (request == null || authUser == null || authUser.getUserId() == null) {
            return false;
        }
        Set<String> visibleDeptNames = assistantVisibleDeptNames(authUser);
        Set<String> visibleDeptCodes = assistantVisibleDeptCodes(authUser);
        return (StringUtils.hasText(request.getDeptName()) && visibleDeptNames.contains(request.getDeptName().trim()))
            || (StringUtils.hasText(request.getDeptCode()) && visibleDeptCodes.contains(request.getDeptCode().trim()));
    }

    private Set<String> assistantVisibleDeptNames(AuthUser authUser) {
        return assistantRoutes(authUser).stream()
            .map(ProcurementApprovalRoute::getDeptName)
            .filter(StringUtils::hasText)
            .map(String::trim)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<String> assistantVisibleDeptCodes(AuthUser authUser) {
        return assistantRoutes(authUser).stream()
            .flatMap(route -> List.of(route.getDeptCode(), route.getCostCenterCode()).stream())
            .filter(StringUtils::hasText)
            .map(String::trim)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private List<ProcurementApprovalRoute> assistantRoutes(AuthUser authUser) {
        if (authUser == null || authUser.getUserId() == null || DataScopeHelper.isSystemAdmin(authUser)) {
            return List.of();
        }
        return approvalRouteMapper.selectList(new LambdaQueryWrapper<ProcurementApprovalRoute>()
                .eq(ProcurementApprovalRoute::getEnabled, 1)
                .isNotNull(ProcurementApprovalRoute::getAssistantUserIds))
            .stream()
            .filter(route -> parseIdSet(route.getAssistantUserIds()).contains(authUser.getUserId()))
            .toList();
    }

    private Set<Long> parseIdSet(String text) {
        if (!StringUtils.hasText(text)) {
            return Set.of();
        }
        Set<Long> ids = new LinkedHashSet<>();
        for (String part : text.split("[,，\\s]+")) {
            try {
                ids.add(Long.parseLong(part.trim()));
            } catch (NumberFormatException ignored) {
                // Ignore stale names in legacy data.
            }
        }
        return ids;
    }

    private WorkbenchRequestVO baseRow(String sourceType, Long sourceId) {
        WorkbenchRequestVO row = new WorkbenchRequestVO();
        row.setId(sourceKey(sourceType, sourceId));
        row.setSourceType(sourceType);
        row.setSourceId(sourceId);
        row.setTerminable(false);
        row.setReturnable(true);
        row.setFollowed(false);
        return row;
    }

    private WorkbenchDetailRowVO purchaseDetailRow(PurchaseRequestVO request, PurchaseRequestItemVO item) {
        WorkbenchDetailRowVO row = new WorkbenchDetailRowVO();
        row.setItemId(item.getId());
        row.setLineNo(item.getLineNo());
        row.setName(firstText(item.getItemName(), item.getItemCode()));
        PurchaseRequest sourceRequest = sourceRequest(item);
        String roomName = sourceRequestRoomName(sourceRequest, request);
        row.setRoomName(roomName);
        row.setDeptName(roomName);
        row.setSpec(firstText(item.getSpecModel(), item.getItemBrand()));
        row.setQty(item.getQty());
        row.setUnit(item.getUnit());
        row.setAmount(item.getLineAmount());
        row.setAmountText(formatMoney(item.getLineAmount()));
        row.setReferenceAmount(item.getReferenceLineAmount());
        row.setReferenceAmountText(formatMoney(item.getReferenceLineAmount()));
        row.setAwardedUnitPrice(item.getAwardedUnitPrice());
        row.setOverPriceLineDifference(item.getOverPriceLineDifference());
        row.setOverPriceRatePercent(item.getOverPriceRatePercent());
        row.setOverPriceReviewRequired(item.getOverPriceReviewRequired());
        row.setSourceRequestNo(item.getSourceRequestNo());
        row.setSourceApplicantName(item.getSourceApplicantName());
        row.setSourceApplicantDeptName(item.getSourceApplicantDeptName());
        row.setStatus(item.getStatus());
        row.setStatusText(genericStatusText(item.getStatus()));
        row.setPurchaseReason(firstText(item.getPurpose(), "-"));
        row.setRejectReason(ProcurementWorkflow.ITEM_STATUS_REJECTED.equals(item.getStatus()) ? item.getRemark() : null);
        row.setRemark(ProcurementWorkflow.ITEM_STATUS_REJECTED.equals(item.getStatus())
            ? firstText(item.getRemark(), "-")
            : firstText(item.getPurpose(), "-"));
        return row;
    }

    private List<WorkbenchDetailRowVO> purchaseApprovalDetailRows(PurchaseRequestVO request) {
        if (request == null || request.getItems() == null) {
            return List.of();
        }
        Map<Long, String> supplierNameByItemId = awardedSupplierNameByItemId(request);
        return request.getItems().stream()
            .map(item -> {
                WorkbenchDetailRowVO row = purchaseDetailRow(request, item);
                row.setSupplierName(StringUtils.hasText(item.getAwardedSupplierName())
                    ? item.getAwardedSupplierName().trim()
                    : supplierNameByItemId.get(item.getId()));
                return row;
            })
            .toList();
    }

    private Map<Long, String> awardedSupplierNameByItemId(PurchaseRequestVO request) {
        if (request.getId() == null
            || !ProcurementWorkflow.PRICE_SOURCE_SUPPLIER_QUOTE.equals(request.getPriceSource())) {
            return Map.of();
        }
        List<SupplierQuoteItem> awardedItems = supplierQuoteItemMapper.selectList(
            new LambdaQueryWrapper<SupplierQuoteItem>()
                .eq(SupplierQuoteItem::getPurchaseRequestId, request.getId())
                .eq(SupplierQuoteItem::getAwarded, 1)
                .eq(SupplierQuoteItem::getStatus, "ACTIVE")
        );
        if (awardedItems.isEmpty()) {
            return Map.of();
        }
        List<Long> quoteIds = awardedItems.stream()
            .map(SupplierQuoteItem::getQuoteId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        Map<Long, SupplierQuote> quoteById = quoteIds.isEmpty()
            ? Map.of()
            : supplierQuoteMapper.selectBatchIds(quoteIds).stream()
                .filter(quote -> ProcurementWorkflow.QUOTE_STATUS_SUBMITTED.equals(quote.getStatus()))
                .collect(Collectors.toMap(SupplierQuote::getId, quote -> quote, (left, right) -> left, LinkedHashMap::new));
        List<Long> supplierIds = quoteById.values().stream()
            .map(SupplierQuote::getSupplierId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        Map<Long, Supplier> supplierById = supplierIds.isEmpty()
            ? Map.of()
            : supplierMapper.selectBatchIds(supplierIds).stream()
                .collect(Collectors.toMap(Supplier::getId, supplier -> supplier, (left, right) -> left, LinkedHashMap::new));

        Map<Long, String> result = new LinkedHashMap<>();
        for (SupplierQuoteItem item : awardedItems) {
            SupplierQuote quote = quoteById.get(item.getQuoteId());
            Supplier supplier = quote == null ? null : supplierById.get(quote.getSupplierId());
            if (item.getPurchaseRequestItemId() != null && supplier != null) {
                result.putIfAbsent(
                    item.getPurchaseRequestItemId(),
                    firstText(supplier.getSupplierShortName(), supplier.getSupplierName(), "-")
                );
            }
        }
        return result;
    }

    private boolean isReplacementConfirmNode(PurchaseRequestVO request) {
        return request != null
            && Objects.equals(request.getCurrentNodeNo(), ProcurementWorkflow.NODE_APPLICANT_REPLACEMENT_REVIEW);
    }

    private List<WorkbenchDetailRowVO> replacementQuoteDetailRows(PurchaseRequestVO request) {
        if (request == null || request.getId() == null) {
            return List.of();
        }
        List<SupplierQuoteItem> quoteItems = supplierQuoteItemMapper.selectList(new LambdaQueryWrapper<SupplierQuoteItem>()
            .eq(SupplierQuoteItem::getPurchaseRequestId, request.getId())
            .eq(SupplierQuoteItem::getReplacementFlag, 1)
            .eq(SupplierQuoteItem::getStatus, "ACTIVE")
            .orderByAsc(SupplierQuoteItem::getLineNo)
            .orderByDesc(SupplierQuoteItem::getAwarded)
            .orderByAsc(SupplierQuoteItem::getId));
        if (quoteItems.isEmpty()) {
            return List.of();
        }
        List<Long> quoteIds = quoteItems.stream()
            .map(SupplierQuoteItem::getQuoteId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        if (quoteIds.isEmpty()) {
            return List.of();
        }
        Map<Long, SupplierQuote> quoteById = supplierQuoteMapper.selectBatchIds(quoteIds)
            .stream()
            .filter(quote -> ProcurementWorkflow.QUOTE_STATUS_SUBMITTED.equals(quote.getStatus()))
            .collect(Collectors.toMap(SupplierQuote::getId, quote -> quote, (left, right) -> left, LinkedHashMap::new));
        if (quoteById.isEmpty()) {
            return List.of();
        }
        List<Long> supplierIds = quoteById.values().stream()
            .map(SupplierQuote::getSupplierId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        Map<Long, Supplier> supplierById = supplierIds.isEmpty()
            ? Map.of()
            : supplierMapper.selectBatchIds(supplierIds)
                .stream()
                .collect(Collectors.toMap(Supplier::getId, supplier -> supplier, (left, right) -> left, LinkedHashMap::new));
        Map<Long, PurchaseRequestItemVO> originalItemById = (request.getItems() == null ? List.<PurchaseRequestItemVO>of() : request.getItems()).stream()
            .filter(item -> item.getId() != null)
            .collect(Collectors.toMap(PurchaseRequestItemVO::getId, item -> item, (left, right) -> left, LinkedHashMap::new));
        List<Long> originalItemIds = quoteItems.stream()
            .map(SupplierQuoteItem::getPurchaseRequestItemId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        Map<Long, PurchaseRequestItem> originalEntityById = originalItemIds.isEmpty()
            ? Map.of()
            : purchaseRequestItemMapper.selectBatchIds(originalItemIds)
                .stream()
                .collect(Collectors.toMap(PurchaseRequestItem::getId, item -> item, (left, right) -> left, LinkedHashMap::new));
        return quoteItems.stream()
            .filter(item -> quoteById.containsKey(item.getQuoteId()))
            .map(item -> replacementQuoteDetailRow(item, quoteById.get(item.getQuoteId()), supplierById, originalItemById, originalEntityById))
            .sorted(Comparator
                .comparing((WorkbenchDetailRowVO row) -> row.getLineNo() == null ? Integer.MAX_VALUE : row.getLineNo())
                .thenComparing(row -> Boolean.TRUE.equals(row.getAwarded()) ? 0 : 1)
                .thenComparing(row -> firstText(row.getSupplierName(), "")))
            .toList();
    }

    private WorkbenchDetailRowVO replacementQuoteDetailRow(SupplierQuoteItem quoteItem,
                                                           SupplierQuote quote,
                                                           Map<Long, Supplier> supplierById,
                                                           Map<Long, PurchaseRequestItemVO> originalItemById,
                                                           Map<Long, PurchaseRequestItem> originalEntityById) {
        PurchaseRequestItemVO originalItem = originalItemById.get(quoteItem.getPurchaseRequestItemId());
        PurchaseRequestItem originalEntity = originalEntityById.get(quoteItem.getPurchaseRequestItemId());
        Supplier supplier = quote == null ? null : supplierById.get(quote.getSupplierId());
        boolean awarded = quoteItem.getAwarded() != null && quoteItem.getAwarded() == 1;
        WorkbenchDetailRowVO row = new WorkbenchDetailRowVO();
        row.setReplacementQuote(true);
        row.setItemId(quoteItem.getPurchaseRequestItemId());
        row.setQuoteItemId(quoteItem.getId());
        row.setLineNo(originalItem == null
            ? originalEntity == null ? quoteItem.getLineNo() : originalEntity.getLineNo()
            : originalItem.getLineNo());
        row.setOriginalItemName(originalItem == null
            ? originalEntity == null
                ? firstText(quoteItem.getItemCode(), "-")
                : firstText(originalEntity.getItemName(), originalEntity.getItemCode(), "-")
            : firstText(originalItem.getItemName(), originalItem.getItemCode(), "-"));
        row.setOriginalSpec(originalItem == null
            ? originalEntity == null
                ? "-"
                : firstText(originalEntity.getSpecModel(), originalEntity.getItemBrand(), "-")
            : firstText(originalItem.getSpecModel(), originalItem.getItemBrand(), "-"));
        row.setReplacementItemName(firstText(quoteItem.getReplacementItemName(), quoteItem.getItemName(), quoteItem.getItemCode(), "-"));
        row.setReplacementSpec(firstText(quoteItem.getReplacementSpecModel(), quoteItem.getSpecModel(), "-"));
        row.setReplacementEcommerceUrl(firstText(quoteItem.getReplacementEcommerceUrl(), null));
        row.setReplacementUnitPrice(firstAmount(quoteItem.getReplacementUnitPrice(), quoteItem.getUnitPrice()));
        row.setReplacementAmount(firstAmount(quoteItem.getReplacementLineAmount(), quoteItem.getLineAmount()));
        row.setReplacementReason(firstText(quoteItem.getReplacementReason(), "-"));
        row.setReplacementReviewStatus(firstText(quoteItem.getReplacementReviewStatus(), "-"));
        row.setReplacementReviewRemark(firstText(quoteItem.getReplacementReviewRemark(), "-"));
        row.setName(row.getReplacementItemName());
        row.setSpec(row.getReplacementSpec());
        row.setQty(quoteItem.getQty());
        row.setUnit(quoteItem.getUnit());
        row.setAmount(row.getReplacementAmount());
        row.setAmountText(formatMoney(row.getReplacementAmount()));
        row.setQuoteUnitPrice(quoteItem.getUnitPrice());
        row.setQuoteAmount(quoteItem.getLineAmount());
        row.setQuoteAmountText(formatMoney(quoteItem.getLineAmount()));
        row.setLeadTimeDays(quoteItem.getLeadTimeDays());
        row.setAwarded(awarded);
        row.setStatus(awarded ? "AWARDED" : "QUOTED");
        row.setStatusText(awarded ? "中标" : "已报价");
        row.setSupplierName(supplier == null ? "-" : firstText(supplier.getSupplierShortName(), supplier.getSupplierName(), "-"));
        row.setSupplierContactName(supplier == null ? "-" : firstText(supplier.getContactName(), "-"));
        row.setSupplierContactPhone(supplier == null ? "-" : firstText(supplier.getContactPhone(), "-"));
        row.setRemark(firstText(quoteItem.getQuoteRemark(), quote == null ? null : quote.getQuoteRemark(), "-"));
        return row;
    }

    private PurchaseRequest sourceRequest(PurchaseRequestItemVO item) {
        if (item == null || item.getSourceRequestId() == null) {
            return null;
        }
        return purchaseRequestMapper.selectById(item.getSourceRequestId());
    }

    private String sourceRequestRoomName(PurchaseRequest sourceRequest, PurchaseRequestVO request) {
        return firstText(
            sourceRequest == null ? null : sourceRequest.getDeptName(),
            sourceRequest == null ? null : sourceRequest.getDeptCode(),
            request == null ? null : request.getDeptName(),
            request == null ? null : request.getDeptCode(),
            ""
        );
    }

    private WorkbenchDetailRowVO platformDetailRow(PlatformPermissionRequest request) {
        WorkbenchDetailRowVO row = new WorkbenchDetailRowVO();
        row.setLineNo(1);
        row.setName(appName(request.getAppCode()));
        row.setSpec(firstText(request.getRoleCode(), "-"));
        row.setQty(BigDecimal.ONE);
        row.setUnit("项");
        row.setAmountText("-");
        row.setStatus(request.getStatus());
        row.setStatusText(platformStatusText(request.getStatus()));
        row.setRemark(firstText(request.getReason(), "-"));
        return row;
    }

    private WorkbenchDetailRowVO monthlyDetailRow(MonthlyProcurementDemand demand) {
        WorkbenchDetailRowVO row = new WorkbenchDetailRowVO();
        row.setLineNo(1);
        row.setName(firstText(demand.getItemName(), demand.getReqTitle()));
        row.setSpec(firstText(demand.getSpecModel(), "-"));
        row.setQty(demand.getQty());
        row.setUnit(demand.getUnit());
        row.setAmount(demand.getLineAmount());
        row.setAmountText(formatMoney(demand.getLineAmount()));
        row.setStatus(demand.getStatus());
        row.setStatusText(genericStatusText(demand.getStatus()));
        row.setRemark(firstText(demand.getPurchaseReason(), demand.getRemark(), "-"));
        return row;
    }

    private WorkbenchDetailRowVO afterSaleDetailRow(ProcurementAfterSale sale, ProcurementOrderVO order) {
        WorkbenchDetailRowVO row = new WorkbenchDetailRowVO();
        row.setItemId(sale.getQuoteItemId());
        row.setQuoteItemId(sale.getQuoteItemId());
        row.setLineNo(order == null || order.getLineNo() == null ? 1 : order.getLineNo());
        row.setName(order == null ? "-" : firstText(order.getItemName(), order.getItemCode(), "-"));
        row.setSpec(order == null ? "-" : firstText(order.getSpecModel(), "-"));
        row.setQty(sale.getRequestedQty());
        row.setUnit(order == null ? "" : order.getUnit());
        row.setAmount(order == null ? null : order.getLineAmount());
        row.setAmountText(formatMoney(order == null ? null : order.getLineAmount()));
        row.setStatus(sale.getStatus());
        row.setStatusText(afterSaleStatusText(sale.getStatus()));
        row.setRemark(afterSaleRemark(sale, order));
        row.setReplacementItemName(sale.getReplacementItemName());
        row.setReplacementSpec(sale.getReplacementSpecModel());
        row.setReplacementReason(sale.getReplacementRemark());
        row.setOriginalUnitPrice(order == null ? null : order.getUnitPrice());
        row.setPriceIncreaseLimit(order == null || order.getUnitPrice() == null ? AFTER_SALE_UNIT_PRICE_INCREASE_LIMIT : order.getUnitPrice().add(AFTER_SALE_UNIT_PRICE_INCREASE_LIMIT));
        row.setSupplierProposalType(firstText(sale.getSupplierProposalType(), sale.getAfterSaleType()));
        row.setSupplierProposalRemark(sale.getSupplierProposalRemark());
        row.setSupplierReplacementItemCode(sale.getSupplierReplacementItemCode());
        row.setSupplierReplacementItemName(sale.getSupplierReplacementItemName());
        row.setSupplierReplacementSpecModel(sale.getSupplierReplacementSpecModel());
        row.setSupplierReplacementUnit(sale.getSupplierReplacementUnit());
        row.setSupplierReplacementUnitPrice(sale.getSupplierReplacementUnitPrice());
        row.setSupplierReplacementLineAmount(sale.getSupplierReplacementLineAmount());
        row.setSupplierProposedAt(text(sale.getSupplierProposedAt()));
        row.setRequesterConfirmRemark(sale.getRequesterConfirmRemark());
        row.setRequesterConfirmedAt(text(sale.getRequesterConfirmedAt()));
        return row;
    }

    private List<WorkbenchFlowNodeVO> purchaseFlow(PurchaseRequestVO request, List<ApprovalRecordVO> records) {
        int currentNodeNo = request.getCurrentNodeNo() == null ? 0 : request.getCurrentNodeNo();
        Set<Integer> nodeNos = new java.util.TreeSet<>();
        nodeNos.add(0);
        records.stream()
            .map(ApprovalRecordVO::getNodeNo)
            .filter(Objects::nonNull)
            .forEach(nodeNos::add);
        if (isPendingApproval(request) && currentNodeNo > 0) {
            nodeNos.add(currentNodeNo);
        }
        List<WorkbenchFlowNodeVO> nodes = new ArrayList<>();
        for (Integer nodeNo : nodeNos) {
            ApprovalRecordVO record = latestRecord(records, nodeNo);
            WorkbenchFlowNodeVO node = new WorkbenchFlowNodeVO();
            node.setNodeNo(nodeNo);
            node.setName(record == null ? nodeName(nodeNo) : firstText(record.getNodeName(), nodeName(nodeNo)));
            node.setState(flowState(request, record, nodeNo));
            node.setStateText(flowStateText(node.getState()));
            node.setMeta(record == null ? "" : approvalRecordMeta(record));
            nodes.add(node);
        }
        return nodes;
    }

    private boolean isPendingApproval(PurchaseRequestVO request) {
        return "PENDING_APPROVAL".equals(normalizeCode(request.getStatus()))
            || "PENDING".equals(normalizeCode(request.getApprovalStatus()));
    }

    private List<WorkbenchFlowNodeVO> platformFlow(PlatformPermissionRequest request) {
        return List.of(
            flowNode(0, "提交申请", "done", firstText(request.getApplicantName(), "-") + " / " + request.getCreatedAt()),
            flowNode(1, "管理员审核", "PENDING".equals(request.getStatus()) ? "current" : donePlatform(request.getStatus()) ? "done" : "rejected", firstText(text(request.getReviewedAt()), "等待管理员处理")),
            flowNode(2, "权限生效", "APPROVED".equals(request.getStatus()) ? "done" : "pending", "APPROVED".equals(request.getStatus()) ? "系统入口已开通" : "")
        );
    }

    private List<WorkbenchFlowNodeVO> monthlyFlow(MonthlyProcurementDemand demand) {
        boolean generated = demand.getPurchaseRequestId() != null;
        return List.of(
            flowNode(0, "提交需求", "done", text(demand.getCreatedAt())),
            flowNode(1, "需求池汇总", generated ? "done" : "current", generated ? "已进入采购申请" : "等待本月需求汇总"),
            flowNode(2, "采购单生成", generated ? "done" : "pending", generated ? "采购申请ID：" + demand.getPurchaseRequestId() : "")
        );
    }

    private List<WorkbenchFlowNodeVO> afterSaleFlow(ProcurementAfterSale sale, ProcurementOrderVO order) {
        String status = normalizeCode(sale.getStatus());
        boolean pending = AFTER_SALE_STATUS_PENDING.equals(status);
        boolean proposalPending = AFTER_SALE_STATUS_PROPOSAL_PENDING.equals(status);
        boolean approved = AFTER_SALE_STATUS_APPROVED.equals(status);
        boolean rejected = AFTER_SALE_STATUS_REJECTED.equals(status);
        return List.of(
            flowNode(0, "提交售后", "done", firstText(afterSaleRequesterName(sale), "-") + " / " + text(sale.getCreatedAt())),
            flowNode(1, "供应商给出售后方案", pending ? "current" : rejected ? "rejected" : "done",
                pending ? afterSaleHandlerName(order) : firstText(afterSaleApproverName(sale), "-") + " / " + text(firstDateTime(sale.getSupplierProposedAt(), sale.getApprovedAt()))),
            flowNode(2, "需求方确认方案", proposalPending ? "current" : approved ? "done" : "pending",
                proposalPending ? afterSaleConfirmHandlerName(sale, order) : firstText(afterSaleRequesterConfirmName(sale), "")),
            flowNode(3, "售后处理", approved ? "done" : "pending",
                approved ? "方案确认后进入售后处理" : "")
        );
    }

    private WorkbenchFlowNodeVO flowNode(Integer nodeNo, String name, String state, String meta) {
        WorkbenchFlowNodeVO node = new WorkbenchFlowNodeVO();
        node.setNodeNo(nodeNo);
        node.setName(name);
        node.setState(state);
        node.setStateText(flowStateText(state));
        node.setMeta(meta);
        return node;
    }

    private String flowState(PurchaseRequestVO request, ApprovalRecordVO record, int nodeNo) {
        int currentNodeNo = request.getCurrentNodeNo() == null ? 0 : request.getCurrentNodeNo();
        String status = normalizeCode(firstText(request.getApprovalStatus(), request.getStatus()));
        if (nodeNo == currentNodeNo && isPendingApproval(request)) {
            return "current";
        }
        if (record != null) {
            String action = normalizeCode(record.getAction());
            if ("REJECT".equals(action)) {
                return "rejected";
            }
            if ("RETURN".equals(action)) {
                return "returned";
            }
            if ("TERMINATE".equals(action)) {
                return "rejected";
            }
            return "done";
        }
        if (nodeNo < currentNodeNo || List.of("APPROVED", "DONE", "ARCHIVED", "CLOSED").contains(status)) {
            return "done";
        }
        if (nodeNo == currentNodeNo) {
            return List.of("REJECTED", "TERMINATED").contains(status) ? "rejected" : "current";
        }
        return "pending";
    }

    private ApprovalRecordVO latestRecord(List<ApprovalRecordVO> records, int nodeNo) {
        return records.stream()
            .filter(record -> Objects.equals(record.getNodeNo(), nodeNo))
            .max(Comparator.comparing(record -> firstDateTime(record.getOperatedAt(), record.getCreatedAt())))
            .orElse(null);
    }

    private String approvalRecordMeta(ApprovalRecordVO record) {
        return firstText(record.getApproverName(), text(record.getApproverUserId())) + " / " + actionText(record.getAction()) + " / " + text(record.getOperatedAt());
    }

    private PlatformPermissionRequestVO toPlatformVO(PlatformPermissionRequest request) {
        PlatformPermissionRequestVO vo = new PlatformPermissionRequestVO();
        vo.setId(request.getId());
        vo.setApplicantUserId(request.getApplicantUserId());
        vo.setApplicantName(request.getApplicantName());
        vo.setApplicantEmail(request.getApplicantEmail());
        vo.setApplicantMobile(request.getApplicantMobile());
        vo.setUnitId(request.getUnitId());
        vo.setUnitName(unitName(request.getUnitId()));
        vo.setAppCode(request.getAppCode());
        vo.setAppName(appName(request.getAppCode()));
        vo.setRoleCode(request.getRoleCode());
        vo.setReason(request.getReason());
        vo.setStatus(request.getStatus());
        vo.setReviewerUserId(request.getReviewerUserId());
        vo.setReviewComment(request.getReviewComment());
        vo.setReviewedAt(request.getReviewedAt());
        vo.setCreatedAt(request.getCreatedAt());
        return vo;
    }

    private MonthlyDemandVO toMonthlyVO(MonthlyProcurementDemand demand) {
        MonthlyDemandVO vo = new MonthlyDemandVO();
        vo.setId(demand.getId());
        vo.setDemandNo(demand.getDemandNo());
        vo.setPlanMonth(demand.getPlanMonth());
        vo.setApplicantUserId(demand.getApplicantUserId());
        vo.setRegionId(demand.getRegionId());
        vo.setDeptName(demand.getDeptName());
        vo.setBudgetCode(demand.getBudgetCode());
        vo.setReqType(demand.getReqType());
        vo.setUrgencyLevel(demand.getUrgencyLevel());
        vo.setEmergency(demand.getEmergencyFlag() != null && demand.getEmergencyFlag() == 1);
        vo.setReqTitle(demand.getReqTitle());
        vo.setItemCode(demand.getItemCode());
        vo.setItemName(demand.getItemName());
        vo.setSpecModel(demand.getSpecModel());
        vo.setUnit(demand.getUnit());
        vo.setQty(demand.getQty());
        vo.setUnitPrice(demand.getUnitPrice());
        vo.setLineAmount(demand.getLineAmount());
        vo.setNeedDate(demand.getNeedDate());
        vo.setPurchaseReason(demand.getPurchaseReason());
        vo.setEcommerceUrl(demand.getEcommerceUrl());
        vo.setSourceChannel(demand.getSourceChannel());
        vo.setStatus(demand.getStatus());
        vo.setPurchaseRequestId(demand.getPurchaseRequestId());
        vo.setCreatedAt(demand.getCreatedAt());
        return vo;
    }

    private List<WorkbenchMetricVO> metrics(List<WorkbenchRequestVO> rows) {
        return List.of(
            new WorkbenchMetricVO("todo", "待我处理", count(rows, row -> "todo".equals(row.getRelation()))),
            new WorkbenchMetricVO("mine", "我发起", count(rows, row -> "mine".equals(row.getRelation()))),
            new WorkbenchMetricVO("related", "抄送", count(rows, row -> "related".equals(row.getRelation()))),
            new WorkbenchMetricVO("department", "部门工单", count(rows, row -> "department".equals(row.getRelation()))),
            new WorkbenchMetricVO("done", "已处理", count(rows, row -> "done".equals(row.getRelation())))
        );
    }

    private boolean matchesQuery(WorkbenchRequestVO row, WorkbenchRequestQueryDTO query) {
        if (!matchesFilter(row, normalizeCode(query.getFilter()))) {
            return false;
        }
        if (StringUtils.hasText(query.getModule()) && !normalizeCode(query.getModule()).equals(normalizeCode(row.getModule()))) {
            return false;
        }
        if (StringUtils.hasText(query.getStatusText()) && !query.getStatusText().equals(row.getStatusText())) {
            return false;
        }
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim().toLowerCase(Locale.ROOT);
            String haystack = (firstText(row.getReqNo(), "") + " " + firstText(row.getTitle(), "") + " " + firstText(row.getApplicant(), "")).toLowerCase(Locale.ROOT);
            if (!haystack.contains(keyword)) {
                return false;
            }
        }
        if (query.getStartDate() != null && row.getArrivedAt() != null && row.getArrivedAt().toLocalDate().isBefore(query.getStartDate())) {
            return false;
        }
        return query.getEndDate() == null || row.getArrivedAt() == null || !row.getArrivedAt().toLocalDate().isAfter(query.getEndDate());
    }

    private boolean matchesFilter(WorkbenchRequestVO row, String filter) {
        if (!StringUtils.hasText(filter) || "ALL".equals(filter)) {
            return true;
        }
        return switch (filter) {
            case "TODO" -> "todo".equals(row.getRelation());
            case "MINE" -> "mine".equals(row.getRelation());
            case "RELATED" -> "related".equals(row.getRelation());
            case "DEPARTMENT" -> "department".equals(row.getRelation());
            case "DONE" -> "done".equals(row.getRelation());
            case "PROCUREMENT" -> "PROCUREMENT".equals(row.getModule());
            case "MAINTENANCE" -> "MAINTENANCE".equals(row.getModule());
            case "PLATFORM" -> "PLATFORM".equals(row.getModule());
            case "SUPPLIER" -> "SUPPLIER".equals(row.getModule());
            default -> true;
        };
    }

    private int sortRow(WorkbenchRequestVO left, WorkbenchRequestVO right) {
        int relationCompare = Integer.compare(relationPriority(left.getRelation()), relationPriority(right.getRelation()));
        if (relationCompare != 0) {
            return relationCompare;
        }
        return firstDateTime(right.getArrivedAt()).compareTo(firstDateTime(left.getArrivedAt()));
    }

    private int relationPriority(String relation) {
        return switch (firstText(relation, "")) {
            case "todo" -> 0;
            case "done" -> 1;
            case "mine" -> 2;
            case "department" -> 3;
            case "related" -> 4;
            default -> 4;
        };
    }

    private long count(List<WorkbenchRequestVO> rows, Predicate<WorkbenchRequestVO> predicate) {
        return rows.stream().filter(predicate).count();
    }

    private <T> List<T> pageSlice(List<T> rows, long pageNo, long pageSize) {
        if (rows.isEmpty()) {
            return List.of();
        }
        int fromIndex = (int) Math.min(rows.size(), Math.max(0, (pageNo - 1) * pageSize));
        int toIndex = (int) Math.min(rows.size(), fromIndex + pageSize);
        return rows.subList(fromIndex, toIndex);
    }

    private Set<String> followedKeys(Long userId) {
        return followMapper.selectList(new LambdaQueryWrapper<WorkbenchFollow>()
                .eq(WorkbenchFollow::getUserId, userId))
            .stream()
            .map(follow -> sourceKey(follow.getSourceType(), follow.getSourceId()))
            .collect(Collectors.toSet());
    }

    private boolean isCopiedToUser(String sourceType, Long sourceId, Long userId) {
        if (sourceId == null || userId == null) {
            return false;
        }
        return copyMapper.selectCount(new LambdaQueryWrapper<WorkbenchCopy>()
            .eq(WorkbenchCopy::getUserId, userId)
            .eq(WorkbenchCopy::getSourceType, sourceType)
            .eq(WorkbenchCopy::getSourceId, sourceId)) > 0;
    }

    private WorkbenchUserOptionVO userOption(SysUser user) {
        WorkbenchUserOptionVO option = new WorkbenchUserOptionVO();
        option.setId(user.getId());
        option.setRealName(user.getRealName());
        option.setEmployeeNo(user.getEmployeeNo());
        option.setUsername(user.getUsername());
        option.setDisplayName(userDisplayName(user));
        return option;
    }

    private Long resolveAddSignUserId(String addSignUser) {
        if (!StringUtils.hasText(addSignUser)) {
            throw new BizException("ADD_SIGN_TARGET_INVALID", "Add-sign user is required");
        }
        String text = addSignUser.trim();
        if (text.matches("\\d+")) {
            return Long.parseLong(text);
        }
        List<SysUser> users = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
            .eq(SysUser::getStatus, 1)
            .and(wrapper -> wrapper
                .eq(SysUser::getUsername, text)
                .or()
                .eq(SysUser::getRealName, text)
                .or()
                .eq(SysUser::getEmployeeNo, text)));
        if (users.isEmpty()) {
            throw new BizException("ADD_SIGN_TARGET_NOT_FOUND", "Add-sign user was not found");
        }
        if (users.size() > 1) {
            throw new BizException("ADD_SIGN_TARGET_AMBIGUOUS", "Add-sign user is ambiguous, please use username or employee number");
        }
        return users.get(0).getId();
    }

    private String purchaseRelation(PurchaseRequestVO request, AuthUser authUser) {
        if (Objects.equals(request.getApplicantUserId(), authUser.getUserId())) {
            return "mine";
        }
        if (doneApproval(request.getApprovalStatus()) || doneApproval(request.getStatus())) {
            return "done";
        }
        return "related";
    }

    private void ensureSourceAppAccess(AuthUser authUser, String sourceType) {
        if (!hasSourceAppAccess(authUser, sourceType)) {
            throw new BizException("APP_ACCESS_DENIED", "Current account has no access to this application");
        }
    }

    private boolean hasSourceAppAccess(AuthUser authUser, String sourceType) {
        try {
            String appCode = appCodeForSource(normalizeSourceType(sourceType));
            return !StringUtils.hasText(appCode) || hasAppAccess(authUser, appCode);
        } catch (BizException ex) {
            return false;
        }
    }

    private String appCodeForSource(String sourceType) {
        return SOURCE_PLATFORM.equals(sourceType) ? APP_CODE_PLATFORM : APP_CODE_PROCUREMENT;
    }

    private boolean hasAppAccess(AuthUser authUser, String appCode) {
        if (authUser == null) {
            return false;
        }
        if (!StringUtils.hasText(appCode)) {
            return true;
        }
        String normalizedAppCode = normalizeCode(appCode);
        return authUser.getAppCodes() != null
            && authUser.getAppCodes().stream()
                .filter(StringUtils::hasText)
                .map(this::normalizeCode)
                .anyMatch(normalizedAppCode::equals);
    }

    private String normalizeSourceType(String sourceType) {
        String normalized = normalizeCode(sourceType);
        if (List.of(SOURCE_PURCHASE, SOURCE_PLATFORM, SOURCE_MONTHLY, SOURCE_SUPPLIER, SOURCE_AFTER_SALE).contains(normalized)) {
            return normalized;
        }
        throw new BizException("WORKBENCH_SOURCE_INVALID", "Unsupported workbench source");
    }

    private String sourceKey(String sourceType, Long sourceId) {
        return normalizeCode(sourceType) + ":" + sourceId;
    }

    private String normalizeCode(String code) {
        return StringUtils.hasText(code) ? code.trim().toUpperCase(Locale.ROOT) : "";
    }

    private String currentUserName() {
        AuthUser authUser = AuthContextHolder.get();
        if (authUser == null) {
            return "-";
        }
        return firstText(authUser.getRealName(), authUser.getUsername(), text(authUser.getUserId()));
    }

    private String currentApproverNames(PurchaseRequestVO request, AuthUser authUser) {
        if (request == null || request.getId() == null || request.getCurrentNodeNo() == null) {
            return "";
        }
        List<Long> userIds = approvalTaskMapper.selectList(new LambdaQueryWrapper<ApprovalTask>()
                .eq(ApprovalTask::getPurchaseRequestId, request.getId())
                .eq(ApprovalTask::getLevelNo, request.getCurrentNodeNo())
                .eq(ApprovalTask::getStatus, APPROVAL_TASK_PENDING)
                .orderByAsc(ApprovalTask::getId))
            .stream()
            .map(ApprovalTask::getCandidateUserId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        if (shouldShowOnlyCurrentAggregateApprover(request, authUser, userIds)) {
            return userDisplayNames(List.of(authUser.getUserId()));
        }
        return userDisplayNames(userIds);
    }

    private boolean shouldShowOnlyCurrentAggregateApprover(PurchaseRequestVO request,
                                                           AuthUser authUser,
                                                           List<Long> pendingUserIds) {
        return request != null
            && authUser != null
            && authUser.getUserId() != null
            && Objects.equals(request.getCurrentNodeNo(), ProcurementWorkflow.NODE_MANAGER_REVIEW)
            && NATIONAL_DEMAND_AGGREGATE_REASON.equals(request.getReqReason())
            && pendingUserIds != null
            && pendingUserIds.contains(authUser.getUserId());
    }

    private boolean isActivePurchaseApproval(PurchaseRequestVO request) {
        if (request == null) {
            return false;
        }
        return ProcurementWorkflow.STATUS_PENDING_APPROVAL.equals(normalizeCode(request.getStatus()))
            && ProcurementWorkflow.APPROVAL_PENDING.equals(normalizeCode(request.getApprovalStatus()));
    }

    private boolean isActivePurchaseApproval(PurchaseRequest request) {
        if (request == null) {
            return false;
        }
        return ProcurementWorkflow.STATUS_PENDING_APPROVAL.equals(normalizeCode(request.getStatus()))
            && ProcurementWorkflow.APPROVAL_PENDING.equals(normalizeCode(request.getApprovalStatus()));
    }

    private String purchaseCurrentNode(PurchaseRequestVO request) {
        if (request == null) {
            return "-";
        }
        String status = normalizeCode(request.getStatus());
        String approvalStatus = normalizeCode(request.getApprovalStatus());
        if (isActivePurchaseApproval(request)) {
            return nodeName(request.getCurrentNodeNo());
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
        return nodeName(request.getCurrentNodeNo());
    }

    private String userDisplayNames(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return "";
        }
        Map<Long, SysUser> users = userMapper.selectBatchIds(userIds).stream()
            .collect(Collectors.toMap(SysUser::getId, user -> user, (left, right) -> left));
        return userIds.stream()
            .map(users::get)
            .filter(Objects::nonNull)
            .map(this::userDisplayName)
            .filter(StringUtils::hasText)
            .collect(Collectors.joining("、"));
    }

    private String userDisplayName(SysUser user) {
        String name = firstText(user.getRealName(), user.getUsername(), text(user.getId()));
        return name;
    }

    private String nodeName(Integer nodeNo) {
        if (nodeNo == null) {
            return "-";
        }
        if (nodeNo == 0) {
            return "提交申请";
        }
        if (nodeNo == ProcurementWorkflow.NODE_APPLICANT_REPLACEMENT_REVIEW) {
            return ProcurementWorkflow.nodeNameForNode(nodeNo);
        }
        if (nodeNo >= ProcurementWorkflow.NODE_DUTY_LEADER_REVIEW && nodeNo <= ProcurementWorkflow.NODE_APPROVAL_V8_REVIEW) {
            return ProcurementWorkflow.nodeNameForNode(nodeNo);
        }
        return "节点 " + nodeNo;
    }

    private String requestTypeText(String type) {
        return switch (normalizeCode(type)) {
            case "MONTHLY" -> "月度需求";
            case "DAILY" -> "日常办公";
            case "EMERGENCY" -> "紧急采购";
            case "SERVICE" -> "服务采购";
            default -> firstText(type, "采购申请");
        };
    }

    private String approvalStatusText(String status) {
        return switch (normalizeCode(status)) {
            case "PENDING", "PENDING_APPROVAL" -> "待审批";
            case "APPROVED" -> "已通过";
            case "REJECTED" -> "已驳回";
            case "RETURNED" -> "已退回";
            case "CANCELLED" -> "已关闭";
            case "TERMINATED" -> "已终止";
            case "DRAFT" -> "草稿";
            case "QUOTING" -> "询价中";
            case "COMPARED" -> "已比价";
            case "ARCHIVED" -> "已归档";
            default -> firstText(status, "-");
        };
    }

    private String purchaseStatusText(PurchaseRequestVO request, String status, String relation, boolean actionable) {
        if (request != null && ProcurementWorkflow.STATUS_CANCELLED.equals(normalizeCode(request.getStatus()))) {
            return "已关闭";
        }
        if (isPartialApprovedPurchase(request)) {
            return "部分通过";
        }
        if (!actionable && "done".equals(relation) && isPendingPurchaseApproval(request, status)) {
            return "待他人确认";
        }
        return approvalStatusText(status);
    }

    private boolean isPendingPurchaseApproval(PurchaseRequestVO request, String status) {
        if ("PENDING".equals(normalizeCode(status)) || "PENDING_APPROVAL".equals(normalizeCode(status))) {
            return true;
        }
        return request != null
            && "PENDING_APPROVAL".equals(normalizeCode(request.getStatus()))
            && "PENDING".equals(normalizeCode(request.getApprovalStatus()));
    }

    private boolean isPartialApprovedPurchase(PurchaseRequestVO request) {
        if (request == null || !"APPROVED".equals(normalizeCode(request.getApprovalStatus()))) {
            return false;
        }
        if (request.getItems() == null || request.getItems().isEmpty()) {
            return false;
        }
        boolean hasRejected = request.getItems().stream()
            .anyMatch(item -> ProcurementWorkflow.ITEM_STATUS_REJECTED.equals(normalizeCode(item.getStatus())));
        boolean hasActive = request.getItems().stream()
            .anyMatch(item -> !ProcurementWorkflow.ITEM_STATUS_REJECTED.equals(normalizeCode(item.getStatus())));
        return hasRejected && hasActive;
    }

    private String platformStatusText(String status) {
        return switch (normalizeCode(status)) {
            case "PENDING" -> "待审批";
            case "APPROVED" -> "已通过";
            case "REJECTED" -> "已驳回";
            default -> firstText(status, "-");
        };
    }

    private String afterSaleStatusText(String status) {
        return switch (normalizeCode(status)) {
            case AFTER_SALE_STATUS_PENDING -> "待供应商方案";
            case AFTER_SALE_STATUS_PROPOSAL_PENDING -> "方案待确认";
            case AFTER_SALE_STATUS_APPROVED -> "已通过";
            case AFTER_SALE_STATUS_REJECTED -> "已驳回";
            default -> firstText(status, "-");
        };
    }

    private String afterSaleCurrentNode(String status) {
        return switch (normalizeCode(status)) {
            case AFTER_SALE_STATUS_PENDING -> "供应商给出售后方案";
            case AFTER_SALE_STATUS_PROPOSAL_PENDING -> "需求方确认售后方案";
            default -> "售后完成";
        };
    }

    private String afterSaleCurrentApproverNames(ProcurementAfterSale sale, ProcurementOrderVO order, String status) {
        return switch (normalizeCode(status)) {
            case AFTER_SALE_STATUS_PENDING -> afterSaleHandlerName(order);
            case AFTER_SALE_STATUS_PROPOSAL_PENDING -> afterSaleConfirmHandlerName(sale, order);
            default -> "";
        };
    }

    private Integer afterSaleCurrentNodeNo(String status) {
        return switch (normalizeCode(status)) {
            case AFTER_SALE_STATUS_PENDING -> 1;
            case AFTER_SALE_STATUS_PROPOSAL_PENDING -> 2;
            default -> 3;
        };
    }

    private String afterSaleTypeText(String type) {
        return switch (normalizeCode(type)) {
            case "NO_REASON_RETURN" -> "7天无理由退货";
            case "RETURN_EXCHANGE" -> "14天退换货";
            case "REPLACE_ITEM" -> "更换商品";
            case "REPAIR" -> "维修/退换";
            default -> "售后申请";
        };
    }

    private String afterSaleRequesterName(ProcurementAfterSale sale) {
        SysUser user = sale.getRequesterUserId() == null ? null : userMapper.selectById(sale.getRequesterUserId());
        return user == null ? text(sale.getRequesterUserId()) : userDisplayName(user);
    }

    private String afterSaleApproverName(ProcurementAfterSale sale) {
        SysUser user = sale.getApproverUserId() == null ? null : userMapper.selectById(sale.getApproverUserId());
        return user == null ? text(sale.getApproverUserId()) : userDisplayName(user);
    }

    private String afterSaleRequesterConfirmName(ProcurementAfterSale sale) {
        SysUser user = sale.getRequesterConfirmUserId() == null ? null : userMapper.selectById(sale.getRequesterConfirmUserId());
        return user == null ? text(sale.getRequesterConfirmUserId()) : userDisplayName(user);
    }

    private String afterSaleHandlerName(ProcurementOrderVO order) {
        return order == null ? "中标供应商" : firstText(order.getSupplierName(), "中标供应商");
    }

    private String afterSaleConfirmHandlerName(ProcurementAfterSale sale, ProcurementOrderVO order) {
        if (sale != null && sale.getRequesterUserId() != null) {
            String requester = afterSaleRequesterName(sale);
            if (StringUtils.hasText(requester)) {
                return requester;
            }
        }
        return order == null ? "需求提交者" : firstText(order.getApplicantName(), order.getPurchaserName(), "需求提交者");
    }

    private String afterSaleRemark(ProcurementAfterSale sale, ProcurementOrderVO order) {
        List<String> parts = new ArrayList<>();
        parts.add("类型：" + afterSaleTypeText(sale.getAfterSaleType()));
        parts.add("原因：" + firstText(sale.getReason(), "-"));
        if (StringUtils.hasText(sale.getReplacementItemName())) {
            parts.add("更换商品：" + sale.getReplacementItemName() + " / " + firstText(sale.getReplacementSpecModel(), "-"));
        }
        if (StringUtils.hasText(sale.getSupplierProposalRemark())) {
            parts.add("供应商方案：" + sale.getSupplierProposalRemark());
        }
        if (StringUtils.hasText(sale.getSupplierReplacementItemName())) {
            parts.add("方案商品：" + sale.getSupplierReplacementItemName() + " / " + firstText(sale.getSupplierReplacementSpecModel(), "-"));
        }
        if (sale.getSupplierReplacementUnitPrice() != null) {
            parts.add("方案单价：" + formatMoney(sale.getSupplierReplacementUnitPrice()));
        }
        if (StringUtils.hasText(sale.getRequesterConfirmRemark())) {
            parts.add("确认意见：" + sale.getRequesterConfirmRemark());
        }
        if (order != null && StringUtils.hasText(order.getDeliveryAddress())) {
            parts.add("送达地址：" + order.getDeliveryAddress());
        }
        return String.join("；", parts);
    }

    private String supplierStatusText(String status) {
        return switch (normalizeCode(status)) {
            case "AWARD_PENDING" -> "中标待确认";
            case "AWARD_CONFIRMED" -> "中标";
            case "AWARD_WAIVED" -> "已放弃";
            case "DRAFT" -> "待报价";
            case "SUBMITTED" -> "已报价";
            case "AWARDED" -> "已中标";
            case "NOT_AWARDED" -> "未中标";
            case "CONFIRMED" -> "已确认";
            case "PENDING" -> "中标待确认";
            case "WAIVED" -> "已放弃";
            case "QUOTING" -> "询价中";
            default -> genericStatusText(status);
        };
    }

    private String genericStatusText(String status) {
        return switch (normalizeCode(status)) {
            case "DRAFT" -> "草稿";
            case "PENDING" -> "待处理";
            case "APPROVED" -> "已通过";
            case "ACTIVE", "VALID" -> "有效";
            case "DONE", "COMPLETED" -> "已完成";
            case "REJECTED" -> "已驳回";
            case "RETURNED" -> "已退回";
            case "SUBMITTED" -> "已提交";
            case "SUPPLEMENTED" -> "已补充";
            default -> firstText(status, "-");
        };
    }

    private String actionText(String action) {
        return switch (normalizeCode(action)) {
            case "APPROVE" -> "通过";
            case "REJECT" -> "驳回";
            case "RETURN" -> "退回补充";
            case "ADD_SIGN" -> "加签";
            case "SAVE" -> "暂存";
            case "TERMINATE" -> "终止";
            case "CLOSE" -> "关闭";
            case "SUBMIT" -> "提交";
            default -> firstText(action, "-");
        };
    }

    private String flowStateText(String state) {
        return switch (firstText(state, "")) {
            case "done" -> "已提交";
            case "current" -> "当前节点";
            case "rejected" -> "已驳回";
            case "returned" -> "已退回";
            default -> "未开始";
        };
    }

    private boolean doneApproval(String status) {
        return List.of("APPROVED", "REJECTED", "CANCELLED", "TERMINATED", "CLOSED", "DONE", "ARCHIVED")
            .contains(normalizeCode(status));
    }

    private boolean donePlatform(String status) {
        String normalizedStatus = normalizeCode(status);
        return "APPROVED".equals(normalizedStatus) || "REJECTED".equals(normalizedStatus);
    }

    private String appName(String appCode) {
        return switch (normalizeCode(appCode)) {
            case "MAINTENANCE" -> "维保管理";
            case "PROCUREMENT" -> "采购管理";
            case "PLATFORM" -> "平台管理";
            default -> firstText(appCode, "-");
        };
    }

    private String unitName(Long unitId) {
        if (unitId == null) {
            return "";
        }
        PlatformUnit unit = platformUnitMapper.selectById(unitId);
        return unit == null ? "" : unit.getUnitName();
    }

    private String formatMoney(BigDecimal amount) {
        if (amount == null) {
            return "-";
        }
        return "¥" + amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private BigDecimal firstAmount(BigDecimal... values) {
        for (BigDecimal value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private LocalDateTime dateStart(LocalDate date) {
        return date == null ? null : date.atStartOfDay();
    }

    private LocalDateTime firstDateTime(LocalDateTime... values) {
        for (LocalDateTime value : values) {
            if (value != null) {
                return value;
            }
        }
        return LocalDateTime.MIN;
    }

    private boolean canReviewPlatformPermission(AuthUser authUser) {
        if (DataScopeHelper.isSystemAdmin(authUser)) {
            return true;
        }
        if (authUser == null || authUser.getPermissionCodes() == null) {
            return false;
        }
        return authUser.getPermissionCodes().stream()
            .filter(StringUtils::hasText)
            .map(code -> code.trim().toLowerCase(Locale.ROOT))
            .anyMatch(code -> PLATFORM_PERMISSION_MANAGE.equals(code) || WILDCARD_PERMISSION_CODE.equals(code));
    }

    private boolean canApproveAfterSale(AuthUser authUser, ProcurementOrderVO order) {
        if (authUser == null) {
            return false;
        }
        if (DataScopeHelper.isSupplierUser(authUser)) {
            return SupplierSoldOrderAccess.canOperate(authUser, order);
        }
        if (DataScopeHelper.isSystemAdmin(authUser)) {
            return true;
        }
        return false;
    }

    private boolean canConfirmAfterSaleProposal(AuthUser authUser, ProcurementAfterSale sale, ProcurementOrderVO order) {
        if (authUser == null || sale == null) {
            return false;
        }
        if (DataScopeHelper.isSystemAdmin(authUser)) {
            return true;
        }
        if (DataScopeHelper.isSupplierUser(authUser)) {
            return false;
        }
        Long userId = authUser.getUserId();
        return Objects.equals(userId, sale.getRequesterUserId())
            || (order != null && (Objects.equals(userId, order.getApplicantUserId())
            || Objects.equals(userId, order.getPurchaserUserId())
            || orderInvoiceService.canOperateAsSourceAssistant(authUser, order)));
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
