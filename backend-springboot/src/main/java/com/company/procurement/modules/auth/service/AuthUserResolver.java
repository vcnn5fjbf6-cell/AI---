package com.company.procurement.modules.auth.service;

import com.company.procurement.common.exception.BizException;
import com.company.procurement.common.security.AuthUser;
import com.company.procurement.common.security.JwtTokenIdentity;
import com.company.procurement.common.security.PermissionApi;
import com.company.procurement.modules.ehs.config.EhsProperties;
import com.company.procurement.modules.outsourcing.config.OutsourcingProperties;
import com.company.procurement.modules.platform.permission.service.PlatformPermissionService;
import com.company.procurement.modules.system.user.entity.SysUser;
import com.company.procurement.modules.system.user.mapper.SysUserMapper;
import com.company.procurement.modules.system.user.mapper.SysUserRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthUserResolver {

    private static final List<String> ADMIN_ROLE_CODES = List.of("SUPER_ADMIN", "SYS_ADMIN", "ADMIN");
    private static final List<String> ADMIN_APP_CODES = List.of("PLATFORM", "PROCUREMENT");
    private static final String WILDCARD_PERMISSION_CODE = "*:*:*";
    private static final long PASSWORD_EXPIRE_DAYS = 90L;

    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final PlatformPermissionService platformPermissionService;
    private final EhsProperties ehsProperties;
    private final OutsourcingProperties outsourcingProperties;

    public AuthUser loadFromToken(JwtTokenIdentity identity) {
        if (identity == null || identity.userId() == null || !StringUtils.hasText(identity.sessionSecret())) {
            throw new BizException("UNAUTHORIZED", "Invalid session, please login again");
        }
        SysUser user = userMapper.selectById(identity.userId());
        if (user == null) {
            throw new BizException("UNAUTHORIZED", "Invalid session, please login again");
        }
        if (StringUtils.hasText(identity.username()) && !identity.username().equals(user.getUsername())) {
            throw new BizException("UNAUTHORIZED", "Invalid session, please login again");
        }
        if (!secureEquals(identity.sessionSecret(), user.getSessionSecret())) {
            throw new BizException("UNAUTHORIZED", "Invalid session, please login again");
        }
        return buildAuthUser(user);
    }

    public AuthUser buildAuthUser(SysUser user) {
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            throw new BizException("USER_DISABLED", "Account is disabled");
        }

        List<String> roleCodes = userRoleMapper.selectRoleCodesByUserId(user.getId());
        boolean systemAdmin = isSystemAdministrator(user, roleCodes);
        if (CollectionUtils.isEmpty(roleCodes) && !systemAdmin) {
            throw new BizException("ROLE_MISSING", "No role assigned to current user");
        }
        List<String> permissionCodes = userRoleMapper.selectPermissionCodesByUserId(user.getId());
        List<String> apiPermissions = buildApiPermissions(userRoleMapper.selectPermissionApisByUserId(user.getId()));
        List<String> appCodes = platformPermissionService.enabledAppCodesByUserId(user.getId());

        if (systemAdmin) {
            roleCodes = mergeCodes(roleCodes, ADMIN_ROLE_CODES);
            permissionCodes = mergeCodes(permissionCodes, List.of(WILDCARD_PERMISSION_CODE));
            appCodes = mergeCodes(appCodes, adminAppCodes());
        }

        return AuthUser.builder()
            .userId(user.getId())
            .username(user.getUsername())
            .realName(user.getRealName())
            .employeeNo(user.getEmployeeNo())
            .userType(user.getUserType())
            .regionId(user.getRegionId())
            .deptCode(user.getDeptCode())
            .deptName(user.getDeptName())
            .supplierId(user.getSupplierId())
            .dataScopeType(systemAdmin ? "ALL" : user.getDataScopeType())
            .roleCodes(roleCodes)
            .permissionCodes(permissionCodes == null ? List.of() : permissionCodes)
            .appCodes(appCodes == null ? List.of() : appCodes)
            .apiPermissions(apiPermissions)
            .mustChangePassword(user.getMustChangePassword())
            .passwordChangedAt(user.getPasswordChangedAt())
            .passwordExpiresAt(passwordExpiresAt(user))
            .passwordExpired(passwordExpired(user))
            .build();
    }

    private List<String> adminAppCodes() {
        List<String> appCodes = ehsProperties.isEnabled()
            ? mergeCodes(ADMIN_APP_CODES, List.of("EHS"))
            : ADMIN_APP_CODES;
        return outsourcingProperties.isEnabled()
            ? mergeCodes(appCodes, List.of("OUTSOURCING"))
            : appCodes;
    }

    private boolean passwordExpired(SysUser user) {
        if (user == null) {
            return true;
        }
        if (Integer.valueOf(1).equals(user.getMustChangePassword())) {
            return true;
        }
        LocalDateTime expiresAt = passwordExpiresAt(user);
        return expiresAt == null || !expiresAt.isAfter(LocalDateTime.now());
    }

    private LocalDateTime passwordExpiresAt(SysUser user) {
        if (user == null || user.getPasswordChangedAt() == null) {
            return null;
        }
        return user.getPasswordChangedAt().plusDays(PASSWORD_EXPIRE_DAYS);
    }

    private boolean secureEquals(String left, String right) {
        if (!StringUtils.hasText(left) || !StringUtils.hasText(right)) {
            return false;
        }
        return MessageDigest.isEqual(left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }

    private boolean isSystemAdministrator(SysUser user, List<String> roleCodes) {
        if (user == null) {
            return false;
        }
        if (isAdminCode(user.getUserType())) {
            return true;
        }
        return !CollectionUtils.isEmpty(roleCodes) && roleCodes.stream().anyMatch(this::isAdminCode);
    }

    private boolean isAdminCode(String code) {
        if (!StringUtils.hasText(code)) {
            return false;
        }
        return ADMIN_ROLE_CODES.contains(code.trim().toUpperCase(Locale.ROOT));
    }

    private List<String> mergeCodes(List<String> existing, List<String> additions) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        if (existing != null) {
            existing.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .forEach(merged::add);
        }
        additions.stream()
            .filter(StringUtils::hasText)
            .map(String::trim)
            .forEach(merged::add);
        return List.copyOf(merged);
    }

    private List<String> buildApiPermissions(List<PermissionApi> permissionApis) {
        if (CollectionUtils.isEmpty(permissionApis)) {
            return List.of();
        }
        List<String> apiPermissions = new ArrayList<>();
        for (PermissionApi permissionApi : permissionApis) {
            if (permissionApi == null || !StringUtils.hasText(permissionApi.getPath())) {
                continue;
            }
            String method = StringUtils.hasText(permissionApi.getHttpMethod())
                ? permissionApi.getHttpMethod().trim().toUpperCase(Locale.ROOT)
                : "*";
            String path = normalizePath(permissionApi.getPath());
            apiPermissions.add(method + " " + path);
        }
        return apiPermissions.stream().distinct().toList();
    }

    private String normalizePath(String path) {
        String normalized = path.trim();
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        while (normalized.contains("//")) {
            normalized = normalized.replace("//", "/");
        }
        return normalized;
    }
}
