package com.company.procurement.modules.platform.permission.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PlatformPermissionReviewDTO {

    @NotBlank(message = "审批动作不能为空")
    private String action;

    private String comment;
}
