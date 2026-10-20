package com.company.procurement.modules.procurement.asset.service;

import com.company.procurement.modules.procurement.asset.service.AssetCatalogService.AssetCatalogEntry;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class QuoteAssetClassificationServiceTest {

    private final QuoteAssetClassificationService service = new QuoteAssetClassificationService();
    private final AssetCatalogEntry catalogEntry = new AssetCatalogEntry();

    @Test
    void catalogItemIsAssetRegardlessOfConditionAndPrice() {
        assertThat(service.classify(catalogEntry, true))
            .isEqualTo(QuoteAssetClassificationService.CLASSIFICATION_ASSET);
        assertThat(service.requiresSpecialApproval("USED", new BigDecimal("999.99"))).isFalse();
    }

    @Test
    void usedCatalogItemAtOneThousandRequiresSpecialApproval() {
        assertThat(service.classify(catalogEntry, true))
            .isEqualTo(QuoteAssetClassificationService.CLASSIFICATION_ASSET);
        assertThat(service.requiresSpecialApproval("USED", new BigDecimal("1000.00"))).isTrue();
    }

    @Test
    void unmatchedItemIsLowValueAfterAiReview() {
        assertThat(service.classify(null, true))
            .isEqualTo(QuoteAssetClassificationService.CLASSIFICATION_LOW_VALUE);
    }

    @Test
    void newItemDoesNotRequireSpecialApproval() {
        assertThat(service.requiresSpecialApproval("NEW", new BigDecimal("5000.00"))).isFalse();
    }

    @Test
    void unmatchedItemStaysPendingBeforeAiReview() {
        assertThat(service.classify(null, false))
            .isEqualTo(QuoteAssetClassificationService.CLASSIFICATION_PENDING);
    }
}
