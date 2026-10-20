package com.company.procurement.modules.procurement.request.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class PurchaseRequestItemDTO {

    private String itemCode;
    private String reqType;

    @NotBlank(message = "Item name must not be blank")
    private String itemName;

    private String specModel;
    private String itemBrand;

    @NotBlank(message = "Unit must not be blank")
    private String unit;

    @NotNull(message = "Quantity must not be null")
    private BigDecimal qty;

    @NotNull(message = "Unit price must not be null")
    private BigDecimal unitPrice;

    @NotNull(message = "Tax rate must not be null")
    private BigDecimal taxRate;

    private BigDecimal lineAmount;
    private LocalDate needDate;
    private String purpose;
    private String ecommerceUrl;
    private String receivingCity;
    private String deliveryAddress;
    private String status;
    private String remark;
}
