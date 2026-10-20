package com.company.procurement.modules.supplier.controller;

import com.company.procurement.common.result.PageResult;
import com.company.procurement.common.result.Result;
import com.company.procurement.modules.supplier.dto.TemporarySupplierRecommendationCreateDTO;
import com.company.procurement.modules.supplier.dto.TemporarySupplierRecommendationPageQueryDTO;
import com.company.procurement.modules.supplier.dto.TemporarySupplierRecommendationReviewDTO;
import com.company.procurement.modules.supplier.service.SupplierFeishuNotificationService;
import com.company.procurement.modules.supplier.service.TemporarySupplierRecommendationService;
import com.company.procurement.modules.supplier.vo.TemporarySupplierRecommendationVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/procurement/temporary-supplier-recommendations")
@RequiredArgsConstructor
public class TemporarySupplierRecommendationController {

    private final TemporarySupplierRecommendationService recommendationService;
    private final SupplierFeishuNotificationService supplierFeishuNotificationService;

    @GetMapping("/page")
    public Result<PageResult<TemporarySupplierRecommendationVO>> page(@Valid TemporarySupplierRecommendationPageQueryDTO queryDTO) {
        return Result.ok(recommendationService.page(queryDTO));
    }

    @GetMapping("/{id:\\d+}")
    public Result<TemporarySupplierRecommendationVO> detail(@PathVariable Long id) {
        return Result.ok(recommendationService.detail(id));
    }

    @PostMapping
    public Result<Long> create(@RequestBody @Valid TemporarySupplierRecommendationCreateDTO dto) {
        Long id = recommendationService.create(dto);
        supplierFeishuNotificationService.pushQuoteNotificationsForRequest(dto.getPurchaseRequestId());
        return Result.ok(id);
    }

    @PostMapping("/{id:\\d+}/approve")
    public Result<TemporarySupplierRecommendationVO> approve(@PathVariable Long id,
                                                             @RequestBody(required = false) @Valid TemporarySupplierRecommendationReviewDTO dto) {
        return Result.ok(recommendationService.approve(
            id,
            dto == null ? new TemporarySupplierRecommendationReviewDTO() : dto
        ));
    }

    @PostMapping("/{id:\\d+}/reject")
    public Result<TemporarySupplierRecommendationVO> reject(@PathVariable Long id,
                                                            @RequestBody @Valid TemporarySupplierRecommendationReviewDTO dto) {
        return Result.ok(recommendationService.reject(id, dto));
    }
}
