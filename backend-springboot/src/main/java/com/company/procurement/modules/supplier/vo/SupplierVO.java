package com.company.procurement.modules.supplier.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class SupplierVO {

    private Long id;
    private String supplierCode;
    private String supplierName;
    private String supplierShortName;
    private String taxNo;
    private String supplierType;
    private Long regionId;
    private String contactName;
    private String contactPhone;
    private String contactEmail;
    private String address;
    private String bankName;
    private String bankAccount;
    private String invoiceTitle;
    private String defaultPaymentTerm;
    private String ratingLevel;
    private String onboardingStatus;
    private String status;
    private Integer blacklistFlag;
    private Integer dataIsolationFlag;
    private String blacklistReason;
    private LocalDateTime bidSuspendedUntil;
    private String bidSuspendedReason;
    private LocalDate entryDate;
    private String remark;
    private Long qualificationCount;
    private Long linkedUserCount;
    private List<String> linkedUsernames;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
