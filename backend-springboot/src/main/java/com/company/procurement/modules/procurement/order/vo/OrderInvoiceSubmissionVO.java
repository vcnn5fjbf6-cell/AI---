package com.company.procurement.modules.procurement.order.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class OrderInvoiceSubmissionVO extends OrderInvoiceSummaryVO {

    private Long quoteItemId;
    private Long supplierId;
    private String remark;
    private List<OrderInvoiceAttachmentVO> attachments;
}
