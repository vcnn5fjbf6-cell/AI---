package com.company.procurement.modules.procurement.order.service.impl;

import com.company.procurement.modules.procurement.order.vo.ProcurementOrderVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProcurementOrderServiceImplArchiveFilterTest {

    @Test
    void filtersCentralizedArchiveByRoomAndSupplier() {
        ProcurementOrderVO m6Jd = order(1L, "酒仙桥 / M6机房");
        ProcurementOrderVO m6Suning = order(2L, "酒仙桥 / M6机房");
        ProcurementOrderVO b28Jd = order(1L, "酒仙桥 / B28机房");

        List<ProcurementOrderVO> result = ProcurementOrderServiceImpl.filterArchiveOrders(
            List.of(m6Jd, m6Suning, b28Jd),
            "酒仙桥 / M6机房",
            1L
        );

        assertThat(result).containsExactly(m6Jd);
    }

    @Test
    void blankFiltersKeepWholeCentralizedArchive() {
        ProcurementOrderVO first = order(1L, "酒仙桥 / M6机房");
        ProcurementOrderVO second = order(2L, "酒仙桥 / B28机房");

        assertThat(ProcurementOrderServiceImpl.filterArchiveOrders(List.of(first, second), "  ", null))
            .containsExactly(first, second);
    }

    private ProcurementOrderVO order(Long supplierId, String deptName) {
        ProcurementOrderVO order = new ProcurementOrderVO();
        order.setSupplierId(supplierId);
        order.setDeptName(deptName);
        return order;
    }
}
