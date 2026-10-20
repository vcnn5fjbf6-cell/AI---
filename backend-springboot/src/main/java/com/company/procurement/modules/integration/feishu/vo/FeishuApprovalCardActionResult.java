package com.company.procurement.modules.integration.feishu.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class FeishuApprovalCardActionResult {

    private boolean success;

    private String toastType;

    private String message;

    private String cardType;

    private Object cardData;

    public static FeishuApprovalCardActionResult ok(String message) {
        return FeishuApprovalCardActionResult.builder()
            .success(true)
            .toastType("success")
            .message(message)
            .build();
    }

    public static FeishuApprovalCardActionResult ok(String message, Object cardData) {
        return FeishuApprovalCardActionResult.builder()
            .success(true)
            .toastType("success")
            .message(message)
            .cardType("raw")
            .cardData(cardData)
            .build();
    }

    public static FeishuApprovalCardActionResult warning(String message) {
        return FeishuApprovalCardActionResult.builder()
            .success(false)
            .toastType("warning")
            .message(message)
            .build();
    }

    @Data
    @Builder
    public static class ActionRequest {

        private String action;

        private Long approvalTaskId;

        private Long purchaseRequestId;

        private Long rejectItemId;

        private List<Long> rejectItemIds;

        private String rejectReason;

        private Integer itemPage;

        private String approvalComment;

        private String cardToken;

        private String openId;

        private String userId;

        private String unionId;

        private String messageId;
    }
}
