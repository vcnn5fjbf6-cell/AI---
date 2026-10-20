package com.company.procurement.modules.ehs.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.company.procurement.common.exception.BizException;
import com.company.procurement.modules.ehs.entity.EhsPublicAccessToken;
import com.company.procurement.modules.ehs.mapper.EhsPublicAccessTokenMapper;
import com.company.procurement.modules.integration.feishu.config.FeishuApprovalCardRuntimeConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class EhsPublicAccessService {

    public static final String TYPE_APPLICANT = "APPLICANT";
    public static final String TYPE_APPROVER = "APPROVER";

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final EhsPublicAccessTokenMapper tokenMapper;
    private final FeishuApprovalCardRuntimeConfig feishuRuntimeConfig;

    @Transactional(rollbackFor = Exception.class)
    public IssuedToken issueApplicantToken(Long permitId) {
        return issue(permitId, TYPE_APPLICANT, null, LocalDateTime.now().plusDays(180));
    }

    @Transactional(rollbackFor = Exception.class)
    public IssuedToken issueApproverToken(Long permitId, Long userId) {
        revokeApproverTokens(permitId, userId);
        return issue(permitId, TYPE_APPROVER, userId, LocalDateTime.now().plusDays(7));
    }

    public EhsPublicAccessToken requireApplicant(String rawToken) {
        return require(rawToken, TYPE_APPLICANT);
    }

    public EhsPublicAccessToken requireApprover(String rawToken) {
        return require(rawToken, TYPE_APPROVER);
    }

    @Transactional(rollbackFor = Exception.class)
    public void markUsed(Long tokenId) {
        if (tokenId == null) {
            return;
        }
        tokenMapper.update(null, new LambdaUpdateWrapper<EhsPublicAccessToken>()
            .eq(EhsPublicAccessToken::getId, tokenId)
            .set(EhsPublicAccessToken::getUsedAt, LocalDateTime.now())
            .set(EhsPublicAccessToken::getRevokedAt, LocalDateTime.now()));
    }

    @Transactional(rollbackFor = Exception.class)
    public void revokeApproverTokens(Long permitId) {
        revokeApproverTokens(permitId, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public void revokeApproverTokens(Long permitId, Long userId) {
        if (permitId == null) {
            return;
        }
        LambdaUpdateWrapper<EhsPublicAccessToken> wrapper = new LambdaUpdateWrapper<EhsPublicAccessToken>()
            .eq(EhsPublicAccessToken::getPermitId, permitId)
            .eq(EhsPublicAccessToken::getTokenType, TYPE_APPROVER)
            .isNull(EhsPublicAccessToken::getRevokedAt)
            .set(EhsPublicAccessToken::getRevokedAt, LocalDateTime.now());
        if (userId != null) {
            wrapper.eq(EhsPublicAccessToken::getUserId, userId);
        }
        tokenMapper.update(null, wrapper);
    }

    public String reviewUrl(String rawToken) {
        String baseUrl = feishuRuntimeConfig.current().appBaseUrl();
        if (!StringUtils.hasText(baseUrl) || !StringUtils.hasText(rawToken)) {
            return "";
        }
        return baseUrl.trim().replaceAll("/+$", "") + "/ehs-public/review#token=" + rawToken;
    }

    private IssuedToken issue(Long permitId, String tokenType, Long userId, LocalDateTime expiresAt) {
        if (permitId == null) {
            throw new BizException("EHS_PUBLIC_TOKEN_INVALID", "作业票不存在");
        }
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        EhsPublicAccessToken token = new EhsPublicAccessToken();
        token.setPermitId(permitId);
        token.setTokenHash(hash(rawToken));
        token.setTokenType(tokenType);
        token.setUserId(userId);
        token.setExpiresAt(expiresAt);
        tokenMapper.insert(token);
        return new IssuedToken(token.getId(), rawToken, expiresAt);
    }

    private EhsPublicAccessToken require(String rawToken, String tokenType) {
        if (!StringUtils.hasText(rawToken) || rawToken.length() < 40 || rawToken.length() > 128) {
            throw invalidToken();
        }
        EhsPublicAccessToken token = tokenMapper.selectOne(new LambdaQueryWrapper<EhsPublicAccessToken>()
            .eq(EhsPublicAccessToken::getTokenHash, hash(rawToken.trim()))
            .eq(EhsPublicAccessToken::getTokenType, tokenType)
            .isNull(EhsPublicAccessToken::getRevokedAt)
            .gt(EhsPublicAccessToken::getExpiresAt, LocalDateTime.now())
            .last("LIMIT 1"));
        if (token == null) {
            throw invalidToken();
        }
        return token;
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private BizException invalidToken() {
        return new BizException("EHS_PUBLIC_TOKEN_INVALID", "访问链接无效或已过期");
    }

    public record IssuedToken(Long id, String rawToken, LocalDateTime expiresAt) {
    }
}
