package com.company.procurement.modules.ehs.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class EhsPublicApprovalRecordVO {
    private Integer nodeNo;
    private String nodeName;
    private String approverName;
    private String action;
    private String approvalComment;
    private LocalDateTime operatedAt;
}
