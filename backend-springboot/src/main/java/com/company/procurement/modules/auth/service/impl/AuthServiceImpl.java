package com.company.procurement.modules.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.company.procurement.common.exception.BizException;
import com.company.procurement.common.security.AuthContextHolder;
import com.company.procurement.common.security.AuthUser;
import com.company.procurement.common.security.JwtProperties;
import com.company.procurement.common.security.JwtTokenProvider;
import com.company.procurement.common.security.PasswordCrypto;
import com.company.procurement.modules.auth.config.FeishuAuthProperties;
import com.company.procurement.modules.auth.dto.AuthLoginDTO;
import com.company.procurement.modules.auth.dto.AuthPasswordChangeDTO;
import com.company.procurement.modules.auth.vo.AuthLoginCandidateVO;
import com.company.procurement.modules.auth.dto.FeishuCallbackDTO;
import com.company.procurement.modules.auth.service.AuthService;
import com.company.procurement.modules.auth.service.AuthUserResolver;
import com.company.procurement.modules.auth.vo.AuthLoginVO;
import com.company.procurement.modules.auth.vo.AuthUserVO;
import com.company.procurement.modules.auth.vo.FeishuLoginUrlVO;
import com.company.procurement.modules.system.user.entity.SysUser;
import com.company.procurement.modules.system.user.entity.SysUserPasswordHistory;
import com.company.procurement.modules.system.user.mapper.SysUserMapper;
import com.company.procurement.modules.system.user.mapper.SysUserPasswordHistoryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
        new ParameterizedTypeReference<>() {
        };
    private static final int MAX_LOGIN_FAILURES = 5;
    private static final long LOGIN_LOCK_MILLIS = 15L * 60L * 1000L;
    private static final int PASSWORD_HISTORY_LIMIT = 6;
    private static final int PASSWORD_EXPIRE_DAYS = 90;
    private static final int FEISHU_EMAIL_LOOKUP_BATCH_SIZE = 50;

    private final SysUserMapper userMapper;
    private final SysUserPasswordHistoryMapper passwordHistoryMapper;
    private final AuthUserResolver authUserResolver;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;
    private final FeishuAuthProperties feishuProperties;

    private final RestClient restClient = RestClient.builder().build();
    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, Long> feishuStates = new ConcurrentHashMap<>();
    private final Map<String, LoginFailure> loginFailures = new ConcurrentHashMap<>();

    private volatile String cachedAppAccessToken;
    private volatile long cachedAppAccessTokenExpiresAt;
    private volatile String cachedTenantAccessToken;
    private volatile long cachedTenantAccessTokenExpiresAt;

    @Override
    public AuthLoginVO login(AuthLoginDTO dto, String clientIp) {
        String loginKey = loginThrottleKey(dto.getUsername(), clientIp);
        assertLoginAllowed(loginKey);
        List<SysUser> passwordMatchedUsers = findLoginCandidates(dto.getUsername()).stream()
            .filter(user -> PasswordCrypto.matches(dto.getPassword(), user.getPasswordHash()))
            .toList();
        if (passwordMatchedUsers.isEmpty()) {
            recordLoginFailure(loginKey);
            throw new BizException("LOGIN_FAILED", "Invalid username or password");
        }

        if (dto.getSelectedUserId() != null) {
            SysUser selectedUser = passwordMatchedUsers.stream()
                .filter(user -> dto.getSelectedUserId().equals(user.getId()))
                .findFirst()
                .orElse(null);
            if (selectedUser == null) {
                recordLoginFailure(loginKey);
                throw new BizException("LOGIN_FAILED", "Invalid username or password");
            }
            clearLoginFailure(loginKey);
            return buildLoginVO(selectedUser, clientIp);
        }

        List<SysUser> activeUsers = passwordMatchedUsers.stream()
            .filter(user -> user.getStatus() != null && user.getStatus() == 1)
            .toList();
        if (activeUsers.size() > 1) {
            AuthLoginVO loginVO = new AuthLoginVO();
            loginVO.setRequiresUserSelection(true);
            loginVO.setCandidates(activeUsers.stream().map(this::toLoginCandidateVO).toList());
            return loginVO;
        }
        clearLoginFailure(loginKey);
        return buildLoginVO(activeUsers.isEmpty() ? passwordMatchedUsers.get(0) : activeUsers.get(0), clientIp);
    }

    private void assertLoginAllowed(String key) {
        LoginFailure failure = loginFailures.get(key);
        long now = System.currentTimeMillis();
        if (failure == null) {
            return;
        }
        if (failure.lockedUntil() > now) {
            throw new BizException("LOGIN_LOCKED", "Too many failed login attempts, please try again later");
        }
        if (failure.lockedUntil() > 0 && failure.lockedUntil() <= now) {
            loginFailures.remove(key);
        }
    }

    private void recordLoginFailure(String key) {
        long now = System.currentTimeMillis();
        loginFailures.compute(key, (ignored, existing) -> {
            int count = existing == null || existing.lastFailedAt() + LOGIN_LOCK_MILLIS < now
                ? 1
                : existing.count() + 1;
            long lockedUntil = count >= MAX_LOGIN_FAILURES ? now + LOGIN_LOCK_MILLIS : 0L;
            return new LoginFailure(count, now, lockedUntil);
        });
    }

    private void clearLoginFailure(String key) {
        loginFailures.remove(key);
    }

    private String loginThrottleKey(String username, String clientIp) {
        return (StringUtils.hasText(username) ? username.trim().toLowerCase() : "-")
            + "|"
            + (StringUtils.hasText(clientIp) ? clientIp.trim() : "-");
    }

    @Override
    public FeishuLoginUrlVO feishuLoginUrl(String redirectUri) {
        assertFeishuEnabled();
        String callbackUri = resolveRedirectUri(redirectUri);
        String state = createFeishuState();
        String url = UriComponentsBuilder
            .fromUriString(feishuProperties.getAuthorizeUrl())
            .queryParam("app_id", feishuProperties.getAppId())
            .queryParam("redirect_uri", callbackUri)
            .queryParam("state", state)
            .build()
            .encode()
            .toUriString();
        return new FeishuLoginUrlVO(url);
    }

    @Override
    public AuthLoginVO feishuCallback(FeishuCallbackDTO dto, String clientIp) {
        assertFeishuEnabled();
        if (!consumeFeishuState(dto.getState())) {
            throw new BizException("FEISHU_STATE_INVALID", "飞书登录状态已失效，请重新发起登录");
        }

        Map<String, Object> identity = requestFeishuIdentity(dto.getCode(), dto.getRedirectUri());
        SysUser user = findBoundUser(identity);
        if (user == null) {
            user = findUserByFeishuEmailLookup(identity);
        }
        if (user == null) {
            Map<String, Object> enrichedIdentity = enrichFeishuIdentity(identity);
            user = findBoundUser(enrichedIdentity);
            identity = enrichedIdentity;
        }
        if (user == null) {
            log.warn("Feishu login user is not bound, identity={}", feishuIdentitySummary(identity));
            throw new BizException("FEISHU_USER_NOT_BOUND", "飞书账号未绑定系统用户，请联系管理员维护邮箱或手机号");
        }
        backfillFeishuBinding(user, identity);
        return buildLoginVO(user, clientIp);
    }

    @Override
    public AuthUserVO currentUser() {
        return toAuthUserVO(AuthContextHolder.requireUser());
    }

    @Override
    public void changePassword(AuthPasswordChangeDTO dto) {
        AuthUser authUser = AuthContextHolder.requireUser();
        SysUser user = userMapper.selectById(authUser.getUserId());
        if (user == null) {
            throw new BizException("USER_NOT_FOUND", "当前账号不存在");
        }
        if (!PasswordCrypto.matches(dto.getOldPassword(), user.getPasswordHash())) {
            throw new BizException("OLD_PASSWORD_INVALID", "当前密码不正确");
        }
        if (!dto.getNewPassword().equals(dto.getConfirmPassword())) {
            throw new BizException("PASSWORD_CONFIRM_MISMATCH", "两次输入的新密码不一致");
        }
        if (PasswordCrypto.matches(dto.getNewPassword(), user.getPasswordHash())) {
            throw new BizException("PASSWORD_REUSED", "新密码不能与最近 6 次使用过的密码重复");
        }
        assertPasswordNotRecentlyUsed(user.getId(), dto.getNewPassword());

        SysUserPasswordHistory history = new SysUserPasswordHistory();
        history.setUserId(user.getId());
        history.setPasswordHash(user.getPasswordHash());
        history.setChangedAt(user.getPasswordChangedAt() == null ? LocalDateTime.now() : user.getPasswordChangedAt());
        passwordHistoryMapper.insert(history);

        user.setPasswordHash(PasswordCrypto.encode(dto.getNewPassword()));
        user.setPasswordChangedAt(LocalDateTime.now());
        user.setMustChangePassword(0);
        user.setSessionSecret(createSessionSecret());
        userMapper.updateById(user);
    }

    @Override
    public void logout() {
        AuthUser authUser = AuthContextHolder.get();
        if (authUser != null && authUser.getUserId() != null) {
            SysUser user = userMapper.selectById(authUser.getUserId());
            if (user != null) {
                user.setSessionSecret(createSessionSecret());
                userMapper.updateById(user);
            }
        }
        AuthContextHolder.clear();
    }

    private void assertPasswordNotRecentlyUsed(Long userId, String newPassword) {
        List<SysUserPasswordHistory> histories = passwordHistoryMapper.selectList(new LambdaQueryWrapper<SysUserPasswordHistory>()
            .eq(SysUserPasswordHistory::getUserId, userId)
            .orderByDesc(SysUserPasswordHistory::getChangedAt)
            .orderByDesc(SysUserPasswordHistory::getId)
            .last("LIMIT " + PASSWORD_HISTORY_LIMIT));
        for (SysUserPasswordHistory history : histories) {
            if (PasswordCrypto.matches(newPassword, history.getPasswordHash())) {
                throw new BizException("PASSWORD_REUSED", "新密码不能与最近 6 次使用过的密码重复");
            }
        }
    }

    private AuthLoginVO buildLoginVO(SysUser user, String clientIp) {
        AuthUser authUser = authUserResolver.buildAuthUser(user);
        String sessionSecret = createSessionSecret();
        user.setSessionSecret(sessionSecret);
        user.setLastLoginAt(LocalDateTime.now());
        user.setLastLoginIp(clientIp);
        userMapper.updateById(user);

        String token = jwtTokenProvider.generateToken(authUser, sessionSecret);

        AuthLoginVO loginVO = new AuthLoginVO();
        loginVO.setToken(token);
        loginVO.setTokenType(jwtProperties.getTokenPrefix());
        loginVO.setExpiresIn(jwtProperties.getExpireSeconds());
        loginVO.setUser(toAuthUserVO(authUser, user));
        return loginVO;
    }

    private String createSessionSecret() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void assertFeishuEnabled() {
        if (!feishuProperties.isEnabled()) {
            throw new BizException("FEISHU_NOT_CONFIGURED", "飞书登录未启用");
        }
        if (!StringUtils.hasText(feishuProperties.getAppId())
            || !StringUtils.hasText(feishuProperties.getAppSecret())) {
            throw new BizException("FEISHU_NOT_CONFIGURED", "飞书应用参数未配置");
        }
    }

    private String resolveRedirectUri(String redirectUri) {
        if (StringUtils.hasText(redirectUri)) {
            return redirectUri.trim();
        }
        if (StringUtils.hasText(feishuProperties.getRedirectUri())) {
            return feishuProperties.getRedirectUri().trim();
        }
        throw new BizException("FEISHU_NOT_CONFIGURED", "飞书回调地址未配置");
    }

    private String createFeishuState() {
        cleanupExpiredStates();
        byte[] stateBytes = new byte[24];
        secureRandom.nextBytes(stateBytes);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(stateBytes);
        long ttlMillis = Math.max(60L, feishuProperties.getStateTtlSeconds()) * 1000L;
        feishuStates.put(state, System.currentTimeMillis() + ttlMillis);
        return state;
    }

    private boolean consumeFeishuState(String state) {
        if (!StringUtils.hasText(state)) {
            return false;
        }
        Long expiresAt = feishuStates.remove(state);
        return expiresAt != null && expiresAt > System.currentTimeMillis();
    }

    private void cleanupExpiredStates() {
        long now = System.currentTimeMillis();
        feishuStates.entrySet().removeIf(entry -> entry.getValue() <= now);
    }

    private Map<String, Object> requestFeishuIdentity(String code, String redirectUri) {
        String appAccessToken = resolveAppAccessToken();
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("grant_type", "authorization_code");
        requestBody.put("code", code);

        Map<String, Object> tokenResponse = restClient.post()
            .uri(feishuProperties.getAccessTokenUrl())
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + appAccessToken)
            .contentType(MediaType.APPLICATION_JSON)
            .body(requestBody)
            .retrieve()
            .body(MAP_TYPE);

        Map<String, Object> tokenData = unwrapFeishuData(tokenResponse, "FEISHU_TOKEN_FAILED", "飞书授权码换取令牌失败");
        Map<String, Object> identity = new HashMap<>(tokenData);
        String userAccessToken = text(tokenData.get("access_token"));
        if (StringUtils.hasText(userAccessToken) && StringUtils.hasText(feishuProperties.getUserInfoUrl())) {
            Map<String, Object> userResponse = restClient.get()
                .uri(feishuProperties.getUserInfoUrl())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + userAccessToken)
                .retrieve()
                .body(MAP_TYPE);
            identity.putAll(unwrapFeishuData(userResponse, "FEISHU_USER_INFO_FAILED", "获取飞书用户信息失败"));
        }
        return identity;
    }

    private String resolveAppAccessToken() {
        long now = System.currentTimeMillis();
        if (StringUtils.hasText(cachedAppAccessToken) && cachedAppAccessTokenExpiresAt > now) {
            return cachedAppAccessToken;
        }
        synchronized (this) {
            now = System.currentTimeMillis();
            if (StringUtils.hasText(cachedAppAccessToken) && cachedAppAccessTokenExpiresAt > now) {
                return cachedAppAccessToken;
            }

            Map<String, Object> requestBody = Map.of(
                "app_id", feishuProperties.getAppId(),
                "app_secret", feishuProperties.getAppSecret()
            );
            Map<String, Object> response = restClient.post()
                .uri(feishuProperties.getAppAccessTokenUrl())
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(MAP_TYPE);
            Map<String, Object> data = unwrapFeishuData(response, "FEISHU_APP_TOKEN_FAILED", "获取飞书应用令牌失败");
            String token = text(data.get("app_access_token"));
            if (!StringUtils.hasText(token)) {
                throw new BizException("FEISHU_APP_TOKEN_FAILED", "飞书应用令牌响应缺少 app_access_token");
            }
            long expireSeconds = numberValue(data.get("expire"), 7200L);
            cachedAppAccessToken = token;
            cachedAppAccessTokenExpiresAt = now + Math.max(60L, expireSeconds - 60L) * 1000L;
            return cachedAppAccessToken;
        }
    }

    private String resolveTenantAccessToken() {
        long now = System.currentTimeMillis();
        if (StringUtils.hasText(cachedTenantAccessToken) && cachedTenantAccessTokenExpiresAt > now) {
            return cachedTenantAccessToken;
        }
        synchronized (this) {
            now = System.currentTimeMillis();
            if (StringUtils.hasText(cachedTenantAccessToken) && cachedTenantAccessTokenExpiresAt > now) {
                return cachedTenantAccessToken;
            }
            Map<String, Object> requestBody = Map.of(
                "app_id", feishuProperties.getAppId(),
                "app_secret", feishuProperties.getAppSecret()
            );
            Map<String, Object> response = restClient.post()
                .uri(feishuProperties.getTenantAccessTokenUrl())
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(MAP_TYPE);
            Map<String, Object> data = unwrapFeishuData(response, "FEISHU_TENANT_TOKEN_FAILED", "获取飞书租户访问凭证失败");
            String token = firstText(
                text(response == null ? null : response.get("tenant_access_token")),
                text(data.get("tenant_access_token"))
            );
            if (!StringUtils.hasText(token)) {
                throw new BizException("FEISHU_TENANT_TOKEN_FAILED", "飞书响应缺少 tenant_access_token");
            }
            Object expireValue = data.containsKey("expire") ? data.get("expire") : response == null ? null : response.get("expire");
            long expireSeconds = numberValue(expireValue, 7200L);
            cachedTenantAccessToken = token;
            cachedTenantAccessTokenExpiresAt = now + Math.max(60L, expireSeconds - 60L) * 1000L;
            return cachedTenantAccessToken;
        }
    }

    private Map<String, Object> unwrapFeishuData(Map<String, Object> response, String code, String defaultMessage) {
        if (response == null) {
            throw new BizException(code, defaultMessage);
        }
        String responseCode = text(response.get("code"));
        if (StringUtils.hasText(responseCode) && !"0".equals(responseCode)) {
            String msg = text(response.get("msg"));
            if (!StringUtils.hasText(msg)) {
                msg = text(response.get("message"));
            }
            throw new BizException(code, StringUtils.hasText(msg) ? msg : defaultMessage);
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

    private SysUser findBoundUser(Map<String, Object> identity) {
        for (String email : identityCandidates(identity, "enterprise_email", "email")) {
            SysUser user = findUserByEmail(email);
            if (user != null) {
                return user;
            }
        }
        for (String mobile : mobileCandidates(text(identity.get("mobile")))) {
            SysUser user = findUserByMobile(mobile);
            if (user != null) {
                return user;
            }
        }
        for (String employeeNo : identityCandidates(identity, "employee_no", "employee_id", "employee_number")) {
            SysUser user = findUserByEmployeeNo(employeeNo);
            if (user != null) {
                return user;
            }
        }
        SysUser feishuUser = findUserByFeishuIdentity(identity);
        if (feishuUser != null) {
            return feishuUser;
        }
        for (String username : identityCandidates(identity, "user_id", "open_id", "union_id")) {
            SysUser user = findUserByUsername(username);
            if (user != null) {
                return user;
            }
        }
        return null;
    }

    private SysUser findUserByFeishuEmailLookup(Map<String, Object> identity) {
        List<FeishuIdCandidate> candidates = feishuIdCandidates(identity);
        if (candidates.isEmpty()) {
            return null;
        }
        List<SysUser> users = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
            .isNotNull(SysUser::getEmail)
            .ne(SysUser::getEmail, ""));
        if (users.isEmpty()) {
            return null;
        }
        for (FeishuIdCandidate candidate : candidates) {
            try {
                SysUser user = findUserByFeishuEmailLookup(users, candidate);
                if (user != null) {
                    return user;
                }
            } catch (RestClientException | BizException ex) {
                log.warn("Failed to resolve Feishu login by local emails and {}, message={}", candidate.idType(), ex.getMessage());
            }
        }
        return null;
    }

    private SysUser findUserByFeishuEmailLookup(List<SysUser> users, FeishuIdCandidate candidate) {
        for (int start = 0; start < users.size(); start += FEISHU_EMAIL_LOOKUP_BATCH_SIZE) {
            List<SysUser> batch = users.subList(start, Math.min(start + FEISHU_EMAIL_LOOKUP_BATCH_SIZE, users.size()));
            List<String> emails = batch.stream()
                .map(SysUser::getEmail)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
            if (emails.isEmpty()) {
                continue;
            }
            Map<String, String> emailToFeishuId = fetchFeishuIdsByEmails(emails, candidate.idType());
            for (SysUser user : batch) {
                String feishuId = emailToFeishuId.get(normalizeEmail(user.getEmail()));
                if (StringUtils.hasText(feishuId) && feishuId.equals(candidate.id())) {
                    return user;
                }
            }
        }
        return null;
    }

    private Map<String, String> fetchFeishuIdsByEmails(List<String> emails, String idType) {
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("emails", emails);
        requestBody.put("include_resigned", true);
        Map<String, Object> response = restClient.post()
            .uri(UriComponentsBuilder.fromUriString(feishuProperties.getUserBatchGetIdUrl())
                .queryParam("user_id_type", idType)
                .build(true)
                .toUri())
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + resolveTenantAccessToken())
            .contentType(MediaType.APPLICATION_JSON)
            .body(requestBody)
            .retrieve()
            .body(MAP_TYPE);
        Map<String, Object> data = unwrapFeishuData(response, "FEISHU_USER_ID_LOOKUP_FAILED", "通过邮箱获取飞书用户 ID 失败");
        Map<String, String> result = new HashMap<>();
        for (Map<String, Object> item : objectList(data.get("user_list"))) {
            String email = normalizeEmail(text(item.get("email")));
            String userId = text(item.get("user_id"));
            if (StringUtils.hasText(email) && StringUtils.hasText(userId)) {
                result.put(email, userId);
            }
        }
        return result;
    }

    private Map<String, Object> enrichFeishuIdentity(Map<String, Object> identity) {
        Map<String, Object> enriched = new HashMap<>(identity);
        for (FeishuIdCandidate candidate : feishuIdCandidates(identity)) {
            try {
                Map<String, Object> detail = fetchFeishuUserDetail(candidate.id(), candidate.idType());
                mergeIdentity(enriched, detail);
                return enriched;
            } catch (RestClientException | BizException ex) {
                log.warn("Failed to enrich Feishu login identity by {}, message={}", candidate.idType(), ex.getMessage());
            }
        }
        return enriched;
    }

    private List<FeishuIdCandidate> feishuIdCandidates(Map<String, Object> identity) {
        List<FeishuIdCandidate> candidates = new ArrayList<>();
        addFeishuIdCandidate(candidates, identity, "open_id");
        addFeishuIdCandidate(candidates, identity, "user_id");
        addFeishuIdCandidate(candidates, identity, "union_id");
        return candidates;
    }

    private void addFeishuIdCandidate(List<FeishuIdCandidate> candidates, Map<String, Object> identity, String idType) {
        String id = text(identity.get(idType));
        if (StringUtils.hasText(id)) {
            candidates.add(new FeishuIdCandidate(id, idType));
        }
    }

    private Map<String, Object> fetchFeishuUserDetail(String id, String idType) {
        String url = feishuUserDetailUrl(id);
        Map<String, Object> response = restClient.get()
            .uri(UriComponentsBuilder.fromUriString(url)
                .queryParam("user_id_type", idType)
                .queryParam("department_id_type", feishuProperties.getDepartmentIdType())
                .build(true)
                .toUri())
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + resolveTenantAccessToken())
            .retrieve()
            .body(MAP_TYPE);
        Map<String, Object> data = unwrapFeishuData(response, "FEISHU_USER_DETAIL_FAILED", "获取飞书用户详情失败");
        Object user = data.get("user");
        return user instanceof Map<?, ?> map ? copyMap(map) : data;
    }

    private String feishuUserDetailUrl(String userId) {
        String url = feishuProperties.getUserDetailUrl();
        if (url.contains("%s")) {
            return String.format(url, userId);
        }
        if (url.contains("{userId}")) {
            return url.replace("{userId}", userId);
        }
        return url.endsWith("/") ? url + userId : url + "/" + userId;
    }

    private void mergeIdentity(Map<String, Object> identity, Map<String, Object> detail) {
        for (Map.Entry<String, Object> entry : detail.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null && !StringUtils.hasText(text(identity.get(entry.getKey())))) {
                identity.put(entry.getKey(), entry.getValue());
            }
        }
        copyAlias(identity, "enterprise_email", "email");
        copyAlias(identity, "employee_no", "employee_id");
        copyAlias(identity, "employee_no", "employee_number");
    }

    private void copyAlias(Map<String, Object> identity, String targetKey, String sourceKey) {
        if (!StringUtils.hasText(text(identity.get(targetKey))) && StringUtils.hasText(text(identity.get(sourceKey)))) {
            identity.put(targetKey, text(identity.get(sourceKey)));
        }
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

    private SysUser findUserByFeishuIdentity(Map<String, Object> identity) {
        String openId = text(identity.get("open_id"));
        String userId = text(identity.get("user_id"));
        String unionId = text(identity.get("union_id"));
        if (!StringUtils.hasText(openId) && !StringUtils.hasText(userId) && !StringUtils.hasText(unionId)) {
            return null;
        }
        return userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
            .and(wrapper -> {
                boolean hasCondition = false;
                if (StringUtils.hasText(openId)) {
                    wrapper.eq(SysUser::getFeishuOpenId, openId);
                    hasCondition = true;
                }
                if (StringUtils.hasText(userId)) {
                    if (hasCondition) {
                        wrapper.or();
                    }
                    wrapper.eq(SysUser::getFeishuUserId, userId);
                    hasCondition = true;
                }
                if (StringUtils.hasText(unionId)) {
                    if (hasCondition) {
                        wrapper.or();
                    }
                    wrapper.eq(SysUser::getFeishuUnionId, unionId);
                }
            })
            .last("LIMIT 1"));
    }

    private void backfillFeishuBinding(SysUser user, Map<String, Object> identity) {
        if (user == null || user.getId() == null) {
            return;
        }
        LambdaUpdateWrapper<SysUser> wrapper = new LambdaUpdateWrapper<SysUser>()
            .eq(SysUser::getId, user.getId());
        boolean changed = false;
        if (!StringUtils.hasText(user.getFeishuOpenId()) && StringUtils.hasText(text(identity.get("open_id")))) {
            wrapper.set(SysUser::getFeishuOpenId, text(identity.get("open_id")));
            changed = true;
        }
        if (!StringUtils.hasText(user.getFeishuUserId()) && StringUtils.hasText(text(identity.get("user_id")))) {
            wrapper.set(SysUser::getFeishuUserId, text(identity.get("user_id")));
            changed = true;
        }
        if (!StringUtils.hasText(user.getFeishuUnionId()) && StringUtils.hasText(text(identity.get("union_id")))) {
            wrapper.set(SysUser::getFeishuUnionId, text(identity.get("union_id")));
            changed = true;
        }
        if (changed) {
            userMapper.update(null, wrapper);
        }
    }

    private String feishuIdentitySummary(Map<String, Object> identity) {
        return Map.of(
            "email", maskEmail(firstText(text(identity.get("enterprise_email")), text(identity.get("email")))),
            "mobile", maskMobile(text(identity.get("mobile"))),
            "employee_no", maskMiddle(firstText(text(identity.get("employee_no")), text(identity.get("employee_id")), text(identity.get("employee_number")))),
            "has_open_id", StringUtils.hasText(text(identity.get("open_id"))),
            "has_user_id", StringUtils.hasText(text(identity.get("user_id"))),
            "has_union_id", StringUtils.hasText(text(identity.get("union_id")))
        ).toString();
    }

    private List<String> identityCandidates(Map<String, Object> identity, String... keys) {
        List<String> candidates = new ArrayList<>();
        for (String key : keys) {
            String value = text(identity.get(key));
            if (StringUtils.hasText(value) && !candidates.contains(value)) {
                candidates.add(value);
            }
        }
        return candidates;
    }

    private List<String> mobileCandidates(String mobile) {
        List<String> candidates = new ArrayList<>();
        if (!StringUtils.hasText(mobile)) {
            return candidates;
        }
        String normalized = mobile.replace(" ", "").replace("-", "").trim();
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
        return userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
            .eq(SysUser::getEmail, email.trim())
            .last("LIMIT 1"));
    }

    private SysUser findUserByMobile(String mobile) {
        return userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
            .eq(SysUser::getMobile, mobile.trim())
            .last("LIMIT 1"));
    }

    private SysUser findUserByEmployeeNo(String employeeNo) {
        return userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
            .eq(SysUser::getEmployeeNo, employeeNo.trim())
            .last("LIMIT 1"));
    }

    private SysUser findUserByUsername(String username) {
        return userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
            .eq(SysUser::getUsername, username.trim())
            .last("LIMIT 1"));
    }

    private List<SysUser> findLoginCandidates(String identity) {
        String keyword = identity == null ? "" : identity.trim();
        if (!StringUtils.hasText(keyword)) {
            return List.of();
        }
        List<SysUser> users = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
            .and(wrapper -> wrapper
                .eq(SysUser::getUsername, keyword)
                .or()
                .eq(SysUser::getEmployeeNo, keyword)
                .or()
                .eq(SysUser::getRealName, keyword))
            .orderByAsc(SysUser::getId));
        Map<Long, SysUser> uniqueUsers = new LinkedHashMap<>();
        for (SysUser user : users) {
            if (user != null && user.getId() != null) {
                uniqueUsers.putIfAbsent(user.getId(), user);
            }
        }
        return new ArrayList<>(uniqueUsers.values());
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

    private String normalizeEmail(String email) {
        return StringUtils.hasText(email) ? email.trim().toLowerCase() : "";
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private String maskEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return "";
        }
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***" + (at >= 0 ? email.substring(at) : "");
        }
        return email.charAt(0) + "***" + email.substring(at);
    }

    private String maskMobile(String mobile) {
        if (!StringUtils.hasText(mobile)) {
            return "";
        }
        String normalized = mobile.replace(" ", "").replace("-", "");
        return normalized.length() <= 4 ? "***" : "***" + normalized.substring(normalized.length() - 4);
    }

    private String maskMiddle(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() <= 2 ? "***" : trimmed.charAt(0) + "***" + trimmed.charAt(trimmed.length() - 1);
    }

    private AuthUserVO toAuthUserVO(AuthUser authUser) {
        SysUser user = authUser.getUserId() == null ? null : userMapper.selectById(authUser.getUserId());
        return toAuthUserVO(authUser, user);
    }

    private AuthUserVO toAuthUserVO(AuthUser authUser, SysUser user) {
        AuthUserVO vo = new AuthUserVO();
        vo.setUserId(authUser.getUserId());
        vo.setUsername(authUser.getUsername());
        vo.setRealName(authUser.getRealName());
        vo.setEmployeeNo(authUser.getEmployeeNo());
        vo.setUserType(authUser.getUserType());
        vo.setRegionId(authUser.getRegionId());
        vo.setDeptCode(authUser.getDeptCode());
        vo.setDeptName(authUser.getDeptName());
        if (user != null) {
            if (!StringUtils.hasText(vo.getEmployeeNo())) {
                vo.setEmployeeNo(user.getEmployeeNo());
            }
            if (!StringUtils.hasText(vo.getDeptCode())) {
                vo.setDeptCode(user.getDeptCode());
            }
            if (!StringUtils.hasText(vo.getDeptName())) {
                vo.setDeptName(user.getDeptName());
            }
        }
        vo.setSupplierId(authUser.getSupplierId());
        vo.setDataScopeType(authUser.getDataScopeType());
        vo.setRoleCodes(authUser.getRoleCodes());
        vo.setPermissionCodes(authUser.getPermissionCodes());
        vo.setAppCodes(authUser.getAppCodes());
        vo.setMustChangePassword(user == null ? authUser.getMustChangePassword() : user.getMustChangePassword());
        vo.setPasswordChangedAt(user == null ? authUser.getPasswordChangedAt() : user.getPasswordChangedAt());
        vo.setPasswordExpiresAt(passwordExpiresAt(user, authUser));
        vo.setPasswordExpired(passwordExpired(user, authUser));
        vo.setPasswordExpireDays(PASSWORD_EXPIRE_DAYS);
        return vo;
    }

    private LocalDateTime passwordExpiresAt(SysUser user, AuthUser authUser) {
        LocalDateTime changedAt = user == null ? authUser.getPasswordChangedAt() : user.getPasswordChangedAt();
        return changedAt == null ? null : changedAt.plusDays(PASSWORD_EXPIRE_DAYS);
    }

    private boolean passwordExpired(SysUser user, AuthUser authUser) {
        Integer mustChangePassword = user == null ? authUser.getMustChangePassword() : user.getMustChangePassword();
        if (Integer.valueOf(1).equals(mustChangePassword)) {
            return true;
        }
        LocalDateTime expiresAt = passwordExpiresAt(user, authUser);
        return expiresAt == null || !expiresAt.isAfter(LocalDateTime.now());
    }

    private AuthLoginCandidateVO toLoginCandidateVO(SysUser user) {
        AuthLoginCandidateVO vo = new AuthLoginCandidateVO();
        vo.setUserId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        vo.setEmployeeNo(user.getEmployeeNo());
        vo.setDeptName(user.getDeptName());
        return vo;
    }

    private record LoginFailure(int count, long lastFailedAt, long lockedUntil) {
    }

    private record FeishuIdCandidate(String id, String idType) {
    }
}
