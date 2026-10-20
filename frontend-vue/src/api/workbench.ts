import http from "./http";
import type {
  WorkbenchApprovalForm,
  WorkbenchNoticeForm,
  WorkbenchNoticeVO,
  WorkbenchPageVO,
  WorkbenchRequestDetailVO,
  WorkbenchSourceType,
  WorkbenchUserOptionVO
} from "../types/workbench";
import type { WorkflowSubmitResultVO } from "./workflow";

export function getWorkbenchRequestsApi(params: Record<string, unknown>): Promise<WorkbenchPageVO> {
  return http.get("/workbench/requests/page", { params });
}

export function getWorkbenchRequestDetailApi(
  sourceType: WorkbenchSourceType,
  sourceId: number
): Promise<WorkbenchRequestDetailVO> {
  return http.get(`/workbench/requests/${sourceType}/${sourceId}`);
}

export function approveWorkbenchRequestApi(
  sourceType: WorkbenchSourceType,
  sourceId: number,
  data: WorkbenchApprovalForm
): Promise<WorkflowSubmitResultVO> {
  return http.post(`/workbench/requests/${sourceType}/${sourceId}/approval`, data);
}

export function setWorkbenchFollowApi(data: {
  sourceType: WorkbenchSourceType;
  sourceId: number;
  followed: boolean;
}): Promise<void> {
  return http.post("/workbench/follows", data);
}

export function getWorkbenchUserOptionsApi(keyword = ""): Promise<WorkbenchUserOptionVO[]> {
  return http.get("/workbench/users/options", { params: { keyword } });
}

export function getWorkbenchNoticesApi(): Promise<WorkbenchNoticeVO[]> {
  return http.get("/workbench/notices");
}

export function getWorkbenchManageNoticesApi(): Promise<WorkbenchNoticeVO[]> {
  return http.get("/workbench/notices/manage");
}

export function createWorkbenchNoticeApi(data: WorkbenchNoticeForm): Promise<WorkbenchNoticeVO> {
  return http.post("/workbench/notices/manage", data);
}

export function updateWorkbenchNoticeApi(id: number, data: WorkbenchNoticeForm): Promise<WorkbenchNoticeVO> {
  return http.put(`/workbench/notices/manage/${id}`, data);
}

export function deleteWorkbenchNoticeApi(id: number): Promise<void> {
  return http.delete(`/workbench/notices/manage/${id}`);
}
