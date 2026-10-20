package com.company.procurement.modules.procurement.order.support;

import com.company.procurement.common.security.AuthUser;
import com.company.procurement.modules.procurement.order.vo.ProcurementOrderVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SupplierSoldOrderAccessTest {

    @Test
    void manageAllPermissionAllowsSupplierToOperateOtherSupplierOrder() {
        AuthUser supervisor = unboundSupervisor();
        ProcurementOrderVO order = new ProcurementOrderVO();
        order.setSupplierId(2L);

        assertThat(SupplierSoldOrderAccess.canManageAll(supervisor)).isTrue();
        assertThat(SupplierSoldOrderAccess.canOperate(supervisor, order)).isTrue();
        assertThat(SupplierSoldOrderAccess.canUploadOrderDocuments(supervisor, order)).isTrue();
    }

    @Test
    void ordinarySupplierRemainsRestrictedToOwnOrders() {
        AuthUser supplier = supplierUser(14L, List.of("procurement_order:supplier-read"));
        ProcurementOrderVO ownOrder = new ProcurementOrderVO();
        ownOrder.setSupplierId(14L);
        ProcurementOrderVO otherOrder = new ProcurementOrderVO();
        otherOrder.setSupplierId(2L);

        assertThat(SupplierSoldOrderAccess.canManageAll(supplier)).isFalse();
        assertThat(SupplierSoldOrderAccess.canOperate(supplier, ownOrder)).isTrue();
        assertThat(SupplierSoldOrderAccess.canOperate(supplier, otherOrder)).isFalse();
        assertThat(SupplierSoldOrderAccess.canUploadOrderDocuments(supplier, ownOrder)).isTrue();
        assertThat(SupplierSoldOrderAccess.canUploadOrderDocuments(supplier, otherOrder)).isFalse();
    }

    private AuthUser supplierUser(Long supplierId, List<String> permissionCodes) {
        return AuthUser.builder()
            .userId(255L)
            .username("supplier-supervisor")
            .userType("SUPPLIER")
            .supplierId(supplierId)
            .roleCodes(List.of("SUPPLIER", "SUPPLIER_QUOTE_VIEWER"))
            .permissionCodes(permissionCodes)
            .build();
    }

    private AuthUser unboundSupervisor() {
        return AuthUser.builder()
            .userId(255L)
            .username("supplier-supervisor")
            .userType("SUPPLIER_QUOTE_VIEWER")
            .roleCodes(List.of("SUPPLIER_QUOTE_VIEWER"))
            .permissionCodes(List.of(SupplierSoldOrderAccess.MANAGE_ALL_PERMISSION))
            .build();
    }
}
