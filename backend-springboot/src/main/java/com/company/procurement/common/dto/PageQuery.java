package com.company.procurement.common.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class PageQuery {

    @Min(value = 1, message = "Page number must be greater than or equal to 1")
    private long pageNo = 1;

    @Min(value = 1, message = "Page size must be greater than or equal to 1")
    @Max(value = 500, message = "Page size must be less than or equal to 500")
    private long pageSize = 10;
}
