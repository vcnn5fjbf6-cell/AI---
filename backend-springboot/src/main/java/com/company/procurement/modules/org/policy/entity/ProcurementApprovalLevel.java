package com.company.procurement.modules.org.policy.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.procurement.common.entity.BaseEntity;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("procurement_approval_level")
public class ProcurementApprovalLevel extends BaseEntity {

    @TableField("policy_id")
    private Long policyId;

    @TableField("level_no")
    private Integer levelNo;

    @TableField("level_code")
    private String levelCode;

    @TableField("level_name")
    private String levelName;

    @TableField("role_code")
    private String roleCode;

    @TableField("enabled")
    private Integer enabled;

    @TableField("trigger_mode")
    private String triggerMode;

    @TableField("pass_rule")
    private String passRule;

    @TableField("amount_min")
    private BigDecimal amountMin;

    @TableField("amount_max")
    private BigDecimal amountMax;

    @TableField("skip_if_empty")
    private Integer skipIfEmpty;

    @TableField("sort_no")
    private Integer sortNo;

    @TableField("remark")
    private String remark;
}
