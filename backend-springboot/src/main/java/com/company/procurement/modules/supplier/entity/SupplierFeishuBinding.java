package com.company.procurement.modules.supplier.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.procurement.common.entity.BaseEntity;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("supplier_feishu_binding")
public class SupplierFeishuBinding extends BaseEntity {

    public static final String STATUS_BOUND = "BOUND";
    public static final String STATUS_DISABLED = "DISABLED";

    @TableField("supplier_id")
    private Long supplierId;

    @TableField("supplier_code")
    private String supplierCode;

    @TableField("supplier_name")
    private String supplierName;

    @TableField("bind_status")
    private String bindStatus;

    @TableField("receive_id_type")
    private String receiveIdType;

    @TableField("receive_id")
    private String receiveId;

    @TableField("open_id")
    private String openId;

    @TableField("user_id")
    private String userId;

    @TableField("union_id")
    private String unionId;

    @TableField("chat_id")
    private String chatId;

    @TableField("chat_type")
    private String chatType;

    @TableField("tenant_key")
    private String tenantKey;

    @TableField("source_user_key")
    private String sourceUserKey;

    @TableField("last_message_id")
    private String lastMessageId;

    @TableField("bound_at")
    private LocalDateTime boundAt;

    @TableField("disabled_at")
    private LocalDateTime disabledAt;

    @TableField("remark")
    private String remark;
}
