package com.company.procurement.modules.outsourcing.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "outsourcing")
public class OutsourcingProperties {

    private boolean enabled;
}
