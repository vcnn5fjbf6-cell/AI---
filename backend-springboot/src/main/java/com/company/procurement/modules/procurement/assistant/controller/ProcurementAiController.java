package com.company.procurement.modules.procurement.assistant.controller;

import com.company.procurement.common.result.Result;
import com.company.procurement.modules.procurement.assistant.dto.ProcurementAiConfirmDTO;
import com.company.procurement.modules.procurement.assistant.dto.ProcurementAiStartDTO;
import com.company.procurement.modules.procurement.assistant.service.ProcurementAiService;
import com.company.procurement.modules.procurement.assistant.vo.ProcurementAiConfirmResultVO;
import com.company.procurement.modules.procurement.assistant.vo.ProcurementAiSessionVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/workflow/procurement-ai")
@RequiredArgsConstructor
public class ProcurementAiController {

    private final ProcurementAiService procurementAiService;

    @PostMapping("/sessions")
    public Result<ProcurementAiSessionVO> startSession(@RequestBody @Valid ProcurementAiStartDTO dto) {
        return Result.ok(procurementAiService.startSession(dto));
    }

    @GetMapping("/sessions/{id}")
    public Result<ProcurementAiSessionVO> getSession(@PathVariable Long id) {
        return Result.ok(procurementAiService.getSession(id));
    }

    @PostMapping("/sessions/{id}/confirm")
    public Result<ProcurementAiConfirmResultVO> confirm(@PathVariable Long id,
                                                        @RequestBody @Valid ProcurementAiConfirmDTO dto) {
        return Result.ok(procurementAiService.confirm(id, dto));
    }
}
