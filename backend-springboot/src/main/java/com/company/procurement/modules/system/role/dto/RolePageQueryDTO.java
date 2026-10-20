package com.company.procurement.modules.system.role.dto;

import com.company.procurement.common.dto.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class RolePageQueryDTO extends PageQuery {

    private String roleCode;
    private String roleName;
    private Integer status;
}
