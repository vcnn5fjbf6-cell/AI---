package com.company.procurement.modules.procurement.order.controller;

import com.company.procurement.common.export.ExportFile;
import com.company.procurement.common.result.PageResult;
import com.company.procurement.common.result.Result;
import com.company.procurement.modules.procurement.order.dto.AfterSaleApprovalDTO;
import com.company.procurement.modules.procurement.order.dto.AfterSaleCreateDTO;
import com.company.procurement.modules.procurement.order.dto.OrderReceiptDTO;
import com.company.procurement.modules.procurement.order.dto.OrderInvoiceReviewDTO;
import com.company.procurement.modules.procurement.order.dto.ProcurementOrderPageQueryDTO;
import com.company.procurement.modules.procurement.order.service.ProcurementOrderService;
import com.company.procurement.modules.procurement.order.vo.AfterSaleVO;
import com.company.procurement.modules.procurement.order.service.OrderInvoiceService;
import com.company.procurement.modules.procurement.order.vo.OrderInvoiceSubmissionVO;
import com.company.procurement.modules.procurement.order.vo.ProcurementOrderVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/procurement/orders")
@RequiredArgsConstructor
public class ProcurementOrderController {

    private final ProcurementOrderService procurementOrderService;

    @GetMapping("/my/page")
    public Result<PageResult<ProcurementOrderVO>> pageMyOrders(@Valid ProcurementOrderPageQueryDTO queryDTO) {
        return Result.ok(procurementOrderService.pageMyOrders(queryDTO));
    }

    @GetMapping("/my/{quoteItemId:\\d+}")
    public Result<ProcurementOrderVO> orderDetail(@PathVariable Long quoteItemId) {
        return Result.ok(procurementOrderService.orderDetail(quoteItemId));
    }

    @GetMapping("/my/groups/{requestNo:[A-Za-z0-9_-]+}")
    public Result<java.util.List<ProcurementOrderVO>> listOrdersByRequestNo(@PathVariable String requestNo) {
        return Result.ok(procurementOrderService.listMyOrdersByRequestNo(requestNo));
    }

    @GetMapping("/my/groups/{requestNo:[A-Za-z0-9_-]+}/export")
    public ResponseEntity<byte[]> exportOrdersByRequestNo(
        @PathVariable String requestNo,
        @RequestParam(value = "deptName", required = false) String deptName,
        @RequestParam(value = "supplierId", required = false) Long supplierId
    ) {
        ExportFile file = procurementOrderService.exportOrdersByRequestNo(requestNo, deptName, supplierId);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename(file.fileName(), StandardCharsets.UTF_8)
                .build()
                .toString())
            .contentType(MediaType.parseMediaType(file.contentType()))
            .body(file.content());
    }

    @GetMapping("/my/{quoteItemId:\\d+}/export")
    public ResponseEntity<byte[]> exportOrder(@PathVariable Long quoteItemId) {
        ExportFile file = procurementOrderService.exportOrder(quoteItemId);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename(file.fileName(), StandardCharsets.UTF_8)
                .build()
                .toString())
            .contentType(MediaType.parseMediaType(file.contentType()))
            .body(file.content());
    }

    @GetMapping("/my/{quoteItemId:\\d+}/invoices")
    public Result<List<OrderInvoiceSubmissionVO>> listInvoices(
        @PathVariable Long quoteItemId,
        @RequestParam(value = "documentType", defaultValue = "INVOICE") String documentType
    ) {
        return Result.ok(procurementOrderService.listInvoices(quoteItemId, documentType));
    }

    @PostMapping(value = "/my/{quoteItemId:\\d+}/invoices", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<OrderInvoiceSubmissionVO> uploadInvoice(
        @PathVariable Long quoteItemId,
        @RequestParam("files") List<MultipartFile> files,
        @RequestParam(value = "documentType", defaultValue = "INVOICE") String documentType,
        @RequestParam(value = "invoiceNo", required = false) String invoiceNo,
        @RequestParam(value = "invoiceDate", required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate invoiceDate,
        @RequestParam(value = "totalAmount", required = false) BigDecimal totalAmount,
        @RequestParam(value = "invoiceType", required = false) String invoiceType,
        @RequestParam(value = "remark", required = false) String remark
    ) {
        return Result.ok(procurementOrderService.uploadInvoice(
            quoteItemId,
            documentType,
            files,
            invoiceNo,
            invoiceDate,
            totalAmount,
            invoiceType,
            remark
        ));
    }

    @PostMapping("/my/{quoteItemId:\\d+}/invoices/{submissionId:\\d+}/accept")
    public Result<OrderInvoiceSubmissionVO> acceptInvoice(@PathVariable Long quoteItemId,
                                                          @PathVariable Long submissionId,
                                                          @RequestBody(required = false) @Valid OrderInvoiceReviewDTO dto) {
        return Result.ok(procurementOrderService.acceptInvoice(quoteItemId, submissionId, dto));
    }

    @PostMapping("/my/{quoteItemId:\\d+}/invoices/{submissionId:\\d+}/return")
    public Result<OrderInvoiceSubmissionVO> returnInvoice(@PathVariable Long quoteItemId,
                                                          @PathVariable Long submissionId,
                                                          @RequestBody(required = false) @Valid OrderInvoiceReviewDTO dto) {
        return Result.ok(procurementOrderService.returnInvoice(quoteItemId, submissionId, dto));
    }

    @GetMapping("/my/{quoteItemId:\\d+}/invoices/attachments/{attachmentId:\\d+}/download")
    public ResponseEntity<?> downloadInvoiceAttachment(@PathVariable Long quoteItemId,
                                                       @PathVariable Long attachmentId) {
        OrderInvoiceService.AttachmentResource attachment = procurementOrderService.loadInvoiceAttachment(quoteItemId, attachmentId);
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        if (StringUtils.hasText(attachment.contentType())) {
            try {
                mediaType = MediaType.parseMediaType(attachment.contentType());
            } catch (RuntimeException ignored) {
                mediaType = MediaType.APPLICATION_OCTET_STREAM;
            }
        }
        return ResponseEntity.ok()
            .contentType(mediaType)
            .contentLength(attachment.contentLength())
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .header("X-Content-Type-Options", "nosniff")
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename(attachment.fileName(), StandardCharsets.UTF_8)
                .build()
                .toString())
            .body(attachment.resource());
    }

    @PostMapping("/my/{quoteItemId:\\d+}/receipt")
    public Result<ProcurementOrderVO> confirmReceipt(@PathVariable Long quoteItemId,
                                                     @RequestBody(required = false) OrderReceiptDTO dto) {
        return Result.ok(procurementOrderService.confirmReceipt(quoteItemId, dto == null ? new OrderReceiptDTO() : dto));
    }

    @PostMapping("/my/{quoteItemId:\\d+}/after-sales")
    public Result<AfterSaleVO> createAfterSale(@PathVariable Long quoteItemId,
                                               @RequestBody @Valid AfterSaleCreateDTO dto) {
        return Result.ok(procurementOrderService.createAfterSale(quoteItemId, dto));
    }

    @GetMapping("/after-sales/page")
    public Result<PageResult<AfterSaleVO>> pageAfterSales(@Valid ProcurementOrderPageQueryDTO queryDTO) {
        return Result.ok(procurementOrderService.pageAfterSales(queryDTO));
    }

    @PostMapping("/after-sales/{id:\\d+}/approval")
    public Result<AfterSaleVO> approveAfterSale(@PathVariable Long id,
                                                @RequestBody @Valid AfterSaleApprovalDTO dto) {
        return Result.ok(procurementOrderService.approveAfterSale(id, dto));
    }
}
