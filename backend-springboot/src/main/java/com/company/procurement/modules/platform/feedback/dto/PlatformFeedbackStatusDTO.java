package com.company.procurement.modules.platform.feedback.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PlatformFeedbackStatusDTO {

    @NotBlank(message = "处理状态不能为空")
    private String status;

    private String handleComment;
}
