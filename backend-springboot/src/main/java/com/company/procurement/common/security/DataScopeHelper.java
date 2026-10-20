package com.company.procurement.common.security;

import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class DataScopeHelper {

    public static final String SCOPE_ALL = "ALL";
    public static final String SCOPE_ORG = "ORG";
    public static final String SCOPE_REGION = "REGION";
    public static final String SCOPE_SELF = "SELF";
    public static final String USER_TYPE_SUPPLIER = "SUPPLIER";
    public static final String USER_TYPE_SUPPLIER_SUPERVISOR = "SUPPLIER_QUOTE_VIEWER";
    public static final List<String> SUPPLIER_USER_TYPES = List.of(USER_TYPE_SUPPLIER, USER_TYPE_SUPPLIER_SUPERVISOR);
    public static final List<String> SUPPLIER_ROLE_CODES = List.of(USER_TYPE_SUPPLIER, USER_TYPE_SUPPLIER_SUPERVISOR);
    public static final String SUPER_ADMIN_ROLE_CODE = "SUPER_ADMIN";
    public static final List<String> SUPER_ADMIN_ROLE_CODES = List.of(SUPER_ADMIN_ROLE_CODE);
    public static final List<String> SYSTEM_ADMIN_ROLE_CODES = List.of(SUPER_ADMIN_ROLE_CODE, "SYS_ADMIN", "ADMIN");
    public static final List<String> SYSTEM_ADMIN_USER_TYPES = List.of(SUPER_ADMIN_ROLE_CODE, "SYS_ADMIN", "ADMIN");

    private DataScopeHelper() {
    }

    public static boolean isSupplierUser(AuthUser authUser) {
        if (authUser == null) {
            return false;
        }
        if (hasAnyUserType(authUser, SUPPLIER_USER_TYPES)) {
            return true;
        }
        if (authUser.getSupplierId() != null) {
            return true;
        }
        if (authUser.getRoleCodes() == null) {
            return false;
        }
        return hasAnyRole(authUser, SUPPLIER_ROLE_CODES);
    }

    public static boolean isSuperAdmin(AuthUser authUser) {
        if (authUser == null) {
            return false;
        }
        if (SUPER_ADMIN_ROLE_CODE.equalsIgnoreCase(authUser.getUserType())) {
            return true;
        }
        return hasAnyRole(authUser, SUPER_ADMIN_ROLE_CODES);
    }

    public static boolean isSystemAdmin(AuthUser authUser) {
        if (authUser == null) {
            return false;
        }
        if (hasAnyUserType(authUser, SYSTEM_ADMIN_USER_TYPES)) {
            return true;
        }
        return hasAnyRole(authUser, SYSTEM_ADMIN_ROLE_CODES);
    }

    public static boolean hasRole(AuthUser authUser, String roleCode) {
        if (!StringUtils.hasText(roleCode)) {
            return false;
        }
        return hasAnyRole(authUser, List.of(roleCode));
    }

    public static boolean hasPermission(AuthUser authUser, String permissionCode) {
        if (authUser == null || !StringUtils.hasText(permissionCode) || authUser.getPermissionCodes() == null) {
            return false;
        }
        return authUser.getPermissionCodes().stream()
            .filter(StringUtils::hasText)
            .anyMatch(code -> "*".equals(code) || "*:*:*".equals(code) || permissionCode.equals(code));
    }

    public static boolean hasAnyRole(AuthUser authUser, List<String> roleCodes) {
        if (authUser == null || authUser.getRoleCodes() == null || roleCodes == null || roleCodes.isEmpty()) {
            return false;
        }
        return authUser.getRoleCodes().stream()
            .filter(StringUtils::hasText)
            .map(code -> code.toUpperCase(Locale.ROOT))
            .anyMatch(roleCodes::contains);
    }

    private static boolean hasAnyUserType(AuthUser authUser, List<String> userTypes) {
        if (authUser == null || !StringUtils.hasText(authUser.getUserType()) || userTypes == null || userTypes.isEmpty()) {
            return false;
        }
        return userTypes.contains(authUser.getUserType().trim().toUpperCase(Locale.ROOT));
    }

    public static String resolveDataScope(AuthUser authUser) {
        if (authUser == null) {
            return SCOPE_SELF;
        }
        if (isSystemAdmin(authUser)) {
            return SCOPE_ALL;
        }
        if (isSupplierUser(authUser)) {
            return SCOPE_SELF;
        }
        if (!StringUtils.hasText(authUser.getDataScopeType())) {
            return SCOPE_REGION;
        }
        String normalized = authUser.getDataScopeType().toUpperCase(Locale.ROOT);
        if (SCOPE_ALL.equals(normalized) || SCOPE_ORG.equals(normalized) || SCOPE_REGION.equals(normalized) || SCOPE_SELF.equals(normalized)) {
            return normalized;
        }
        return SCOPE_REGION;
    }

    public static boolean canAccessRegion(AuthUser authUser, Long regionId) {
        if (isSystemAdmin(authUser)) {
            return true;
        }
        if (regionId == null) {
            return false;
        }
        String scope = resolveDataScope(authUser);
        if (SCOPE_ALL.equals(scope)) {
            return true;
        }
        return SCOPE_REGION.equals(scope) && Objects.equals(authUser.getRegionId(), regionId);
    }

    public static boolean canAccessSupplier(AuthUser authUser, Long supplierId) {
        if (isSystemAdmin(authUser)) {
            return true;
        }
        if (isSupplierUser(authUser)) {
            return Objects.equals(authUser.getSupplierId(), supplierId);
        }
        return true;
    }
}
