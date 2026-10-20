package com.company.procurement.modules.supplier.service.impl;

import com.company.procurement.modules.procurement.asset.service.AssetCatalogService;
import com.company.procurement.modules.procurement.asset.service.QuoteAssetClassificationService;
import com.company.procurement.modules.procurement.assistant.client.M3VisionClient;
import com.company.procurement.modules.supplier.entity.SupplierQuoteItem;
import com.company.procurement.modules.supplier.mapper.SupplierQuoteItemMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SupplierQuoteAssetReviewServiceTest {

    private final SupplierQuoteItemMapper quoteItemMapper = mock(SupplierQuoteItemMapper.class);
    private final M3VisionClient m3VisionClient = mock(M3VisionClient.class);
    private final AssetCatalogService assetCatalogService = mock(AssetCatalogService.class);
    private final QuoteAssetClassificationService classificationService = new QuoteAssetClassificationService();
    private final SupplierQuoteAssetReviewService service = new SupplierQuoteAssetReviewService(
        quoteItemMapper,
        m3VisionClient,
        assetCatalogService,
        classificationService,
        new ObjectMapper()
    );

    @Test
    void missingModelResultMovesUnmatchedItemToManualReview() {
        SupplierQuoteItem item = pendingQuoteItem();
        when(m3VisionClient.canCompleteText()).thenReturn(true);
        when(quoteItemMapper.selectList(any())).thenReturn(List.of(item));
        when(assetCatalogService.promptText()).thenReturn("1702|0056|UPS电源|电子设备");
        when(assetCatalogService.matchExact(any(), any())).thenReturn(Optional.empty());
        when(assetCatalogService.version()).thenReturn("21V_Accounting_Manual_V2.1");
        when(m3VisionClient.reviewQuoteAssets(any(), any(), any())).thenReturn(List.of());

        service.reviewPendingQuoteItems();

        SupplierQuoteItem updated = updatedItem();
        assertThat(updated.getAssetReviewStatus()).isEqualTo("PARTIAL");
        assertThat(updated.getOriginalAssetClassification()).isEqualTo("MANUAL_REVIEW");
        assertThat(updated.getAssetReviewMessage()).contains("需人工确认");
    }

    @Test
    void reviewedItemOutsideCatalogIsLowValue() {
        SupplierQuoteItem item = pendingQuoteItem();
        when(m3VisionClient.canCompleteText()).thenReturn(true);
        when(quoteItemMapper.selectList(any())).thenReturn(List.of(item));
        when(assetCatalogService.promptText()).thenReturn("1702|0056|UPS电源|电子设备");
        when(assetCatalogService.matchExact(any(), any())).thenReturn(Optional.empty());
        when(assetCatalogService.find(any(), any())).thenReturn(Optional.empty());
        when(assetCatalogService.version()).thenReturn("21V_Accounting_Manual_V2.1");
        when(m3VisionClient.reviewQuoteAssets(any(), any(), any())).thenReturn(List.of(
            new M3VisionClient.QuoteAssetReview(
                10L,
                "ORIGINAL",
                "NEW",
                "商品描述为全新",
                "",
                "",
                "",
                new BigDecimal("0.95")
            )
        ));

        service.reviewPendingQuoteItems();

        SupplierQuoteItem updated = updatedItem();
        assertThat(updated.getAssetReviewStatus()).isEqualTo("COMPLETED");
        assertThat(updated.getOriginalAssetClassification()).isEqualTo("LOW_VALUE");
        assertThat(updated.getOriginalSpecialApprovalRequired()).isZero();
        assertThat(updated.getAssetReviewMessage()).contains("未命中资产目录");
    }

    @Test
    void usedHighPriceOutsideCatalogRemainsLowValueAndRequiresSpecialApproval() {
        SupplierQuoteItem item = pendingQuoteItem();
        item.setOriginalCondition("USED");
        item.setUnitPrice(new BigDecimal("1500.00"));
        when(m3VisionClient.canCompleteText()).thenReturn(true);
        when(quoteItemMapper.selectList(any())).thenReturn(List.of(item));
        when(assetCatalogService.promptText()).thenReturn("1702|0056|UPS电源|电子设备");
        when(assetCatalogService.matchExact(any(), any())).thenReturn(Optional.empty());
        when(assetCatalogService.find(any(), any())).thenReturn(Optional.empty());
        when(assetCatalogService.version()).thenReturn("MANAGED_ASSET_CATALOG");
        when(m3VisionClient.reviewQuoteAssets(any(), any(), any())).thenReturn(List.of(
            new M3VisionClient.QuoteAssetReview(
                10L,
                "ORIGINAL",
                "USED",
                "商品描述为二手",
                "",
                "",
                "",
                new BigDecimal("0.95")
            )
        ));

        service.reviewPendingQuoteItems();

        SupplierQuoteItem updated = updatedItem();
        assertThat(updated.getOriginalAssetClassification()).isEqualTo("LOW_VALUE");
        assertThat(updated.getOriginalSpecialApprovalRequired()).isEqualTo(1);
        assertThat(updated.getAssetReviewMessage()).contains("需特殊审批");
    }

    @Test
    void modelFailureMovesPendingClassificationsToManualReview() {
        SupplierQuoteItem item = pendingQuoteItem();
        item.setReplacementFlag(1);
        item.setReplacementItemName("替换设备");
        item.setReplacementCondition("USED");
        item.setReplacementUnitPrice(new BigDecimal("1500.00"));
        item.setReplacementAssetClassification("PENDING");
        when(m3VisionClient.canCompleteText()).thenReturn(true);
        when(quoteItemMapper.selectList(any())).thenReturn(List.of(item));
        when(assetCatalogService.promptText()).thenReturn("1702|0056|UPS电源|电子设备");
        when(m3VisionClient.reviewQuoteAssets(any(), any(), any()))
            .thenThrow(new IllegalStateException("upstream unavailable"));

        service.reviewPendingQuoteItems();

        SupplierQuoteItem updated = updatedItem();
        assertThat(updated.getAssetReviewStatus()).isEqualTo("FAILED");
        assertThat(updated.getOriginalAssetClassification()).isEqualTo("MANUAL_REVIEW");
        assertThat(updated.getReplacementAssetClassification()).isEqualTo("MANUAL_REVIEW");
    }

    private SupplierQuoteItem pendingQuoteItem() {
        SupplierQuoteItem item = new SupplierQuoteItem();
        item.setId(10L);
        item.setPurchaseRequestItemId(20L);
        item.setStatus("ACTIVE");
        item.setItemName("无法匹配目录的设备");
        item.setOriginalCondition("NEW");
        item.setUnitPrice(new BigDecimal("500.00"));
        item.setAssetReviewStatus("PENDING");
        item.setOriginalAssetClassification("PENDING");
        return item;
    }

    private SupplierQuoteItem updatedItem() {
        ArgumentCaptor<SupplierQuoteItem> captor = ArgumentCaptor.forClass(SupplierQuoteItem.class);
        verify(quoteItemMapper).updateById(captor.capture());
        return captor.getValue();
    }
}
