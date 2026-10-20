import type { PageQuery } from "./common";

export interface SupplierPageQuery extends PageQuery {
  supplierCode?: string;
  supplierName?: string;
  regionId?: number | null;
  status?: string;
  onboardingStatus?: string;
  blacklistFlag?: number | null;
}

export interface SupplierVO {
  id: number;
  supplierCode: string;
  supplierName: string;
  supplierShortName: string;
  taxNo: string;
  supplierType: string;
  regionId: number | null;
  contactName: string;
  contactPhone: string;
  contactEmail: string;
  address: string;
  bankName: string;
  bankAccount: string;
  invoiceTitle: string;
  defaultPaymentTerm: string;
  ratingLevel: string;
  onboardingStatus: string;
  status: string;
  blacklistFlag: number;
  dataIsolationFlag: number;
  blacklistReason: string;
  bidSuspendedUntil?: string | null;
  bidSuspendedReason?: string | null;
  entryDate: string;
  remark: string;
  qualificationCount: number;
  linkedUserCount?: number;
  linkedUsernames?: string[];
  createdAt: string;
  updatedAt: string;
}

export interface SupplierForm {
  supplierCode?: string;
  supplierName: string;
  supplierShortName: string;
  taxNo: string;
  supplierType: string;
  regionId: number | null;
  contactName: string;
  contactPhone: string;
  contactEmail: string;
  address: string;
  bankName: string;
  bankAccount: string;
  invoiceTitle: string;
  defaultPaymentTerm: string;
  ratingLevel: string;
  onboardingStatus: string;
  status: string;
  blacklistFlag: number;
  dataIsolationFlag: number;
  blacklistReason: string;
  entryDate: string;
  remark: string;
}

export interface SupplierQuoteRequestVO {
  id: number;
  reqNo: string;
  reqTitle: string;
  reqType: string;
  regionId: number | null;
  deptName: string;
  receivingCity?: string | null;
  requestAmount: number | null;
  needDate: string;
  awardStrategy?: "TOTAL_LOWEST" | "ITEM_LOWEST" | string;
  status: string;
  approvalStatus: string;
  myQuoteAmount?: number | null;
  myLeadTimeDays?: number | null;
  myQuoteStatus?: string | null;
  mySubmittedAt?: string | null;
  myAwarded?: boolean;
  mySupplierConfirmStatus?: string | null;
  mySupplierConfirmDueAt?: string | null;
  supplierBiddingSuspended?: boolean;
  supplierBidSuspendedUntil?: string | null;
  supplierBanned?: boolean;
  supplierBanReason?: string | null;
  internalProductCodeRule?: string | null;
  leaderConfirmStatus?: string | null;
  quoteCountdownStartedAt?: string | null;
  quoteCountdownDueAt?: string | null;
  directAwardApplied?: boolean;
  directAwardSupplierId?: number | null;
  directAwardSupplierName?: string | null;
  directAwardThresholdAmount?: number | null;
  directAwardReason?: string | null;
}

export interface SupplierQuoteVO {
  id: number;
  quoteNo: string;
  purchaseRequestId: number;
  supplierId: number;
  supplierName: string;
  supplierUserId: number;
  quoteSource?: "NORMAL" | "TEMP_RECOMMENDED" | string;
  recommendationId?: number | null;
  awardEligible?: boolean;
  awardEligibilityText?: string | null;
  receivingCity?: string | null;
  totalAmount: number;
  leadTimeDays: number;
  quoteRemark: string;
  awarded?: boolean;
  status: string;
  selected?: boolean;
  supplierConfirmStatus?: string | null;
  supplierConfirmRemark?: string | null;
  supplierConfirmedBy?: number | null;
  supplierConfirmedAt?: string | null;
  supplierConfirmDueAt?: string | null;
  supplierBiddingSuspended?: boolean;
  supplierBidSuspendedUntil?: string | null;
  supplierBanned?: boolean;
  supplierBanReason?: string | null;
  submittedAt: string;
  createdAt: string;
  updatedAt: string;
  items?: SupplierQuoteItemVO[];
}

