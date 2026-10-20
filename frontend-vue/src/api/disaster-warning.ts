import http from "./http";
import type { PageResult } from "../types/common";
import type {
  DisasterWeatherOverviewVO,
  DisasterWeatherRecordQuery,
  DisasterWeatherRecordVO,
  DisasterWeatherSettingsForm,
  DisasterWeatherSettingsVO,
  DisasterWeatherTriggerForm
} from "../types/disaster-warning";

export function getDisasterWeatherOverviewApi(): Promise<DisasterWeatherOverviewVO> {
  return http.get("/admin/disaster-weather/overview");
}

export function getDisasterWeatherSettingsApi(): Promise<DisasterWeatherSettingsVO> {
  return http.get("/admin/disaster-weather/settings");
}

export function updateDisasterWeatherSettingsApi(data: DisasterWeatherSettingsForm): Promise<void> {
  return http.put("/admin/disaster-weather/settings", data);
}

export function getDisasterWeatherRecordsApi(
  params: DisasterWeatherRecordQuery
): Promise<PageResult<DisasterWeatherRecordVO>> {
  return http.get("/admin/disaster-weather/records/page", { params });
}

export function triggerDisasterWeatherApi(data: DisasterWeatherTriggerForm): Promise<DisasterWeatherRecordVO> {
  return http.post("/admin/disaster-weather/trigger", data);
}
