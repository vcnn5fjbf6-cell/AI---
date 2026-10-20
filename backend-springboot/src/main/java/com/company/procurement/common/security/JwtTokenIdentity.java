package com.company.procurement.common.security;

public record JwtTokenIdentity(Long userId, String username, String sessionSecret) {
}
