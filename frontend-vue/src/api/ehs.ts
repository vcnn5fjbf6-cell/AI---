import http from "./http";
import type {
  EhsAiMessage,
  EhsAiSession,
  EhsDepartmentOption,
  EhsAssistantChatResult,
  EhsKnowledgePage,
  EhsOverviewVO,
  EhsPermit,
  EhsPermitPage,
  EhsPermitSavePayload,
  EhsRegistryModule,
  EhsRegistryPage,
  EhsRegistrySavePayload,
  EhsRegistryStats
} from "../types/ehs";

export function getEhsOverviewApi(): Promise<EhsOverviewVO> {
  return http.get("/ehs/overview");
}

export function pageEhsRecordsApi(params: Record<string, unknown>): Promise<EhsRegistryPage> {
  return http.get("/ehs/records/page", { params });
}

export function getEhsRecordApi(id: number) {
  return http.get(`/ehs/records/${id}`);
}

export function getEhsRecordStatsApi(moduleType: EhsRegistryModule): Promise<EhsRegistryStats> {
  return http.get("/ehs/records/stats", { params: { moduleType } });
}

export function createEhsRecordApi(payload: EhsRegistrySavePayload): Promise<number> {
  return http.post("/ehs/records", payload);
}

export function updateEhsRecordApi(id: number, payload: EhsRegistrySavePayload): Promise<void> {
  return http.put(`/ehs/records/${id}`, payload);
}

export function deleteEhsRecordApi(id: number): Promise<void> {
  return http.delete(`/ehs/records/${id}`);
}

export function importEhsRecordsApi(moduleType: EhsRegistryModule, file: File): Promise<number> {
  const form = new FormData();
  form.append("file", file);
  form.append("moduleType", moduleType);
  return http.post("/ehs/records/import", form);
}

export function downloadEhsImportTemplateApi(moduleType: EhsRegistryModule): Promise<Blob> {
  return http.get("/ehs/records/import-template", { params: { moduleType }, responseType: "blob" });
}

export function exportEhsRecordsApi(moduleType: EhsRegistryModule): Promise<Blob> {
  return http.get("/ehs/records/export", { params: { moduleType }, responseType: "blob" });
}

export function pageEhsPermitsApi(params: Record<string, unknown>): Promise<EhsPermitPage> {
  return http.get("/ehs/permits/page", { params });
}

export function getEhsPermitApi(id: number): Promise<EhsPermit> {
  return http.get(`/ehs/permits/${id}`);
}

export function getEhsPermitStatsApi(): Promise<Record<string, number>> {
  return http.get("/ehs/permits/stats");
}

export function getEhsDepartmentsApi(): Promise<EhsDepartmentOption[]> {
  return http.get("/ehs/permits/departments");
}

export function createEhsPermitApi(payload: EhsPermitSavePayload): Promise<number> {
  return http.post("/ehs/permits", payload);
}

export function updateEhsPermitApi(id: number, payload: EhsPermitSavePayload): Promise<void> {
  return http.put(`/ehs/permits/${id}`, payload);
}

export function deleteEhsPermitApi(id: number): Promise<void> {
  return http.delete(`/ehs/permits/${id}`);
}

export function submitEhsPermitApi(id: number): Promise<void> {
  return http.post(`/ehs/permits/${id}/submit`);
}

export function approveEhsPermitApi(id: number, action: "APPROVE" | "RETURN", comment: string): Promise<void> {
  return http.post(`/ehs/permits/${id}/approval`, { action, comment });
}

export function startEhsPermitApi(id: number): Promise<void> {
  return http.post(`/ehs/permits/${id}/start`);
}

export function addEhsPermitSampleApi(id: number, payload: Record<string, unknown>): Promise<void> {
  return http.post(`/ehs/permits/${id}/samples`, payload);
}

export function closeEhsPermitApi(id: number, payload: Record<string, unknown>): Promise<void> {
  return http.post(`/ehs/permits/${id}/close`, payload);
}

export function pageEhsKnowledgeApi(params: Record<string, unknown>): Promise<EhsKnowledgePage> {
  return http.get("/ehs/assistant/knowledge/page", { params });
}

export function createEhsKnowledgeApi(payload: Record<string, unknown>): Promise<number> {
  return http.post("/ehs/assistant/knowledge", payload);
}

export function uploadEhsKnowledgeApi(file: File, title?: string, category?: string): Promise<number> {
  const form = new FormData();
  form.append("file", file);
  if (title) form.append("title", title);
  if (category) form.append("category", category);
  return http.post("/ehs/assistant/knowledge/upload", form);
}

export function deleteEhsKnowledgeApi(id: number): Promise<void> {
  return http.delete(`/ehs/assistant/knowledge/${id}`);
}

export function listEhsAiSessionsApi(): Promise<EhsAiSession[]> {
  return http.get("/ehs/assistant/sessions");
}

export function listEhsAiMessagesApi(sessionId: number): Promise<EhsAiMessage[]> {
  return http.get(`/ehs/assistant/sessions/${sessionId}/messages`);
}

export function deleteEhsAiSessionApi(sessionId: number): Promise<void> {
  return http.delete(`/ehs/assistant/sessions/${sessionId}`);
}

export function chatEhsAssistantApi(message: string, sessionId?: number): Promise<EhsAssistantChatResult> {
  return http.post("/ehs/assistant/chat", { message, sessionId });
}
