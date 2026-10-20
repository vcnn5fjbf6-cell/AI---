package com.company.procurement.modules.supplier.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.procurement.common.entity.BaseEntity;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("supplier_feishu_quote_notice")
public class SupplierFeishuQuoteNotice extends BaseEntity {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_SENT = "SENT";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_SKIPPED = "SKIPPED";

    @TableField("purchase_request_id")
    private Long purchaseRequestId;

    @TableField("request_no")
    private String requestNo;

    @TableField("supplier_id")
    private Long supplierId;

    @TableField("notice_type")
    private String noticeType;

    @TableField("binding_id")
    private Long bindingId;

    @TableField("receive_id_type")
    private String receiveIdType;

    @TableField("receive_id")
    private String receiveId;

    @TableField("status")
    private String status;

    @TableField("message_id")
    private String messageId;

    @TableField("last_error")
    private String lastError;

    @TableField("sent_at")
    private LocalDateTime sentAt;

    @TableField("retry_count")
    private Integer retryCount;

    @TableField("card_version")
    private String cardVersion;
}
