package com.company.procurement.modules.ehs.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class EhsPublicPermitCreatedVO {
    private Long permitId;
    private String permitNo;
    private String accessToken;
    private LocalDateTime accessExpiresAt;
}
