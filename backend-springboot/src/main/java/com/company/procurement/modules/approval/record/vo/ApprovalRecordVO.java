package com.company.procurement.modules.approval.record.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ApprovalRecordVO {

    private Long id;
    private Long instanceId;
    private Long purchaseRequestId;
    private Integer nodeNo;
    private String nodeName;
    private Long approverUserId;
    private String approverName;
    private String approverRoleCode;
    private String action;
    private String fromStatus;
    private String toStatus;
    private String approvalComment;
    private LocalDateTime operatedAt;
    private Integer submitRound;
    private Integer isFinalNode;
    private Integer canResubmit;
    private Long transferToUserId;
    private String transferToUserName;
    private String status;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
