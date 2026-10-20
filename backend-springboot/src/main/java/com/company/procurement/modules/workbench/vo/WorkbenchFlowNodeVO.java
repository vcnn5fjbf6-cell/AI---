package com.company.procurement.modules.workbench.vo;

import lombok.Data;

@Data
public class WorkbenchFlowNodeVO {

    private Integer nodeNo;
    private String name;
    private String state;
    private String stateText;
    private String meta;
}
