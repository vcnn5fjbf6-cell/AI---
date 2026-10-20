package com.company.procurement.modules.procurement.integration.shopping.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "integrations.shopping")
public class ShoppingToolProperties {

    private String token;
}
