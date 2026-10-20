package com.company.procurement.modules.workbench.vo;

import com.company.procurement.common.result.PageResult;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class WorkbenchPageVO {

    private PageResult<WorkbenchRequestVO> page;

    private List<WorkbenchMetricVO> metrics = new ArrayList<>();
}
