package com.company.procurement.modules.integration.feishu.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.procurement.common.entity.BaseEntity;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("feishu_approval_card_notification")
public class FeishuApprovalCardNotification extends BaseEntity {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_SENT = "SENT";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_RETURNED = "RETURNED";
    public static final String STATUS_CLOSED = "CLOSED";

    @TableField("approval_task_id")
    private Long approvalTaskId;

    @TableField("purchase_request_id")
    private Long purchaseRequestId;

    @TableField("approver_user_id")
    private Long approverUserId;

    @TableField("recipient_user_id")
    private Long recipientUserId;

    @TableField("submit_round")
    private Integer submitRound;

    @TableField("receive_id_type")
    private String receiveIdType;

    @TableField("receive_id")
    private String receiveId;

    @TableField("message_id")
    private String messageId;

    @TableField("card_token")
    private String cardToken;

    @TableField("status")
    private String status;

    @TableField("action")
    private String action;

    @TableField("retry_count")
    private Integer retryCount;

    @TableField("last_error")
    private String lastError;

    @TableField("sent_at")
    private LocalDateTime sentAt;

    @TableField("acted_at")
    private LocalDateTime actedAt;
}
