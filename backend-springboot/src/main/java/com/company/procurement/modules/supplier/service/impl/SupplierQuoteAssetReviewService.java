package com.company.procurement.modules.supplier.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.procurement.modules.procurement.asset.service.AssetCatalogService;
import com.company.procurement.modules.procurement.asset.service.AssetCatalogService.AssetCatalogEntry;
import com.company.procurement.modules.procurement.asset.service.QuoteAssetClassificationService;
import com.company.procurement.modules.procurement.assistant.client.M3VisionClient;
import com.company.procurement.modules.procurement.assistant.client.M3VisionClient.QuoteAssetReview;
import com.company.procurement.modules.procurement.assistant.client.M3VisionClient.QuoteAssetReviewInput;
import com.company.procurement.modules.supplier.entity.SupplierQuoteItem;
import com.company.procurement.modules.supplier.mapper.SupplierQuoteItemMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class SupplierQuoteAssetReviewService {

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_PARTIAL = "PARTIAL";
    private static final String STATUS_FAILED = "FAILED";
    private static final String OPTION_ORIGINAL = "ORIGINAL";
    private static final String OPTION_REPLACEMENT = "REPLACEMENT";
    private static final BigDecimal TRUST_THRESHOLD = new BigDecimal("0.75");

    private final SupplierQuoteItemMapper supplierQuoteItemMapper;
    private final M3VisionClient m3VisionClient;
    private final AssetCatalogService assetCatalogService;
    private final QuoteAssetClassificationService classificationService;
    private final ObjectMapper objectMapper;

    @Scheduled(
        fixedDelayString = "${procurement.quote-asset-review.delay-ms:30000}",
        initialDelayString = "${procurement.quote-asset-review.initial-delay-ms:15000}"
    )
    public void reviewPendingQuoteItems() {
        if (!m3VisionClient.canCompleteText()) {
            return;
        }
        List<SupplierQuoteItem> pendingItems = supplierQuoteItemMapper.selectList(
            new LambdaQueryWrapper<SupplierQuoteItem>()
                .eq(SupplierQuoteItem::getAssetReviewStatus, STATUS_PENDING)
                .eq(SupplierQuoteItem::getStatus, "ACTIVE")
                .orderByAsc(SupplierQuoteItem::getId)
                .last("LIMIT 10")
        );
        if (pendingItems.isEmpty()) {
            return;
        }

        List<QuoteAssetReviewInput> inputs = buildInputs(pendingItems);
        try {
            List<QuoteAssetReview> reviews = m3VisionClient.reviewQuoteAssets(
                inputs,
                assetCatalogService.promptText(),
                "supplier-quote-asset-review"
            );
            applyReviews(pendingItems, reviews);
        } catch (RuntimeException ex) {
            log.warn("M3 quote asset review failed for {} quote items", pendingItems.size(), ex);
            markFailed(pendingItems);
        }
    }

    private List<QuoteAssetReviewInput> buildInputs(List<SupplierQuoteItem> items) {
        List<QuoteAssetReviewInput> inputs = new ArrayList<>();
        for (SupplierQuoteItem item : items) {
            inputs.add(new QuoteAssetReviewInput(
                item.getId(),
                OPTION_ORIGINAL,
                item.getItemName(),
                item.getSpecModel(),
                "",
                item.getQuoteRemark(),
                item.getOriginalCondition(),
                item.getOriginalSecondHandRemark()
            ));
            if (item.getReplacementFlag() != null && item.getReplacementFlag() == 1) {
                inputs.add(new QuoteAssetReviewInput(
                    item.getId(),
                    OPTION_REPLACEMENT,
                    item.getReplacementItemName(),
                    item.getReplacementSpecModel(),
                    item.getReplacementEcommerceUrl(),
                    item.getReplacementReason(),
                    item.getReplacementCondition(),
                    item.getReplacementSecondHandRemark()
                ));
            }
        }
        return inputs;
    }

    private void applyReviews(List<SupplierQuoteItem> items, List<QuoteAssetReview> reviews) {
        Map<ReviewKey, QuoteAssetReview> reviewMap = new LinkedHashMap<>();
        for (QuoteAssetReview review : reviews == null ? List.<QuoteAssetReview>of() : reviews) {
            reviewMap.put(new ReviewKey(review.quoteItemId(), normalizeOption(review.option())), review);
        }
        for (SupplierQuoteItem item : items) {
            QuoteAssetReview originalReview = reviewMap.get(new ReviewKey(item.getId(), OPTION_ORIGINAL));
            OptionOutcome originalOutcome = resolveOutcome(
                item.getItemName(),
                item.getSpecModel(),
                item.getOriginalCondition(),
                item.getUnitPrice(),
                originalReview
            );
            applyOriginalOutcome(item, originalOutcome);

            QuoteAssetReview replacementReview = null;
            OptionOutcome replacementOutcome = null;
            if (item.getReplacementFlag() != null && item.getReplacementFlag() == 1) {
                replacementReview = reviewMap.get(new ReviewKey(item.getId(), OPTION_REPLACEMENT));
                replacementOutcome = resolveOutcome(
                    item.getReplacementItemName(),
                    item.getReplacementSpecModel(),
                    item.getReplacementCondition(),
                    item.getReplacementUnitPrice(),
                    replacementReview
                );
                applyReplacementOutcome(item, replacementOutcome);
            }

            boolean mismatch = originalOutcome.conditionMismatch()
                || (replacementOutcome != null && replacementOutcome.conditionMismatch());
            boolean missingReview = originalReview == null
                || (item.getReplacementFlag() != null && item.getReplacementFlag() == 1 && replacementReview == null);
            item.setConditionMismatch(mismatch ? 1 : 0);
            item.setAssetReviewStatus(missingReview ? STATUS_PARTIAL : STATUS_COMPLETED);
            item.setAssetReviewMessage(buildReviewMessage(originalOutcome, replacementOutcome, missingReview));
            item.setAssetReviewResultJson(reviewJson(originalReview, replacementReview));
            item.setAssetReviewedAt(LocalDateTime.now());
            supplierQuoteItemMapper.updateById(item);
        }
    }

    private OptionOutcome resolveOutcome(String itemName,
                                         String specModel,
                                         String declaredCondition,
                                         BigDecimal unitPrice,
                                         QuoteAssetReview review) {
        AssetCatalogEntry catalog = trustedCatalog(review);
        if (catalog == null) {
            catalog = assetCatalogService.matchExact(itemName, specModel).orElse(null);
        }
        String detectedCondition = review == null
            ? QuoteAssetClassificationService.CONDITION_UNKNOWN
            : classificationService.normalizeCondition(review.detectedCondition());
        boolean trustedCondition = review != null
            && review.confidence() != null
            && review.confidence().compareTo(TRUST_THRESHOLD) >= 0
            && classificationService.isKnownCondition(detectedCondition);
        boolean mismatch = trustedCondition
            && !Objects.equals(classificationService.normalizeCondition(declaredCondition), detectedCondition);
        String classification = classificationService.classify(catalog, review != null || catalog != null);
        if (review == null && catalog == null) {
            classification = QuoteAssetClassificationService.CLASSIFICATION_MANUAL_REVIEW;
        }
        boolean specialApprovalRequired = classificationService.requiresSpecialApproval(declaredCondition, unitPrice);
        return new OptionOutcome(
            catalog,
            classification,
            detectedCondition,
            mismatch,
            specialApprovalRequired,
            review == null ? "" : review.conditionEvidence()
        );
    }

    private AssetCatalogEntry trustedCatalog(QuoteAssetReview review) {
        if (review == null || review.confidence() == null || review.confidence().compareTo(TRUST_THRESHOLD) < 0) {
            return null;
        }
        return assetCatalogService.find(review.categoryCode(), review.assetNameCode()).orElse(null);
    }

    private void applyOriginalOutcome(SupplierQuoteItem item, OptionOutcome outcome) {
        item.setOriginalAssetClassification(outcome.classification());
        item.setOriginalSpecialApprovalRequired(outcome.specialApprovalRequired() ? 1 : 0);
        if (outcome.catalog() != null) {
            item.setOriginalAssetCategoryCode(outcome.catalog().getCategoryCode());
            item.setOriginalAssetNameCode(outcome.catalog().getAssetNameCode());
            item.setOriginalAssetCatalogName(outcome.catalog().getAssetName());
        }
    }

    private void applyReplacementOutcome(SupplierQuoteItem item, OptionOutcome outcome) {
        item.setReplacementAssetClassification(outcome.classification());
        item.setReplacementSpecialApprovalRequired(outcome.specialApprovalRequired() ? 1 : 0);
        if (outcome.catalog() != null) {
            item.setReplacementAssetCategoryCode(outcome.catalog().getCategoryCode());
            item.setReplacementAssetNameCode(outcome.catalog().getAssetNameCode());
            item.setReplacementAssetCatalogName(outcome.catalog().getAssetName());
        }
    }

    private String buildReviewMessage(OptionOutcome original, OptionOutcome replacement, boolean partial) {
        List<String> parts = new ArrayList<>();
        parts.add("原商品：" + optionSummary(original));
        if (replacement != null) {
            parts.add("替换商品：" + optionSummary(replacement));
        }
        if (partial) {
            parts.add("部分结果无法识别，需人工确认");
        }
        return String.join("；", parts);
    }

    private String optionSummary(OptionOutcome outcome) {
        String catalogText = outcome.catalog() == null
            ? "未命中资产目录"
            : outcome.catalog().getCategoryCode() + "/" + outcome.catalog().getAssetNameCode() + " " + outcome.catalog().getAssetName();
        String mismatchText = outcome.conditionMismatch() ? "，成色声明与M3复核不一致" : "";
        String evidenceText = StringUtils.hasText(outcome.evidence()) ? "（" + outcome.evidence().trim() + "）" : "";
        String approvalText = outcome.specialApprovalRequired() ? "，二手单价达到1000元，需特殊审批" : "";
        return catalogText + "，分类=" + outcome.classification() + approvalText + mismatchText + evidenceText;
    }

    private String reviewJson(QuoteAssetReview original, QuoteAssetReview replacement) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                "catalogVersion", assetCatalogService.version(),
                "original", original == null ? Map.of() : original,
                "replacement", replacement == null ? Map.of() : replacement
            ));
        } catch (JsonProcessingException ex) {
            return "";
        }
    }

    private void markFailed(List<SupplierQuoteItem> items) {
        for (SupplierQuoteItem item : items) {
            if (!StringUtils.hasText(item.getOriginalAssetClassification())
                || QuoteAssetClassificationService.CLASSIFICATION_PENDING.equals(item.getOriginalAssetClassification())) {
                item.setOriginalAssetClassification(QuoteAssetClassificationService.CLASSIFICATION_MANUAL_REVIEW);
            }
            if (item.getReplacementFlag() != null && item.getReplacementFlag() == 1
                && (!StringUtils.hasText(item.getReplacementAssetClassification())
                || QuoteAssetClassificationService.CLASSIFICATION_PENDING.equals(item.getReplacementAssetClassification()))) {
                item.setReplacementAssetClassification(QuoteAssetClassificationService.CLASSIFICATION_MANUAL_REVIEW);
            }
            item.setAssetReviewStatus(STATUS_FAILED);
            item.setAssetReviewMessage("M3复核失败，已保留供应商声明并转人工确认");
            item.setAssetReviewedAt(LocalDateTime.now());
            supplierQuoteItemMapper.updateById(item);
        }
    }

    private String normalizeOption(String option) {
        return StringUtils.hasText(option) ? option.trim().toUpperCase(Locale.ROOT) : "";
    }

    private record ReviewKey(Long quoteItemId, String option) {
    }

    private record OptionOutcome(
        AssetCatalogEntry catalog,
        String classification,
        String detectedCondition,
        boolean conditionMismatch,
        boolean specialApprovalRequired,
        String evidence
    ) {
    }
}
