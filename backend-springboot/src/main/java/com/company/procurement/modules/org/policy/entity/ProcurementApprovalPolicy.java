package com.company.procurement.modules.org.policy.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.procurement.common.entity.BaseEntity;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("procurement_approval_policy")
public class ProcurementApprovalPolicy extends BaseEntity {

    @TableField("policy_code")
    private String policyCode;

    @TableField("policy_name")
    private String policyName;

    @TableField("scope_id")
    private Long scopeId;

    @TableField("enabled")
    private Integer enabled;

    @TableField("pool_push_day")
    private Integer poolPushDay;

    @TableField("region_threshold")
    private BigDecimal regionThreshold;

    @TableField("director_threshold")
    private BigDecimal directorThreshold;

    @TableField("skip_self_approval")
    private Integer skipSelfApproval;

    @TableField("skip_duplicate_approver")
    private Integer skipDuplicateApprover;

    @TableField("skip_lower_when_higher_approved")
    private Integer skipLowerWhenHigherApproved;

    @TableField("allow_same_user_multi_node")
    private Integer allowSameUserMultiNode;

    @TableField("four_eye_required")
    private Integer fourEyeRequired;

    @TableField("over_price_rule")
    private String overPriceRule;

    @TableField("remark")
    private String remark;
}
