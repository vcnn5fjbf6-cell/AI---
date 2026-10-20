package com.company.procurement.modules.supplier.service.impl;

import com.company.procurement.common.exception.BizException;
import com.company.procurement.common.security.AuthContextHolder;
import com.company.procurement.common.security.AuthUser;
import com.company.procurement.modules.procurement.asset.service.AssetCatalogService;
import com.company.procurement.modules.procurement.asset.service.QuoteAssetClassificationService;
import com.company.procurement.modules.procurement.request.entity.PurchaseRequest;
import com.company.procurement.modules.procurement.request.mapper.PurchaseRequestItemMapper;
import com.company.procurement.modules.procurement.request.mapper.PurchaseRequestMapper;
import com.company.procurement.modules.procurement.request.service.EcommerceUrlService;
import com.company.procurement.modules.procurement.request.service.PurchaseRequestService;
import com.company.procurement.modules.procurement.request.vo.PurchaseRequestVO;
import com.company.procurement.modules.procurement.route.service.ProcurementApprovalRouteResolver;
import com.company.procurement.modules.procurement.settings.mapper.ProcurementSettingMapper;
import com.company.procurement.modules.procurement.workflow.ProcurementWorkflow;
import com.company.procurement.modules.procurement.workflow.service.AwardPriceSyncService;
import com.company.procurement.modules.supplier.mapper.SupplierMapper;
import com.company.procurement.modules.supplier.mapper.SupplierQuoteItemMapper;
import com.company.procurement.modules.supplier.mapper.SupplierQuoteMapper;
import com.company.procurement.modules.supplier.service.SupplierAwardNotificationService;
import com.company.procurement.modules.supplier.service.SupplierProductLinkService;
import com.company.procurement.modules.supplier.service.TemporarySupplierRecommendationService;
import com.company.procurement.modules.supplier.support.SupplierQuoteAccess;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupplierQuoteServiceImplViewAllTest {

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
    @Mock
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

    @AfterEach
    void tearDown() {
        AuthContextHolder.clear();
    }

    @Test
    void viewAllPermissionAllowsReadingAnotherDirectAwardRequest() {
        PurchaseRequest request = directAwardRequest();
        PurchaseRequestVO detail = new PurchaseRequestVO();
        detail.setId(request.getId());
        detail.setStatus(request.getStatus());
        detail.setTotalAmount(new BigDecimal("1234.56"));
        when(purchaseRequestMapper.selectById(request.getId())).thenReturn(request);
        when(purchaseRequestService.detail(request.getId())).thenReturn(detail);
        AuthContextHolder.set(unboundSupervisor());

        PurchaseRequestVO result = service.quoteRequestDetail(request.getId());

        assertThat(result.getId()).isEqualTo(request.getId());
        assertThat(result.getTotalAmount()).isEqualByComparingTo("1234.56");
    }

    @Test
    void ordinarySupplierCannotReadAnotherDirectAwardRequest() {
        PurchaseRequest request = directAwardRequest();
        when(purchaseRequestMapper.selectById(request.getId())).thenReturn(request);
        AuthContextHolder.set(supplierUser(List.of()));

        assertThatThrownBy(() -> service.quoteRequestDetail(request.getId()))
            .isInstanceOf(BizException.class)
            .hasMessageContaining("not open for quotation");
    }

    private PurchaseRequest directAwardRequest() {
        PurchaseRequest request = new PurchaseRequest();
        request.setId(77L);
        request.setStatus(ProcurementWorkflow.STATUS_QUOTING);
        request.setDirectAwardApplied(1);
        request.setDirectAwardSupplierId(999L);
        return request;
    }

    private AuthUser supplierUser(List<String> permissions) {
        return AuthUser.builder()
            .userId(10L)
            .userType("SUPPLIER")
            .supplierId(100L)
            .regionId(1L)
            .roleCodes(List.of("SUPPLIER"))
            .permissionCodes(permissions)
            .build();
    }

    private AuthUser unboundSupervisor() {
        return AuthUser.builder()
            .userId(10L)
            .userType("SUPPLIER_QUOTE_VIEWER")
            .regionId(1L)
            .roleCodes(List.of("SUPPLIER_QUOTE_VIEWER"))
            .permissionCodes(List.of(SupplierQuoteAccess.VIEW_ALL_SUBMISSIONS_PERMISSION))
            .build();
    }
}
