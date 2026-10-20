package com.company.procurement.modules.procurement.assistant.service;

import com.company.procurement.common.security.AuthUser;
import com.company.procurement.modules.integration.feishu.vo.FeishuApprovalCardActionResult;

import java.util.Map;

public interface FeishuBotService {

    Object handleEvent(Map<String, Object> payload);

    Object handleTrustedEvent(Map<String, Object> payload);

    FeishuApprovalCardActionResult handleCardAction(Map<String, Object> value, Map<String, Object> formValue,
                                                    String openId, String userId, String unionId, String messageId);

    AuthUser resolveBotUser(Map<String, Object> payload);
}
