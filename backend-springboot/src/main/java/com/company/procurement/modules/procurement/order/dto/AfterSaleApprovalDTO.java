package com.company.procurement.modules.procurement.order.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class AfterSaleApprovalDTO {

    @NotBlank(message = "Approval action is required")
    private String action;

    private String approvalRemark;

    private String proposalType;

    private String proposalRemark;

    private String replacementItemCode;

    private String replacementItemName;

    private String replacementSpecModel;

    private String replacementUnit;

    private BigDecimal replacementUnitPrice;

    private BigDecimal replacementLineAmount;
}
