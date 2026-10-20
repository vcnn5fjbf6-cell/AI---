import http from "./http";
import type { PageResult } from "../types/common";
import type {
  PlatformAppVO,
  PlatformMyAccessVO,
  PlatformPermissionRequestForm,
  PlatformPermissionRequestQuery,
  PlatformPermissionRequestVO,
  PlatformPermissionReviewForm,
  PlatformFeedbackQuery,
  PlatformFeedbackStatusForm,
  PlatformFeedbackSubmitForm,
  PlatformFeedbackVO,
  ProcurementApprovalRouteConfigVO,
  ProcurementApprovalRouteForm,
  ProcurementApprovalRouteQuery,
  ProcurementRuleTestResultVO,
  ProcurementSettingsUpdateForm,
  ProcurementSettingsVO,
  PlatformSummaryVO,
  PlatformUnitForm,
  PlatformUnitVO,
  PlatformUserAccessForm,
  PlatformUserAccessVO,
  DatabaseRow,
  DatabaseRowDeleteForm,
  DatabaseRowQuery,
  DatabaseRowSaveForm,
  DatabaseRowUpdateForm,
  DatabaseTableDetailVO,
  DatabaseTableVO,
  FeishuApprovalCardNotificationQuery,
  FeishuApprovalCardNotificationVO,
  SupplierFeishuQuoteNoticeQuery,
  SupplierFeishuQuoteNoticeVO,
  AiContentRevisionVO,
  AiGovernanceOverviewVO,
  AiKnowledgeBaseForm,
  AiSkillForm,
  ImportFileLogQuery,
  ImportFileLogVO,
  ProcurementAiMessageLogQuery,
  ProcurementAiMessageLogVO,
  SystemErrorLogQuery,
  SystemErrorLogStatusForm,
  SystemErrorLogVO,
  AssetCatalogItemVO,
  AssetCatalogQuery,
  AssetCatalogForm
} from "../types/platform";

export function getPlatformAppsApi(): Promise<PlatformAppVO[]> {
  return http.get("/platform/apps");
}

export function getMyPlatformAccessApi(): Promise<PlatformMyAccessVO> {
  return http.get("/platform/me/access");
}

export function createPlatformPermissionRequestApi(data: PlatformPermissionRequestForm): Promise<number> {
  return http.post("/platform/permission-requests", data);
}

export function getPlatformSummaryApi(): Promise<PlatformSummaryVO> {
  return http.get("/admin/platform/summary");
}

export function getPlatformUserAccessListApi(): Promise<PlatformUserAccessVO[]> {
  return http.get("/admin/platform/users/access");
}

export function updatePlatformUserAccessApi(userId: number, data: PlatformUserAccessForm): Promise<void> {
  return http.put(`/admin/platform/users/${userId}/access`, data);
}

export function getPlatformUnitsApi(): Promise<PlatformUnitVO[]> {
  return http.get("/admin/platform/units");
}

export function savePlatformUnitApi(data: PlatformUnitForm): Promise<number> {
  return http.post("/admin/platform/units", data);
}

export function deletePlatformUnitApi(id: number): Promise<void> {
  return http.delete(`/admin/platform/units/${id}`);
}

export function getPlatformPermissionRequestPageApi(
  params: PlatformPermissionRequestQuery
): Promise<PageResult<PlatformPermissionRequestVO>> {
  return http.get("/admin/platform/permission-requests/page", { params });
}

export function reviewPlatformPermissionRequestApi(
  id: number,
  data: PlatformPermissionReviewForm
): Promise<void> {
  return http.put(`/admin/platform/permission-requests/${id}/review`, data);
}

export function submitPlatformFeedbackApi(data: PlatformFeedbackSubmitForm): Promise<number> {
  const formData = new FormData();
  formData.append("category", data.category || "SUGGESTION");
  formData.append("title", data.title || "");
  formData.append("content", data.content || "");
  formData.append("links", data.links || "");
  formData.append("pageUrl", data.pageUrl || "");
  for (const image of data.images || []) {
    formData.append("images", image);
  }
  return http.post("/platform/feedbacks", formData, {
    headers: { "Content-Type": "multipart/form-data" },
    timeout: 60000
  });
}

export function getPlatformFeedbackPageApi(params: PlatformFeedbackQuery): Promise<PageResult<PlatformFeedbackVO>> {
  return http.get("/admin/platform/feedbacks/page", { params });
}

export function getPlatformFeedbackDetailApi(id: number): Promise<PlatformFeedbackVO> {
  return http.get(`/admin/platform/feedbacks/${id}`);
}

