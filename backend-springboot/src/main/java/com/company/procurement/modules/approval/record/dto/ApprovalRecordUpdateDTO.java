package com.company.procurement.modules.approval.record.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ApprovalRecordUpdateDTO {

    @NotNull(message = "Node number must not be null")
    private Integer nodeNo;

    @NotBlank(message = "Node name must not be blank")
    private String nodeName;

    @NotNull(message = "Approver user ID must not be null")
    private Long approverUserId;

    private String approverRoleCode;

    @NotBlank(message = "Action must not be blank")
    private String action;

    private String fromStatus;
    private String toStatus;
    private String approvalComment;
    private LocalDateTime operatedAt;

    @NotNull(message = "Submit round must not be null")
    private Integer submitRound;

    @NotNull(message = "Final node flag must not be null")
    private Integer isFinalNode;

    @NotNull(message = "Can resubmit flag must not be null")
    private Integer canResubmit;

    private Long transferToUserId;

    @NotBlank(message = "Record status must not be blank")
    private String status;

    private String remark;
}
