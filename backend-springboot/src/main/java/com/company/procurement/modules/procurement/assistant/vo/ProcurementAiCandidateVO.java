package com.company.procurement.modules.procurement.assistant.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProcurementAiCandidateVO {

    private Long id;
    private Integer candidateNo;
    private String itemName;
    private String specModel;
    private String unit;
    private BigDecimal qty;
    private BigDecimal unitPrice;
    private BigDecimal taxRate;
    private BigDecimal lineAmount;
    private String reqType;
    private String purchaseReason;
    private String ecommercePlatform;
    private String ecommerceUrl;
    private String imageUrl;
    private BigDecimal score;
    private Boolean selected;
    private String status;
    private String remark;
}
