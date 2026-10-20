package com.company.procurement.modules.ehs.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class EhsOverviewVO {

    private Long permitTotalCount;

    private Long permitApprovingCount;

    private Long permitCompletedCount;

    private Long myPendingApprovalCount;

    private Long knowledgeDocumentCount;

    private Long notificationFailureCount;

    private boolean dataSourceConnected;

    private boolean sharedApprovalAvailable;

    private boolean sharedNotificationAvailable;

    private LocalDateTime refreshedAt;

    private List<EhsModuleStatusVO> modules;

    private List<Map<String, Object>> riskQueue;
}
