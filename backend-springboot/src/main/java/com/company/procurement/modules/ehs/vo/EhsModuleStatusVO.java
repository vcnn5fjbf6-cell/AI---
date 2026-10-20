package com.company.procurement.modules.ehs.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EhsModuleStatusVO {

    private String moduleCode;

    private String moduleName;

    private String status;

    private long totalCount;

    private long activeCount;

    private long alertCount;
}
