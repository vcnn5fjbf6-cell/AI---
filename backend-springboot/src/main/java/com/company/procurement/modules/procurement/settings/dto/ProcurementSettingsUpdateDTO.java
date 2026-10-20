package com.company.procurement.modules.procurement.settings.dto;

import jakarta.validation.Valid;
import lombok.Data;

import java.util.List;

@Data
public class ProcurementSettingsUpdateDTO {

    @Valid
    private List<ProcurementSettingItemDTO> settings;

    @Valid
    private List<ProcurementDepartmentLimitDTO> departmentLimits;
}
