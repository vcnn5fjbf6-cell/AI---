package com.company.procurement.modules.procurement.workflow.dto;

import lombok.Data;

@Data
public class WorkflowReplacementDecisionDTO {

    private Long quoteItemId;

    private Boolean replacementApproved;
}
