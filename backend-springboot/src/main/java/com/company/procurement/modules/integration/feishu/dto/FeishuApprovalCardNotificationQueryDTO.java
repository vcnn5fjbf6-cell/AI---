package com.company.procurement.modules.integration.feishu.dto;

import com.company.procurement.common.dto.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class FeishuApprovalCardNotificationQueryDTO extends PageQuery {

    private String status;

    private String keyword;

    private Long approvalTaskId;

    private Long purchaseRequestId;
}
