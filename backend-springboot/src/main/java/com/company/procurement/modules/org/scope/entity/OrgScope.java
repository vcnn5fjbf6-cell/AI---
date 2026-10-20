package com.company.procurement.modules.org.scope.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.procurement.common.entity.BaseEntity;
import lombok.Data;

@Data
@TableName("org_scope")
public class OrgScope extends BaseEntity {

    @TableField("scope_code")
    private String scopeCode;

    @TableField("scope_name")
    private String scopeName;

    @TableField("scope_type")
    private String scopeType;

    @TableField("parent_id")
    private Long parentId;

    @TableField("path")
    private String path;

    @TableField("region_name")
    private String regionName;

    @TableField("room_name")
    private String roomName;

    @TableField("building_name")
    private String buildingName;

    @TableField("room_nature")
    private String roomNature;

    @TableField("cost_center_code")
    private String costCenterCode;

    @TableField("dept_code")
    private String deptCode;

    @TableField("dept_name")
    private String deptName;

    @TableField("province")
    private String province;

    @TableField("city")
    private String city;

    @TableField("district_name")
    private String districtName;

    @TableField("town_name")
    private String townName;

    @TableField("address")
    private String address;

    @TableField("status")
    private Integer status;

    @TableField("remark")
    private String remark;
}
