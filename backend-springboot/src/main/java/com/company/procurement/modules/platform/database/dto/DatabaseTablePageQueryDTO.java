package com.company.procurement.modules.platform.database.dto;

import com.company.procurement.common.dto.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class DatabaseTablePageQueryDTO extends PageQuery {

    private String keyword;
}
