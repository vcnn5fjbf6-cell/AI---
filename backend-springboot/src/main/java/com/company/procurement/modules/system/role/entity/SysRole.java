package com.company.procurement.modules.system.role.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.procurement.common.entity.BaseEntity;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("sys_role")
public class SysRole extends BaseEntity {

    @TableField("role_code")
    private String roleCode;

    @TableField("role_name")
    private String roleName;

    @TableField("role_type")
    private String roleType;

    @TableField("data_scope_type")
    private String dataScopeType;

    @TableField("approval_amount_min")
    private BigDecimal approvalAmountMin;

    @TableField("approval_amount_max")
    private BigDecimal approvalAmountMax;

    @TableField("status")
    private Integer status;

    @TableField("description")
    private String description;
}
