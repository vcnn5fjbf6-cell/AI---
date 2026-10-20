package com.company.procurement.modules.procurement.workflow.controller;

import com.company.procurement.common.export.ExportFile;
import com.company.procurement.common.export.ExcelExportUtils;
import com.company.procurement.common.export.ExcelImportUtils;
import com.company.procurement.common.exception.BizException;
import com.company.procurement.common.result.PageResult;
import com.company.procurement.common.result.Result;
import com.company.procurement.modules.approval.record.vo.ApprovalRecordVO;
import com.company.procurement.modules.platform.importlog.service.ImportFileLogService;
import com.company.procurement.modules.procurement.request.dto.PurchaseRequestCreateDTO;
import com.company.procurement.modules.procurement.request.dto.PurchaseRequestPageQueryDTO;
import com.company.procurement.modules.procurement.request.dto.PurchaseRequestUpdateDTO;
import com.company.procurement.modules.procurement.request.vo.PurchaseRequestVO;
import com.company.procurement.modules.procurement.assistant.service.ProcurementComplianceReviewService;
import com.company.procurement.modules.procurement.assistant.service.ProcurementPurposeOptimizationService;
import com.company.procurement.modules.procurement.assistant.dto.ProcurementPurposeOptimizeDTO;
import com.company.procurement.modules.procurement.assistant.vo.ProcurementCompliancePrecheckVO;
import com.company.procurement.modules.procurement.assistant.vo.ProcurementPurposeOptimizationVO;
import com.company.procurement.modules.procurement.workflow.dto.WorkflowApprovalActionDTO;
import com.company.procurement.modules.procurement.workflow.dto.WorkflowMessageReadDTO;
import com.company.procurement.modules.procurement.workflow.dto.WorkflowQuoteConfirmDTO;
import com.company.procurement.modules.procurement.workflow.dto.WorkflowQuoteDeadlineExtendDTO;
import com.company.procurement.modules.procurement.request.service.EcommerceUrlService;
import com.company.procurement.modules.procurement.request.service.PurchaseRequestNoService;
import com.company.procurement.modules.procurement.workflow.service.WorkflowService;
import com.company.procurement.modules.procurement.workflow.vo.PurchaseRequestNoVO;
import com.company.procurement.modules.procurement.workflow.vo.WorkflowDashboardVO;
import com.company.procurement.modules.procurement.workflow.vo.WorkflowSubmitResultVO;
import com.company.procurement.modules.supplier.vo.SupplierQuoteSummaryVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/workflow")
@RequiredArgsConstructor
public class WorkflowController {

    private static final List<String> PURCHASE_TYPE_OPTIONS = List.of("工程项目", "实物资产/无形资产/长摊等", "服务/费用/低值易耗等");
    private static final List<String> PURCHASE_UNIT_OPTIONS = List.of("件", "台", "个", "套", "批", "张", "支", "米", "箱", "条", "双", "副", "只", "份");
    private static final List<List<String>> PURCHASE_ITEM_IMPORT_TEMPLATE = List.of(
        List.of("商品名称", "采购类型", "规格型号", "单位", "数量", "单价", "需求日期", "采购原因", "电商链接", "物料编码"),
        List.of("示例商品", "服务/费用/低值易耗等", "规格型号", "件", "1", "1", "2026-05-30", "填写购买原因", "https://...", "")
    );

    private final WorkflowService workflowService;
    private final ProcurementComplianceReviewService complianceReviewService;
    private final ProcurementPurposeOptimizationService purposeOptimizationService;
    private final PurchaseRequestNoService requestNoService;
    private final EcommerceUrlService ecommerceUrlService;
    private final ImportFileLogService importFileLogService;

    @GetMapping("/dashboard")
    public Result<WorkflowDashboardVO> dashboard() {
        return Result.ok(workflowService.getDashboard());
    }

    @GetMapping("/purchase-requests/next-no")
    public Result<PurchaseRequestNoVO> nextRequestNo(@RequestParam(required = false) Integer urgencyLevel) {
        PurchaseRequestNoVO vo = new PurchaseRequestNoVO();
        vo.setReqNo(requestNoService.allocate(urgencyLevel));
        return Result.ok(vo);
    }

    @GetMapping("/purchase-requests/page")
    public Result<PageResult<PurchaseRequestVO>> pageVisibleRequests(@Valid PurchaseRequestPageQueryDTO queryDTO) {
        return Result.ok(workflowService.pageVisibleRequests(queryDTO));
    }

