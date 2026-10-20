package com.company.procurement.modules.supplier.service.impl;

import com.company.procurement.common.exception.BizException;
import com.company.procurement.modules.procurement.asset.service.AssetCatalogService;
import com.company.procurement.modules.procurement.asset.service.QuoteAssetClassificationService;
import com.company.procurement.modules.procurement.request.mapper.PurchaseRequestItemMapper;
import com.company.procurement.modules.procurement.request.mapper.PurchaseRequestMapper;
import com.company.procurement.modules.procurement.request.service.EcommerceUrlService;
import com.company.procurement.modules.procurement.request.service.PurchaseRequestService;
import com.company.procurement.modules.procurement.route.service.ProcurementApprovalRouteResolver;
import com.company.procurement.modules.procurement.settings.mapper.ProcurementSettingMapper;
import com.company.procurement.modules.procurement.workflow.service.AwardPriceSyncService;
import com.company.procurement.modules.supplier.dto.SupplierQuoteItemSubmitDTO;
import com.company.procurement.modules.supplier.entity.Supplier;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupplierQuoteServiceImplReplacementValidationTest {

    @Mock private PurchaseRequestMapper purchaseRequestMapper;
    @Mock private PurchaseRequestItemMapper purchaseRequestItemMapper;
    @Mock private PurchaseRequestService purchaseRequestService;
    @Mock private SupplierQuoteMapper supplierQuoteMapper;
    @Mock private SupplierQuoteItemMapper supplierQuoteItemMapper;
    @Mock private SupplierMapper supplierMapper;
    @Mock private ProcurementApprovalRouteResolver approvalRouteResolver;
    @Mock private ProcurementSettingMapper procurementSettingMapper;
    @Mock private SupplierAwardNotificationService awardNotificationService;
    @Mock private TemporarySupplierRecommendationService temporarySupplierRecommendationService;
    @Mock private AwardPriceSyncService awardPriceSyncService;
    @Mock private AssetCatalogService assetCatalogService;
    @Mock private QuoteAssetClassificationService quoteAssetClassificationService;

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
            new SupplierProductLinkService(new EcommerceUrlService())
        );
    }

    @Test
    void replacementRequiresSpecification() {
        SupplierQuoteItemSubmitDTO item = validReplacement();
        item.setReplacementSpecModel(" ");

        assertValidationCode(item, "QUOTE_ITEM_REPLACEMENT_SPEC_REQUIRED");
    }

    @Test
    void replacementRequiresProductLink() {
        SupplierQuoteItemSubmitDTO item = validReplacement();
        item.setReplacementEcommerceUrl(null);

        assertValidationCode(item, "QUOTE_ITEM_REPLACEMENT_URL_REQUIRED");
    }

    @Test
    void replacementRequiresReason() {
        SupplierQuoteItemSubmitDTO item = validReplacement();
        item.setReplacementReason("");

        assertValidationCode(item, "QUOTE_ITEM_REPLACEMENT_REASON_REQUIRED");
    }

    @Test
    void replacementNormalizesProductLinkBeforeSave() {
        SupplierQuoteItemSubmitDTO item = validReplacement();
        item.setReplacementEcommerceUrl("https://item.m.jd.com/product/100123.html?utm_source=test");
        when(quoteAssetClassificationService.isKnownCondition("NEW")).thenReturn(true);

        ReflectionTestUtils.invokeMethod(service, "validateAndNormalizeReplacement", item, supplier("普通供应商"), 2);

        assertThat(item.getReplacementEcommerceUrl()).isEqualTo("https://item.jd.com/100123.html");
    }

    private void assertValidationCode(SupplierQuoteItemSubmitDTO item, String code) {
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
            service,
            "validateAndNormalizeReplacement",
            item,
            supplier("普通供应商"),
            2
        ))
            .isInstanceOf(BizException.class)
            .extracting("code")
            .isEqualTo(code);
    }

    private SupplierQuoteItemSubmitDTO validReplacement() {
        SupplierQuoteItemSubmitDTO item = new SupplierQuoteItemSubmitDTO();
        item.setReplacementFlag(true);
        item.setReplacementItemName("神火 GT X8 Pro");
        item.setReplacementSpecModel("GT X8 Pro 800W");
        item.setReplacementEcommerceUrl("https://item.jd.com/100123.html");
        item.setReplacementUnitPrice(new BigDecimal("410.00"));
        item.setReplacementReason("原型号缺货，同品牌同用途替换");
        item.setReplacementCondition("NEW");
        return item;
    }

    private Supplier supplier(String name) {
        Supplier supplier = new Supplier();
        supplier.setSupplierName(name);
        return supplier;
    }
}
