package com.company.procurement.modules.procurement.route.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.procurement.common.entity.BaseEntity;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("procurement_approval_route")
public class ProcurementApprovalRoute extends BaseEntity {

    @TableField("enabled")
    private Integer enabled;

    @TableField("route_key")
    private String routeKey;

    @TableField("room_nature")
    private String roomNature;

    @TableField("room_name")
    private String roomName;

    @TableField("building_name")
    private String buildingName;

    @TableField("cost_center_code")
    private String costCenterCode;

    @TableField("dept_code")
    private String deptCode;

    @TableField("dept_name")
    private String deptName;

    @TableField("floor_leader_names")
    private String floorLeaderNames;

    @TableField("floor_leader_rule")
    private String floorLeaderRule;

    @TableField("manager_names")
    private String managerNames;

    @TableField("manager_rule")
    private String managerRule;

    @TableField("assistant_user_ids")
    private String assistantUserIds;

    @TableField("assistant_names")
    private String assistantNames;

    @TableField("region_name")
    private String regionName;

    @TableField("region_manager_names")
    private String regionManagerNames;

    @TableField("region_manager_rule")
    private String regionManagerRule;

    @TableField("director_names")
    private String directorNames;

    @TableField("director_rule")
    private String directorRule;

    @TableField("pool_push_day")
    private Integer poolPushDay;

    @TableField("region_threshold")
    private BigDecimal regionThreshold;

    @TableField("director_threshold")
    private BigDecimal directorThreshold;

    @TableField("over_price_rule")
    private String overPriceRule;

    @TableField("replacement_confirm_names")
    private String replacementConfirmNames;

    @TableField("supplier_quote_scope")
    private String supplierQuoteScope;

    @TableField("remark")
    private String remark;
}
