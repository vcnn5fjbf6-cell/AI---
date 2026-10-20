package com.company.procurement.modules.approval.record.controller;

import com.company.procurement.common.result.PageResult;
import com.company.procurement.common.result.Result;
import com.company.procurement.modules.approval.record.dto.ApprovalRecordCreateDTO;
import com.company.procurement.modules.approval.record.dto.ApprovalRecordPageQueryDTO;
import com.company.procurement.modules.approval.record.dto.ApprovalRecordUpdateDTO;
import com.company.procurement.modules.approval.record.service.ApprovalRecordService;
import com.company.procurement.modules.approval.record.vo.ApprovalRecordVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/approval-records")
@RequiredArgsConstructor
public class ApprovalRecordController {

    private final ApprovalRecordService approvalRecordService;

    @GetMapping("/page")
    public Result<PageResult<ApprovalRecordVO>> page(@Valid ApprovalRecordPageQueryDTO queryDTO) {
        return Result.ok(approvalRecordService.page(queryDTO));
    }

    @GetMapping("/list")
    public Result<List<ApprovalRecordVO>> list(ApprovalRecordPageQueryDTO queryDTO) {
        return Result.ok(approvalRecordService.list(queryDTO));
    }

    @GetMapping("/{id:\\d+}")
    public Result<ApprovalRecordVO> detail(@PathVariable Long id) {
        return Result.ok(approvalRecordService.detail(id));
    }

    @PostMapping
    public Result<Long> create(@RequestBody @Valid ApprovalRecordCreateDTO dto) {
        return Result.ok(approvalRecordService.create(dto));
    }

    @PutMapping("/{id:\\d+}")
    public Result<Void> update(@PathVariable Long id, @RequestBody @Valid ApprovalRecordUpdateDTO dto) {
        approvalRecordService.update(id, dto);
        return Result.ok();
    }

    @DeleteMapping("/{id:\\d+}")
    public Result<Void> remove(@PathVariable Long id) {
        approvalRecordService.remove(id);
        return Result.ok();
    }
}
