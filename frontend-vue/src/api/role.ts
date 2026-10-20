import http from "./http";
import type { PageResult } from "../types/common";
import type { PermissionVO, RoleForm, RolePageQuery, RoleVO } from "../types/system";

export function getRolePageApi(params: RolePageQuery): Promise<PageResult<RoleVO>> {
  return http.get("/admin/roles/page", { params });
}

export function getRoleListApi(): Promise<RoleVO[]> {
  return http.get("/admin/roles/list", {
    params: { pageNo: 1, pageSize: 500 }
  });
}

export function getRoleDetailApi(id: number): Promise<RoleVO> {
  return http.get(`/admin/roles/${id}`);
}

export function createRoleApi(data: RoleForm): Promise<number> {
  return http.post("/admin/roles", data);
}

export function updateRoleApi(id: number, data: RoleForm): Promise<void> {
  return http.put(`/admin/roles/${id}`, data);
}

export function deleteRoleApi(id: number): Promise<void> {
  return http.delete(`/admin/roles/${id}`);
}

export function getPermissionListApi(): Promise<PermissionVO[]> {
  return http.get("/admin/roles/permissions/list");
}
