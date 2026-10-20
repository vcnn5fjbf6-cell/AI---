package com.company.procurement.modules.system.role.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class RoleVO {

    private Long id;
    private String roleCode;
    private String roleName;
    private String roleType;
    private String dataScopeType;
    private BigDecimal approvalAmountMin;
    private BigDecimal approvalAmountMax;
    private Integer status;
    private String description;
    private List<Long> permissionIds;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
