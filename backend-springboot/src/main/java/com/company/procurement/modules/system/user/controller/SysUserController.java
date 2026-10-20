package com.company.procurement.modules.system.user.controller;

import com.company.procurement.common.result.PageResult;
import com.company.procurement.common.result.Result;
import com.company.procurement.common.export.ExcelExportUtils;
import com.company.procurement.modules.platform.importlog.service.ImportFileLogService;
import com.company.procurement.modules.system.user.dto.FeishuUserSyncDTO;
import com.company.procurement.modules.system.user.dto.UserCreateDTO;
import com.company.procurement.modules.system.user.dto.UserOffboardingDTO;
import com.company.procurement.modules.system.user.dto.UserPageQueryDTO;
import com.company.procurement.modules.system.user.dto.UserUpdateDTO;
import com.company.procurement.modules.system.user.service.SysUserService;
import com.company.procurement.modules.system.user.service.UserOffboardingService;
import com.company.procurement.modules.system.user.vo.FeishuDepartmentSearchVO;
import com.company.procurement.modules.system.user.vo.FeishuUserSyncResultVO;
import com.company.procurement.modules.system.user.vo.UserImportResultVO;
import com.company.procurement.modules.system.user.vo.UserOffboardingImpactVO;
import com.company.procurement.modules.system.user.vo.UserVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class SysUserController {

    private final SysUserService userService;
    private final UserOffboardingService userOffboardingService;
    private final ImportFileLogService importFileLogService;

    @GetMapping("/page")
    public Result<PageResult<UserVO>> page(@Valid UserPageQueryDTO queryDTO) {
        return Result.ok(userService.page(queryDTO));
    }

    @GetMapping("/list")
    public Result<List<UserVO>> list(UserPageQueryDTO queryDTO) {
        return Result.ok(userService.list(queryDTO));
    }

    @GetMapping("/{id:\\d+}")
    public Result<UserVO> detail(@PathVariable Long id) {
        return Result.ok(userService.detail(id));
    }

    @GetMapping("/{id:\\d+}/offboarding-impact")
    public Result<UserOffboardingImpactVO> offboardingImpact(@PathVariable Long id) {
        return Result.ok(userOffboardingService.impact(id));
    }

    @PostMapping
    public Result<Long> create(@RequestBody @Valid UserCreateDTO dto) {
        return Result.ok(userService.create(dto));
    }

    @PostMapping("/feishu-sync")
    public Result<FeishuUserSyncResultVO> syncFeishuUsers(@RequestBody(required = false) FeishuUserSyncDTO dto) {
        return Result.ok(userService.syncFeishuUsers(dto));
    }

    @GetMapping("/feishu-departments/search")
    public Result<List<FeishuDepartmentSearchVO>> searchFeishuDepartments(@RequestParam String keyword) {
        return Result.ok(userService.searchFeishuDepartments(keyword));
    }

    @GetMapping("/import-template")
    public ResponseEntity<byte[]> importTemplate() {
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of(
            "工号",
            "用户名",
            "姓名",
            "用户类型",
            "供应商ID",
            "区域ID",
            "部门编号",
            "部门名称",
            "手机号",
            "邮箱",
            "飞书 open_id",
            "飞书 user_id",
            "飞书 union_id",
            "状态",
            "角色编码",
            "初始密码",
            "备注"
        ));
        rows.add(List.of(
            "100001",
            "100001",
            "张三",
            "工程师",
            "",
            "1",
            "N420N00077",
            "[N420N00077]世纪互联-业务共享中台-DC运维事业部-华北一部-酒仙桥-M6",
            "13800000001",
            "demo.user011@example.invalid",
            "ou_xxx",
            "user_xxx",
            "on_xxx",
            "启用",
            "ENGINEER",
            "123456",
            "批量导入示例"
        ));
        ExcelExportUtils.WorkbookOptions options = ExcelExportUtils.WorkbookOptions.builder()
            .freezeHeader(true)
            .autoFilter(true)
            .requiredColumns(2, 3, 13, 15)
            .dropdown(3, List.of("工程师", "楼长", "经理", "区域经理", "高级经理", "采购员", "供应商账号", "最高管理员"))
            .dropdown(13, List.of("启用", "停用"))
            .dropdown(14, List.of("ENGINEER", "FLOOR_LEADER", "MANAGER", "REGION_MANAGER", "SENIOR_MANAGER", "PURCHASER", "SUPPLIER", "SUPER_ADMIN"))
            .note(0, 0, "员工账号建议填写工号；供应商账号可留空。")
            .note(0, 1, "员工账号建议与工号一致；留空时系统使用工号。")
            .note(0, 3, "请选择下拉值；系统也兼容英文编码，例如 ENGINEER、SUPPLIER。")
            .note(0, 4, "仅供应商账号必填，填写系统供应商ID。")
            .note(0, 5, "默认区域ID为 1；多区域账号按实际区域填写。")
            .note(0, 10, "飞书ID字段可留空，通常由飞书同步或后续绑定维护。")
            .note(0, 14, "可留空，系统会按用户类型绑定主角色；多个角色请用英文逗号分隔。")
            .note(0, 15, "新增用户可留空，系统统一使用 123456 并要求首次登录修改；更新已有用户时可留空保持原密码。")
            .build();
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename("人员批量导入模板.xlsx", StandardCharsets.UTF_8)
                .build()
                .toString())
            .contentType(MediaType.parseMediaType(ExcelExportUtils.XLSX_CONTENT_TYPE))
            .body(ExcelExportUtils.toXlsxBytes("人员导入模板", rows, options));
    }

    @PostMapping("/import")
    public Result<UserImportResultVO> importUsers(@RequestParam("file") MultipartFile file) {
        Long logId = importFileLogService.recordWebImport("USER_IMPORT", "用户批量导入", file);
        try {
            UserImportResultVO result = userService.importUsers(file);
            importFileLogService.markSuccess(
                logId,
                result.getTotalCount(),
                result.getSuccessCount(),
                result.getFailedCount(),
                "新增 " + result.getCreatedCount() + " 个，更新 " + result.getUpdatedCount() + " 个"
            );
            return Result.ok(result);
        } catch (RuntimeException ex) {
            importFileLogService.markFailed(logId, ex);
            throw ex;
        }
    }

    @PutMapping("/{id:\\d+}")
    public Result<Void> update(@PathVariable Long id, @RequestBody @Valid UserUpdateDTO dto) {
        userService.update(id, dto);
        return Result.ok();
    }

    @PostMapping("/{id:\\d+}/offboarding")
    public Result<Void> offboard(@PathVariable Long id, @RequestBody @Valid UserOffboardingDTO dto) {
        userOffboardingService.offboard(id, dto);
        return Result.ok();
    }

    @DeleteMapping("/{id:\\d+}")
    public Result<Void> remove(@PathVariable Long id) {
        userService.remove(id);
        return Result.ok();
    }
}
