package com.company.procurement.modules.auth.vo;

import lombok.Data;

@Data
public class AuthLoginCandidateVO {

    private Long userId;

    private String username;

    private String realName;

    private String employeeNo;

    private String deptName;
}
