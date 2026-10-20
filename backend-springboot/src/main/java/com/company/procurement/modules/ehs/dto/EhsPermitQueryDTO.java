package com.company.procurement.modules.ehs.dto;

import com.company.procurement.common.dto.PageQuery;
import lombok.Data;

@Data
public class EhsPermitQueryDTO extends PageQuery {
    private String keyword;
    private String status;
    private String approvalStatus;
    private String permitType;
    private Boolean mineOnly;
    private Boolean pendingMine;
}
