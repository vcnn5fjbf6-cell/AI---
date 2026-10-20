package com.company.procurement.modules.integration.feishu.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "feishu.approval-card")
public class FeishuApprovalCardProperties {

    private boolean enabled = false;

    private boolean heartbeatEnabled = true;

    private boolean longConnectionEnabled = false;

    private long heartbeatFixedDelayMs = 60000L;

    private int batchSize = 20;

    private int maxRetryCount = 5;

    private int detailItemLimit = 8;

    private String receiveIdType = "email";

    private String testRecipientEmail;

    private String verificationToken;

    private String encryptKey;

    private String messageSendUrl = "https://open.feishu.cn/open-apis/im/v1/messages";

    private String messagePatchUrl = "https://open.feishu.cn/open-apis/im/v1/messages/%s";

    private String appBaseUrl;

    private String systemActionUrl;

    private String approvalPath = "/procurement/approval";
}
