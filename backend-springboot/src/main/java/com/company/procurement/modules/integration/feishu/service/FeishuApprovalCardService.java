package com.company.procurement.modules.integration.feishu.service;

import com.company.procurement.common.result.PageResult;
import com.company.procurement.modules.integration.feishu.dto.FeishuApprovalCardNotificationQueryDTO;
import com.company.procurement.modules.integration.feishu.vo.FeishuApprovalCardActionResult;
import com.company.procurement.modules.integration.feishu.vo.FeishuApprovalCardNotificationVO;

public interface FeishuApprovalCardService {

    void pushPendingApprovalCards();

    FeishuApprovalCardActionResult handleCardAction(FeishuApprovalCardActionResult.ActionRequest request);

    FeishuApprovalCardActionResult sendTestCardToUser(Long userId);

    FeishuApprovalCardActionResult sendBusinessNotification(Long userId, String title, String content);

    PageResult<FeishuApprovalCardNotificationVO> pageNotifications(FeishuApprovalCardNotificationQueryDTO queryDTO);

    FeishuApprovalCardActionResult resendNotification(Long notificationId);
}
