package com.company.procurement.modules.system.role.controller;

import com.company.procurement.common.result.PageResult;
import com.company.procurement.common.result.Result;
import com.company.procurement.modules.system.role.dto.RoleCreateDTO;
import com.company.procurement.modules.system.role.dto.RolePageQueryDTO;
import com.company.procurement.modules.system.role.dto.RolePermissionAssignDTO;
import com.company.procurement.modules.system.role.dto.RoleUpdateDTO;
import com.company.procurement.modules.system.role.service.SysRoleService;
import com.company.procurement.modules.system.role.vo.PermissionVO;
import com.company.procurement.modules.system.role.vo.RoleVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/roles")
@RequiredArgsConstructor
public class SysRoleController {

    private final SysRoleService roleService;

    @GetMapping("/page")
    public Result<PageResult<RoleVO>> page(@Valid RolePageQueryDTO queryDTO) {
        return Result.ok(roleService.page(queryDTO));
    }

    @GetMapping("/list")
    public Result<List<RoleVO>> list(RolePageQueryDTO queryDTO) {
        return Result.ok(roleService.list(queryDTO));
    }

    @GetMapping("/permissions/list")
    public Result<List<PermissionVO>> permissionList() {
        return Result.ok(roleService.listAllPermissions());
    }

    @GetMapping("/{id:\\d+}")
    public Result<RoleVO> detail(@PathVariable Long id) {
        return Result.ok(roleService.detail(id));
    }

    @PostMapping
    public Result<Long> create(@RequestBody @Valid RoleCreateDTO dto) {
        return Result.ok(roleService.create(dto));
    }

    @PutMapping("/{id:\\d+}")
    public Result<Void> update(@PathVariable Long id, @RequestBody @Valid RoleUpdateDTO dto) {
        roleService.update(id, dto);
        return Result.ok();
    }

    @PutMapping("/{id:\\d+}/permissions")
    public Result<Void> assignPermissions(@PathVariable Long id, @RequestBody @Valid RolePermissionAssignDTO dto) {
        roleService.assignPermissions(id, dto);
        return Result.ok();
    }

    @DeleteMapping("/{id:\\d+}")
    public Result<Void> remove(@PathVariable Long id) {
        roleService.remove(id);
        return Result.ok();
    }
}
