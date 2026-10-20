package com.company.procurement.modules.ehs.vo;

import lombok.Builder;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@Builder
public class EhsRegistryStatsVO {
    private long totalCount;
    private long activeCount;
    private long completedCount;
    private long alertCount;
    @Builder.Default
    private Map<String, Long> statusCounts = new LinkedHashMap<>();
    @Builder.Default
    private Map<String, Long> categoryCounts = new LinkedHashMap<>();
}
