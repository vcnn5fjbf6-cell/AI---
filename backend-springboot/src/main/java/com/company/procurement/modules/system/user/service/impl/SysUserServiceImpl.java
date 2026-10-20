package com.company.procurement.modules.system.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.company.procurement.modules.auth.config.FeishuAuthProperties;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.company.procurement.common.exception.BizException;
import com.company.procurement.common.export.ExcelImportUtils;
import com.company.procurement.common.result.PageResult;
import com.company.procurement.common.security.AuthContextHolder;
import com.company.procurement.common.security.AuthUser;
import com.company.procurement.common.security.DataScopeHelper;
import com.company.procurement.common.security.PasswordCrypto;
import com.company.procurement.modules.supplier.entity.Supplier;
import com.company.procurement.modules.supplier.mapper.SupplierMapper;
import com.company.procurement.modules.platform.permission.entity.PlatformUserAppAccess;
import com.company.procurement.modules.platform.permission.mapper.PlatformUserAppAccessMapper;
import com.company.procurement.modules.procurement.settings.entity.ProcurementSetting;
import com.company.procurement.modules.procurement.settings.mapper.ProcurementSettingMapper;
import com.company.procurement.modules.system.role.entity.SysRole;
import com.company.procurement.modules.system.role.mapper.SysRoleMapper;
import com.company.procurement.modules.system.department.service.UserDepartmentResolver;
import com.company.procurement.modules.system.user.dto.FeishuUserSyncDTO;
import com.company.procurement.modules.system.user.dto.UserCreateDTO;
import com.company.procurement.modules.system.user.dto.UserPageQueryDTO;
import com.company.procurement.modules.system.user.dto.UserUpdateDTO;
import com.company.procurement.modules.system.user.entity.SysUser;
import com.company.procurement.modules.system.user.entity.SysUserRole;
import com.company.procurement.modules.system.user.mapper.SysUserMapper;
import com.company.procurement.modules.system.user.mapper.SysUserRoleMapper;
import com.company.procurement.modules.system.user.service.SysUserService;
import com.company.procurement.modules.system.user.vo.FeishuDepartmentSearchVO;
import com.company.procurement.modules.system.user.vo.FeishuUserSyncResultVO;
import com.company.procurement.modules.system.user.vo.UserImportResultVO;
import com.company.procurement.modules.system.user.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
        new ParameterizedTypeReference<>() {
        };

    private static final String KEY_FEISHU_APP_ID = "feishu_app_id";
    private static final String KEY_FEISHU_APP_SECRET = "feishu_app_secret";
    private static final String KEY_FEISHU_ROOT_DEPARTMENT_ID = "feishu_root_department_id";
    private static final String KEY_FEISHU_DEPARTMENT_ID_TYPE = "feishu_department_id_type";
    private static final String DEFAULT_INITIAL_PASSWORD = "123456";
    private static final int STATUS_DEPARTED = 2;
    private static final Pattern DUPLICATE_USER_KEY_PATTERN = Pattern.compile(
        "Duplicate entry '([^']+)' for key '(?:sys_user\\.)?uk_user_(username|employee_no|mobile|email)'");

    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;
    private final SupplierMapper supplierMapper;
    private final PlatformUserAppAccessMapper userAppAccessMapper;
    private final ProcurementSettingMapper procurementSettingMapper;
    private final FeishuAuthProperties feishuProperties;
    private final UserDepartmentResolver userDepartmentResolver;

    private final RestClient restClient = RestClient.builder().build();

    private volatile String cachedTenantAccessToken;
    private volatile long cachedTenantAccessTokenExpiresAt;
    private volatile String cachedTenantAccessTokenSignature;

    @Override
    public PageResult<UserVO> page(UserPageQueryDTO queryDTO) {
        LambdaQueryWrapper<SysUser> wrapper = buildQuery(queryDTO);
        Page<SysUser> page = baseMapper.selectPage(new Page<>(queryDTO.getPageNo(), queryDTO.getPageSize()), wrapper);
        List<UserVO> records = page.getRecords().stream().map(this::toUserVO).toList();
        return PageResult.of(page.getCurrent(), page.getSize(), page.getTotal(), records);
    }

    @Override
    public List<UserVO> list(UserPageQueryDTO queryDTO) {
        return baseMapper.selectList(buildQuery(queryDTO)).stream().map(this::toUserVO).toList();
    }

    @Override
    public UserVO detail(Long id) {
        SysUser user = getById(id);
        if (user == null) {
            throw new BizException("USER_NOT_FOUND", "User not found");
        }
        ensureCanAccessTargetUser(user);
        applyResolvedDepartment(user);
        return toUserVO(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(UserCreateDTO dto) {
        assertDedicatedOffboardingTransition(null, dto.getStatus());
        String userType = normalizeUserType(dto.getUserType());
        Long regionId = resolveUserRegionId(dto.getRegionId());
        ensureCanCreateOrUpdateUser(userType, regionId, dto.getSupplierId());
        String employeeNo = resolveEmployeeNo(userType, dto.getEmployeeNo());
        String username = resolveUsername(userType, dto.getUsername(), employeeNo, dto.getSupplierId(), null);
        List<Long> normalizedRoleIds = normalizeRoleIds(userType, dto.getRoleIds());
        String mobile = blankToNull(limit(dto.getMobile(), 32));
        String email = blankToNull(limit(dto.getEmail(), 128));
        SysUser deletedUser = findDeletedUserForCreate(employeeNo, username, mobile, email);
        if (deletedUser != null) {
            ensureCanAccessTargetUser(deletedUser);
            checkUpdateUniqueExceptEmail(username, employeeNo, mobile, deletedUser.getId());
            clearOccupiedEmailForCreate(email, deletedUser.getId(), dto.getConfirmClearOccupiedEmail());
            checkUpdateUnique(username, employeeNo, mobile, email, deletedUser.getId());
            validateSupplierUser(userType, dto.getSupplierId());
            UserDepartmentResolver.DepartmentFields department = userDepartmentResolver.resolve(dto.getDeptCode(), dto.getDeptName());
            applyUser(username, DEFAULT_INITIAL_PASSWORD, dto.getRealName(), employeeNo, userType, dto.getSupplierId(), regionId,
                department.deptCode(), department.deptName(), mobile, email, dto.getStatus(), dto.getDataScopeType(),
                1, dto.getRemark(), deletedUser);
            applyFeishuIds(deletedUser, dto.getFeishuUserId(), dto.getFeishuOpenId(), dto.getFeishuUnionId());
            restoreDeletedUser(deletedUser);
            bindRoles(deletedUser.getId(), normalizedRoleIds);
            grantDefaultPlatformAccessIfMissing(deletedUser.getId(), deletedUser.getUserType());
            return deletedUser.getId();
        }
        checkCreateUniqueExceptEmail(username, employeeNo, mobile);
        clearOccupiedEmailForCreate(email, null, dto.getConfirmClearOccupiedEmail());
        checkCreateUnique(username, employeeNo, mobile, email);
        validateSupplierUser(userType, dto.getSupplierId());
        UserDepartmentResolver.DepartmentFields department = userDepartmentResolver.resolve(dto.getDeptCode(), dto.getDeptName());
        SysUser user = new SysUser();
        applyUser(username, DEFAULT_INITIAL_PASSWORD, dto.getRealName(), employeeNo, userType, dto.getSupplierId(), regionId,
            department.deptCode(), department.deptName(), mobile, email, dto.getStatus(), dto.getDataScopeType(),
            1, dto.getRemark(), user);
        applyFeishuIds(user, dto.getFeishuUserId(), dto.getFeishuOpenId(), dto.getFeishuUnionId());
        save(user);
        bindRoles(user.getId(), normalizedRoleIds);
        grantDefaultPlatformAccess(user.getId(), user.getUserType());
        return user.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, UserUpdateDTO dto) {
        SysUser user = getById(id);
        if (user == null) {
            throw new BizException("USER_NOT_FOUND", "User not found");
        }
        ensureCanAccessTargetUser(user);
        assertDedicatedOffboardingTransition(user.getStatus(), dto.getStatus());
        String userType = normalizeUserType(dto.getUserType());
        Long regionId = resolveUserRegionId(dto.getRegionId());
        ensureCanCreateOrUpdateUser(userType, regionId, dto.getSupplierId());
        String employeeNo = resolveEmployeeNo(userType, dto.getEmployeeNo());
        String username = resolveUsername(userType, dto.getUsername(), employeeNo, dto.getSupplierId(), id);
        List<Long> normalizedRoleIds = dto.getRoleIds() == null ? null : normalizeRoleIds(userType, dto.getRoleIds());
        String mobile = blankToNull(limit(dto.getMobile(), 32));
        String email = blankToNull(limit(dto.getEmail(), 128));
        checkUpdateUnique(username, employeeNo, mobile, email, id);
        validateSupplierUser(userType, dto.getSupplierId());
        UserDepartmentResolver.DepartmentFields department = userDepartmentResolver.resolve(dto.getDeptCode(), dto.getDeptName());
        applyUser(username, dto.getPassword(), dto.getRealName(), employeeNo, userType, dto.getSupplierId(), regionId,
            department.deptCode(), department.deptName(), mobile, email, dto.getStatus(), dto.getDataScopeType(),
            dto.getMustChangePassword(), dto.getRemark(), user);
        applyFeishuIds(user, dto.getFeishuUserId(), dto.getFeishuOpenId(), dto.getFeishuUnionId());
        updateById(user);
        updateNullableUserFields(id, user);
        if (normalizedRoleIds != null) {
            bindRoles(id, normalizedRoleIds);
        }
        grantDefaultPlatformAccessIfMissing(id, normalizeUserType(dto.getUserType()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long id) {
        SysUser user = getById(id);
        if (user == null) {
            return;
        }
        ensureCanAccessTargetUser(user);
        removeById(id);
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, id));
        userAppAccessMapper.delete(new LambdaQueryWrapper<PlatformUserAppAccess>()
            .eq(PlatformUserAppAccess::getUserId, id));
    }

    @Override
    public FeishuUserSyncResultVO syncFeishuUsers(FeishuUserSyncDTO dto) {
        assertFeishuConfigured();
        FeishuUserSyncDTO options = dto == null ? new FeishuUserSyncDTO() : dto;
        List<String> rootDepartmentIds = resolveRootDepartmentIds(options.getRootDepartmentId());
        int pageSize = Math.max(10, Math.min(100, feishuProperties.getDirectorySyncPageSize()));
        boolean recursive = options.getRecursive() == null || options.getRecursive();
        boolean updateExisting = options.getUpdateExisting() == null || options.getUpdateExisting();
        boolean createMissing = options.getCreateMissing() == null || options.getCreateMissing();
        boolean includeInactive = options.getIncludeInactive() != null && options.getIncludeInactive();

        FeishuUserSyncResultVO result = new FeishuUserSyncResultVO();
        result.setSyncedAt(LocalDateTime.now());
        List<FeishuDepartment> departments = fetchFeishuDepartments(rootDepartmentIds, recursive, pageSize);
        result.setDepartmentCount(departments.size());

        Set<String> seenUserKeys = new LinkedHashSet<>();
        String defaultUserType = resolveDefaultFeishuUserType(options.getDefaultUserType());
        Long defaultRegionId = options.getDefaultRegionId() == null ? 1L : options.getDefaultRegionId();

        for (FeishuDepartment department : departments) {
            List<FeishuDirectoryUser> users = fetchFeishuUsers(department, pageSize);
            for (FeishuDirectoryUser user : users) {
                String dedupeKey = user.dedupeKey();
                if (StringUtils.hasText(dedupeKey) && !seenUserKeys.add(dedupeKey)) {
                    continue;
                }
                result.setFeishuUserCount(result.getFeishuUserCount() + 1);
                if (!includeInactive && !user.active()) {
                    result.setSkippedCount(result.getSkippedCount() + 1);
                    continue;
                }
                syncOneFeishuUser(user, defaultUserType, defaultRegionId, createMissing, updateExisting, result);
            }
        }
        return result;
    }

    @Override
    public List<FeishuDepartmentSearchVO> searchFeishuDepartments(String keyword) {
        assertFeishuConfigured();
        String query = keyword == null ? "" : keyword.trim();
        if (!StringUtils.hasText(query)) {
            throw new BizException("FEISHU_DEPARTMENT_SEARCH_KEYWORD_REQUIRED", "请输入部门名称关键词");
        }
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(feishuProperties.getDepartmentSearchUrl())
            .queryParam("query", query)
            .queryParam("department_id_type", resolveFeishuDepartmentIdType())
            .queryParam("page_size", 50);
        Map<String, Object> response = feishuGet(builder, "FEISHU_DEPARTMENT_SEARCH_FAILED", "搜索飞书部门失败");
        Map<String, Object> data = unwrapFeishuData(response, "FEISHU_DEPARTMENT_SEARCH_FAILED", "搜索飞书部门失败");
        return objectList(data.get("items")).stream()
            .map(this::toFeishuDepartmentSearchVO)
            .filter(item -> StringUtils.hasText(item.getDepartmentId()) || StringUtils.hasText(item.getOpenDepartmentId()))
            .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserImportResultVO importUsers(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException("USER_IMPORT_FILE_EMPTY", "请选择人员导入文件");
        }
        List<Map<String, String>> rows = parseImportRows(file);
        UserDepartmentResolver.DepartmentReferences departmentReferences = userDepartmentResolver.loadReferences();
        UserImportResultVO result = new UserImportResultVO();
        int rowNo = 1;
        for (Map<String, String> row : rows) {
            rowNo++;
            if (row.values().stream().noneMatch(StringUtils::hasText)) {
                continue;
            }
            result.setTotalCount(result.getTotalCount() + 1);
            try {
                boolean created = importOneUser(row, departmentReferences);
                result.setSuccessCount(result.getSuccessCount() + 1);
                if (created) {
                    result.setCreatedCount(result.getCreatedCount() + 1);
                } else {
                    result.setUpdatedCount(result.getUpdatedCount() + 1);
                }
            } catch (RuntimeException ex) {
                result.setFailedCount(result.getFailedCount() + 1);
                if (result.getErrors().size() < 100) {
                    result.getErrors().add("第" + rowNo + "行：" + formatImportError(ex));
                }
            }
        }
        return result;
    }

    private String formatImportError(RuntimeException ex) {
        String duplicateKeyMessage = duplicateUserKeyMessage(ex);
        if (StringUtils.hasText(duplicateKeyMessage)) {
            return duplicateKeyMessage;
        }
        return StringUtils.hasText(ex.getMessage()) ? ex.getMessage() : ex.getClass().getSimpleName();
    }

    private String duplicateUserKeyMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (StringUtils.hasText(message)) {
                Matcher matcher = DUPLICATE_USER_KEY_PATTERN.matcher(message);
                if (matcher.find()) {
                    return duplicateUserKeyMessage(matcher.group(2), matcher.group(1));
                }
            }
            current = current.getCause();
        }
        return null;
    }

    private String duplicateUserKeyMessage(String field, String value) {
        return switch (field) {
            case "username" -> "用户名/账号 " + value + " 已存在，请检查是否重复导入或恢复已删除账号后更新";
            case "employee_no" -> "工号 " + value + " 已存在，请检查是否重复导入或恢复已删除账号后更新";
            case "mobile" -> "手机号 " + value + " 已存在，请检查是否重复导入或恢复已删除账号后更新";
            case "email" -> "邮箱 " + value + " 已存在，请检查是否重复导入或恢复已删除账号后更新";
            default -> "账号唯一信息 " + value + " 已存在，请检查是否重复导入";
        };
    }

    private boolean importOneUser(Map<String, String> row, UserDepartmentResolver.DepartmentReferences departmentReferences) {
        String realName = requiredImportValue(row, "姓名", "真实姓名", "realName");
        String employeeNo = blankToNull(limit(importValue(row, "工号", "员工编号", "employeeNo"), 64));
        String usernameValue = blankToNull(limit(importValue(row, "用户名", "账号", "username"), 64));
        String userType = normalizeUserType(firstText(importValue(row, "用户类型", "userType"), "ENGINEER"));
        Long supplierId = parseLong(importValue(row, "供应商ID", "supplierId"), null);
        Long regionId = parseLong(importValue(row, "区域ID", "regionId"), 1L);
        String deptCode = blankToNull(limit(importValue(row, "部门编号", "部门编码", "deptCode"), 64));
        String deptName = blankToNull(limit(importValue(row, "部门名称", "deptName"), 128));
        UserDepartmentResolver.DepartmentFields department = userDepartmentResolver.resolve(deptCode, deptName, departmentReferences);
        deptCode = department.deptCode();
        deptName = department.deptName();
        if (StringUtils.hasText(deptCode) != StringUtils.hasText(deptName)) {
            throw new BizException(
                "USER_IMPORT_DEPARTMENT_AMBIGUOUS",
                "部门信息无法唯一匹配，请同时填写完整部门名称和部门编码，或使用可唯一识别的部门信息"
            );
        }
        String mobile = blankToNull(limit(importValue(row, "手机号", "手机", "mobile"), 32));
        String email = blankToNull(limit(importValue(row, "邮箱", "email"), 128));
        String feishuUserId = blankToNull(limit(importValue(row, "飞书 user_id", "飞书UserID", "feishuUserId"), 128));
        String feishuOpenId = blankToNull(limit(importValue(row, "飞书 open_id", "飞书OpenID", "feishuOpenId"), 128));
        String feishuUnionId = blankToNull(limit(importValue(row, "飞书 union_id", "飞书UnionID", "feishuUnionId"), 128));
        Integer status = parseStatus(importValue(row, "状态", "status"));
        String remark = blankToNull(limit(importValue(row, "备注", "remark"), 500));
        String roleCodes = importValue(row, "角色编码", "角色", "roleCode", "roleCodes");
        String password = blankToNull(limit(importValue(row, "初始密码", "密码", "password"), 128));

        employeeNo = resolveEmployeeNo(userType, employeeNo);
        String username = resolveUsername(userType, usernameValue, employeeNo, supplierId, null);
        SysUser existing = findExistingImportUser(employeeNo, username, mobile, email);
        if (existing == null) {
            ensureCanCreateOrUpdateUser(userType, regionId, supplierId);
            checkCreateUnique(username, employeeNo, mobile, email);
            validateSupplierUser(userType, supplierId);
            SysUser user = new SysUser();
            applyUser(username, DEFAULT_INITIAL_PASSWORD, realName, employeeNo, userType, supplierId, regionId,
                deptCode, deptName, mobile, email, status, null, 1, remark, user);
            applyFeishuIds(user, feishuUserId, feishuOpenId, feishuUnionId);
            save(user);
            bindRoles(user.getId(), importRoleIds(roleCodes, userType, true));
            grantDefaultPlatformAccess(user.getId(), user.getUserType());
            return true;
        }

        ensureCanAccessTargetUser(existing);
        ensureCanCreateOrUpdateUser(userType, regionId, supplierId);
        checkUpdateUnique(username, employeeNo, mobile, email, existing.getId());
        validateSupplierUser(userType, supplierId);
        boolean restoringDeleted = isDeletedUser(existing);
        String updatePassword = restoringDeleted ? firstText(password, DEFAULT_INITIAL_PASSWORD) : password;
        Integer mustChangePassword = restoringDeleted ? 1 : existing.getMustChangePassword();
        if (!restoringDeleted && Objects.equals(existing.getStatus(), STATUS_DEPARTED)) {
            status = STATUS_DEPARTED;
        }
        applyUser(username, updatePassword, realName, employeeNo, userType, supplierId, regionId, deptCode, deptName, mobile, email,
            status, null, mustChangePassword, remark, existing);
        applyFeishuIds(existing, feishuUserId, feishuOpenId, feishuUnionId);
        if (restoringDeleted) {
            restoreDeletedUser(existing);
        } else {
            updateById(existing);
        }
        List<Long> roleIds = importRoleIds(roleCodes, userType, false);
        if (roleIds != null) {
            bindRoles(existing.getId(), roleIds);
        }
        grantDefaultPlatformAccessIfMissing(existing.getId(), userType);
        return false;
    }

    private List<Map<String, String>> parseImportRows(MultipartFile file) {
        List<List<String>> table = ExcelImportUtils.readFirstSheet(file);
        if (table.isEmpty()) {
            throw new BizException("USER_IMPORT_EMPTY", "导入文件没有内容");
        }
        List<String> headers = table.get(0).stream().map(this::normalizeImportHeader).toList();
        if (headers.stream().noneMatch(header -> List.of("姓名", "真实姓名", "realname").contains(header))) {
            throw new BizException("USER_IMPORT_HEADER_INVALID", "导入文件缺少姓名列");
        }
        return ExcelImportUtils.toHeaderMaps(table);
    }

    private String normalizeImportHeader(String value) {
        return ExcelImportUtils.normalizeHeader(value);
    }

    private String requiredImportValue(Map<String, String> row, String... aliases) {
        String value = importValue(row, aliases);
        if (!StringUtils.hasText(value)) {
            throw new BizException("USER_IMPORT_REQUIRED", aliases[0] + "不能为空");
        }
        return value.trim();
    }

    private String importValue(Map<String, String> row, String... aliases) {
        for (String alias : aliases) {
            String value = row.get(normalizeImportHeader(alias));
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private Long parseLong(String value, Long defaultValue) {
        if (!StringUtils.hasText(value)) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ex) {
            throw new BizException("USER_IMPORT_NUMBER_INVALID", value + "不是有效数字");
        }
    }

    private Integer parseStatus(String value) {
        if (!StringUtils.hasText(value)) {
            return 1;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if ("0".equals(normalized) || "停用".equals(normalized) || "禁用".equals(normalized)
            || "disabled".equals(normalized) || "inactive".equals(normalized)) {
            return 0;
        }
        return 1;
    }

    private SysUser findExistingImportUser(String employeeNo, String username, String mobile, String email) {
        if (StringUtils.hasText(employeeNo)) {
            SysUser user = baseMapper.selectAnyByEmployeeNo(employeeNo.trim());
            if (user != null) {
                return user;
            }
        }
        if (StringUtils.hasText(username)) {
            SysUser user = baseMapper.selectAnyByUsername(username.trim());
            if (user != null) {
                return user;
            }
        }
        for (String mobileCandidate : mobileCandidates(mobile)) {
            SysUser user = baseMapper.selectAnyByMobile(mobileCandidate);
            if (user != null) {
                return user;
            }
        }
        if (StringUtils.hasText(email)) {
            return baseMapper.selectAnyByEmail(email.trim());
        }
        return null;
    }

    private SysUser findDeletedUserForCreate(String employeeNo, String username, String mobile, String email) {
        if (StringUtils.hasText(employeeNo)) {
            SysUser user = baseMapper.selectDeletedByEmployeeNo(employeeNo.trim());
            if (user != null) {
                return user;
            }
        }
        if (StringUtils.hasText(username)) {
            SysUser user = baseMapper.selectDeletedByUsername(username.trim());
            if (user != null) {
                return user;
            }
        }
        for (String mobileCandidate : mobileCandidates(mobile)) {
            SysUser user = baseMapper.selectDeletedByMobile(mobileCandidate);
            if (user != null) {
                return user;
            }
        }
        if (StringUtils.hasText(email)) {
            return baseMapper.selectDeletedByEmail(email.trim());
        }
        return null;
    }

    private void clearOccupiedEmailForCreate(String email, Long targetUserId, Boolean confirmed) {
        if (!StringUtils.hasText(email)) {
            return;
        }
        SysUser occupiedUser = baseMapper.selectAnyByEmail(email.trim());
        if (occupiedUser == null || Objects.equals(occupiedUser.getId(), targetUserId)) {
            return;
        }
        ensureCanAccessTargetUser(occupiedUser);
        if (!Boolean.TRUE.equals(confirmed)) {
            throw new BizException(
                "USER_EMAIL_CLEAR_CONFIRM_REQUIRED",
                "邮箱 " + email.trim() + " 已被账号 " + describeUserForConfirm(occupiedUser)
                    + " 占用。请人工确认是否清空该账号邮箱后继续新增。"
            );
        }
        int updated = baseMapper.clearEmailById(occupiedUser.getId(), currentOperatorId());
        if (updated <= 0) {
            throw new BizException("USER_EMAIL_CLEAR_FAILED", "清空占用邮箱失败，请刷新后重试");
        }
    }

    private String describeUserForConfirm(SysUser user) {
        String name = firstText(user.getRealName(), user.getUsername(), "未命名账号");
        String employeeNo = firstText(user.getEmployeeNo(), "-");
        String deleted = isDeletedUser(user) ? "已删除" : "未删除";
        String status = Objects.equals(user.getStatus(), 1)
            ? "启用"
            : Objects.equals(user.getStatus(), STATUS_DEPARTED) ? "离职" : "停用";
        return "ID " + user.getId() + "（" + name + " / 工号 " + employeeNo + " / " + status + " / " + deleted + "）";
    }

    private List<Long> importRoleIds(String roleCodes, String userType, boolean createDefault) {
        if (!StringUtils.hasText(roleCodes)) {
            return createDefault ? normalizeRoleIds(userType, new ArrayList<>()) : null;
        }
        List<Long> roleIds = new ArrayList<>();
        for (String token : roleCodes.split("[,，;；/\\s]+")) {
            if (!StringUtils.hasText(token)) {
                continue;
            }
            SysRole role = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .and(wrapper -> wrapper.eq(SysRole::getRoleCode, token.trim())
                    .or()
                    .eq(SysRole::getRoleName, token.trim()))
                .last("LIMIT 1"));
            if (role == null) {
                throw new BizException("USER_IMPORT_ROLE_NOT_FOUND", "角色不存在：" + token);
            }
            roleIds.add(role.getId());
        }
        return normalizeRoleIds(userType, roleIds);
    }

    private void syncOneFeishuUser(FeishuDirectoryUser user, String defaultUserType, Long defaultRegionId,
                                   boolean createMissing, boolean updateExisting, FeishuUserSyncResultVO result) {
        SysUser existing = findExistingFeishuUser(user);
        try {
            if (existing != null) {
                if (!updateExisting) {
                    result.setSkippedCount(result.getSkippedCount() + 1);
                    return;
                }
                updateFromFeishu(existing, user);
                result.setUpdatedCount(result.getUpdatedCount() + 1);
                return;
            }
            if (!createMissing) {
                result.setSkippedCount(result.getSkippedCount() + 1);
                return;
            }
            createFromFeishu(user, defaultUserType, defaultRegionId);
            result.setCreatedCount(result.getCreatedCount() + 1);
        } catch (RuntimeException ex) {
            result.setSkippedCount(result.getSkippedCount() + 1);
            appendWarning(result, "同步用户 " + user.displayName() + " 失败：" + ex.getMessage());
        }
    }

    private void createFromFeishu(FeishuDirectoryUser user, String defaultUserType, Long defaultRegionId) {
        ensureCanCreateOrUpdateUser(defaultUserType, defaultRegionId, null);
        String employeeNo = requiredEmployeeNo(user.employeeNo());
        String username = resolveUsername(null, employeeNo);
        String mobile = blankToNull(limit(user.mobile(), 32));
        String email = blankToNull(limit(user.email(), 128));
        checkCreateUnique(username, employeeNo, mobile, email);

        SysUser newUser = new SysUser();
        applyUser(username, DEFAULT_INITIAL_PASSWORD, limit(firstText(user.name(), username), 64), employeeNo, defaultUserType, null, defaultRegionId,
            limit(user.departmentId(), 64), limit(user.departmentPath(), 128), mobile, email, user.active() ? 1 : 0,
            null, 1, buildFeishuRemark(null, user), newUser);
        applyFeishuIds(newUser, user.userId(), user.openId(), user.unionId());
        save(newUser);
        bindRoles(newUser.getId(), normalizeRoleIds(defaultUserType, new ArrayList<>()));
    }

    private void updateFromFeishu(SysUser existing, FeishuDirectoryUser user) {
        ensureCanAccessTargetUser(existing);
        existing.setRealName(limit(firstText(user.name(), existing.getRealName(), existing.getUsername()), 64));
        existing.setEmployeeNo(blankToNull(limit(resolveAvailableUniqueValue(SysUser::getEmployeeNo, user.employeeNo(), existing.getId(), existing.getEmployeeNo()), 64)));
        if (StringUtils.hasText(existing.getEmployeeNo())) {
            if (existsUsername(existing.getEmployeeNo(), existing.getId())) {
                throw new BizException("USERNAME_EXISTS", "Username already exists");
            }
            existing.setUsername(existing.getEmployeeNo());
        }
        existing.setDeptCode(limit(user.departmentId(), 64));
        existing.setDeptName(limit(user.departmentPath(), 128));
        existing.setMobile(blankToNull(limit(resolveAvailableUniqueValue(SysUser::getMobile, user.mobile(), existing.getId(), existing.getMobile()), 32)));
        existing.setEmail(blankToNull(limit(resolveAvailableUniqueValue(SysUser::getEmail, user.email(), existing.getId(), existing.getEmail()), 128)));
        applyFeishuIds(existing, user.userId(), user.openId(), user.unionId());
        existing.setRemark(buildFeishuRemark(existing.getRemark(), user));
        updateById(existing);
    }

    private SysUser findExistingFeishuUser(FeishuDirectoryUser user) {
        for (String email : identityCandidates(user.email())) {
            SysUser existing = findUserByEmail(email);
            if (existing != null) {
                return existing;
            }
        }
        for (String mobile : mobileCandidates(user.mobile())) {
            SysUser existing = findUserByMobile(mobile);
            if (existing != null) {
                return existing;
            }
        }
        for (String employeeNo : identityCandidates(user.employeeNo())) {
            SysUser existing = findUserByEmployeeNo(employeeNo);
            if (existing != null) {
                return existing;
            }
        }
        for (String userId : identityCandidates(user.userId())) {
            SysUser existing = findUserByFeishuUserId(userId);
            if (existing != null) {
                return existing;
            }
        }
        for (String openId : identityCandidates(user.openId())) {
            SysUser existing = findUserByFeishuOpenId(openId);
            if (existing != null) {
                return existing;
            }
        }
        for (String unionId : identityCandidates(user.unionId())) {
            SysUser existing = findUserByFeishuUnionId(unionId);
            if (existing != null) {
                return existing;
            }
        }
        for (String username : identityCandidates(user.userId(), user.openId(), user.unionId())) {
            SysUser existing = findUserByUsername(username);
            if (existing != null) {
                return existing;
            }
        }
        return null;
    }

    private List<FeishuDepartment> fetchFeishuDepartments(List<String> rootDepartmentIds, boolean recursive, int pageSize) {
        List<FeishuDepartment> departments = new ArrayList<>();
        Deque<FeishuDepartment> queue = new ArrayDeque<>();
        Set<String> seenDepartmentIds = new LinkedHashSet<>();
        for (String rootDepartmentId : rootDepartmentIds) {
            if (!StringUtils.hasText(rootDepartmentId) || !seenDepartmentIds.add(rootDepartmentId)) {
                continue;
            }
            FeishuDepartment root = new FeishuDepartment(rootDepartmentId, "飞书组织", "飞书组织");
            departments.add(root);
            queue.add(root);
        }
        while (!queue.isEmpty()) {
            FeishuDepartment parent = queue.removeFirst();
            List<FeishuDepartment> children = fetchFeishuDepartmentChildren(parent, pageSize);
            children = children.stream()
                .filter(child -> StringUtils.hasText(child.id()) && seenDepartmentIds.add(child.id()))
                .toList();
            departments.addAll(children);
            if (recursive) {
                queue.addAll(children);
            }
        }
        return departments;
    }

    private List<FeishuDepartment> fetchFeishuDepartmentChildren(FeishuDepartment parent, int pageSize) {
        List<FeishuDepartment> departments = new ArrayList<>();
        String pageToken = "";
        boolean hasMore;
        do {
            UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(departmentChildrenUrl(parent.id()))
                .queryParam("department_id_type", resolveFeishuDepartmentIdType())
                .queryParam("page_size", pageSize);
            if (StringUtils.hasText(pageToken)) {
                builder.queryParam("page_token", pageToken);
            }
            Map<String, Object> response = feishuGet(builder, "FEISHU_DEPARTMENT_SYNC_FAILED", "获取飞书部门列表失败");
            Map<String, Object> data = unwrapFeishuData(response, "FEISHU_DEPARTMENT_SYNC_FAILED", "获取飞书部门列表失败");
            for (Map<String, Object> item : objectList(data.get("items"))) {
                String departmentId = departmentIdForConfiguredType(item);
                if (!StringUtils.hasText(departmentId)) {
                    continue;
                }
                String name = limit(firstText(text(item.get("name")), departmentId), 64);
                String path = "飞书组织".equals(parent.path()) ? name : parent.path() + "/" + name;
                departments.add(new FeishuDepartment(departmentId, name, path));
            }
            hasMore = booleanValue(data.get("has_more"));
            pageToken = text(data.get("page_token"));
        } while (hasMore && StringUtils.hasText(pageToken));
        return departments;
    }

    private List<FeishuDirectoryUser> fetchFeishuUsers(FeishuDepartment department, int pageSize) {
        List<FeishuDirectoryUser> users = new ArrayList<>();
        String pageToken = "";
        boolean hasMore;
        do {
            UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(feishuProperties.getDepartmentUsersUrl())
                .queryParam("department_id", department.id())
                .queryParam("department_id_type", resolveFeishuDepartmentIdType())
                .queryParam("user_id_type", resolveFeishuUserIdType())
                .queryParam("page_size", pageSize);
            if (StringUtils.hasText(pageToken)) {
                builder.queryParam("page_token", pageToken);
            }
            Map<String, Object> response = feishuGet(builder, "FEISHU_USER_SYNC_FAILED", "获取飞书部门用户失败");
            Map<String, Object> data = unwrapFeishuData(response, "FEISHU_USER_SYNC_FAILED", "获取飞书部门用户失败");
            for (Map<String, Object> item : objectList(data.get("items"))) {
                FeishuDirectoryUser user = toFeishuDirectoryUser(item, department);
                if (StringUtils.hasText(user.dedupeKey())) {
                    users.add(user);
                }
            }
            hasMore = booleanValue(data.get("has_more"));
            pageToken = text(data.get("page_token"));
        } while (hasMore && StringUtils.hasText(pageToken));
        return users;
    }

    private String departmentIdForConfiguredType(Map<String, Object> item) {
        String idType = resolveFeishuDepartmentIdType();
        if ("open_department_id".equals(idType)) {
            return firstText(text(item.get("open_department_id")), text(item.get("department_id")));
        }
        return firstText(text(item.get("department_id")), text(item.get("open_department_id")));
    }

    private FeishuDepartmentSearchVO toFeishuDepartmentSearchVO(Map<String, Object> item) {
        FeishuDepartmentSearchVO vo = new FeishuDepartmentSearchVO();
        vo.setName(limit(firstText(text(item.get("name")), text(item.get("i18n_name")), text(item.get("department_id")), text(item.get("open_department_id"))), 128));
        vo.setDepartmentId(text(item.get("department_id")));
        vo.setOpenDepartmentId(text(item.get("open_department_id")));
        vo.setParentDepartmentId(firstText(text(item.get("parent_department_id")), text(item.get("open_parent_department_id"))));
        vo.setPath(limit(firstText(text(item.get("department_path")), text(item.get("path")), text(item.get("name"))), 500));
        return vo;
    }

    private FeishuDirectoryUser toFeishuDirectoryUser(Map<String, Object> item, FeishuDepartment department) {
        String email = firstText(text(item.get("enterprise_email")), text(item.get("email")));
        String mobile = normalizeMobile(text(item.get("mobile")));
        String userId = firstText(text(item.get("user_id")), text(item.get("id")));
        String openId = text(item.get("open_id"));
        String unionId = text(item.get("union_id"));
        String employeeNo = text(item.get("employee_no"));
        String name = firstText(text(item.get("name")), text(item.get("nickname")), text(item.get("en_name")), userId, openId, email);
        return new FeishuDirectoryUser(
            userId,
            openId,
            unionId,
            employeeNo,
            name,
            email,
            mobile,
            department.id(),
            department.path(),
            parseFeishuUserActive(item.get("status"))
        );
    }

    private String resolveTenantAccessToken() {
        long now = System.currentTimeMillis();
        String appId = resolveFeishuAppId();
        String appSecret = resolveFeishuAppSecret();
        String tokenSignature = appId + "\n" + appSecret + "\n" + feishuProperties.getTenantAccessTokenUrl();
        if (StringUtils.hasText(cachedTenantAccessToken)
            && cachedTenantAccessTokenExpiresAt > now
            && Objects.equals(cachedTenantAccessTokenSignature, tokenSignature)) {
            return cachedTenantAccessToken;
        }
        synchronized (this) {
            now = System.currentTimeMillis();
            if (StringUtils.hasText(cachedTenantAccessToken)
                && cachedTenantAccessTokenExpiresAt > now
                && Objects.equals(cachedTenantAccessTokenSignature, tokenSignature)) {
                return cachedTenantAccessToken;
            }
            Map<String, Object> requestBody = Map.of(
                "app_id", appId,
                "app_secret", appSecret
            );
            Map<String, Object> response = feishuPost(feishuProperties.getTenantAccessTokenUrl(), requestBody,
                "FEISHU_TENANT_TOKEN_FAILED", "获取飞书租户访问凭证失败");
            Map<String, Object> data = unwrapFeishuData(response, "FEISHU_TENANT_TOKEN_FAILED", "获取飞书租户访问凭证失败");
            String token = text(data.get("tenant_access_token"));
            if (!StringUtils.hasText(token)) {
                throw new BizException("FEISHU_TENANT_TOKEN_FAILED", "飞书响应缺少 tenant_access_token");
            }
            long expireSeconds = numberValue(data.get("expire"), 7200L);
            cachedTenantAccessToken = token;
            cachedTenantAccessTokenExpiresAt = now + Math.max(60L, expireSeconds - 60L) * 1000L;
            cachedTenantAccessTokenSignature = tokenSignature;
            return cachedTenantAccessToken;
        }
    }

    private Map<String, Object> feishuGet(UriComponentsBuilder builder, String code, String defaultMessage) {
        try {
            return restClient.get()
                .uri(builder.build(true).toUri())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + resolveTenantAccessToken())
                .retrieve()
                .body(MAP_TYPE);
        } catch (RestClientResponseException ex) {
            throw toFeishuBizException(ex, code, defaultMessage);
        }
    }

    private Map<String, Object> feishuPost(String url, Map<String, Object> body, String code, String defaultMessage) {
        try {
            return restClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(MAP_TYPE);
        } catch (RestClientResponseException ex) {
            throw toFeishuBizException(ex, code, defaultMessage);
        }
    }

    private BizException toFeishuBizException(RestClientResponseException ex, String code, String defaultMessage) {
        String body = ex.getResponseBodyAsString();
        if (isFeishuContactAuthorityError(body)) {
            return new BizException("FEISHU_CONTACT_PERMISSION_MISSING", feishuContactAuthorityMessage());
        }
        String message = StringUtils.hasText(body) ? limit(body, 300) : defaultMessage;
        return new BizException(code, message);
    }

    private Map<String, Object> unwrapFeishuData(Map<String, Object> response, String code, String defaultMessage) {
        if (response == null) {
            throw new BizException(code, defaultMessage);
        }
        String responseCode = text(response.get("code"));
        if (StringUtils.hasText(responseCode) && !"0".equals(responseCode)) {
            String msg = firstText(text(response.get("msg")), text(response.get("message")), defaultMessage);
            if (isFeishuContactAuthorityError(responseCode + " " + msg)) {
                throw new BizException("FEISHU_CONTACT_PERMISSION_MISSING", feishuContactAuthorityMessage());
            }
            throw new BizException(code, msg);
        }
        Object data = response.get("data");
        if (data instanceof Map<?, ?> map) {
            return copyMap(map);
        }
        return response;
    }

    private Map<String, Object> copyMap(Map<?, ?> source) {
        Map<String, Object> target = new HashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (entry.getKey() != null) {
                target.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return target;
    }

    private void assertFeishuConfigured() {
        if (!feishuProperties.isEnabled()
            || !StringUtils.hasText(resolveFeishuAppId())
            || !StringUtils.hasText(resolveFeishuAppSecret())) {
            throw new BizException("FEISHU_NOT_CONFIGURED", "飞书应用参数未配置或未启用");
        }
    }

    private boolean isFeishuContactAuthorityError(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        return value.contains("40004")
            || value.contains("99991672")
            || value.contains("99991679")
            || value.contains("no dept authority")
            || value.contains("Access denied")
            || value.contains("contact:department.base:readonly")
            || value.contains("contact:user.basic_profile:readonly")
            || value.contains("contact:user.department:readonly")
            || value.contains("contact:user.employee_id:readonly")
            || value.contains("contact:user.employee_number:read")
            || value.contains("contact:department.organize:readonly")
            || value.contains("contact:contact:readonly_as_app")
            || value.contains("获取部门基础信息")
            || value.contains("获取用户的基本信息")
            || value.contains("获取用户组织架构信息");
    }

    private String feishuContactAuthorityMessage() {
        return "飞书应用没有目标部门的通讯录权限。请在飞书开放平台给应用开通并发布通讯录权限："
            + "获取部门基础信息、获取用户的基本信息、获取用户组织架构信息、获取用户 user ID、查看成员工号；"
            + "如需按邮箱或手机号匹配账号，再开通获取用户邮箱信息、获取用户手机号或通过手机号或邮箱获取用户 ID。"
            + "同时确保应用可用范围/通讯录授权范围包含要同步的部门；如果只授权了部分部门，"
            + "请在平台集成设置里把“飞书同步根部门 ID 列表”配成已授权的部门 ID，并确认“飞书部门 ID 类型”与这些 ID 一致；"
            + "也可继续使用服务器环境变量 FEISHU_ROOT_DEPARTMENT_ID 和 FEISHU_DEPARTMENT_ID_TYPE。";
    }

    private List<String> resolveRootDepartmentIds(String requestValue) {
        String value = StringUtils.hasText(requestValue)
            ? requestValue.trim()
            : settingText(KEY_FEISHU_ROOT_DEPARTMENT_ID, feishuProperties.getRootDepartmentId(), "0");
        Set<String> ids = new LinkedHashSet<>();
        for (String item : value.split("[,，;；\\s]+")) {
            String id = item.trim();
            if (StringUtils.hasText(id)) {
                ids.add(id);
            }
        }
        if (ids.isEmpty()) {
            ids.add("0");
        }
        return new ArrayList<>(ids);
    }

    private String resolveFeishuAppId() {
        return settingText(KEY_FEISHU_APP_ID, feishuProperties.getAppId(), "");
    }

    private String resolveFeishuAppSecret() {
        return settingText(KEY_FEISHU_APP_SECRET, feishuProperties.getAppSecret(), "");
    }

    private String settingText(String key, String fallback, String defaultValue) {
        ProcurementSetting setting = procurementSettingMapper.selectOne(new LambdaQueryWrapper<ProcurementSetting>()
            .eq(ProcurementSetting::getSettingKey, key)
            .eq(ProcurementSetting::getIsDeleted, 0)
            .last("LIMIT 1"));
        if (setting != null && StringUtils.hasText(setting.getSettingValue())) {
            return setting.getSettingValue().trim();
        }
        if (StringUtils.hasText(fallback)) {
            return fallback.trim();
        }
        return defaultValue;
    }

    private String resolveFeishuDepartmentIdType() {
        String idType = settingText(KEY_FEISHU_DEPARTMENT_ID_TYPE, feishuProperties.getDepartmentIdType(), "department_id");
        return StringUtils.hasText(idType) ? idType.trim() : "department_id";
    }

    private String resolveFeishuUserIdType() {
        String idType = feishuProperties.getUserIdType();
        return StringUtils.hasText(idType) ? idType.trim() : "user_id";
    }

    private String departmentChildrenUrl(String departmentId) {
        String url = feishuProperties.getDepartmentChildrenUrl();
        if (url.contains("%s")) {
            return String.format(Locale.ROOT, url, departmentId);
        }
        if (url.contains("{departmentId}")) {
            return url.replace("{departmentId}", departmentId);
        }
        return url.endsWith("/") ? url + departmentId + "/children" : url + "/" + departmentId + "/children";
    }

    private boolean parseFeishuUserActive(Object statusValue) {
        if (!(statusValue instanceof Map<?, ?> status)) {
            return true;
        }
        return !booleanValue(status.get("is_frozen"))
            && !booleanValue(status.get("is_resigned"))
            && !booleanValue(status.get("is_exited"))
            && !Boolean.FALSE.equals(booleanObject(status.get("is_activated")));
    }

    private List<Map<String, Object>> objectList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                result.add(copyMap(map));
            }
        }
        return result;
    }

    private Boolean booleanObject(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        String textValue = text(value);
        if (!StringUtils.hasText(textValue)) {
            return null;
        }
        if ("true".equalsIgnoreCase(textValue) || "1".equals(textValue)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(textValue) || "0".equals(textValue)) {
            return Boolean.FALSE;
        }
        return null;
    }

    private boolean booleanValue(Object value) {
        Boolean bool = booleanObject(value);
        return bool != null && bool;
    }

    private long numberValue(Object value, long defaultValue) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        String textValue = text(value);
        if (!StringUtils.hasText(textValue)) {
            return defaultValue;
        }
        try {
            return Long.parseLong(textValue);
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String firstText(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private String limit(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private List<String> identityCandidates(String... values) {
        List<String> candidates = new ArrayList<>();
        if (values == null) {
            return candidates;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                String trimmed = value.trim();
                if (!candidates.contains(trimmed)) {
                    candidates.add(trimmed);
                }
            }
        }
        return candidates;
    }

    private String normalizeMobile(String mobile) {
        if (!StringUtils.hasText(mobile)) {
            return "";
        }
        return mobile.replace(" ", "").replace("-", "").trim();
    }

    private List<String> mobileCandidates(String mobile) {
        List<String> candidates = new ArrayList<>();
        String normalized = normalizeMobile(mobile);
        if (!StringUtils.hasText(normalized)) {
            return candidates;
        }
        candidates.add(normalized);
        if (normalized.startsWith("+86") && normalized.length() > 3) {
            candidates.add(normalized.substring(3));
        }
        if (normalized.startsWith("86") && normalized.length() == 13) {
            candidates.add(normalized.substring(2));
        }
        return candidates.stream().distinct().toList();
    }

    private SysUser findUserByEmail(String email) {
        return baseMapper.selectOne(new LambdaQueryWrapper<SysUser>()
            .eq(SysUser::getEmail, email.trim())
            .last("LIMIT 1"));
    }

    private SysUser findUserByMobile(String mobile) {
        return baseMapper.selectOne(new LambdaQueryWrapper<SysUser>()
            .eq(SysUser::getMobile, mobile.trim())
            .last("LIMIT 1"));
    }

    private SysUser findUserByEmployeeNo(String employeeNo) {
        return baseMapper.selectOne(new LambdaQueryWrapper<SysUser>()
            .eq(SysUser::getEmployeeNo, employeeNo.trim())
            .last("LIMIT 1"));
    }

    private SysUser findUserByUsername(String username) {
        return baseMapper.selectOne(new LambdaQueryWrapper<SysUser>()
            .eq(SysUser::getUsername, username.trim())
            .last("LIMIT 1"));
    }

    private SysUser findUserByFeishuUserId(String userId) {
        return baseMapper.selectOne(new LambdaQueryWrapper<SysUser>()
            .eq(SysUser::getFeishuUserId, userId.trim())
            .last("LIMIT 1"));
    }

    private SysUser findUserByFeishuOpenId(String openId) {
        return baseMapper.selectOne(new LambdaQueryWrapper<SysUser>()
            .eq(SysUser::getFeishuOpenId, openId.trim())
            .last("LIMIT 1"));
    }

    private SysUser findUserByFeishuUnionId(String unionId) {
        return baseMapper.selectOne(new LambdaQueryWrapper<SysUser>()
            .eq(SysUser::getFeishuUnionId, unionId.trim())
            .last("LIMIT 1"));
    }

    private String resolveAvailableUniqueValue(SFunction<SysUser, ?> column, String candidate, Long currentUserId, String fallback) {
        if (!StringUtils.hasText(candidate)) {
            return fallback;
        }
        Long count = baseMapper.selectCount(new LambdaQueryWrapper<SysUser>()
            .eq(column, candidate.trim())
            .ne(SysUser::getId, currentUserId));
        return count == null || count == 0 ? candidate.trim() : fallback;
    }

    private String buildFeishuRemark(String existingRemark, FeishuDirectoryUser user) {
        String feishuRemark = "飞书同步"
            + "; user_id=" + firstText(user.userId(), "-")
            + "; open_id=" + firstText(user.openId(), "-")
            + "; union_id=" + firstText(user.unionId(), "-")
            + "; employee_no=" + firstText(user.employeeNo(), "-")
            + "; synced_at=" + LocalDateTime.now();
        if (!StringUtils.hasText(existingRemark) || existingRemark.startsWith("飞书同步")) {
            return limit(feishuRemark, 500);
        }
        return limit(existingRemark + "\n" + feishuRemark, 500);
    }

    private void appendWarning(FeishuUserSyncResultVO result, String warning) {
        if (result.getWarnings().size() < 30) {
            result.getWarnings().add(warning);
        }
    }

    private String resolveDefaultFeishuUserType(String requestedUserType) {
        String normalized = normalizeUserType(requestedUserType);
        if (roleExists(normalized)) {
            return normalized;
        }
        for (String fallback : List.of("ENGINEER", "PURCHASER", "MANAGER")) {
            if (roleExists(fallback)) {
                return fallback;
            }
        }
        return StringUtils.hasText(normalized) ? normalized : "ENGINEER";
    }

    private boolean roleExists(String roleCode) {
        if (!StringUtils.hasText(roleCode)) {
            return false;
        }
        return roleMapper.selectCount(new LambdaQueryWrapper<SysRole>().eq(SysRole::getRoleCode, roleCode)) > 0;
    }

    private record FeishuDepartment(String id, String name, String path) {
    }

    private record FeishuDirectoryUser(
        String userId,
        String openId,
        String unionId,
        String employeeNo,
        String name,
        String email,
        String mobile,
        String departmentId,
        String departmentPath,
        boolean active
    ) {
        private String dedupeKey() {
            return firstNonBlank(userId, openId, unionId, email, mobile, name + "@" + departmentId);
        }

        private String displayName() {
            return firstNonBlank(name, email, mobile, userId, openId);
        }

        private static String firstNonBlank(String... values) {
            if (values == null) {
                return "";
            }
            for (String value : values) {
                if (StringUtils.hasText(value)) {
                    return value.trim();
                }
            }
            return "";
        }
    }

    private LambdaQueryWrapper<SysUser> buildQuery(UserPageQueryDTO queryDTO) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(queryDTO.getUsername())) {
            wrapper.and(child -> child.like(SysUser::getUsername, queryDTO.getUsername())
                .or()
                .like(SysUser::getEmployeeNo, queryDTO.getUsername()));
        }
        if (StringUtils.hasText(queryDTO.getRealName())) {
            wrapper.like(SysUser::getRealName, queryDTO.getRealName());
        }
        if (StringUtils.hasText(queryDTO.getEmployeeNo())) {
            wrapper.like(SysUser::getEmployeeNo, queryDTO.getEmployeeNo());
        }
        if ("SUPPLIER".equalsIgnoreCase(queryDTO.getAccountCategory())) {
            wrapper.eq(SysUser::getUserType, DataScopeHelper.USER_TYPE_SUPPLIER);
        } else if ("EMPLOYEE".equalsIgnoreCase(queryDTO.getAccountCategory())) {
            wrapper.ne(SysUser::getUserType, DataScopeHelper.USER_TYPE_SUPPLIER);
        }
        if (StringUtils.hasText(queryDTO.getUserType())) {
            wrapper.eq(SysUser::getUserType, queryDTO.getUserType());
        }
        if (queryDTO.getRegionId() != null) {
            wrapper.eq(SysUser::getRegionId, queryDTO.getRegionId());
        }
        if (queryDTO.getSupplierId() != null) {
            wrapper.eq(SysUser::getSupplierId, queryDTO.getSupplierId());
        }
        if (queryDTO.getStatus() != null) {
            wrapper.eq(SysUser::getStatus, queryDTO.getStatus());
        }
        applyDataScope(wrapper);
        wrapper.orderByDesc(SysUser::getCreatedAt);
        return wrapper;
    }

    private void applyDataScope(LambdaQueryWrapper<SysUser> wrapper) {
        AuthUser authUser = AuthContextHolder.get();
        if (authUser == null || DataScopeHelper.isSuperAdmin(authUser)) {
            return;
        }
        wrapper.ne(SysUser::getUserType, DataScopeHelper.SUPER_ADMIN_ROLE_CODE);
        if (DataScopeHelper.isSupplierUser(authUser)) {
            wrapper.eq(SysUser::getSupplierId, authUser.getSupplierId());
            wrapper.eq(SysUser::getUserType, DataScopeHelper.USER_TYPE_SUPPLIER);
            return;
        }
        String scope = DataScopeHelper.resolveDataScope(authUser);
        if (DataScopeHelper.SCOPE_REGION.equals(scope)) {
            wrapper.eq(SysUser::getRegionId, authUser.getRegionId());
        } else if (DataScopeHelper.SCOPE_SELF.equals(scope)) {
            wrapper.eq(SysUser::getId, authUser.getUserId());
        }
    }

    private UserVO toUserVO(SysUser user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        vo.setEmployeeNo(user.getEmployeeNo());
        vo.setUserType(user.getUserType());
        vo.setSupplierId(user.getSupplierId());
        vo.setRegionId(user.getRegionId());
        vo.setDeptCode(user.getDeptCode());
        vo.setDeptName(user.getDeptName());
        vo.setMobile(user.getMobile());
        vo.setEmail(user.getEmail());
        vo.setFeishuUserId(user.getFeishuUserId());
        vo.setFeishuOpenId(user.getFeishuOpenId());
        vo.setFeishuUnionId(user.getFeishuUnionId());
        vo.setStatus(user.getStatus());
        vo.setDataScopeType(user.getDataScopeType());
        vo.setMustChangePassword(user.getMustChangePassword());
        vo.setPasswordChangedAt(user.getPasswordChangedAt());
        vo.setRemark(user.getRemark());
        vo.setLastLoginAt(user.getLastLoginAt());
        vo.setLastLoginIp(user.getLastLoginIp());
        vo.setCreatedAt(user.getCreatedAt());
        vo.setUpdatedAt(user.getUpdatedAt());
        List<SysUserRole> userRoles = userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, user.getId()));
        vo.setRoleIds(userRoles.stream().map(SysUserRole::getRoleId).toList());
        if (user.getSupplierId() != null) {
            Supplier supplier = supplierMapper.selectById(user.getSupplierId());
            if (supplier != null) {
                vo.setSupplierCode(supplier.getSupplierCode());
                vo.setSupplierName(supplier.getSupplierName());
            }
        }
        return vo;
    }

    private void applyResolvedDepartment(SysUser user) {
        UserDepartmentResolver.DepartmentFields department = userDepartmentResolver.resolve(user.getDeptCode(), user.getDeptName());
        user.setDeptCode(department.deptCode());
        user.setDeptName(department.deptName());
    }

    private void checkCreateUnique(String username, String employeeNo, String mobile, String email) {
        checkCreateUniqueExceptEmail(username, employeeNo, mobile);
        if (StringUtils.hasText(email) && baseMapper.countAnyByEmailExcept(email, null) > 0) {
            throw new BizException("EMAIL_EXISTS", "邮箱已存在，请检查是否重复导入或恢复已删除账号后更新");
        }
    }

    private void checkCreateUniqueExceptEmail(String username, String employeeNo, String mobile) {
        if (baseMapper.countAnyByUsernameExcept(username, null) > 0) {
            throw new BizException("USERNAME_EXISTS", "用户名/账号已存在，请检查是否重复导入或恢复已删除账号后更新");
        }
        if (StringUtils.hasText(employeeNo) && baseMapper.countAnyByEmployeeNoExcept(employeeNo, null) > 0) {
            throw new BizException("EMPLOYEE_NO_EXISTS", "工号已存在，请检查是否重复导入或恢复已删除账号后更新");
        }
        if (StringUtils.hasText(mobile) && baseMapper.countAnyByMobileExcept(mobile, null) > 0) {
            throw new BizException("MOBILE_EXISTS", "手机号已存在，请检查是否重复导入或恢复已删除账号后更新");
        }
    }

    private void checkUpdateUnique(String username, String employeeNo, String mobile, String email, Long excludeId) {
        checkUpdateUniqueExceptEmail(username, employeeNo, mobile, excludeId);
        if (StringUtils.hasText(email) && baseMapper.countAnyByEmailExcept(email, excludeId) > 0) {
            throw new BizException("EMAIL_EXISTS", "邮箱已被其他账号占用，请检查是否重复导入");
        }
    }

    private void checkUpdateUniqueExceptEmail(String username, String employeeNo, String mobile, Long excludeId) {
        if (baseMapper.countAnyByUsernameExcept(username, excludeId) > 0) {
            throw new BizException("USERNAME_EXISTS", "用户名/账号已被其他账号占用，请检查是否重复导入");
        }
        if (StringUtils.hasText(employeeNo) && baseMapper.countAnyByEmployeeNoExcept(employeeNo, excludeId) > 0) {
            throw new BizException("EMPLOYEE_NO_EXISTS", "工号已被其他账号占用，请检查是否重复导入");
        }
        if (StringUtils.hasText(mobile) && baseMapper.countAnyByMobileExcept(mobile, excludeId) > 0) {
            throw new BizException("MOBILE_EXISTS", "手机号已被其他账号占用，请检查是否重复导入");
        }
    }

    private void validateSupplierUser(String userType, Long supplierId) {
        if (DataScopeHelper.USER_TYPE_SUPPLIER.equalsIgnoreCase(userType) && supplierId == null) {
            throw new BizException("SUPPLIER_ID_REQUIRED", "供应商账号必须绑定供应商");
        }
        if (!DataScopeHelper.USER_TYPE_SUPPLIER.equalsIgnoreCase(userType) && supplierId != null) {
            throw new BizException("SUPPLIER_BIND_FORBIDDEN", "只有供应商账号可以绑定供应商档案");
        }
        if (supplierId != null && supplierMapper.selectById(supplierId) == null) {
            throw new BizException("SUPPLIER_NOT_FOUND", "供应商不存在");
        }
    }

    private void applyUser(String username, String password, String realName, String employeeNo, String userType, Long supplierId, Long regionId,
                           String deptCode, String deptName, String mobile, String email, Integer status, String dataScopeType,
                           Integer mustChangePassword, String remark, SysUser user) {
        String normalizedUserType = normalizeUserType(userType);
        user.setUsername(username);
        if (StringUtils.hasText(password)) {
            user.setPasswordHash(PasswordCrypto.encode(password));
            user.setPasswordChangedAt(LocalDateTime.now());
        }
        user.setRealName(realName);
        user.setEmployeeNo(employeeNo);
        user.setUserType(normalizedUserType);
        user.setSupplierId(DataScopeHelper.USER_TYPE_SUPPLIER.equals(normalizedUserType) ? supplierId : null);
        user.setRegionId(regionId);
        user.setDeptCode(deptCode);
        user.setDeptName(deptName);
        user.setMobile(mobile);
        user.setEmail(email);
        user.setStatus(status);
        user.setDataScopeType(resolveDataScopeType(normalizedUserType, dataScopeType));
        user.setMustChangePassword(mustChangePassword == null ? 0 : mustChangePassword);
        user.setRemark(remark);
    }

    private void assertDedicatedOffboardingTransition(Integer currentStatus, Integer requestedStatus) {
        if (Objects.equals(requestedStatus, STATUS_DEPARTED)
            && !Objects.equals(currentStatus, STATUS_DEPARTED)) {
            throw new BizException(
                "USER_OFFBOARDING_REQUIRED",
                "员工离职必须通过办理离职功能检查并交接审批节点"
            );
        }
    }

    private void applyFeishuIds(SysUser user, String userId, String openId, String unionId) {
        user.setFeishuUserId(blankToNull(limit(userId, 128)));
        user.setFeishuOpenId(blankToNull(limit(openId, 128)));
        user.setFeishuUnionId(blankToNull(limit(unionId, 128)));
    }

    private void updateNullableUserFields(Long id, SysUser user) {
        LambdaUpdateWrapper<SysUser> wrapper = new LambdaUpdateWrapper<SysUser>()
            .eq(SysUser::getId, id)
            .set(SysUser::getSupplierId, user.getSupplierId())
            .set(SysUser::getDeptCode, user.getDeptCode())
            .set(SysUser::getDeptName, user.getDeptName())
            .set(SysUser::getMobile, user.getMobile())
            .set(SysUser::getEmail, user.getEmail())
            .set(SysUser::getFeishuUserId, user.getFeishuUserId())
            .set(SysUser::getFeishuOpenId, user.getFeishuOpenId())
            .set(SysUser::getFeishuUnionId, user.getFeishuUnionId());
        baseMapper.update(null, wrapper);
    }

    private boolean isDeletedUser(SysUser user) {
        return user != null && Objects.equals(user.getIsDeleted(), 1);
    }

    private void restoreDeletedUser(SysUser user) {
        user.setIsDeleted(0);
        user.setUpdatedBy(currentOperatorId());
        int updated = baseMapper.restoreDeletedUser(user);
        if (updated <= 0) {
            throw new BizException("USER_RESTORE_FAILED", "恢复已删除账号失败，请刷新后重试");
        }
    }

    private void bindRoles(Long userId, List<Long> roleIds) {
        List<Long> filtered = CollectionUtils.isEmpty(roleIds)
            ? List.of()
            : roleIds.stream().filter(Objects::nonNull).distinct().toList();
        List<SysRole> roles = filtered.isEmpty() ? List.of() : roleMapper.selectBatchIds(filtered);
        if (!filtered.isEmpty() && roles.size() != filtered.size()) {
            throw new BizException("ROLE_NOT_FOUND", "Invalid role ID exists in request");
        }
        AuthUser authUser = AuthContextHolder.get();
        if (authUser != null && !DataScopeHelper.isSuperAdmin(authUser)) {
            for (SysRole role : roles) {
                if (isAdminRole(role)) {
                    throw new BizException("FORBIDDEN", "Current account cannot assign admin roles");
                }
            }
        }

        Set<Long> targetRoleIds = new LinkedHashSet<>(filtered);
        Set<Long> existingRoleIds = new LinkedHashSet<>();
        Long operatorId = currentOperatorId();
        List<SysUserRole> existingRoles = userRoleMapper.selectAllByUserIdIncludingDeleted(userId);
        for (SysUserRole existingRole : existingRoles) {
            existingRoleIds.add(existingRole.getRoleId());
            boolean selected = targetRoleIds.contains(existingRole.getRoleId());
            userRoleMapper.updateBindingState(
                userId,
                existingRole.getRoleId(),
                selected ? 1 : 0,
                selected ? 0 : 1,
                operatorId
            );
        }

        for (Long roleId : filtered) {
            if (existingRoleIds.contains(roleId)) {
                continue;
            }
            SysUserRole relation = new SysUserRole();
            relation.setUserId(userId);
            relation.setRoleId(roleId);
            relation.setStatus(1);
            userRoleMapper.insert(relation);
        }
    }

    private Long currentOperatorId() {
        AuthUser authUser = AuthContextHolder.get();
        return authUser == null || authUser.getUserId() == null ? 0L : authUser.getUserId();
    }

    private void grantDefaultPlatformAccessIfMissing(Long userId, String userType) {
        Long existing = userAppAccessMapper.selectCount(new LambdaQueryWrapper<PlatformUserAppAccess>()
            .eq(PlatformUserAppAccess::getUserId, userId));
        if (existing == null || existing == 0) {
            grantDefaultPlatformAccess(userId, userType);
        }
    }

    private void grantDefaultPlatformAccess(Long userId, String userType) {
        List<String> appCodes = defaultPlatformAppCodes(userType);
        if (appCodes.isEmpty()) {
            return;
        }
        for (String appCode : appCodes) {
            PlatformUserAppAccess existing = userAppAccessMapper.selectOne(new LambdaQueryWrapper<PlatformUserAppAccess>()
                .eq(PlatformUserAppAccess::getUserId, userId)
                .eq(PlatformUserAppAccess::getAppCode, appCode)
                .last("LIMIT 1"));
            if (existing == null) {
                PlatformUserAppAccess access = new PlatformUserAppAccess();
                access.setUserId(userId);
                access.setAppCode(appCode);
                access.setStatus(1);
                access.setGrantedBy(AuthContextHolder.get() == null ? 0L : AuthContextHolder.get().getUserId());
                access.setGrantedAt(LocalDateTime.now());
                access.setRemark("按用户类型自动授权");
                userAppAccessMapper.insert(access);
            } else if (existing.getStatus() == null || existing.getStatus() != 1) {
                existing.setStatus(1);
                existing.setGrantedBy(AuthContextHolder.get() == null ? 0L : AuthContextHolder.get().getUserId());
                existing.setGrantedAt(LocalDateTime.now());
                existing.setRemark("按用户类型自动授权");
                userAppAccessMapper.updateById(existing);
            }
        }
    }

    private List<String> defaultPlatformAppCodes(String userType) {
        return switch (normalizeUserType(userType)) {
            case "SUPER_ADMIN" -> List.of("PROCUREMENT", "PLATFORM");
            case "ENGINEER", "FLOOR_LEADER" -> List.of("PROCUREMENT");
            case "PURCHASER", "MANAGER", "REGION_MANAGER", "SENIOR_MANAGER", "SUPPLIER" -> List.of("PROCUREMENT");
            default -> List.of();
        };
    }

    private boolean isAdminRole(SysRole role) {
        if (role == null) {
            return false;
        }
        return DataScopeHelper.SUPER_ADMIN_ROLE_CODE.equalsIgnoreCase(role.getRoleCode());
    }

    private void ensureCanCreateOrUpdateUser(String targetUserType, Long targetRegionId, Long targetSupplierId) {
        AuthUser authUser = AuthContextHolder.get();
        if (authUser == null || DataScopeHelper.isSuperAdmin(authUser)) {
            return;
        }
        if (DataScopeHelper.isSupplierUser(authUser)) {
            throw new BizException("FORBIDDEN", "Supplier account cannot maintain users");
        }
        if (isPrivilegedUserType(targetUserType)) {
            throw new BizException("FORBIDDEN", "Current account cannot create or update admin users");
        }
        String scope = DataScopeHelper.resolveDataScope(authUser);
        if (DataScopeHelper.SCOPE_REGION.equals(scope) && !Objects.equals(authUser.getRegionId(), targetRegionId)) {
            throw new BizException("FORBIDDEN", "Current account cannot maintain users in other regions");
        }
        if (DataScopeHelper.SCOPE_SELF.equals(scope)) {
            throw new BizException("FORBIDDEN", "Current account has no user maintenance permission");
        }
        if (DataScopeHelper.USER_TYPE_SUPPLIER.equalsIgnoreCase(targetUserType) && !DataScopeHelper.canAccessSupplier(authUser, targetSupplierId)) {
            throw new BizException("FORBIDDEN", "Current account cannot maintain this supplier user");
        }
    }

    private void ensureCanAccessTargetUser(SysUser targetUser) {
        AuthUser authUser = AuthContextHolder.get();
        if (authUser == null || DataScopeHelper.isSuperAdmin(authUser)) {
            return;
        }
        if (isPrivilegedUserType(targetUser.getUserType())) {
            throw new BizException("FORBIDDEN", "Current account cannot access privileged users");
        }
        if (DataScopeHelper.isSupplierUser(authUser)) {
            if (!Objects.equals(authUser.getUserId(), targetUser.getId())) {
                throw new BizException("FORBIDDEN", "Supplier account can only access self data");
            }
            return;
        }
        String scope = DataScopeHelper.resolveDataScope(authUser);
        if (DataScopeHelper.SCOPE_REGION.equals(scope) && !Objects.equals(authUser.getRegionId(), targetUser.getRegionId())) {
            throw new BizException("FORBIDDEN", "Current account cannot access users in other regions");
        }
        if (DataScopeHelper.SCOPE_SELF.equals(scope) && !Objects.equals(authUser.getUserId(), targetUser.getId())) {
            throw new BizException("FORBIDDEN", "Current account can only access self data");
        }
    }

    private String resolveUsername(String username, String employeeNo) {
        String normalizedEmployeeNo = requiredEmployeeNo(employeeNo);
        if (StringUtils.hasText(username) && !normalizedEmployeeNo.equals(username.trim())) {
            throw new BizException("USERNAME_EMPLOYEE_NO_MISMATCH", "Username must match employee number");
        }
        return normalizedEmployeeNo;
    }

    private String resolveUsername(String userType, String username, String employeeNo, Long supplierId, Long excludeId) {
        if (DataScopeHelper.USER_TYPE_SUPPLIER.equalsIgnoreCase(userType)) {
            return resolveSupplierUsername(username, supplierId, excludeId);
        }
        return resolveUsername(username, employeeNo);
    }

    private String resolveSupplierUsername(String username, Long supplierId, Long excludeId) {
        String normalizedUsername = limit(username, 64);
        if (StringUtils.hasText(normalizedUsername)) {
            return normalizedUsername;
        }
        if (supplierId == null) {
            throw new BizException("SUPPLIER_REQUIRED", "Supplier account must bind a supplier");
        }
        Supplier supplier = supplierMapper.selectById(supplierId);
        if (supplier == null) {
            throw new BizException("SUPPLIER_NOT_FOUND", "Supplier not found");
        }
        List<String> candidates = new ArrayList<>();
        candidates.add(limit(supplier.getSupplierName(), 64));
        candidates.add(limit(supplier.getSupplierShortName(), 64));
        candidates.add(limit(supplier.getSupplierCode(), 64));
        candidates.add(limit("supplier-" + supplierId, 64));
        for (String candidate : candidates) {
            if (StringUtils.hasText(candidate) && !existsUsername(candidate, excludeId)) {
                return candidate;
            }
        }
        throw new BizException("SUPPLIER_USERNAME_EXISTS", "Supplier account username already exists");
    }

    private String resolveEmployeeNo(String userType, String employeeNo) {
        if (DataScopeHelper.USER_TYPE_SUPPLIER.equalsIgnoreCase(userType)) {
            return blankToNull(limit(employeeNo, 64));
        }
        return requiredEmployeeNo(employeeNo);
    }

    private String requiredEmployeeNo(String employeeNo) {
        String normalizedEmployeeNo = limit(employeeNo, 64);
        if (!StringUtils.hasText(normalizedEmployeeNo)) {
            throw new BizException("EMPLOYEE_NO_REQUIRED", "工号必填");
        }
        return normalizedEmployeeNo;
    }

    private Long resolveUserRegionId(Long regionId) {
        if (regionId != null) {
            return regionId;
        }
        AuthUser authUser = AuthContextHolder.get();
        if (authUser != null && authUser.getRegionId() != null) {
            return authUser.getRegionId();
        }
        return 1L;
    }

    private boolean existsUsername(String username, Long excludeId) {
        return baseMapper.countAnyByUsernameExcept(username, excludeId) > 0;
    }

    private List<Long> normalizeRoleIds(String userType, List<Long> roleIds) {
        List<Long> normalized = roleIds == null
            ? new ArrayList<>()
            : new ArrayList<>(roleIds.stream().filter(Objects::nonNull).distinct().toList());
        String primaryRoleCode = resolvePrimaryRoleCode(userType);
        if (!StringUtils.hasText(primaryRoleCode)) {
            return normalized;
        }
        SysRole primaryRole = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>().eq(SysRole::getRoleCode, primaryRoleCode));
        if (primaryRole == null) {
            return normalized;
        }
        if (DataScopeHelper.USER_TYPE_SUPPLIER.equalsIgnoreCase(userType)) {
            return List.of(primaryRole.getId());
        }
        if (!normalized.contains(primaryRole.getId())) {
            normalized.add(0, primaryRole.getId());
        }
        return normalized;
    }

    private String resolvePrimaryRoleCode(String userType) {
        String normalized = normalizeUserType(userType);
        return StringUtils.hasText(normalized) ? normalized : null;
    }

    private String resolveDataScopeType(String userType, String dataScopeType) {
        if (DataScopeHelper.USER_TYPE_SUPPLIER.equalsIgnoreCase(userType)) {
            return DataScopeHelper.SCOPE_SELF;
        }
        if (isPrivilegedUserType(userType)) {
            return DataScopeHelper.SCOPE_ALL;
        }
        return DataScopeHelper.SCOPE_ORG;
    }

    private boolean isPrivilegedUserType(String userType) {
        String normalized = normalizeUserType(userType);
        return DataScopeHelper.SUPER_ADMIN_ROLE_CODE.equals(normalized);
    }

    private String normalizeUserType(String userType) {
        if (!StringUtils.hasText(userType)) {
            return "";
        }
        String value = userType.trim();
        String compact = value
            .replace(" ", "")
            .replace("　", "")
            .replace("（", "(")
            .replace("）", ")");
        String upper = compact.toUpperCase(Locale.ROOT);
        List<String> codes = List.of(
            "SUPER_ADMIN",
            "SENIOR_MANAGER",
            "REGION_MANAGER",
            "FLOOR_LEADER",
            "PURCHASER",
            "SUPPLIER",
            "ENGINEER",
            "MANAGER",
            "SYS_ADMIN",
            "ADMIN"
        );
        for (String code : codes) {
            if (upper.equals(code) || upper.contains("(" + code + ")")) {
                return code;
            }
        }
        return switch (compact) {
            case "最高管理员", "系统管理员", "超级管理员" -> DataScopeHelper.SUPER_ADMIN_ROLE_CODE;
            case "工程师", "员工", "自有员工" -> "ENGINEER";
            case "楼长", "楼长/现场负责人", "现场负责人" -> "FLOOR_LEADER";
            case "经理", "机房经理", "部门经理" -> "MANAGER";
            case "高级经理" -> "SENIOR_MANAGER";
            case "区域经理" -> "REGION_MANAGER";
            case "采购", "采购员", "采购人员" -> "PURCHASER";
            case "供应商", "供应商账号", "外部供应商" -> DataScopeHelper.USER_TYPE_SUPPLIER;
            default -> value.toUpperCase(Locale.ROOT);
        };
    }
}