export function updatePlatformFeedbackStatusApi(id: number, data: PlatformFeedbackStatusForm): Promise<void> {
  return http.put(`/admin/platform/feedbacks/${id}/status`, data);
}

export function downloadPlatformFeedbackAttachmentApi(id: number): Promise<Blob> {
  return http.get(`/admin/platform/feedbacks/attachments/${id}/content`, { responseType: "blob" });
}

export function getProcurementSettingsApi(): Promise<ProcurementSettingsVO> {
  return http.get("/admin/procurement/settings");
}

export function updateProcurementSettingsApi(data: ProcurementSettingsUpdateForm): Promise<void> {
  return http.put("/admin/procurement/settings", data);
}

export function getAiGovernanceOverviewApi(): Promise<AiGovernanceOverviewVO> {
  return http.get("/admin/ai-governance/overview");
}

export function getAiContentRevisionsApi(targetType: string, targetId: number): Promise<AiContentRevisionVO[]> {
  return http.get("/admin/ai-governance/revisions", { params: { targetType, targetId } });
}

export function createAiKnowledgeBaseApi(data: AiKnowledgeBaseForm): Promise<number> {
  return http.post("/admin/ai-governance/knowledge-bases", data);
}

export function updateAiKnowledgeBaseApi(id: number, data: AiKnowledgeBaseForm): Promise<void> {
  return http.put(`/admin/ai-governance/knowledge-bases/${id}`, data);
}

export function deleteAiKnowledgeBaseApi(id: number): Promise<void> {
  return http.delete(`/admin/ai-governance/knowledge-bases/${id}`);
}

export function createAiSkillApi(data: AiSkillForm): Promise<number> {
  return http.post("/admin/ai-governance/skills", data);
}

export function updateAiSkillApi(id: number, data: AiSkillForm): Promise<void> {
  return http.put(`/admin/ai-governance/skills/${id}`, data);
}

export function deleteAiSkillApi(id: number): Promise<void> {
  return http.delete(`/admin/ai-governance/skills/${id}`);
}

export function reviewAiChangeProposalApi(
  id: number,
  data: { action: "APPROVE" | "REJECT"; comment?: string }
): Promise<void> {
  return http.post(`/admin/ai-governance/proposals/${id}/review`, data);
}

export function testProcurementRuleApi(actionCode: string): Promise<ProcurementRuleTestResultVO> {
  return http.post(`/admin/procurement/settings/rule-tests/${actionCode}`);
}

export function sendFeishuApprovalCardTestApi(): Promise<ProcurementRuleTestResultVO> {
  return http.post("/admin/procurement/settings/feishu-approval-card/test");
}

export function sendFeishuSupplierQuoteCardTestApi(data: {
  receiveIdType: string;
  receiveId: string;
}): Promise<ProcurementRuleTestResultVO> {
  return http.post("/admin/procurement/settings/feishu-supplier-quote-card/test", data);
}

export function getFeishuApprovalCardNotificationsApi(
  params: FeishuApprovalCardNotificationQuery
): Promise<PageResult<FeishuApprovalCardNotificationVO>> {
  return http.get("/admin/procurement/settings/feishu-approval-card/notifications", { params });
}

export function resendFeishuApprovalCardNotificationApi(id: number): Promise<ProcurementRuleTestResultVO> {
  return http.post(`/admin/procurement/settings/feishu-approval-card/notifications/${id}/resend`);
}

export function getSupplierFeishuQuoteNoticesApi(
  params: SupplierFeishuQuoteNoticeQuery
): Promise<PageResult<SupplierFeishuQuoteNoticeVO>> {
  return http.get("/admin/procurement/settings/feishu-supplier-quote-card/notices", { params });
}

export function resendSupplierFeishuQuoteNoticeApi(id: number): Promise<ProcurementRuleTestResultVO> {
  return http.post(`/admin/procurement/settings/feishu-supplier-quote-card/notices/${id}/resend`);
}

export function getProcurementApprovalRouteConfigApi(
  params: ProcurementApprovalRouteQuery = {}
): Promise<ProcurementApprovalRouteConfigVO> {
  return http.get("/admin/procurement/departments/config", { params });
}

export function exportProcurementApprovalRouteTemplateApi(
  params: ProcurementApprovalRouteQuery = {}
): Promise<Blob> {
  return http.get("/admin/procurement/departments/import-template", { params, responseType: "blob" });
}

export function importProcurementApprovalRouteTemplateApi(file: File): Promise<number> {
  const formData = new FormData();
  formData.append("file", file);
  return http.post("/admin/procurement/departments/import", formData, {
    headers: { "Content-Type": "multipart/form-data" }
  });
}

