package com.company.procurement.modules.ai.governance.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AiContentRevisionVO {
    private Long id;
    private String targetType;
    private Long targetId;
    private String targetCode;
    private Integer revisionNo;
    private String snapshotJson;
    private String changeSource;
    private String changeSummary;
    private Long createdBy;
    private LocalDateTime createdAt;
}
