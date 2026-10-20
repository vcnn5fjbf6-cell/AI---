package com.company.procurement.modules.ai.governance.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AiChangeProposalVO {
    private Long id;
    private String targetType;
    private Long targetId;
    private String targetCode;
    private Integer baseRevisionNo;
    private String currentContent;
    private String proposedContent;
    private String rationale;
    private String sourceBizType;
    private Long sourceBizId;
    private String status;
    private Long reviewerUserId;
    private String reviewerName;
    private String reviewComment;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
}
