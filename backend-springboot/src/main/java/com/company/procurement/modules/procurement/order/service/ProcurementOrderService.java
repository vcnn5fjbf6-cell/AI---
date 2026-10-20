package com.company.procurement.modules.procurement.order.service;

import com.company.procurement.common.result.PageResult;
import com.company.procurement.common.export.ExportFile;
import com.company.procurement.modules.procurement.order.dto.AfterSaleApprovalDTO;
import com.company.procurement.modules.procurement.order.dto.AfterSaleCreateDTO;
import com.company.procurement.modules.procurement.order.dto.OrderReceiptDTO;
import com.company.procurement.modules.procurement.order.dto.OrderInvoiceReviewDTO;
import com.company.procurement.modules.procurement.order.dto.ProcurementOrderPageQueryDTO;
import com.company.procurement.modules.procurement.order.vo.AfterSaleVO;
import com.company.procurement.modules.procurement.order.vo.OrderInvoiceSubmissionVO;
import com.company.procurement.modules.procurement.order.vo.ProcurementOrderVO;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface ProcurementOrderService {

    PageResult<ProcurementOrderVO> pageMyOrders(ProcurementOrderPageQueryDTO queryDTO);

    ProcurementOrderVO orderDetail(Long quoteItemId);

    ExportFile exportOrder(Long quoteItemId);

    List<ProcurementOrderVO> listMyOrdersByRequestNo(String requestNo);

    ExportFile exportOrdersByRequestNo(String requestNo, String deptName, Long supplierId);

    List<OrderInvoiceSubmissionVO> listInvoices(Long quoteItemId, String documentType);

    OrderInvoiceSubmissionVO uploadInvoice(Long quoteItemId,
                                           String documentType,
                                           List<MultipartFile> files,
                                           String invoiceNo,
                                           LocalDate invoiceDate,
                                           BigDecimal totalAmount,
                                           String invoiceType,
                                           String remark);

    OrderInvoiceSubmissionVO acceptInvoice(Long quoteItemId, Long submissionId, OrderInvoiceReviewDTO dto);

    OrderInvoiceSubmissionVO returnInvoice(Long quoteItemId, Long submissionId, OrderInvoiceReviewDTO dto);

    OrderInvoiceService.AttachmentResource loadInvoiceAttachment(Long quoteItemId, Long attachmentId);

    ProcurementOrderVO confirmReceipt(Long quoteItemId, OrderReceiptDTO dto);

    AfterSaleVO createAfterSale(Long quoteItemId, AfterSaleCreateDTO dto);

    PageResult<AfterSaleVO> pageAfterSales(ProcurementOrderPageQueryDTO queryDTO);

    AfterSaleVO approveAfterSale(Long id, AfterSaleApprovalDTO dto);
}
