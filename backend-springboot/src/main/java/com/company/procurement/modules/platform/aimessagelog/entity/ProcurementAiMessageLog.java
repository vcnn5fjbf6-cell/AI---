package com.company.procurement.modules.platform.aimessagelog.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.procurement.common.entity.BaseEntity;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("procurement_ai_message_log")
public class ProcurementAiMessageLog extends BaseEntity {

    @TableField("source_channel")
    private String sourceChannel;

    @TableField("event_type")
    private String eventType;

    @TableField("message_id")
    private String messageId;

    @TableField("chat_id")
    private String chatId;

    @TableField("chat_type")
    private String chatType;

    @TableField("message_type")
    private String messageType;

    @TableField("source_user_key")
    private String sourceUserKey;

    @TableField("sender_open_id")
    private String senderOpenId;

    @TableField("sender_user_id")
    private String senderUserId;

    @TableField("sender_union_id")
    private String senderUnionId;

    @TableField("applicant_user_id")
    private Long applicantUserId;

    @TableField("username")
    private String username;

    @TableField("real_name")
    private String realName;

    @TableField("session_id")
    private Long sessionId;

    @TableField("session_no")
    private String sessionNo;

    @TableField("candidate_no")
    private Integer candidateNo;

    @TableField("monthly_demand_id")
    private Long monthlyDemandId;

    @TableField("monthly_demand_no")
    private String monthlyDemandNo;

    @TableField("purchase_request_id")
    private Long purchaseRequestId;

    @TableField("purchase_request_no")
    private String purchaseRequestNo;

    @TableField("intent_code")
    private String intentCode;

    @TableField("intent_name")
    private String intentName;

    @TableField("process_status")
    private String processStatus;

    @TableField("raw_text")
    private String rawText;

    @TableField("raw_content")
    private String rawContent;

    @TableField("raw_payload")
    private String rawPayload;

    @TableField("dify_request_json")
    private String difyRequestJson;

    @TableField("dify_response_text")
    private String difyResponseText;

    @TableField("dify_parsed_json")
    private String difyParsedJson;

    @TableField("reply_text")
    private String replyText;

    @TableField("result_summary")
    private String resultSummary;

    @TableField("error_code")
    private String errorCode;

    @TableField("error_message")
    private String errorMessage;

    @TableField("duration_ms")
    private Long durationMs;

    @TableField("occurred_at")
    private LocalDateTime occurredAt;
}
