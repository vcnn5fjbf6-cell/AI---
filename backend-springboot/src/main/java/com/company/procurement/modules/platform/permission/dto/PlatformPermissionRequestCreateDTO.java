package com.company.procurement.modules.platform.permission.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PlatformPermissionRequestCreateDTO {

    @NotNull(message = "申请单元不能为空")
    private Long unitId;

    @NotBlank(message = "申请系统不能为空")
    private String appCode;

    private String roleCode;

    private String reason;
}
