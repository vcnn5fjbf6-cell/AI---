package com.company.procurement.modules.procurement.route.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class ProcurementApprovalRouteSaveDTO {

    private Integer enabled;
    private String routeKey;
    private String roomNature;

    @NotBlank(message = "机房不能为空")
    private String roomName;

    @NotBlank(message = "楼栋不能为空")
    private String buildingName;

    private String costCenterCode;
    private String deptCode;
    private String province;
    private String city;
    private String districtName;
    private String townName;
    private String address;

    @NotBlank(message = "部门/楼栋归属不能为空")
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

    @NotNull(message = "需求池推送日不能为空")
    private Integer poolPushDay;

    private BigDecimal regionThreshold;
    private BigDecimal directorThreshold;
    private String overPriceRule;
    private String replacementConfirmNames;
    private String supplierQuoteScope;
    private String remark;
    private List<ApprovalLevelSaveDTO> approvalLevels;
}
