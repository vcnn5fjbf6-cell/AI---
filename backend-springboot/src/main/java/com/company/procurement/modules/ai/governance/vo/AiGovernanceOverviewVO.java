package com.company.procurement.modules.ai.governance.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AiGovernanceOverviewVO {
    private List<AiKnowledgeBaseVO> knowledgeBases = new ArrayList<>();
    private List<AiSkillVO> skills = new ArrayList<>();
    private List<AiChangeProposalVO> proposals = new ArrayList<>();
    private long pendingProposalCount;
}
