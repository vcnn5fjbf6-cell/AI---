package com.company.procurement.modules.procurement.settings.service;

import com.company.procurement.modules.procurement.settings.vo.ProcurementRuleTestResultVO;

public interface ProcurementFlowTestService {

    ProcurementRuleTestResultVO triggerFormalAction(String actionCode);
}
