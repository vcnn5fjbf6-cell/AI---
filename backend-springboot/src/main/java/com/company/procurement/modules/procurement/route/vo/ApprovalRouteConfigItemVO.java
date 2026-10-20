package com.company.procurement.modules.procurement.route.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalRouteConfigItemVO {

    private String code;
    private String name;
    private String source;
    private String scope;
    private String permissions;
    private String rule;
    private String remark;
}
