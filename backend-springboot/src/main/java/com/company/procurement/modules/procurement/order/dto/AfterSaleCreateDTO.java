package com.company.procurement.modules.procurement.order.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class AfterSaleCreateDTO {

    @NotBlank(message = "After-sale type is required")
    private String afterSaleType;

    @NotBlank(message = "After-sale reason is required")
    private String reason;

    @NotNull(message = "After-sale quantity is required")
    @DecimalMin(value = "0.0001", message = "After-sale quantity must be greater than 0")
    private BigDecimal requestedQty;

    private String replacementItemCode;

    private String replacementItemName;

    private String replacementSpecModel;

    private String replacementUnit;

    private String replacementRemark;
}
