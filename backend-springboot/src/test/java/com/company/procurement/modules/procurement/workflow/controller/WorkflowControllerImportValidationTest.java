package com.company.procurement.modules.procurement.workflow.controller;

import com.company.procurement.common.exception.BizException;
import com.company.procurement.modules.procurement.request.service.EcommerceUrlService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowControllerImportValidationTest {

    @Test
    void rejectsUnrelatedWorkbookHeaders() {
        List<List<String>> rows = List.of(
            List.of("姓名", "手机号"),
            List.of("测试用户", "13800000000")
        );

        assertThatThrownBy(() -> WorkflowController.validatePurchaseItemImportRows(rows))
            .isInstanceOf(BizException.class)
            .extracting("code")
            .isEqualTo("PURCHASE_ITEM_IMPORT_HEADER_INVALID");
    }

    @Test
    void rejectsInvalidRequiredValues() {
        List<List<String>> rows = List.of(
            List.of("商品名称", "采购类型", "单位", "数量", "单价", "需求日期", "采购原因"),
            List.of("测试商品", "服务/费用/低值易耗等", "件", "0", "10", "2026/07/22", "测试")
        );

        assertThatThrownBy(() -> WorkflowController.validatePurchaseItemImportRows(rows))
            .isInstanceOf(BizException.class)
            .extracting("code")
            .isEqualTo("PURCHASE_ITEM_IMPORT_NUMBER_INVALID");
    }

    @Test
    void acceptsValidTemplateRows() {
        List<List<String>> rows = List.of(
            List.of("商品名称", "采购类型", "规格型号", "单位", "数量", "单价", "需求日期", "采购原因"),
            List.of("测试商品", "服务/费用/低值易耗等", "标准", "件", "2", "10.50", "2026-07-30", "测试")
        );

        assertThatCode(() -> WorkflowController.validatePurchaseItemImportRows(rows))
            .doesNotThrowAnyException();
    }

    @Test
    void rejectsFractionalQuantityBeforePreviewSucceeds() {
        List<List<String>> rows = List.of(
            List.of("商品名称", "采购类型", "单位", "数量", "单价", "采购原因"),
            List.of("测试商品", "服务/费用/低值易耗等", "件", "1.5", "10", "测试")
        );

        assertThatThrownBy(() -> WorkflowController.validatePurchaseItemImportRows(rows))
            .isInstanceOf(BizException.class)
            .extracting("code")
            .isEqualTo("PURCHASE_ITEM_IMPORT_QUANTITY_INVALID");
    }

    @Test
    void normalizesMobileProductLinkDuringImportPreview() {
        List<List<String>> rows = new ArrayList<>();
        rows.add(new ArrayList<>(List.of(
            "商品名称", "采购类型", "规格型号", "单位", "数量", "单价", "需求日期", "采购原因", "电商链接"
        )));
        rows.add(new ArrayList<>(List.of(
            "山特 UPS", "服务/费用/低值易耗等", "TG500", "台", "1", "500", "2026-08-10", "机房备件",
            "https://item.m.jd.com/product/100104072733.html?utm_source=share"
        )));
        WorkflowController controller = new WorkflowController(null, null, null, null, new EcommerceUrlService(), null);

        controller.normalizePurchaseItemLinks(rows);

        assertThat(rows.get(1).get(8)).isEqualTo("https://item.jd.com/100104072733.html");
    }

    @Test
    void rejectsLoginPageDuringImportPreview() {
        List<List<String>> rows = new ArrayList<>();
        rows.add(new ArrayList<>(List.of(
            "商品名称", "采购类型", "规格型号", "单位", "数量", "单价", "需求日期", "采购原因", "电商链接"
        )));
        rows.add(new ArrayList<>(List.of(
            "硅胶管", "服务/费用/低值易耗等", "100mm", "件", "1", "10", "2026-08-10", "机房备件",
            "https://login.taobao.com/member/login.jhtml"
        )));
        WorkflowController controller = new WorkflowController(null, null, null, null, new EcommerceUrlService(), null);

        assertThatThrownBy(() -> controller.normalizePurchaseItemLinks(rows))
            .isInstanceOf(BizException.class)
            .extracting("code")
            .isEqualTo("ECOMMERCE_URL_INVALID");
    }
}
