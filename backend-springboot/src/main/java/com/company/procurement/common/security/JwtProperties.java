package com.company.procurement.common.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "auth.jwt")
public class JwtProperties {

    private String secret;

    private String issuer;

    private Long expireSeconds = 7200L;

    private String headerName = "Authorization";

    private String tokenPrefix = "Bearer";

    private Long clockSkewSeconds = 60L;
}
