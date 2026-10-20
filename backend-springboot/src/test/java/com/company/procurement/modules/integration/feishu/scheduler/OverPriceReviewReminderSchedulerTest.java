package com.company.procurement.modules.integration.feishu.scheduler;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class OverPriceReviewReminderSchedulerTest {

    private final LocalDateTime dueAt = LocalDateTime.of(2026, 8, 3, 12, 0);

    @Test
    void reminderStagesFollowTwelveHourTwoHourAndOverdueBoundaries() {
        assertThat(OverPriceReviewReminderScheduler.reminderStage(dueAt.minusHours(13), dueAt)).isZero();
        assertThat(OverPriceReviewReminderScheduler.reminderStage(dueAt.minusHours(12), dueAt)).isEqualTo(1);
        assertThat(OverPriceReviewReminderScheduler.reminderStage(dueAt.minusHours(2), dueAt)).isEqualTo(2);
        assertThat(OverPriceReviewReminderScheduler.reminderStage(dueAt, dueAt)).isEqualTo(3);
    }
}
