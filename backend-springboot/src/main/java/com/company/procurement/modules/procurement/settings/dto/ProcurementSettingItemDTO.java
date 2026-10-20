package com.company.procurement.modules.procurement.settings.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ProcurementSettingItemDTO {

    @NotBlank(message = "配置变量名不能为空")
    private String settingKey;

    private String settingValue;
    private String settingGroup;
    private String settingLabel;
    private String valueType;
    private Integer sortNo;
    private String remark;
}
