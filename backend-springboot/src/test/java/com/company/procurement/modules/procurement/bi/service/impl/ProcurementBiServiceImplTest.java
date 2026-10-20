package com.company.procurement.modules.procurement.bi.service.impl;

import com.company.procurement.common.security.AuthContextHolder;
import com.company.procurement.common.security.AuthUser;
import com.company.procurement.modules.procurement.bi.dto.ProcurementBiQueryDTO;
import com.company.procurement.modules.procurement.bi.vo.ProcurementBiDatasetVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProcurementBiServiceImplTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final ProcurementBiServiceImpl service = new ProcurementBiServiceImpl(jdbcTemplate, new ObjectMapper());

    @AfterEach
    void tearDown() {
        AuthContextHolder.clear();
    }

    @Test
    void exposesBusinessRegionAndRoomDimensionsForExecutiveDashboard() {
        loginAsSuperAdmin();

        ProcurementBiDatasetVO dataset = service.listDatasets().stream()
            .filter(item -> "purchase_items".equals(item.getDatasetCode()))
            .findFirst()
            .orElseThrow();

        assertThat(dataset.getFields())
            .extracting(ProcurementBiDatasetVO.FieldVO::getFieldCode)
            .contains("businessRegion", "roomGroup", "buildingName", "createdMonth", "requestId");
    }

    @Test
    void appliesBusinessRegionAndRoomFiltersToTheMappedProcurementDataset() {
        loginAsSuperAdmin();
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());

        ProcurementBiQueryDTO dto = new ProcurementBiQueryDTO();
        dto.setDatasetCode("purchase_items");
        dto.setDimensions(List.of("businessRegion", "roomGroup"));
        dto.setMetrics(List.of(metric("requestAmount", "SUM")));
        dto.setFilters(List.of(filter("businessRegion", "华北一"), filter("roomGroup", "酒仙桥")));

        service.query(dto);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForList(sqlCaptor.capture(), any(Object[].class));
        assertThat(sqlCaptor.getValue())
            .contains("MAX(region_name) AS businessRegion")
            .contains("MAX(room_name) AS roomGroup")
            .contains("businessRegion = ?")
            .contains("roomGroup = ?");
    }

    private void loginAsSuperAdmin() {
        AuthContextHolder.set(AuthUser.builder()
            .userId(1L)
            .username("admin")
            .userType("SUPER_ADMIN")
            .roleCodes(List.of("SUPER_ADMIN"))
            .build());
    }

    private ProcurementBiQueryDTO.MetricDTO metric(String fieldCode, String aggregation) {
        ProcurementBiQueryDTO.MetricDTO metric = new ProcurementBiQueryDTO.MetricDTO();
        metric.setFieldCode(fieldCode);
        metric.setAggregation(aggregation);
        return metric;
    }

    private ProcurementBiQueryDTO.FilterDTO filter(String fieldCode, String value) {
        ProcurementBiQueryDTO.FilterDTO filter = new ProcurementBiQueryDTO.FilterDTO();
        filter.setFieldCode(fieldCode);
        filter.setOperator("=");
        filter.setValue(value);
        return filter;
    }
}
