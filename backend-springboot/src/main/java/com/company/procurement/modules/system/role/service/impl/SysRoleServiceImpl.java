package com.company.procurement.modules.system.role.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.company.procurement.common.exception.BizException;
import com.company.procurement.common.result.PageResult;
import com.company.procurement.common.security.AuthContextHolder;
import com.company.procurement.common.security.AuthUser;
import com.company.procurement.common.security.DataScopeHelper;
import com.company.procurement.modules.system.role.dto.RoleCreateDTO;
import com.company.procurement.modules.system.role.dto.RolePageQueryDTO;
import com.company.procurement.modules.system.role.dto.RolePermissionAssignDTO;
import com.company.procurement.modules.system.role.dto.RoleUpdateDTO;
import com.company.procurement.modules.system.role.entity.SysPermission;
import com.company.procurement.modules.system.role.entity.SysRole;
import com.company.procurement.modules.system.role.entity.SysRolePermission;
import com.company.procurement.modules.system.role.mapper.SysPermissionMapper;
import com.company.procurement.modules.system.role.mapper.SysRoleMapper;
import com.company.procurement.modules.system.role.mapper.SysRolePermissionMapper;
import com.company.procurement.modules.system.role.service.SysRoleService;
import com.company.procurement.modules.system.role.vo.PermissionVO;
import com.company.procurement.modules.system.role.vo.RoleVO;
import com.company.procurement.modules.system.user.entity.SysUserRole;
import com.company.procurement.modules.system.user.mapper.SysUserRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class SysRoleServiceImpl extends ServiceImpl<SysRoleMapper, SysRole> implements SysRoleService {

    private static final List<String> APPROVAL_NODE_ONLY_ROLE_CODES = List.of(
        "DUTY_LEADER",
        "PROFESSIONAL_ENGINEER",
        "APPROVAL_V7",
        "APPROVAL_V8"
    );

    private static final Map<String, String> PERMISSION_NAME_OVERRIDES = Map.ofEntries(
        Map.entry("sys_user:create", "新增用户"),
        Map.entry("sys_user:update", "编辑用户"),
        Map.entry("sys_user:delete", "删除用户"),
        Map.entry("sys_user:view", "查看用户"),
        Map.entry("sys_user:import", "批量导入用户"),
        Map.entry("sys_user:feishu_sync", "同步飞书组织"),
        Map.entry("sys_role:create", "新增角色"),
        Map.entry("sys_role:update", "编辑角色"),
        Map.entry("sys_role:delete", "删除角色"),
        Map.entry("sys_role:view", "查看角色"),
        Map.entry("supplier:create", "新增供应商"),
        Map.entry("supplier:update", "编辑供应商"),
        Map.entry("supplier:delete", "删除供应商"),
        Map.entry("supplier:view", "查看供应商"),
        Map.entry("supplier:ban", "封禁供应商"),
        Map.entry("supplier:unban", "解封供应商"),
        Map.entry("supplier:bidding-suspension-release", "解除投标暂停"),
        Map.entry("supplier_quote:read", "查看供应商报价"),
        Map.entry("purchase_request:create", "新增采购申请"),
        Map.entry("purchase_request:update", "编辑采购申请"),
        Map.entry("purchase_request:submit", "提交采购申请"),
        Map.entry("purchase_request:delete", "删除采购申请"),
        Map.entry("approval:action", "审批操作"),
        Map.entry("platform_permission:manage", "平台权限管理"),
        Map.entry("platform_permission:apply", "平台权限申请"),
        Map.entry("workbench_notice:manage", "工作台公告维护"),
        Map.entry("platform_error_log:manage", "错误台账管理"),
        Map.entry("platform_ai_message_log:manage", "\u0041\u0049\u52a9\u624b\u6d88\u606f\u65e5\u5fd7"),
        Map.entry("procurement_route:manage", "部门审批配置"),
        Map.entry("procurement_route:address", "部门地址维护"),
        Map.entry("procurement_department:options", "查看采购部门选项"),
        Map.entry("procurement_settings:manage", "平台/采购设置"),
        Map.entry("procurement:demand-pool-publish", "需求池正式推送"),
        Map.entry("procurement:demand-pool-publish-test", "需求池测试推送"),
        Map.entry("monthly_procurement:submit", "飞书采购需求提交"),
        Map.entry("monthly_procurement:view", "查看月度采购池"),
        Map.entry("monthly_procurement:aggregate", "月度采购汇总"),
        Map.entry("monthly_procurement:supplement", "本月需求补充采购需求"),
        Map.entry("procurement_ai:start", "AI采购助手发起"),
        Map.entry("procurement_ai:view", "AI采购助手查看"),
        Map.entry("procurement_ai:confirm", "AI采购助手确认")
    );

    private final SysRolePermissionMapper rolePermissionMapper;
    private final SysPermissionMapper permissionMapper;
    private final SysUserRoleMapper userRoleMapper;

    @Override
    public PageResult<RoleVO> page(RolePageQueryDTO queryDTO) {
        LambdaQueryWrapper<SysRole> wrapper = buildQuery(queryDTO);
        Page<SysRole> page = baseMapper.selectPage(new Page<>(queryDTO.getPageNo(), queryDTO.getPageSize()), wrapper);
        List<RoleVO> records = page.getRecords().stream().map(this::toRoleVO).toList();
        return PageResult.of(page.getCurrent(), page.getSize(), page.getTotal(), records);
    }

    @Override
    public List<RoleVO> list(RolePageQueryDTO queryDTO) {
        return baseMapper.selectList(buildQuery(queryDTO)).stream().map(this::toRoleVO).toList();
    }

    @Override
    public RoleVO detail(Long id) {
        SysRole role = getById(id);
        if (role == null) {
            throw new BizException("ROLE_NOT_FOUND", "Role not found");
        }
        return toRoleVO(role);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(RoleCreateDTO dto) {
        ensureRoleManagePermission();
        ensureCanMaintainRole(dto.getRoleCode(), dto.getRoleType());
        ensureAdminTypeOnlyForSuperAdmin(dto.getRoleCode(), dto.getRoleType());
        checkRoleCodeUnique(dto.getRoleCode(), null);
        SysRole role = new SysRole();
        applyRole(dto.getRoleCode(), dto.getRoleName(), dto.getRoleType(), dto.getDataScopeType(),
            dto.getApprovalAmountMin(), dto.getApprovalAmountMax(), dto.getStatus(), dto.getDescription(), role);
        save(role);
        bindPermissions(role.getId(), dto.getPermissionIds());
        return role.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, RoleUpdateDTO dto) {
        ensureRoleManagePermission();
        SysRole role = getById(id);
        if (role == null) {
            throw new BizException("ROLE_NOT_FOUND", "Role not found");
        }
        ensureCanMaintainRole(role.getRoleCode(), role.getRoleType());
        ensureCanMaintainRole(dto.getRoleCode(), dto.getRoleType());
        ensureAdminTypeOnlyForSuperAdmin(dto.getRoleCode(), dto.getRoleType());
        checkRoleCodeUnique(dto.getRoleCode(), id);
        applyRole(dto.getRoleCode(), dto.getRoleName(), dto.getRoleType(), dto.getDataScopeType(),
            dto.getApprovalAmountMin(), dto.getApprovalAmountMax(), dto.getStatus(), dto.getDescription(), role);
        updateById(role);
        if (dto.getPermissionIds() != null) {
            bindPermissions(id, dto.getPermissionIds());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long id) {
        ensureRoleManagePermission();
        SysRole role = getById(id);
        if (role == null) {
            return;
        }
        ensureCanMaintainRole(role.getRoleCode(), role.getRoleType());
        Long userCount = userRoleMapper.selectCount(new LambdaQueryWrapper<SysUserRole>()
            .eq(SysUserRole::getRoleId, id));
        if (userCount != null && userCount > 0) {
            throw new BizException("ROLE_IN_USE", "Role is assigned to users and cannot be deleted");
        }
        removeById(id);
        rolePermissionMapper.hardDeleteByRoleId(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignPermissions(Long roleId, RolePermissionAssignDTO dto) {
        ensureRoleManagePermission();
        SysRole role = getById(roleId);
        if (role == null) {
            throw new BizException("ROLE_NOT_FOUND", "Role not found");
        }
        ensureCanMaintainRole(role.getRoleCode(), role.getRoleType());
        bindPermissions(roleId, dto.getPermissionIds());
    }

    @Override
    public List<PermissionVO> listAllPermissions() {
        List<SysPermission> permissions = permissionMapper.selectList(new LambdaQueryWrapper<SysPermission>()
            .eq(SysPermission::getStatus, 1)
            .orderByAsc(SysPermission::getModuleCode)
            .orderByAsc(SysPermission::getSortNo));
        List<PermissionVO> result = new ArrayList<>();
        for (SysPermission permission : permissions) {
            PermissionVO vo = new PermissionVO();
            vo.setId(permission.getId());
            vo.setPermCode(permission.getPermCode());
            vo.setPermName(normalizePermissionName(permission));
            vo.setModuleCode(permission.getModuleCode());
            vo.setPermType(permission.getPermType());
            vo.setParentId(permission.getParentId());
            vo.setPath(permission.getPath());
            vo.setHttpMethod(permission.getHttpMethod());
            vo.setSortNo(permission.getSortNo());
            vo.setStatus(permission.getStatus());
            vo.setDescription(permission.getDescription());
            result.add(vo);
        }
        return result;
    }

    private String normalizePermissionName(SysPermission permission) {
        if (permission == null || !StringUtils.hasText(permission.getPermCode())) {
            return permission == null ? "" : permission.getPermName();
        }
        return PERMISSION_NAME_OVERRIDES.getOrDefault(permission.getPermCode(), permission.getPermName());
    }

    private LambdaQueryWrapper<SysRole> buildQuery(RolePageQueryDTO queryDTO) {
        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(queryDTO.getRoleCode())) {
            wrapper.like(SysRole::getRoleCode, queryDTO.getRoleCode());
        }
        if (StringUtils.hasText(queryDTO.getRoleName())) {
            wrapper.like(SysRole::getRoleName, queryDTO.getRoleName());
        }
        if (queryDTO.getStatus() != null) {
            wrapper.eq(SysRole::getStatus, queryDTO.getStatus());
        }
        wrapper.notIn(SysRole::getRoleCode, APPROVAL_NODE_ONLY_ROLE_CODES);
        wrapper.orderByDesc(SysRole::getCreatedAt);
        return wrapper;
    }

    private RoleVO toRoleVO(SysRole role) {
        RoleVO vo = new RoleVO();
        vo.setId(role.getId());
        vo.setRoleCode(role.getRoleCode());
        vo.setRoleName(role.getRoleName());
        vo.setRoleType(role.getRoleType());
        vo.setDataScopeType(role.getDataScopeType());
        vo.setApprovalAmountMin(role.getApprovalAmountMin());
        vo.setApprovalAmountMax(role.getApprovalAmountMax());
        vo.setStatus(role.getStatus());
        vo.setDescription(role.getDescription());
        vo.setCreatedAt(role.getCreatedAt());
        vo.setUpdatedAt(role.getUpdatedAt());
        List<SysRolePermission> rolePermissions = rolePermissionMapper.selectList(new LambdaQueryWrapper<SysRolePermission>()
            .eq(SysRolePermission::getRoleId, role.getId()));
        vo.setPermissionIds(rolePermissions.stream().map(SysRolePermission::getPermissionId).toList());
        return vo;
    }

    private void checkRoleCodeUnique(String roleCode, Long excludeId) {
        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<SysRole>().eq(SysRole::getRoleCode, roleCode);
        if (excludeId != null) {
            wrapper.ne(SysRole::getId, excludeId);
        }
        if (baseMapper.selectCount(wrapper) > 0) {
            throw new BizException("ROLE_CODE_EXISTS", "Role code already exists");
        }
    }

    private void applyRole(String roleCode, String roleName, String roleType, String dataScopeType,
                           BigDecimal approvalAmountMin, BigDecimal approvalAmountMax,
                           Integer status, String description, SysRole role) {
        role.setRoleCode(roleCode);
        role.setRoleName(roleName);
        role.setRoleType(roleType);
        role.setDataScopeType(resolveRoleDataScopeType(roleCode, roleType));
        role.setApprovalAmountMin(approvalAmountMin);
        role.setApprovalAmountMax(approvalAmountMax);
        role.setStatus(status);
        role.setDescription(description);
    }

    private String resolveRoleDataScopeType(String roleCode, String roleType) {
        if ("SUPPLIER".equalsIgnoreCase(roleType)) {
            return DataScopeHelper.SCOPE_SELF;
        }
        if ("SUPER_ADMIN".equalsIgnoreCase(roleCode)) {
            return DataScopeHelper.SCOPE_ALL;
        }
        return DataScopeHelper.SCOPE_ORG;
    }

    private void bindPermissions(Long roleId, List<Long> permissionIds) {
        rolePermissionMapper.hardDeleteByRoleId(roleId);
        if (CollectionUtils.isEmpty(permissionIds)) {
            return;
        }
        for (Long permissionId : permissionIds.stream().filter(Objects::nonNull).distinct().toList()) {
            SysPermission permission = permissionMapper.selectById(permissionId);
            if (permission == null) {
                throw new BizException("PERMISSION_NOT_FOUND", "Permission not found: " + permissionId);
            }
            SysRolePermission relation = new SysRolePermission();
            relation.setRoleId(roleId);
            relation.setPermissionId(permissionId);
            relation.setStatus(1);
            rolePermissionMapper.insert(relation);
        }
    }

    private void ensureRoleManagePermission() {
        AuthUser authUser = AuthContextHolder.get();
        if (authUser == null || DataScopeHelper.isSystemAdmin(authUser)) {
            return;
        }
        throw new BizException("FORBIDDEN", "Only administrators can manage roles and permissions");
    }

    private void ensureCanMaintainRole(String roleCode, String roleType) {
        AuthUser authUser = AuthContextHolder.get();
        if (authUser == null || DataScopeHelper.isSuperAdmin(authUser)) {
            return;
        }
        if (isPrivilegedRole(roleCode, roleType)) {
            throw new BizException("FORBIDDEN", "Only super administrators can maintain privileged roles");
        }
    }

    private boolean isPrivilegedRole(String roleCode, String roleType) {
        return "SUPER_ADMIN".equalsIgnoreCase(roleCode);
    }

    private void ensureAdminTypeOnlyForSuperAdmin(String roleCode, String roleType) {
        if ("ADMIN".equalsIgnoreCase(roleType) && !"SUPER_ADMIN".equalsIgnoreCase(roleCode)) {
            throw new BizException("ADMIN_ROLE_RESERVED", "Only SUPER_ADMIN can use admin role type");
        }
    }
}
