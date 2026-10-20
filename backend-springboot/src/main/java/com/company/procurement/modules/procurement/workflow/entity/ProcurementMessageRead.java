package com.company.procurement.modules.procurement.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.procurement.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("procurement_message_read")
public class ProcurementMessageRead extends BaseEntity {

    public static final String ACTION_READ = "READ";
    public static final String ACTION_CLEARED = "CLEARED";

    @TableField("user_id")
    private Long userId;

    @TableField("message_key")
    private String messageKey;

    @TableField("action_type")
    private String actionType;
}
