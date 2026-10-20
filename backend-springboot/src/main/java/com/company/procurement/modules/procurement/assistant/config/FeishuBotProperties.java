package com.company.procurement.modules.procurement.assistant.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "feishu.bot")
public class FeishuBotProperties {

    private boolean enabled = false;

    private String verificationToken;

    private String messageSendUrl = "https://open.feishu.cn/open-apis/im/v1/messages";
}
