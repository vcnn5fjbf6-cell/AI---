import http from "./http";
import type { PageResult } from "../types/common";
import type {
  AfterSaleApprovalForm,
  AfterSaleCreateForm,
  AfterSaleVO,
  OrderInvoiceReviewForm,
  OrderInvoiceSubmissionVO,
  OrderDocumentType,
  OrderInvoiceUploadForm,
  OrderReceiptForm,
  ProcurementOrderPageQuery,
  ProcurementOrderVO
} from "../types/order";

export function getMyOrdersPageApi(params: ProcurementOrderPageQuery): Promise<PageResult<ProcurementOrderVO>> {
  return http.get("/procurement/orders/my/page", { params });
}

export function getMyOrderDetailApi(quoteItemId: number): Promise<ProcurementOrderVO> {
  return http.get(`/procurement/orders/my/${quoteItemId}`);
}

export function exportMyOrderApi(quoteItemId: number): Promise<Blob> {
  return http.get(`/procurement/orders/my/${quoteItemId}/export`, { responseType: "blob" });
}

export function getMyOrderGroupApi(requestNo: string): Promise<ProcurementOrderVO[]> {
  return http.get(`/procurement/orders/my/groups/${encodeURIComponent(requestNo)}`);
}

export function exportMyOrderGroupApi(
  requestNo: string,
  params?: { deptName?: string; supplierId?: number }
): Promise<Blob> {
  return http.get(`/procurement/orders/my/groups/${encodeURIComponent(requestNo)}/export`, {
    params,
    responseType: "blob"
  });
}

export function confirmReceiptApi(quoteItemId: number, data?: OrderReceiptForm): Promise<ProcurementOrderVO> {
  return http.post(`/procurement/orders/my/${quoteItemId}/receipt`, data || {});
}

export function getOrderInvoicesApi(
  quoteItemId: number,
  documentType: OrderDocumentType = "INVOICE"
): Promise<OrderInvoiceSubmissionVO[]> {
  return http.get(`/procurement/orders/my/${quoteItemId}/invoices`, { params: { documentType } });
}

export function uploadOrderInvoiceApi(
  quoteItemId: number,
  data: OrderInvoiceUploadForm
): Promise<OrderInvoiceSubmissionVO> {
  const formData = new FormData();
  formData.append("documentType", data.documentType);
  for (const file of data.files) {
    formData.append("files", file);
  }
  if (data.invoiceNo) {
    formData.append("invoiceNo", data.invoiceNo);
  }
  if (data.invoiceDate) {
    formData.append("invoiceDate", data.invoiceDate);
  }
  if (data.totalAmount != null) {
    formData.append("totalAmount", String(data.totalAmount));
  }
  if (data.invoiceType) {
    formData.append("invoiceType", data.invoiceType);
  }
  if (data.remark) {
    formData.append("remark", data.remark);
  }
  return http.post(`/procurement/orders/my/${quoteItemId}/invoices`, formData, {
    headers: { "Content-Type": "multipart/form-data" },
    timeout: 120000
  });
}

export function acceptOrderInvoiceApi(
  quoteItemId: number,
  submissionId: number,
  data: OrderInvoiceReviewForm
): Promise<OrderInvoiceSubmissionVO> {
  return http.post(`/procurement/orders/my/${quoteItemId}/invoices/${submissionId}/accept`, data);
}

export function returnOrderInvoiceApi(
  quoteItemId: number,
  submissionId: number,
  data: Required<OrderInvoiceReviewForm>
): Promise<OrderInvoiceSubmissionVO> {
  return http.post(`/procurement/orders/my/${quoteItemId}/invoices/${submissionId}/return`, data);
}

export function downloadOrderInvoiceAttachmentApi(quoteItemId: number, attachmentId: number): Promise<Blob> {
  return http.get(`/procurement/orders/my/${quoteItemId}/invoices/attachments/${attachmentId}/download`, {
    responseType: "blob"
  });
}

export function createAfterSaleApi(quoteItemId: number, data: AfterSaleCreateForm): Promise<AfterSaleVO> {
  return http.post(`/procurement/orders/my/${quoteItemId}/after-sales`, data);
}

export function getAfterSalesPageApi(params: ProcurementOrderPageQuery): Promise<PageResult<AfterSaleVO>> {
  return http.get("/procurement/orders/after-sales/page", { params });
}

export function approveAfterSaleApi(id: number, data: AfterSaleApprovalForm): Promise<AfterSaleVO> {
  return http.post(`/procurement/orders/after-sales/${id}/approval`, data);
}
