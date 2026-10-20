package com.company.procurement.modules.procurement.asset.service;

import com.company.procurement.common.exception.BizException;
import com.company.procurement.common.security.AuthContextHolder;
import com.company.procurement.common.security.AuthUser;
import com.company.procurement.modules.procurement.asset.dto.AssetCatalogQueryDTO;
import com.company.procurement.modules.procurement.asset.entity.ProcurementAssetCatalogItem;
import com.company.procurement.modules.procurement.asset.mapper.ProcurementAssetCatalogItemMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AssetCatalogAdminServiceTest {

    private final ProcurementAssetCatalogItemMapper mapper = mock(ProcurementAssetCatalogItemMapper.class);
    private final AssetCatalogAdminService service = new AssetCatalogAdminService(mapper);

    @AfterEach
    void clearAuth() {
        AuthContextHolder.clear();
    }

    @Test
    void nonSuperAdminCannotManageCatalog() {
        AuthContextHolder.set(AuthUser.builder()
            .userId(20L)
            .roleCodes(List.of("ADMIN"))
            .build());

        assertThatThrownBy(() -> service.page(new AssetCatalogQueryDTO()))
            .isInstanceOfSatisfying(BizException.class, ex -> assertThat(ex.getCode()).isEqualTo("FORBIDDEN"));
    }

    @Test
    void superAdminCanDisableCatalogItemWithoutDeletingIt() {
        AuthContextHolder.set(AuthUser.builder()
            .userId(1L)
            .roleCodes(List.of("SUPER_ADMIN"))
            .build());
        ProcurementAssetCatalogItem item = new ProcurementAssetCatalogItem();
        item.setId(8L);
        item.setEnabled(1);
        item.setSourceVersion("V2.1");
        when(mapper.selectById(8L)).thenReturn(item);

        service.updateEnabled(8L, 0);

        ArgumentCaptor<ProcurementAssetCatalogItem> captor = ArgumentCaptor.forClass(ProcurementAssetCatalogItem.class);
        verify(mapper).updateById(captor.capture());
        assertThat(captor.getValue().getEnabled()).isZero();
        assertThat(captor.getValue().getSourceVersion()).isEqualTo("人工维护");
        assertThat(captor.getValue().getIsDeleted()).isNull();
    }
}
