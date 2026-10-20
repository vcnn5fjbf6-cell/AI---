package com.company.procurement.modules.platform.permission.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.procurement.common.entity.BaseEntity;
import lombok.Data;

@Data
@TableName("platform_unit_app_scope")
public class PlatformUnitAppScope extends BaseEntity {

    @TableField("unit_id")
    private Long unitId;

    @TableField("app_code")
    private String appCode;

    @TableField("status")
    private Integer status;

    @TableField("remark")
    private String remark;
}
