package com.company.procurement.modules.approval.record.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.procurement.common.entity.BaseEntity;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("approval_instance")
public class ApprovalInstance extends BaseEntity {

    @TableField("instance_no")
    private String instanceNo;

    @TableField("biz_type")
    private String bizType;

    @TableField("biz_id")
    private Long bizId;

    @TableField("purchase_request_id")
    private Long purchaseRequestId;

    @TableField("current_node_no")
    private Integer currentNodeNo;

    @TableField("current_level_no")
    private Integer currentLevelNo;

    @TableField("current_level_code")
    private String currentLevelCode;

    @TableField("current_pass_rule")
    private String currentPassRule;

    @TableField("total_node_no")
    private Integer totalNodeNo;

    @TableField("current_approver_id")
    private Long currentApproverId;

    @TableField("last_action")
    private String lastAction;

    @TableField("last_action_by")
    private Long lastActionBy;

    @TableField("last_action_at")
    private LocalDateTime lastActionAt;

    @TableField("status")
    private String status;

    @TableField("started_at")
    private LocalDateTime startedAt;

    @TableField("ended_at")
    private LocalDateTime endedAt;

    @TableField("remark")
    private String remark;
}
