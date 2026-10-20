package com.company.procurement.modules.procurement.workflow.dto;

import lombok.Data;

@Data
public class WorkflowApprovalItemDecisionDTO {

    private Long itemId;

    private Boolean rejected;

    private String rejectReason;
}
