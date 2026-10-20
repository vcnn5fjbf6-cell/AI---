package com.company.procurement.modules.procurement.order.dto;

import com.company.procurement.common.dto.PageQuery;
import lombok.Data;

@Data
public class ProcurementOrderPageQueryDTO extends PageQuery {

    private String keyword;

    private String status;
}
