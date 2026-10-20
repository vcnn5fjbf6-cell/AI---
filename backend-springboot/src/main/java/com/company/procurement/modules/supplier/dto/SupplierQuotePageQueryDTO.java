package com.company.procurement.modules.supplier.dto;

import com.company.procurement.common.dto.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class SupplierQuotePageQueryDTO extends PageQuery {

    private String reqNo;
    private String reqTitle;
}
