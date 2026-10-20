package com.company.procurement.modules.procurement.assistant.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "procurement.ai.dify")
public class DifyProcurementProperties {

    private boolean enabled = false;

    private String apiKey;

    private String appType = "workflow";

    private String workflowRunUrl = "https://api.dify.ai/v1/workflows/run";

    private String chatMessagesUrl = "https://api.dify.ai/v1/chat-messages";

    private String responseMode = "blocking";
}