export function createProcurementApprovalRouteApi(data: ProcurementApprovalRouteForm): Promise<number> {
  return http.post("/admin/procurement/departments", data);
}

export function updateProcurementApprovalRouteApi(id: number, data: ProcurementApprovalRouteForm): Promise<void> {
  return http.put(`/admin/procurement/departments/${id}`, data);
}

export function updateProcurementApprovalRouteAddressApi(id: number, data: ProcurementApprovalRouteForm): Promise<void> {
  return http.put(`/admin/procurement/departments/${id}/address`, data);
}

export function deleteProcurementApprovalRouteApi(id: number): Promise<void> {
  return http.delete(`/admin/procurement/departments/${id}`);
}

export function getAssetCatalogPageApi(params: AssetCatalogQuery): Promise<PageResult<AssetCatalogItemVO>> {
  return http.get("/admin/platform/asset-catalog/page", { params });
}

export function exportAssetCatalogTemplateApi(): Promise<Blob> {
  return http.get("/admin/platform/asset-catalog/export-template", { responseType: "blob" });
}

export function importAssetCatalogTemplateApi(file: File): Promise<number> {
  const formData = new FormData();
  formData.append("file", file);
  return http.post("/admin/platform/asset-catalog/import", formData, {
    headers: { "Content-Type": "multipart/form-data" }
  });
}

export function createAssetCatalogItemApi(data: AssetCatalogForm): Promise<number> {
  return http.post("/admin/platform/asset-catalog", data);
}

export function updateAssetCatalogItemApi(id: number, data: AssetCatalogForm): Promise<void> {
  return http.put(`/admin/platform/asset-catalog/${id}`, data);
}

export function updateAssetCatalogItemEnabledApi(id: number, enabled: number): Promise<void> {
  return http.put(`/admin/platform/asset-catalog/${id}/enabled`, { enabled });
}

export function getSystemErrorLogPageApi(params: SystemErrorLogQuery): Promise<PageResult<SystemErrorLogVO>> {
  return http.get("/admin/error-logs/page", { params });
}

export function getSystemErrorLogDetailApi(id: number): Promise<SystemErrorLogVO> {
  return http.get(`/admin/error-logs/${id}`);
}

export function updateSystemErrorLogStatusApi(id: number, data: SystemErrorLogStatusForm): Promise<void> {
  return http.put(`/admin/error-logs/${id}/status`, data);
}

export function getProcurementAiMessageLogPageApi(
  params: ProcurementAiMessageLogQuery
): Promise<PageResult<ProcurementAiMessageLogVO>> {
  return http.get("/admin/procurement-ai/message-logs/page", { params });
}

export function getProcurementAiMessageLogDetailApi(id: number): Promise<ProcurementAiMessageLogVO> {
  return http.get(`/admin/procurement-ai/message-logs/${id}`);
}

export function getImportFileLogPageApi(params: ImportFileLogQuery): Promise<PageResult<ImportFileLogVO>> {
  return http.get("/admin/import-file-logs/page", { params });
}

export function getImportFileLogDetailApi(id: number): Promise<ImportFileLogVO> {
  return http.get(`/admin/import-file-logs/${id}`);
}

export function getDatabaseTablesApi(keyword?: string): Promise<DatabaseTableVO[]> {
  return http.get("/admin/database/tables", { params: { keyword: keyword || undefined } });
}

export function getDatabaseTableDetailApi(tableName: string): Promise<DatabaseTableDetailVO> {
  return http.get(`/admin/database/tables/${encodeURIComponent(tableName)}`);
}

export function getDatabaseRowsApi(tableName: string, params: DatabaseRowQuery): Promise<PageResult<DatabaseRow>> {
  return http.get(`/admin/database/tables/${encodeURIComponent(tableName)}/rows`, { params });
}

export function createDatabaseRowApi(tableName: string, data: DatabaseRowSaveForm): Promise<void> {
  return http.post(`/admin/database/tables/${encodeURIComponent(tableName)}/rows`, data);
}

export function updateDatabaseRowApi(tableName: string, data: DatabaseRowUpdateForm): Promise<void> {
  return http.put(`/admin/database/tables/${encodeURIComponent(tableName)}/rows`, data);
}

export function deleteDatabaseRowApi(tableName: string, data: DatabaseRowDeleteForm): Promise<void> {
  return http.delete(`/admin/database/tables/${encodeURIComponent(tableName)}/rows`, { data });
}
