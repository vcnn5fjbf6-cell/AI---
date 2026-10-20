package com.company.procurement.modules.ehs.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class EhsRegistryVO {
    private Long id;
    private String recordNo;
    private String moduleType;
    private String title;
    private String category;
    private String subType;
    private String building;
    private String location;
    private String riskLevel;
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
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
