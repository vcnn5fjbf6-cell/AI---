package com.company.procurement.modules.supplier.service.impl;

import com.company.procurement.common.export.ExcelImportUtils;
import com.company.procurement.common.export.ExportFile;
import com.company.procurement.modules.procurement.request.entity.PurchaseRequest;
import com.company.procurement.modules.procurement.request.entity.PurchaseRequestItem;
import com.company.procurement.modules.procurement.request.service.EcommerceUrlService;
import com.company.procurement.modules.supplier.entity.Supplier;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SupplierFeishuNotificationServiceImplQuoteTemplateTest {

    @Test
    void quoteTemplateMarksEditableAndReadonlyColumnsWithDropdowns() throws Exception {
        SupplierFeishuNotificationServiceImpl service = newService();
        ExportFile file = invokeBuildQuoteTemplateFile(service);

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(file.content()))) {
            Sheet sheet = workbook.getSheet("询价单");

            assertThat(cellText(sheet, 0, 0)).isEqualTo("行号（勿改）");
            assertThat(cellText(sheet, 0, 8)).isEqualTo("是否放弃报价（可填）");
            assertThat(cellText(sheet, 0, 9)).isEqualTo("原商品成色（可填）");
            assertThat(cellText(sheet, 0, 11)).isEqualTo("报价单价（可填）");
            assertThat(cellText(sheet, 0, 12)).isEqualTo("交期天数（可填，默认7天）");
            assertThat(cellText(sheet, 0, 14)).isEqualTo("是否替换商品（可填）");
            assertThat(cellText(sheet, 0, 18)).isEqualTo("替换商品成色（可填）");
            assertThat(cellText(sheet, 0, 22)).isEqualTo("报价说明（勿改）");
            assertThat(cellText(sheet, 1, 8)).isEqualTo("否");
            assertThat(cellText(sheet, 1, 9)).isEqualTo("全新");
            assertThat(cellText(sheet, 1, 12)).isEqualTo("7");
            assertThat(cellText(sheet, 1, 14)).isEqualTo("否");
            assertThat(cellText(sheet, 1, 18)).isEqualTo("全新");
            assertThat(cellText(sheet, 1, 4)).isEqualTo("https://item.jd.com/100104072733.html");
            assertThat(sheet.getRow(1).getCell(4).getHyperlink().getAddress())
                .isEqualTo("https://item.jd.com/100104072733.html");

            assertThat(headerComment(sheet, 8)).contains("可填写");
            assertThat(headerComment(sheet, 9)).contains("可填写");
            assertThat(headerComment(sheet, 12)).contains("默认 7 天");
            assertThat(headerComment(sheet, 14)).contains("可填写");
            assertThat(headerComment(sheet, 15)).contains("替换商品=是");
            assertThat(headerComment(sheet, 16)).contains("替换商品=是");
            assertThat(headerComment(sheet, 17)).contains("替换商品=是");
            assertThat(headerComment(sheet, 18)).contains("可填写");
            assertThat(headerComment(sheet, 0)).contains("勿改");
            assertThat(headerComment(sheet, 22)).contains("勿改");
            assertThat(hasDropdownOnColumn(sheet, 8)).isTrue();
            assertThat(hasDropdownOnColumn(sheet, 9)).isTrue();
            assertThat(hasDropdownOnColumn(sheet, 14)).isTrue();
            assertThat(hasDropdownOnColumn(sheet, 18)).isTrue();
        }

        assertThat(ExcelImportUtils.normalizeHeader("是否放弃报价（可填）")).isEqualTo("是否放弃报价");
        assertThat(ExcelImportUtils.normalizeHeader("替换商品链接（替换时必填）")).isEqualTo("替换商品链接");
        assertThat(ExcelImportUtils.normalizeHeader("采购明细ID（勿改）")).isEqualTo("采购明细ID");
    }

    @Test
    void quoteImportDefaultsBlankLeadTimeToSevenDaysAndKeepsExplicitValue() throws Exception {
        SupplierFeishuNotificationServiceImpl service = newService();
        Method method = SupplierFeishuNotificationServiceImpl.class.getDeclaredMethod(
            "parseQuoteLeadTimeDays",
            Map.class
        );
        method.setAccessible(true);

        assertThat(method.invoke(service, Map.of(
            ExcelImportUtils.normalizeHeader("交期天数（可填，默认7天）"),
            ""
        ))).isEqualTo(7);
        assertThat(method.invoke(service, Map.of(
            ExcelImportUtils.normalizeHeader("交期天数"),
            "12"
        ))).isEqualTo(12);
        assertThat(method.invoke(service, Map.of(
            ExcelImportUtils.normalizeHeader("交期天数"),
            "0"
        ))).isEqualTo(0);
    }

    @Test
    void quoteTemplateReplacesHistoricalLoginPageWithActionableWarning() throws Exception {
        SupplierFeishuNotificationServiceImpl service = newService();
        PurchaseRequestItem invalidItem = item();
        invalidItem.setEcommerceUrl("https://login.taobao.com/havanaone/render");
        ExportFile file = invokeBuildQuoteTemplateFile(service, List.of(invalidItem));

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(file.content()))) {
            var cell = workbook.getSheet("询价单").getRow(1).getCell(4);
            assertThat(cell.getStringCellValue()).isEqualTo("链接无效，请联系采购人");
            assertThat(cell.getHyperlink()).isNull();
        }
    }

    private SupplierFeishuNotificationServiceImpl newService() throws Exception {
        Constructor<?> constructor = SupplierFeishuNotificationServiceImpl.class.getDeclaredConstructors()[0];
        Object[] args = new Object[constructor.getParameterCount()];
        Class<?>[] parameterTypes = constructor.getParameterTypes();
        for (int index = 0; index < parameterTypes.length; index++) {
            if (parameterTypes[index] == EcommerceUrlService.class) {
                args[index] = new EcommerceUrlService();
            }
        }
        return (SupplierFeishuNotificationServiceImpl) constructor.newInstance(args);
    }

    private ExportFile invokeBuildQuoteTemplateFile(SupplierFeishuNotificationServiceImpl service) throws Exception {
        return invokeBuildQuoteTemplateFile(service, List.of(item()));
    }

    private ExportFile invokeBuildQuoteTemplateFile(SupplierFeishuNotificationServiceImpl service,
                                                    List<PurchaseRequestItem> items) throws Exception {
        Method method = SupplierFeishuNotificationServiceImpl.class.getDeclaredMethod(
            "buildQuoteTemplateFile",
            PurchaseRequest.class,
            Supplier.class,
            List.class
        );
        method.setAccessible(true);
        return (ExportFile) method.invoke(service, request(), supplier(), items);
    }

    private PurchaseRequest request() {
        PurchaseRequest request = new PurchaseRequest();
        request.setId(100L);
        request.setReqNo("HZCG-000003");
        return request;
    }

    private Supplier supplier() {
        Supplier supplier = new Supplier();
        supplier.setId(200L);
        supplier.setSupplierCode("SUP-001");
        return supplier;
    }

    private PurchaseRequestItem item() {
        PurchaseRequestItem item = new PurchaseRequestItem();
        item.setId(300L);
        item.setLineNo(1);
        item.setItemCode("MAT-XB7X9U");
        item.setItemName("测试数据");
        item.setSpecModel("123");
        item.setReceivingCity("北京");
        item.setQty(new BigDecimal("20"));
        item.setUnit("件");
        item.setEcommerceUrl("https://item.m.jd.com/product/100104072733.html?utm_source=share");
        return item;
    }

    private String cellText(Sheet sheet, int rowIndex, int columnIndex) {
        return sheet.getRow(rowIndex).getCell(columnIndex).getStringCellValue();
    }

    private String headerComment(Sheet sheet, int columnIndex) {
        return sheet.getRow(0).getCell(columnIndex).getCellComment().getString().getString();
    }

    private boolean hasDropdownOnColumn(Sheet sheet, int columnIndex) {
        for (DataValidation validation : sheet.getDataValidations()) {
            for (var range : validation.getRegions().getCellRangeAddresses()) {
                if (range.getFirstColumn() == columnIndex && range.getLastColumn() == columnIndex) {
                    return true;
                }
            }
        }
        return false;
    }
}
