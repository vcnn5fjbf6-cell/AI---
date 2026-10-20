package com.company.procurement.modules.platform.errorlog.dto;

import com.company.procurement.common.dto.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class SystemErrorLogPageQueryDTO extends PageQuery {

    private String moduleCode;

    private String status;

    private String keyword;
}
