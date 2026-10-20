import type { PageResult } from "./common";
import type { ApprovalRecordVO } from "./approval";

export type WorkbenchSourceType = "PURCHASE" | "PLATFORM" | "MONTHLY" | "SUPPLIER" | "AFTER_SALE";
export type WorkbenchRelation = "todo" | "mine" | "related" | "department" | "done";
export type WorkbenchModule = "PROCUREMENT" | "MAINTENANCE" | "PLATFORM" | "SUPPLIER";
export type WorkbenchAction = "APPROVE" | "REJECT" | "RETURN" | "ADD_SIGN" | "SAVE" | "TERMINATE";

export interface WorkbenchRequestVO {
  id: string;
  sourceId: number;
  sourceType: WorkbenchSourceType;
  module: WorkbenchModule;
  moduleName: string;
  type: string;
  relation: WorkbenchRelation;
  detailKind: string;
  reqNo: string;
  title: string;
  applicant: string;
  currentNode: string;
  currentApproverNames?: string;
  currentNodeNo: number;
  status: string;
  statusText: string;
  arrivedAt: string;
  amount?: number | null;
  amountText?: string;
  referenceAmount?: number | null;
  referenceAmountText?: string;
  priceSource?: "REFERENCE" | "SUPPLIER_QUOTE" | string;
  deptName?: string;
  viewerRole?: string;
  actionable: boolean;
  terminable?: boolean;
  returnable?: boolean;
  followed: boolean;
}

export interface WorkbenchMetricVO {
  key: string;
  label: string;
  value: number;
}

export interface WorkbenchPageVO {
  page: PageResult<WorkbenchRequestVO>;
  metrics: WorkbenchMetricVO[];
}

export interface WorkbenchFlowNodeVO {
  nodeNo: number;
  name: string;
  state: "done" | "current" | "pending" | "rejected" | "returned" | string;
  stateText: string;
  meta: string;
}

export interface WorkbenchDetailRowVO {
  itemId?: number | null;
  quoteItemId?: number | null;
  lineNo: number;
  name: string;
  roomName?: string;
  deptName?: string;
  spec: string;
  qty: number | string;
  unit: string;
  amount?: number | null;
  amountText: string;
  referenceAmount?: number | null;
  referenceAmountText?: string;
  awardedUnitPrice?: number | null;
  overPriceLineDifference?: number | null;
  overPriceRatePercent?: number | null;
  overPriceReviewRequired?: boolean;
  sourceRequestNo?: string;
  sourceApplicantName?: string;
  sourceApplicantDeptName?: string;
  status: string;
  statusText: string;
  remark: string;
  purchaseReason?: string;
  rejectReason?: string | null;
  replacementQuote?: boolean;
  originalItemName?: string;
  originalSpec?: string;
  replacementItemName?: string;
  replacementSpec?: string;
  replacementEcommerceUrl?: string;
  replacementUnitPrice?: number;
  replacementAmount?: number;
  replacementReason?: string;
  replacementReviewStatus?: string;
  replacementReviewRemark?: string;
  supplierName?: string;
  supplierContactName?: string;
  supplierContactPhone?: string;
  quoteUnitPrice?: number | null;
  quoteAmount?: number | null;
  quoteAmountText?: string;
  leadTimeDays?: number | null;
  awarded?: boolean;
  originalUnitPrice?: number | null;
  priceIncreaseLimit?: number | null;
  supplierProposalType?: string | null;
  supplierProposalRemark?: string | null;
  supplierReplacementItemCode?: string | null;
  supplierReplacementItemName?: string | null;
  supplierReplacementSpecModel?: string | null;
  supplierReplacementUnit?: string | null;
  supplierReplacementUnitPrice?: number | null;
  supplierReplacementLineAmount?: number | null;
  supplierProposedAt?: string | null;
  requesterConfirmRemark?: string | null;
  requesterConfirmedAt?: string | null;
}

export interface WorkbenchRequestDetailVO {
  summary: WorkbenchRequestVO;
  flowNodes: WorkbenchFlowNodeVO[];
  detailRows: WorkbenchDetailRowVO[];
  approvalRecords: ApprovalRecordVO[];
  currentApprovalDueAt?: string | null;
}

export interface WorkbenchApprovalForm {
  action: WorkbenchAction;
  comment?: string;
  targetNodeNo?: number | null;
  itemDecisions?: Array<{
    itemId?: number | null;
    rejected: boolean;
    rejectReason?: string;
  }>;
  replacementDecisions?: Array<{
    quoteItemId?: number | null;
    replacementApproved: boolean;
  }>;
  addSignUser?: string;
  afterSaleProposalType?: string;
  afterSaleReplacementItemCode?: string;
  afterSaleReplacementItemName?: string;
  afterSaleReplacementSpecModel?: string;
  afterSaleReplacementUnit?: string;
  afterSaleReplacementUnitPrice?: number | null;
  afterSaleReplacementLineAmount?: number | null;
}

export interface WorkbenchUserOptionVO {
  id: number;
  realName?: string;
  employeeNo?: string;
  username?: string;
  displayName: string;
}

export interface WorkbenchNoticeVO {
  id: number;
  title: string;
  content: string;
  pinned: boolean;
  status: number;
  sortNo: number;
  updatedAt?: string;
}

export interface WorkbenchNoticeForm {
  title: string;
  content: string;
  pinned: boolean;
  status: number;
  sortNo: number;
}
