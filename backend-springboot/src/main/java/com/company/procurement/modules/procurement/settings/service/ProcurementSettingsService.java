package com.company.procurement.modules.procurement.settings.service;

import com.company.procurement.common.security.AuthUser;
import com.company.procurement.modules.procurement.settings.dto.ProcurementSettingsUpdateDTO;
import com.company.procurement.modules.procurement.settings.vo.ProcurementRuleTestResultVO;
import com.company.procurement.modules.procurement.settings.vo.ProcurementSettingsVO;

import java.math.BigDecimal;

public interface ProcurementSettingsService {

    ProcurementSettingsVO getSettings();

    void updateSettings(ProcurementSettingsUpdateDTO dto);

    ProcurementRuleTestResultVO testRuleAction(String actionCode);

    void validateRequest(
        AuthUser authUser,
        String deptCode,
        String deptName,
        BigDecimal totalAmount,
        Integer urgencyLevel,
        Long excludeRequestId
    );
}
