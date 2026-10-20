package com.company.procurement.modules.approval.record.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.procurement.common.entity.BaseEntity;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("approval_task")
public class ApprovalTask extends BaseEntity {

    @TableField("instance_id")
    private Long instanceId;

    @TableField("purchase_request_id")
    private Long purchaseRequestId;

    @TableField("level_no")
    private Integer levelNo;

    @TableField("level_code")
    private String levelCode;

    @TableField("level_name")
    private String levelName;

    @TableField("role_code")
    private String roleCode;

    @TableField("candidate_user_id")
    private Long candidateUserId;

    @TableField("pass_rule")
    private String passRule;

    @TableField("status")
    private String status;

    @TableField("action")
    private String action;

    @TableField("action_comment")
    private String actionComment;

    @TableField("action_at")
    private LocalDateTime actionAt;

    @TableField("closed_reason")
    private String closedReason;

    @TableField("due_at")
    private LocalDateTime dueAt;

    @TableField("reminder_stage")
    private Integer reminderStage;

    @TableField("submit_round")
    private Integer submitRound;
}
