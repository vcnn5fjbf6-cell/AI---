package com.company.procurement.modules.supplier.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.procurement.common.entity.BaseEntity;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("supplier")
public class Supplier extends BaseEntity {

    @TableField("supplier_code")
    private String supplierCode;

    @TableField("supplier_name")
    private String supplierName;

    @TableField("supplier_short_name")
    private String supplierShortName;

    @TableField("tax_no")
    private String taxNo;

    @TableField("supplier_type")
    private String supplierType;

    @TableField("region_id")
    private Long regionId;

    @TableField("contact_name")
    private String contactName;

    @TableField("contact_phone")
    private String contactPhone;

    @TableField("contact_email")
    private String contactEmail;

    @TableField("address")
    private String address;

    @TableField("bank_name")
    private String bankName;

    @TableField("bank_account")
    private String bankAccount;

    @TableField("invoice_title")
    private String invoiceTitle;

    @TableField("default_payment_term")
    private String defaultPaymentTerm;

    @TableField("rating_level")
    private String ratingLevel;

    @TableField("onboarding_status")
    private String onboardingStatus;

    @TableField("status")
    private String status;

    @TableField("blacklist_flag")
    private Integer blacklistFlag;

    @TableField("data_isolation_flag")
    private Integer dataIsolationFlag;

    @TableField("blacklist_reason")
    private String blacklistReason;

    @TableField("bid_suspended_until")
    private LocalDateTime bidSuspendedUntil;

    @TableField("bid_suspended_reason")
    private String bidSuspendedReason;

    @TableField("entry_date")
    private LocalDate entryDate;

    @TableField("remark")
    private String remark;
}
