package com.company.procurement.modules.ehs.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.procurement.common.entity.BaseEntity;
import lombok.Data;

@Data
@TableName("ehs_ai_message")
public class EhsAiMessage extends BaseEntity {
    @TableField("session_id")
    private Long sessionId;
    @TableField("role")
    private String role;
    @TableField("content")
    private String content;
    @TableField("source_refs_json")
    private String sourceRefsJson;
}
