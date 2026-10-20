package com.company.procurement.modules.supplier.controller;

import com.company.procurement.common.result.PageResult;
import com.company.procurement.common.result.Result;
import com.company.procurement.modules.supplier.dto.SupplierBanDTO;
import com.company.procurement.modules.supplier.dto.SupplierCreateDTO;
import com.company.procurement.modules.supplier.dto.SupplierPageQueryDTO;
import com.company.procurement.modules.supplier.dto.SupplierUpdateDTO;
import com.company.procurement.modules.supplier.service.SupplierService;
import com.company.procurement.modules.supplier.vo.SupplierVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/suppliers")
@RequiredArgsConstructor
public class SupplierController {

    private final SupplierService supplierService;

    @GetMapping("/page")
    public Result<PageResult<SupplierVO>> page(@Valid SupplierPageQueryDTO queryDTO) {
        return Result.ok(supplierService.page(queryDTO));
    }

    @GetMapping("/list")
    public Result<List<SupplierVO>> list(SupplierPageQueryDTO queryDTO) {
        return Result.ok(supplierService.list(queryDTO));
    }

    @GetMapping("/{id:\\d+}")
    public Result<SupplierVO> detail(@PathVariable Long id) {
        return Result.ok(supplierService.detail(id));
    }

    @PostMapping
    public Result<Long> create(@RequestBody @Valid SupplierCreateDTO dto) {
        return Result.ok(supplierService.create(dto));
    }

    @PutMapping("/{id:\\d+}")
    public Result<Void> update(@PathVariable Long id, @RequestBody @Valid SupplierUpdateDTO dto) {
        supplierService.update(id, dto);
        return Result.ok();
    }

    @PostMapping("/{id:\\d+}/bidding-suspension/release")
    public Result<Void> releaseBiddingSuspension(@PathVariable Long id) {
        supplierService.releaseBiddingSuspension(id);
        return Result.ok();
    }

    @PostMapping("/{id:\\d+}/ban")
    public Result<Void> ban(@PathVariable Long id, @RequestBody(required = false) SupplierBanDTO dto) {
        supplierService.ban(id, dto == null ? new SupplierBanDTO() : dto);
        return Result.ok();
    }

    @PostMapping("/{id:\\d+}/unban")
    public Result<Void> unban(@PathVariable Long id) {
        supplierService.unban(id);
        return Result.ok();
    }

    @DeleteMapping("/{id:\\d+}")
    public Result<Void> remove(@PathVariable Long id) {
        supplierService.remove(id);
        return Result.ok();
    }
}
