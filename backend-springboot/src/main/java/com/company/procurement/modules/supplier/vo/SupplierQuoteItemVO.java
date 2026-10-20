package com.company.procurement.modules.supplier.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class SupplierQuoteItemVO {

    private Long id;
    private Long quoteId;
    private Long purchaseRequestId;
    private Long purchaseRequestItemId;
    private Integer lineNo;
    private String itemCode;
    private String itemName;
    private String specModel;
    private String unit;
    private BigDecimal qty;
    private BigDecimal unitPrice;
    private BigDecimal supplierSubmittedUnitPrice;
    private BigDecimal lineAmount;
    private Integer leadTimeDays;
    private String originalCondition;
    private String originalSecondHandRemark;
    private String quoteRemark;
    private Boolean replacementFlag;
    private String replacementItemName;
    private String replacementSpecModel;
    private String replacementEcommerceUrl;
    private BigDecimal replacementUnitPrice;
    private BigDecimal replacementLineAmount;
    private String replacementReason;
    private String replacementCondition;
    private String replacementSecondHandRemark;
    private String replacementReviewStatus;
    private String replacementReviewRemark;
    private String assetReviewStatus;
    private String assetReviewMessage;
    private String originalAssetCategoryCode;
    private String originalAssetNameCode;
    private String originalAssetCatalogName;
    private String originalAssetClassification;
    private Boolean originalSpecialApprovalRequired;
    private String replacementAssetCategoryCode;
    private String replacementAssetNameCode;
    private String replacementAssetCatalogName;
    private String replacementAssetClassification;
    private Boolean replacementSpecialApprovalRequired;
    private Boolean conditionMismatch;
    private LocalDateTime assetReviewedAt;
    private Boolean waived;
    private Boolean awarded;
    private String receivingCity;
    private String deliveryAddress;
    private String status;
}
