import http from "./http";
import type { PageResult } from "../types/common";
import type { PurchaseRequestForm, PurchaseRequestPageQuery, PurchaseRequestVO } from "../types/purchase";
import type { WorkflowSubmitResultVO } from "./workflow";

export function getPurchaseRequestPageApi(params: PurchaseRequestPageQuery): Promise<PageResult<PurchaseRequestVO>> {
  return http.get("/admin/purchase-requests/page", { params });
}

export function getPurchaseRequestDetailApi(id: number): Promise<PurchaseRequestVO> {
  return http.get(`/admin/purchase-requests/${id}`);
}

export function createPurchaseRequestApi(data: PurchaseRequestForm): Promise<number> {
  return http.post("/admin/purchase-requests", data);
}

export function updatePurchaseRequestApi(id: number, data: PurchaseRequestForm): Promise<void> {
  return http.put(`/admin/purchase-requests/${id}`, data);
}

export function submitPurchaseRequestApi(id: number): Promise<WorkflowSubmitResultVO> {
  return http.post(`/admin/purchase-requests/${id}/submit`);
}

export function deletePurchaseRequestApi(id: number): Promise<void> {
  return http.delete(`/admin/purchase-requests/${id}`);
}
