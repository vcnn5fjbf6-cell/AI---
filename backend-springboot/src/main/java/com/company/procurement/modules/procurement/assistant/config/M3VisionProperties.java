package com.company.procurement.modules.procurement.assistant.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "procurement.ai.m3")
public class M3VisionProperties {

    private boolean enabled = false;

    private String baseUrl;

    private String apiKey;

    private String model = "m3-vision";

    private String endpoint = "/chat/completions";
}
