package com.company.procurement.modules.workbench.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class WorkbenchDetailRowVO {

    private Long itemId;
    private Long quoteItemId;
    private Integer lineNo;
    private String name;
    private String roomName;
    private String deptName;
    private String spec;
    private BigDecimal qty;
    private String unit;
    private BigDecimal amount;
    private String amountText;
    private BigDecimal referenceAmount;
    private String referenceAmountText;
    private BigDecimal awardedUnitPrice;
    private BigDecimal overPriceLineDifference;
    private BigDecimal overPriceRatePercent;
    private Boolean overPriceReviewRequired;
    private String sourceRequestNo;
    private String sourceApplicantName;
    private String sourceApplicantDeptName;
    private String status;
    private String statusText;
    private String remark;
    private String purchaseReason;
    private String rejectReason;
    private Boolean replacementQuote;
    private String originalItemName;
    private String originalSpec;
    private String replacementItemName;
    private String replacementSpec;
    private String replacementEcommerceUrl;
    private BigDecimal replacementUnitPrice;
    private BigDecimal replacementAmount;
    private String replacementReason;
    private String replacementReviewStatus;
    private String replacementReviewRemark;
    private String supplierName;
    private String supplierContactName;
    private String supplierContactPhone;
    private BigDecimal quoteUnitPrice;
    private BigDecimal quoteAmount;
    private String quoteAmountText;
    private Integer leadTimeDays;
    private Boolean awarded;
    private BigDecimal originalUnitPrice;
    private BigDecimal priceIncreaseLimit;
    private String supplierProposalType;
    private String supplierProposalRemark;
    private String supplierReplacementItemCode;
    private String supplierReplacementItemName;
    private String supplierReplacementSpecModel;
    private String supplierReplacementUnit;
    private BigDecimal supplierReplacementUnitPrice;
    private BigDecimal supplierReplacementLineAmount;
    private String supplierProposedAt;
    private String requesterConfirmRemark;
    private String requesterConfirmedAt;
}
