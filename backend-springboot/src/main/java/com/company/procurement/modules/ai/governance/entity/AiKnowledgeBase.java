package com.company.procurement.modules.ai.governance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.company.procurement.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_knowledge_base")
public class AiKnowledgeBase extends BaseEntity {

    private String knowledgeCode;
    private String knowledgeName;
    private String description;
    private String content;
    private Integer status;
    private Integer revisionNo;
}
