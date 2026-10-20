package com.company.procurement.modules.platform.permission.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.procurement.common.entity.BaseEntity;
import lombok.Data;

@Data
@TableName("platform_unit")
public class PlatformUnit extends BaseEntity {

    @TableField("unit_code")
    private String unitCode;

    @TableField("unit_name")
    private String unitName;

    @TableField("parent_id")
    private Long parentId;

    @TableField("status")
    private Integer status;

    @TableField("remark")
    private String remark;
}
