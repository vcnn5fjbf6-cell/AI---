package com.company.procurement.modules.ehs.service;

import com.company.procurement.common.exception.BizException;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EhsPublicRateLimitServiceTest {

    @Test
    void rejectsRequestsPastConfiguredWindowLimit() {
        EhsPublicRateLimitService service = new EhsPublicRateLimitService();

        service.check("127.0.0.1", "create", 1, Duration.ofMinutes(10));

        assertThatThrownBy(() -> service.check("127.0.0.1", "create", 1, Duration.ofMinutes(10)))
            .isInstanceOf(BizException.class)
            .hasMessageContaining("操作过于频繁");
    }
}
