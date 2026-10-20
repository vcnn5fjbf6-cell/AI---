package com.company.procurement.modules.procurement.bi.dto;

import lombok.Data;

import java.util.Map;

@Data
public class ProcurementBiDashboardSaveDTO {

    private String dashboardCode;

    private Map<String, Object> layout;
}
