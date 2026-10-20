import axios from "axios";
import type { ApiResult } from "../types/common";
import type {
  EhsDepartmentOption,
  EhsPublicPermit,
  EhsPublicPermitCreated,
  EhsPublicPermitSavePayload
} from "../types/ehs";

const client = axios.create({ baseURL: "/api/public/ehs", timeout: 20000 });

client.interceptors.response.use((response) => {
  const result = response.data as ApiResult<unknown>;
  if (result && typeof result.success === "boolean") {
    if (result.success) return result.data;
    return Promise.reject(new Error(result.message || "请求失败"));
  }
  return response.data;
});

function tokenConfig(token: string) {
  return { headers: { "X-EHS-Public-Token": token } };
}

export function listPublicEhsDepartmentsApi(): Promise<EhsDepartmentOption[]> {
  return client.get("/departments");
}

export function createPublicEhsPermitApi(payload: EhsPublicPermitSavePayload): Promise<EhsPublicPermitCreated> {
  return client.post("/permits", payload);
}

export function getPublicEhsPermitApi(token: string): Promise<EhsPublicPermit> {
  return client.get("/permit", tokenConfig(token));
}

export function updatePublicEhsPermitApi(id: number, payload: EhsPublicPermitSavePayload, token: string): Promise<void> {
  return client.put(`/permits/${id}`, payload, tokenConfig(token));
}

export function submitPublicEhsPermitApi(id: number, token: string): Promise<void> {
  return client.post(`/permits/${id}/submit`, undefined, tokenConfig(token));
}

export function startPublicEhsPermitApi(id: number, token: string): Promise<void> {
  return client.post(`/permits/${id}/start`, undefined, tokenConfig(token));
}

export function addPublicEhsSampleApi(id: number, payload: Record<string, unknown>, token: string): Promise<void> {
  return client.post(`/permits/${id}/samples`, payload, tokenConfig(token));
}

export function closePublicEhsPermitApi(id: number, payload: Record<string, unknown>, token: string): Promise<void> {
  return client.post(`/permits/${id}/close`, payload, tokenConfig(token));
}

export function getPublicEhsReviewApi(token: string): Promise<EhsPublicPermit> {
  return client.get("/review", tokenConfig(token));
}

export function approvePublicEhsPermitApi(token: string, action: "APPROVE" | "RETURN", comment: string): Promise<void> {
  return client.post("/review/approval", { action, comment }, tokenConfig(token));
}
