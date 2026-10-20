package com.company.procurement.modules.procurement.monthly.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class MonthlyDemandSupplementDTO {

    @NotNull(message = "购买数量不能为空")
    private BigDecimal qty;

    @NotBlank(message = "购买原因不能为空")
    private String purchaseReason;
}
