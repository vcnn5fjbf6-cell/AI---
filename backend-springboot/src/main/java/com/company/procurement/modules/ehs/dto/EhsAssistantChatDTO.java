package com.company.procurement.modules.ehs.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class EhsAssistantChatDTO {
    private Long sessionId;
    @NotBlank(message = "请输入问题")
    private String message;
}
