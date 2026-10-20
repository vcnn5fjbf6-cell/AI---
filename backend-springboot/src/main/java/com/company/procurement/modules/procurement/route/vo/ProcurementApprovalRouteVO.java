package com.company.procurement.modules.procurement.route.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ProcurementApprovalRouteVO {

    private Long id;
    private Integer enabled;
    private String routeKey;
    private String roomNature;
    private String roomName;
    private String buildingName;
    private String costCenterCode;
    private String deptCode;
    private String province;
    private String city;
    private String districtName;
    private String townName;
    private String address;
    private String deptName;
    private String floorLeaderNames;
    private String floorLeaderRule;
    private String managerNames;
    private String managerRule;
    private String assistantUserIds;
    private String assistantNames;
    private String regionName;
    private String regionManagerNames;
    private String regionManagerRule;
    private String directorNames;
    private String directorRule;
    private Integer poolPushDay;
    private BigDecimal regionThreshold;
    private BigDecimal directorThreshold;
    private String overPriceRule;
    private String replacementConfirmNames;
    private String supplierQuoteScope;
    private String remark;
    private Integer enabledApprovalLevelCount;
    private Integer missingApprovalLevelCount;
    private String approvalPreviewText;
    private List<ApprovalLevelVO> approvalLevels;
    private LocalDateTime updatedAt;
}
