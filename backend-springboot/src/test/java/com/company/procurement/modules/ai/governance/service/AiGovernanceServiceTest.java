package com.company.procurement.modules.ai.governance.service;

import com.company.procurement.common.security.AuthContextHolder;
import com.company.procurement.common.security.AuthUser;
import com.company.procurement.modules.ai.governance.dto.AiProposalReviewDTO;
import com.company.procurement.modules.ai.governance.entity.AiChangeProposal;
import com.company.procurement.modules.ai.governance.entity.AiContentRevision;
import com.company.procurement.modules.ai.governance.entity.AiKnowledgeBase;
import com.company.procurement.modules.ai.governance.entity.AiSkillDefinition;
import com.company.procurement.modules.ai.governance.mapper.AiChangeProposalMapper;
import com.company.procurement.modules.ai.governance.mapper.AiContentRevisionMapper;
import com.company.procurement.modules.ai.governance.mapper.AiKnowledgeBaseMapper;
import com.company.procurement.modules.ai.governance.mapper.AiSkillDefinitionMapper;
import com.company.procurement.modules.ai.governance.service.AiGovernanceService.ModelImprovementSuggestion;
import com.company.procurement.modules.ai.governance.service.AiGovernanceService.RuntimeSkill;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiGovernanceServiceTest {

    @Mock
    private AiKnowledgeBaseMapper knowledgeBaseMapper;
    @Mock
    private AiSkillDefinitionMapper skillMapper;
    @Mock
    private AiContentRevisionMapper revisionMapper;
    @Mock
    private AiChangeProposalMapper proposalMapper;

    @AfterEach
    void clearAuth() {
        AuthContextHolder.clear();
    }

    @Test
    void runtimeSkillCombinesCurrentInstructionsAndKnowledge() {
        AiSkillDefinition skill = skill(7L, 3);
        AiKnowledgeBase knowledge = knowledge(11L, 4, "当前采购合规规则正文");
        when(skillMapper.selectOne(any())).thenReturn(skill);
        when(knowledgeBaseMapper.selectOne(any())).thenReturn(knowledge);

        RuntimeSkill result = service().runtimeSkill("procurement-compliance-review");

        assertThat(result.revisionNo()).isEqualTo(3);
        assertThat(result.prompt())
            .contains("当前Skill执行指令")
            .contains("采购合规基础规则")
            .contains("当前采购合规规则正文")
            .contains("修订 4");
    }

    @Test
    void modelSuggestionIsStoredAsPendingInsteadOfChangingContent() {
        when(knowledgeBaseMapper.selectOne(any())).thenReturn(knowledge(11L, 4, "当前正文"));

        service().captureModelSuggestions(List.of(new ModelImprovementSuggestion(
            "KNOWLEDGE_BASE",
            "procurement-compliance-rules",
            "建议替换后的完整正文",
            "发现可复用规则缺口"
        )), "PURCHASE_REQUEST_COMPLIANCE_REVIEW", 100L);

        ArgumentCaptor<AiChangeProposal> captor = ArgumentCaptor.forClass(AiChangeProposal.class);
        verify(proposalMapper).insert(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("PENDING");
        assertThat(captor.getValue().getProposedContent()).isEqualTo("建议替换后的完整正文");
        assertThat(captor.getValue().getSourceBizId()).isEqualTo(100L);
    }

    @Test
    void adminApprovalCreatesRevisionAndThenMarksProposalApproved() {
        AiChangeProposal proposal = new AiChangeProposal();
        proposal.setId(21L);
        proposal.setTargetType("KNOWLEDGE_BASE");
        proposal.setTargetId(11L);
        proposal.setTargetCode("procurement-compliance-rules");
        proposal.setBaseRevisionNo(4);
        proposal.setProposedContent("管理员审核后的新正文");
        proposal.setRationale("补充通用规则");
        proposal.setStatus("PENDING");
        when(proposalMapper.selectById(21L)).thenReturn(proposal);
        AiKnowledgeBase knowledge = knowledge(11L, 4, "旧正文");
        when(knowledgeBaseMapper.selectById(11L)).thenReturn(knowledge);
        AuthContextHolder.set(AuthUser.builder().userId(1L).username("admin").realName("管理员").build());
        AiProposalReviewDTO dto = new AiProposalReviewDTO();
        dto.setAction("APPROVE");
        dto.setComment("同意补充");

        service().reviewProposal(21L, dto);

        ArgumentCaptor<AiKnowledgeBase> knowledgeCaptor = ArgumentCaptor.forClass(AiKnowledgeBase.class);
        verify(knowledgeBaseMapper).updateById(knowledgeCaptor.capture());
        assertThat(knowledgeCaptor.getValue().getContent()).isEqualTo("管理员审核后的新正文");
        assertThat(knowledgeCaptor.getValue().getRevisionNo()).isEqualTo(5);
        ArgumentCaptor<AiContentRevision> revisionCaptor = ArgumentCaptor.forClass(AiContentRevision.class);
        verify(revisionMapper).insert(revisionCaptor.capture());
        assertThat(revisionCaptor.getValue().getChangeSource()).isEqualTo("MODEL_APPROVED");
        ArgumentCaptor<AiChangeProposal> proposalCaptor = ArgumentCaptor.forClass(AiChangeProposal.class);
        verify(proposalMapper).updateById(proposalCaptor.capture());
        assertThat(proposalCaptor.getValue().getStatus()).isEqualTo("APPROVED");
        assertThat(proposalCaptor.getValue().getReviewerName()).isEqualTo("管理员");
    }

    private AiGovernanceService service() {
        return new AiGovernanceService(
            knowledgeBaseMapper,
            skillMapper,
            revisionMapper,
            proposalMapper,
            new ObjectMapper()
        );
    }

    private AiSkillDefinition skill(Long id, int revision) {
        AiSkillDefinition skill = new AiSkillDefinition();
        skill.setId(id);
        skill.setSkillCode("procurement-compliance-review");
        skill.setSkillName("采购合规审查");
        skill.setInstructions("当前Skill执行指令");
        skill.setKnowledgeBaseCodes("procurement-compliance-rules");
        skill.setStatus(1);
        skill.setRevisionNo(revision);
        return skill;
    }

    private AiKnowledgeBase knowledge(Long id, int revision, String content) {
        AiKnowledgeBase knowledge = new AiKnowledgeBase();
        knowledge.setId(id);
        knowledge.setKnowledgeCode("procurement-compliance-rules");
        knowledge.setKnowledgeName("采购合规基础规则");
        knowledge.setContent(content);
        knowledge.setStatus(1);
        knowledge.setRevisionNo(revision);
        return knowledge;
    }
}
