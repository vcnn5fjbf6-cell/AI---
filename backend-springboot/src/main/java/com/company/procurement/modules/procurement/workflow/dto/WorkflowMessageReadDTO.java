package com.company.procurement.modules.procurement.workflow.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class WorkflowMessageReadDTO {

    @NotEmpty(message = "Message keys must not be empty")
    private List<String> messageKeys;
}
