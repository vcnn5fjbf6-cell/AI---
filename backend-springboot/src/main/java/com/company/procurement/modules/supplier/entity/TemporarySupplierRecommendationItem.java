package com.company.procurement.modules.supplier.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.procurement.common.entity.BaseEntity;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("temporary_supplier_recommendation_item")
public class TemporarySupplierRecommendationItem extends BaseEntity {

    @TableField("recommendation_id")
    private Long recommendationId;

    @TableField("purchase_request_id")
    private Long purchaseRequestId;

    @TableField("purchase_request_item_id")
    private Long purchaseRequestItemId;

    @TableField("line_no")
    private Integer lineNo;

    @TableField("item_code")
    private String itemCode;

    @TableField("req_type")
    private String reqType;

    @TableField("item_name")
    private String itemName;

    @TableField("spec_model")
    private String specModel;

    @TableField("unit")
    private String unit;

    @TableField("qty")
    private BigDecimal qty;

    @TableField("purpose")
    private String purpose;

    @TableField("status")
    private String status;
}
