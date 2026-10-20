package com.company.procurement.modules.system.user.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.procurement.common.entity.BaseEntity;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_user_password_history")
public class SysUserPasswordHistory extends BaseEntity {

    @TableField("user_id")
    private Long userId;

    @TableField("password_hash")
    private String passwordHash;

    @TableField("changed_at")
    private LocalDateTime changedAt;
}
