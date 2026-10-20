package com.company.procurement.modules.procurement.bi.controller;

import com.company.procurement.common.result.Result;
import com.company.procurement.modules.procurement.bi.dto.ProcurementBiDashboardSaveDTO;
import com.company.procurement.modules.procurement.bi.dto.ProcurementBiQueryDTO;
import com.company.procurement.modules.procurement.bi.service.ProcurementBiService;
import com.company.procurement.modules.procurement.bi.vo.ProcurementBiDashboardVO;
import com.company.procurement.modules.procurement.bi.vo.ProcurementBiDatasetVO;
import com.company.procurement.modules.procurement.bi.vo.ProcurementBiQueryResultVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/procurement/bi")
@RequiredArgsConstructor
public class ProcurementBiController {

    private final ProcurementBiService biService;

    @GetMapping("/datasets")
    public Result<List<ProcurementBiDatasetVO>> datasets() {
        return Result.ok(biService.listDatasets());
    }

    @GetMapping("/datasets/{datasetCode}/fields")
    public Result<List<ProcurementBiDatasetVO.FieldVO>> datasetFields(@PathVariable String datasetCode) {
        return Result.ok(biService.listFields(datasetCode));
    }

    @PostMapping("/query")
    public Result<ProcurementBiQueryResultVO> query(@RequestBody ProcurementBiQueryDTO dto) {
        return Result.ok(biService.query(dto));
    }

    @GetMapping("/dashboards/default")
    public Result<ProcurementBiDashboardVO> defaultDashboard() {
        return Result.ok(biService.getDefaultDashboard());
    }

    @PostMapping("/dashboards/default")
    public Result<ProcurementBiDashboardVO> saveDefaultDashboard(@RequestBody ProcurementBiDashboardSaveDTO dto) {
        return Result.ok(biService.saveDefaultDashboard(dto));
    }
}
