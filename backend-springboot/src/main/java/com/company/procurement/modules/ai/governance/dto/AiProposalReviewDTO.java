package com.company.procurement.modules.ai.governance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AiProposalReviewDTO {

    @NotBlank(message = "审核动作不能为空")
    @Pattern(regexp = "^(APPROVE|REJECT)$", message = "审核动作只能是APPROVE或REJECT")
    private String action;

    @Size(max = 1000, message = "审核意见不能超过1000个字符")
    private String comment;
}