    @GetMapping("/purchase-requests/rejected-item-messages")
    public Result<List<PurchaseRequestVO>> rejectedItemMessages() {
        return Result.ok(workflowService.rejectedItemMessages());
    }

    @GetMapping("/purchase-requests/rejected-messages/read-keys")
    public Result<List<String>> rejectedMessageReadKeys() {
        return Result.ok(workflowService.rejectedMessageReadKeys());
    }

    @PostMapping("/purchase-requests/rejected-messages/read")
    public Result<Void> markRejectedMessagesRead(@RequestBody @Valid WorkflowMessageReadDTO dto) {
        workflowService.markRejectedMessagesRead(dto.getMessageKeys());
        return Result.ok();
    }

    @GetMapping("/purchase-requests/rejected-messages/clear-keys")
    public Result<List<String>> rejectedMessageClearedKeys() {
        return Result.ok(workflowService.rejectedMessageClearedKeys());
    }

    @PostMapping("/purchase-requests/rejected-messages/clear")
    public Result<Void> clearRejectedMessages(@RequestBody @Valid WorkflowMessageReadDTO dto) {
        workflowService.clearRejectedMessages(dto.getMessageKeys());
        return Result.ok();
    }

    @GetMapping("/purchase-requests/{id:\\d+}")
    public Result<PurchaseRequestVO> detailVisibleRequest(@PathVariable Long id) {
        return Result.ok(workflowService.detailVisibleRequest(id));
    }

    @PostMapping("/purchase-requests")
    public Result<Long> createRequest(@RequestBody @Valid PurchaseRequestCreateDTO dto) {
        return Result.ok(workflowService.createRequest(dto));
    }

    @PostMapping("/purchase-requests/ai-precheck")
    public Result<ProcurementCompliancePrecheckVO> precheckRequest(@RequestBody @Valid PurchaseRequestCreateDTO dto) {
        return Result.ok(complianceReviewService.precheck(dto));
    }

    @PostMapping("/purchase-requests/purpose-optimize")
    public Result<ProcurementPurposeOptimizationVO> optimizePurpose(
        @RequestBody @Valid ProcurementPurposeOptimizeDTO dto) {
        return Result.ok(purposeOptimizationService.optimize(dto.getPurpose()));
    }

    @PutMapping("/purchase-requests/{id:\\d+}")
    public Result<Void> updateRequest(@PathVariable Long id, @RequestBody @Valid PurchaseRequestUpdateDTO dto) {
        workflowService.updateRequest(id, dto);
        return Result.ok();
    }

    @PostMapping("/purchase-requests/{id:\\d+}/close")
    public Result<Void> closeReturnedRequest(@PathVariable Long id) {
        workflowService.closeReturnedRequest(id);
        return Result.ok();
    }

