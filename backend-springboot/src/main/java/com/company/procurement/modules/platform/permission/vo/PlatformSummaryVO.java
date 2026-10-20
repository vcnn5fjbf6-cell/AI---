package com.company.procurement.modules.platform.permission.vo;

import lombok.Data;

@Data
public class PlatformSummaryVO {

    private long totalUsers;

    private long loginEnabledUsers;

    private long accessBindings;

    private long pendingRequests;
}
