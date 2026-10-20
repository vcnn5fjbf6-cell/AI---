package com.company.procurement.modules.supplier.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.procurement.common.entity.BaseEntity;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("supplier_quote_item")
public class SupplierQuoteItem extends BaseEntity {

    @TableField("quote_id")
    private Long quoteId;

    @TableField("purchase_request_id")
    private Long purchaseRequestId;

    @TableField("purchase_request_item_id")
    private Long purchaseRequestItemId;

    @TableField("line_no")
    private Integer lineNo;

    @TableField("item_code")
    private String itemCode;

    @TableField("item_name")
    private String itemName;

    @TableField("spec_model")
    private String specModel;

    @TableField("unit")
    private String unit;

    @TableField("qty")
    private BigDecimal qty;

    @TableField("unit_price")
    private BigDecimal unitPrice;

    @TableField("supplier_submitted_unit_price")
    private BigDecimal supplierSubmittedUnitPrice;

    @TableField("line_amount")
    private BigDecimal lineAmount;

    @TableField("lead_time_days")
    private Integer leadTimeDays;

    @TableField("original_condition")
    private String originalCondition;

    @TableField("original_second_hand_remark")
    private String originalSecondHandRemark;

    @TableField("quote_remark")
    private String quoteRemark;

    @TableField("receiving_city")
    private String receivingCity;

    @TableField("delivery_address")
    private String deliveryAddress;

    @TableField("replacement_flag")
    private Integer replacementFlag;

    @TableField("replacement_item_name")
    private String replacementItemName;

    @TableField("replacement_spec_model")
    private String replacementSpecModel;

    @TableField("replacement_ecommerce_url")
    private String replacementEcommerceUrl;

    @TableField("replacement_unit_price")
    private BigDecimal replacementUnitPrice;

    @TableField("replacement_line_amount")
    private BigDecimal replacementLineAmount;

    @TableField("replacement_reason")
    private String replacementReason;

    @TableField("replacement_condition")
    private String replacementCondition;

    @TableField("replacement_second_hand_remark")
    private String replacementSecondHandRemark;

    @TableField("asset_review_status")
    private String assetReviewStatus;

    @TableField("asset_review_result_json")
    private String assetReviewResultJson;

    @TableField("asset_review_message")
    private String assetReviewMessage;

    @TableField("original_asset_category_code")
    private String originalAssetCategoryCode;

    @TableField("original_asset_name_code")
    private String originalAssetNameCode;

    @TableField("original_asset_catalog_name")
    private String originalAssetCatalogName;

    @TableField("original_asset_classification")
    private String originalAssetClassification;

    @TableField("original_special_approval_required")
    private Integer originalSpecialApprovalRequired;

    @TableField("replacement_asset_category_code")
    private String replacementAssetCategoryCode;

    @TableField("replacement_asset_name_code")
    private String replacementAssetNameCode;

    @TableField("replacement_asset_catalog_name")
    private String replacementAssetCatalogName;

    @TableField("replacement_asset_classification")
    private String replacementAssetClassification;

    @TableField("replacement_special_approval_required")
    private Integer replacementSpecialApprovalRequired;

    @TableField("condition_mismatch")
    private Integer conditionMismatch;

    @TableField("asset_reviewed_at")
    private LocalDateTime assetReviewedAt;

    @TableField("replacement_review_status")
    private String replacementReviewStatus;

    @TableField("replacement_reviewed_by")
    private Long replacementReviewedBy;

    @TableField("replacement_reviewed_at")
    private LocalDateTime replacementReviewedAt;

    @TableField("replacement_review_remark")
    private String replacementReviewRemark;

    @TableField("awarded")
    private Integer awarded;

    @TableField("receipt_status")
    private String receiptStatus;

    @TableField("receipt_due_at")
    private LocalDateTime receiptDueAt;

    @TableField("received_by")
    private Long receivedBy;

    @TableField("received_at")
    private LocalDateTime receivedAt;

    @TableField("receipt_remark")
    private String receiptRemark;

    @TableField("auto_received")
    private Integer autoReceived;

    @TableField("status")
    private String status;
}
