package com.company.procurement.modules.procurement.request.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class PurchaseRequestItemVO {

    private Long id;
    private Integer lineNo;
    private String itemCode;
    private String reqType;
    private String itemName;
    private String specModel;
    private String itemBrand;
    private String unit;
    private BigDecimal qty;
    private BigDecimal unitPrice;
    private BigDecimal referenceUnitPrice;
    private BigDecimal taxRate;
    private BigDecimal lineAmount;
    private BigDecimal referenceLineAmount;
    private String awardedSupplierName;
    private BigDecimal awardedUnitPrice;
    private BigDecimal overPriceUnitDifference;
    private BigDecimal overPriceLineDifference;
    private BigDecimal overPriceRatePercent;
    private Boolean overPriceReviewRequired;
    private String sourceRequestNo;
    private String sourceApplicantName;
    private String sourceApplicantDeptName;
    private LocalDate needDate;
    private String purpose;
    private String ecommerceUrl;
    private String receivingCity;
    private String deliveryAddress;
    private Long sourceRequestId;
    private Long sourceRequestItemId;
    private String status;
    private String remark;
}
