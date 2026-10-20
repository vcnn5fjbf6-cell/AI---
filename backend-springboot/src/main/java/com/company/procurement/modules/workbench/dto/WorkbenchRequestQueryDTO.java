package com.company.procurement.modules.workbench.dto;

import com.company.procurement.common.dto.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
public class WorkbenchRequestQueryDTO extends PageQuery {

    private String filter;

    private String module;

    private String keyword;

    private String statusText;

    private LocalDate startDate;

    private LocalDate endDate;
}
