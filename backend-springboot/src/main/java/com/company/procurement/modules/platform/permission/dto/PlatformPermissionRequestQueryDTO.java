package com.company.procurement.modules.platform.permission.dto;

import com.company.procurement.common.dto.PageQuery;
import lombok.Data;

@Data
public class PlatformPermissionRequestQueryDTO extends PageQuery {

    private String status;

    private String appCode;

    private Long applicantUserId;
}
