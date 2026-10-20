package com.company.procurement.modules.ehs.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class EhsPermitApprovalDTO {
    @NotBlank(message = "审批动作不能为空")
    private String action;
    private String comment;
}
