package com.company.procurement.modules.procurement.order.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class AfterSaleVO {

    private Long id;
    private String afterSaleNo;
    private Long purchaseRequestId;
    private Long purchaseRequestItemId;
    private Long quoteId;
    private Long quoteItemId;
    private Long supplierId;
    private String supplierName;
    private Long requesterUserId;
    private String requesterName;
    private String afterSaleType;
    private String reason;
    private BigDecimal requestedQty;
    private String replacementItemCode;
    private String replacementItemName;
    private String replacementSpecModel;
    private String replacementUnit;
    private String replacementRemark;
    private String supplierProposalType;
    private String supplierProposalRemark;
    private String supplierReplacementItemCode;
    private String supplierReplacementItemName;
    private String supplierReplacementSpecModel;
    private String supplierReplacementUnit;
    private BigDecimal supplierReplacementUnitPrice;
    private BigDecimal supplierReplacementLineAmount;
    private LocalDateTime supplierProposedAt;
    private Long requesterConfirmUserId;
    private String requesterConfirmName;
    private LocalDateTime requesterConfirmedAt;
    private String requesterConfirmRemark;
    private String status;
    private Long approverUserId;
    private String approverName;
    private LocalDateTime approvedAt;
    private String approvalRemark;
    private String orderNo;
    private String reqNo;
    private String reqTitle;
    private String itemCode;
    private String itemName;
    private String specModel;
    private String unit;
    private BigDecimal orderQty;
    private BigDecimal orderAmount;
    private String receivingCity;
    private String deliveryAddress;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
