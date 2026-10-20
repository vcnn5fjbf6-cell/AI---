package com.company.procurement.modules.supplier.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class SupplierQuoteItemSubmitDTO {

    @NotNull(message = "Purchase request item ID must not be null")
    private Long purchaseRequestItemId;

    private BigDecimal unitPrice;

    private Integer leadTimeDays;

    private String originalCondition;

    private String originalSecondHandRemark;

    private Boolean replacementFlag;

    private String replacementItemName;

    private String replacementSpecModel;

    private String replacementEcommerceUrl;

    private BigDecimal replacementUnitPrice;

    private String replacementReason;

    private String replacementCondition;

    private String replacementSecondHandRemark;

    private Boolean waived;

    private String quoteRemark;
}
