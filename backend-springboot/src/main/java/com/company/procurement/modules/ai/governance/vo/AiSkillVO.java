package com.company.procurement.modules.ai.governance.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class AiSkillVO {
    private Long id;
    private String skillCode;
    private String skillName;
    private String description;
    private String instructions;
    private List<String> knowledgeBaseCodes = new ArrayList<>();
    private Integer status;
    private Integer revisionNo;
    private Long updatedBy;
    private LocalDateTime updatedAt;
}
