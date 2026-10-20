package com.company.procurement.modules.approval.record.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.procurement.common.entity.BaseEntity;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("approval_record")
public class ApprovalRecord extends BaseEntity {

    @TableField("instance_id")
    private Long instanceId;

    @TableField("purchase_request_id")
    private Long purchaseRequestId;

    @TableField("node_no")
    private Integer nodeNo;

    @TableField("node_name")
    private String nodeName;

    @TableField("approver_user_id")
    private Long approverUserId;

    @TableField("approver_role_code")
    private String approverRoleCode;

    @TableField("action")
    private String action;

    @TableField("from_status")
    private String fromStatus;

    @TableField("to_status")
    private String toStatus;

    @TableField("approval_comment")
    private String approvalComment;

    @TableField("operated_at")
    private LocalDateTime operatedAt;

    @TableField("submit_round")
    private Integer submitRound;

    @TableField("is_final_node")
    private Integer isFinalNode;

    @TableField("can_resubmit")
    private Integer canResubmit;

    @TableField("transfer_to_user_id")
    private Long transferToUserId;

    @TableField("status")
    private String status;

    @TableField("remark")
    private String remark;
}
