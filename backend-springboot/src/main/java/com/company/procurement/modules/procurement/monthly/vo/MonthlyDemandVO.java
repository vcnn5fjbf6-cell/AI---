package com.company.procurement.modules.procurement.monthly.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class MonthlyDemandVO {

    private Long id;
    private String demandNo;
    private String planMonth;
    private Long applicantUserId;
    private Long regionId;
    private String deptName;
    private String budgetCode;
    private String reqType;
    private Integer urgencyLevel;
    private Boolean emergency;
    private String reqTitle;
    private String itemCode;
    private String itemName;
    private String specModel;
    private String unit;
    private BigDecimal qty;
    private BigDecimal unitPrice;
    private BigDecimal lineAmount;
    private LocalDate needDate;
    private String purchaseReason;
    private String ecommerceUrl;
    private String sourceChannel;
    private String status;
    private Long purchaseRequestId;
    private LocalDateTime createdAt;
}
