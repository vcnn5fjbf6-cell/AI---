package com.company.procurement.modules.workbench.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class WorkbenchFollowDTO {

    @NotBlank
    private String sourceType;

    @NotNull
    private Long sourceId;

    @NotNull
    private Boolean followed;
}
