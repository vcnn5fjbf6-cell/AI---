package com.company.procurement.modules.procurement.settings.vo;

import lombok.Data;

import java.util.List;

@Data
public class ProcurementSettingsVO {

    private List<ProcurementSettingItemVO> settings;
    private List<ProcurementDepartmentLimitVO> departmentLimits;
}
