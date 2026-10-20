package com.company.procurement.modules.supplier.dto;

import com.company.procurement.common.dto.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class SupplierPageQueryDTO extends PageQuery {

    private String supplierCode;
    private String supplierName;
    private Long regionId;
    private String status;
    private String onboardingStatus;
    private Integer blacklistFlag;
}
