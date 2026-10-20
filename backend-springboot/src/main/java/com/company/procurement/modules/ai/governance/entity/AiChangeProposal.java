package com.company.procurement.modules.ai.governance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.company.procurement.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_change_proposal")
public class AiChangeProposal extends BaseEntity {

    private String targetType;
    private Long targetId;
    private String targetCode;
    private Integer baseRevisionNo;
    private String proposalHash;
    private String proposedContent;
    private String rationale;
    private String sourceBizType;
    private Long sourceBizId;
    private String status;
    private Long reviewerUserId;
    private String reviewerName;
    private String reviewComment;
    private LocalDateTime reviewedAt;
}
