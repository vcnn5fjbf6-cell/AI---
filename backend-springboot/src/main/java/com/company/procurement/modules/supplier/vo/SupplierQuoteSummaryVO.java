package com.company.procurement.modules.supplier.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class SupplierQuoteSummaryVO {

    private Long purchaseRequestId;
    private String awardStrategy;
    private int supplierQuoteCount;
    private BigDecimal lowestAmount;
    private Long lowestSupplierId;
    private String lowestSupplierName;
    private Long selectedQuoteId;
    private Long selectedSupplierId;
    private String selectedSupplierName;
    private Boolean quoteDetailsVisible;
    private String leaderConfirmStatus;
    private String leaderConfirmRemark;
    private Long leaderConfirmedBy;
    private java.time.LocalDateTime leaderConfirmedAt;
    private List<SupplierQuoteVO> quotes;
}
