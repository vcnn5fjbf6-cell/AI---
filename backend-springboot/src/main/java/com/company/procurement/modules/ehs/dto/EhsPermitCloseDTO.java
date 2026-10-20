package com.company.procurement.modules.ehs.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class EhsPermitCloseDTO {
    @NotBlank(message = "关单结果不能为空")
    private String outcome;
    private LocalDateTime actualEndAt;
    @NotBlank(message = "关单签名不能为空")
    private String signature;
    private String note;
    private List<String> photos = new ArrayList<>();
}
