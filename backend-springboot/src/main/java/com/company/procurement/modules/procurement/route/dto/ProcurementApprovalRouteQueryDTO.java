package com.company.procurement.modules.procurement.route.dto;

import lombok.Data;

@Data
public class ProcurementApprovalRouteQueryDTO {

    private String keyword;
    private String roomName;
    private String regionName;
    private Integer enabled;
    private Long routeId;
    private Boolean includeRoutes;
    private Boolean includeRules;
    private Boolean includeApprovalLevels;
}
