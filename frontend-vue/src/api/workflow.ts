import http from "./http";
import type { PageResult } from "../types/common";
import type { PurchaseRequestVO } from "../types/purchase";
import type { ApprovalRecordVO } from "../types/approval";
import type { ProcurementApprovalRouteVO } from "../types/platform";
import type { SupplierQuoteSummaryVO } from "../types/supplier";

export interface WorkflowApprovalActionDTO {
  action: "APPROVE" | "RETURN" | "SAVE" | "TERMINATE";
  approvalComment?: string;
  targetNodeNo?: number | null;
  itemDecisions?: Array<{
    itemId?: number;
    rejected: boolean;
    rejectReason?: string;
  }>;
}

export interface WorkflowQuoteConfirmDTO {
  quoteId?: number | null;
  awardStrategy?: "TOTAL_LOWEST" | "ITEM_LOWEST" | string;
  leaderConfirmRemark?: string | null;
}

export interface WorkflowQuoteDeadlineExtendDTO {
  days: number;
}

export interface WorkflowPrecheckItemOpinion {
  lineNo: number;
  decisionSuggestion: "APPROVE" | "SUPPLEMENT" | "RETURN";
  opinion: string;
  missingFields?: Array<"USE_SCENE" | "CURRENT_IMPACT" | "NECESSITY" | "QUANTITY_BASIS" | "URGENCY_BASIS">;
}

export interface WorkflowPrecheckResult {
  overallOpinion: string;
  itemOpinions: WorkflowPrecheckItemOpinion[];
}

export interface WorkflowPurposeOptimizationResult {
  optimizedContent: string;
}

export interface WorkflowDashboardVO {
  myRequestTotal: number;
  pendingMyApprovalTotal: number;
  pendingApprovalTotal: number;
  quotingRequestTotal: number;
  comparedRequestTotal: number;
  mySubmittedQuoteTotal: number;
  totalRequestCount?: number;
  openRequestCount?: number;
  approvedRequestTotal?: number;
  returnedRequestTotal?: number;
  monthRequestTotal?: number;
  totalAmount?: number;
  monthAmount?: number;
  averageApprovalHours?: number;
  metrics?: WorkflowDashboardMetricVO[];
  statusItems?: WorkflowDashboardBreakdownVO[];
  departmentItems?: WorkflowDashboardBreakdownVO[];
  funnelItems?: WorkflowDashboardBreakdownVO[];
  recentRequests?: WorkflowDashboardRequestVO[];
  quoteRequests?: WorkflowDashboardRequestVO[];
  productItems?: WorkflowDashboardProductVO[];
  supplierItems?: WorkflowDashboardSupplierVO[];
  cityItems?: WorkflowDashboardBreakdownVO[];
  purchaseLines?: WorkflowDashboardPurchaseLineVO[];
}

export interface WorkflowDashboardMetricVO {
  key: string;
  label: string;
  value: string;
  note?: string;
  tone?: "primary" | "success" | "warning" | "danger" | string;
}

export interface WorkflowDashboardBreakdownVO {
  key: string;
  label: string;
  count: number;
  amount?: number;
  tone?: "primary" | "success" | "warning" | "danger" | "info" | string;
}

export interface WorkflowDashboardRequestVO {
  id: number;
  reqNo: string;
  reqTitle: string;
  deptName?: string;
  applicantName?: string;
  receivingCity?: string;
  totalAmount?: number;
  status: string;
  statusName?: string;
  currentNodeName?: string;
  currentNodeNo?: number;
  createdAt?: string;
  submittedAt?: string;
  dueAt?: string;
  quoteCount?: number;
}

export interface WorkflowDashboardProductVO {
  key: string;
  itemCode?: string;
  itemName: string;
  specModel?: string;
  unit?: string;
  requestedQty?: number;
  awardedQty?: number;
  requestAmount?: number;
  awardedAmount?: number;
  requestCount?: number;
  supplierCount?: number;
}

