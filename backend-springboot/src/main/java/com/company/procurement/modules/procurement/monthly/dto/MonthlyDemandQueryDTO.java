package com.company.procurement.modules.procurement.monthly.dto;

import com.company.procurement.common.dto.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class MonthlyDemandQueryDTO extends PageQuery {

    private String planMonth;
    private String status;
    private String reqType;
}
