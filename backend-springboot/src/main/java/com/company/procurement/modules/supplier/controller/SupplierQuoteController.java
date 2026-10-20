package com.company.procurement.modules.supplier.controller;

import com.company.procurement.common.export.ExportFile;
import com.company.procurement.common.export.ExcelExportUtils;
import com.company.procurement.common.export.ExcelImportUtils;
import com.company.procurement.common.exception.BizException;
import com.company.procurement.common.result.PageResult;
import com.company.procurement.common.result.Result;
import com.company.procurement.modules.platform.importlog.service.ImportFileLogService;
import com.company.procurement.modules.procurement.request.service.EcommerceUrlService;
import com.company.procurement.modules.procurement.request.vo.PurchaseRequestVO;
import com.company.procurement.modules.supplier.dto.SupplierAwardConfirmDTO;
import com.company.procurement.modules.supplier.dto.SupplierAwardWaiveDTO;
import com.company.procurement.modules.supplier.dto.SupplierQuotePageQueryDTO;
import com.company.procurement.modules.supplier.dto.SupplierQuoteSubmitDTO;
import com.company.procurement.modules.supplier.service.SupplierQuoteService;
import com.company.procurement.modules.supplier.vo.SupplierQuoteRequestVO;
import com.company.procurement.modules.supplier.vo.SupplierQuoteVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/supplier")
@RequiredArgsConstructor
public class SupplierQuoteController {

    private final SupplierQuoteService supplierQuoteService;
    private final EcommerceUrlService ecommerceUrlService;
    private final ImportFileLogService importFileLogService;

    @GetMapping("/quote-requests/page")
    public Result<PageResult<SupplierQuoteRequestVO>> pageQuoteRequests(@Valid SupplierQuotePageQueryDTO queryDTO) {
        return Result.ok(supplierQuoteService.pageQuoteRequests(queryDTO));
    }

    @GetMapping("/quote-requests/{purchaseRequestId:\\d+}")
    public Result<PurchaseRequestVO> quoteRequestDetail(@PathVariable Long purchaseRequestId) {
        return Result.ok(supplierQuoteService.quoteRequestDetail(purchaseRequestId));
    }

    @GetMapping("/quotes/{purchaseRequestId:\\d+}")
    public Result<SupplierQuoteVO> myQuote(@PathVariable Long purchaseRequestId) {
        return Result.ok(supplierQuoteService.myQuote(purchaseRequestId));
    }

    @GetMapping("/quotes/{purchaseRequestId:\\d+}/award-confirmation-detail")
    public Result<SupplierQuoteVO> awardConfirmationDetail(@PathVariable Long purchaseRequestId) {
        return Result.ok(supplierQuoteService.awardConfirmationDetail(purchaseRequestId));
    }

    @PostMapping("/quotes")
    public Result<SupplierQuoteVO> submitQuote(@RequestBody @Valid SupplierQuoteSubmitDTO dto) {
        return Result.ok(supplierQuoteService.submitQuote(dto));
    }

