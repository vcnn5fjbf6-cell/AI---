package com.company.procurement.modules.procurement.asset.service;

import com.company.procurement.modules.procurement.asset.service.AssetCatalogService.AssetCatalogEntry;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Locale;

@Service
public class QuoteAssetClassificationService {

    public static final String CONDITION_NEW = "NEW";
    public static final String CONDITION_USED = "USED";
    public static final String CONDITION_UNKNOWN = "UNKNOWN";

    public static final String CLASSIFICATION_ASSET = "ASSET";
    public static final String CLASSIFICATION_LOW_VALUE = "LOW_VALUE";
    public static final String CLASSIFICATION_SPECIAL_APPROVAL = "SPECIAL_APPROVAL";
    public static final String CLASSIFICATION_NOT_ASSET = "NOT_ASSET";
    public static final String CLASSIFICATION_PENDING = "PENDING";
    public static final String CLASSIFICATION_MANUAL_REVIEW = "MANUAL_REVIEW";

    public static final BigDecimal USED_ASSET_SPECIAL_APPROVAL_THRESHOLD = new BigDecimal("1000.00");

    public String normalizeCondition(String condition) {
        if (!StringUtils.hasText(condition)) {
            return CONDITION_UNKNOWN;
        }
        String normalized = condition.trim().toUpperCase(Locale.ROOT);
        if (CONDITION_NEW.equals(normalized) || "全新".equals(condition.trim())) {
            return CONDITION_NEW;
        }
        if (CONDITION_USED.equals(normalized) || "二手".equals(condition.trim()) || "翻新".equals(condition.trim())) {
            return CONDITION_USED;
        }
        return CONDITION_UNKNOWN;
    }

    public String classify(AssetCatalogEntry catalogEntry, boolean aiReviewFinished) {
        if (catalogEntry == null) {
            if (!aiReviewFinished) {
                return CLASSIFICATION_PENDING;
            }
            return CLASSIFICATION_LOW_VALUE;
        }
        return CLASSIFICATION_ASSET;
    }

    public boolean requiresSpecialApproval(String condition, BigDecimal unitPrice) {
        return isUsed(condition)
            && unitPrice != null
            && unitPrice.compareTo(USED_ASSET_SPECIAL_APPROVAL_THRESHOLD) >= 0;
    }

    public boolean isUsed(String condition) {
        return CONDITION_USED.equals(normalizeCondition(condition));
    }

    public boolean isKnownCondition(String condition) {
        return !CONDITION_UNKNOWN.equals(normalizeCondition(condition));
    }

    public boolean isAssetRequestType(String requestType) {
        return StringUtils.hasText(requestType)
            && (requestType.contains("实物资产") || requestType.contains("无形资产") || requestType.contains("长摊"));
    }
}
