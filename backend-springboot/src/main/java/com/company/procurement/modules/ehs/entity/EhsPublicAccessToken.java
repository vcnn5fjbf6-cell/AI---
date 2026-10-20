package com.company.procurement.modules.ehs.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.procurement.common.entity.BaseEntity;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ehs_public_access_token")
public class EhsPublicAccessToken extends BaseEntity {

    @TableField("permit_id")
    private Long permitId;

    @TableField("token_hash")
    private String tokenHash;

    @TableField("token_type")
    private String tokenType;

    @TableField("user_id")
    private Long userId;

    @TableField("expires_at")
    private LocalDateTime expiresAt;

    @TableField("used_at")
    private LocalDateTime usedAt;

    @TableField("revoked_at")
    private LocalDateTime revokedAt;
}
