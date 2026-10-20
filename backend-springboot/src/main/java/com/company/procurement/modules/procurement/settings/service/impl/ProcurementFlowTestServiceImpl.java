package com.company.procurement.modules.procurement.settings.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.procurement.common.exception.BizException;
import com.company.procurement.common.security.AuthContextHolder;
import com.company.procurement.common.security.AuthUser;
import com.company.procurement.common.security.DataScopeHelper;
import com.company.procurement.modules.org.scope.entity.OrgScope;
import com.company.procurement.modules.org.scope.mapper.OrgScopeMapper;
import com.company.procurement.modules.procurement.request.dto.PurchaseRequestCreateDTO;
import com.company.procurement.modules.procurement.request.dto.PurchaseRequestItemDTO;
import com.company.procurement.modules.procurement.request.entity.PurchaseRequest;
import com.company.procurement.modules.procurement.request.entity.PurchaseRequestItem;
import com.company.procurement.modules.procurement.request.mapper.PurchaseRequestItemMapper;
import com.company.procurement.modules.procurement.request.mapper.PurchaseRequestMapper;
import com.company.procurement.modules.procurement.settings.service.ProcurementFlowTestService;
import com.company.procurement.modules.procurement.settings.vo.ProcurementRuleTestResultVO;
import com.company.procurement.modules.procurement.workflow.ProcurementWorkflow;
import com.company.procurement.modules.procurement.workflow.dto.WorkflowApprovalActionDTO;
import com.company.procurement.modules.procurement.workflow.dto.WorkflowQuoteConfirmDTO;
import com.company.procurement.modules.procurement.workflow.service.WorkflowService;
import com.company.procurement.modules.supplier.dto.SupplierAwardConfirmDTO;
import com.company.procurement.modules.supplier.dto.SupplierQuoteItemSubmitDTO;
import com.company.procurement.modules.supplier.dto.SupplierQuoteSubmitDTO;
import com.company.procurement.modules.supplier.entity.Supplier;
import com.company.procurement.modules.supplier.entity.SupplierQuote;
import com.company.procurement.modules.supplier.entity.SupplierQuoteItem;
import com.company.procurement.modules.supplier.mapper.SupplierMapper;
import com.company.procurement.modules.supplier.mapper.SupplierQuoteItemMapper;
import com.company.procurement.modules.supplier.mapper.SupplierQuoteMapper;
import com.company.procurement.modules.supplier.service.SupplierQuoteService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ProcurementFlowTestServiceImpl implements ProcurementFlowTestService {

    private static final String TEST_TITLE_PREFIX = "[流程测试]";
    private static final List<String> TEST_SUPPLIER_CODES = List.of("TEST-SUP-001", "TEST-SUP-002", "TEST-SUP-003");

    private final WorkflowService workflowService;
    private final PurchaseRequestMapper purchaseRequestMapper;
    private final PurchaseRequestItemMapper purchaseRequestItemMapper;
    private final SupplierMapper supplierMapper;
    private final SupplierQuoteMapper supplierQuoteMapper;
    private final SupplierQuoteItemMapper supplierQuoteItemMapper;
    private final SupplierQuoteService supplierQuoteService;
    private final OrgScopeMapper orgScopeMapper;
    private final Environment environment;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProcurementRuleTestResultVO triggerFormalAction(String actionCode) {
        if (!environment.acceptsProfiles(Profiles.of("test"))) {
            throw new BizException("FLOW_TEST_TEST_ENV_ONLY", "正式流程测试仅允许在测试环境执行");
        }
        String normalized = actionCode == null ? "" : actionCode.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return switch (normalized) {
            case "CREATE_REQUEST" -> createRequest(false);
            case "CREATE_URGENT_REQUEST" -> createRequest(true);
            case "SUBMIT_REQUEST" -> submitRequest();
            case "APPROVE_DEMAND" -> approveDemand();
            case "CREATE_QUOTES" -> createSupplierQuotes();
            case "SUBMIT_QUOTE_REVIEW" -> submitQuoteReview();
            case "APPROVE_QUOTE_REVIEW" -> approveQuoteReview();
            case "SUPPLIER_CONFIRM" -> supplierConfirm();
            default -> throw new BizException("FLOW_TEST_ACTION_NOT_FOUND", "正式测试操作不存在");
        };
    }

    private ProcurementRuleTestResultVO createRequest(boolean urgent) {
        AuthUser authUser = AuthContextHolder.requireUser();
        PurchaseRequestCreateDTO dto = new PurchaseRequestCreateDTO();
        dto.setReqTitle(TEST_TITLE_PREFIX + (urgent ? " 紧急采购 " : " 普通采购 ") + nowCode());
        dto.setReqType("服务/费用/低值易耗等");
        dto.setRegionId(authUser.getRegionId() == null ? 1L : authUser.getRegionId());
        applyTestDepartment(dto);
        dto.setCurrency("CNY");
        dto.setTotalAmount(new BigDecimal("3200.00"));
        dto.setUrgencyLevel(urgent ? 3 : 1);
        dto.setNeedDate(LocalDate.now().plusDays(7));
        dto.setReqReason("正式流程测试按钮生成");
        dto.setAwardStrategy(ProcurementWorkflow.AWARD_STRATEGY_TOTAL_LOWEST);
        dto.setRemark("正式流程测试数据，可用于后续审批、报价和确认");
        dto.setItems(List.of(testItem("TEST-MAT-001", "测试网线", "CAT6", "箱", "2", "800.00"),
            testItem("TEST-MAT-002", "测试扎带", "4*300", "包", "4", "400.00")));

        Long requestId = workflowService.createRequest(dto);
        return result(urgent ? "CREATE_URGENT_REQUEST" : "CREATE_REQUEST", urgent ? "创建紧急测试申请" : "创建测试申请",
            true, "已正式创建采购申请", requestDetail(requestId));
    }

    private void applyTestDepartment(PurchaseRequestCreateDTO dto) {
        OrgScope scope = orgScopeMapper.selectOne(new LambdaQueryWrapper<OrgScope>()
            .eq(OrgScope::getStatus, 1)
            .eq(OrgScope::getScopeType, "BUILDING")
            .orderByAsc(OrgScope::getId)
            .last("LIMIT 1"));
        if (scope == null) {
            return;
        }
        dto.setDeptCode(firstText(scope.getDeptCode(), scope.getCostCenterCode(), scope.getScopeCode()));
        dto.setDeptName(firstText(scope.getDeptName(), scope.getScopeName(), scope.getBuildingName(), scope.getRoomName()));
    }

    private ProcurementRuleTestResultVO submitRequest() {
        PurchaseRequest request = latestRequest(List.of(ProcurementWorkflow.STATUS_DRAFT, ProcurementWorkflow.STATUS_RETURNED), null);
        workflowService.submitRequest(request.getId());
        return result("SUBMIT_REQUEST", "提交测试申请", true, "已正式提交采购申请", requestDetail(request.getId()));
    }

    private ProcurementRuleTestResultVO approveDemand() {
        PurchaseRequest request = latestRequest(List.of(ProcurementWorkflow.STATUS_PENDING_APPROVAL),
            ProcurementWorkflow.NODE_FLOOR_LEADER_DEMAND_REVIEW);
        workflowService.approveRequest(request.getId(), approval("APPROVE", "正式测试：需求审批通过，进入供应商报价"));
        return result("APPROVE_DEMAND", "需求审批通过", true, "已正式审批通过并进入报价", requestDetail(request.getId()));
    }

    private ProcurementRuleTestResultVO createSupplierQuotes() {
        PurchaseRequest request = latestRequest(List.of(
            ProcurementWorkflow.STATUS_QUOTING,
            ProcurementWorkflow.STATUS_DEMAND_POOL
        ), null);
        if (ProcurementWorkflow.STATUS_DEMAND_POOL.equals(request.getStatus())) {
            Long aggregateRequestId = workflowService.publishDemandPoolRequestForFlowTest(request.getId());
            request = purchaseRequestMapper.selectById(aggregateRequestId);
        }
        List<PurchaseRequestItem> items = requestItems(request.getId());
        if (items.isEmpty()) {
            throw new BizException("FLOW_TEST_ITEMS_EMPTY", "测试采购申请没有明细，无法生成报价");
        }
        List<Supplier> suppliers = ensureTestSuppliers(request.getRegionId());
        AuthUser originalAuthUser = AuthContextHolder.requireUser();
        try {
            int index = 0;
            for (Supplier supplier : suppliers) {
                AuthContextHolder.set(flowTestSupplierAuthUser(supplier));
                submitSupplierQuote(
                    request,
                    items,
                    new BigDecimal("0.90").add(new BigDecimal("0.08").multiply(BigDecimal.valueOf(index))),
                    3 + index
                );
                index++;
            }
        } finally {
            AuthContextHolder.set(originalAuthUser);
        }
        return result("CREATE_QUOTES", "生成供应商报价", true, "已正式生成3家供应商报价", requestDetail(request.getId()));
    }

    private ProcurementRuleTestResultVO submitQuoteReview() {
        PurchaseRequest request = latestRequest(List.of(ProcurementWorkflow.STATUS_QUOTING), null);
        WorkflowQuoteConfirmDTO dto = new WorkflowQuoteConfirmDTO();
        dto.setAwardStrategy(ProcurementWorkflow.AWARD_STRATEGY_TOTAL_LOWEST);
        dto.setLeaderConfirmRemark("正式测试：按总价最低发起报价审核");
        workflowService.confirmWinningQuote(request.getId(), dto);
        return result("SUBMIT_QUOTE_REVIEW", "发起报价审核", true, "已正式发起报价审核", requestDetail(request.getId()));
    }

    private ProcurementRuleTestResultVO approveQuoteReview() {
        PurchaseRequest request = latestRequest(List.of(ProcurementWorkflow.STATUS_PENDING_APPROVAL), null);
        if (request.getCurrentNodeNo() == null || request.getCurrentNodeNo() < ProcurementWorkflow.NODE_MANAGER_QUOTE_REVIEW) {
            throw new BizException("FLOW_TEST_STAGE_INVALID", "最新测试单尚未进入报价审核节点");
        }
        workflowService.approveRequest(request.getId(), approval("APPROVE", "正式测试：报价审核通过"));
        return result("APPROVE_QUOTE_REVIEW", "报价审核通过", true, "已正式通过报价审核", requestDetail(request.getId()));
    }

    private ProcurementRuleTestResultVO supplierConfirm() {
        AuthUser originalAuthUser = AuthContextHolder.requireUser();
        PurchaseRequest request = latestRequest(List.of(ProcurementWorkflow.STATUS_APPROVED), null);
        List<SupplierQuote> awardedQuotes = awardedQuotes(request);
        if (awardedQuotes.isEmpty()) {
            throw new BizException("FLOW_TEST_AWARD_NOT_FOUND", "最新测试单没有待确认的中标报价");
        }
        try {
            for (SupplierQuote quote : awardedQuotes) {
                Supplier supplier = supplierMapper.selectById(quote.getSupplierId());
                if (supplier == null) {
                    throw new BizException("FLOW_TEST_SUPPLIER_NOT_FOUND", "流程测试中标供应商不存在");
                }
                AuthContextHolder.set(flowTestSupplierAuthUser(supplier));
                SupplierAwardConfirmDTO dto = new SupplierAwardConfirmDTO();
                dto.setConfirmRemark("正式流程测试：供应商确认中标");
                supplierQuoteService.confirmAward(request.getId(), dto);
            }
        } finally {
            AuthContextHolder.set(originalAuthUser);
        }
        return result("SUPPLIER_CONFIRM", "供应商确认中标", true, "已正式确认中标供应商", requestDetail(request.getId()));
    }

    private AuthUser flowTestSupplierAuthUser(Supplier supplier) {
        return AuthUser.builder()
            .userId(0L)
            .username("flow-test-supplier:" + supplier.getSupplierCode())
            .realName(supplier.getSupplierName())
            .userType(DataScopeHelper.USER_TYPE_SUPPLIER)
            .regionId(supplier.getRegionId())
            .supplierId(supplier.getId())
            .dataScopeType(DataScopeHelper.SCOPE_SELF)
            .roleCodes(List.of("SUPPLIER"))
            .build();
    }

    private PurchaseRequestItemDTO testItem(String code, String name, String spec, String unit, String qty, String unitPrice) {
        PurchaseRequestItemDTO item = new PurchaseRequestItemDTO();
        item.setItemCode(code);
        item.setReqType("服务/费用/低值易耗等");
        item.setItemName(name);
        item.setSpecModel(spec);
        item.setUnit(unit);
        item.setQty(new BigDecimal(qty));
        item.setUnitPrice(new BigDecimal(unitPrice));
        item.setTaxRate(new BigDecimal("0.13"));
        item.setLineAmount(item.getQty().multiply(item.getUnitPrice()).setScale(2, RoundingMode.HALF_UP));
        item.setNeedDate(LocalDate.now().plusDays(7));
        item.setPurpose("正式流程测试");
        item.setStatus("ACTIVE");
        return item;
    }

    private WorkflowApprovalActionDTO approval(String action, String comment) {
        WorkflowApprovalActionDTO dto = new WorkflowApprovalActionDTO();
        dto.setAction(action);
        dto.setApprovalComment(comment);
        return dto;
    }

    private PurchaseRequest latestRequest(List<String> statuses, Integer currentNodeNo) {
        LambdaQueryWrapper<PurchaseRequest> wrapper = new LambdaQueryWrapper<PurchaseRequest>()
            .likeRight(PurchaseRequest::getReqTitle, TEST_TITLE_PREFIX)
            .orderByDesc(PurchaseRequest::getId)
            .last("LIMIT 1");
        if (statuses != null && !statuses.isEmpty()) {
            wrapper.in(PurchaseRequest::getStatus, statuses);
        }
        if (currentNodeNo != null) {
            wrapper.eq(PurchaseRequest::getCurrentNodeNo, currentNodeNo);
        }
        PurchaseRequest request = purchaseRequestMapper.selectOne(wrapper);
        if (request == null) {
            request = latestAggregateForTestSource(statuses, currentNodeNo);
        }
        if (request == null) {
            throw new BizException("FLOW_TEST_REQUEST_NOT_FOUND", "没有找到当前步骤可用的测试采购申请，请先执行前置按钮");
        }
        return request;
    }

    private PurchaseRequest latestAggregateForTestSource(List<String> statuses, Integer currentNodeNo) {
        PurchaseRequest source = purchaseRequestMapper.selectOne(new LambdaQueryWrapper<PurchaseRequest>()
            .likeRight(PurchaseRequest::getReqTitle, TEST_TITLE_PREFIX)
            .eq(PurchaseRequest::getStatus, ProcurementWorkflow.STATUS_DEMAND_AGGREGATED)
            .orderByDesc(PurchaseRequest::getId)
            .last("LIMIT 1"));
        if (source == null) {
            return null;
        }
        PurchaseRequestItem aggregateItem = purchaseRequestItemMapper.selectOne(
            new LambdaQueryWrapper<PurchaseRequestItem>()
                .eq(PurchaseRequestItem::getSourceRequestId, source.getId())
                .orderByDesc(PurchaseRequestItem::getId)
                .last("LIMIT 1")
        );
        if (aggregateItem == null) {
            return null;
        }
        PurchaseRequest aggregate = purchaseRequestMapper.selectById(aggregateItem.getPurchaseRequestId());
        if (aggregate == null
            || (statuses != null && !statuses.isEmpty() && !statuses.contains(aggregate.getStatus()))
            || (currentNodeNo != null && !currentNodeNo.equals(aggregate.getCurrentNodeNo()))) {
            return null;
        }
        return aggregate;
    }

    private List<PurchaseRequestItem> requestItems(Long requestId) {
        return purchaseRequestItemMapper.selectList(new LambdaQueryWrapper<PurchaseRequestItem>()
            .eq(PurchaseRequestItem::getPurchaseRequestId, requestId)
            .orderByAsc(PurchaseRequestItem::getLineNo));
    }

    private List<Supplier> ensureTestSuppliers(Long regionId) {
        return TEST_SUPPLIER_CODES.stream().map(code -> ensureSupplier(code, regionId)).toList();
    }

    private Supplier ensureSupplier(String code, Long regionId) {
        Supplier supplier = supplierMapper.selectOne(new LambdaQueryWrapper<Supplier>()
            .eq(Supplier::getSupplierCode, code)
            .last("LIMIT 1"));
        if (supplier != null) {
            return supplier;
        }
        supplier = new Supplier();
        supplier.setSupplierCode(code);
        supplier.setSupplierName("流程测试供应商" + code.substring(code.length() - 1));
        supplier.setSupplierShortName("测试供应商" + code.substring(code.length() - 1));
        supplier.setTaxNo("TESTTAX" + code.replace("-", ""));
        supplier.setSupplierType("DISTRIBUTOR");
        supplier.setRegionId(regionId == null ? 1L : regionId);
        supplier.setContactName("测试联系人");
        supplier.setContactPhone("13800000001");
        supplier.setContactEmail(code.toLowerCase(Locale.ROOT) + "@test.local");
        supplier.setAddress("酒仙桥测试地址");
        supplier.setOnboardingStatus("APPROVED");
        supplier.setStatus("ACTIVE");
        supplier.setBlacklistFlag(0);
        supplier.setDataIsolationFlag(1);
        supplier.setEntryDate(LocalDate.now());
        supplier.setRemark("正式流程测试供应商");
        supplierMapper.insert(supplier);
        return supplier;
    }

    private void submitSupplierQuote(PurchaseRequest request, List<PurchaseRequestItem> items,
                                     BigDecimal priceFactor, int leadTimeDays) {
        SupplierQuoteSubmitDTO dto = new SupplierQuoteSubmitDTO();
        dto.setPurchaseRequestId(request.getId());
        dto.setLeadTimeDays(leadTimeDays);
        dto.setQuoteRemark("正式流程测试报价");
        dto.setItems(items.stream().map(requestItem -> {
            if (requestItem.getUnitPrice() == null || requestItem.getUnitPrice().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BizException("FLOW_TEST_ITEM_PRICE_INVALID", "测试采购申请明细缺少有效参考单价");
            }
            SupplierQuoteItemSubmitDTO item = new SupplierQuoteItemSubmitDTO();
            item.setPurchaseRequestItemId(requestItem.getId());
            item.setUnitPrice(requestItem.getUnitPrice().multiply(priceFactor).setScale(2, RoundingMode.HALF_UP));
            item.setLeadTimeDays(leadTimeDays);
            item.setReplacementFlag(false);
            item.setWaived(false);
            item.setQuoteRemark("正式流程测试报价明细");
            return item;
        }).toList());
        supplierQuoteService.submitQuote(dto);
    }

    private List<SupplierQuote> awardedQuotes(PurchaseRequest request) {
        if (ProcurementWorkflow.isItemLowestAwardStrategy(request.getAwardStrategy())) {
            List<Long> quoteIds = supplierQuoteItemMapper.selectList(new LambdaQueryWrapper<SupplierQuoteItem>()
                    .eq(SupplierQuoteItem::getPurchaseRequestId, request.getId())
                    .eq(SupplierQuoteItem::getAwarded, 1))
                .stream()
                .map(SupplierQuoteItem::getQuoteId)
                .distinct()
                .toList();
            return quoteIds.isEmpty() ? List.of() : supplierQuoteMapper.selectBatchIds(quoteIds);
        }
        if (request.getSelectedQuoteId() == null) {
            return List.of();
        }
        SupplierQuote quote = supplierQuoteMapper.selectById(request.getSelectedQuoteId());
        return quote == null ? List.of() : List.of(quote);
    }

    private Map<String, Object> requestDetail(Long requestId) {
        PurchaseRequest request = purchaseRequestMapper.selectById(requestId);
        Map<String, Object> detail = new LinkedHashMap<>();
        if (request == null) {
            detail.put("requestId", requestId);
            return detail;
        }
        detail.put("requestId", request.getId());
        detail.put("reqNo", request.getReqNo());
        detail.put("status", request.getStatus());
        detail.put("approvalStatus", request.getApprovalStatus());
        detail.put("currentNodeNo", request.getCurrentNodeNo());
        detail.put("selectedQuoteId", request.getSelectedQuoteId());
        detail.put("leaderConfirmStatus", request.getLeaderConfirmStatus());
        return detail;
    }

    private ProcurementRuleTestResultVO result(
        String actionCode,
        String actionName,
        boolean triggered,
        String message,
        Map<String, ?> detail
    ) {
        ProcurementRuleTestResultVO vo = new ProcurementRuleTestResultVO();
        vo.setActionCode(actionCode);
        vo.setActionName(actionName);
        vo.setTriggered(triggered);
        vo.setMessage(message);
        vo.setDetail(new LinkedHashMap<>(detail));
        return vo;
    }

    private String nowCode() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
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
}
