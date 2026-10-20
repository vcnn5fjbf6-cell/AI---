package com.company.procurement.modules.platform.errorlog.controller;

import com.company.procurement.common.result.PageResult;
import com.company.procurement.common.result.Result;
import com.company.procurement.modules.platform.errorlog.dto.SystemErrorLogPageQueryDTO;
import com.company.procurement.modules.platform.errorlog.dto.SystemErrorLogStatusDTO;
import com.company.procurement.modules.platform.errorlog.service.SystemErrorLogService;
import com.company.procurement.modules.platform.errorlog.vo.SystemErrorLogVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/error-logs")
@RequiredArgsConstructor
public class SystemErrorLogController {

    private final SystemErrorLogService errorLogService;

    @GetMapping("/page")
    public Result<PageResult<SystemErrorLogVO>> page(@Valid SystemErrorLogPageQueryDTO queryDTO) {
        return Result.ok(errorLogService.page(queryDTO));
    }

    @GetMapping("/{id:\\d+}")
    public Result<SystemErrorLogVO> detail(@PathVariable Long id) {
        return Result.ok(errorLogService.detail(id));
    }

    @PutMapping("/{id:\\d+}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestBody @Valid SystemErrorLogStatusDTO dto) {
        errorLogService.updateStatus(id, dto);
        return Result.ok();
    }
}
