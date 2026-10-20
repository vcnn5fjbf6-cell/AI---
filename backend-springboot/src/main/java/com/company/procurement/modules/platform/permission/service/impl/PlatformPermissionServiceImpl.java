package com.company.procurement.modules.platform.permission.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.company.procurement.common.exception.BizException;
import com.company.procurement.common.result.PageResult;
import com.company.procurement.common.security.AuthContextHolder;
import com.company.procurement.common.security.AuthUser;
import com.company.procurement.modules.ehs.config.EhsProperties;
import com.company.procurement.modules.outsourcing.config.OutsourcingProperties;
import com.company.procurement.modules.platform.permission.dto.PlatformPermissionRequestCreateDTO;
import com.company.procurement.modules.platform.permission.dto.PlatformPermissionRequestQueryDTO;
import com.company.procurement.modules.platform.permission.dto.PlatformPermissionReviewDTO;
import com.company.procurement.modules.platform.permission.dto.PlatformUnitSaveDTO;
import com.company.procurement.modules.platform.permission.dto.PlatformUserAccessUpdateDTO;
import com.company.procurement.modules.platform.permission.entity.PlatformPermissionRequest;
import com.company.procurement.modules.platform.permission.entity.PlatformUnit;
import com.company.procurement.modules.platform.permission.entity.PlatformUnitAppScope;
import com.company.procurement.modules.platform.permission.entity.PlatformUserAppAccess;
import com.company.procurement.modules.platform.permission.mapper.PlatformPermissionRequestMapper;
import com.company.procurement.modules.platform.permission.mapper.PlatformUnitAppScopeMapper;
import com.company.procurement.modules.platform.permission.mapper.PlatformUnitMapper;
import com.company.procurement.modules.platform.permission.mapper.PlatformUserAppAccessMapper;
import com.company.procurement.modules.platform.permission.service.PlatformPermissionService;
import com.company.procurement.modules.platform.permission.vo.PlatformAppVO;
import com.company.procurement.modules.platform.permission.vo.PlatformMyAccessVO;
import com.company.procurement.modules.platform.permission.vo.PlatformPermissionRequestVO;
import com.company.procurement.modules.platform.permission.vo.PlatformSummaryVO;
import com.company.procurement.modules.platform.permission.vo.PlatformUnitVO;
import com.company.procurement.modules.platform.permission.vo.PlatformUserAccessVO;
import com.company.procurement.modules.system.role.entity.SysRole;
import com.company.procurement.modules.system.role.mapper.SysRoleMapper;
import com.company.procurement.modules.system.user.entity.SysUser;
import com.company.procurement.modules.system.user.entity.SysUserRole;
import com.company.procurement.modules.system.user.mapper.SysUserMapper;
import com.company.procurement.modules.system.user.mapper.SysUserRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PlatformPermissionServiceImpl implements PlatformPermissionService {

    private static final List<PlatformAppVO> APPS = List.of(
        new PlatformAppVO("MAINTENANCE", "维保管理"),
        new PlatformAppVO("PROCUREMENT", "采购管理"),
        new PlatformAppVO("OUTSOURCING", "工程外包管理"),
        new PlatformAppVO("EHS", "EHS工作台"),
        new PlatformAppVO("PLATFORM", "平台管理")
    );

    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final EhsProperties ehsProperties;
    private final OutsourcingProperties outsourcingProperties;
    private final SysRoleMapper roleMapper;
    private final PlatformUnitMapper unitMapper;
    private final PlatformUnitAppScopeMapper unitAppScopeMapper;
    private final PlatformUserAppAccessMapper userAppAccessMapper;
    private final PlatformPermissionRequestMapper requestMapper;

    @Override
    public List<PlatformAppVO> apps() {
        return availableApps();
    }

    @Override
    public PlatformSummaryVO summary() {
        PlatformSummaryVO vo = new PlatformSummaryVO();
        vo.setTotalUsers(userMapper.selectCount(new LambdaQueryWrapper<SysUser>()));
        vo.setLoginEnabledUsers(userMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getStatus, 1)));
        vo.setAccessBindings(userAppAccessMapper.selectCount(new LambdaQueryWrapper<PlatformUserAppAccess>()
            .eq(PlatformUserAppAccess::getStatus, 1)));
        vo.setPendingRequests(requestMapper.selectCount(new LambdaQueryWrapper<PlatformPermissionRequest>()
            .eq(PlatformPermissionRequest::getStatus, "PENDING")));
        return vo;
    }

    @Override
    public List<PlatformUserAccessVO> userAccessList() {
        List<SysUser> users = userMapper.selectList(new LambdaQueryWrapper<SysUser>().orderByDesc(SysUser::getUpdatedAt));
        Map<Long, List<String>> accessMap = accessMap();
        return users.stream().map(user -> toUserAccessVO(user, accessMap.getOrDefault(user.getId(), List.of()))).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUserAccess(Long userId, PlatformUserAccessUpdateDTO dto) {
        SysUser user = requireUser(userId);
        user.setStatus(dto.getLoginEnabled() == null || dto.getLoginEnabled() != 1 ? 0 : 1);
        userMapper.updateById(user);
        replaceUserApps(userId, normalizeAppCodes(dto.getAppCodes()), "管理员维护");
    }

    @Override
    public List<PlatformUnitVO> unitList() {
        List<PlatformUnit> units = unitMapper.selectList(new LambdaQueryWrapper<PlatformUnit>().orderByAsc(PlatformUnit::getUnitCode));
        Map<Long, List<String>> scopeMap = unitScopeMap();
        return units.stream().map(unit -> toUnitVO(unit, scopeMap.getOrDefault(unit.getId(), List.of()))).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveUnit(PlatformUnitSaveDTO dto) {
        String unitCode = normalizeCode(dto.getUnitCode());
        LambdaQueryWrapper<PlatformUnit> duplicateWrapper = new LambdaQueryWrapper<PlatformUnit>().eq(PlatformUnit::getUnitCode, unitCode);
        if (dto.getId() != null) {
            duplicateWrapper.ne(PlatformUnit::getId, dto.getId());
        }
        if (unitMapper.selectCount(duplicateWrapper) > 0) {
            throw new BizException("UNIT_CODE_EXISTS", "单元编码已存在");
        }

        PlatformUnit unit = dto.getId() == null ? new PlatformUnit() : unitMapper.selectById(dto.getId());
        if (unit == null) {
            throw new BizException("UNIT_NOT_FOUND", "单元不存在");
        }
        unit.setUnitCode(unitCode);
        unit.setUnitName(dto.getUnitName().trim());
        unit.setParentId(dto.getParentId());
        unit.setStatus(dto.getStatus());
        unit.setRemark(dto.getRemark());
        if (unit.getId() == null) {
            unitMapper.insert(unit);
        } else {
            unitMapper.updateById(unit);
        }
        replaceUnitScopes(unit.getId(), normalizeAppCodes(dto.getAppCodes()));
        return unit.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeUnit(Long id) {
        unitMapper.deleteById(id);
        unitAppScopeMapper.delete(new LambdaQueryWrapper<PlatformUnitAppScope>().eq(PlatformUnitAppScope::getUnitId, id));
    }

    @Override
    public PageResult<PlatformPermissionRequestVO> requestPage(PlatformPermissionRequestQueryDTO queryDTO) {
        LambdaQueryWrapper<PlatformPermissionRequest> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(queryDTO.getStatus())) {
            wrapper.eq(PlatformPermissionRequest::getStatus, normalizeCode(queryDTO.getStatus()));
        }
        if (StringUtils.hasText(queryDTO.getAppCode())) {
            wrapper.eq(PlatformPermissionRequest::getAppCode, normalizeCode(queryDTO.getAppCode()));
        }
        if (queryDTO.getApplicantUserId() != null) {
            wrapper.eq(PlatformPermissionRequest::getApplicantUserId, queryDTO.getApplicantUserId());
        }
        wrapper.orderByDesc(PlatformPermissionRequest::getCreatedAt);
        Page<PlatformPermissionRequest> page = requestMapper.selectPage(new Page<>(queryDTO.getPageNo(), queryDTO.getPageSize()), wrapper);
        List<PlatformPermissionRequestVO> records = page.getRecords().stream().map(this::toRequestVO).toList();
        return PageResult.of(page.getCurrent(), page.getSize(), page.getTotal(), records);
    }

    @Override
    public PlatformMyAccessVO myAccess() {
        AuthUser authUser = AuthContextHolder.requireUser();
        PlatformMyAccessVO vo = new PlatformMyAccessVO();
        vo.setAppCodes(enabledAppCodesByUserId(authUser.getUserId()));
        vo.setUnits(unitList().stream().filter(unit -> unit.getStatus() != null && unit.getStatus() == 1).toList());
        PlatformPermissionRequestQueryDTO query = new PlatformPermissionRequestQueryDTO();
        query.setPageNo(1);
        query.setPageSize(20);
        query.setApplicantUserId(authUser.getUserId());
        vo.setRecentRequests(requestPage(query).getRecords());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createRequest(PlatformPermissionRequestCreateDTO dto) {
        AuthUser authUser = AuthContextHolder.requireUser();
        SysUser user = requireUser(authUser.getUserId());
        PlatformUnit unit = unitMapper.selectById(dto.getUnitId());
        if (unit == null || unit.getStatus() == null || unit.getStatus() != 1) {
            throw new BizException("UNIT_NOT_AVAILABLE", "申请单元不可用");
        }
        String appCode = normalizeAppCode(dto.getAppCode());
        if (!isUnitAllowed(unit.getId(), appCode)) {
            throw new BizException("APP_NOT_ALLOWED_FOR_UNIT", "当前单元不可申请该系统权限");
        }
        if (enabledAppCodesByUserId(user.getId()).contains(appCode)) {
            throw new BizException("APP_ALREADY_GRANTED", "你已经拥有该系统权限");
        }
        Long pendingCount = requestMapper.selectCount(new LambdaQueryWrapper<PlatformPermissionRequest>()
            .eq(PlatformPermissionRequest::getApplicantUserId, user.getId())
            .eq(PlatformPermissionRequest::getAppCode, appCode)
            .eq(PlatformPermissionRequest::getStatus, "PENDING"));
        if (pendingCount > 0) {
            throw new BizException("REQUEST_PENDING", "该系统已有待审批申请");
        }

        PlatformPermissionRequest request = new PlatformPermissionRequest();
        request.setApplicantUserId(user.getId());
        request.setApplicantName(user.getRealName());
        request.setApplicantEmail(user.getEmail());
        request.setApplicantMobile(user.getMobile());
        request.setUnitId(unit.getId());
        request.setAppCode(appCode);
        request.setRoleCode(StringUtils.hasText(dto.getRoleCode()) ? normalizeCode(dto.getRoleCode()) : defaultRoleCode(appCode));
        request.setReason(dto.getReason());
        request.setStatus("PENDING");
        requestMapper.insert(request);
        return request.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reviewRequest(Long id, PlatformPermissionReviewDTO dto) {
        PlatformPermissionRequest request = requestMapper.selectById(id);
        if (request == null) {
            throw new BizException("REQUEST_NOT_FOUND", "权限申请不存在");
        }
        if (!"PENDING".equals(request.getStatus())) {
            throw new BizException("REQUEST_ALREADY_REVIEWED", "权限申请已审批");
        }
        AuthUser reviewer = AuthContextHolder.requireUser();
        String action = normalizeCode(dto.getAction());
        if ("APPROVE".equals(action) || "APPROVED".equals(action)) {
            request.setStatus("APPROVED");
            grantUserApp(request.getApplicantUserId(), request.getAppCode(), "权限申请审批通过");
            bindRoleByCode(request.getApplicantUserId(), request.getRoleCode());
        } else if ("REJECT".equals(action) || "REJECTED".equals(action)) {
            request.setStatus("REJECTED");
        } else {
            throw new BizException("INVALID_REVIEW_ACTION", "审批动作不合法");
        }
        request.setReviewerUserId(reviewer.getUserId());
        request.setReviewComment(dto.getComment());
        request.setReviewedAt(LocalDateTime.now());
        requestMapper.updateById(request);
    }

    @Override
    public List<String> enabledAppCodesByUserId(Long userId) {
        return userAppAccessMapper.selectList(new LambdaQueryWrapper<PlatformUserAppAccess>()
                .eq(PlatformUserAppAccess::getUserId, userId)
                .eq(PlatformUserAppAccess::getStatus, 1))
            .stream()
            .map(PlatformUserAppAccess::getAppCode)
            .filter(StringUtils::hasText)
            .map(this::normalizeCode)
            .distinct()
            .toList();
    }

    private void replaceUserApps(Long userId, List<String> appCodes, String remark) {
        List<PlatformUserAppAccess> existing = userAppAccessMapper.selectList(new LambdaQueryWrapper<PlatformUserAppAccess>()
            .eq(PlatformUserAppAccess::getUserId, userId));
        for (PlatformUserAppAccess access : existing) {
            if (appCodes.contains(normalizeCode(access.getAppCode()))) {
                access.setStatus(1);
                access.setRemark(remark);
                access.setGrantedBy(currentUserId());
                access.setGrantedAt(LocalDateTime.now());
                userAppAccessMapper.updateById(access);
            } else {
                access.setStatus(0);
                userAppAccessMapper.updateById(access);
            }
        }
        List<String> existingCodes = existing.stream().map(item -> normalizeCode(item.getAppCode())).toList();
        for (String appCode : appCodes) {
            if (!existingCodes.contains(appCode)) {
                grantUserApp(userId, appCode, remark);
            }
        }
    }

    private void grantUserApp(Long userId, String appCode, String remark) {
        String normalizedAppCode = normalizeAppCode(appCode);
        PlatformUserAppAccess access = userAppAccessMapper.selectOne(new LambdaQueryWrapper<PlatformUserAppAccess>()
            .eq(PlatformUserAppAccess::getUserId, userId)
            .eq(PlatformUserAppAccess::getAppCode, normalizedAppCode)
            .last("LIMIT 1"));
        if (access == null) {
            access = new PlatformUserAppAccess();
            access.setUserId(userId);
            access.setAppCode(normalizedAppCode);
        }
        access.setStatus(1);
        access.setGrantedBy(currentUserId());
        access.setGrantedAt(LocalDateTime.now());
        access.setRemark(remark);
        if (access.getId() == null) {
            userAppAccessMapper.insert(access);
        } else {
            userAppAccessMapper.updateById(access);
        }
    }

    private void replaceUnitScopes(Long unitId, List<String> appCodes) {
        List<PlatformUnitAppScope> existing = unitAppScopeMapper.selectList(new LambdaQueryWrapper<PlatformUnitAppScope>()
            .eq(PlatformUnitAppScope::getUnitId, unitId));
        for (PlatformUnitAppScope scope : existing) {
            if (appCodes.contains(normalizeCode(scope.getAppCode()))) {
                scope.setStatus(1);
            } else {
                scope.setStatus(0);
            }
            unitAppScopeMapper.updateById(scope);
        }
        List<String> existingCodes = existing.stream().map(item -> normalizeCode(item.getAppCode())).toList();
        for (String appCode : appCodes) {
            if (!existingCodes.contains(appCode)) {
                PlatformUnitAppScope scope = new PlatformUnitAppScope();
                scope.setUnitId(unitId);
                scope.setAppCode(appCode);
                scope.setStatus(1);
                scope.setRemark("管理员维护");
                unitAppScopeMapper.insert(scope);
            }
        }
    }

    private void bindRoleByCode(Long userId, String roleCode) {
        if (!StringUtils.hasText(roleCode)) {
            return;
        }
        SysRole role = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
            .eq(SysRole::getRoleCode, normalizeCode(roleCode))
            .eq(SysRole::getStatus, 1)
            .eq(SysRole::getIsDeleted, 0)
            .last("LIMIT 1"));
        if (role == null) {
            return;
        }
        Long exists = userRoleMapper.selectCount(new LambdaQueryWrapper<SysUserRole>()
            .eq(SysUserRole::getUserId, userId)
            .eq(SysUserRole::getRoleId, role.getId()));
        if (exists > 0) {
            return;
        }
        SysUserRole relation = new SysUserRole();
        relation.setUserId(userId);
        relation.setRoleId(role.getId());
        relation.setStatus(1);
        userRoleMapper.insert(relation);
    }

    private boolean isUnitAllowed(Long unitId, String appCode) {
        return unitAppScopeMapper.selectCount(new LambdaQueryWrapper<PlatformUnitAppScope>()
            .eq(PlatformUnitAppScope::getUnitId, unitId)
            .eq(PlatformUnitAppScope::getAppCode, normalizeAppCode(appCode))
            .eq(PlatformUnitAppScope::getStatus, 1)) > 0;
    }

    private Map<Long, List<String>> accessMap() {
        Map<Long, List<String>> map = new LinkedHashMap<>();
        for (PlatformUserAppAccess access : userAppAccessMapper.selectList(new LambdaQueryWrapper<PlatformUserAppAccess>()
            .eq(PlatformUserAppAccess::getStatus, 1))) {
            map.computeIfAbsent(access.getUserId(), key -> new ArrayList<>()).add(normalizeCode(access.getAppCode()));
        }
        return map;
    }

    private Map<Long, List<String>> unitScopeMap() {
        Map<Long, List<String>> map = new LinkedHashMap<>();
        for (PlatformUnitAppScope scope : unitAppScopeMapper.selectList(new LambdaQueryWrapper<PlatformUnitAppScope>()
            .eq(PlatformUnitAppScope::getStatus, 1))) {
            map.computeIfAbsent(scope.getUnitId(), key -> new ArrayList<>()).add(normalizeCode(scope.getAppCode()));
        }
        return map;
    }

    private PlatformUserAccessVO toUserAccessVO(SysUser user, List<String> appCodes) {
        PlatformUserAccessVO vo = new PlatformUserAccessVO();
        vo.setUserId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        vo.setUserType(user.getUserType());
        vo.setDeptCode(user.getDeptCode());
        vo.setDeptName(user.getDeptName());
        vo.setMobile(user.getMobile());
        vo.setEmail(user.getEmail());
        vo.setLoginEnabled(user.getStatus());
        vo.setAppCodes(appCodes.stream().distinct().sorted().toList());
        return vo;
    }

    private PlatformUnitVO toUnitVO(PlatformUnit unit, List<String> appCodes) {
        PlatformUnitVO vo = new PlatformUnitVO();
        vo.setId(unit.getId());
        vo.setUnitCode(unit.getUnitCode());
        vo.setUnitName(unit.getUnitName());
        vo.setParentId(unit.getParentId());
        vo.setStatus(unit.getStatus());
        vo.setRemark(unit.getRemark());
        vo.setAppCodes(appCodes.stream().distinct().sorted().toList());
        vo.setUpdatedAt(unit.getUpdatedAt());
        return vo;
    }

    private PlatformPermissionRequestVO toRequestVO(PlatformPermissionRequest request) {
        PlatformPermissionRequestVO vo = new PlatformPermissionRequestVO();
        vo.setId(request.getId());
        vo.setApplicantUserId(request.getApplicantUserId());
        vo.setApplicantName(request.getApplicantName());
        vo.setApplicantEmail(request.getApplicantEmail());
        vo.setApplicantMobile(request.getApplicantMobile());
        vo.setUnitId(request.getUnitId());
        PlatformUnit unit = request.getUnitId() == null ? null : unitMapper.selectById(request.getUnitId());
        vo.setUnitName(unit == null ? "" : unit.getUnitName());
        vo.setAppCode(request.getAppCode());
        vo.setAppName(appName(request.getAppCode()));
        vo.setRoleCode(request.getRoleCode());
        vo.setReason(request.getReason());
        vo.setStatus(request.getStatus());
        vo.setReviewerUserId(request.getReviewerUserId());
        vo.setReviewComment(request.getReviewComment());
        vo.setReviewedAt(request.getReviewedAt());
        vo.setCreatedAt(request.getCreatedAt());
        return vo;
    }

    private SysUser requireUser(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException("USER_NOT_FOUND", "用户不存在");
        }
        return user;
    }

    private List<String> normalizeAppCodes(List<String> appCodes) {
        if (appCodes == null) {
            return List.of();
        }
        return appCodes.stream()
            .filter(StringUtils::hasText)
            .map(this::normalizeAppCode)
            .distinct()
            .sorted(Comparator.naturalOrder())
            .toList();
    }

    private String normalizeAppCode(String appCode) {
        String normalized = normalizeCode(appCode);
        boolean exists = availableApps().stream().anyMatch(app -> Objects.equals(app.getAppCode(), normalized));
        if (!exists) {
            throw new BizException("APP_NOT_FOUND", "系统编码不存在");
        }
        return normalized;
    }

    private String normalizeCode(String code) {
        return StringUtils.hasText(code) ? code.trim().toUpperCase(Locale.ROOT) : "";
    }

    private String appName(String appCode) {
        String normalized = normalizeCode(appCode);
        return availableApps().stream()
            .filter(app -> app.getAppCode().equals(normalized))
            .map(PlatformAppVO::getAppName)
            .findFirst()
            .orElse(normalized);
    }

    private String defaultRoleCode(String appCode) {
        return switch (normalizeCode(appCode)) {
            case "PROCUREMENT" -> "PURCHASER";
            case "MAINTENANCE" -> "ENGINEER";
            case "OUTSOURCING" -> "ENGINEER";
            case "EHS" -> "ENGINEER";
            case "PLATFORM" -> "";
            default -> "";
        };
    }

    private Long currentUserId() {
        AuthUser authUser = AuthContextHolder.get();
        return authUser == null ? 0L : authUser.getUserId();
    }

    private List<PlatformAppVO> availableApps() {
        return APPS.stream()
            .filter(app -> ehsProperties.isEnabled() || !"EHS".equals(app.getAppCode()))
            .filter(app -> outsourcingProperties.isEnabled() || !"OUTSOURCING".equals(app.getAppCode()))
            .toList();
    }
}
