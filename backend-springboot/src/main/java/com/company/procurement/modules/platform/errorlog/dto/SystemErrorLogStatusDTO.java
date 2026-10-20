package com.company.procurement.modules.platform.errorlog.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SystemErrorLogStatusDTO {

    @NotBlank(message = "Status must not be blank")
    private String status;

    private String fixRemark;
}
