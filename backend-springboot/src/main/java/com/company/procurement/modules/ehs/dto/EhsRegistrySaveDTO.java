package com.company.procurement.modules.ehs.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class EhsRegistrySaveDTO {
    @NotBlank(message = "模块类型不能为空")
    private String moduleType;
    @NotBlank(message = "名称不能为空")
    private String title;
    private String category;
    private String subType;
    private String building;
    private String location;
    private String riskLevel;
    @NotBlank(message = "状态不能为空")
    private String status;
    private Long ownerUserId;
    private String ownerName;
    private String handlerName;
    private BigDecimal quantity;
    private String unit;
    private LocalDateTime occurredAt;
    private LocalDateTime deadlineAt;
    private LocalDateTime completedAt;
    private Map<String, Object> details = new LinkedHashMap<>();
    private String remark;
}
