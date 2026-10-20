package com.company.procurement.modules.system.user.vo;

import lombok.Data;

@Data
public class FeishuDepartmentSearchVO {

    private String name;

    private String departmentId;

    private String openDepartmentId;

    private String parentDepartmentId;

    private String path;
}
