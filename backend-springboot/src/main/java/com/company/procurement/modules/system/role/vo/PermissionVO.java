package com.company.procurement.modules.system.role.vo;

import lombok.Data;

@Data
public class PermissionVO {

    private Long id;
    private String permCode;
    private String permName;
    private String moduleCode;
    private String permType;
    private Long parentId;
    private String path;
    private String httpMethod;
    private Integer sortNo;
    private Integer status;
    private String description;
}
