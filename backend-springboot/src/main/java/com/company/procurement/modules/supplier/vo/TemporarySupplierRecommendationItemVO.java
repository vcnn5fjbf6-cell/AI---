package com.company.procurement.modules.supplier.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class TemporarySupplierRecommendationItemVO {

    private Long id;
    private Long purchaseRequestItemId;
    private Integer lineNo;
    private String itemCode;
    private String reqType;
    private String itemName;
    private String specModel;
    private String unit;
    private BigDecimal qty;
    private String purpose;
    private String status;
}
