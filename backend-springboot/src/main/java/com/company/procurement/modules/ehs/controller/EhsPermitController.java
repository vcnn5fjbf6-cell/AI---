package com.company.procurement.modules.ehs.controller;

import com.company.procurement.common.result.PageResult;
import com.company.procurement.common.result.Result;
import com.company.procurement.modules.ehs.dto.EhsPermitApprovalDTO;
import com.company.procurement.modules.ehs.dto.EhsPermitCloseDTO;
import com.company.procurement.modules.ehs.dto.EhsPermitQueryDTO;
import com.company.procurement.modules.ehs.dto.EhsPermitSampleDTO;
import com.company.procurement.modules.ehs.dto.EhsPermitSaveDTO;
import com.company.procurement.modules.ehs.entity.EhsNotificationLog;
import com.company.procurement.modules.ehs.service.EhsNotificationService;
import com.company.procurement.modules.ehs.service.EhsDepartmentOptionService;
import com.company.procurement.modules.ehs.service.EhsPermitService;
import com.company.procurement.modules.ehs.vo.EhsDepartmentOptionVO;
import com.company.procurement.modules.ehs.vo.EhsPermitVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ehs/permits")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ehs", name = "enabled", havingValue = "true")
public class EhsPermitController {

    private final EhsPermitService permitService;
    private final EhsNotificationService notificationService;
    private final EhsDepartmentOptionService departmentOptionService;

    @GetMapping("/page")
    public Result<PageResult<EhsPermitVO>> page(@Valid EhsPermitQueryDTO query) {
        return Result.ok(permitService.page(query));
    }

    @GetMapping("/stats")
    public Result<Map<String, Long>> stats() {
        return Result.ok(permitService.stats());
    }

    @GetMapping("/departments")
    public Result<List<EhsDepartmentOptionVO>> departments() {
        return Result.ok(departmentOptionService.listEnabledOptions());
    }

    @GetMapping("/{id:\\d+}")
    public Result<EhsPermitVO> detail(@PathVariable Long id) {
        return Result.ok(permitService.detail(id));
    }

    @PostMapping
    public Result<Long> create(@RequestBody @Valid EhsPermitSaveDTO dto) {
        return Result.ok(permitService.create(dto));
    }

    @PutMapping("/{id:\\d+}")
    public Result<Void> update(@PathVariable Long id, @RequestBody @Valid EhsPermitSaveDTO dto) {
        permitService.update(id, dto);
        return Result.ok();
    }

    @DeleteMapping("/{id:\\d+}")
    public Result<Void> delete(@PathVariable Long id) {
        permitService.delete(id);
        return Result.ok();
    }

    @PostMapping("/{id:\\d+}/submit")
    public Result<Void> submit(@PathVariable Long id) {
        permitService.submit(id);
        return Result.ok();
    }

    @PostMapping("/{id:\\d+}/approval")
    public Result<Void> approve(@PathVariable Long id, @RequestBody @Valid EhsPermitApprovalDTO dto) {
        permitService.approve(id, dto);
        return Result.ok();
    }

    @PostMapping("/{id:\\d+}/start")
    public Result<Void> start(@PathVariable Long id) {
        permitService.start(id);
        return Result.ok();
    }

    @PostMapping("/{id:\\d+}/samples")
    public Result<Void> addSample(@PathVariable Long id, @RequestBody @Valid EhsPermitSampleDTO dto) {
        permitService.addSample(id, dto);
        return Result.ok();
    }

    @PostMapping("/{id:\\d+}/close")
    public Result<Void> close(@PathVariable Long id, @RequestBody @Valid EhsPermitCloseDTO dto) {
        permitService.close(id, dto);
        return Result.ok();
    }

    @GetMapping("/{id:\\d+}/notifications")
    public Result<List<EhsNotificationLog>> notifications(@PathVariable Long id) {
        EhsPermitVO permit = permitService.detail(id);
        String bizType = permit.getParentId() == null ? "EHS_WORK_PERMIT" : "EHS_SPECIAL_PERMIT";
        return Result.ok(notificationService.list(bizType, id));
    }
}
