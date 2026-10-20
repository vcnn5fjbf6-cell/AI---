package com.company.procurement.modules.system.role.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class RoleUpdateDTO {

    @NotBlank(message = "Role code must not be blank")
    private String roleCode;

    @NotBlank(message = "Role name must not be blank")
    private String roleName;

    @NotBlank(message = "Role type must not be blank")
    private String roleType;

    private String dataScopeType;

    @NotNull(message = "Approval minimum amount must not be null")
    private BigDecimal approvalAmountMin;

    @NotNull(message = "Approval maximum amount must not be null")
    private BigDecimal approvalAmountMax;

    @NotNull(message = "Status must not be null")
    private Integer status;

    private String description;

    private List<Long> permissionIds;
}
