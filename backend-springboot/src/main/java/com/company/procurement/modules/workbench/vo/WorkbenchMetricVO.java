package com.company.procurement.modules.workbench.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkbenchMetricVO {

    private String key;
    private String label;
    private long value;
}
