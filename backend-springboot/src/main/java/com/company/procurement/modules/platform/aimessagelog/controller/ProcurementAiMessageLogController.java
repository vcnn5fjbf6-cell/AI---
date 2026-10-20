package com.company.procurement.modules.platform.aimessagelog.controller;

import com.company.procurement.common.result.PageResult;
import com.company.procurement.common.result.Result;
import com.company.procurement.modules.platform.aimessagelog.dto.ProcurementAiMessageLogPageQueryDTO;
import com.company.procurement.modules.platform.aimessagelog.service.ProcurementAiMessageLogService;
import com.company.procurement.modules.platform.aimessagelog.vo.ProcurementAiMessageLogVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/procurement-ai/message-logs")
@RequiredArgsConstructor
public class ProcurementAiMessageLogController {

    private final ProcurementAiMessageLogService messageLogService;

    @GetMapping("/page")
    public Result<PageResult<ProcurementAiMessageLogVO>> page(@Valid ProcurementAiMessageLogPageQueryDTO queryDTO) {
        return Result.ok(messageLogService.page(queryDTO));
    }

    @GetMapping("/{id:\\d+}")
    public Result<ProcurementAiMessageLogVO> detail(@PathVariable Long id) {
        return Result.ok(messageLogService.detail(id));
    }
}
