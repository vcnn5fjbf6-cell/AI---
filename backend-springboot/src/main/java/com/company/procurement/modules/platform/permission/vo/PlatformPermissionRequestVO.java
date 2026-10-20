package com.company.procurement.modules.platform.permission.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PlatformPermissionRequestVO {

    private Long id;

    private Long applicantUserId;

    private String applicantName;

    private String applicantEmail;

    private String applicantMobile;

    private Long unitId;

    private String unitName;

    private String appCode;

    private String appName;

    private String roleCode;

    private String reason;

    private String status;

    private Long reviewerUserId;

    private String reviewComment;

    private LocalDateTime reviewedAt;

    private LocalDateTime createdAt;
}
