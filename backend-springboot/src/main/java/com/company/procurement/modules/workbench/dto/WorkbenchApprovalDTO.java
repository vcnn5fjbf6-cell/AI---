package com.company.procurement.modules.workbench.dto;

import com.company.procurement.modules.procurement.workflow.dto.WorkflowApprovalItemDecisionDTO;
import com.company.procurement.modules.procurement.workflow.dto.WorkflowReplacementDecisionDTO;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class WorkbenchApprovalDTO {

    @NotBlank
    private String action;

    private String comment;

    private Integer targetNodeNo;

    private List<WorkflowApprovalItemDecisionDTO> itemDecisions;

    private List<WorkflowReplacementDecisionDTO> replacementDecisions;

    private String addSignUser;

    private String afterSaleProposalType;

    private String afterSaleReplacementItemCode;

    private String afterSaleReplacementItemName;

    private String afterSaleReplacementSpecModel;

    private String afterSaleReplacementUnit;

    private BigDecimal afterSaleReplacementUnitPrice;

    private BigDecimal afterSaleReplacementLineAmount;
}
