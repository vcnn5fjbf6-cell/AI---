package com.company.procurement.modules.procurement.settings.vo;

import lombok.Data;

import java.util.Map;

@Data
public class ProcurementRuleTestResultVO {

    private String actionCode;

    private String actionName;

    private Boolean triggered;

    private String message;

    private Map<String, Object> detail;
}
