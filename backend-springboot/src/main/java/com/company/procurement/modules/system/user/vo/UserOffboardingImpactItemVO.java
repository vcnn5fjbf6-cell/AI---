package com.company.procurement.modules.system.user.vo;

import lombok.Data;

@Data
public class UserOffboardingImpactItemVO {

    private String impactKey;
    private String impactType;
    private Long referenceId;
    private Long levelId;
    private String scopeCode;
    private String scopeName;
    private String levelCode;
    private String levelName;
    private String roleCode;
    private Integer nodeEnabled;
    private String businessNo;
    private String businessTitle;
}
