package com.company.procurement.modules.procurement.order.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class OrderInvoiceSummaryVO {

    private Long submissionId;
    private String documentType;
    private String status;
    private Integer versionNo;
    private String invoiceNo;
    private LocalDate invoiceDate;
    private BigDecimal totalAmount;
    private String invoiceType;
    private Integer attachmentCount;
    private LocalDateTime submittedAt;
    private String submittedByName;
    private String submittedByRole;
    private LocalDateTime reviewedAt;
    private String reviewedByName;
    private String reviewRemark;
}
