package com.company.procurement.modules.system.user.dto;

import lombok.Data;

@Data
public class FeishuUserSyncDTO {

    private String rootDepartmentId;

    private Boolean recursive = true;

    private Boolean createMissing = true;

    private Boolean updateExisting = true;

    private Boolean includeInactive = false;

    private String defaultUserType = "ENGINEER";

    private Long defaultRegionId = 1L;
}
