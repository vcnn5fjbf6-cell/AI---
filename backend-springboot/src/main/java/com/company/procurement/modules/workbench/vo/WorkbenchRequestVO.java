package com.company.procurement.modules.workbench.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class WorkbenchRequestVO {

    private String id;
    private Long sourceId;
    private String sourceType;
    private String module;
    private String moduleName;
    private String type;
    private String relation;
    private String detailKind;
    private String reqNo;
    private String title;
    private String applicant;
    private String currentNode;
    private String currentApproverNames;
    private Integer currentNodeNo;
    private String status;
    private String statusText;
    private LocalDateTime arrivedAt;
    private BigDecimal amount;
    private String amountText;
    private BigDecimal referenceAmount;
    private String referenceAmountText;
    private String priceSource;
    private String deptName;
    private String viewerRole;
    private Boolean actionable;
    private Boolean terminable;
    private Boolean returnable;
    private Boolean followed;
}
