import http from "./http";
import type { PageResult } from "../types/common";
import type { ApprovalActionForm, ApprovalRecordPageQuery, ApprovalRecordVO } from "../types/approval";

export function getApprovalRecordPageApi(params: ApprovalRecordPageQuery): Promise<PageResult<ApprovalRecordVO>> {
  return http.get("/admin/approval-records/page", { params });
}

export function createApprovalRecordApi(data: ApprovalActionForm): Promise<number> {
  return http.post("/admin/approval-records", data);
}
