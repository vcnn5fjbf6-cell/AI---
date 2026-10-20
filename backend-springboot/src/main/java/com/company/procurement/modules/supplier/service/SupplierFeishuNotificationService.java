package com.company.procurement.modules.supplier.service;

import com.company.procurement.common.result.PageResult;
import com.company.procurement.modules.integration.feishu.vo.FeishuApprovalCardActionResult;
import com.company.procurement.modules.supplier.dto.SupplierFeishuQuoteNoticeQueryDTO;
import com.company.procurement.modules.supplier.vo.SupplierFeishuQuoteNoticeVO;

public interface SupplierFeishuNotificationService {

    boolean isSupplierBindMessage(String text);

    boolean isSupplierBindIntentMessage(String text);

    String supplierBindGuideMessage();

    String bindSupplierFromMessage(
        String text,
        String openId,
        String userId,
        String unionId,
        String chatId,
        String chatType,
        String tenantKey,
        String sourceUserKey,
        String messageId
    );

    String tryImportSupplierQuoteFile(
        String messageType,
        String fileKey,
        String fileName,
        String openId,
        String userId,
        String unionId,
        String chatId,
        String chatType,
        String tenantKey,
        String sourceUserKey,
        String messageId,
        Long messageLogId
    );

    void pushPendingQuoteNotifications();

    void pushQuoteNotificationsForRequest(Long purchaseRequestId);

    void pushPendingAwardNotifications();

    FeishuApprovalCardActionResult sendSupplierQuoteNoticeTest(String receiveIdType, String receiveId);

    PageResult<SupplierFeishuQuoteNoticeVO> pageQuoteNotices(SupplierFeishuQuoteNoticeQueryDTO queryDTO);

    FeishuApprovalCardActionResult resendQuoteNotice(Long noticeId);

    FeishuApprovalCardActionResult handleQuoteCardAction(
        java.util.Map<String, Object> value,
        java.util.Map<String, Object> formValue,
        String openId,
        String userId,
        String unionId,
        String messageId
    );
}
