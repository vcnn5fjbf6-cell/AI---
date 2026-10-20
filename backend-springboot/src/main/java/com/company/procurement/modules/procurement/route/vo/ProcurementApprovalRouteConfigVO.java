package com.company.procurement.modules.procurement.route.vo;

import lombok.Data;

import java.util.List;

@Data
public class ProcurementApprovalRouteConfigVO {

    private List<ProcurementApprovalRouteVO> routes;
    private List<ApprovalRouteConfigItemVO> workflowPaths;
    private List<ApprovalRouteConfigItemVO> rolePermissions;
    private List<ApprovalRouteConfigItemVO> approvalRules;
    private List<ApprovalRouteConfigItemVO> supplierPermissions;
    private List<ApprovalRouteConfigItemVO> fieldMappings;
}
