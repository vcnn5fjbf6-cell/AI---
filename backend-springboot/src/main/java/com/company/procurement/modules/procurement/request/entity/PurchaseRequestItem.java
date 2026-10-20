package com.company.procurement.modules.procurement.request.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.procurement.common.entity.BaseEntity;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@TableName("purchase_request_item")
public class PurchaseRequestItem extends BaseEntity {

    @TableField("purchase_request_id")
    private Long purchaseRequestId;

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

    @TableField("item_brand")
    private String itemBrand;

    @TableField("unit")
    private String unit;

    @TableField("qty")
    private BigDecimal qty;

    @TableField("unit_price")
    private BigDecimal unitPrice;

    @TableField("reference_unit_price")
    private BigDecimal referenceUnitPrice;

    @TableField("tax_rate")
    private BigDecimal taxRate;

    @TableField("line_amount")
    private BigDecimal lineAmount;

    @TableField("reference_line_amount")
    private BigDecimal referenceLineAmount;

    @TableField("need_date")
    private LocalDate needDate;

    @TableField("purpose")
    private String purpose;

    @TableField("ecommerce_url")
    private String ecommerceUrl;

    @TableField("receiving_city")
    private String receivingCity;

    @TableField("delivery_address")
    private String deliveryAddress;

    @TableField("source_request_id")
    private Long sourceRequestId;

    @TableField("source_request_item_id")
    private Long sourceRequestItemId;

    @TableField("status")
    private String status;

    @TableField("remark")
    private String remark;
}
