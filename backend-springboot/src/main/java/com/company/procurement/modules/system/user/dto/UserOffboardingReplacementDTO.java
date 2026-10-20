package com.company.procurement.modules.system.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UserOffboardingReplacementDTO {

    @NotBlank(message = "审批影响项不能为空")
    private String impactKey;

    @NotNull(message = "替换人员不能为空")
    private Long replacementUserId;
}
