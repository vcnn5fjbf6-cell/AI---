package com.company.procurement.modules.ehs.controller;

import com.company.procurement.common.export.ExcelExportUtils;
import com.company.procurement.common.export.ExcelImportUtils;
import com.company.procurement.common.result.PageResult;
import com.company.procurement.common.result.Result;
import com.company.procurement.modules.ehs.dto.EhsRegistryQueryDTO;
import com.company.procurement.modules.ehs.dto.EhsRegistrySaveDTO;
import com.company.procurement.modules.ehs.service.EhsRegistryService;
import com.company.procurement.modules.ehs.vo.EhsRegistryStatsVO;
import com.company.procurement.modules.ehs.vo.EhsRegistryVO;
import com.company.procurement.modules.platform.importlog.service.ImportFileLogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/ehs/records")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ehs", name = "enabled", havingValue = "true")
public class EhsRegistryController {

    private final EhsRegistryService registryService;
    private final ImportFileLogService importFileLogService;

    @GetMapping("/page")
    public Result<PageResult<EhsRegistryVO>> page(@Valid EhsRegistryQueryDTO query) {
        return Result.ok(registryService.page(query));
    }

    @GetMapping("/{id:\\d+}")
    public Result<EhsRegistryVO> detail(@PathVariable Long id) {
        return Result.ok(registryService.detail(id));
    }

    @PostMapping
    public Result<Long> create(@RequestBody @Valid EhsRegistrySaveDTO dto) {
        return Result.ok(registryService.create(dto));
    }

    @PutMapping("/{id:\\d+}")
    public Result<Void> update(@PathVariable Long id, @RequestBody @Valid EhsRegistrySaveDTO dto) {
        registryService.update(id, dto);
        return Result.ok();
    }

    @DeleteMapping("/{id:\\d+}")
    public Result<Void> delete(@PathVariable Long id) {
        registryService.delete(id);
        return Result.ok();
    }

    @GetMapping("/stats")
    public Result<EhsRegistryStatsVO> stats(@RequestParam String moduleType) {
        return Result.ok(registryService.stats(moduleType));
    }

    @PostMapping("/import")
    public Result<Integer> importRecords(@RequestParam String moduleType,
                                         @RequestParam("file") MultipartFile file) {
        Long logId = importFileLogService.recordWebImport(
            "EHS_" + moduleType.toUpperCase(Locale.ROOT),
            "EHS台账导入",
            file
        );
        try {
            List<List<String>> rows = ExcelImportUtils.readFirstSheet(file);
            int imported = registryService.importRows(moduleType, rows);
            importFileLogService.markSuccess(logId, Math.max(0, rows.size() - 1), imported, 0, "成功导入 " + imported + " 条 EHS 台账");
            return Result.ok(imported);
        } catch (RuntimeException ex) {
            importFileLogService.markFailed(logId, ex);
            throw ex;
        }
    }

    @GetMapping("/import-template")
    public ResponseEntity<byte[]> importTemplate(@RequestParam String moduleType) {
        return excelResponse(
            "EHS-" + moduleType.toUpperCase(Locale.ROOT) + "-导入模板.xlsx",
            ExcelExportUtils.toXlsxBytes("EHS台账", registryService.importTemplate(moduleType),
                ExcelExportUtils.WorkbookOptions.builder()
                    .freezeHeader(true)
                    .autoFilter(true)
                    .requiredColumns(0, 6)
                    .build())
        );
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam String moduleType) {
        return excelResponse(
            "EHS-" + moduleType.toUpperCase(Locale.ROOT) + "-台账.xlsx",
            ExcelExportUtils.toXlsxBytes("EHS台账", registryService.exportRows(moduleType),
                ExcelExportUtils.WorkbookOptions.builder().freezeHeader(true).autoFilter(true).build())
        );
    }

    private ResponseEntity<byte[]> excelResponse(String fileName, byte[] content) {
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename(fileName, StandardCharsets.UTF_8)
                .build()
                .toString())
            .contentType(MediaType.parseMediaType(ExcelExportUtils.XLSX_CONTENT_TYPE))
            .body(content);
    }
}
