package com.company.procurement.modules.supplier.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class SupplierQuoteRequestVO {

    private Long id;
    private String reqNo;
    private String reqTitle;
    private String reqType;
    private Long regionId;
    private String deptName;
    private String receivingCity;
    private BigDecimal requestAmount;
    private LocalDate needDate;
    private String awardStrategy;
    private String status;
    private String approvalStatus;
    private BigDecimal myQuoteAmount;
    private Integer myLeadTimeDays;
    private String myQuoteStatus;
    private LocalDateTime mySubmittedAt;
    private Boolean myAwarded;
    private String mySupplierConfirmStatus;
    private LocalDateTime mySupplierConfirmDueAt;
    private Boolean supplierBiddingSuspended;
    private LocalDateTime supplierBidSuspendedUntil;
    private Boolean supplierBanned;
    private String supplierBanReason;
    private String internalProductCodeRule;
    private String leaderConfirmStatus;
    private LocalDateTime quoteCountdownStartedAt;
    private LocalDateTime quoteCountdownDueAt;
    private Boolean directAwardApplied;
    private Long directAwardSupplierId;
    private String directAwardSupplierName;
    private BigDecimal directAwardThresholdAmount;
    private String directAwardReason;
}
