package com.company.procurement.modules.procurement.order.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.procurement.common.entity.BaseEntity;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("order_invoice_submission")
public class OrderInvoiceSubmission extends BaseEntity {

    @TableField("quote_item_id")
    private Long quoteItemId;

    @TableField("purchase_request_id")
    private Long purchaseRequestId;

    @TableField("supplier_id")
    private Long supplierId;

    @TableField("document_type")
    private String documentType;

    @TableField("version_no")
    private Integer versionNo;

    @TableField("status")
    private String status;

    @TableField("invoice_no")
    private String invoiceNo;

    @TableField("invoice_date")
    private LocalDate invoiceDate;

    @TableField("total_amount")
    private BigDecimal totalAmount;

    @TableField("invoice_type")
    private String invoiceType;

    @TableField("remark")
    private String remark;

    @TableField("attachment_count")
    private Integer attachmentCount;

    @TableField("submitted_by")
    private Long submittedBy;

    @TableField("submitted_by_name")
    private String submittedByName;

    @TableField("submitted_by_role")
    private String submittedByRole;

    @TableField("submitted_at")
    private LocalDateTime submittedAt;

    @TableField("reviewed_by")
    private Long reviewedBy;

    @TableField("reviewed_by_name")
    private String reviewedByName;

    @TableField("reviewed_at")
    private LocalDateTime reviewedAt;

    @TableField("review_remark")
    private String reviewRemark;

    @TableField("supersedes_submission_id")
    private Long supersedesSubmissionId;
}
