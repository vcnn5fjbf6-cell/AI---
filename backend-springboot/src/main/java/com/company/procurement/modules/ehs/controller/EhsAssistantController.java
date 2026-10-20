package com.company.procurement.modules.ehs.controller;

import com.company.procurement.common.result.PageResult;
import com.company.procurement.common.result.Result;
import com.company.procurement.modules.ehs.dto.EhsAssistantChatDTO;
import com.company.procurement.modules.ehs.dto.EhsKnowledgeSaveDTO;
import com.company.procurement.modules.ehs.entity.EhsAiMessage;
import com.company.procurement.modules.ehs.entity.EhsAiSession;
import com.company.procurement.modules.ehs.entity.EhsKnowledgeDocument;
import com.company.procurement.modules.ehs.service.EhsAssistantService;
import com.company.procurement.modules.ehs.vo.EhsAssistantChatVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/ehs/assistant")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ehs", name = "enabled", havingValue = "true")
public class EhsAssistantController {

    private final EhsAssistantService assistantService;

    @GetMapping("/knowledge/page")
    public Result<PageResult<EhsKnowledgeDocument>> knowledgePage(
        @RequestParam(defaultValue = "1") @Min(1) long pageNo,
        @RequestParam(defaultValue = "10") @Min(1) @Max(100) long pageSize,
        @RequestParam(required = false) String keyword
    ) {
        return Result.ok(assistantService.pageKnowledge(pageNo, pageSize, keyword));
    }

    @PostMapping("/knowledge")
    public Result<Long> createKnowledge(@RequestBody @Valid EhsKnowledgeSaveDTO dto) {
        return Result.ok(assistantService.createKnowledge(dto));
    }

    @PostMapping("/knowledge/upload")
    public Result<Long> uploadKnowledge(@RequestParam(required = false) String title,
                                        @RequestParam(required = false) String category,
                                        @RequestParam("file") MultipartFile file) {
        return Result.ok(assistantService.uploadKnowledge(title, category, file));
    }

    @PutMapping("/knowledge/{id:\\d+}")
    public Result<Void> updateKnowledge(@PathVariable Long id, @RequestBody @Valid EhsKnowledgeSaveDTO dto) {
        assistantService.updateKnowledge(id, dto);
        return Result.ok();
    }

    @DeleteMapping("/knowledge/{id:\\d+}")
    public Result<Void> deleteKnowledge(@PathVariable Long id) {
        assistantService.deleteKnowledge(id);
        return Result.ok();
    }

    @GetMapping("/sessions")
    public Result<List<EhsAiSession>> sessions() {
        return Result.ok(assistantService.sessions());
    }

    @GetMapping("/sessions/{id:\\d+}/messages")
    public Result<List<EhsAiMessage>> messages(@PathVariable Long id) {
        return Result.ok(assistantService.messages(id));
    }

    @DeleteMapping("/sessions/{id:\\d+}")
    public Result<Void> deleteSession(@PathVariable Long id) {
        assistantService.deleteSession(id);
        return Result.ok();
    }

    @PostMapping("/chat")
    public Result<EhsAssistantChatVO> chat(@RequestBody @Valid EhsAssistantChatDTO dto) {
        return Result.ok(assistantService.chat(dto));
    }
}
