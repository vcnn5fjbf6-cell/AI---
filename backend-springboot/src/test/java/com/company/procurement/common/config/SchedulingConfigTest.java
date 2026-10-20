package com.company.procurement.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.config.TaskManagementConfigUtils;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import static org.assertj.core.api.Assertions.assertThat;

class SchedulingConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(SchedulingConfig.class);

    @Test
    void schedulingIsEnabledByDefaultForExistingEnvironments() {
        contextRunner.run(context -> {
            assertThat(context).hasBean(TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME);
            ThreadPoolTaskScheduler scheduler = context.getBean(
                "taskScheduler",
                ThreadPoolTaskScheduler.class
            );
            assertThat(scheduler.getScheduledThreadPoolExecutor().getCorePoolSize()).isEqualTo(4);
            scheduler.shutdown();
        });
    }

    @Test
    void schedulingPoolSizeNeverFallsBackToSingleThread() {
        contextRunner
            .withPropertyValues("app.scheduling.pool-size=1")
            .run(context -> {
                ThreadPoolTaskScheduler scheduler = context.getBean(
                    "taskScheduler",
                    ThreadPoolTaskScheduler.class
                );
                assertThat(scheduler.getScheduledThreadPoolExecutor().getCorePoolSize()).isEqualTo(2);
                scheduler.shutdown();
            });
    }

    @Test
    void schedulingCanBeDisabledForTheTestEnvironment() {
        contextRunner
            .withPropertyValues("app.scheduling.enabled=false")
            .run(context -> assertThat(context)
                .doesNotHaveBean(TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME));
    }
}
