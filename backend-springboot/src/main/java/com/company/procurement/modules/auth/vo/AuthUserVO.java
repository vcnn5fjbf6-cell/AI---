package com.company.procurement.modules.auth.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class AuthUserVO {

    private Long userId;

    private String username;

    private String realName;

    private String employeeNo;

    private String userType;

    private Long regionId;

    private String deptCode;

    private String deptName;

    private Long supplierId;

    private String dataScopeType;

    private List<String> roleCodes;

    private List<String> permissionCodes;

    private List<String> appCodes;

    private Integer mustChangePassword;

    private LocalDateTime passwordChangedAt;

    private LocalDateTime passwordExpiresAt;

    private Boolean passwordExpired;

    private Integer passwordExpireDays;
}
