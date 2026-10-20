package com.company.procurement.modules.procurement.workflow.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class WorkflowApprovalActionDTO {

    @NotBlank(message = "Approval action must not be blank")
    private String action;

    private String approvalComment;

    private Integer targetNodeNo;

    private List<WorkflowApprovalItemDecisionDTO> itemDecisions;
}
