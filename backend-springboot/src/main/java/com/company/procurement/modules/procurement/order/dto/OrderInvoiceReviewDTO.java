package com.company.procurement.modules.procurement.order.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class OrderInvoiceReviewDTO {

    @Size(max = 1000, message = "审核意见不能超过1000个字符")
    private String remark;
}
