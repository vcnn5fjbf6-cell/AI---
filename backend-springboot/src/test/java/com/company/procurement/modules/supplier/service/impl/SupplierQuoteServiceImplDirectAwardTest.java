package com.company.procurement.modules.supplier.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.procurement.modules.procurement.request.entity.PurchaseRequest;
import com.company.procurement.modules.procurement.asset.service.AssetCatalogService;
import com.company.procurement.modules.procurement.asset.service.QuoteAssetClassificationService;
import com.company.procurement.modules.procurement.request.mapper.PurchaseRequestItemMapper;
import com.company.procurement.modules.procurement.request.mapper.PurchaseRequestMapper;
import com.company.procurement.modules.procurement.request.service.EcommerceUrlService;
import com.company.procurement.modules.procurement.request.service.PurchaseRequestService;
import com.company.procurement.modules.procurement.route.service.ProcurementApprovalRouteResolver;
import com.company.procurement.modules.procurement.settings.mapper.ProcurementSettingMapper;
import com.company.procurement.modules.procurement.workflow.ProcurementWorkflow;
import com.company.procurement.modules.procurement.workflow.service.AwardPriceSyncService;
import com.company.procurement.modules.supplier.entity.SupplierQuote;
import com.company.procurement.modules.supplier.entity.SupplierQuoteItem;
import com.company.procurement.modules.supplier.mapper.SupplierMapper;
import com.company.procurement.modules.supplier.mapper.SupplierQuoteItemMapper;
import com.company.procurement.modules.supplier.mapper.SupplierQuoteMapper;
import com.company.procurement.modules.supplier.service.SupplierAwardNotificationService;
import com.company.procurement.modules.supplier.service.SupplierProductLinkService;
import com.company.procurement.modules.supplier.service.TemporarySupplierRecommendationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupplierQuoteServiceImplDirectAwardTest {

    @Mock
    private PurchaseRequestMapper purchaseRequestMapper;
    @Mock
    private PurchaseRequestItemMapper purchaseRequestItemMapper;
    @Mock
    private PurchaseRequestService purchaseRequestService;
    @Mock
    private SupplierQuoteMapper supplierQuoteMapper;
    @Mock
    private SupplierQuoteItemMapper supplierQuoteItemMapper;
    @Mock
    private SupplierMapper supplierMapper;
    @Mock
    private ProcurementApprovalRouteResolver approvalRouteResolver;
    @Mock
    private ProcurementSettingMapper procurementSettingMapper;
    @Mock
    private SupplierAwardNotificationService awardNotificationService;
    @Mock
    private TemporarySupplierRecommendationService temporarySupplierRecommendationService;
    @Mock
    private AwardPriceSyncService awardPriceSyncService;
    private AssetCatalogService assetCatalogService;
    @Mock
    private QuoteAssetClassificationService quoteAssetClassificationService;
    @Mock
    private EcommerceUrlService ecommerceUrlService;

    private SupplierQuoteServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SupplierQuoteServiceImpl(
            purchaseRequestMapper,
            purchaseRequestItemMapper,
            purchaseRequestService,
            supplierQuoteMapper,
            supplierQuoteItemMapper,
            supplierMapper,
            approvalRouteResolver,
            procurementSettingMapper,
            awardNotificationService,
            temporarySupplierRecommendationService,
            awardPriceSyncService,
            assetCatalogService,
            quoteAssetClassificationService,
            new SupplierProductLinkService(ecommerceUrlService)
        );
    }

    @Test
    void directAwardQuoteAboveLimitClearsDirectAwardForThreeQuoteStage() {
        PurchaseRequest request = directAwardRequest();
        SupplierQuote quote = directQuote(new BigDecimal("1300.01"));

        ReflectionTestUtils.invokeMethod(
            service,
            "downgradeDirectAwardIfQuoteOverLimit",
            request,
            quote,
            quote.getTotalAmount()
        );

        assertThat(request.getDirectAwardApplied()).isZero();
        assertThat(request.getDirectAwardSupplierId()).isNull();
        assertThat(request.getDirectAwardThresholdAmount()).isNull();
        assertThat(request.getQuoteCountdownStartedAt()).isNull();
        assertThat(request.getQuoteCountdownDueAt()).isNull();
        assertThat(request.getRemark()).contains("转入三方比价");
        verify(purchaseRequestMapper).updateById(request);
    }

    @Test
    void directAwardQuoteAtLimitKeepsDirectAward() {
        PurchaseRequest request = directAwardRequest();
        SupplierQuote quote = directQuote(new BigDecimal("1300.00"));

        ReflectionTestUtils.invokeMethod(
            service,
            "downgradeDirectAwardIfQuoteOverLimit",
            request,
            quote,
            quote.getTotalAmount()
        );

        assertThat(request.getDirectAwardApplied()).isEqualTo(1);
        assertThat(request.getDirectAwardSupplierId()).isEqualTo(1001L);
        verify(purchaseRequestMapper, never()).updateById(any(PurchaseRequest.class));
        verify(procurementSettingMapper, never()).selectOne(any(LambdaQueryWrapper.class));
    }

    @Test
    void awardedItemsStartPendingWithoutAutomaticReceiptDeadline() {
        SupplierQuote quote = new SupplierQuote();
        quote.setId(2001L);
        SupplierQuoteItem item = new SupplierQuoteItem();
        item.setId(3001L);
        item.setReceiptDueAt(LocalDateTime.now().plusDays(7));
        item.setReceivedBy(0L);
        item.setReceivedAt(LocalDateTime.now());
        item.setReceiptRemark("系统自动收货");
        item.setAutoReceived(1);
        when(supplierQuoteItemMapper.selectList(any())).thenReturn(List.of(item));

        ReflectionTestUtils.invokeMethod(service, "initializeReceiptForAwardedItems", quote);

        assertThat(item.getReceiptStatus()).isEqualTo("PENDING");
        assertThat(item.getReceiptDueAt()).isNull();
        assertThat(item.getReceivedBy()).isNull();
        assertThat(item.getReceivedAt()).isNull();
        assertThat(item.getReceiptRemark()).isNull();
        assertThat(item.getAutoReceived()).isZero();
        verify(supplierQuoteItemMapper).updateById(item);
        verify(procurementSettingMapper, never()).selectOne(any(LambdaQueryWrapper.class));
    }

    private PurchaseRequest directAwardRequest() {
        PurchaseRequest request = new PurchaseRequest();
        request.setId(1L);
        request.setStatus(ProcurementWorkflow.STATUS_QUOTING);
        request.setDirectAwardApplied(1);
        request.setDirectAwardSupplierId(1001L);
        request.setDirectAwardSupplierName("测试供应商");
        request.setDirectAwardThresholdAmount(new BigDecimal("1000.00"));
        return request;
    }

    private SupplierQuote directQuote(BigDecimal amount) {
        SupplierQuote quote = new SupplierQuote();
        quote.setId(2001L);
        quote.setPurchaseRequestId(1L);
        quote.setSupplierId(1001L);
        quote.setTotalAmount(amount);
        return quote;
    }
}
