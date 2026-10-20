package com.company.procurement.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class FeishuCallbackDTO {

    @NotBlank(message = "授权码不能为空")
    private String code;

    @NotBlank(message = "登录状态不能为空")
    private String state;

    private String redirectUri;
}
