package com.company.procurement.modules.procurement.asset.service;

import com.company.procurement.modules.procurement.asset.entity.ProcurementAssetCatalogItem;
import com.company.procurement.modules.procurement.asset.mapper.ProcurementAssetCatalogItemMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AssetCatalogServiceTest {

    private final AssetCatalogService service = new AssetCatalogService(new ObjectMapper());

    @Test
    void loadsOnlyActiveEntriesFromOfficialCatalog() {
        assertThat(service.version()).contains("V2.1");
        assertThat(service.activeEntries()).hasSize(158);
        assertThat(service.activeEntries()).allMatch(entry -> entry.isActive());
    }

    @Test
    void matchesCatalogEntryByCodeAndProductText() {
        assertThat(service.find("1702", "0056")).isPresent();
        assertThat(service.matchExact("10kVA UPS电源", "机架式"))
            .isPresent()
            .get()
            .extracting(AssetCatalogService.AssetCatalogEntry::getCategoryCode)
            .isEqualTo("1702");
    }

    @Test
    void managedCatalogReplacesPackagedCatalogAtRuntime() {
        ProcurementAssetCatalogItemMapper mapper = mock(ProcurementAssetCatalogItemMapper.class);
        ProcurementAssetCatalogItem managed = new ProcurementAssetCatalogItem();
        managed.setCategoryCode("9000");
        managed.setCategoryName("测试大类");
        managed.setAssetNameCode("0001");
        managed.setAssetName("受管目录设备");
        managed.setEnabled(1);
        when(mapper.selectList(any())).thenReturn(List.of(managed));

        AssetCatalogService managedService = new AssetCatalogService(mapper, new ObjectMapper());

        assertThat(managedService.find("9000", "0001")).isPresent();
        assertThat(managedService.find("1702", "0056")).isEmpty();
    }

    @Test
    void disabledManagedCatalogItemDoesNotMatch() {
        ProcurementAssetCatalogItemMapper mapper = mock(ProcurementAssetCatalogItemMapper.class);
        when(mapper.selectList(any())).thenReturn(List.of());

        AssetCatalogService managedService = new AssetCatalogService(mapper, new ObjectMapper());

        assertThat(managedService.activeEntries()).isEmpty();
        assertThat(managedService.find("1702", "0056")).isEmpty();
    }
}
