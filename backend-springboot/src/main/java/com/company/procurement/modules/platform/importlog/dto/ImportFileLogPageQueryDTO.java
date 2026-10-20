package com.company.procurement.modules.platform.importlog.dto;

import com.company.procurement.common.dto.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ImportFileLogPageQueryDTO extends PageQuery {

    private String sourceChannel;

    private String importType;

    private String processStatus;

    private String keyword;
}
