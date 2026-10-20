package com.company.procurement.common.security;

import com.company.procurement.common.exception.BizException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private final JwtProperties jwtProperties;

    private SecretKey secretKey;

    @PostConstruct
    public void init() {
        if (!StringUtils.hasText(jwtProperties.getSecret())) {
            throw new IllegalStateException("JWT secret must not be empty");
        }
        byte[] bytes = jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("JWT secret must be at least 32 bytes");
        }
        this.secretKey = Keys.hmacShaKeyFor(bytes);
    }

    public String generateToken(AuthUser authUser, String sessionSecret) {
        if (!StringUtils.hasText(sessionSecret)) {
            throw new IllegalArgumentException("JWT session secret must not be empty");
        }
        Instant now = Instant.now();
        Instant expireAt = now.plusSeconds(jwtProperties.getExpireSeconds());
        return Jwts.builder()
            .issuer(jwtProperties.getIssuer())
            .subject(authUser.getUsername())
            .issuedAt(Date.from(now))
            .expiration(Date.from(expireAt))
            .claim("uid", authUser.getUserId())
            .claim("uname", authUser.getUsername())
            .claim("sid", sessionSecret)
            .signWith(secretKey)
            .compact();
    }

    public JwtTokenIdentity parseToken(String token) {
        try {
            Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .requireIssuer(jwtProperties.getIssuer())
                .clockSkewSeconds(jwtProperties.getClockSkewSeconds())
                .build()
                .parseSignedClaims(token)
                .getPayload();

            Long userId = toLong(claims.get("uid"));
            String sessionSecret = claims.get("sid", String.class);
            if (userId == null || !StringUtils.hasText(sessionSecret)) {
                throw new BizException("UNAUTHORIZED", "Invalid session, please login again");
            }
            return new JwtTokenIdentity(userId, claims.get("uname", String.class), sessionSecret);
        } catch (JwtException | IllegalArgumentException ex) {
            throw new BizException("UNAUTHORIZED", "Invalid session, please login again");
        }
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }
}
