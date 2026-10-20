package com.company.procurement.modules.integration.feishu.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FeishuApprovalCardNotificationVO {

    private Long id;

    private Long approvalTaskId;

    private Long purchaseRequestId;

    private String requestNo;

    private String requestTitle;

    private String taskLevelName;

    private String taskStatus;

    private Long approverUserId;

    private String approverName;

    private String approverEmail;

    private Long recipientUserId;

    private String recipientName;

    private String receiveIdType;

    private String receiveId;

    private String messageId;

    private String status;

    private String action;

    private Integer retryCount;

    private String lastError;

    private LocalDateTime sentAt;

    private LocalDateTime actedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
