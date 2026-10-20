package com.company.procurement.modules.ehs.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class EhsPermitSampleDTO {
    @NotNull(message = "采样时间不能为空")
    private LocalDateTime sampledAt;
    @NotBlank(message = "采样位置不能为空")
    private String position;
    private BigDecimal oxygen;
    private BigDecimal lel;
    private BigDecimal carbonMonoxide;
    private BigDecimal hydrogenSulfide;
    @NotBlank(message = "记录人不能为空")
    private String recorder;
    private String signature;
}
