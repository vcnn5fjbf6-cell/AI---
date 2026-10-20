package com.company.procurement.modules.supplier.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.procurement.common.entity.BaseEntity;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("supplier_quote")
public class SupplierQuote extends BaseEntity {

    @TableField("quote_no")
    private String quoteNo;

    @TableField("purchase_request_id")
    private Long purchaseRequestId;

    @TableField("supplier_id")
    private Long supplierId;

    @TableField("supplier_user_id")
    private Long supplierUserId;

    @TableField("quote_source")
    private String quoteSource;

    @TableField("recommendation_id")
    private Long recommendationId;

    @TableField("total_amount")
    private BigDecimal totalAmount;

    @TableField("lead_time_days")
    private Integer leadTimeDays;

    @TableField("quote_remark")
    private String quoteRemark;

    @TableField("status")
    private String status;

    @TableField("supplier_confirm_status")
    private String supplierConfirmStatus;

    @TableField("supplier_confirm_remark")
    private String supplierConfirmRemark;

    @TableField("supplier_confirmed_by")
    private Long supplierConfirmedBy;

    @TableField("supplier_confirmed_at")
    private LocalDateTime supplierConfirmedAt;

    @TableField("supplier_confirm_due_at")
    private LocalDateTime supplierConfirmDueAt;

    @TableField("supplier_confirm_notified_at")
    private LocalDateTime supplierConfirmNotifiedAt;

    @TableField("submitted_at")
    private LocalDateTime submittedAt;
}
