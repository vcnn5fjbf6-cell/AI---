package com.company.procurement.modules.ai.governance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.company.procurement.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_skill_definition")
public class AiSkillDefinition extends BaseEntity {

    private String skillCode;
    private String skillName;
    private String description;
    private String instructions;
    private String knowledgeBaseCodes;
    private Integer status;
    private Integer revisionNo;
}
