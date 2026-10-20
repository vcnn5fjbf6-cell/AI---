package com.company.procurement.modules.platform.permission.controller;

import com.company.procurement.common.result.PageResult;
import com.company.procurement.common.result.Result;
import com.company.procurement.modules.platform.permission.dto.PlatformPermissionRequestCreateDTO;
import com.company.procurement.modules.platform.permission.dto.PlatformPermissionRequestQueryDTO;
import com.company.procurement.modules.platform.permission.dto.PlatformPermissionReviewDTO;
import com.company.procurement.modules.platform.permission.dto.PlatformUnitSaveDTO;
import com.company.procurement.modules.platform.permission.dto.PlatformUserAccessUpdateDTO;
import com.company.procurement.modules.platform.permission.service.PlatformPermissionService;
import com.company.procurement.modules.platform.permission.vo.PlatformAppVO;
import com.company.procurement.modules.platform.permission.vo.PlatformMyAccessVO;
import com.company.procurement.modules.platform.permission.vo.PlatformPermissionRequestVO;
import com.company.procurement.modules.platform.permission.vo.PlatformSummaryVO;
import com.company.procurement.modules.platform.permission.vo.PlatformUnitVO;
import com.company.procurement.modules.platform.permission.vo.PlatformUserAccessVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class PlatformPermissionController {

    private final PlatformPermissionService platformPermissionService;

    @GetMapping("/api/platform/apps")
    public Result<List<PlatformAppVO>> apps() {
        return Result.ok(platformPermissionService.apps());
    }

    @GetMapping("/api/platform/me/access")
    public Result<PlatformMyAccessVO> myAccess() {
        return Result.ok(platformPermissionService.myAccess());
    }

    @PostMapping("/api/platform/permission-requests")
    public Result<Long> createRequest(@RequestBody @Valid PlatformPermissionRequestCreateDTO dto) {
        return Result.ok(platformPermissionService.createRequest(dto));
    }

    @GetMapping("/api/admin/platform/summary")
    public Result<PlatformSummaryVO> summary() {
        return Result.ok(platformPermissionService.summary());
    }

    @GetMapping("/api/admin/platform/users/access")
    public Result<List<PlatformUserAccessVO>> userAccessList() {
        return Result.ok(platformPermissionService.userAccessList());
    }

    @PutMapping("/api/admin/platform/users/{userId:\\d+}/access")
    public Result<Void> updateUserAccess(@PathVariable Long userId, @RequestBody @Valid PlatformUserAccessUpdateDTO dto) {
        platformPermissionService.updateUserAccess(userId, dto);
        return Result.ok();
    }

    @GetMapping("/api/admin/platform/units")
    public Result<List<PlatformUnitVO>> unitList() {
        return Result.ok(platformPermissionService.unitList());
    }

    @PostMapping("/api/admin/platform/units")
    public Result<Long> saveUnit(@RequestBody @Valid PlatformUnitSaveDTO dto) {
        return Result.ok(platformPermissionService.saveUnit(dto));
    }

    @DeleteMapping("/api/admin/platform/units/{id:\\d+}")
    public Result<Void> removeUnit(@PathVariable Long id) {
        platformPermissionService.removeUnit(id);
        return Result.ok();
    }

    @GetMapping("/api/admin/platform/permission-requests/page")
    public Result<PageResult<PlatformPermissionRequestVO>> requestPage(@Valid PlatformPermissionRequestQueryDTO queryDTO) {
        return Result.ok(platformPermissionService.requestPage(queryDTO));
    }

    @PutMapping("/api/admin/platform/permission-requests/{id:\\d+}/review")
    public Result<Void> reviewRequest(@PathVariable Long id, @RequestBody @Valid PlatformPermissionReviewDTO dto) {
        platformPermissionService.reviewRequest(id, dto);
        return Result.ok();
    }
}
