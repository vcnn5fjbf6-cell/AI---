import type { PageQuery } from "./common";

export interface PurchaseRequestPageQuery extends PageQuery {
  reqNo?: string;
  reqTitle?: string;
  status?: string;
  approvalStatus?: string;
  applicantUserId?: number | null;
  purchaserUserId?: number | null;
  regionId?: number | null;
  rejectedItemsOnly?: boolean;
}

export interface PurchaseRequestItem {
  id?: number;
  lineNo?: number;
  itemCode: string;
  reqType: string;
  itemName: string;
  specModel: string;
  itemBrand: string;
  unit: string;
  qty: number;
  unitPrice: number;
  referenceUnitPrice?: number;
  taxRate: number;
  lineAmount?: number;
  referenceLineAmount?: number;
  awardedSupplierName?: string | null;
  awardedUnitPrice?: number | null;
  overPriceUnitDifference?: number | null;
  overPriceLineDifference?: number | null;
  overPriceRatePercent?: number | null;
  overPriceReviewRequired?: boolean;
  sourceRequestNo?: string | null;
  sourceApplicantName?: string | null;
  sourceApplicantDeptName?: string | null;
  needDate: string;
  purpose: string;
  ecommerceUrl: string;
  receivingCity?: string | null;
  deliveryAddress?: string | null;
  status: string;
  remark: string;
}

export interface PurchaseRequestVO {
  id: number;
  reqNo: string;
  reqTitle: string;
  reqType: string;
  applicantUserId: number;
  applicantName?: string;
  applicantEmployeeNo?: string;
  purchaserUserId: number;
  regionId: number;
  deptCode: string;
  deptName: string;
  receivingCity?: string | null;
  budgetCode: string;
  actingUserId?: number | null;
  actingUserName?: string | null;
  actingReason?: string | null;
  actingDelegated?: boolean;
  currency: string;
  totalAmount: number;
  referenceTotalAmount?: number;
  priceSource?: "REFERENCE" | "SUPPLIER_QUOTE" | string;
  urgencyLevel: number;
  needDate: string;
  reqReason: string;
  awardStrategy: "TOTAL_LOWEST" | "ITEM_LOWEST" | string;
  submitCount: number;
  currentNodeNo: number;
  currentNodeName?: string;
  approvalStatus: string;
  status: string;
  submittedAt: string;
  approvedAt: string;
  quoteCountdownStartedAt?: string | null;
  quoteCountdownDueAt?: string | null;
  currentApprovalDueAt?: string | null;
  overPriceReviewRequired?: boolean;
  directAwardApplied?: boolean;
  directAwardSupplierId?: number | null;
  directAwardSupplierName?: string | null;
  directAwardThresholdAmount?: number | null;
  directAwardReason?: string | null;
  selectedQuoteId?: number | null;
  leaderConfirmStatus?: string | null;
  leaderConfirmRemark?: string | null;
  leaderConfirmedBy?: number | null;
  leaderConfirmedAt?: string | null;
  remark: string;
  createdAt: string;
  updatedAt: string;
  copyUserIds?: number[];
  items: PurchaseRequestItem[];
}

export interface PurchaseRequestForm {
  reqNo?: string;
  reqTitle: string;
  reqType: string;
  applicantUserId: number | null;
  purchaserUserId: number | null;
  regionId: number | null;
  deptCode: string;
  deptName: string;
  budgetCode: string;
  actingReason?: string;
  currency: string;
  totalAmount: number;
  urgencyLevel: number;
  needDate: string;
  reqReason: string;
  awardStrategy: "TOTAL_LOWEST" | "ITEM_LOWEST" | string;
  remark: string;
  copyUserIds?: number[];
  items: PurchaseRequestItem[];
}
