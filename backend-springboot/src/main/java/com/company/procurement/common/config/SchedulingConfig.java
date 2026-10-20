package com.company.procurement.common.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(
    prefix = "app.scheduling",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class SchedulingConfig {

    private static final int MIN_POOL_SIZE = 2;

    @Bean(name = "taskScheduler")
    public ThreadPoolTaskScheduler taskScheduler(
        @Value("${app.scheduling.pool-size:4}") int configuredPoolSize
    ) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(Math.max(MIN_POOL_SIZE, configuredPoolSize));
        scheduler.setThreadNamePrefix("scheduling-");
        scheduler.setDaemon(true);
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        return scheduler;
    }
}
