package com.company.procurement.modules.system.user.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class UserVO {

    private Long id;
    private String username;
    private String realName;
    private String employeeNo;
    private String userType;
    private Long supplierId;
    private String supplierCode;
    private String supplierName;
    private Long regionId;
    private String deptCode;
    private String deptName;
    private String mobile;
    private String email;
    private String feishuUserId;
    private String feishuOpenId;
    private String feishuUnionId;
    private Integer status;
    private String dataScopeType;
    private Integer mustChangePassword;
    private LocalDateTime passwordChangedAt;
    private String remark;
    private List<Long> roleIds;
    private LocalDateTime lastLoginAt;
    private String lastLoginIp;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
