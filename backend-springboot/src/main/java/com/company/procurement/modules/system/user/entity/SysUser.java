package com.company.procurement.modules.system.user.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.procurement.common.entity.BaseEntity;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_user")
public class SysUser extends BaseEntity {

    @TableField("username")
    private String username;

    @TableField("password_hash")
    private String passwordHash;

    @TableField("real_name")
    private String realName;

    @TableField("employee_no")
    private String employeeNo;

    @TableField("user_type")
    private String userType;

    @TableField("supplier_id")
    private Long supplierId;

    @TableField("region_id")
    private Long regionId;

    @TableField("dept_code")
    private String deptCode;

    @TableField("dept_name")
    private String deptName;

    @TableField("mobile")
    private String mobile;

    @TableField("email")
    private String email;

    @TableField("feishu_user_id")
    private String feishuUserId;

    @TableField("feishu_open_id")
    private String feishuOpenId;

    @TableField("feishu_union_id")
    private String feishuUnionId;

    @TableField("status")
    private Integer status;

    @TableField("data_scope_type")
    private String dataScopeType;

    @TableField("session_secret")
    private String sessionSecret;

    @TableField("last_login_at")
    private LocalDateTime lastLoginAt;

    @TableField("last_login_ip")
    private String lastLoginIp;

    @TableField("must_change_password")
    private Integer mustChangePassword;

    @TableField("password_changed_at")
    private LocalDateTime passwordChangedAt;

    @TableField("remark")
    private String remark;
}
