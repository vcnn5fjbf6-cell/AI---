package com.company.procurement.modules.supplier.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SupplierFeishuQuoteNoticeVO {

    private Long id;

    private Long purchaseRequestId;

    private String requestNo;

    private String requestTitle;

    private Long supplierId;

    private String supplierCode;

    private String supplierName;

    private String noticeType;

    private Long bindingId;

    private String receiveIdType;

    private String receiveId;

    private String status;

    private String messageId;

    private String lastError;

    private LocalDateTime sentAt;

    private Integer retryCount;

    private String cardVersion;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
