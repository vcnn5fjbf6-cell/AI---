import type { PageQuery } from "./common";

export interface ProcurementOrderPageQuery extends PageQuery {
  keyword?: string;
  status?: string;
}

export interface ProcurementOrderVO {
  quoteItemId: number;
  quoteId: number;
  purchaseRequestId: number;
  purchaseRequestItemId?: number;
  sourceRequestId?: number;
  sourceReqNo?: string;
  sourceReqTitle?: string;
  sourceReqType?: string;
  aggregateReqNo?: string;
  supplierId: number;
  supplierName?: string;
  applicantUserId?: number;
  applicantName?: string;
  purchaserUserId?: number;
  purchaserName?: string;
  orderNo: string;
  reqNo: string;
  reqTitle: string;
  reqType?: string;
  deptName?: string;
  lineNo?: number;
  itemCode?: string;
  itemName?: string;
  specModel?: string;
  unit?: string;
  qty?: number;
  unitPrice?: number;
  lineAmount?: number;
  leadTimeDays?: number;
  quoteRemark?: string;
  receivingCity?: string;
  deliveryAddress?: string;
  quoteReplacementFlag?: number;
  needDate?: string;
  orderTime?: string;
  supplierConfirmStatus?: string;
  supplierConfirmedAt?: string;
  orderStatus?: string;
  receiptStatus?: string;
  receiptDueAt?: string;
  receivedBy?: number;
  receivedAt?: string;
  receiptRemark?: string;
  autoReceived?: number;
  noReasonReturnDeadline?: string;
  returnExchangeDeadline?: string;
  afterSaleAllowed?: boolean;
  noReasonReturnAvailable?: boolean;
  returnExchangeAvailable?: boolean;
  latestAfterSaleId?: number;
  latestAfterSaleNo?: string;
  latestAfterSaleType?: string;
  latestAfterSaleStatus?: string;
  latestAfterSaleReason?: string;
  replacementItemCode?: string;
  replacementItemName?: string;
  replacementSpecModel?: string;
  replacementUnit?: string;
  latestApprovalRemark?: string;
  latestInvoice?: OrderInvoiceSummaryVO;
  latestDeliveryNote?: OrderInvoiceSummaryVO;
  canExportOrder?: boolean;
  canConfirmReceipt?: boolean;
  canApplyAfterSale?: boolean;
  canConfirmAfterSaleProposal?: boolean;
  canViewInvoice?: boolean;
  canUploadInvoice?: boolean;
  canReviewInvoice?: boolean;
  canViewOrderDocuments?: boolean;
  canUploadDeliveryNote?: boolean;
  canReviewOrderDocuments?: boolean;
}

export type OrderInvoiceStatus = "PENDING" | "ACCEPTED" | "RETURNED" | "SUPERSEDED";
export type OrderDocumentType = "INVOICE" | "DELIVERY_NOTE";

export interface OrderInvoiceSummaryVO {
  submissionId: number;
  documentType: OrderDocumentType;
  status: OrderInvoiceStatus;
  versionNo: number;
  invoiceNo?: string;
  invoiceDate?: string;
  totalAmount?: number;
  invoiceType?: string;
  attachmentCount?: number;
  submittedAt?: string;
  submittedByName?: string;
  submittedByRole?: "SUPPLIER" | "ASSISTANT" | "ADMIN";
  reviewedAt?: string;
  reviewedByName?: string;
  reviewRemark?: string;
}

export interface OrderInvoiceAttachmentVO {
  id: number;
  fileName: string;
  contentType?: string;
  fileSize?: number;
  sha256?: string;
  downloadUrl?: string;
}

export interface OrderInvoiceSubmissionVO extends OrderInvoiceSummaryVO {
  quoteItemId: number;
  supplierId: number;
  remark?: string;
  attachments: OrderInvoiceAttachmentVO[];
}

export interface OrderInvoiceUploadForm {
  documentType: OrderDocumentType;
  files: File[];
  invoiceNo?: string;
  invoiceDate?: string;
  totalAmount?: number;
  invoiceType?: string;
  remark?: string;
}

export interface OrderInvoiceReviewForm {
  remark?: string;
}

export interface AfterSaleVO {
  id: number;
  afterSaleNo: string;
  purchaseRequestId: number;
  purchaseRequestItemId?: number;
  quoteId: number;
  quoteItemId: number;
  supplierId: number;
  supplierName?: string;
  requesterUserId: number;
  requesterName?: string;
  afterSaleType: string;
  reason: string;
  requestedQty: number;
  replacementItemCode?: string;
  replacementItemName?: string;
  replacementSpecModel?: string;
  replacementUnit?: string;
  replacementRemark?: string;
  supplierProposalType?: string;
  supplierProposalRemark?: string;
  supplierReplacementItemCode?: string;
  supplierReplacementItemName?: string;
  supplierReplacementSpecModel?: string;
  supplierReplacementUnit?: string;
  supplierReplacementUnitPrice?: number;
  supplierReplacementLineAmount?: number;
  supplierProposedAt?: string;
  requesterConfirmUserId?: number;
  requesterConfirmName?: string;
  requesterConfirmedAt?: string;
  requesterConfirmRemark?: string;
  status: string;
  approverUserId?: number;
  approverName?: string;
  approvedAt?: string;
  approvalRemark?: string;
  orderNo?: string;
  reqNo?: string;
  reqTitle?: string;
  itemCode?: string;
  itemName?: string;
  specModel?: string;
  unit?: string;
  orderQty?: number;
  orderAmount?: number;
  receivingCity?: string;
  deliveryAddress?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface AfterSaleCreateForm {
  afterSaleType: string;
  reason: string;
  requestedQty: number;
  replacementItemCode?: string;
  replacementItemName?: string;
  replacementSpecModel?: string;
  replacementUnit?: string;
  replacementRemark?: string;
}

export interface AfterSaleApprovalForm {
  action: string;
  approvalRemark?: string;
  proposalType?: string;
  proposalRemark?: string;
  replacementItemCode?: string;
  replacementItemName?: string;
  replacementSpecModel?: string;
  replacementUnit?: string;
  replacementUnitPrice?: number;
  replacementLineAmount?: number;
}

export interface OrderReceiptForm {
  receiptRemark?: string;
}
