package com.company.procurement.modules.procurement.route.controller;

import com.company.procurement.common.export.ExportFile;
import com.company.procurement.common.result.Result;
import com.company.procurement.modules.platform.importlog.service.ImportFileLogService;
import com.company.procurement.modules.procurement.route.dto.ProcurementApprovalRouteQueryDTO;
import com.company.procurement.modules.procurement.route.dto.ProcurementApprovalRouteSaveDTO;
import com.company.procurement.modules.procurement.route.service.ProcurementApprovalRouteService;
import com.company.procurement.modules.procurement.route.vo.ProcurementApprovalRouteConfigVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

@RestController
@RequestMapping({"/api/admin/procurement/approval-routes", "/api/admin/procurement/departments"})
@RequiredArgsConstructor
public class ProcurementApprovalRouteController {

    private final ProcurementApprovalRouteService routeService;
    private final ImportFileLogService importFileLogService;

    @GetMapping("/config")
    public Result<ProcurementApprovalRouteConfigVO> getConfig(ProcurementApprovalRouteQueryDTO queryDTO) {
        return Result.ok(routeService.getConfig(queryDTO));
    }

    @GetMapping("/import-template")
    public ResponseEntity<byte[]> exportImportTemplate(ProcurementApprovalRouteQueryDTO queryDTO) {
        ExportFile file = routeService.exportImportTemplate(queryDTO);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename(file.fileName(), StandardCharsets.UTF_8)
                .build()
                .toString())
            .contentType(MediaType.parseMediaType(file.contentType()))
            .body(file.content());
    }

    @PostMapping("/import")
    public Result<Integer> importRoutes(@RequestParam("file") MultipartFile file) {
        Long logId = importFileLogService.recordWebImport("APPROVAL_ROUTE_IMPORT", "部门审批配置导入", file);
        try {
            Integer count = routeService.importRouteTemplate(file);
            importFileLogService.markSuccess(logId, count, count, 0, "导入或更新部门审批配置 " + count + " 条");
            return Result.ok(count);
        } catch (RuntimeException ex) {
            importFileLogService.markFailed(logId, ex);
            throw ex;
        }
    }

    @PostMapping
    public Result<Long> createRoute(@RequestBody @Valid ProcurementApprovalRouteSaveDTO dto) {
        return Result.ok(routeService.createRoute(dto));
    }

    @PutMapping("/{id:\\d+}")
    public Result<Void> updateRoute(@PathVariable Long id, @RequestBody @Valid ProcurementApprovalRouteSaveDTO dto) {
        routeService.updateRoute(id, dto);
        return Result.ok();
    }

    @PutMapping("/{id:\\d+}/address")
    public Result<Void> updateRouteAddress(@PathVariable Long id, @RequestBody ProcurementApprovalRouteSaveDTO dto) {
        routeService.updateRouteAddress(id, dto);
        return Result.ok();
    }

    @DeleteMapping("/{id:\\d+}")
    public Result<Void> deleteRoute(@PathVariable Long id) {
        routeService.deleteRoute(id);
        return Result.ok();
    }
}
