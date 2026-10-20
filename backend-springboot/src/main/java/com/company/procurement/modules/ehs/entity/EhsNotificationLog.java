package com.company.procurement.modules.ehs.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.procurement.common.entity.BaseEntity;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ehs_notification_log")
public class EhsNotificationLog extends BaseEntity {
    @TableField("biz_type")
    private String bizType;
    @TableField("biz_id")
    private Long bizId;
    @TableField("recipient_user_id")
    private Long recipientUserId;
    @TableField("channel")
    private String channel;
    @TableField("event_type")
    private String eventType;
    @TableField("title")
    private String title;
    @TableField("content")
    private String content;
    @TableField("status")
    private String status;
    @TableField("external_message_id")
    private String externalMessageId;
    @TableField("error_message")
    private String errorMessage;
    @TableField("sent_at")
    private LocalDateTime sentAt;
}
