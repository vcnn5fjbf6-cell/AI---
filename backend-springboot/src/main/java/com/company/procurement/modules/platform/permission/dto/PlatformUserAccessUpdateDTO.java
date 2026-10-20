package com.company.procurement.modules.platform.permission.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class PlatformUserAccessUpdateDTO {

    @NotNull(message = "登录状态不能为空")
    private Integer loginEnabled;

    private List<String> appCodes;
}
