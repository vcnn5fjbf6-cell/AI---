package com.company.procurement.modules.procurement.request.dto;

import com.company.procurement.common.dto.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class PurchaseRequestPageQueryDTO extends PageQuery {

    private String reqNo;
    private String reqTitle;
    private String status;
    private String approvalStatus;
    private Long applicantUserId;
    private Long purchaserUserId;
    private Long regionId;
}
