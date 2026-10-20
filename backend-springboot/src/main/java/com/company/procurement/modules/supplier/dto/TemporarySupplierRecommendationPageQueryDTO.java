package com.company.procurement.modules.supplier.dto;

import com.company.procurement.common.dto.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class TemporarySupplierRecommendationPageQueryDTO extends PageQuery {

    private String keyword;
    private String status;
    private Long purchaseRequestId;
}
