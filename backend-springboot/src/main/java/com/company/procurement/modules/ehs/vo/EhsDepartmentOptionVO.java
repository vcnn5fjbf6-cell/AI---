package com.company.procurement.modules.ehs.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class EhsDepartmentOptionVO {
    private Long approvalRouteId;
    private String deptCode;
    private String deptName;
    private String regionName;
    private String roomName;
    private String buildingName;
    private String label;
}
