package com.company.procurement.modules.ai.governance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.procurement.common.exception.BizException;
import com.company.procurement.common.security.AuthContextHolder;
import com.company.procurement.common.security.AuthUser;
import com.company.procurement.modules.ai.governance.dto.AiKnowledgeBaseSaveDTO;
import com.company.procurement.modules.ai.governance.dto.AiProposalReviewDTO;
import com.company.procurement.modules.ai.governance.dto.AiSkillSaveDTO;
import com.company.procurement.modules.ai.governance.entity.AiChangeProposal;
import com.company.procurement.modules.ai.governance.entity.AiContentRevision;
import com.company.procurement.modules.ai.governance.entity.AiKnowledgeBase;
import com.company.procurement.modules.ai.governance.entity.AiSkillDefinition;
import com.company.procurement.modules.ai.governance.mapper.AiChangeProposalMapper;
import com.company.procurement.modules.ai.governance.mapper.AiContentRevisionMapper;
import com.company.procurement.modules.ai.governance.mapper.AiKnowledgeBaseMapper;
import com.company.procurement.modules.ai.governance.mapper.AiSkillDefinitionMapper;
import com.company.procurement.modules.ai.governance.vo.AiChangeProposalVO;
import com.company.procurement.modules.ai.governance.vo.AiContentRevisionVO;
import com.company.procurement.modules.ai.governance.vo.AiGovernanceOverviewVO;
import com.company.procurement.modules.ai.governance.vo.AiKnowledgeBaseVO;
import com.company.procurement.modules.ai.governance.vo.AiSkillVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiGovernanceService {

    public static final String TARGET_KNOWLEDGE_BASE = "KNOWLEDGE_BASE";
    public static final String TARGET_SKILL = "SKILL";
    private static final String STATUS_PENDING = "PENDING";

    private final AiKnowledgeBaseMapper knowledgeBaseMapper;
    private final AiSkillDefinitionMapper skillMapper;
    private final AiContentRevisionMapper revisionMapper;
    private final AiChangeProposalMapper proposalMapper;
    private final ObjectMapper objectMapper;

    public AiGovernanceOverviewVO overview() {
        AiGovernanceOverviewVO overview = new AiGovernanceOverviewVO();
        overview.setKnowledgeBases(knowledgeBaseMapper.selectList(new LambdaQueryWrapper<AiKnowledgeBase>()
            .orderByDesc(AiKnowledgeBase::getStatus)
            .orderByAsc(AiKnowledgeBase::getKnowledgeName)
        ).stream().map(this::toKnowledgeBaseVO).toList());
        overview.setSkills(skillMapper.selectList(new LambdaQueryWrapper<AiSkillDefinition>()
            .orderByDesc(AiSkillDefinition::getStatus)
            .orderByAsc(AiSkillDefinition::getSkillName)
        ).stream().map(this::toSkillVO).toList());
        List<AiChangeProposalVO> proposals = proposalMapper.selectList(new LambdaQueryWrapper<AiChangeProposal>()
            .orderByAsc(AiChangeProposal::getStatus)
            .orderByDesc(AiChangeProposal::getCreatedAt)
            .last("LIMIT 200")
        ).stream().map(this::toProposalVO).toList();
        overview.setProposals(proposals);
        overview.setPendingProposalCount(proposals.stream().filter(item -> STATUS_PENDING.equals(item.getStatus())).count());
        return overview;
    }

    public List<AiContentRevisionVO> revisions(String targetType, Long targetId) {
        String normalizedType = normalizeTargetType(targetType);
        return revisionMapper.selectList(new LambdaQueryWrapper<AiContentRevision>()
            .eq(AiContentRevision::getTargetType, normalizedType)
            .eq(AiContentRevision::getTargetId, targetId)
            .orderByDesc(AiContentRevision::getRevisionNo)
        ).stream().map(this::toRevisionVO).toList();
    }

    @Transactional
    public Long createKnowledgeBase(AiKnowledgeBaseSaveDTO dto) {
        ensureKnowledgeCodeAvailable(dto.getKnowledgeCode(), null);
        AiKnowledgeBase knowledgeBase = new AiKnowledgeBase();
        applyKnowledgeBase(knowledgeBase, dto);
        knowledgeBase.setRevisionNo(1);
        knowledgeBaseMapper.insert(knowledgeBase);
        saveRevision(TARGET_KNOWLEDGE_BASE, knowledgeBase.getId(), knowledgeBase.getKnowledgeCode(), 1,
            knowledgeSnapshot(knowledgeBase), "INITIAL", dto.getChangeSummary());
        return knowledgeBase.getId();
    }

    @Transactional
    public void updateKnowledgeBase(Long id, AiKnowledgeBaseSaveDTO dto) {
        AiKnowledgeBase knowledgeBase = requireKnowledgeBase(id);
        if (!knowledgeBase.getKnowledgeCode().equals(dto.getKnowledgeCode())) {
            throw new BizException("AI_KNOWLEDGE_CODE_IMMUTABLE", "知识库编码创建后不可修改；如需更换编码，请新建知识库");
        }
        applyKnowledgeBase(knowledgeBase, dto);
        int revisionNo = nextRevision(knowledgeBase.getRevisionNo());
        knowledgeBase.setRevisionNo(revisionNo);
        knowledgeBaseMapper.updateById(knowledgeBase);
        saveRevision(TARGET_KNOWLEDGE_BASE, id, knowledgeBase.getKnowledgeCode(), revisionNo,
            knowledgeSnapshot(knowledgeBase), "MANUAL", dto.getChangeSummary());
    }

    @Transactional
    public void deleteKnowledgeBase(Long id) {
        AiKnowledgeBase knowledgeBase = requireKnowledgeBase(id);
        boolean referenced = skillMapper.selectList(new LambdaQueryWrapper<AiSkillDefinition>()
                .eq(AiSkillDefinition::getStatus, 1))
            .stream()
            .map(AiSkillDefinition::getKnowledgeBaseCodes)
            .map(this::splitCodes)
            .anyMatch(codes -> codes.contains(knowledgeBase.getKnowledgeCode()));
        if (referenced) {
            throw new BizException("AI_KNOWLEDGE_IN_USE", "该知识库仍被启用的Skill引用，请先解除引用或停用Skill");
        }
        int revisionNo = nextRevision(knowledgeBase.getRevisionNo());
        saveRevision(TARGET_KNOWLEDGE_BASE, id, knowledgeBase.getKnowledgeCode(), revisionNo,
            knowledgeSnapshot(knowledgeBase), "DELETE", "删除知识库");
        knowledgeBaseMapper.deleteById(id);
    }

    @Transactional
    public Long createSkill(AiSkillSaveDTO dto) {
        ensureSkillCodeAvailable(dto.getSkillCode(), null);
        validateKnowledgeCodes(dto.getKnowledgeBaseCodes());
        AiSkillDefinition skill = new AiSkillDefinition();
        applySkill(skill, dto);
        skill.setRevisionNo(1);
        skillMapper.insert(skill);
        saveRevision(TARGET_SKILL, skill.getId(), skill.getSkillCode(), 1,
            skillSnapshot(skill), "INITIAL", dto.getChangeSummary());
        return skill.getId();
    }

    @Transactional
    public void updateSkill(Long id, AiSkillSaveDTO dto) {
        AiSkillDefinition skill = requireSkill(id);
        if (!skill.getSkillCode().equals(dto.getSkillCode())) {
            throw new BizException("AI_SKILL_CODE_IMMUTABLE", "Skill编码创建后不可修改；如需更换编码，请新建Skill");
        }
        validateKnowledgeCodes(dto.getKnowledgeBaseCodes());
        applySkill(skill, dto);
        int revisionNo = nextRevision(skill.getRevisionNo());
        skill.setRevisionNo(revisionNo);
        skillMapper.updateById(skill);
        saveRevision(TARGET_SKILL, id, skill.getSkillCode(), revisionNo,
            skillSnapshot(skill), "MANUAL", dto.getChangeSummary());
    }

    @Transactional
    public void deleteSkill(Long id) {
        AiSkillDefinition skill = requireSkill(id);
        int revisionNo = nextRevision(skill.getRevisionNo());
        saveRevision(TARGET_SKILL, id, skill.getSkillCode(), revisionNo,
            skillSnapshot(skill), "DELETE", "删除Skill");
        skillMapper.deleteById(id);
    }

    public RuntimeSkill runtimeSkill(String skillCode) {
        if (!StringUtils.hasText(skillCode)) {
            return null;
        }
        AiSkillDefinition skill = skillMapper.selectOne(new LambdaQueryWrapper<AiSkillDefinition>()
            .eq(AiSkillDefinition::getSkillCode, skillCode.trim())
            .eq(AiSkillDefinition::getStatus, 1)
            .last("LIMIT 1"));
        if (skill == null) {
            return null;
        }
        List<RuntimeKnowledge> knowledge = new ArrayList<>();
        for (String code : splitCodes(skill.getKnowledgeBaseCodes())) {
            AiKnowledgeBase item = knowledgeBaseMapper.selectOne(new LambdaQueryWrapper<AiKnowledgeBase>()
                .eq(AiKnowledgeBase::getKnowledgeCode, code)
                .eq(AiKnowledgeBase::getStatus, 1)
                .last("LIMIT 1"));
            if (item != null) {
                knowledge.add(new RuntimeKnowledge(item.getKnowledgeCode(), item.getKnowledgeName(), item.getRevisionNo(), item.getContent()));
            }
        }
        return new RuntimeSkill(skill.getSkillCode(), skill.getSkillName(), skill.getRevisionNo(), skill.getInstructions(), knowledge);
    }

    public void captureModelSuggestions(List<ModelImprovementSuggestion> suggestions,
                                        String sourceBizType,
                                        Long sourceBizId) {
        if (suggestions == null || suggestions.isEmpty()) {
            return;
        }
        suggestions.stream().limit(3).forEach(suggestion -> captureModelSuggestion(suggestion, sourceBizType, sourceBizId));
    }

    private void captureModelSuggestion(ModelImprovementSuggestion suggestion, String sourceBizType, Long sourceBizId) {
        if (suggestion == null || !StringUtils.hasText(suggestion.targetCode())
            || !StringUtils.hasText(suggestion.proposedContent()) || !StringUtils.hasText(suggestion.rationale())) {
            return;
        }
        String targetType;
        try {
            targetType = normalizeTargetType(suggestion.targetType());
        } catch (BizException ignored) {
            return;
        }
        Target target = findTarget(targetType, suggestion.targetCode());
        if (target == null || suggestion.proposedContent().length() > 30000) {
            return;
        }
        String hash = sha256(targetType + "|" + target.code() + "|" + target.revisionNo() + "|" + suggestion.proposedContent().trim());
        AiChangeProposal proposal = new AiChangeProposal();
        proposal.setTargetType(targetType);
        proposal.setTargetId(target.id());
        proposal.setTargetCode(target.code());
        proposal.setBaseRevisionNo(target.revisionNo());
        proposal.setProposalHash(hash);
        proposal.setProposedContent(suggestion.proposedContent().trim());
        proposal.setRationale(limit(suggestion.rationale().trim(), 1000));
        proposal.setSourceBizType(limit(sourceBizType, 64));
        proposal.setSourceBizId(sourceBizId);
        proposal.setStatus(STATUS_PENDING);
        try {
            proposalMapper.insert(proposal);
        } catch (DuplicateKeyException ignored) {
            log.debug("Ignore duplicate AI improvement proposal, targetType={}, targetCode={}", targetType, target.code());
        }
    }

    @Transactional
    public void reviewProposal(Long id, AiProposalReviewDTO dto) {
        AiChangeProposal proposal = proposalMapper.selectById(id);
        if (proposal == null) {
            throw new BizException("AI_PROPOSAL_NOT_FOUND", "改进建议不存在");
        }
        if (!STATUS_PENDING.equals(proposal.getStatus())) {
            throw new BizException("AI_PROPOSAL_ALREADY_REVIEWED", "该改进建议已经审核");
        }
        AuthUser reviewer = AuthContextHolder.requireUser();
        String action = dto.getAction().trim().toUpperCase(Locale.ROOT);
        if ("APPROVE".equals(action)) {
            applyApprovedProposal(proposal);
            proposal.setStatus("APPROVED");
        } else {
            proposal.setStatus("REJECTED");
        }
        proposal.setReviewerUserId(reviewer.getUserId());
        proposal.setReviewerName(limit(firstText(reviewer.getRealName(), reviewer.getUsername()), 128));
        proposal.setReviewComment(limit(dto.getComment(), 1000));
        proposal.setReviewedAt(LocalDateTime.now());
        proposalMapper.updateById(proposal);
    }

    private void applyApprovedProposal(AiChangeProposal proposal) {
        if (TARGET_KNOWLEDGE_BASE.equals(proposal.getTargetType())) {
            AiKnowledgeBase knowledgeBase = requireKnowledgeBase(proposal.getTargetId());
            ensureProposalIsCurrent(proposal, knowledgeBase.getRevisionNo());
            knowledgeBase.setContent(proposal.getProposedContent());
            int revisionNo = nextRevision(knowledgeBase.getRevisionNo());
            knowledgeBase.setRevisionNo(revisionNo);
            knowledgeBaseMapper.updateById(knowledgeBase);
            saveRevision(TARGET_KNOWLEDGE_BASE, knowledgeBase.getId(), knowledgeBase.getKnowledgeCode(), revisionNo,
                knowledgeSnapshot(knowledgeBase), "MODEL_APPROVED", proposal.getRationale());
            return;
        }
        AiSkillDefinition skill = requireSkill(proposal.getTargetId());
        ensureProposalIsCurrent(proposal, skill.getRevisionNo());
        skill.setInstructions(proposal.getProposedContent());
        int revisionNo = nextRevision(skill.getRevisionNo());
        skill.setRevisionNo(revisionNo);
        skillMapper.updateById(skill);
        saveRevision(TARGET_SKILL, skill.getId(), skill.getSkillCode(), revisionNo,
            skillSnapshot(skill), "MODEL_APPROVED", proposal.getRationale());
    }

    private Target findTarget(String targetType, String targetCode) {
        if (TARGET_KNOWLEDGE_BASE.equals(targetType)) {
            AiKnowledgeBase item = knowledgeBaseMapper.selectOne(new LambdaQueryWrapper<AiKnowledgeBase>()
                .eq(AiKnowledgeBase::getKnowledgeCode, targetCode.trim())
                .eq(AiKnowledgeBase::getStatus, 1)
                .last("LIMIT 1"));
            return item == null ? null : new Target(item.getId(), item.getKnowledgeCode(), item.getRevisionNo());
        }
        AiSkillDefinition item = skillMapper.selectOne(new LambdaQueryWrapper<AiSkillDefinition>()
            .eq(AiSkillDefinition::getSkillCode, targetCode.trim())
            .eq(AiSkillDefinition::getStatus, 1)
            .last("LIMIT 1"));
        return item == null ? null : new Target(item.getId(), item.getSkillCode(), item.getRevisionNo());
    }

    private void ensureProposalIsCurrent(AiChangeProposal proposal, Integer currentRevisionNo) {
        if (proposal.getBaseRevisionNo() == null || !proposal.getBaseRevisionNo().equals(currentRevisionNo)) {
            throw new BizException("AI_PROPOSAL_STALE", "目标内容已产生新修订，请重新生成建议或人工合并后再保存");
        }
    }

    private void applyKnowledgeBase(AiKnowledgeBase target, AiKnowledgeBaseSaveDTO dto) {
        target.setKnowledgeCode(dto.getKnowledgeCode().trim());
        target.setKnowledgeName(dto.getKnowledgeName().trim());
        target.setDescription(trim(dto.getDescription()));
        target.setContent(dto.getContent().trim());
        target.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
    }

    private void applySkill(AiSkillDefinition target, AiSkillSaveDTO dto) {
        target.setSkillCode(dto.getSkillCode().trim());
        target.setSkillName(dto.getSkillName().trim());
        target.setDescription(trim(dto.getDescription()));
        target.setInstructions(dto.getInstructions().trim());
        target.setKnowledgeBaseCodes(String.join(",", normalizeCodes(dto.getKnowledgeBaseCodes())));
        target.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
    }

    private void validateKnowledgeCodes(List<String> codes) {
        for (String code : normalizeCodes(codes)) {
            Long count = knowledgeBaseMapper.selectCount(new LambdaQueryWrapper<AiKnowledgeBase>()
                .eq(AiKnowledgeBase::getKnowledgeCode, code));
            if (count == null || count == 0) {
                throw new BizException("AI_KNOWLEDGE_NOT_FOUND", "引用的知识库不存在：" + code);
            }
        }
    }

    private void ensureKnowledgeCodeAvailable(String code, Long excludedId) {
        LambdaQueryWrapper<AiKnowledgeBase> wrapper = new LambdaQueryWrapper<AiKnowledgeBase>()
            .eq(AiKnowledgeBase::getKnowledgeCode, code.trim());
        if (excludedId != null) {
            wrapper.ne(AiKnowledgeBase::getId, excludedId);
        }
        if (knowledgeBaseMapper.selectCount(wrapper) > 0) {
            throw new BizException("AI_KNOWLEDGE_CODE_EXISTS", "知识库编码已存在");
        }
    }

    private void ensureSkillCodeAvailable(String code, Long excludedId) {
        LambdaQueryWrapper<AiSkillDefinition> wrapper = new LambdaQueryWrapper<AiSkillDefinition>()
            .eq(AiSkillDefinition::getSkillCode, code.trim());
        if (excludedId != null) {
            wrapper.ne(AiSkillDefinition::getId, excludedId);
        }
        if (skillMapper.selectCount(wrapper) > 0) {
            throw new BizException("AI_SKILL_CODE_EXISTS", "Skill编码已存在");
        }
    }

    private AiKnowledgeBase requireKnowledgeBase(Long id) {
        AiKnowledgeBase result = knowledgeBaseMapper.selectById(id);
        if (result == null) {
            throw new BizException("AI_KNOWLEDGE_NOT_FOUND", "知识库不存在");
        }
        return result;
    }

    private AiSkillDefinition requireSkill(Long id) {
        AiSkillDefinition result = skillMapper.selectById(id);
        if (result == null) {
            throw new BizException("AI_SKILL_NOT_FOUND", "Skill不存在");
        }
        return result;
    }

    private void saveRevision(String targetType, Long targetId, String targetCode, int revisionNo,
                              String snapshotJson, String source, String summary) {
        AiContentRevision revision = new AiContentRevision();
        revision.setTargetType(targetType);
        revision.setTargetId(targetId);
        revision.setTargetCode(targetCode);
        revision.setRevisionNo(revisionNo);
        revision.setSnapshotJson(snapshotJson);
        revision.setChangeSource(source);
        revision.setChangeSummary(limit(summary, 500));
        revisionMapper.insert(revision);
    }

    private String knowledgeSnapshot(AiKnowledgeBase item) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("knowledgeCode", item.getKnowledgeCode());
        snapshot.put("knowledgeName", item.getKnowledgeName());
        snapshot.put("description", item.getDescription());
        snapshot.put("content", item.getContent());
        snapshot.put("status", item.getStatus());
        return json(snapshot);
    }

    private String skillSnapshot(AiSkillDefinition item) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("skillCode", item.getSkillCode());
        snapshot.put("skillName", item.getSkillName());
        snapshot.put("description", item.getDescription());
        snapshot.put("instructions", item.getInstructions());
        snapshot.put("knowledgeBaseCodes", splitCodes(item.getKnowledgeBaseCodes()));
        snapshot.put("status", item.getStatus());
        return json(snapshot);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Cannot serialize AI content revision", ex);
        }
    }

    private AiKnowledgeBaseVO toKnowledgeBaseVO(AiKnowledgeBase item) {
        AiKnowledgeBaseVO vo = new AiKnowledgeBaseVO();
        vo.setId(item.getId());
        vo.setKnowledgeCode(item.getKnowledgeCode());
        vo.setKnowledgeName(item.getKnowledgeName());
        vo.setDescription(item.getDescription());
        vo.setContent(item.getContent());
        vo.setStatus(item.getStatus());
        vo.setRevisionNo(item.getRevisionNo());
        vo.setUpdatedBy(item.getUpdatedBy());
        vo.setUpdatedAt(item.getUpdatedAt());
        return vo;
    }

    private AiSkillVO toSkillVO(AiSkillDefinition item) {
        AiSkillVO vo = new AiSkillVO();
        vo.setId(item.getId());
        vo.setSkillCode(item.getSkillCode());
        vo.setSkillName(item.getSkillName());
        vo.setDescription(item.getDescription());
        vo.setInstructions(item.getInstructions());
        vo.setKnowledgeBaseCodes(splitCodes(item.getKnowledgeBaseCodes()));
        vo.setStatus(item.getStatus());
        vo.setRevisionNo(item.getRevisionNo());
        vo.setUpdatedBy(item.getUpdatedBy());
        vo.setUpdatedAt(item.getUpdatedAt());
        return vo;
    }

    private AiChangeProposalVO toProposalVO(AiChangeProposal item) {
        AiChangeProposalVO vo = new AiChangeProposalVO();
        vo.setId(item.getId());
        vo.setTargetType(item.getTargetType());
        vo.setTargetId(item.getTargetId());
        vo.setTargetCode(item.getTargetCode());
        vo.setBaseRevisionNo(item.getBaseRevisionNo());
        vo.setCurrentContent(currentContent(item.getTargetType(), item.getTargetId()));
        vo.setProposedContent(item.getProposedContent());
        vo.setRationale(item.getRationale());
        vo.setSourceBizType(item.getSourceBizType());
        vo.setSourceBizId(item.getSourceBizId());
        vo.setStatus(item.getStatus());
        vo.setReviewerUserId(item.getReviewerUserId());
        vo.setReviewerName(item.getReviewerName());
        vo.setReviewComment(item.getReviewComment());
        vo.setReviewedAt(item.getReviewedAt());
        vo.setCreatedAt(item.getCreatedAt());
        return vo;
    }

    private String currentContent(String targetType, Long targetId) {
        if (TARGET_KNOWLEDGE_BASE.equals(targetType)) {
            AiKnowledgeBase item = knowledgeBaseMapper.selectById(targetId);
            return item == null ? "" : item.getContent();
        }
        AiSkillDefinition item = skillMapper.selectById(targetId);
        return item == null ? "" : item.getInstructions();
    }

    private AiContentRevisionVO toRevisionVO(AiContentRevision item) {
        AiContentRevisionVO vo = new AiContentRevisionVO();
        vo.setId(item.getId());
        vo.setTargetType(item.getTargetType());
        vo.setTargetId(item.getTargetId());
        vo.setTargetCode(item.getTargetCode());
        vo.setRevisionNo(item.getRevisionNo());
        vo.setSnapshotJson(item.getSnapshotJson());
        vo.setChangeSource(item.getChangeSource());
        vo.setChangeSummary(item.getChangeSummary());
        vo.setCreatedBy(item.getCreatedBy());
        vo.setCreatedAt(item.getCreatedAt());
        return vo;
    }

    private String normalizeTargetType(String value) {
        String normalized = trim(value).toUpperCase(Locale.ROOT);
        if (!List.of(TARGET_KNOWLEDGE_BASE, TARGET_SKILL).contains(normalized)) {
            throw new BizException("AI_TARGET_TYPE_INVALID", "AI内容目标类型不合法");
        }
        return normalized;
    }

    private List<String> normalizeCodes(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream().filter(StringUtils::hasText).map(String::trim).distinct().toList();
    }

    private List<String> splitCodes(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        return normalizeCodes(List.of(value.split(",")));
    }

    private int nextRevision(Integer current) {
        return current == null ? 1 : current + 1;
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot hash AI improvement proposal", ex);
        }
    }

    private String firstText(String... values) {
        if (values != null) {
            for (String value : values) {
                if (StringUtils.hasText(value)) {
                    return value.trim();
                }
            }
        }
        return "";
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private record Target(Long id, String code, Integer revisionNo) {
    }

    public record RuntimeKnowledge(String code, String name, Integer revisionNo, String content) {
    }

    public record RuntimeSkill(String code, String name, Integer revisionNo, String instructions,
                               List<RuntimeKnowledge> knowledgeBases) {
        public String prompt() {
            StringBuilder builder = new StringBuilder(instructions == null ? "" : instructions.trim());
            for (RuntimeKnowledge knowledge : knowledgeBases == null ? List.<RuntimeKnowledge>of() : knowledgeBases) {
                builder.append("\n\n## 引用知识库：")
                    .append(knowledge.name())
                    .append("（")
                    .append(knowledge.code())
                    .append("，修订 ")
                    .append(knowledge.revisionNo())
                    .append("）\n")
                    .append(knowledge.content());
            }
            return builder.toString();
        }
    }

    public record ModelImprovementSuggestion(String targetType, String targetCode,
                                             String proposedContent, String rationale) {
    }
}
