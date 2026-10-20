package com.company.procurement.modules.ehs.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "ehs")
public class EhsProperties {

    private boolean enabled;
}
