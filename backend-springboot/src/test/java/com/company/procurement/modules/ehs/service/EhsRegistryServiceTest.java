package com.company.procurement.modules.ehs.service;

import com.company.procurement.modules.ehs.entity.EhsRegistryRecord;
import com.company.procurement.modules.ehs.mapper.EhsRegistryRecordMapper;
import com.company.procurement.modules.ehs.vo.EhsRegistryStatsVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EhsRegistryServiceTest {

    @Mock
    private EhsRegistryRecordMapper recordMapper;

    @Test
    void ppeStatsOnlyCountLowStockAndExpiringAsActiveAlerts() {
        when(recordMapper.selectList(any())).thenReturn(List.of(
            record("PPE", "NORMAL"),
            record("PPE", "LOW_STOCK"),
            record("PPE", "EXPIRING")
        ));
        EhsRegistryService service = new EhsRegistryService(recordMapper, new ObjectMapper());

        EhsRegistryStatsVO stats = service.stats("PPE");

        assertThat(stats.getTotalCount()).isEqualTo(3);
        assertThat(stats.getActiveCount()).isEqualTo(2);
        assertThat(stats.getCompletedCount()).isEqualTo(1);
        assertThat(stats.getAlertCount()).isEqualTo(2);
    }

    private EhsRegistryRecord record(String module, String status) {
        EhsRegistryRecord record = new EhsRegistryRecord();
        record.setModuleType(module);
        record.setStatus(status);
        return record;
    }
}
