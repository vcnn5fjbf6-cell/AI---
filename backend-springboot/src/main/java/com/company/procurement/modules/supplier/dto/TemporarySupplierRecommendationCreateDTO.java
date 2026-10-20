package com.company.procurement.modules.supplier.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class TemporarySupplierRecommendationCreateDTO {

    @NotNull(message = "采购申请ID不能为空")
    private Long purchaseRequestId;

    @NotBlank(message = "临时供应商名称不能为空")
    private String supplierName;

    @NotBlank(message = "联系人不能为空")
    private String contactName;

    @NotBlank(message = "联系电话不能为空")
    private String contactPhone;

    private String contactEmail;

    @NotBlank(message = "推荐原因不能为空")
    private String recommendReason;

    private String remark;

    @NotEmpty(message = "推荐报价明细不能为空")
    private List<Long> itemIds;
}
