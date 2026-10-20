package com.company.procurement.modules.integration.feishu.scheduler;

import com.company.procurement.modules.integration.feishu.service.FeishuApprovalCardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FeishuApprovalCardScheduler {

    private final FeishuApprovalCardService approvalCardService;

    @Scheduled(fixedDelayString = "${feishu.approval-card.heartbeat-fixed-delay-ms:60000}")
    public void pushPendingApprovalCards() {
        try {
            approvalCardService.pushPendingApprovalCards();
        } catch (RuntimeException ex) {
            log.warn("Feishu approval card heartbeat failed", ex);
        }
    }
}