export interface WorkflowDashboardSupplierVO {
  supplierId?: number;
  supplierName: string;
  quoteRequestCount?: number;
  awardedRequestCount?: number;
  awardedItemCount?: number;
  awardedAmount?: number;
  winRate?: number;
  returnAfterSaleCount?: number;
  replacementAfterSaleCount?: number;
  returnRate?: number;
  replacementRate?: number;
  averageLeadTimeDays?: number;
  efficiencyName?: string;
  efficiencyScore?: number;
}

export interface WorkflowDashboardPurchaseLineVO {
  requestId?: number;
  reqNo?: string;
  reqTitle?: string;
  deptName?: string;
  receivingCity?: string;
  itemCode?: string;
  itemName?: string;
  specModel?: string;
  qty?: number;
  unit?: string;
  amount?: number;
  supplierName?: string;
  leadTimeDays?: number;
  statusName?: string;
  occurredAt?: string;
}

export interface WorkflowSubmitApproverVO {
  userId: number;
  username?: string;
  realName?: string;
  employeeNo?: string;
  displayName?: string;
}

export interface WorkflowSubmitResultVO {
  requestId: number;
  reqNo?: string;
  reqTitle?: string;
  nextNodeNo?: number;
  nextLevelCode?: string;
  nextNodeName?: string;
  passRule?: "ANY_ONE" | "ALL" | string;
  passRuleName?: string;
  currentApproverId?: number | null;
  approvers?: WorkflowSubmitApproverVO[];
  hasNextApprover?: boolean;
}

export interface PurchaseRequestNoVO {
  reqNo: string;
}

/** 工作流仪表盘数据 */
export function getWorkflowDashboardApi(): Promise<WorkflowDashboardVO> {
  return http.get("/workflow/dashboard");
}

/** 分配一个新采购申请单号，用于新建页提前展示并防重复提交 */
export function getNextWorkflowRequestNoApi(urgencyLevel: number): Promise<PurchaseRequestNoVO> {
  return http.get("/workflow/purchase-requests/next-no", { params: { urgencyLevel } });
}

/** 可见的采购申请分页列表 */
export function getWorkflowPageApi(params: Record<string, unknown>): Promise<PageResult<PurchaseRequestVO>> {
  return http.get("/workflow/purchase-requests/page", { params });
}

/** 当前账号收到的单项商品驳回提醒 */
export function getRejectedItemMessagesApi(): Promise<PurchaseRequestVO[]> {
  return http.get("/workflow/purchase-requests/rejected-item-messages");
}

/** 当前账号已读的采购驳回消息 key */
export function getRejectedMessageReadKeysApi(): Promise<string[]> {
  return http.get("/workflow/purchase-requests/rejected-messages/read-keys");
}

/** 批量标记采购驳回消息为已读 */
export function markRejectedMessagesReadApi(messageKeys: string[]): Promise<void> {
  return http.post("/workflow/purchase-requests/rejected-messages/read", { messageKeys });
}

/** 当前账号已清除的采购驳回消息 key */
export function getRejectedMessageClearKeysApi(): Promise<string[]> {
  return http.get("/workflow/purchase-requests/rejected-messages/clear-keys");
}

/** 批量清除采购驳回消息 */
export function clearRejectedMessagesApi(messageKeys: string[]): Promise<void> {
  return http.post("/workflow/purchase-requests/rejected-messages/clear", { messageKeys });
}

/** 查看可见的采购申请详情 */
export function getWorkflowDetailApi(id: number): Promise<PurchaseRequestVO> {
  return http.get(`/workflow/purchase-requests/${id}`);
}

/** 通过工作流创建采购申请 */
export function createWorkflowRequestApi(data: unknown): Promise<number> {
  return http.post("/workflow/purchase-requests", data);
}

/** 使用正式采购合规审核能力预审草稿，不保存或提交申请 */
export function precheckWorkflowRequestApi(data: unknown): Promise<WorkflowPrecheckResult> {
  return http.post("/workflow/purchase-requests/ai-precheck", data);
}

