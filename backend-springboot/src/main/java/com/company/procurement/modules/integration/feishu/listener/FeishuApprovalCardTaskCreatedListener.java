package com.company.procurement.modules.integration.feishu.listener;

import com.company.procurement.modules.approval.record.event.ApprovalTasksCreatedEvent;
import com.company.procurement.modules.integration.feishu.service.FeishuApprovalCardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class FeishuApprovalCardTaskCreatedListener {

    private final FeishuApprovalCardService approvalCardService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onApprovalTasksCreated(ApprovalTasksCreatedEvent event) {
        try {
            approvalCardService.pushPendingApprovalCards();
        } catch (RuntimeException ex) {
            log.warn("Push Feishu approval cards after approval task creation failed, requestId={}",
                event == null ? null : event.purchaseRequestId(), ex);
        }
    }
}
