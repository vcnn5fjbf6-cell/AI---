package com.company.procurement.modules.procurement.assistant.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ProcurementAiStartDTO {

    @NotBlank(message = "rawText must not be blank")
    private String rawText;

    private String imageKey;

    private String imageUrl;

    private String imageDataUrl;

    private String reqTitle;

    private String reqType;

    private String planMonth;

    private String deptCode;

    private String deptName;

    private String budgetCode;

    private Integer urgencyLevel = 1;

    private Boolean emergency = false;

    private String needDate;

    private String remark;
}
