import http from "./http";

export interface PersonalLaborItemStatusVO {
  recordId?: number | null;
  itemCode: string;
  itemName: string;
  unit: string;
  enabled: boolean;
  forced: boolean;
  cycleDays: number;
  quantityLimit?: number | null;
  advanceDays: number;
  reminderDays: number;
  emergencyEnabled: boolean;
  managerApprovalRequired: boolean;
  dailyUnclaimedReminderEnabled: boolean;
  specModel?: string | null;
  quantity?: number | null;
  lastReceivedDate?: string | null;
  nextDueDate?: string | null;
  earliestApplyDate?: string | null;
  daysUntilDue?: number | null;
  status: string;
  statusName: string;
  canApply: boolean;
  canEmergencyPurchase: boolean;
  canConfirmReceived: boolean;
  purchaseRequestId?: number | null;
  purchaseRequestNo?: string | null;
}

export interface PersonalLaborRequestItemForm {
  itemCode: string;
  specModel?: string;
  qty: number;
  unitPrice: number;
  purpose?: string;
}

export interface PersonalLaborRequestForm {
  requestTitle?: string;
  deptCode?: string;
  deptName?: string;
  budgetCode?: string;
  receivingCity?: string;
  reason?: string;
  needDate?: string;
  emergency?: boolean;
  items: PersonalLaborRequestItemForm[];
}

export interface PersonalLaborSubmitResultVO {
  purchaseRequestId: number;
  purchaseRequestNo?: string;
  nextNodeName?: string;
  message?: string;
}

export function getPersonalLaborItemsApi(): Promise<PersonalLaborItemStatusVO[]> {
  return http.get("/procurement/labor/items");
}

export function submitPersonalLaborRequestApi(data: PersonalLaborRequestForm): Promise<PersonalLaborSubmitResultVO> {
  return http.post("/procurement/labor/requests", data);
}

export function confirmPersonalLaborReceivedApi(recordId: number): Promise<PersonalLaborItemStatusVO> {
  return http.post(`/procurement/labor/records/${recordId}/confirm-received`);
}
