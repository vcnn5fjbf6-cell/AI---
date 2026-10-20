package com.company.procurement.common.export;

import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExcelExportUtilsTest {

    @Test
    void writesConfiguredUrlColumnsAsClickableHyperlinks() throws Exception {
        String productUrl = "https://item.jd.com/100104072733.html";
        byte[] content = ExcelExportUtils.toXlsxBytes(
            "报价单",
            List.of(
                List.of("商品名称", "电商链接"),
                List.of("山特 UPS", productUrl)
            ),
            ExcelExportUtils.WorkbookOptions.builder()
                .hyperlinkColumns(1)
                .build()
        );

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(content))) {
            var cell = workbook.getSheet("报价单").getRow(1).getCell(1);
            assertThat(cell.getStringCellValue()).isEqualTo(productUrl);
            assertThat(cell.getHyperlink()).isNotNull();
            assertThat(cell.getHyperlink().getAddress()).isEqualTo(productUrl);
        }
    }

    @Test
    void doesNotCreateHyperlinkForShareTextThatWasNotNormalized() throws Exception {
        byte[] content = ExcelExportUtils.toXlsxBytes(
            "报价单",
            List.of(
                List.of("电商链接"),
                List.of("【京东】商品 https://3.cn/abc")
            ),
            ExcelExportUtils.WorkbookOptions.builder()
                .hyperlinkColumns(0)
                .build()
        );

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(content))) {
            assertThat(workbook.getSheet("报价单").getRow(1).getCell(0).getHyperlink()).isNull();
        }
    }
}
