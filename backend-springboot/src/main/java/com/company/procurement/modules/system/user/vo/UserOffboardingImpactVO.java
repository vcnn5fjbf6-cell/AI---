package com.company.procurement.modules.system.user.vo;

import lombok.Data;

import java.util.List;

@Data
public class UserOffboardingImpactVO {

    private Long userId;
    private String userName;
    private Integer status;
    private Integer approvalConfigCount;
    private Integer pendingTaskCount;
    private List<UserOffboardingImpactItemVO> impacts;
}
