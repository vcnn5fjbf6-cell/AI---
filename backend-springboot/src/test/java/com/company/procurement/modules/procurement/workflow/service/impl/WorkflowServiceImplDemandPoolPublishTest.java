package com.company.procurement.modules.procurement.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.procurement.common.exception.BizException;
import com.company.procurement.common.security.AuthContextHolder;
import com.company.procurement.common.security.AuthUser;
import com.company.procurement.modules.approval.record.entity.ApprovalInstance;
import com.company.procurement.modules.approval.record.entity.ApprovalRecord;
import com.company.procurement.modules.approval.record.mapper.ApprovalInstanceMapper;
import com.company.procurement.modules.approval.record.mapper.ApprovalRecordMapper;
import com.company.procurement.modules.approval.record.service.ApprovalTaskService;
import com.company.procurement.modules.procurement.order.mapper.ProcurementAfterSaleMapper;
import com.company.procurement.modules.procurement.request.entity.PurchaseRequest;
import com.company.procurement.modules.procurement.request.entity.PurchaseRequestItem;
import com.company.procurement.modules.procurement.request.mapper.PurchaseRequestItemMapper;
import com.company.procurement.modules.procurement.request.mapper.PurchaseRequestMapper;
import com.company.procurement.modules.procurement.request.service.PurchaseRequestNoService;
import com.company.procurement.modules.procurement.request.service.PurchaseRequestService;
import com.company.procurement.modules.procurement.route.service.ProcurementApprovalRouteResolver;
import com.company.procurement.modules.procurement.settings.mapper.ProcurementSettingMapper;
import com.company.procurement.modules.procurement.settings.service.ProcurementSettingsService;
import com.company.procurement.modules.procurement.settings.vo.ProcurementSettingItemVO;
import com.company.procurement.modules.procurement.settings.vo.ProcurementSettingsVO;
import com.company.procurement.modules.procurement.workflow.ProcurementWorkflow;
import com.company.procurement.modules.procurement.workflow.dto.WorkflowApprovalActionDTO;
import com.company.procurement.modules.procurement.workflow.dto.WorkflowApprovalItemDecisionDTO;
import com.company.procurement.modules.procurement.workflow.mapper.ProcurementMessageReadMapper;
import com.company.procurement.modules.procurement.workflow.service.AwardPriceSyncService;
import com.company.procurement.modules.procurement.workflow.service.OverPriceReviewService;
import com.company.procurement.modules.supplier.entity.SupplierQuote;
import com.company.procurement.modules.supplier.entity.SupplierQuoteItem;
import com.company.procurement.modules.supplier.mapper.SupplierMapper;
import com.company.procurement.modules.supplier.mapper.SupplierQuoteItemMapper;
import com.company.procurement.modules.supplier.mapper.SupplierQuoteMapper;
import com.company.procurement.modules.supplier.service.SupplierAwardNotificationService;
import com.company.procurement.modules.supplier.service.TemporarySupplierRecommendationService;
import com.company.procurement.modules.system.user.entity.SysUser;
import com.company.procurement.modules.system.user.mapper.SysUserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowServiceImplDemandPoolPublishTest {

    private static final String NATIONAL_DEMAND_AGGREGATE_REASON = "需求池全国月度汇总生成";

    @Mock
    private PurchaseRequestService purchaseRequestService;
    @Mock
    private PurchaseRequestMapper purchaseRequestMapper;
    @Mock
    private PurchaseRequestItemMapper purchaseRequestItemMapper;
    @Mock
    private ApprovalInstanceMapper approvalInstanceMapper;
    @Mock
    private ApprovalRecordMapper approvalRecordMapper;
    @Mock
    private ApprovalTaskService approvalTaskService;
    @Mock
    private ProcurementApprovalRouteResolver approvalRouteResolver;
    @Mock
    private SysUserMapper userMapper;
    @Mock
    private ProcurementSettingMapper procurementSettingMapper;
    @Mock
    private ProcurementSettingsService procurementSettingsService;
    @Mock
    private SupplierQuoteMapper supplierQuoteMapper;
    @Mock
    private SupplierQuoteItemMapper supplierQuoteItemMapper;
    @Mock
    private ProcurementAfterSaleMapper procurementAfterSaleMapper;
    @Mock
    private SupplierMapper supplierMapper;
    @Mock
    private SupplierAwardNotificationService awardNotificationService;
    @Mock
    private TemporarySupplierRecommendationService temporarySupplierRecommendationService;
    @Mock
    private PurchaseRequestNoService requestNoService;
    @Mock
    private ProcurementMessageReadMapper messageReadMapper;
    @Mock
    private AwardPriceSyncService awardPriceSyncService;
    @Mock
    private OverPriceReviewService overPriceReviewService;

    private WorkflowServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new WorkflowServiceImpl(
            purchaseRequestService,
            purchaseRequestMapper,
            purchaseRequestItemMapper,
            approvalInstanceMapper,
            approvalRecordMapper,
            approvalTaskService,
            approvalRouteResolver,
            userMapper,
            procurementSettingMapper,
            procurementSettingsService,
            supplierQuoteMapper,
            supplierQuoteItemMapper,
            procurementAfterSaleMapper,
            supplierMapper,
            awardNotificationService,
            temporarySupplierRecommendationService,
            requestNoService,
            messageReadMapper,
            awardPriceSyncService,
            overPriceReviewService
        );
        AuthContextHolder.set(AuthUser.builder()
            .userId(1L)
            .userType("ADMIN")
            .roleCodes(List.of("ADMIN"))
            .build());
    }

    @AfterEach
    void tearDown() {
        AuthContextHolder.clear();
    }

    @Test
    void reopenedApprovalInstanceExplicitlyClearsPersistedEndedAt() {
        ApprovalInstance instance = new ApprovalInstance();
        instance.setId(99L);
        instance.setEndedAt(null);

        ReflectionTestUtils.invokeMethod(service, "updateApprovalInstance", instance);

        verify(approvalInstanceMapper).updateById(instance);
        verify(approvalInstanceMapper).clearEndedAt(99L);
    }

    @Test
    void publishDemandPoolRequestsForTestSkipsNationalAggregatesReturnedByMapper() {
        PurchaseRequest aggregateCandidate = new PurchaseRequest();
        aggregateCandidate.setId(10L);
        aggregateCandidate.setReqNo("HZCG-000123");
        aggregateCandidate.setReqReason(NATIONAL_DEMAND_AGGREGATE_REASON);
        aggregateCandidate.setStatus(ProcurementWorkflow.STATUS_DEMAND_POOL);
        aggregateCandidate.setApprovalStatus(ProcurementWorkflow.APPROVAL_APPROVED);
        when(purchaseRequestMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(List.of(), List.of(aggregateCandidate));

        int publishedCount = service.publishDemandPoolRequestsForTest();

        assertThat(publishedCount).isZero();
        assertThat(aggregateCandidate.getStatus()).isEqualTo(ProcurementWorkflow.STATUS_DEMAND_POOL);
        verify(purchaseRequestItemMapper, never()).selectList(any(LambdaQueryWrapper.class));
        verify(purchaseRequestMapper, never()).updateById(aggregateCandidate);
        verify(purchaseRequestMapper, never()).insert(any(PurchaseRequest.class));
    }

    @Test
    void scheduledPublishDoesNotRequireInteractiveLogin() {
        AuthContextHolder.clear();
        when(purchaseRequestMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(List.of(), List.of());

        int publishedCount = service.publishDemandPoolRequestsOnSchedule();

        assertThat(publishedCount).isZero();
    }

    @Test
    void manualTestPublishStillRequiresInteractiveLogin() {
        AuthContextHolder.clear();

        assertThatThrownBy(() -> service.publishDemandPoolRequestsForTest())
            .isInstanceOf(BizException.class)
            .satisfies(ex -> assertThat(((BizException) ex).getCode()).isEqualTo("UNAUTHORIZED"));
    }

    @Test
    void publishDemandPoolRequestsForTestCreatesAggregateDirectlyForSupplierQuote() {
        PurchaseRequest sourceRequest = new PurchaseRequest();
        sourceRequest.setId(11L);
        sourceRequest.setReqNo("PTCG-100011");
        sourceRequest.setReqTitle("普通月度采购");
        sourceRequest.setReqType("服务/费用/低值易耗等");
        sourceRequest.setApplicantUserId(2L);
        sourceRequest.setPurchaserUserId(3L);
        sourceRequest.setRegionId(1L);
        sourceRequest.setDeptCode("D001");
        sourceRequest.setDeptName("M6机房");
        sourceRequest.setCurrency("CNY");
        sourceRequest.setNeedDate(LocalDate.of(2026, 7, 30));
        sourceRequest.setStatus(ProcurementWorkflow.STATUS_DEMAND_POOL);
        sourceRequest.setApprovalStatus(ProcurementWorkflow.APPROVAL_APPROVED);

        PurchaseRequestItem item = new PurchaseRequestItem();
        item.setId(101L);
        item.setPurchaseRequestId(sourceRequest.getId());
        item.setLineNo(1);
        item.setItemCode("MAT-001");
        item.setReqType("服务/费用/低值易耗等");
        item.setItemName("LED灯管");
        item.setSpecModel("T8 1.2米");
        item.setUnit("件");
        item.setQty(new BigDecimal("10"));
        item.setUnitPrice(new BigDecimal("100.00"));
        item.setLineAmount(new BigDecimal("1000.00"));
        item.setNeedDate(LocalDate.of(2026, 7, 30));

        SysUser admin = new SysUser();
        admin.setId(1L);
        admin.setUsername("admin");
        admin.setRealName("系统管理员");

        when(purchaseRequestMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(List.of(), List.of(sourceRequest));
        when(purchaseRequestItemMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(List.of(item));
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(admin);
        when(requestNoService.allocateAggregate()).thenReturn("HZCG-000002");

        int publishedCount = service.publishDemandPoolRequestsForTest();

        assertThat(publishedCount).isEqualTo(1);
        ArgumentCaptor<PurchaseRequest> aggregateCaptor = ArgumentCaptor.forClass(PurchaseRequest.class);
        verify(purchaseRequestMapper).insert(aggregateCaptor.capture());
        PurchaseRequest aggregate = aggregateCaptor.getValue();
        assertThat(aggregate.getReqNo()).isEqualTo("HZCG-000002");
        assertThat(aggregate.getStatus()).isEqualTo(ProcurementWorkflow.STATUS_QUOTING);
        assertThat(aggregate.getApprovalStatus()).isEqualTo(ProcurementWorkflow.APPROVAL_APPROVED);
        assertThat(aggregate.getCurrentNodeNo()).isZero();
        assertThat(aggregate.getApprovedAt()).isNotNull();

        ArgumentCaptor<ApprovalInstance> instanceCaptor = ArgumentCaptor.forClass(ApprovalInstance.class);
        verify(approvalInstanceMapper).insert(instanceCaptor.capture());
        ApprovalInstance instance = instanceCaptor.getValue();
        assertThat(instance.getStatus()).isEqualTo(ProcurementWorkflow.APPROVAL_APPROVED);
        assertThat(instance.getCurrentNodeNo()).isZero();
        assertThat(instance.getCurrentApproverId()).isNull();
        assertThat(instance.getEndedAt()).isNotNull();

        ArgumentCaptor<ApprovalRecord> recordCaptor = ArgumentCaptor.forClass(ApprovalRecord.class);
        verify(approvalRecordMapper).insert(recordCaptor.capture());
        ApprovalRecord record = recordCaptor.getValue();
        assertThat(record.getNodeName()).isEqualTo("需求池汇总直达报价");
        assertThat(record.getToStatus()).isEqualTo(ProcurementWorkflow.APPROVAL_APPROVED);
        assertThat(record.getIsFinalNode()).isEqualTo(1);
        assertThat(record.getTransferToUserId()).isNull();

        assertThat(sourceRequest.getStatus()).isEqualTo(ProcurementWorkflow.STATUS_DEMAND_AGGREGATED);
        verify(approvalTaskService, never()).createTasks(any(), any(), any());
    }

    @Test
    void nationalAggregateQuoteReviewStillUsesAmountThresholds() {
        PurchaseRequest sourceRequest = new PurchaseRequest();
        sourceRequest.setId(12L);
        BigDecimal highAmount = new BigDecimal("200000.00");
        when(approvalRouteResolver.totalQuoteApprovalNodes(sourceRequest, highAmount))
            .thenReturn(ProcurementWorkflow.NODE_DIRECTOR_REVIEW);

        Integer totalNodeNo = ReflectionTestUtils.invokeMethod(
            service,
            "totalNationalAggregateApprovalNodes",
            List.of(sourceRequest),
            highAmount,
            true
        );

        assertThat(totalNodeNo).isEqualTo(ProcurementWorkflow.NODE_DIRECTOR_REVIEW);
    }

    @Test
    void urgentDirectAwardDisabledKeepsEmergencyRequestInNormalQuoting() {
        PurchaseRequest request = new PurchaseRequest();
        request.setId(13L);
        request.setStatus(ProcurementWorkflow.STATUS_QUOTING);
        request.setUrgencyLevel(2);
        when(procurementSettingsService.getSettings()).thenReturn(settings(
            setting("urgent_direct_award_enabled", "false"),
            setting("urgent_direct_award_threshold", "1000")
        ));

        ReflectionTestUtils.invokeMethod(
            service,
            "applyUrgentDirectAwardIfEligible",
            request,
            new BigDecimal("999.00"),
            LocalDateTime.now()
        );

        assertThat(request.getDirectAwardApplied()).isZero();
        assertThat(request.getDirectAwardSupplierId()).isNull();
        verify(supplierQuoteItemMapper, never()).selectList(any(LambdaQueryWrapper.class));
        verify(supplierMapper, never()).selectList(any(LambdaQueryWrapper.class));
    }

    @Test
    void directAwardQuoteAboveLimitFallsBackToThreeQuoteReview() {
        PurchaseRequest request = new PurchaseRequest();
        request.setId(14L);
        request.setStatus(ProcurementWorkflow.STATUS_QUOTING);
        request.setApprovalStatus(ProcurementWorkflow.APPROVAL_APPROVED);
        request.setDirectAwardApplied(1);
        request.setDirectAwardSupplierId(1001L);
        request.setDirectAwardSupplierName("测试供应商");
        request.setDirectAwardThresholdAmount(new BigDecimal("1000.00"));

        SupplierQuote directQuote = new SupplierQuote();
        directQuote.setId(2001L);
        directQuote.setPurchaseRequestId(request.getId());
        directQuote.setSupplierId(1001L);
        directQuote.setStatus(ProcurementWorkflow.QUOTE_STATUS_SUBMITTED);
        directQuote.setTotalAmount(new BigDecimal("1300.01"));
        directQuote.setSubmittedAt(LocalDateTime.now());

        PurchaseRequestItem requestItem = quotedRequestItem(request.getId(), 1401L, new BigDecimal("1"));
        SupplierQuoteItem quoteItem = quotedSupplierItem(request.getId(), requestItem, 2401L);
        quoteItem.setQuoteId(directQuote.getId());
        quoteItem.setLineAmount(directQuote.getTotalAmount());
        quoteItem.setLeadTimeDays(7);
        when(supplierQuoteMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(List.of(directQuote), List.of(directQuote), List.of());
        when(purchaseRequestItemMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(requestItem));
        when(supplierQuoteItemMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(quoteItem));
        when(temporarySupplierRecommendationService.isQuoteAwardEligible(directQuote)).thenReturn(true);

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
            service,
            "submitQuoteReview",
            request,
            null,
            "测试报价复审",
            1L,
            "SYSTEM"
        ))
            .isInstanceOf(BizException.class)
            .satisfies(ex -> assertThat(((BizException) ex).getCode()).isEqualTo("QUOTE_COUNT_NOT_ENOUGH"));

        assertThat(request.getDirectAwardApplied()).isZero();
        assertThat(request.getDirectAwardSupplierId()).isNull();
        assertThat(request.getDirectAwardReason()).isNull();
        assertThat(request.getRemark()).contains("转入三方比价");
        verify(purchaseRequestMapper).updateById(request);
        verify(approvalTaskService, never()).createTasks(any(), any(), any());
    }

    @Test
    void replacementOriginalPriceUsesSecondHighestPeerPricePlusOne() {
        Long requestId = 18L;
        Long requestItemId = 1801L;
        SupplierQuote replacementQuote = submittedQuote(2801L, requestId, new BigDecimal("50.02"));
        SupplierQuote highQuote = submittedQuote(2802L, requestId, new BigDecimal("240.00"));
        SupplierQuote middleQuote = submittedQuote(2803L, requestId, new BigDecimal("200.00"));
        SupplierQuote lowQuote = submittedQuote(2804L, requestId, new BigDecimal("160.00"));

        SupplierQuoteItem replacementItem = pricedQuoteItem(
            3801L,
            replacementQuote.getId(),
            requestId,
            requestItemId,
            new BigDecimal("2"),
            new BigDecimal("0.01"),
            1
        );
        SupplierQuoteItem retainedItem = pricedQuoteItem(
            3802L,
            replacementQuote.getId(),
            requestId,
            1802L,
            BigDecimal.ONE,
            new BigDecimal("50.00"),
            0
        );
        SupplierQuoteItem highItem = pricedQuoteItem(
            3803L,
            highQuote.getId(),
            requestId,
            requestItemId,
            new BigDecimal("2"),
            new BigDecimal("120.00"),
            0
        );
        SupplierQuoteItem middleItem = pricedQuoteItem(
            3804L,
            middleQuote.getId(),
            requestId,
            requestItemId,
            new BigDecimal("2"),
            new BigDecimal("100.00"),
            0
        );
        SupplierQuoteItem lowItem = pricedQuoteItem(
            3805L,
            lowQuote.getId(),
            requestId,
            requestItemId,
            new BigDecimal("2"),
            new BigDecimal("80.00"),
            0
        );
        when(supplierQuoteItemMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(List.of(replacementItem, retainedItem, highItem, middleItem, lowItem));

        ReflectionTestUtils.invokeMethod(
            service,
            "adjustReplacementOriginalPrices",
            requestId,
            List.of(replacementQuote, highQuote, middleQuote, lowQuote)
        );

        assertThat(replacementItem.getSupplierSubmittedUnitPrice()).isEqualByComparingTo("0.01");
        assertThat(replacementItem.getUnitPrice()).isEqualByComparingTo("101.00");
        assertThat(replacementItem.getLineAmount()).isEqualByComparingTo("202.00");
        assertThat(replacementQuote.getTotalAmount()).isEqualByComparingTo("252.00");
        verify(supplierQuoteItemMapper).updateById(replacementItem);
        verify(supplierQuoteMapper).updateById(replacementQuote);
    }

    @Test
    void replacementOriginalPriceWithOnePeerUsesPeerPricePlusOne() {
        Long requestId = 19L;
        Long requestItemId = 1901L;
        SupplierQuote replacementQuote = submittedQuote(2901L, requestId, new BigDecimal("0.01"));
        SupplierQuote peerQuote = submittedQuote(2902L, requestId, new BigDecimal("88.00"));
        SupplierQuoteItem replacementItem = pricedQuoteItem(
            3901L,
            replacementQuote.getId(),
            requestId,
            requestItemId,
            BigDecimal.ONE,
            new BigDecimal("0.01"),
            1
        );
        SupplierQuoteItem peerItem = pricedQuoteItem(
            3902L,
            peerQuote.getId(),
            requestId,
            requestItemId,
            BigDecimal.ONE,
            new BigDecimal("88.00"),
            0
        );
        when(supplierQuoteItemMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(List.of(replacementItem, peerItem));

        ReflectionTestUtils.invokeMethod(
            service,
            "adjustReplacementOriginalPrices",
            requestId,
            List.of(replacementQuote, peerQuote)
        );

        assertThat(replacementItem.getUnitPrice()).isEqualByComparingTo("89.00");
        assertThat(replacementQuote.getTotalAmount()).isEqualByComparingTo("89.00");
    }

    @Test
    void replacementOriginalPriceWithoutPeerKeepsSupplierSubmission() {
        Long requestId = 20L;
        SupplierQuote replacementQuote = submittedQuote(3001L, requestId, new BigDecimal("0.01"));
        SupplierQuoteItem replacementItem = pricedQuoteItem(
            4001L,
            replacementQuote.getId(),
            requestId,
            2001L,
            BigDecimal.ONE,
            new BigDecimal("0.01"),
            1
        );
        when(supplierQuoteItemMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(List.of(replacementItem));

        ReflectionTestUtils.invokeMethod(
            service,
            "adjustReplacementOriginalPrices",
            requestId,
            List.of(replacementQuote)
        );

        assertThat(replacementItem.getUnitPrice()).isEqualByComparingTo("0.01");
        assertThat(replacementQuote.getTotalAmount()).isEqualByComparingTo("0.01");
        verify(supplierQuoteItemMapper, never()).updateById(any(SupplierQuoteItem.class));
        verify(supplierQuoteMapper, never()).updateById(any(SupplierQuote.class));
    }

    @Test
    void quoteReviewItemRejectClearsAwardedQuoteItemForRejectedRequestItem() {
        PurchaseRequest request = new PurchaseRequest();
        request.setId(15L);

        PurchaseRequestItem activeItem = new PurchaseRequestItem();
        activeItem.setId(1501L);
        activeItem.setPurchaseRequestId(request.getId());
        activeItem.setStatus(ProcurementWorkflow.ITEM_STATUS_ACTIVE);

        SupplierQuoteItem activeQuoteItem = new SupplierQuoteItem();
        activeQuoteItem.setId(2501L);
        activeQuoteItem.setPurchaseRequestId(request.getId());
        activeQuoteItem.setPurchaseRequestItemId(activeItem.getId());
        activeQuoteItem.setAwarded(1);
        activeQuoteItem.setStatus("ACTIVE");

        SupplierQuoteItem rejectedQuoteItem = new SupplierQuoteItem();
        rejectedQuoteItem.setId(2502L);
        rejectedQuoteItem.setPurchaseRequestId(request.getId());
        rejectedQuoteItem.setPurchaseRequestItemId(1502L);
        rejectedQuoteItem.setAwarded(1);
        rejectedQuoteItem.setStatus("ACTIVE");
        rejectedQuoteItem.setReplacementFlag(1);
        rejectedQuoteItem.setReplacementReviewStatus("PENDING");

        when(purchaseRequestItemMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(activeItem));
        when(supplierQuoteItemMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(List.of(activeQuoteItem, rejectedQuoteItem));

        ReflectionTestUtils.invokeMethod(service, "syncAwardedQuoteItemsWithActiveRequestItems", request);

        assertThat(activeQuoteItem.getAwarded()).isEqualTo(1);
        assertThat(rejectedQuoteItem.getAwarded()).isZero();
        assertThat(rejectedQuoteItem.getReplacementReviewStatus()).isNull();
        verify(supplierQuoteItemMapper, never()).updateById(activeQuoteItem);
        verify(supplierQuoteItemMapper).updateById(rejectedQuoteItem);
    }

    @Test
    void returnedRequestRebindsUnchangedQuoteItemToCurrentRequestItem() {
        PurchaseRequest request = new PurchaseRequest();
        request.setId(36L);
        PurchaseRequestItem currentItem = quotedRequestItem(request.getId(), 323L, new BigDecimal("5"));
        SupplierQuoteItem staleQuoteItem = quotedSupplierItem(request.getId(), currentItem, 426L);
        staleQuoteItem.setPurchaseRequestItemId(319L);

        when(purchaseRequestItemMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(currentItem));
        when(supplierQuoteItemMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(staleQuoteItem));

        ReflectionTestUtils.invokeMethod(service, "reconcileQuoteItemsWithActiveRequestItems", request);

        assertThat(staleQuoteItem.getPurchaseRequestItemId()).isEqualTo(currentItem.getId());
        verify(supplierQuoteItemMapper).updateById(staleQuoteItem);
    }

    @Test
    void returnedRequestDoesNotReuseQuoteWhenQuantityChanged() {
        PurchaseRequest request = new PurchaseRequest();
        request.setId(37L);
        PurchaseRequestItem currentItem = quotedRequestItem(request.getId(), 333L, new BigDecimal("10"));
        SupplierQuoteItem staleQuoteItem = quotedSupplierItem(request.getId(), currentItem, 436L);
        staleQuoteItem.setPurchaseRequestItemId(329L);
        staleQuoteItem.setQty(new BigDecimal("5"));

        when(purchaseRequestItemMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(currentItem));
        when(supplierQuoteItemMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(staleQuoteItem));

        ReflectionTestUtils.invokeMethod(service, "reconcileQuoteItemsWithActiveRequestItems", request);

        assertThat(staleQuoteItem.getPurchaseRequestItemId()).isEqualTo(329L);
        assertThat(staleQuoteItem.getStatus()).isEqualTo("INACTIVE");
        verify(supplierQuoteItemMapper).updateById(staleQuoteItem);
    }

    @Test
    void quoteCompletenessRejectsStaleRequestItemLinksEvenWhenLineCountsMatch() {
        PurchaseRequest request = new PurchaseRequest();
        request.setId(38L);
        PurchaseRequestItem currentItem = quotedRequestItem(request.getId(), 343L, new BigDecimal("5"));
        SupplierQuote quote = new SupplierQuote();
        quote.setId(2038L);
        SupplierQuoteItem staleQuoteItem = quotedSupplierItem(request.getId(), currentItem, 446L);
        staleQuoteItem.setPurchaseRequestItemId(339L);

        when(purchaseRequestItemMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(currentItem));
        when(supplierQuoteItemMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(staleQuoteItem));

        Boolean complete = ReflectionTestUtils.invokeMethod(service, "hasCompleteActiveQuoteItems", request, quote);

        assertThat(complete).isFalse();
    }

    @Test
    void quoteReviewAllowsComparisonWhenOneSupplierWaivesAnItem() {
        PurchaseRequest request = new PurchaseRequest();
        request.setId(41L);
        PurchaseRequestItem requestItem = quotedRequestItem(request.getId(), 413L, new BigDecimal("2"));

        SupplierQuote firstQuote = submittedQuote(2101L);
        SupplierQuote secondQuote = submittedQuote(2102L);
        SupplierQuote waivedQuote = submittedQuote(2103L);
        SupplierQuoteItem firstQuoteItem = quotedSupplierItem(request.getId(), requestItem, 5101L);
        firstQuoteItem.setQuoteId(firstQuote.getId());
        SupplierQuoteItem secondQuoteItem = quotedSupplierItem(request.getId(), requestItem, 5102L);
        secondQuoteItem.setQuoteId(secondQuote.getId());

        when(purchaseRequestItemMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(requestItem));
        when(supplierQuoteItemMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(List.of(firstQuoteItem, secondQuoteItem));

        ReflectionTestUtils.invokeMethod(
            service,
            "ensureEveryActiveItemHasQuote",
            request,
            List.of(firstQuote, secondQuote, waivedQuote)
        );
    }

    @Test
    void quoteReviewStillRejectsAnActiveItemWithoutAnySupplierQuote() {
        PurchaseRequest request = new PurchaseRequest();
        request.setId(42L);
        PurchaseRequestItem requestItem = quotedRequestItem(request.getId(), 423L, new BigDecimal("1"));

        when(purchaseRequestItemMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(requestItem));
        when(supplierQuoteItemMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
            service,
            "ensureEveryActiveItemHasQuote",
            request,
            List.of(submittedQuote(2201L), submittedQuote(2202L), submittedQuote(2203L))
        ))
            .isInstanceOf(BizException.class)
            .satisfies(ex -> assertThat(((BizException) ex).getCode()).isEqualTo("QUOTE_ITEM_MISSING"));
    }

    @Test
    void returnedRequestRemovesDeletedItemFromSubmittedQuoteAndRecalculatesTotal() {
        PurchaseRequest request = new PurchaseRequest();
        request.setId(39L);
        PurchaseRequestItem currentItem = quotedRequestItem(request.getId(), 353L, new BigDecimal("6"));

        SupplierQuoteItem retainedItem = quotedSupplierItem(request.getId(), currentItem, 456L);
        retainedItem.setQuoteId(2039L);
        retainedItem.setPurchaseRequestItemId(349L);
        retainedItem.setLineAmount(new BigDecimal("2280.00"));
        retainedItem.setLeadTimeDays(7);

        SupplierQuoteItem deletedItem = quotedSupplierItem(request.getId(), currentItem, 457L);
        deletedItem.setQuoteId(2039L);
        deletedItem.setPurchaseRequestItemId(350L);
        deletedItem.setItemCode("MAT-DELETED");
        deletedItem.setItemName("Deleted item");
        deletedItem.setLineAmount(new BigDecimal("75.20"));
        deletedItem.setLeadTimeDays(3);

        SupplierQuote quote = new SupplierQuote();
        quote.setId(2039L);
        quote.setPurchaseRequestId(request.getId());
        quote.setStatus(ProcurementWorkflow.QUOTE_STATUS_SUBMITTED);
        quote.setTotalAmount(new BigDecimal("2355.20"));
        quote.setLeadTimeDays(7);

        when(purchaseRequestItemMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(currentItem));
        when(supplierQuoteItemMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(List.of(retainedItem, deletedItem));
        when(supplierQuoteMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(quote));

        ReflectionTestUtils.invokeMethod(service, "reconcileQuoteItemsWithActiveRequestItems", request);

        assertThat(retainedItem.getPurchaseRequestItemId()).isEqualTo(currentItem.getId());
        assertThat(deletedItem.getStatus()).isEqualTo("INACTIVE");
        assertThat(quote.getStatus()).isEqualTo(ProcurementWorkflow.QUOTE_STATUS_SUBMITTED);
        assertThat(quote.getTotalAmount()).isEqualByComparingTo("2280.00");
        verify(supplierQuoteMapper).updateById(quote);
    }

    @Test
    void returnedRequestRequiresNewQuoteWhenCurrentItemChanged() {
        PurchaseRequest request = new PurchaseRequest();
        request.setId(40L);
        PurchaseRequestItem currentItem = quotedRequestItem(request.getId(), 363L, new BigDecimal("10"));
        SupplierQuoteItem staleItem = quotedSupplierItem(request.getId(), currentItem, 466L);
        staleItem.setQuoteId(2040L);
        staleItem.setPurchaseRequestItemId(359L);
        staleItem.setQty(new BigDecimal("5"));
        staleItem.setLineAmount(new BigDecimal("100.00"));

        SupplierQuote quote = new SupplierQuote();
        quote.setId(2040L);
        quote.setPurchaseRequestId(request.getId());
        quote.setStatus(ProcurementWorkflow.QUOTE_STATUS_SUBMITTED);
        quote.setTotalAmount(new BigDecimal("100.00"));

        when(purchaseRequestItemMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(currentItem));
        when(supplierQuoteItemMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(staleItem));
        when(supplierQuoteMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(quote));

        ReflectionTestUtils.invokeMethod(service, "reconcileQuoteItemsWithActiveRequestItems", request);

        assertThat(staleItem.getStatus()).isEqualTo("INACTIVE");
        assertThat(quote.getStatus()).isEqualTo(ProcurementWorkflow.QUOTE_STATUS_SUBMITTED);
        assertThat(quote.getTotalAmount()).isEqualByComparingTo("0.00");
        verify(supplierQuoteMapper).updateById(quote);
    }

    private PurchaseRequestItem quotedRequestItem(Long requestId, Long itemId, BigDecimal qty) {
        PurchaseRequestItem item = new PurchaseRequestItem();
        item.setId(itemId);
        item.setPurchaseRequestId(requestId);
        item.setLineNo(1);
        item.setItemCode("MAT-VALVE-01");
        item.setItemName("Brass ball valve");
        item.setSpecModel("DN15");
        item.setUnit("piece");
        item.setQty(qty);
        item.setStatus(ProcurementWorkflow.ITEM_STATUS_ACTIVE);
        return item;
    }

    private SupplierQuoteItem quotedSupplierItem(
        Long requestId,
        PurchaseRequestItem requestItem,
        Long quoteItemId
    ) {
        SupplierQuoteItem item = new SupplierQuoteItem();
        item.setId(quoteItemId);
        item.setPurchaseRequestId(requestId);
        item.setPurchaseRequestItemId(requestItem.getId());
        item.setLineNo(requestItem.getLineNo());
        item.setItemCode(requestItem.getItemCode());
        item.setItemName(requestItem.getItemName());
        item.setSpecModel(requestItem.getSpecModel());
        item.setUnit(requestItem.getUnit());
        item.setQty(requestItem.getQty());
        item.setStatus("ACTIVE");
        return item;
    }

    private SupplierQuote submittedQuote(Long quoteId, Long requestId, BigDecimal totalAmount) {
        SupplierQuote quote = new SupplierQuote();
        quote.setId(quoteId);
        quote.setPurchaseRequestId(requestId);
        quote.setStatus(ProcurementWorkflow.QUOTE_STATUS_SUBMITTED);
        quote.setTotalAmount(totalAmount);
        return quote;
    }

    private SupplierQuoteItem pricedQuoteItem(
        Long itemId,
        Long quoteId,
        Long requestId,
        Long requestItemId,
        BigDecimal qty,
        BigDecimal unitPrice,
        int replacementFlag
    ) {
        SupplierQuoteItem item = new SupplierQuoteItem();
        item.setId(itemId);
        item.setQuoteId(quoteId);
        item.setPurchaseRequestId(requestId);
        item.setPurchaseRequestItemId(requestItemId);
        item.setQty(qty);
        item.setUnitPrice(unitPrice);
        item.setSupplierSubmittedUnitPrice(unitPrice);
        item.setLineAmount(qty.multiply(unitPrice).setScale(2));
        item.setReplacementFlag(replacementFlag);
        item.setStatus("ACTIVE");
        return item;
    }

    private SupplierQuote submittedQuote(Long quoteId) {
        SupplierQuote quote = new SupplierQuote();
        quote.setId(quoteId);
        quote.setStatus(ProcurementWorkflow.QUOTE_STATUS_SUBMITTED);
        return quote;
    }

    @Test
    void overPriceReviewStartsAtFloorLeaderWithDedicatedLevelCode() {
        PurchaseRequest request = new PurchaseRequest();
        request.setId(16L);
        ProcurementApprovalRouteResolver.ApprovalAssignment floorAssignment =
            new ProcurementApprovalRouteResolver.ApprovalAssignment(
                301L,
                List.of(301L, 302L),
                "楼长甲,楼长乙",
                true,
                1L,
                3L,
                ProcurementWorkflow.NODE_FLOOR_LEADER_DEMAND_REVIEW,
                "V3",
                "楼长审核",
                ProcurementApprovalRouteResolver.PASS_RULE_ALL,
                true
            );
        when(overPriceReviewService.requiresReview(request)).thenReturn(true);
        when(approvalRouteResolver.resolveAssignmentForNode(
            request,
            ProcurementWorkflow.NODE_FLOOR_LEADER_DEMAND_REVIEW
        )).thenReturn(floorAssignment);

        ProcurementApprovalRouteResolver.ApprovalAssignment assignment = ReflectionTestUtils.invokeMethod(
            service,
            "resolveInitialQuoteReviewAssignment",
            request,
            6
        );

        assertThat(assignment).isNotNull();
        assertThat(assignment.levelNo()).isEqualTo(ProcurementWorkflow.NODE_FLOOR_LEADER_DEMAND_REVIEW);
        assertThat(assignment.levelCode()).isEqualTo(ProcurementWorkflow.LEVEL_CODE_OVER_PRICE_REVIEW);
        assertThat(assignment.passRule()).isEqualTo(ProcurementApprovalRouteResolver.PASS_RULE_ANY_ONE);
        assertThat(assignment.candidateUserIds()).containsExactly(301L, 302L);
    }

    @Test
    void overPriceReviewRejectsGenericPassCommentAndNonFlaggedItemDecision() {
        PurchaseRequest request = new PurchaseRequest();
        request.setId(17L);
        when(overPriceReviewService.requiredReviewItemIds(request)).thenReturn(Set.of(1701L));

        WorkflowApprovalActionDTO genericComment = new WorkflowApprovalActionDTO();
        genericComment.setApprovalComment("同意");
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
            service,
            "validateOverPriceReviewDecision",
            request,
            genericComment
        )).isInstanceOf(BizException.class)
            .satisfies(ex -> assertThat(((BizException) ex).getCode()).isEqualTo("OVER_PRICE_REVIEW_COMMENT_REQUIRED"));

        WorkflowApprovalItemDecisionDTO invalidDecision = new WorkflowApprovalItemDecisionDTO();
        invalidDecision.setItemId(1702L);
        invalidDecision.setRejected(true);
        invalidDecision.setRejectReason("无需采购");
        WorkflowApprovalActionDTO nonFlaggedItem = new WorkflowApprovalActionDTO();
        nonFlaggedItem.setApprovalComment("近期市场价上涨，仍需按期采购");
        nonFlaggedItem.setItemDecisions(List.of(invalidDecision));
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
            service,
            "validateOverPriceReviewDecision",
            request,
            nonFlaggedItem
        )).isInstanceOf(BizException.class)
            .satisfies(ex -> assertThat(((BizException) ex).getCode()).isEqualTo("OVER_PRICE_REVIEW_ITEM_INVALID"));
    }

    private ProcurementSettingsVO settings(ProcurementSettingItemVO... settings) {
        ProcurementSettingsVO vo = new ProcurementSettingsVO();
        vo.setSettings(List.of(settings));
        return vo;
    }

    private ProcurementSettingItemVO setting(String key, String value) {
        ProcurementSettingItemVO vo = new ProcurementSettingItemVO();
        vo.setSettingKey(key);
        vo.setSettingValue(value);
        return vo;
    }
}
