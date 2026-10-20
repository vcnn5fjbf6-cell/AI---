package com.company.procurement.modules.procurement.route.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ApprovalLevelVO {

    private Long id;
    private Integer levelNo;
    private String levelCode;
    private String levelName;
    private String roleCode;
    private Integer enabled;
    private String triggerMode;
    private String passRule;
    private BigDecimal amountMin;
    private BigDecimal amountMax;
    private Integer skipIfEmpty;
    private String candidateUserIds;
    private String candidateNames;
    private String remark;
}
