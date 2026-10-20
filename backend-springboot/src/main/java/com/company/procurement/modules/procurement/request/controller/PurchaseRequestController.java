package com.company.procurement.modules.procurement.request.controller;

import com.company.procurement.common.result.PageResult;
import com.company.procurement.common.result.Result;
import com.company.procurement.modules.procurement.request.dto.PurchaseRequestCreateDTO;
import com.company.procurement.modules.procurement.request.dto.PurchaseRequestPageQueryDTO;
import com.company.procurement.modules.procurement.request.dto.PurchaseRequestUpdateDTO;
import com.company.procurement.modules.procurement.request.service.PurchaseRequestService;
import com.company.procurement.modules.procurement.request.vo.PurchaseRequestVO;
import com.company.procurement.modules.procurement.workflow.vo.WorkflowSubmitResultVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/purchase-requests")
@RequiredArgsConstructor
public class PurchaseRequestController {

    private final PurchaseRequestService purchaseRequestService;

    @GetMapping("/page")
    public Result<PageResult<PurchaseRequestVO>> page(@Valid PurchaseRequestPageQueryDTO queryDTO) {
        return Result.ok(purchaseRequestService.page(queryDTO));
    }

    @GetMapping("/list")
    public Result<List<PurchaseRequestVO>> list(PurchaseRequestPageQueryDTO queryDTO) {
        return Result.ok(purchaseRequestService.list(queryDTO));
    }

    @GetMapping("/{id:\\d+}")
    public Result<PurchaseRequestVO> detail(@PathVariable Long id) {
        return Result.ok(purchaseRequestService.detail(id));
    }

    @PostMapping
    public Result<Long> create(@RequestBody @Valid PurchaseRequestCreateDTO dto) {
        return Result.ok(purchaseRequestService.create(dto));
    }

    @PutMapping("/{id:\\d+}")
    public Result<Void> update(@PathVariable Long id, @RequestBody @Valid PurchaseRequestUpdateDTO dto) {
        purchaseRequestService.update(id, dto);
        return Result.ok();
    }

    @DeleteMapping("/{id:\\d+}")
    public Result<Void> remove(@PathVariable Long id) {
        purchaseRequestService.remove(id);
        return Result.ok();
    }

    @PostMapping("/{id:\\d+}/submit")
    public Result<WorkflowSubmitResultVO> submit(@PathVariable Long id) {
        return Result.ok(purchaseRequestService.submit(id));
    }

    @GetMapping("/{id:\\d+}/submit-preview")
    public Result<WorkflowSubmitResultVO> previewSubmit(@PathVariable Long id) {
        return Result.ok(purchaseRequestService.previewSubmit(id));
    }
}
