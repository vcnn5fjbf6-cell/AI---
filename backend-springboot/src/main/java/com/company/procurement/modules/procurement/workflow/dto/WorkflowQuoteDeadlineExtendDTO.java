package com.company.procurement.modules.procurement.workflow.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class WorkflowQuoteDeadlineExtendDTO {

    @NotNull(message = "Extension days are required")
    @Min(value = 1, message = "Extension days must be at least 1")
    @Max(value = 60, message = "Extension days must not exceed 60")
    private Integer days;
}
