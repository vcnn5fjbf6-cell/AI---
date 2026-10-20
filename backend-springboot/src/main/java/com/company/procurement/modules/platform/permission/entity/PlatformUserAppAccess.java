package com.company.procurement.modules.platform.permission.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.procurement.common.entity.BaseEntity;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("platform_user_app_access")
public class PlatformUserAppAccess extends BaseEntity {

    @TableField("user_id")
    private Long userId;

    @TableField("app_code")
    private String appCode;

    @TableField("status")
    private Integer status;

    @TableField("granted_by")
    private Long grantedBy;

    @TableField("granted_at")
    private LocalDateTime grantedAt;

    @TableField("remark")
    private String remark;
}
