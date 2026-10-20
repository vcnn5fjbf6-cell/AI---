package com.company.procurement.modules.procurement.monthly.controller;

import com.company.procurement.common.result.PageResult;
import com.company.procurement.common.result.Result;
import com.company.procurement.modules.procurement.monthly.dto.MonthlyAggregateDTO;
import com.company.procurement.modules.procurement.monthly.dto.MonthlyDemandCreateDTO;
import com.company.procurement.modules.procurement.monthly.dto.MonthlyDemandQueryDTO;
import com.company.procurement.modules.procurement.monthly.dto.MonthlyDemandQuantityUpdateDTO;
import com.company.procurement.modules.procurement.monthly.dto.MonthlyDemandSupplementDTO;
import com.company.procurement.modules.procurement.monthly.service.MonthlyProcurementService;
import com.company.procurement.modules.procurement.monthly.vo.MonthlyAggregateResultVO;
import com.company.procurement.modules.procurement.monthly.vo.MonthlyDemandVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/workflow/monthly-procurement")
@RequiredArgsConstructor
public class MonthlyProcurementController {

    private final MonthlyProcurementService monthlyProcurementService;

    @PostMapping("/demands")
    public Result<MonthlyDemandVO> submitDemand(@RequestBody @Valid MonthlyDemandCreateDTO dto) {
        return Result.ok(monthlyProcurementService.submitDemand(dto));
    }

    @GetMapping("/demands/my")
    public Result<PageResult<MonthlyDemandVO>> pageMyDemands(@Valid MonthlyDemandQueryDTO queryDTO) {
        return Result.ok(monthlyProcurementService.pageMyDemands(queryDTO));
    }

    @GetMapping("/demands/current")
    public Result<PageResult<MonthlyDemandVO>> pageCurrentMonthDemands(@Valid MonthlyDemandQueryDTO queryDTO) {
        return Result.ok(monthlyProcurementService.pageCurrentMonthDemands(queryDTO));
    }

    @PostMapping("/demands/{id}/supplement")
    public Result<MonthlyDemandVO> submitSupplementDemand(@PathVariable("id") Long id,
                                                          @RequestBody @Valid MonthlyDemandSupplementDTO dto) {
        return Result.ok(monthlyProcurementService.submitSupplementDemand(id, dto));
    }

    @PatchMapping("/demands/{id}/quantity")
    public Result<MonthlyDemandVO> reduceOwnDemandQuantity(@PathVariable("id") Long id,
                                                           @RequestBody @Valid MonthlyDemandQuantityUpdateDTO dto) {
        return Result.ok(monthlyProcurementService.reduceOwnDemandQuantity(id, dto));
    }

    @DeleteMapping("/demands/{id}")
    public Result<Void> deleteOwnDemand(@PathVariable("id") Long id) {
        monthlyProcurementService.deleteOwnDemand(id);
        return Result.ok();
    }

    @GetMapping("/demands/pending")
    public Result<PageResult<MonthlyDemandVO>> pagePendingDemands(@Valid MonthlyDemandQueryDTO queryDTO) {
        return Result.ok(monthlyProcurementService.pagePendingDemands(queryDTO));
    }

    @PostMapping("/aggregate")
    public Result<MonthlyAggregateResultVO> aggregate(@RequestBody MonthlyAggregateDTO dto) {
        return Result.ok(monthlyProcurementService.aggregate(dto));
    }
}
