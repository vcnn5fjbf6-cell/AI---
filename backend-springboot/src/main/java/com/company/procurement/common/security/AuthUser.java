package com.company.procurement.common.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthUser {

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

    @Builder.Default
    private List<String> roleCodes = new ArrayList<>();

    @Builder.Default
    private List<String> permissionCodes = new ArrayList<>();

    @Builder.Default
    private List<String> appCodes = new ArrayList<>();

    @Builder.Default
    private List<String> apiPermissions = new ArrayList<>();

    private Integer mustChangePassword;

    private LocalDateTime passwordChangedAt;

    private LocalDateTime passwordExpiresAt;

    private Boolean passwordExpired;
}
