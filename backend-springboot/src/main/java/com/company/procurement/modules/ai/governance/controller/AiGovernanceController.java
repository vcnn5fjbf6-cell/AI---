package com.company.procurement.modules.ai.governance.controller;

import com.company.procurement.common.exception.BizException;
import com.company.procurement.common.result.Result;
import com.company.procurement.common.security.AuthContextHolder;
import com.company.procurement.common.security.DataScopeHelper;
import com.company.procurement.modules.ai.governance.dto.AiKnowledgeBaseSaveDTO;
import com.company.procurement.modules.ai.governance.dto.AiProposalReviewDTO;
import com.company.procurement.modules.ai.governance.dto.AiSkillSaveDTO;
import com.company.procurement.modules.ai.governance.service.AiGovernanceService;
import com.company.procurement.modules.ai.governance.vo.AiContentRevisionVO;
import com.company.procurement.modules.ai.governance.vo.AiGovernanceOverviewVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/ai-governance")
@RequiredArgsConstructor
public class AiGovernanceController {

    private final AiGovernanceService governanceService;

    @GetMapping("/overview")
    public Result<AiGovernanceOverviewVO> overview() {
        requireSystemAdmin();
        return Result.ok(governanceService.overview());
    }

    @GetMapping("/revisions")
    public Result<List<AiContentRevisionVO>> revisions(@RequestParam String targetType,
                                                       @RequestParam Long targetId) {
        requireSystemAdmin();
        return Result.ok(governanceService.revisions(targetType, targetId));
    }

    @PostMapping("/knowledge-bases")
    public Result<Long> createKnowledgeBase(@RequestBody @Valid AiKnowledgeBaseSaveDTO dto) {
        requireSystemAdmin();
        return Result.ok(governanceService.createKnowledgeBase(dto));
    }

    @PutMapping("/knowledge-bases/{id}")
    public Result<Void> updateKnowledgeBase(@PathVariable Long id,
                                            @RequestBody @Valid AiKnowledgeBaseSaveDTO dto) {
        requireSystemAdmin();
        governanceService.updateKnowledgeBase(id, dto);
        return Result.ok();
    }

    @DeleteMapping("/knowledge-bases/{id}")
    public Result<Void> deleteKnowledgeBase(@PathVariable Long id) {
        requireSystemAdmin();
        governanceService.deleteKnowledgeBase(id);
        return Result.ok();
    }

    @PostMapping("/skills")
    public Result<Long> createSkill(@RequestBody @Valid AiSkillSaveDTO dto) {
        requireSystemAdmin();
        return Result.ok(governanceService.createSkill(dto));
    }

    @PutMapping("/skills/{id}")
    public Result<Void> updateSkill(@PathVariable Long id, @RequestBody @Valid AiSkillSaveDTO dto) {
        requireSystemAdmin();
        governanceService.updateSkill(id, dto);
        return Result.ok();
    }

    @DeleteMapping("/skills/{id}")
    public Result<Void> deleteSkill(@PathVariable Long id) {
        requireSystemAdmin();
        governanceService.deleteSkill(id);
        return Result.ok();
    }

    @PostMapping("/proposals/{id}/review")
    public Result<Void> reviewProposal(@PathVariable Long id, @RequestBody @Valid AiProposalReviewDTO dto) {
        requireSystemAdmin();
        governanceService.reviewProposal(id, dto);
        return Result.ok();
    }

    private void requireSystemAdmin() {
        if (!DataScopeHelper.isSystemAdmin(AuthContextHolder.requireUser())) {
            throw new BizException("FORBIDDEN", "仅系统管理员可以维护AI知识与技能");
        }
    }
}
