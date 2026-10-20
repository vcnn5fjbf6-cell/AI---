package com.company.procurement.modules.procurement.assistant.vo;

import java.util.List;

public record ProcurementCompliancePrecheckVO(
    String overallOpinion,
    List<ItemOpinion> itemOpinions
) {

    public record ItemOpinion(
        Integer lineNo,
        String decisionSuggestion,
        String opinion,
        List<String> missingFields
    ) {
    }
}
