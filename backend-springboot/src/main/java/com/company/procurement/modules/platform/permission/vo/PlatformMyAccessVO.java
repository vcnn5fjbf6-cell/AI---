package com.company.procurement.modules.platform.permission.vo;

import lombok.Data;

import java.util.List;

@Data
public class PlatformMyAccessVO {

    private List<String> appCodes;

    private List<PlatformUnitVO> units;

    private List<PlatformPermissionRequestVO> recentRequests;
}
