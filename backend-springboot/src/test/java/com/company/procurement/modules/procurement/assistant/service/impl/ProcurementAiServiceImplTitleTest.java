package com.company.procurement.modules.procurement.assistant.service.impl;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProcurementAiServiceImplTitleTest {

    @Test
    void buildsFormalTitleFromFullDepartmentPathAndLowValueType() {
        String title = ProcurementAiServiceImpl.buildAiPurchaseRequestTitle(
            "[N420D01337]世纪互联-业务共享中台-DC运维事业部-华北一部-酒仙桥-百度M1",
            "",
            "服务/费用/低值易耗等");

        assertThat(title).isEqualTo("百度M1-低值易耗采购申请");
    }

    @Test
    void buildsFormalTitleFromDisplayDepartmentAndAssetType() {
        String title = ProcurementAiServiceImpl.buildAiPurchaseRequestTitle(
            "酒仙桥 / M6机房 - [N420N00077]世纪互联-业务共享中台-DC运维事业部-华北一部-酒仙桥-M6",
            "",
            "实物资产/无形资产/长摊等");

        assertThat(title).isEqualTo("M6机房-资产采购申请");
    }

    @Test
    void buildsFormalTitleFromFallbackDepartmentAndProjectType() {
        String title = ProcurementAiServiceImpl.buildAiPurchaseRequestTitle(
            "",
            "酒仙桥-M6V&M5二三期 (2549)",
            "工程项目");

        assertThat(title).isEqualTo("M6V&M5二三期-工程项目采购申请");
    }
}
