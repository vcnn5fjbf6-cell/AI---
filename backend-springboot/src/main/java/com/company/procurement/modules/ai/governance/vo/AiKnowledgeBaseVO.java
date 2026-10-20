package com.company.procurement.modules.ai.governance.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AiKnowledgeBaseVO {
    private Long id;
    private String knowledgeCode;
    private String knowledgeName;
    private String description;
    private String content;
    private Integer status;
    private Integer revisionNo;
    private Long updatedBy;
    private LocalDateTime updatedAt;
}
