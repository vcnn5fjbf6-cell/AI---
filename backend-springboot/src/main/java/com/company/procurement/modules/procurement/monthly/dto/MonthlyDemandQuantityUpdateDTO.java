package com.company.procurement.modules.procurement.monthly.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class MonthlyDemandQuantityUpdateDTO {

    @NotNull(message = "调整后数量不能为空")
    private BigDecimal qty;
}
