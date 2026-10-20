package com.company.procurement.modules.system.role.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class RolePermissionAssignDTO {

    @NotNull(message = "Permission ID list must not be null")
    private List<Long> permissionIds;
}
