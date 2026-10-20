package com.company.procurement.modules.procurement.labor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PersonalLaborRequestItemDTO {

    @NotBlank(message = "劳保用品编码不能为空")
    private String itemCode;

    private String specModel;

    @NotNull(message = "申领数量不能为空")
    private BigDecimal qty;

    private BigDecimal unitPrice;

    private String purpose;
}
