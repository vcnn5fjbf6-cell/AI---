package com.company.procurement.modules.platform.importlog.controller;

import com.company.procurement.common.result.PageResult;
import com.company.procurement.common.result.Result;
import com.company.procurement.modules.platform.importlog.dto.ImportFileLogPageQueryDTO;
import com.company.procurement.modules.platform.importlog.service.ImportFileLogService;
import com.company.procurement.modules.platform.importlog.vo.ImportFileLogVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/import-file-logs")
@RequiredArgsConstructor
public class ImportFileLogController {

    private final ImportFileLogService importFileLogService;

    @GetMapping("/page")
    public Result<PageResult<ImportFileLogVO>> page(@Valid ImportFileLogPageQueryDTO queryDTO) {
        return Result.ok(importFileLogService.page(queryDTO));
    }

    @GetMapping("/{id:\\d+}")
    public Result<ImportFileLogVO> detail(@PathVariable Long id) {
        return Result.ok(importFileLogService.detail(id));
    }
}
