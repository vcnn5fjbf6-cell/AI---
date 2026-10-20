package com.company.procurement.modules.platform.permission.vo;

import lombok.Data;

import java.util.List;

@Data
public class PlatformUserAccessVO {

    private Long userId;

    private String username;

    private String realName;

    private String userType;

    private String deptCode;

    private String deptName;

    private String mobile;

    private String email;

    private Integer loginEnabled;

    private List<String> appCodes;
}
