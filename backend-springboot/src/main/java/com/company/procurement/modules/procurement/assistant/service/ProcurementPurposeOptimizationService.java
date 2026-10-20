package com.company.procurement.modules.procurement.assistant.service;

import com.company.procurement.common.exception.BizException;
import com.company.procurement.modules.ai.governance.service.AiGovernanceService;
import com.company.procurement.modules.ai.governance.service.AiGovernanceService.RuntimeSkill;
import com.company.procurement.modules.procurement.assistant.client.M3VisionClient;
import com.company.procurement.modules.procurement.assistant.vo.ProcurementPurposeOptimizationVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProcurementPurposeOptimizationService {

    static final String SKILL_CODE = "procurement-purpose-optimization";

    private static final String SKILL_RESOURCE =
        "classpath:ai-skills/procurement-purpose-optimization/SKILL.md";
    private static final String STANDARD_RESOURCE =
        "classpath:ai-skills/procurement-purpose-standard.md";
    private static final int PURPOSE_MAX_LENGTH = 500;
    private static final int MIN_APPEND_LENGTH = 20;
    private static final int PREFERRED_APPEND_LENGTH = 220;

    private final M3VisionClient m3VisionClient;
    private final AiGovernanceService governanceService;
    private final ResourceLoader resourceLoader;

    private volatile String fallbackSkillInstructions;

    public ProcurementPurposeOptimizationVO optimize(String purpose) {
        String source = purpose == null ? "" : purpose.trim();
        if (!StringUtils.hasText(source)) {
            throw new BizException("AI_PURPOSE_EMPTY", "请先填写采购原因，再使用AI优化");
        }
        int appendLimit = PURPOSE_MAX_LENGTH - source.length() - 1;
        if (appendLimit < MIN_APPEND_LENGTH) {
            throw new BizException("AI_PURPOSE_TOO_LONG", "采购原因已接近500字，请先精简后再使用AI优化");
        }
        if (!m3VisionClient.canCompleteText()) {
            throw unavailable(null);
        }
        try {
            String optimizedContent = normalize(m3VisionClient.optimizeProcurementPurpose(
                skillInstructions(),
                source,
                Math.min(PREFERRED_APPEND_LENGTH, appendLimit),
                "procurement-purpose-optimization"
            ));
            if (!StringUtils.hasText(optimizedContent) || source.equals(optimizedContent)) {
                throw new BizException("AI_PURPOSE_OPTIMIZATION_EMPTY", "未生成可用的优化内容，请补充更多采购事实后重试");
            }
            return new ProcurementPurposeOptimizationVO(limit(optimizedContent, appendLimit));
        } catch (BizException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("Procurement purpose optimization failed", ex);
            throw unavailable(ex);
        }
    }

    private String skillInstructions() {
        try {
            RuntimeSkill runtimeSkill = governanceService.runtimeSkill(SKILL_CODE);
            if (runtimeSkill == null || !StringUtils.hasText(runtimeSkill.prompt())) {
                throw new BizException("AI_PURPOSE_SKILL_DISABLED", "采购原因AI优化Skill未启用，请联系管理员");
            }
            return "# Skill：" + runtimeSkill.code() + "（修订 " + runtimeSkill.revisionNo() + "）\n\n"
                + runtimeSkill.prompt();
        } catch (BizException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("Cannot load managed procurement purpose optimization skill; use packaged fallback: {}", ex.toString());
            return fallbackSkillInstructions();
        }
    }

    private String fallbackSkillInstructions() {
        String current = fallbackSkillInstructions;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (fallbackSkillInstructions == null) {
                fallbackSkillInstructions = readResource(SKILL_RESOURCE)
                    + "\n\n" + readResource(STANDARD_RESOURCE);
            }
            return fallbackSkillInstructions;
        }
    }

    private String readResource(String location) {
        Resource resource = resourceLoader.getResource(location);
        try (var input = resource.getInputStream()) {
            return StreamUtils.copyToString(input, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot load procurement purpose optimization skill resource: " + location, ex);
        }
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim()
            .replaceFirst("^优化后(?:内容|表述)?[：:]\\s*", "")
            .replaceFirst("^[\\\"“]", "")
            .replaceFirst("[\\\"”]$", "")
            .trim();
    }

    private String limit(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private BizException unavailable(Exception cause) {
        if (cause != null) {
            log.debug("AI purpose optimization unavailable", cause);
        }
        return new BizException("AI_PURPOSE_OPTIMIZATION_UNAVAILABLE", "AI优化暂时不可用，请稍后重试；当前采购原因不会被修改");
    }
}
