package com.company.procurement.modules.supplier.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.Valid;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class SupplierQuoteSubmitDTO {

    @NotNull(message = "Purchase request ID must not be null")
    private Long purchaseRequestId;

    private BigDecimal totalAmount;

    @PositiveOrZero(message = "Lead time must not be negative")
    private Integer leadTimeDays;

    private String quoteRemark;

    @Valid
    private List<SupplierQuoteItemSubmitDTO> items;
}
