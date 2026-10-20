import http from "./http";
import type { PageResult } from "../types/common";
import type {
  MonthlyAggregateResult,
  MonthlyDemandCreateForm,
  MonthlyDemandQuery,
  MonthlyDemandQuantityUpdateForm,
  MonthlyDemandSupplementForm,
  MonthlyDemandVO
} from "../types/monthly-procurement";

export function submitMonthlyDemandApi(data: MonthlyDemandCreateForm): Promise<MonthlyDemandVO> {
  return http.post("/workflow/monthly-procurement/demands", data);
}

export function getMyMonthlyDemandsApi(params: MonthlyDemandQuery): Promise<PageResult<MonthlyDemandVO>> {
  return http.get("/workflow/monthly-procurement/demands/my", { params });
}

export function getCurrentMonthlyDemandsApi(params: MonthlyDemandQuery): Promise<PageResult<MonthlyDemandVO>> {
  return http.get("/workflow/monthly-procurement/demands/current", { params });
}

export function supplementMonthlyDemandApi(id: number, data: MonthlyDemandSupplementForm): Promise<MonthlyDemandVO> {
  return http.post(`/workflow/monthly-procurement/demands/${id}/supplement`, data);
}

export function updateMonthlyDemandQuantityApi(id: number, data: MonthlyDemandQuantityUpdateForm): Promise<MonthlyDemandVO> {
  return http.patch(`/workflow/monthly-procurement/demands/${id}/quantity`, data);
}

export function deleteMonthlyDemandApi(id: number): Promise<void> {
  return http.delete(`/workflow/monthly-procurement/demands/${id}`);
}

export function aggregateMonthlyDemandsApi(planMonth?: string): Promise<MonthlyAggregateResult> {
  return http.post("/workflow/monthly-procurement/aggregate", { planMonth });
}