/** 使用受管Skill优化采购原因，只返回待追加到当前浏览器草稿的内容 */
export function optimizeWorkflowPurposeApi(purpose: string): Promise<WorkflowPurposeOptimizationResult> {
  return http.post("/workflow/purchase-requests/purpose-optimize", { purpose });
}

/** 通过工作流更新采购申请 */
export function updateWorkflowRequestApi(id: number, data: unknown): Promise<void> {
  return http.put(`/workflow/purchase-requests/${id}`, data);
}

/** 关闭已退回采购申请，关闭后不可再次编辑或提交 */
export function closeReturnedWorkflowRequestApi(id: number): Promise<void> {
  return http.post(`/workflow/purchase-requests/${id}/close`);
}

/** 下载采购明细批量导入模板 */
export function exportPurchaseItemTemplateApi(): Promise<Blob> {
  return http.get("/workflow/purchase-requests/items/template", { responseType: "blob" });
}

/** 预览采购明细批量导入文件 */
export function importPurchaseItemPreviewApi(file: File): Promise<string[][]> {
  const formData = new FormData();
  formData.append("file", file);
  return http.post("/workflow/purchase-requests/items/import-preview", formData, {
    headers: { "Content-Type": "multipart/form-data" },
    timeout: 60000
  });
}

/** 提交采购申请（驱动状态机） */
export function submitWorkflowRequestApi(id: number): Promise<WorkflowSubmitResultVO> {
  return http.post(`/workflow/purchase-requests/${id}/submit`);
}

/** 提交前预览下一步审核节点和审核人，不推进流程 */
export function previewSubmitWorkflowRequestApi(id: number): Promise<WorkflowSubmitResultVO> {
  return http.get(`/workflow/purchase-requests/${id}/submit-preview`);
}

/** 待审批任务分页列表 */
export function getApprovalTasksPageApi(params: Record<string, unknown>): Promise<PageResult<PurchaseRequestVO>> {
  return http.get("/workflow/approval-tasks/page", { params });
}

/** 可选采购部门/机房楼栋清单 */
export function getProcurementDepartmentOptionsApi(): Promise<ProcurementApprovalRouteVO[]> {
  return http.get("/workflow/procurement-departments");
}

/** 审批操作 - 驱动工作流状态机推进 */
export function approveRequestApi(id: number, data: WorkflowApprovalActionDTO): Promise<WorkflowSubmitResultVO> {
  return http.post(`/workflow/purchase-requests/${id}/approval`, data);
}

/** 测试专用：跳过日期限制，按机房汇总需求池并直接进入供应商报价 */
export function publishDemandPoolForTestApi(): Promise<number> {
  return http.post("/workflow/demand-pool/publish-test");
}

/** 测试专用：统计超过后台配置确认期限仍未处理的审批，不自动退回或驳回 */
export function processApprovalTimeoutsForTestApi(): Promise<number> {
  return http.post("/workflow/approval-tasks/timeout-test");
}

/** 查看审批记录列表 */
export function getApprovalRecordsApi(id: number): Promise<ApprovalRecordVO[]> {
  return http.get(`/workflow/purchase-requests/${id}/records`);
}

/** 查看报价汇总 */
export function getQuoteSummaryApi(id: number): Promise<SupplierQuoteSummaryVO> {
  return http.get(`/workflow/purchase-requests/${id}/quotes`);
}

export function confirmWinningQuoteApi(id: number, data: WorkflowQuoteConfirmDTO): Promise<SupplierQuoteSummaryVO> {
  return http.post(`/workflow/purchase-requests/${id}/quote-confirmation`, data);
}

export function extendQuoteDeadlineApi(id: number, data: WorkflowQuoteDeadlineExtendDTO): Promise<PurchaseRequestVO> {
  return http.post(`/workflow/purchase-requests/${id}/quote-deadline-extension`, data);
}

export function exportQuoteComparisonApi(id: number): Promise<Blob> {
  return http.get(`/workflow/purchase-requests/${id}/quotes/export`, { responseType: "blob" });
}