export interface SupplierQuoteItemVO {
  id: number;
  quoteId: number;
  purchaseRequestId: number;
  purchaseRequestItemId: number;
  lineNo: number;
  itemCode: string;
  itemName: string;
  specModel: string;
  unit: string;
  qty: number;
  unitPrice: number;
  lineAmount: number;
  leadTimeDays: number;
  originalCondition?: "NEW" | "USED" | string | null;
  originalSecondHandRemark?: string | null;
  quoteRemark: string;
  replacementFlag?: boolean;
  replacementItemName?: string | null;
  replacementSpecModel?: string | null;
  replacementEcommerceUrl?: string | null;
  replacementUnitPrice?: number | null;
  replacementLineAmount?: number | null;
  replacementReason?: string | null;
  replacementCondition?: "NEW" | "USED" | string | null;
  replacementSecondHandRemark?: string | null;
  replacementReviewStatus?: string | null;
  replacementReviewRemark?: string | null;
  assetReviewStatus?: "PENDING" | "COMPLETED" | "PARTIAL" | "FAILED" | string | null;
  assetReviewMessage?: string | null;
  originalAssetCategoryCode?: string | null;
  originalAssetNameCode?: string | null;
  originalAssetCatalogName?: string | null;
  originalAssetClassification?: string | null;
  originalSpecialApprovalRequired?: boolean;
  replacementAssetCategoryCode?: string | null;
  replacementAssetNameCode?: string | null;
  replacementAssetCatalogName?: string | null;
  replacementAssetClassification?: string | null;
  replacementSpecialApprovalRequired?: boolean;
  conditionMismatch?: boolean;
  assetReviewedAt?: string | null;
  waived?: boolean;
  awarded?: boolean;
  receivingCity?: string | null;
  deliveryAddress?: string | null;
  status: string;
}

export interface SupplierQuoteSubmitForm {
  purchaseRequestId: number;
  totalAmount?: number;
  leadTimeDays?: number;
  quoteRemark?: string | null;
  items: SupplierQuoteSubmitItemForm[];
}

export interface SupplierQuoteSubmitItemForm {
  purchaseRequestItemId: number;
  unitPrice?: number | null;
  leadTimeDays?: number | null;
  originalCondition?: "NEW" | "USED" | string | null;
  originalSecondHandRemark?: string | null;
  replacementFlag?: boolean;
  replacementItemName?: string | null;
  replacementSpecModel?: string | null;
  replacementEcommerceUrl?: string | null;
  replacementUnitPrice?: number | null;
  replacementReason?: string | null;
  replacementCondition?: "NEW" | "USED" | string | null;
  replacementSecondHandRemark?: string | null;
  waived?: boolean;
  quoteRemark?: string | null;
}

export interface SupplierQuoteSummaryVO {
  purchaseRequestId: number;
  awardStrategy?: "TOTAL_LOWEST" | "ITEM_LOWEST" | string;
  supplierQuoteCount: number;
  lowestAmount?: number | null;
  lowestSupplierId?: number | null;
  lowestSupplierName?: string | null;
  selectedQuoteId?: number | null;
  selectedSupplierId?: number | null;
  selectedSupplierName?: string | null;
  quoteDetailsVisible?: boolean;
  leaderConfirmStatus?: string | null;
  leaderConfirmRemark?: string | null;
  leaderConfirmedBy?: number | null;
  leaderConfirmedAt?: string | null;
  quotes: SupplierQuoteVO[];
}

export interface TemporarySupplierRecommendationPageQuery extends PageQuery {
  keyword?: string;
  status?: string;
  purchaseRequestId?: number | null;
}

export interface TemporarySupplierRecommendationCreateForm {
  purchaseRequestId: number | null;
  supplierName: string;
  contactName: string;
  contactPhone: string;
  contactEmail?: string | null;
  recommendReason: string;
  remark?: string | null;
  itemIds: number[];
}

export interface TemporarySupplierRecommendationReviewForm {
  comment: string;
}

export interface TemporarySupplierRecommendationItemVO {
  id: number;
  purchaseRequestItemId: number;
  lineNo: number;
  itemCode?: string | null;
  reqType?: string | null;
  itemName: string;
  specModel?: string | null;
  unit?: string | null;
  qty?: number | null;
  purpose?: string | null;
  status?: string | null;
}

export interface TemporarySupplierRecommendationVO {
  id: number;
  recommendationNo: string;
  purchaseRequestId: number;
  requestNo?: string | null;
  requestTitle?: string | null;
  regionId?: number | null;
  deptCode?: string | null;
  deptName?: string | null;
  supplierId: number;
  supplierCode?: string | null;
  supplierName: string;
  contactName: string;
  contactPhone: string;
  contactEmail?: string | null;
  recommendedBy?: number | null;
  recommendedByName?: string | null;
  recommendReason?: string | null;
  status: string;
  statusText?: string | null;
  currentRoleCode?: string | null;
  currentRoleName?: string | null;
  currentUserCanReview?: boolean;
  roomManagerUserId?: number | null;
  roomManagerName?: string | null;
  roomManagerComment?: string | null;
  roomManagerReviewedAt?: string | null;
  regionManagerUserId?: number | null;
  regionManagerName?: string | null;
  regionManagerComment?: string | null;
  regionManagerReviewedAt?: string | null;
  rejectedBy?: number | null;
  rejectedByName?: string | null;
  rejectReason?: string | null;
  rejectedAt?: string | null;
  remark?: string | null;
  itemCount?: number | null;
  items?: TemporarySupplierRecommendationItemVO[];
  createdAt?: string | null;
  updatedAt?: string | null;
}
