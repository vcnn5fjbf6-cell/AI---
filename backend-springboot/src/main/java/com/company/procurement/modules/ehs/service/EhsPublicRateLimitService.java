package com.company.procurement.modules.ehs.service;

import com.company.procurement.common.exception.BizException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class EhsPublicRateLimitService {

    private final Map<String, WindowCounter> counters = new ConcurrentHashMap<>();

    public void check(String clientKey, String action, int limit, Duration window) {
        long now = System.currentTimeMillis();
        long windowMillis = Math.max(1000L, window.toMillis());
        String key = action + ":" + (clientKey == null ? "unknown" : clientKey);
        WindowCounter counter = counters.compute(key, (ignored, current) -> {
            if (current == null || now - current.startedAt >= windowMillis) {
                return new WindowCounter(now, 1);
            }
            current.count++;
            return current;
        });
        if (counter.count > limit) {
            throw new BizException("EHS_PUBLIC_RATE_LIMITED", "操作过于频繁，请稍后再试");
        }
        if (counters.size() > 5000) {
            counters.entrySet().removeIf(entry -> now - entry.getValue().startedAt > windowMillis * 2);
        }
    }

    private static final class WindowCounter {
        private final long startedAt;
        private int count;

        private WindowCounter(long startedAt, int count) {
            this.startedAt = startedAt;
            this.count = count;
        }
    }
}
