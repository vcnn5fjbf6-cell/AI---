import http from "./http";
import type { PageResult } from "../types/common";
import type {
  FeishuDepartmentSearchResult,
  FeishuUserSyncPayload,
  FeishuUserSyncResult,
  UserForm,
  UserImportResult,
  UserOffboardingImpact,
  UserOffboardingPayload,
  UserPageQuery,
  UserVO
} from "../types/system";

export function getUserPageApi(params: UserPageQuery): Promise<PageResult<UserVO>> {
  return http.get("/admin/users/page", { params });
}

export function getUserListApi(params: Partial<UserPageQuery> = {}): Promise<UserVO[]> {
  return http.get("/admin/users/list", { params });
}

export function getUserDetailApi(id: number): Promise<UserVO> {
  return http.get(`/admin/users/${id}`);
}

export function getUserOffboardingImpactApi(id: number): Promise<UserOffboardingImpact> {
  return http.get(`/admin/users/${id}/offboarding-impact`);
}

export function createUserApi(data: UserForm): Promise<number> {
  return http.post("/admin/users", data);
}

export function syncFeishuUsersApi(data: FeishuUserSyncPayload = {}): Promise<FeishuUserSyncResult> {
  return http.post("/admin/users/feishu-sync", data);
}

export function searchFeishuDepartmentsApi(keyword: string): Promise<FeishuDepartmentSearchResult[]> {
  return http.get("/admin/users/feishu-departments/search", { params: { keyword } });
}

export function downloadUserImportTemplateApi(): Promise<Blob> {
  return http.get("/admin/users/import-template", { responseType: "blob" });
}

export function importUsersApi(file: File): Promise<UserImportResult> {
  const formData = new FormData();
  formData.append("file", file);
  return http.post("/admin/users/import", formData, {
    headers: { "Content-Type": "multipart/form-data" },
    timeout: 60000
  });
}

export function updateUserApi(id: number, data: UserForm): Promise<void> {
  return http.put(`/admin/users/${id}`, data);
}

export function offboardUserApi(id: number, data: UserOffboardingPayload): Promise<void> {
  return http.post(`/admin/users/${id}/offboarding`, data);
}

export function deleteUserApi(id: number): Promise<void> {
  return http.delete(`/admin/users/${id}`);
}
