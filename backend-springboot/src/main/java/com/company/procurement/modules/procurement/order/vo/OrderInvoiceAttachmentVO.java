package com.company.procurement.modules.procurement.order.vo;

import lombok.Data;

@Data
public class OrderInvoiceAttachmentVO {

    private Long id;
    private String fileName;
    private String contentType;
    private Long fileSize;
    private String sha256;
    private String downloadUrl;
}