    @GetMapping("/purchase-requests/items/template")
    public ResponseEntity<byte[]> exportPurchaseItemTemplate() {
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename("采购明细批量导入模板.xlsx", StandardCharsets.UTF_8)
                .build()
                .toString())
            .contentType(MediaType.parseMediaType(ExcelExportUtils.XLSX_CONTENT_TYPE))
            .body(ExcelExportUtils.toXlsxBytes("采购明细", PURCHASE_ITEM_IMPORT_TEMPLATE, purchaseItemTemplateOptions()));
    }

    private ExcelExportUtils.WorkbookOptions purchaseItemTemplateOptions() {
        return ExcelExportUtils.WorkbookOptions.builder()
            .freezeHeader(true)
            .autoFilter(true)
            .requiredColumns(0, 1, 3, 4, 5, 7)
            .dropdown(1, PURCHASE_TYPE_OPTIONS)
            .dropdown(3, PURCHASE_UNIT_OPTIONS)
            .note(0, 0, "必填，填写具体商品名称。")
            .note(0, 1, "必填，必须从下拉中选择：工程项目、实物资产/无形资产/长摊等、服务/费用/低值易耗等。")
            .note(0, 3, "必填，建议从下拉选择常用单位。")
            .note(0, 4, "必填，数量必须大于 0。")
            .note(0, 5, "必填，单价必须大于 0。")
            .note(0, 6, "可填 yyyy-MM-dd；不填时按业务默认需求日期处理。")
            .note(0, 7, "必填，说明购买原因或用途。")
            .note(0, 8, "可填京东、淘宝、1688 等参考链接。")
            .build();
    }

    @PostMapping("/purchase-requests/items/import-preview")
    public Result<List<List<String>>> importPurchaseItemPreview(@RequestParam("file") MultipartFile file) {
        Long logId = importFileLogService.recordWebImport("PURCHASE_ITEM_PREVIEW", "采购明细导入预览", file);
        try {
            List<List<String>> rows = ExcelImportUtils.readFirstSheet(file);
            validatePurchaseItemImportRows(rows);
            normalizePurchaseItemLinks(rows);
            int dataRows = dataRowCount(rows);
            importFileLogService.markSuccess(logId, dataRows, dataRows, 0, "读取采购明细预览 " + dataRows + " 条");
            return Result.ok(rows);
        } catch (RuntimeException ex) {
            importFileLogService.markFailed(logId, ex);
            throw ex;
        }
    }

    @PostMapping("/purchase-requests/{id:\\d+}/submit")
    public Result<WorkflowSubmitResultVO> submitRequest(@PathVariable Long id) {
        return Result.ok(workflowService.submitRequest(id));
    }

    @GetMapping("/purchase-requests/{id:\\d+}/submit-preview")
    public Result<WorkflowSubmitResultVO> previewSubmitRequest(@PathVariable Long id) {
        return Result.ok(workflowService.previewSubmitRequest(id));
    }

    @GetMapping("/approval-tasks/page")
    public Result<PageResult<PurchaseRequestVO>> pageApprovalTasks(@Valid PurchaseRequestPageQueryDTO queryDTO) {
        return Result.ok(workflowService.pageApprovalTasks(queryDTO));
    }

    @PostMapping("/purchase-requests/{id:\\d+}/approval")
    public Result<WorkflowSubmitResultVO> approveRequest(@PathVariable Long id, @RequestBody @Valid WorkflowApprovalActionDTO dto) {
        return Result.ok(workflowService.approveRequest(id, dto));
    }

    @PostMapping("/demand-pool/publish")
    public Result<Integer> publishDemandPoolRequests() {
        return Result.ok(workflowService.publishDemandPoolRequests());
    }

    @PostMapping("/demand-pool/publish-test")
    public Result<Integer> publishDemandPoolRequestsForTest() {
        return Result.ok(workflowService.publishDemandPoolRequestsForTest());
    }

    @PostMapping("/approval-tasks/timeout-test")
    public Result<Integer> processApprovalTimeoutsForTest() {
        return Result.ok(workflowService.processApprovalTimeoutsForTest());
    }

    @GetMapping("/purchase-requests/{id:\\d+}/records")
    public Result<List<ApprovalRecordVO>> approvalRecords(@PathVariable Long id) {
        return Result.ok(workflowService.listApprovalRecords(id));
    }

    @GetMapping("/purchase-requests/{id:\\d+}/quotes")
    public Result<SupplierQuoteSummaryVO> quoteSummary(@PathVariable Long id) {
        return Result.ok(workflowService.getQuoteSummary(id));
    }

    @PostMapping("/purchase-requests/{id:\\d+}/quote-confirmation")
    public Result<SupplierQuoteSummaryVO> confirmWinningQuote(@PathVariable Long id,
                                                              @RequestBody @Valid WorkflowQuoteConfirmDTO dto) {
        return Result.ok(workflowService.confirmWinningQuote(id, dto));
    }

    @PostMapping("/purchase-requests/{id:\\d+}/quote-deadline-extension")
    public Result<PurchaseRequestVO> extendQuoteDeadline(@PathVariable Long id,
                                                         @RequestBody @Valid WorkflowQuoteDeadlineExtendDTO dto) {
        return Result.ok(workflowService.extendQuoteDeadline(id, dto));
    }

    @GetMapping("/purchase-requests/{id:\\d+}/quotes/export")
    public ResponseEntity<byte[]> exportQuoteComparison(@PathVariable Long id) {
        ExportFile file = workflowService.exportQuoteComparison(id);
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

    void normalizePurchaseItemLinks(List<List<String>> rows) {
        List<String> headers = rows.get(0).stream().map(ExcelImportUtils::normalizeHeader).toList();
        int linkColumn = headers.indexOf(ExcelImportUtils.normalizeHeader("电商链接"));
        if (linkColumn < 0) {
            return;
        }
        List<String> errors = new ArrayList<>();
        for (int rowIndex = 1; rowIndex < rows.size(); rowIndex++) {
            List<String> row = rows.get(rowIndex);
            if (linkColumn >= row.size() || !org.springframework.util.StringUtils.hasText(row.get(linkColumn))) {
                continue;
            }
            try {
                row.set(linkColumn, ecommerceUrlService.normalizeForLine(row.get(linkColumn), rowIndex + 1));
            } catch (BizException ex) {
                if (errors.size() < 12) {
                    errors.add(ex.getMessage());
                }
            }
        }
        if (!errors.isEmpty()) {
            throw new BizException("ECOMMERCE_URL_INVALID", String.join("；", errors));
        }
    }

    static void validatePurchaseItemImportRows(List<List<String>> rows) {
        if (rows == null || rows.isEmpty()) {
            throw new BizException("PURCHASE_ITEM_IMPORT_EMPTY", "采购明细导入文件没有内容");
        }
        List<String> headers = rows.get(0).stream().map(ExcelImportUtils::normalizeHeader).toList();
        for (String requiredHeader : List.of("商品名称", "采购类型", "单位", "数量", "单价", "采购原因")) {
            if (!headers.contains(ExcelImportUtils.normalizeHeader(requiredHeader))) {
                throw new BizException(
                    "PURCHASE_ITEM_IMPORT_HEADER_INVALID",
                    "采购明细导入文件缺少“" + requiredHeader + "”列"
                );
            }
        }

        int validRowCount = 0;
        List<Map<String, String>> dataRows = ExcelImportUtils.toHeaderMaps(rows);
        for (int index = 0; index < dataRows.size(); index++) {
            Map<String, String> row = dataRows.get(index);
            if (row.values().stream().noneMatch(org.springframework.util.StringUtils::hasText)) {
                continue;
            }
            int rowNo = index + 2;
            validRowCount++;
            requireImportValue(row, rowNo, "商品名称");
            String reqType = requireImportValue(row, rowNo, "采购类型");
            if (!PURCHASE_TYPE_OPTIONS.contains(reqType)) {
                throw new BizException("PURCHASE_ITEM_IMPORT_TYPE_INVALID", "第 " + rowNo + " 行采购类型不在模板选项中");
            }
            requireImportValue(row, rowNo, "单位");
            BigDecimal qty = parsePositiveDecimal(row, rowNo, "数量");
            if (qty.stripTrailingZeros().scale() > 0) {
                throw new BizException("PURCHASE_ITEM_IMPORT_QUANTITY_INVALID", "第 " + rowNo + " 行“数量”必须为大于 0 的整数");
            }
            parsePositiveDecimal(row, rowNo, "单价");
            requireImportValue(row, rowNo, "采购原因");
            String needDate = importValue(row, "需求日期");
            if (org.springframework.util.StringUtils.hasText(needDate)) {
                try {
                    LocalDate.parse(needDate);
                } catch (DateTimeParseException ex) {
                    throw new BizException("PURCHASE_ITEM_IMPORT_DATE_INVALID", "第 " + rowNo + " 行需求日期必须为 yyyy-MM-dd");
                }
            }
        }
        if (validRowCount == 0) {
            throw new BizException("PURCHASE_ITEM_IMPORT_EMPTY", "采购明细导入文件没有有效数据行");
        }
    }

    private static String requireImportValue(Map<String, String> row, int rowNo, String header) {
        String value = importValue(row, header);
        if (!org.springframework.util.StringUtils.hasText(value)) {
            throw new BizException("PURCHASE_ITEM_IMPORT_REQUIRED", "第 " + rowNo + " 行“" + header + "”不能为空");
        }
        return value.trim();
    }

    private static BigDecimal parsePositiveDecimal(Map<String, String> row, int rowNo, String header) {
        String value = requireImportValue(row, rowNo, header);
        try {
            BigDecimal parsed = new BigDecimal(value);
            if (parsed.compareTo(BigDecimal.ZERO) <= 0) {
                throw new NumberFormatException();
            }
            return parsed;
        } catch (NumberFormatException ex) {
            throw new BizException("PURCHASE_ITEM_IMPORT_NUMBER_INVALID", "第 " + rowNo + " 行“" + header + "”必须为大于 0 的数字");
        }
    }

    private static String importValue(Map<String, String> row, String header) {
        return row.getOrDefault(ExcelImportUtils.normalizeHeader(header), "");
    }
}
