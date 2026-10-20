package com.company.procurement.common.security;

import com.company.procurement.common.exception.BizException;
import com.company.procurement.modules.auth.service.AuthUserResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;
    private final AuthUserResolver authUserResolver;

    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private static final String APP_CODE_PLATFORM = "PLATFORM";
    private static final String APP_CODE_PROCUREMENT = "PROCUREMENT";
    private static final String APP_CODE_MAINTENANCE = "MAINTENANCE";
    private static final String APP_CODE_EHS = "EHS";
    private static final String APP_CODE_OUTSOURCING = "OUTSOURCING";

    private static final List<String> WHITE_LIST = List.of(
        "/api/auth/login",
        "/api/public/ehs/**",
        "/api/auth/feishu/**",
        "/api/integrations/feishu-bot/**",
        "/api/integrations/shopping/**",
        "/v3/api-docs/**",
        "/swagger-ui/**",
        "/swagger-ui.html",
        "/error"
    );

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        String path = requestPath(request);
        if (isWhitePath(path)) {
            return true;
        }

        String token = resolveToken(request);
        if (!StringUtils.hasText(token)) {
            throw new BizException("UNAUTHORIZED", "Not logged in or session expired");
        }

        AuthUser authUser = authUserResolver.loadFromToken(jwtTokenProvider.parseToken(token));
        AuthContextHolder.set(authUser);
        if (passwordChangeRequired(authUser) && !isPasswordMaintenancePath(path)) {
            throw new BizException("PASSWORD_EXPIRED", "密码已过期，请先修改密码");
        }
        validatePathPrefix(authUser, request.getMethod(), path);
        validateAppAccess(authUser, path);
        if (!hasPermission(authUser, request.getMethod(), path)) {
            throw new BizException("FORBIDDEN", "No permission to access this API");
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        AuthContextHolder.clear();
    }

    private void validatePathPrefix(AuthUser authUser, String method, String path) {
        if (path.startsWith("/api/admin") && DataScopeHelper.isSupplierUser(authUser)) {
            throw new BizException("FORBIDDEN", "Supplier account cannot access admin APIs");
        }
        if (path.startsWith("/api/supplier")
            && !DataScopeHelper.isSupplierUser(authUser)
            && !DataScopeHelper.isSystemAdmin(authUser)) {
            List<String> apiPermissions = authUser.getApiPermissions();
            if (apiPermissions != null && !apiPermissions.isEmpty()) {
                return;
            }
            throw new BizException("FORBIDDEN", "Only supplier accounts or admins can access supplier APIs");
        }
    }

    private void validateAppAccess(AuthUser authUser, String path) {
        if (DataScopeHelper.isSystemAdmin(authUser)) {
            return;
        }
        String requiredAppCode = requiredAppCode(path);
        if (!StringUtils.hasText(requiredAppCode)) {
            List<String> requiredAnyAppCodes = requiredAnyAppCodes(path);
            if (requiredAnyAppCodes.isEmpty()) {
                return;
            }
            if (!hasAnyAppAccess(authUser, requiredAnyAppCodes)) {
                throw new BizException("APP_ACCESS_DENIED", "Current account has no access to this application");
            }
            return;
        }
        if (!hasAppAccess(authUser, requiredAppCode)) {
            throw new BizException("APP_ACCESS_DENIED", "Current account has no access to this application");
        }
    }

    private String requiredAppCode(String path) {
        if (path.startsWith("/api/workflow")
            || path.startsWith("/api/procurement")
            || path.startsWith("/api/supplier")
            || path.startsWith("/api/admin/procurement")
            || path.startsWith("/api/admin/purchase-requests")
            || path.startsWith("/api/admin/suppliers")) {
            return APP_CODE_PROCUREMENT;
        }
        if (path.startsWith("/api/admin/platform")
            || path.startsWith("/api/admin/ai-governance")
            || path.startsWith("/api/admin/users")
            || path.startsWith("/api/admin/roles")
            || path.startsWith("/api/admin/database")
            || path.startsWith("/api/admin/error-logs")
            || path.startsWith("/api/admin/import-file-logs")
            || path.startsWith("/api/admin/procurement-ai/message-logs")
            || path.startsWith("/api/admin/approval-records")
            || path.startsWith("/api/workbench/notices/manage")) {
            return APP_CODE_PLATFORM;
        }
        if (path.startsWith("/api/maintenance") || path.startsWith("/api/admin/maintenance")) {
            return APP_CODE_MAINTENANCE;
        }
        if (path.startsWith("/api/ehs") || path.startsWith("/api/admin/ehs")) {
            return APP_CODE_EHS;
        }
        if (path.startsWith("/api/outsourcing") || path.startsWith("/api/admin/outsourcing")) {
            return APP_CODE_OUTSOURCING;
        }
        return "";
    }

    private List<String> requiredAnyAppCodes(String path) {
        if (path.startsWith("/api/workbench/requests")
            || path.startsWith("/api/workbench/follows")
            || path.startsWith("/api/workbench/users/options")) {
            return List.of(APP_CODE_PROCUREMENT, APP_CODE_PLATFORM, APP_CODE_MAINTENANCE, APP_CODE_EHS, APP_CODE_OUTSOURCING);
        }
        return List.of();
    }

    private boolean hasAppAccess(AuthUser authUser, String appCode) {
        if (authUser == null || authUser.getAppCodes() == null) {
            return false;
        }
        String normalizedAppCode = appCode.trim().toUpperCase(Locale.ROOT);
        return authUser.getAppCodes().stream()
            .filter(StringUtils::hasText)
            .map(code -> code.trim().toUpperCase(Locale.ROOT))
            .anyMatch(normalizedAppCode::equals);
    }

    private boolean hasAnyAppAccess(AuthUser authUser, List<String> appCodes) {
        if (appCodes == null || appCodes.isEmpty()) {
            return true;
        }
        return appCodes.stream().anyMatch(appCode -> hasAppAccess(authUser, appCode));
    }

    private boolean hasPermission(AuthUser authUser, String method, String requestPath) {
        if (DataScopeHelper.isSystemAdmin(authUser)) {
            return true;
        }
        if (requestPath.startsWith("/api/auth")) {
            return true;
        }
        List<String> apiPermissions = authUser.getApiPermissions();
        if (apiPermissions == null || apiPermissions.isEmpty()) {
            return fallbackByUserType(authUser, requestPath);
        }
        String normalizedMethod = method == null ? "" : method.toUpperCase(Locale.ROOT);
        for (String permission : apiPermissions) {
            if (!StringUtils.hasText(permission)) {
                continue;
            }
            int idx = permission.indexOf(' ');
            if (idx <= 0 || idx == permission.length() - 1) {
                continue;
            }
            String permMethod = permission.substring(0, idx).toUpperCase(Locale.ROOT);
            String permPath = permission.substring(idx + 1);
            if (!"*".equals(permMethod) && !permMethod.equals(normalizedMethod)) {
                continue;
            }
            if (pathMatcher.match(normalizePathPattern(permPath), requestPath)) {
                return true;
            }
        }
        return fallbackByUserType(authUser, requestPath) && !requestPath.startsWith("/api/admin");
    }

    private boolean fallbackByUserType(AuthUser authUser, String requestPath) {
        if (requestPath.startsWith("/api/admin")) {
            return !DataScopeHelper.isSupplierUser(authUser);
        }
        if (requestPath.startsWith("/api/supplier")) {
            return DataScopeHelper.isSupplierUser(authUser) || DataScopeHelper.isSystemAdmin(authUser);
        }
        return true;
    }

    private String resolveToken(HttpServletRequest request) {
        String headerName = jwtProperties.getHeaderName();
        String tokenPrefix = jwtProperties.getTokenPrefix();
        String header = request.getHeader(headerName);
        if (!StringUtils.hasText(header)) {
            return null;
        }
        if (!StringUtils.hasText(tokenPrefix)) {
            return header.trim();
        }
        String expected = tokenPrefix + " ";
        if (!header.startsWith(expected)) {
            return null;
        }
        return header.substring(expected.length()).trim();
    }

    private boolean isWhitePath(String path) {
        for (String pattern : WHITE_LIST) {
            if (pathMatcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    private boolean passwordChangeRequired(AuthUser authUser) {
        if (authUser == null) {
            return false;
        }
        return Boolean.TRUE.equals(authUser.getPasswordExpired())
            || Integer.valueOf(1).equals(authUser.getMustChangePassword());
    }

    private boolean isPasswordMaintenancePath(String path) {
        return "/api/auth/me".equals(path)
            || "/api/auth/password/change".equals(path)
            || "/api/platform/apps".equals(path)
            || "/api/platform/me/access".equals(path);
    }

    private String requestPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (StringUtils.hasText(contextPath) && uri.startsWith(contextPath)) {
            uri = uri.substring(contextPath.length());
        }
        return normalizePathPattern(uri);
    }

    private String normalizePathPattern(String path) {
        if (!StringUtils.hasText(path)) {
            return "/";
        }
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
