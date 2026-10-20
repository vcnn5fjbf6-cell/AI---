package com.company.procurement.modules.system.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class UserUpdateDTO {

    private String username;

    private String password;

    @NotBlank(message = "姓名必填")
    private String realName;

    private String employeeNo;

    @NotBlank(message = "用户类型必填")
    private String userType;

    private Long supplierId;

    private Long regionId;

    private String deptCode;
    private String deptName;
    private String mobile;
    private String email;
    private String feishuUserId;
    private String feishuOpenId;
    private String feishuUnionId;

    @NotNull(message = "状态必填")
    private Integer status;

    @NotBlank(message = "数据范围必填")
    private String dataScopeType;

    private Integer mustChangePassword;
    private String remark;

    private List<Long> roleIds;
}
