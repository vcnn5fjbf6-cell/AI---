package com.company.procurement.modules.procurement.settings.vo;

import lombok.Data;

@Data
public class ProcurementSettingItemVO {

    private Long id;
    private String settingKey;
    private String settingValue;
    private String settingGroup;
    private String settingLabel;
    private String valueType;
    private Integer sortNo;
    private String remark;
}
