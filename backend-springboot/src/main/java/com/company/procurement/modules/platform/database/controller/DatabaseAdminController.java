package com.company.procurement.modules.platform.database.controller;

import com.company.procurement.common.result.PageResult;
import com.company.procurement.common.result.Result;
import com.company.procurement.modules.platform.database.dto.DatabaseRowDeleteDTO;
import com.company.procurement.modules.platform.database.dto.DatabaseRowSaveDTO;
import com.company.procurement.modules.platform.database.dto.DatabaseRowUpdateDTO;
import com.company.procurement.modules.platform.database.dto.DatabaseTablePageQueryDTO;
import com.company.procurement.modules.platform.database.service.DatabaseAdminService;
import com.company.procurement.modules.platform.database.vo.DatabaseTableDetailVO;
import com.company.procurement.modules.platform.database.vo.DatabaseTableVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/database")
@RequiredArgsConstructor
public class DatabaseAdminController {

    private final DatabaseAdminService databaseAdminService;

    @GetMapping("/tables")
    public Result<List<DatabaseTableVO>> listTables(@RequestParam(required = false) String keyword) {
        return Result.ok(databaseAdminService.listTables(keyword));
    }

    @GetMapping("/tables/{tableName}")
    public Result<DatabaseTableDetailVO> detail(@PathVariable String tableName) {
        return Result.ok(databaseAdminService.detail(tableName));
    }

    @GetMapping("/tables/{tableName}/rows")
    public Result<PageResult<Map<String, Object>>> pageRows(
        @PathVariable String tableName,
        @Valid DatabaseTablePageQueryDTO queryDTO
    ) {
        return Result.ok(databaseAdminService.pageRows(tableName, queryDTO));
    }

    @PostMapping("/tables/{tableName}/rows")
    public Result<Void> createRow(@PathVariable String tableName, @RequestBody @Valid DatabaseRowSaveDTO dto) {
        databaseAdminService.createRow(tableName, dto);
        return Result.ok();
    }

    @PutMapping("/tables/{tableName}/rows")
    public Result<Void> updateRow(@PathVariable String tableName, @RequestBody @Valid DatabaseRowUpdateDTO dto) {
        databaseAdminService.updateRow(tableName, dto);
        return Result.ok();
    }

    @DeleteMapping("/tables/{tableName}/rows")
    public Result<Void> deleteRow(@PathVariable String tableName, @RequestBody @Valid DatabaseRowDeleteDTO dto) {
        databaseAdminService.deleteRow(tableName, dto);
        return Result.ok();
    }
}
