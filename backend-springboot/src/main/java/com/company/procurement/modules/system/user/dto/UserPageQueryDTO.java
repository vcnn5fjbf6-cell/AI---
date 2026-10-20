package com.company.procurement.modules.system.user.dto;

import com.company.procurement.common.dto.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class UserPageQueryDTO extends PageQuery {

    private String username;
    private String realName;
    private String employeeNo;
    private String userType;
    private String accountCategory;
    private Long regionId;
    private Long supplierId;
    private Integer status;
}
