<template>
  <VnetPage title="采购看板">
    <template #actions>
      <el-button :icon="Setting" @click="emit('open-custom')">自定义分析</el-button>
      <el-button :icon="Refresh" :loading="loading" @click="reloadExecutive">刷新</el-button>
      <el-button :icon="Download" @click="exportDashboard">导出报表</el-button>
    </template>

    <div v-loading="loading" class="executive-dashboard">
      <section class="filter-card">
        <div>
          <h3>采购经营分析</h3>
        </div>
        <div class="filters">
          <el-date-picker
            v-model="filters.dateRange"
            type="daterange"
            unlink-panels
            value-format="YYYY-MM-DD"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            :clearable="false"
            @change="handleDateChange"
          />
          <el-select
            v-model="filters.businessRegion"
            clearable
            filterable
            placeholder="全部业务区域"
            @change="handleRegionChange"
          >
            <el-option v-for="item in regionOptions" :key="item" :label="item" :value="item" />
          </el-select>
          <el-select
            v-model="filters.roomGroup"
            clearable
            filterable
            placeholder="全部机房群"
            @change="reloadExecutive"
          >
            <el-option v-for="item in roomOptions" :key="item" :label="item" :value="item" />
          </el-select>
        </div>
      </section>

      <section class="metric-grid">
        <article class="saving-card">
          <span>本期累计节省金额</span>
          <strong>{{ formatMoney(summary.savingAmount) }}</strong>
          <div>
            <em>节省率 {{ formatRate(summary.savingRate) }}</em>
            <em>{{ summary.requestCount }} 笔采购申请</em>
          </div>
        </article>
        <article class="metric-card metric-card--blue">
          <span>采购需求金额</span>
          <strong>{{ formatMoney(summary.requestAmount) }}</strong>
          <div><em>同比 {{ compareText(summary.requestAmount, yearSummary.requestAmount) }}</em><em>环比 {{ compareText(summary.requestAmount, previousSummary.requestAmount) }}</em></div>
        </article>
        <article class="metric-card metric-card--green">
          <span>实际中标金额</span>
          <strong>{{ formatMoney(summary.awardAmount) }}</strong>
          <div><em>同比 {{ compareText(summary.awardAmount, yearSummary.awardAmount) }}</em><em>环比 {{ compareText(summary.awardAmount, previousSummary.awardAmount) }}</em></div>
        </article>
        <article class="metric-card metric-card--orange">
          <span>采购数量</span>
          <strong>{{ formatNumber(summary.qty) }} 件</strong>
          <div><em>同比 {{ compareText(summary.qty, yearSummary.qty) }}</em><em>环比 {{ compareText(summary.qty, previousSummary.qty) }}</em></div>
        </article>
      </section>

      <section class="dashboard-row dashboard-row--main">
        <article class="data-panel trend-panel">
          <header>
            <div>
              <h3>采购金额与降本趋势</h3>
            </div>
            <span>{{ rangeLabel }}</span>
          </header>
          <div v-if="trendRows.length" ref="trendChartRef" class="trend-chart" />
          <el-empty v-else description="当前筛选范围暂无趋势数据" :image-size="72" />
        </article>

        <article class="data-panel compare-panel">
          <header>
            <div>
              <h3>同比 / 环比概览</h3>
            </div>
          </header>
          <div class="compare-grid">
            <div v-for="item in compareItems" :key="item.label" class="compare-cell">
              <span>{{ item.label }}</span>
              <strong>{{ item.value }}</strong>
              <div>
                <em :class="changeClass(item.yearValue)">同比 {{ item.yearText }}</em>
                <em :class="changeClass(item.previousValue)">环比 {{ item.previousText }}</em>
              </div>
            </div>
          </div>
        </article>
      </section>

      <section class="dashboard-row dashboard-row--breakdown">
        <article class="data-panel region-panel">
          <header>
            <div>
              <h3>区域降本贡献</h3>
            </div>
            <span v-if="unmatchedRequestCount" class="warning-chip">{{ unmatchedRequestCount }} 笔未归属</span>
          </header>
          <div v-if="regionRows.length" class="region-list">
            <div v-for="row in regionRows" :key="row.name" class="region-row">
              <div class="region-name">
                <strong>{{ row.name }}</strong>
                <span>{{ row.requestCount }} 笔申请</span>
              </div>
              <div class="progress-track">
                <i :style="{ width: `${row.progress}%` }" :class="{ 'is-warning': row.name === '未归属' }" />
              </div>
              <strong>{{ formatMoney(row.savingAmount) }}</strong>
              <span>{{ formatRate(row.savingRate) }}</span>
            </div>
          </div>
          <el-empty v-else description="当前筛选范围暂无区域数据" :image-size="72" />
        </article>

        <article class="data-panel room-panel">
          <header>
            <div>
              <h3>机房降本排行</h3>
            </div>
            <span>{{ roomScopeLabel }}</span>
          </header>
          <div v-if="roomRows.length" class="room-list">
            <div v-for="(row, index) in roomRows" :key="row.key" class="room-row" :class="{ 'is-over': row.savingAmount < 0 }">
              <span class="rank">{{ row.savingAmount < 0 ? "!" : index + 1 }}</span>
              <div>
                <strong>{{ row.buildingName || row.roomGroup || "未归属机房" }}</strong>
                <span>{{ row.businessRegion || "未归属" }} / {{ row.roomGroup || "未归属" }}</span>
              </div>
              <div>
                <strong>{{ row.savingAmount < 0 ? `超预算 ${formatMoney(Math.abs(row.savingAmount))}` : `节省 ${formatMoney(row.savingAmount)}` }}</strong>
                <span>需求 {{ formatMoney(row.requestAmount) }} · 中标 {{ formatMoney(row.awardAmount) }}</span>
              </div>
            </div>
          </div>
          <el-empty v-else description="当前筛选范围暂无机房数据" :image-size="72" />
        </article>
      </section>

      <section class="data-panel supplier-panel">
        <header>
          <div>
            <h3>供应商降本贡献</h3>
          </div>
        </header>
        <el-table :data="supplierRows" stripe size="small" height="210">
          <el-table-column prop="name" label="供应商" min-width="180" />
          <el-table-column label="需求金额" min-width="140" align="right">
            <template #default="{ row }">{{ formatMoney(row.requestAmount) }}</template>
          </el-table-column>
          <el-table-column label="中标金额" min-width="140" align="right">
            <template #default="{ row }">{{ formatMoney(row.awardAmount) }}</template>
          </el-table-column>
          <el-table-column label="节省金额" min-width="140" align="right">
            <template #default="{ row }">
              <span :class="row.savingAmount >= 0 ? 'saving-positive' : 'saving-negative'">
                {{ formatMoney(row.savingAmount) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="节省率" min-width="110" align="right">
            <template #default="{ row }">{{ formatRate(row.savingRate) }}</template>
          </el-table-column>
          <el-table-column label="降本贡献" min-width="190">
            <template #default="{ row }">
              <div class="contribution-cell">
                <i :style="{ width: `${row.contribution}%` }" />
                <span>{{ row.contribution.toFixed(1) }}%</span>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </section>
    </div>
  </VnetPage>
</template>

<script setup lang="ts">
import { Download, Refresh, Setting } from "@element-plus/icons-vue";
import { ElMessage } from "element-plus";
import { BarChart, LineChart } from "echarts/charts";
import { GridComponent, LegendComponent, TooltipComponent } from "echarts/components";
import { init, use, type ECharts, type EChartsCoreOption } from "echarts/core";
import { CanvasRenderer } from "echarts/renderers";
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from "vue";
import VnetPage from "../../components/vnet/VnetPage.vue";
import {
  queryProcurementBiApi,
  type ProcurementBiFilterDTO,
  type ProcurementBiMetricDTO,
  type ProcurementBiQueryResultVO,
  type ProcurementBiSortDTO
} from "../../api/procurement-bi";

use([BarChart, LineChart, GridComponent, LegendComponent, TooltipComponent, CanvasRenderer]);

const emit = defineEmits<{
  "open-custom": [];
}>();

interface ExecutiveSummary {
  requestAmount: number;
  awardAmount: number;
  savingAmount: number;
  savingRate: number;
  qty: number;
  supplierCount: number;
  requestCount: number;
  unitCost: number;
}

interface BreakdownRow {
  name: string;
  requestAmount: number;
  awardAmount: number;
  savingAmount: number;
  savingRate: number;
  requestCount: number;
  progress: number;
}

interface RoomRow {
  key: string;
  businessRegion: string;
  roomGroup: string;
  buildingName: string;
  requestAmount: number;
  awardAmount: number;
  savingAmount: number;
  savingRate: number;
}

interface SupplierRow extends BreakdownRow {
  contribution: number;
}

interface CompareItem {
  label: string;
  value: string;
  yearText: string;
  previousText: string;
  yearValue: number | null;
  previousValue: number | null;
}

const today = new Date();
const filters = reactive({
  dateRange: [formatDate(new Date(today.getFullYear(), today.getMonth(), 1)), formatDate(today)] as string[],
  businessRegion: "",
  roomGroup: ""
});
const loading = ref(false);
const regionOptions = ref<string[]>([]);
const roomOptions = ref<string[]>([]);
const summary = reactive(emptySummary());
const yearSummary = reactive(emptySummary());
const previousSummary = reactive(emptySummary());
const trendRows = ref<Record<string, unknown>[]>([]);
const regionRows = ref<BreakdownRow[]>([]);
const roomRows = ref<RoomRow[]>([]);
const supplierRows = ref<SupplierRow[]>([]);
const trendChartRef = ref<HTMLElement | null>(null);
let trendChart: ECharts | null = null;
let requestVersion = 0;

const rangeLabel = computed(() => filters.dateRange.length === 2 ? `${filters.dateRange[0]} 至 ${filters.dateRange[1]}` : "全部时间");
const roomScopeLabel = computed(() => [filters.businessRegion || "全部区域", filters.roomGroup || "全部机房群"].join(" / "));
const unmatchedRequestCount = computed(() => regionRows.value.find((row) => row.name === "未归属")?.requestCount || 0);
const compareItems = computed<CompareItem[]>(() => [
  compareItem("节省金额", formatMoney(summary.savingAmount), summary.savingAmount, yearSummary.savingAmount, previousSummary.savingAmount),
  compareItem("节省率", formatRate(summary.savingRate), summary.savingRate, yearSummary.savingRate, previousSummary.savingRate, "point"),
  compareItem("单件采购成本", formatMoney(summary.unitCost), summary.unitCost, yearSummary.unitCost, previousSummary.unitCost),
  compareItem("成交供应商", `${formatNumber(summary.supplierCount)} 家`, summary.supplierCount, yearSummary.supplierCount, previousSummary.supplierCount)
]);

onMounted(async () => {
  window.addEventListener("resize", resizeTrendChart);
  await reloadExecutive();
});

onBeforeUnmount(() => {
  window.removeEventListener("resize", resizeTrendChart);
  trendChart?.dispose();
  trendChart = null;
});

async function handleDateChange(): Promise<void> {
  filters.businessRegion = "";
  filters.roomGroup = "";
  await reloadExecutive();
}

async function handleRegionChange(): Promise<void> {
  filters.roomGroup = "";
  await reloadExecutive();
}

async function reloadExecutive(): Promise<void> {
  if (filters.dateRange.length !== 2) {
    return;
  }
  const version = ++requestVersion;
  loading.value = true;
  try {
    const currentRange = normalizeRange(filters.dateRange);
    const yearRange = shiftYearRange(currentRange);
    const previousRange = previousPeriodRange(currentRange);

    const [regions, rooms] = await Promise.all([
      queryRows(["businessRegion"], [metric("requestId", "COUNT_DISTINCT")], currentRange, { includeRegion: false, includeRoom: false }),
      queryRows(["roomGroup"], [metric("requestId", "COUNT_DISTINCT")], currentRange, { includeRoom: false })
    ]);
    if (version !== requestVersion) return;
    regionOptions.value = uniqueText(regions, "businessRegion");
    roomOptions.value = uniqueText(rooms, "roomGroup");

    const summaryMetrics = [
      metric("requestAmount", "SUM"),
      metric("awardAmount", "SUM"),
      metric("qty", "SUM"),
      metric("supplierId", "COUNT_DISTINCT"),
      metric("requestId", "COUNT_DISTINCT")
    ];
    const [currentResult, yearResult, previousResult, trendResult, regionResult, roomResult, supplierResult] = await Promise.all([
      queryRows([], summaryMetrics, currentRange),
      queryRows([], summaryMetrics, yearRange),
      queryRows([], summaryMetrics, previousRange),
      queryRows(["createdMonth"], [metric("requestAmount", "SUM"), metric("awardAmount", "SUM")], currentRange, {}, [{ fieldCode: "createdMonth", direction: "ASC" }], 36),
      queryRows(["businessRegion"], [metric("requestAmount", "SUM"), metric("awardAmount", "SUM"), metric("requestId", "COUNT_DISTINCT")], currentRange, {}, [{ fieldCode: "requestAmount", direction: "DESC" }], 30),
      queryRows(["businessRegion", "roomGroup", "buildingName"], [metric("requestAmount", "SUM"), metric("awardAmount", "SUM")], currentRange, {}, [{ fieldCode: "requestAmount", direction: "DESC" }], 20),
      queryRows(["supplierName"], [metric("requestAmount", "SUM"), metric("awardAmount", "SUM")], currentRange, {}, [{ fieldCode: "awardAmount", direction: "DESC" }], 20)
    ]);
    if (version !== requestVersion) return;

    Object.assign(summary, summaryFrom(currentResult[0]));
    Object.assign(yearSummary, summaryFrom(yearResult[0]));
    Object.assign(previousSummary, summaryFrom(previousResult[0]));
    trendRows.value = trendResult;
    regionRows.value = mapBreakdownRows(regionResult, "businessRegion");
    roomRows.value = mapRoomRows(roomResult);
    supplierRows.value = mapSupplierRows(supplierResult);
    await nextTick();
    renderTrendChart();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : "采购经营数据加载失败");
  } finally {
    if (version === requestVersion) {
      loading.value = false;
    }
  }
}

async function queryRows(
  dimensions: string[],
  metrics: ProcurementBiMetricDTO[],
  range: string[],
  options: { includeRegion?: boolean; includeRoom?: boolean } = {},
  sorts: ProcurementBiSortDTO[] = [],
  limit = 100
): Promise<Record<string, unknown>[]> {
  const result: ProcurementBiQueryResultVO = await queryProcurementBiApi({
    datasetCode: "purchase_items",
    dimensions,
    metrics,
    filters: buildFilters(range, options),
    sorts,
    limit
  });
  return result.rows || [];
}

function buildFilters(
  range: string[],
  options: { includeRegion?: boolean; includeRoom?: boolean }
): ProcurementBiFilterDTO[] {
  const result: ProcurementBiFilterDTO[] = [
    {
      fieldCode: "createdAt",
      operator: "BETWEEN",
      values: range
    },
    {
      fieldCode: "supplierConfirmStatus",
      operator: "=",
      value: "CONFIRMED"
    }
  ];
  if (options.includeRegion !== false && filters.businessRegion) {
    result.push({ fieldCode: "businessRegion", operator: "=", value: filters.businessRegion });
  }
  if (options.includeRoom !== false && filters.roomGroup) {
    result.push({ fieldCode: "roomGroup", operator: "=", value: filters.roomGroup });
  }
  return result;
}

function metric(fieldCode: string, aggregation: string): ProcurementBiMetricDTO {
  return { fieldCode, aggregation };
}

function summaryFrom(row?: Record<string, unknown>): ExecutiveSummary {
  const requestAmount = valueOf(row, "requestAmount__sum");
  const awardAmount = valueOf(row, "awardAmount__sum");
  const qty = valueOf(row, "qty__sum");
  const savingAmount = requestAmount - awardAmount;
  return {
    requestAmount,
    awardAmount,
    savingAmount,
    savingRate: requestAmount ? savingAmount / requestAmount : 0,
    qty,
    supplierCount: valueOf(row, "supplierId__count_distinct"),
    requestCount: valueOf(row, "requestId__count_distinct"),
    unitCost: qty ? awardAmount / qty : 0
  };
}

function mapBreakdownRows(rows: Record<string, unknown>[], nameField: string): BreakdownRow[] {
  const mapped = rows.map((row) => {
    const requestAmount = valueOf(row, "requestAmount__sum");
    const awardAmount = valueOf(row, "awardAmount__sum");
    const savingAmount = requestAmount - awardAmount;
    return {
      name: String(row[nameField] || "未归属"),
      requestAmount,
      awardAmount,
      savingAmount,
      savingRate: requestAmount ? savingAmount / requestAmount : 0,
      requestCount: valueOf(row, "requestId__count_distinct"),
      progress: 0
    };
  }).sort((left, right) => right.savingAmount - left.savingAmount);
  const max = Math.max(...mapped.map((row) => Math.max(row.savingAmount, 0)), 0);
  return mapped.map((row) => ({
    ...row,
    progress: max ? Math.max(0, row.savingAmount) / max * 100 : 0
  }));
}

function mapRoomRows(rows: Record<string, unknown>[]): RoomRow[] {
  return rows.map((row) => {
    const requestAmount = valueOf(row, "requestAmount__sum");
    const awardAmount = valueOf(row, "awardAmount__sum");
    const savingAmount = requestAmount - awardAmount;
    const businessRegion = String(row.businessRegion || "");
    const roomGroup = String(row.roomGroup || "");
    const buildingName = String(row.buildingName || "");
    return {
      key: [businessRegion, roomGroup, buildingName].join("|"),
      businessRegion,
      roomGroup,
      buildingName,
      requestAmount,
      awardAmount,
      savingAmount,
      savingRate: requestAmount ? savingAmount / requestAmount : 0
    };
  }).sort((left, right) => right.savingAmount - left.savingAmount).slice(0, 4);
}

function mapSupplierRows(rows: Record<string, unknown>[]): SupplierRow[] {
  const mapped = mapBreakdownRows(rows, "supplierName");
  const totalPositiveSaving = mapped.reduce((sum, row) => sum + Math.max(row.savingAmount, 0), 0);
  return mapped.map((row) => ({
    ...row,
    contribution: totalPositiveSaving ? Math.max(row.savingAmount, 0) / totalPositiveSaving * 100 : 0
  })).slice(0, 8);
}

function renderTrendChart(): void {
  if (!trendChartRef.value) {
    return;
  }
  trendChart?.dispose();
  trendChart = init(trendChartRef.value);
  const months = trendRows.value.map((row) => String(row.createdMonth || ""));
  const requestValues = trendRows.value.map((row) => valueOf(row, "requestAmount__sum"));
  const awardValues = trendRows.value.map((row) => valueOf(row, "awardAmount__sum"));
  const savingValues = requestValues.map((value, index) => value - awardValues[index]);
  const option: EChartsCoreOption = {
    color: ["#165dff", "#107f4b", "#b76a00"],
    tooltip: { trigger: "axis", valueFormatter: (value: unknown) => formatMoney(Number(value || 0)) },
    legend: { top: 0, right: 4, itemWidth: 10, itemHeight: 8, textStyle: { color: "#64748b", fontSize: 11 } },
    grid: { left: 58, right: 20, top: 42, bottom: 34 },
    xAxis: { type: "category", data: months, axisTick: { show: false }, axisLine: { lineStyle: { color: "#d9e2ec" } }, axisLabel: { color: "#94a3b8" } },
    yAxis: { type: "value", axisLine: { show: false }, axisTick: { show: false }, splitLine: { lineStyle: { color: "#e8eef5" } }, axisLabel: { color: "#94a3b8", formatter: (value: number) => compactMoney(value) } },
    series: [
      { name: "需求金额", type: "line", data: requestValues, smooth: true, symbolSize: 6, lineStyle: { width: 3 }, areaStyle: { opacity: 0.08 } },
      { name: "中标金额", type: "line", data: awardValues, smooth: true, symbolSize: 6, lineStyle: { width: 3 } },
      { name: "节省金额", type: "bar", data: savingValues, barWidth: 18, itemStyle: { borderRadius: [4, 4, 0, 0] } }
    ]
  };
  trendChart.setOption(option);
}

function resizeTrendChart(): void {
  trendChart?.resize();
}

function exportDashboard(): void {
  const lines = [
    ["采购经营分析", rangeLabel.value],
    ["业务区域", filters.businessRegion || "全部"],
    ["机房群", filters.roomGroup || "全部"],
    ["需求金额", summary.requestAmount],
    ["中标金额", summary.awardAmount],
    ["节省金额", summary.savingAmount],
    ["节省率", formatRate(summary.savingRate)],
    [],
    ["供应商", "需求金额", "中标金额", "节省金额", "节省率", "降本贡献"],
    ...supplierRows.value.map((row) => [row.name, row.requestAmount, row.awardAmount, row.savingAmount, formatRate(row.savingRate), `${row.contribution.toFixed(1)}%`])
  ];
  const csv = `\uFEFF${lines.map((line) => line.map(csvCell).join(",")).join("\r\n")}`;
  const url = URL.createObjectURL(new Blob([csv], { type: "text/csv;charset=utf-8" }));
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = `采购经营分析-${filters.dateRange.join("-")}.csv`;
  anchor.click();
  URL.revokeObjectURL(url);
}

function csvCell(value: unknown): string {
  const text = String(value ?? "");
  return /[",\r\n]/.test(text) ? `"${text.replace(/"/g, "\"\"")}"` : text;
}

function compareItem(
  label: string,
  value: string,
  current: number,
  year: number,
  previous: number,
  mode: "percent" | "point" = "percent"
): CompareItem {
  const yearValue = mode === "point" ? (current - year) * 100 : changeValue(current, year);
  const previousValue = mode === "point" ? (current - previous) * 100 : changeValue(current, previous);
  return {
    label,
    value,
    yearText: mode === "point" ? formatPointChange(yearValue) : formatChangeValue(yearValue),
    previousText: mode === "point" ? formatPointChange(previousValue) : formatChangeValue(previousValue),
    yearValue,
    previousValue
  };
}

function compareText(current: number, previous: number): string {
  return formatChangeValue(changeValue(current, previous));
}

function changeValue(current: number, previous: number): number | null {
  if (!previous) return null;
  return (current - previous) / Math.abs(previous) * 100;
}

function formatChangeValue(value: number | null): string {
  if (value == null || !Number.isFinite(value)) return "—";
  return `${value >= 0 ? "+" : ""}${value.toFixed(1)}%`;
}

function formatPointChange(value: number | null): string {
  if (value == null || !Number.isFinite(value)) return "—";
  return `${value >= 0 ? "+" : ""}${value.toFixed(1)}pp`;
}

function changeClass(value: number | null): string {
  if (value == null || value === 0) return "is-neutral";
  return value > 0 ? "is-positive" : "is-negative";
}

function emptySummary(): ExecutiveSummary {
  return {
    requestAmount: 0,
    awardAmount: 0,
    savingAmount: 0,
    savingRate: 0,
    qty: 0,
    supplierCount: 0,
    requestCount: 0,
    unitCost: 0
  };
}

function valueOf(row: Record<string, unknown> | undefined, key: string): number {
  const value = Number(row?.[key] ?? 0);
  return Number.isFinite(value) ? value : 0;
}

function uniqueText(rows: Record<string, unknown>[], key: string): string[] {
  return [...new Set(rows.map((row) => String(row[key] || "").trim()).filter(Boolean))].sort((left, right) => left.localeCompare(right, "zh-CN"));
}

function normalizeRange(range: string[]): string[] {
  return range.length === 2 ? [range[0], range[1]] : [];
}

function shiftYearRange(range: string[]): string[] {
  return range.map((value) => {
    const date = parseDate(value);
    date.setFullYear(date.getFullYear() - 1);
    return formatDate(date);
  });
}

function previousPeriodRange(range: string[]): string[] {
  const start = parseDate(range[0]);
  const end = parseDate(range[1]);
  const durationDays = Math.max(1, Math.round((end.getTime() - start.getTime()) / 86400000) + 1);
  const previousEnd = new Date(start);
  previousEnd.setDate(previousEnd.getDate() - 1);
  const previousStart = new Date(previousEnd);
  previousStart.setDate(previousStart.getDate() - durationDays + 1);
  return [formatDate(previousStart), formatDate(previousEnd)];
}

function parseDate(value: string): Date {
  return new Date(`${value}T00:00:00`);
}

function formatDate(value: Date): string {
  const year = value.getFullYear();
  const month = String(value.getMonth() + 1).padStart(2, "0");
  const day = String(value.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function formatMoney(value: number): string {
  return new Intl.NumberFormat("zh-CN", {
    style: "currency",
    currency: "CNY",
    maximumFractionDigits: 0
  }).format(Number(value || 0));
}

function compactMoney(value: number): string {
  if (Math.abs(value) >= 10000) return `${(value / 10000).toFixed(value % 10000 === 0 ? 0 : 1)}万`;
  return String(Math.round(value));
}

function formatNumber(value: number): string {
  return new Intl.NumberFormat("zh-CN", { maximumFractionDigits: 0 }).format(Number(value || 0));
}

function formatRate(value: number): string {
  return `${(Number(value || 0) * 100).toFixed(1)}%`;
}
</script>

<style scoped>
.executive-dashboard {
  --board-text: #1f2937;
  --board-muted: #64748b;
  --board-soft: #94a3b8;
  --board-line: #d9e2ec;
  --board-line-soft: #e8eef5;
  --board-primary: #165dff;
  --board-primary-soft: #e8f0ff;
  --board-success: #107f4b;
  --board-success-soft: #e7f6ee;
  --board-warning: #b76a00;
  --board-warning-soft: #fff3df;
  --board-danger: #c9341b;
  display: grid;
  gap: 16px;
  color: var(--board-text);
}

.filter-card,
.data-panel,
.metric-card,
.saving-card {
  border: 1px solid var(--board-line-soft);
  border-radius: 10px;
  background: #fff;
  box-shadow: 0 8px 22px rgba(15, 23, 42, 0.055);
}

.filter-card {
  min-height: 64px;
  padding: 10px 16px 10px 20px;
  display: flex;
  align-items: center;
  gap: 20px;
}

.filter-card h3,
.data-panel h3 {
  margin: 0;
  font-size: 16px;
  line-height: 24px;
}

.filters {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 8px;
}

.filters :deep(.el-date-editor) {
  width: 244px;
}

.filters :deep(.el-select) {
  width: 148px;
}

.metric-grid {
  display: grid;
  grid-template-columns: 1.25fr repeat(3, 1fr);
  gap: 16px;
}

.saving-card,
.metric-card {
  min-height: 132px;
  padding: 15px 18px;
}

.saving-card {
  position: relative;
  overflow: hidden;
  border-color: var(--board-primary);
  color: #fff;
  background: radial-gradient(circle at 90% 10%, rgba(255, 255, 255, 0.2), transparent 34%),
    linear-gradient(135deg, #165dff 0%, #0f46c7 100%);
}

.saving-card::after {
  content: "";
  position: absolute;
  right: -46px;
  bottom: -82px;
  width: 146px;
  height: 146px;
  border: 24px solid rgba(255, 255, 255, 0.08);
  border-radius: 50%;
}

.saving-card > span,
.metric-card > span {
  color: inherit;
  font-size: 12px;
  font-weight: 600;
}

.saving-card > strong {
  display: block;
  margin-top: 4px;
  font-size: 34px;
  line-height: 44px;
}

.saving-card > div {
  margin-top: 8px;
  display: flex;
  gap: 10px;
}

.saving-card em {
  padding: 3px 8px;
  border-radius: 11px;
  background: rgba(255, 255, 255, 0.16);
  font-size: 10px;
  font-style: normal;
  font-weight: 600;
}

.metric-card {
  position: relative;
}

.metric-card::after {
  content: "";
  position: absolute;
  right: 17px;
  top: 18px;
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: var(--tone);
  box-shadow: 0 0 0 5px color-mix(in srgb, var(--tone) 12%, transparent);
}

.metric-card--blue { --tone: var(--board-primary); }
.metric-card--green { --tone: var(--board-success); }
.metric-card--orange { --tone: var(--board-warning); }

.metric-card > span {
  color: var(--board-muted);
}

.metric-card > strong {
  display: block;
  margin-top: 11px;
  font-size: 28px;
  line-height: 36px;
}

.metric-card > div {
  margin-top: 8px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.metric-card em {
  padding: 3px 8px;
  border-radius: 11px;
  color: var(--board-success);
  background: var(--board-success-soft);
  font-size: 10px;
  font-style: normal;
  font-weight: 600;
}

.dashboard-row {
  display: grid;
  grid-template-columns: minmax(0, 1.9fr) minmax(360px, 1fr);
  gap: 16px;
}

.data-panel {
  padding: 14px 18px;
  overflow: hidden;
}

.data-panel > header {
  min-height: 42px;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.data-panel > header > span {
  color: var(--board-soft);
  font-size: 10px;
}

.trend-panel,
.compare-panel {
  min-height: 268px;
}

.trend-chart {
  width: 100%;
  height: 204px;
}

.compare-grid {
  margin-top: 14px;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.compare-cell {
  min-height: 84px;
  padding: 10px 11px;
  border: 1px solid var(--board-line-soft);
  border-radius: 8px;
  background: #f8fafc;
}

.compare-cell > span {
  color: var(--board-muted);
  font-size: 10px;
  font-weight: 600;
}

.compare-cell > strong {
  display: block;
  margin-top: 4px;
  font-size: 20px;
}

.compare-cell > div {
  margin-top: 4px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.compare-cell em {
  font-size: 9px;
  font-style: normal;
}

.is-positive { color: var(--board-success); }
.is-negative { color: var(--board-danger); }
.is-neutral { color: var(--board-primary); }

.region-panel,
.room-panel {
  min-height: 244px;
}

.warning-chip {
  padding: 4px 9px;
  border-radius: 11px;
  color: var(--board-warning) !important;
  background: var(--board-warning-soft);
  font-weight: 600;
}

.region-list,
.room-list {
  margin-top: 12px;
}

.region-row {
  min-height: 37px;
  display: grid;
  grid-template-columns: 120px minmax(120px, 1fr) 100px 58px;
  gap: 12px;
  align-items: center;
  font-size: 11px;
}

.region-name strong,
.region-name span {
  display: block;
}

.region-name span {
  margin-top: 2px;
  color: var(--board-soft);
  font-size: 9px;
}

.progress-track {
  height: 8px;
  overflow: hidden;
  border-radius: 4px;
  background: var(--board-line-soft);
}

.progress-track i {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, var(--board-primary), #5b8cff);
}

.progress-track i.is-warning {
  background: var(--board-warning);
}

.region-row > strong,
.region-row > span {
  text-align: right;
}

.region-row > span {
  color: var(--board-success);
}

.room-row {
  min-height: 43px;
  display: grid;
  grid-template-columns: 26px minmax(110px, 1fr) minmax(150px, auto);
  gap: 9px;
  align-items: center;
  border-top: 1px solid var(--board-line-soft);
}

.room-row:first-child {
  border-top: 0;
}

.rank {
  width: 22px;
  height: 22px;
  display: grid;
  place-items: center;
  border-radius: 6px;
  color: var(--board-primary);
  background: var(--board-primary-soft);
  font-size: 9px;
  font-weight: 700;
}

.room-row > div strong,
.room-row > div span {
  display: block;
}

.room-row > div span {
  margin-top: 2px;
  color: var(--board-soft);
  font-size: 9px;
}

.room-row > div:last-child {
  text-align: right;
}

.room-row > div:last-child strong {
  color: var(--board-success);
  font-size: 11px;
}

.room-row.is-over .rank {
  color: var(--board-danger);
  background: #fdebea;
}

.room-row.is-over > div:last-child strong {
  color: var(--board-danger);
}

.supplier-panel {
  min-height: 274px;
}

.saving-positive {
  color: var(--board-success);
  font-weight: 600;
}

.saving-negative {
  color: var(--board-danger);
  font-weight: 600;
}

.contribution-cell {
  position: relative;
  display: flex;
  align-items: center;
  gap: 8px;
}

.contribution-cell::before {
  content: "";
  width: 86px;
  height: 6px;
  border-radius: 3px;
  background: var(--board-line-soft);
}

.contribution-cell i {
  position: absolute;
  width: 0;
  max-width: 86px;
  height: 6px;
  border-radius: 3px;
  background: var(--board-primary);
}

.contribution-cell span {
  margin-left: 2px;
  color: var(--board-primary);
  font-size: 10px;
  font-weight: 600;
}

@media (max-width: 1260px) {
  .filter-card {
    align-items: flex-start;
    flex-direction: column;
  }

  .filters {
    margin-left: 0;
    width: 100%;
    flex-wrap: wrap;
  }

  .metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .dashboard-row {
    grid-template-columns: 1fr;
  }
}
</style>
