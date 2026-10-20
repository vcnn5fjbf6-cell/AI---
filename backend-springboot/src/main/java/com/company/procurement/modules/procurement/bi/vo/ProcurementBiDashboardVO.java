package com.company.procurement.modules.procurement.bi.vo;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class ProcurementBiDashboardVO {

    private String dashboardCode;

    private String dashboardName;

    private Map<String, Object> layout = new LinkedHashMap<>();
}
