package com.company.procurement.modules.procurement.assistant.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ProcurementAiConfirmDTO {

    @NotNull(message = "candidateNo must not be null")
    private Integer candidateNo;
}
