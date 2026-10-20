package com.company.procurement.modules.procurement.request.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class PurchaseRequestNoService {

    private static final long INITIAL_NEXT_NO = 100000L;
    private static final long AGGREGATE_INITIAL_NEXT_NO = 0L;
    private static final String NORMAL_PREFIX = "PTCG";
    private static final String EMERGENCY_PREFIX = "JJCG";
    private static final String AGGREGATE_PREFIX = "HZCG";

    private final JdbcTemplate jdbcTemplate;

    @Transactional(rollbackFor = Exception.class)
    public String allocate(Integer urgencyLevel) {
        String prefix = prefixForUrgency(urgencyLevel);
        return allocateWithPrefix(prefix, INITIAL_NEXT_NO);
    }

    @Transactional(rollbackFor = Exception.class)
    public String allocateAggregate() {
        return allocateWithPrefix(AGGREGATE_PREFIX, AGGREGATE_INITIAL_NEXT_NO);
    }

    private String allocateWithPrefix(String prefix, long initialNextNo) {
        jdbcTemplate.update("""
            INSERT IGNORE INTO purchase_request_no_sequence(prefix, next_no)
            VALUES (?, ?)
            """, prefix, initialNextNo);
        jdbcTemplate.update("""
            UPDATE purchase_request_no_sequence
            SET next_no = LAST_INSERT_ID(next_no) + 1,
                updated_at = CURRENT_TIMESTAMP
            WHERE prefix = ?
            """, prefix);
        Long number = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        if (number == null || number <= 0) {
            number = initialNextNo;
        }
        return prefix + "-" + String.format(Locale.ROOT, "%06d", number);
    }

    public boolean matchesUrgency(String reqNo, Integer urgencyLevel) {
        if (!StringUtils.hasText(reqNo)) {
            return false;
        }
        return reqNo.trim().toUpperCase(Locale.ROOT).startsWith(prefixForUrgency(urgencyLevel) + "-");
    }

    public String normalize(String reqNo) {
        return StringUtils.hasText(reqNo) ? reqNo.trim().toUpperCase(Locale.ROOT) : null;
    }

    private String prefixForUrgency(Integer urgencyLevel) {
        return urgencyLevel != null && urgencyLevel >= 2 ? EMERGENCY_PREFIX : NORMAL_PREFIX;
    }
}
