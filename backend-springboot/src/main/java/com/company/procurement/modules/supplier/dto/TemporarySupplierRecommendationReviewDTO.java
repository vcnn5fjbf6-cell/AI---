package com.company.procurement.modules.supplier.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TemporarySupplierRecommendationReviewDTO {

    @NotBlank(message = "审批意见不能为空")
    private String comment;
}
