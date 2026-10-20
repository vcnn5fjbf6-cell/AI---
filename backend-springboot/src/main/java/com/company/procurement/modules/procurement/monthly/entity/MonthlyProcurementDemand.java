package com.company.procurement.modules.procurement.monthly.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.procurement.common.entity.BaseEntity;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@TableName("monthly_procurement_demand")
public class MonthlyProcurementDemand extends BaseEntity {

    @TableField("demand_no")
    private String demandNo;

    @TableField("plan_month")
    private String planMonth;

    @TableField("applicant_user_id")
    private Long applicantUserId;

    @TableField("region_id")
    private Long regionId;

    @TableField("dept_code")
    private String deptCode;

    @TableField("dept_name")
    private String deptName;

    @TableField("budget_code")
    private String budgetCode;

    @TableField("req_type")
    private String reqType;

    @TableField("urgency_level")
    private Integer urgencyLevel;

    @TableField("emergency_flag")
    private Integer emergencyFlag;

    @TableField("req_title")
    private String reqTitle;

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

    @TableField("tax_rate")
    private BigDecimal taxRate;

    @TableField("line_amount")
    private BigDecimal lineAmount;

    @TableField("need_date")
    private LocalDate needDate;

    @TableField("purchase_reason")
    private String purchaseReason;

    @TableField("ecommerce_url")
    private String ecommerceUrl;

    @TableField("source_channel")
    private String sourceChannel;

    @TableField("status")
    private String status;

    @TableField("purchase_request_id")
    private Long purchaseRequestId;

    @TableField("remark")
    private String remark;
}
