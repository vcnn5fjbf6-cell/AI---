package com.company.procurement.modules.ehs.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.procurement.modules.ehs.entity.EhsNotificationLog;
import com.company.procurement.modules.ehs.mapper.EhsNotificationLogMapper;
import com.company.procurement.modules.integration.feishu.service.FeishuApprovalCardService;
import com.company.procurement.modules.integration.feishu.vo.FeishuApprovalCardActionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EhsNotificationService {

    private final EhsNotificationLogMapper notificationLogMapper;
    private final FeishuApprovalCardService feishuApprovalCardService;

    public void notifyUser(String bizType,
                           Long bizId,
                           Long recipientUserId,
                           String eventType,
                           String title,
                           String content) {
        EhsNotificationLog logRow = new EhsNotificationLog();
        logRow.setBizType(bizType);
        logRow.setBizId(bizId);
        logRow.setRecipientUserId(recipientUserId);
        logRow.setChannel("FEISHU");
        logRow.setEventType(eventType);
        logRow.setTitle(title);
        logRow.setContent(content);
        try {
            FeishuApprovalCardActionResult result = feishuApprovalCardService.sendBusinessNotification(
                recipientUserId,
                title,
                content
            );
            if (result != null && result.isSuccess()) {
                logRow.setStatus("SENT");
                logRow.setExternalMessageId(result.getMessage());
                logRow.setSentAt(LocalDateTime.now());
            } else {
                String message = result == null ? "飞书通知未返回结果" : result.getMessage();
                logRow.setStatus(message != null && message.contains("未启用") ? "SKIPPED" : "FAILED");
                logRow.setErrorMessage(limit(message, 1000));
            }
        } catch (RuntimeException ex) {
            logRow.setStatus("FAILED");
            logRow.setErrorMessage(limit(ex.getMessage(), 1000));
            log.warn("EHS notification failed, bizType={}, bizId={}, recipient={}", bizType, bizId, recipientUserId, ex);
        }
        notificationLogMapper.insert(logRow);
    }

    public List<EhsNotificationLog> list(String bizType, Long bizId) {
        return notificationLogMapper.selectList(new LambdaQueryWrapper<EhsNotificationLog>()
            .eq(EhsNotificationLog::getBizType, bizType)
            .eq(EhsNotificationLog::getBizId, bizId)
            .orderByDesc(EhsNotificationLog::getId));
    }

    private String limit(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String text = value.trim();
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }
}
