package com.company.procurement.modules.platform.aimessagelog.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProcurementAiMessageLogVO {

    private Long id;
    private String sourceChannel;
    private String eventType;
    private String messageId;
    private String chatId;
    private String chatType;
    private String messageType;
    private String sourceUserKey;
    private String senderOpenId;
    private String senderUserId;
    private String senderUnionId;
    private Long applicantUserId;
    private String username;
    private String realName;
    private Long sessionId;
    private String sessionNo;
    private Integer candidateNo;
    private Long monthlyDemandId;
    private String monthlyDemandNo;
    private Long purchaseRequestId;
    private String purchaseRequestNo;
    private String intentCode;
    private String intentName;
    private String processStatus;
    private String rawText;
    private String rawContent;
    private String rawPayload;
    private String difyRequestJson;
    private String difyResponseText;
    private String difyParsedJson;
    private String replyText;
    private String resultSummary;
    private String errorCode;
    private String errorMessage;
    private Long durationMs;
    private LocalDateTime occurredAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
