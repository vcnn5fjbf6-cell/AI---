package com.company.procurement.modules.workbench.controller;

import com.company.procurement.common.result.Result;
import com.company.procurement.modules.procurement.workflow.vo.WorkflowSubmitResultVO;
import com.company.procurement.modules.workbench.dto.WorkbenchApprovalDTO;
import com.company.procurement.modules.workbench.dto.WorkbenchFollowDTO;
import com.company.procurement.modules.workbench.dto.WorkbenchNoticeSaveDTO;
import com.company.procurement.modules.workbench.dto.WorkbenchRequestQueryDTO;
import com.company.procurement.modules.workbench.service.WorkbenchNoticeService;
import com.company.procurement.modules.workbench.service.WorkbenchService;
import com.company.procurement.modules.workbench.vo.WorkbenchNoticeVO;
import com.company.procurement.modules.workbench.vo.WorkbenchPageVO;
import com.company.procurement.modules.workbench.vo.WorkbenchRequestDetailVO;
import com.company.procurement.modules.workbench.vo.WorkbenchUserOptionVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/workbench")
@RequiredArgsConstructor
public class WorkbenchController {

    private final WorkbenchService workbenchService;
    private final WorkbenchNoticeService workbenchNoticeService;

    @GetMapping("/requests/page")
    public Result<WorkbenchPageVO> pageRequests(@Valid WorkbenchRequestQueryDTO queryDTO) {
        return Result.ok(workbenchService.pageRequests(queryDTO));
    }

    @GetMapping("/requests/{sourceType}/{sourceId:\\d+}")
    public Result<WorkbenchRequestDetailVO> detail(@PathVariable String sourceType, @PathVariable Long sourceId) {
        return Result.ok(workbenchService.detail(sourceType, sourceId));
    }

    @PostMapping("/requests/{sourceType}/{sourceId:\\d+}/approval")
    public Result<WorkflowSubmitResultVO> approve(@PathVariable String sourceType,
                                                  @PathVariable Long sourceId,
                                                  @RequestBody @Valid WorkbenchApprovalDTO dto) {
        return Result.ok(workbenchService.approve(sourceType, sourceId, dto));
    }

    @PostMapping("/follows")
    public Result<Void> setFollow(@RequestBody @Valid WorkbenchFollowDTO dto) {
        workbenchService.setFollow(dto);
        return Result.ok();
    }

    @GetMapping("/users/options")
    public Result<List<WorkbenchUserOptionVO>> userOptions(@RequestParam(required = false) String keyword) {
        return Result.ok(workbenchService.userOptions(keyword));
    }

    @GetMapping("/notices")
    public Result<List<WorkbenchNoticeVO>> listVisibleNotices() {
        return Result.ok(workbenchNoticeService.listVisible());
    }

    @GetMapping("/notices/manage")
    public Result<List<WorkbenchNoticeVO>> listManageNotices() {
        return Result.ok(workbenchNoticeService.listManage());
    }

    @PostMapping("/notices/manage")
    public Result<WorkbenchNoticeVO> createNotice(@RequestBody @Valid WorkbenchNoticeSaveDTO dto) {
        return Result.ok(workbenchNoticeService.create(dto));
    }

    @PutMapping("/notices/manage/{id:\\d+}")
    public Result<WorkbenchNoticeVO> updateNotice(@PathVariable Long id, @RequestBody @Valid WorkbenchNoticeSaveDTO dto) {
        return Result.ok(workbenchNoticeService.update(id, dto));
    }

    @DeleteMapping("/notices/manage/{id:\\d+}")
    public Result<Void> deleteNotice(@PathVariable Long id) {
        workbenchNoticeService.delete(id);
        return Result.ok();
    }
}
