package com.company.procurement.modules.procurement.asset.controller;

import com.company.procurement.common.export.ExportFile;
import com.company.procurement.common.result.PageResult;
import com.company.procurement.common.result.Result;
import com.company.procurement.modules.platform.importlog.service.ImportFileLogService;
import com.company.procurement.modules.procurement.asset.dto.AssetCatalogQueryDTO;
import com.company.procurement.modules.procurement.asset.dto.AssetCatalogSaveDTO;
import com.company.procurement.modules.procurement.asset.service.AssetCatalogAdminService;
import com.company.procurement.modules.procurement.asset.vo.AssetCatalogItemVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import java.util.Map;

@RestController
@RequestMapping("/api/admin/platform/asset-catalog")
@RequiredArgsConstructor
public class AssetCatalogAdminController {

    private final AssetCatalogAdminService assetCatalogAdminService;
    private final ImportFileLogService importFileLogService;

    @GetMapping("/page")
    public Result<PageResult<AssetCatalogItemVO>> page(@Valid AssetCatalogQueryDTO query) {
        return Result.ok(assetCatalogAdminService.page(query));
    }

    @GetMapping("/export-template")
    public ResponseEntity<byte[]> exportTemplate() {
        ExportFile file = assetCatalogAdminService.exportTemplate();
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename(file.fileName(), StandardCharsets.UTF_8)
                .build()
                .toString())
            .contentType(MediaType.parseMediaType(file.contentType()))
            .body(file.content());
    }

    @PostMapping("/import")
    public Result<Integer> importTemplate(@RequestParam("file") MultipartFile file) {
        assetCatalogAdminService.assertCanManage();
        Long logId = importFileLogService.recordWebImport("ASSET_CATALOG_IMPORT", "资产目录批量维护", file);
        try {
            int count = assetCatalogAdminService.importTemplate(file);
            importFileLogService.markSuccess(logId, count, count, 0, "导入或更新资产目录 " + count + " 条");
            return Result.ok(count);
        } catch (RuntimeException ex) {
            importFileLogService.markFailed(logId, ex);
            throw ex;
        }
    }

    @PostMapping
    public Result<Long> create(@RequestBody @Valid AssetCatalogSaveDTO dto) {
        return Result.ok(assetCatalogAdminService.create(dto));
    }

    @PutMapping("/{id:\\d+}")
    public Result<Void> update(@PathVariable Long id, @RequestBody @Valid AssetCatalogSaveDTO dto) {
        assetCatalogAdminService.update(id, dto);
        return Result.ok();
    }

    @PutMapping("/{id:\\d+}/enabled")
    public Result<Void> updateEnabled(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        assetCatalogAdminService.updateEnabled(id, body.get("enabled"));
        return Result.ok();
    }
}
