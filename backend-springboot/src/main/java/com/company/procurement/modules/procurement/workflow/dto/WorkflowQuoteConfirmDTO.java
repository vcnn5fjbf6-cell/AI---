package com.company.procurement.modules.procurement.workflow.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class WorkflowQuoteConfirmDTO {

    private Long quoteId;

    private String awardStrategy;

    @Size(max = 1000)
    private String leaderConfirmRemark;
}
