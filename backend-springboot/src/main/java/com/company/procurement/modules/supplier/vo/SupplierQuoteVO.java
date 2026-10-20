package com.company.procurement.modules.supplier.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class SupplierQuoteVO {

    private Long id;
    private String quoteNo;
    private Long purchaseRequestId;
    private Long supplierId;
    private String supplierName;
    private Long supplierUserId;
    private String quoteSource;
    private Long recommendationId;
    private Boolean awardEligible;
    private String awardEligibilityText;
    private String receivingCity;
    private BigDecimal totalAmount;
    private Integer leadTimeDays;
    private String quoteRemark;
    private String status;
    private Boolean selected;
    private String supplierConfirmStatus;
    private String supplierConfirmRemark;
    private Long supplierConfirmedBy;
    private LocalDateTime supplierConfirmedAt;
    private LocalDateTime supplierConfirmDueAt;
    private Boolean supplierBiddingSuspended;
    private LocalDateTime supplierBidSuspendedUntil;
    private Boolean supplierBanned;
    private String supplierBanReason;
    private LocalDateTime submittedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<SupplierQuoteItemVO> items;
}
