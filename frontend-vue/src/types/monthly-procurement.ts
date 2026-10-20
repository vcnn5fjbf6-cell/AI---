import type { PageQuery } from "./common";

export interface MonthlyDemandCreateForm {
  planMonth?: string;
  reqTitle: string;
  reqType: string;
  deptCode?: string;
  deptName?: string;
  budgetCode?: string;
  applicantUserId?: number | null;
  actingReason?: string;
  urgencyLevel: number;
  emergency: boolean;
  itemName: string;
  specModel?: string;
  unit: string;
  qty: number;
  unitPrice: number;
  taxRate: number;
  needDate?: string;
  purchaseReason: string;
  ecommerceUrl?: string;
  remark?: string;
}

export interface MonthlyDemandSupplementForm {
  qty: number;
  purchaseReason: string;
}

export interface MonthlyDemandQuantityUpdateForm {
  qty: number;
}

export interface MonthlyDemandQuery extends PageQuery {
  planMonth?: string;
  status?: string;
  reqType?: string;
}

export interface MonthlyDemandVO {
  id: number;
  demandNo: string;
  planMonth: string;
  applicantUserId: number;
  regionId?: number;
  deptName?: string;
  budgetCode?: string;
  reqType: string;
  urgencyLevel: number;
  emergency: boolean;
  reqTitle: string;
  itemCode: string;
  itemName: string;
  specModel?: string;
  unit: string;
  qty: number;
  unitPrice: number;
  lineAmount: number;
  needDate?: string;
  purchaseReason: string;
  ecommerceUrl?: string;
  sourceChannel: string;
  status: string;
  purchaseRequestId?: number;
  createdAt: string;
}

export interface MonthlyAggregateResult {
  planMonth: string;
  demandCount: number;
  requestCount: number;
  purchaseRequestIds: number[];
}
