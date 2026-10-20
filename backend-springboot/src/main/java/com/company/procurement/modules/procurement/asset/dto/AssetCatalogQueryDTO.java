package com.company.procurement.modules.procurement.asset.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class AssetCatalogQueryDTO {

    private String keyword;
    private Integer enabled;

    @Min(value = 1, message = "页码必须大于0")
    private long pageNo = 1;

    @Min(value = 1, message = "每页数量必须大于0")
    @Max(value = 200, message = "每页最多200条")
    private long pageSize = 20;
}
