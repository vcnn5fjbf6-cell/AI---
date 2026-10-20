package com.company.procurement.modules.ehs.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.procurement.common.entity.BaseEntity;
import lombok.Data;

@Data
@TableName("ehs_ai_session")
public class EhsAiSession extends BaseEntity {
    @TableField("user_id")
    private Long userId;
    @TableField("title")
    private String title;
    @TableField("status")
    private String status;
}
