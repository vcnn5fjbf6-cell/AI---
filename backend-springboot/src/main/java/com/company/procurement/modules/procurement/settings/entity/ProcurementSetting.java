package com.company.procurement.modules.procurement.settings.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.procurement.common.entity.BaseEntity;
import lombok.Data;

@Data
@TableName("procurement_setting")
public class ProcurementSetting extends BaseEntity {

    @TableField("setting_key")
    private String settingKey;

    @TableField("setting_value")
    private String settingValue;

    @TableField("setting_group")
    private String settingGroup;

    @TableField("setting_label")
    private String settingLabel;

    @TableField("value_type")
    private String valueType;

    @TableField("sort_no")
    private Integer sortNo;

    @TableField("remark")
    private String remark;
}
