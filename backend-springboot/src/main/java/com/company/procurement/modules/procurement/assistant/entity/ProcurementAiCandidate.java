package com.company.procurement.modules.procurement.assistant.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.procurement.common.entity.BaseEntity;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("procurement_ai_candidate")
public class ProcurementAiCandidate extends BaseEntity {

    @TableField("session_id")
    private Long sessionId;

    @TableField("candidate_no")
    private Integer candidateNo;

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

    @TableField("tax_rate")
    private BigDecimal taxRate;

    @TableField("line_amount")
    private BigDecimal lineAmount;

    @TableField("req_type")
    private String reqType;

    @TableField("purchase_reason")
    private String purchaseReason;

    @TableField("ecommerce_platform")
    private String ecommercePlatform;

    @TableField("ecommerce_url")
    private String ecommerceUrl;

    @TableField("image_url")
    private String imageUrl;

    @TableField("score")
    private BigDecimal score;

    @TableField("selected_flag")
    private Integer selectedFlag;

    @TableField("source_json")
    private String sourceJson;

    @TableField("status")
    private String status;

    @TableField("remark")
    private String remark;
}
