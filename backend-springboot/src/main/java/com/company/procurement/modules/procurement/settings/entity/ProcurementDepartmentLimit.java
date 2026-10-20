package com.company.procurement.modules.procurement.settings.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.procurement.common.entity.BaseEntity;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("procurement_department_limit")
public class ProcurementDepartmentLimit extends BaseEntity {

    @TableField("dept_code")
    private String deptCode;

    @TableField("dept_name")
    private String deptName;

    @TableField("single_request_limit")
    private BigDecimal singleRequestLimit;

    @TableField("monthly_limit")
    private BigDecimal monthlyLimit;

    @TableField("status")
    private Integer status;

    @TableField("remark")
    private String remark;
}
