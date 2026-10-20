import http from "./http";

export type BiFieldRole = "DIMENSION" | "MEASURE";
export type BiWidgetType = "metric" | "table" | "detail" | "bar" | "pie" | "donut" | "line";

export interface ProcurementBiFieldVO {
  fieldCode: string;
  fieldName: string;
  dataType: string;
  semanticType?: string;
  role: BiFieldRole | string;
  unit?: string | null;
  defaultAggregation?: string | null;
  filterable: boolean;
  groupable: boolean;
  sortable: boolean;
}

export interface ProcurementBiDatasetVO {
  datasetCode: string;
  datasetName: string;
  description?: string;
  fields: ProcurementBiFieldVO[];
}

export interface ProcurementBiMetricDTO {
  fieldCode: string;
  aggregation?: string;
}

export interface ProcurementBiFilterDTO {
  fieldCode: string;
  operator: string;
  value?: unknown;
  values?: unknown[];
}

export interface ProcurementBiSortDTO {
  fieldCode: string;
  direction?: "ASC" | "DESC" | string;
}

export interface ProcurementBiQueryDTO {
  datasetCode: string;
  detail?: boolean;
  fields?: string[];
  dimensions?: string[];
  metrics?: ProcurementBiMetricDTO[];
  filters?: ProcurementBiFilterDTO[];
  sorts?: ProcurementBiSortDTO[];
  limit?: number;
}

export interface ProcurementBiColumnVO {
  code: string;
  label: string;
  role: string;
  dataType: string;
  semanticType?: string;
  unit?: string;
  aggregation?: string;
}

export interface ProcurementBiQueryResultVO {
  datasetCode: string;
  columns: ProcurementBiColumnVO[];
  rows: Record<string, unknown>[];
  summary?: Record<string, unknown>;
}

export interface ProcurementBiDashboardVO {
  dashboardCode: string;
  dashboardName: string;
  layout: ProcurementBiLayout;
}

export interface ProcurementBiLayout {
  version?: number;
  columns?: number;
  widgets?: ProcurementBiWidgetConfig[];
}

export interface ProcurementBiWidgetConfig {
  id: string;
  title: string;
  type: BiWidgetType;
  datasetCode: string;
  x: number;
  y: number;
  w: number;
  h: number;
  visible?: boolean;
  detail?: boolean;
  fields?: string[];
  dimensions?: string[];
  metrics?: ProcurementBiMetricDTO[];
  filters?: ProcurementBiFilterDTO[];
  timeRange?: string[];
  sorts?: ProcurementBiSortDTO[];
  limit?: number;
  theme?: string;
  showLegend?: boolean;
  showLabel?: boolean;
}

export function getProcurementBiDatasetsApi(): Promise<ProcurementBiDatasetVO[]> {
  return http.get("/procurement/bi/datasets", { timeout: 8000 });
}

export function queryProcurementBiApi(data: ProcurementBiQueryDTO): Promise<ProcurementBiQueryResultVO> {
  return http.post("/procurement/bi/query", data, { timeout: 10000 });
}

export function getProcurementBiDashboardApi(): Promise<ProcurementBiDashboardVO> {
  return http.get("/procurement/bi/dashboards/default", { timeout: 8000 });
}

export function saveProcurementBiDashboardApi(layout: ProcurementBiLayout): Promise<ProcurementBiDashboardVO> {
  return http.post("/procurement/bi/dashboards/default", {
    dashboardCode: "procurement_board",
    layout
  }, { timeout: 10000 });
}