    @PostMapping("/quotes/template-export")
    public ResponseEntity<byte[]> exportQuoteTemplate(@RequestBody List<List<String>> rows) {
        List<List<String>> normalizedRows = normalizeQuoteTemplateLinks(rows);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename("报价模板.xlsx", StandardCharsets.UTF_8)
                .build()
                .toString())
            .contentType(MediaType.parseMediaType(ExcelExportUtils.XLSX_CONTENT_TYPE))
            .body(ExcelExportUtils.toXlsxBytes("报价模板", normalizedRows, quoteTemplateOptions(normalizedRows)));
    }

    private List<List<String>> normalizeQuoteTemplateLinks(List<List<String>> sourceRows) {
        if (sourceRows == null || sourceRows.isEmpty()) {
            return List.of();
        }
        List<List<String>> rows = sourceRows.stream().<List<String>>map(ArrayList::new).toList();
        int linkColumn = headerIndex(rows.get(0), "电商链接");
        if (linkColumn < 0) {
            return rows;
        }
        for (int rowIndex = 1; rowIndex < rows.size(); rowIndex++) {
            List<String> row = rows.get(rowIndex);
            if (linkColumn >= row.size() || !StringUtils.hasText(row.get(linkColumn))) {
                continue;
            }
            try {
                row.set(linkColumn, ecommerceUrlService.normalizeForLine(row.get(linkColumn), rowIndex + 1));
            } catch (BizException ex) {
                row.set(linkColumn, "链接无效，请联系采购人");
            }
        }
        return rows;
    }

    private ExcelExportUtils.WorkbookOptions quoteTemplateOptions(List<List<String>> rows) {
        List<String> headers = rows == null || rows.isEmpty() ? List.of() : rows.get(0);
        ExcelExportUtils.Builder builder = ExcelExportUtils.WorkbookOptions.builder()
            .freezeHeader(true)
            .autoFilter(true);
        for (String header : List.of("行号", "物料编码", "商品名称", "规格型号")) {
            requiredIfPresent(builder, headers, header);
        }
        dropdownIfPresent(builder, headers, "是否放弃报价", List.of("是", "否"));
        dropdownIfPresent(builder, headers, "是否替换商品", List.of("是", "否"));
        dropdownIfPresent(builder, headers, "发票类型", List.of("增值税专用发票", "增值税普通发票"));
        dropdownIfPresent(builder, headers, "税率", List.of("0%", "1%", "3%", "6%", "9%", "13%"));
        for (String header : List.of("行号", "物料编码", "商品名称", "规格型号", "电商链接", "收货城市", "数量", "单位", "报价说明")) {
            noteIfPresent(builder, headers, header, "勿改：系统生成字段，回传时用于匹配采购明细。");
        }
        hyperlinkIfPresent(builder, headers, "电商链接");
        hyperlinkIfPresent(builder, headers, "替换商品链接");
        for (String header : List.of("是否放弃报价", "报价备注", "是否替换商品")) {
            noteIfPresent(builder, headers, header, "可填写：供应商报价时填写。");
        }
        for (String header : List.of("替换商品名称", "替换规格型号", "替换商品链接", "替换原因")) {
            noteIfPresent(builder, headers, header, "选择“是否替换商品=是”时必填。");
        }
        noteIfPresent(builder, headers, "报价单价", "报价时必填且必须大于 0；放弃报价时可留空。");
        noteIfPresent(builder, headers, "交期天数", "报价时必填且必须大于 0。");
        noteIfPresent(builder, headers, "替换商品单价", "选择替换商品时必填且必须大于 0。");
        noteIfPresent(builder, headers, "发票类型", "选择增值税专用发票或增值税普通发票；专票可抵扣，普票不可抵扣。");
        noteIfPresent(builder, headers, "税率", "填写供应商报价对应税率，系统按无税金额参与比价。");
        return builder.build();
    }

    private void dropdownIfPresent(ExcelExportUtils.Builder builder, List<String> headers, String header, List<String> options) {
        int index = headerIndex(headers, header);
        if (index >= 0) {
            builder.dropdown(index, options);
        }
    }

    private void noteIfPresent(ExcelExportUtils.Builder builder, List<String> headers, String header, String note) {
        int index = headerIndex(headers, header);
        if (index >= 0) {
            builder.note(0, index, note);
        }
    }

    private void requiredIfPresent(ExcelExportUtils.Builder builder, List<String> headers, String header) {
        int index = headerIndex(headers, header);
        if (index >= 0) {
            builder.requiredColumns(index);
        }
    }

    private void hyperlinkIfPresent(ExcelExportUtils.Builder builder, List<String> headers, String header) {
        int index = headerIndex(headers, header);
        if (index >= 0) {
            builder.hyperlinkColumns(index);
        }
    }

    private int headerIndex(List<String> headers, String header) {
        String normalized = ExcelImportUtils.normalizeHeader(header);
        for (int index = 0; index < headers.size(); index++) {
            if (ExcelImportUtils.normalizeHeader(headers.get(index)).equals(normalized)) {
                return index;
            }
        }
        return -1;
    }

    @PostMapping("/quotes/import-preview")
    public Result<List<List<String>>> importQuotePreview(@RequestParam("file") MultipartFile file) {
        Long logId = importFileLogService.recordWebImport("SUPPLIER_QUOTE_PREVIEW", "供应商报价导入预览", file);
        try {
            List<List<String>> rows = ExcelImportUtils.readFirstSheet(file);
            int dataRows = dataRowCount(rows);
            importFileLogService.markSuccess(logId, dataRows, dataRows, 0, "读取供应商报价预览 " + dataRows + " 条");
            return Result.ok(rows);
        } catch (RuntimeException ex) {
            importFileLogService.markFailed(logId, ex);
            throw ex;
        }
    }

    @PostMapping("/quotes/{purchaseRequestId:\\d+}/award-confirmation")
    public Result<SupplierQuoteVO> confirmAward(@PathVariable Long purchaseRequestId,
                                                @RequestBody(required = false) @Valid SupplierAwardConfirmDTO dto) {
        return Result.ok(supplierQuoteService.confirmAward(
            purchaseRequestId,
            dto == null ? new SupplierAwardConfirmDTO() : dto
        ));
    }

    @PostMapping("/quotes/{purchaseRequestId:\\d+}/award-waiver")
    public Result<SupplierQuoteVO> waiveAward(@PathVariable Long purchaseRequestId,
                                              @RequestBody(required = false) @Valid SupplierAwardWaiveDTO dto) {
        return Result.ok(supplierQuoteService.waiveAward(
            purchaseRequestId,
            dto == null ? new SupplierAwardWaiveDTO() : dto
        ));
    }

    @GetMapping("/quotes/{purchaseRequestId:\\d+}/award-export")
    public ResponseEntity<byte[]> exportAward(@PathVariable Long purchaseRequestId) {
        ExportFile file = supplierQuoteService.exportAward(purchaseRequestId);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename(file.fileName(), StandardCharsets.UTF_8)
                .build()
                .toString())
            .contentType(MediaType.parseMediaType(file.contentType()))
            .body(file.content());
    }

    private int dataRowCount(List<List<String>> rows) {
        if (rows == null || rows.size() <= 1) {
            return 0;
        }
        return (int) rows.stream()
            .skip(1)
            .filter(row -> row != null && row.stream().anyMatch(cell -> cell != null && !cell.trim().isEmpty()))
            .count();
    }
}
