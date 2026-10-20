package com.company.procurement.modules.procurement.assistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProcurementPurposeOptimizeDTO {

    @NotBlank(message = "采购原因不能为空")
    @Size(max = 500, message = "采购原因不能超过500字")
    private String purpose;
}
