package com.company.procurement.modules.supplier.dto;

import com.company.procurement.common.dto.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class SupplierFeishuQuoteNoticeQueryDTO extends PageQuery {

    private String status;

    private String noticeType;

    private String keyword;

    private Long purchaseRequestId;

    private Long supplierId;
}
