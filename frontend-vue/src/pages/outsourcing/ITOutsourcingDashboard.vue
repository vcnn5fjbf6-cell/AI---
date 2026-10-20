<template>
  <main ref="dashboardRoot" class="it-dashboard">
    <header class="dashboard-toolbar">
      <div class="dashboard-title">
        <span>工程外包管理</span>
        <h2>IT工程外包看板</h2>
        <p>工单、履约质量与费用执行概览</p>
      </div>
      <div class="dashboard-filters">
        <div class="filter-field">
          <label>统计周期</label>
          <el-radio-group v-model="quickRange" size="small">
            <el-radio-button value="6m">近6个月</el-radio-button>
            <el-radio-button value="12m">近12个月</el-radio-button>
            <el-radio-button value="year">本年度</el-radio-button>
            <el-radio-button value="all">全部</el-radio-button>
          </el-radio-group>
        </div>
        <div class="filter-field filter-field--range">
          <label>时间区间</label>
          <el-date-picker
            v-model="dateRange"
            type="monthrange"
            range-separator="至"
            start-placeholder="开始月份"
            end-placeholder="结束月份"
            value-format="YYYY-MM"
            :clearable="false"
            @change="handleRangeChange"
          />
        </div>
        <div class="filter-field">
          <label>统计粒度</label>
          <el-radio-group v-model="granularity" size="small">
            <el-radio-button value="month">按月</el-radio-button>
            <el-radio-button value="quarter">按季度</el-radio-button>
          </el-radio-group>
        </div>
      </div>
    </header>

    <section class="dashboard-kpis">
      <article v-for="item in kpis" :key="item.label" :class="`kpi-item kpi-item--${item.tone}`">
        <span>{{ item.label }}</span>
        <strong>{{ item.value }}</strong>
        <small>{{ item.note }}</small>
      </article>
    </section>

    <section class="chart-grid">
      <article class="chart-panel chart-panel--wide">
        <header><div><h3>工单与费用趋势</h3><p>{{ rangeDescription }}</p></div><span>工单 / 万元</span></header>
        <div ref="trendChartRef" class="chart-host chart-host--large" />
      </article>

      <article class="chart-panel chart-panel--side">
        <header><div><h3>机房工单分布</h3><p>按工单归属机房统计</p></div></header>
        <div ref="roomOrderChartRef" class="chart-host chart-host--large" />
      </article>

      <article class="chart-panel chart-panel--full">
        <header><div><h3>重点机房费用趋势</h3><p>机房费用随统计周期变化</p></div><span>万元</span></header>
        <div ref="roomCostTrendChartRef" class="chart-host" />
      </article>

      <article class="chart-panel chart-panel--half">
        <header><div><h3>服务商费用占比</h3><p>已确认及待结算费用</p></div></header>
        <div ref="providerCostChartRef" class="chart-host" />
      </article>

      <article class="chart-panel chart-panel--half">
        <header><div><h3>工作类型占比</h3><p>按工单服务类型统计</p></div></header>
        <div ref="workTypeChartRef" class="chart-host" />
      </article>

      <article class="chart-panel chart-panel--half">
        <header><div><h3>机房操作环境占比</h3><p>生产环境与测试环境工单</p></div></header>
        <div ref="environmentChartRef" class="chart-host" />
      </article>

      <article class="chart-panel chart-panel--half">
        <header><div><h3>机房布线工作量</h3><p>实际综合布线长度</p></div><span>米</span></header>
        <div ref="cablingChartRef" class="chart-host" />
      </article>

      <article class="chart-panel chart-panel--full">
        <header><div><h3>机房设备操作量</h3><p>实际安装、下架、迁移与变更设备数</p></div><span>台</span></header>
        <div ref="deviceOpsChartRef" class="chart-host" />
      </article>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { BarChart, LineChart, PieChart } from "echarts/charts";
import { GraphicComponent, GridComponent, LegendComponent, TooltipComponent } from "echarts/components";
import { init, use, type ECharts, type EChartsCoreOption } from "echarts/core";
import { CanvasRenderer } from "echarts/renderers";

use([BarChart, LineChart, PieChart, GraphicComponent, GridComponent, LegendComponent, TooltipComponent, CanvasRenderer]);

type Granularity = "month" | "quarter";
type QuickRange = "6m" | "12m" | "year" | "all" | "custom";

interface OutsourcingRecord {
  month: string;
  room: string;
  provider: string;
  workType: string;
  environment: string;
  orders: number;
  cost: number;
  cablingMeters: number;
  deviceOps: number;
  passRate: number;
}

interface NamedValue {
  name: string;
  value: number;
}

const rooms = ["M6", "B28", "廊坊一", "云产线", "数据中心"];
const providers = ["神州邦邦", "亚康", "立康", "海创集团"];
const workTypes = ["综合布线", "设备操作", "人天服务", "巡检维护"];
const palette = ["#2f6bff", "#23a26d", "#e08a21", "#7762d8", "#d45353", "#1f9ba8", "#5d7299"];
const monthKeys = createMonthKeys(2024, 1, 2026, 8);
const records = createRecords();
const earliestMonth = monthKeys[0];
const latestMonth = monthKeys[monthKeys.length - 1];

const dashboardRoot = ref<HTMLElement | null>(null);
const trendChartRef = ref<HTMLElement | null>(null);
const roomOrderChartRef = ref<HTMLElement | null>(null);
const roomCostTrendChartRef = ref<HTMLElement | null>(null);
const providerCostChartRef = ref<HTMLElement | null>(null);
const workTypeChartRef = ref<HTMLElement | null>(null);
const environmentChartRef = ref<HTMLElement | null>(null);
const cablingChartRef = ref<HTMLElement | null>(null);
const deviceOpsChartRef = ref<HTMLElement | null>(null);
const charts = new Map<string, ECharts>();
let resizeObserver: ResizeObserver | null = null;

const quickRange = ref<QuickRange>("12m");
const granularity = ref<Granularity>("month");
const dateRange = ref<[string, string]>([shiftMonth(latestMonth, -11), latestMonth]);

const filteredRecords = computed(() => {
  const [start, end] = dateRange.value;
  return records.filter((item) => item.month >= start && item.month <= end);
});

const rangeDescription = computed(() => {
  const [start, end] = dateRange.value;
  return `${formatMonth(start)} 至 ${formatMonth(end)} · ${granularity.value === "month" ? "按月" : "按季度"}`;
});

const kpis = computed(() => {
  const orderCount = sum(filteredRecords.value, (item) => item.orders);
  const totalCost = sum(filteredRecords.value, (item) => item.cost);
  const weightedPassRate = orderCount === 0
    ? 0
    : sum(filteredRecords.value, (item) => item.passRate * item.orders) / orderCount;
  const averageCost = orderCount === 0 ? 0 : totalCost / orderCount;
  return [
    { label: "总工单数", value: `${formatNumber(orderCount)} 单`, note: rangeDescription.value, tone: "primary" },
    { label: "总费用", value: formatCurrency(totalCost), note: "外包费用累计", tone: "purple" },
    { label: "单均成本", value: formatCurrency(averageCost), note: "总费用 / 工单数", tone: "warning" },
    { label: "一次验收通过率", value: `${weightedPassRate.toFixed(1)}%`, note: "按工单量加权", tone: "success" }
  ];
});

watch(quickRange, (value) => {
  if (value === "custom") return;
  if (value === "6m") dateRange.value = [shiftMonth(latestMonth, -5), latestMonth];
  if (value === "12m") dateRange.value = [shiftMonth(latestMonth, -11), latestMonth];
  if (value === "year") dateRange.value = [`${latestMonth.slice(0, 4)}-01`, latestMonth];
  if (value === "all") dateRange.value = [earliestMonth, latestMonth];
});

watch([filteredRecords, granularity], () => {
  void nextTick(renderCharts);
}, { flush: "post" });

onMounted(() => {
  void nextTick(renderCharts);
  resizeObserver = new ResizeObserver(() => charts.forEach((chart) => chart.resize()));
  if (dashboardRoot.value) resizeObserver.observe(dashboardRoot.value);
});

onBeforeUnmount(() => {
  resizeObserver?.disconnect();
  charts.forEach((chart) => chart.dispose());
  charts.clear();
});

function handleRangeChange(value: [string, string] | null): void {
  if (!value || value.length !== 2) return;
  quickRange.value = "custom";
}

function renderCharts(): void {
  const trend = aggregatePeriods(filteredRecords.value);
  const roomOrders = aggregateBy(filteredRecords.value, (item) => item.room, (item) => item.orders);
  const providerCosts = aggregateBy(filteredRecords.value, (item) => item.provider, (item) => item.cost);
  const typeOrders = aggregateBy(filteredRecords.value, (item) => item.workType, (item) => item.orders);
  const environmentOrders = aggregateBy(filteredRecords.value, (item) => item.environment, (item) => item.orders);
  const cabling = aggregateBy(filteredRecords.value, (item) => item.room, (item) => item.cablingMeters);
  const deviceOps = aggregateBy(filteredRecords.value, (item) => item.room, (item) => item.deviceOps);
  const periodLabels = trend.map((item) => item.label);

  drawChart("trend", trendChartRef.value, {
    color: [palette[0], palette[2]],
    tooltip: { trigger: "axis" },
    legend: { top: 2, right: 8, data: ["工单数", "费用"] },
    grid: chartGrid(34, 54),
    xAxis: categoryAxis(periodLabels),
    yAxis: [valueAxis("工单数"), valueAxis("费用（万元）", true)],
    series: [
      { name: "工单数", type: "bar", barMaxWidth: 28, data: trend.map((item) => item.orders), itemStyle: { borderRadius: [3, 3, 0, 0] } },
      { name: "费用", type: "line", yAxisIndex: 1, smooth: true, symbolSize: 7, data: trend.map((item) => round(item.cost / 10000, 2)), lineStyle: { width: 3 }, areaStyle: { opacity: 0.08 } }
    ]
  });

  drawChart("room-orders", roomOrderChartRef.value, donutOption(roomOrders, "单", sum(roomOrders, (item) => item.value), "总工单"));

  const roomCostSeries = rooms.map((room, index) => ({
    name: room,
    type: "line",
    smooth: true,
    symbolSize: 6,
    data: periodLabels.map((label) => round(periodRoomValue(filteredRecords.value, label, room, (item) => item.cost) / 10000, 2)),
    lineStyle: { width: index < 3 ? 3 : 2 }
  }));
  drawChart("room-cost-trend", roomCostTrendChartRef.value, {
    color: palette,
    tooltip: { trigger: "axis" },
    legend: { top: 2, left: 8, data: rooms },
    grid: chartGrid(38, 54),
    xAxis: categoryAxis(periodLabels),
    yAxis: valueAxis("费用（万元）"),
    series: roomCostSeries
  });

  drawChart("provider-cost", providerCostChartRef.value, donutOption(providerCosts.map((item) => ({ ...item, value: round(item.value / 10000, 2) })), "万元", round(sum(providerCosts, (item) => item.value) / 10000, 1), "总费用"));
  drawChart("work-type", workTypeChartRef.value, donutOption(typeOrders, "单", sum(typeOrders, (item) => item.value), "总工单"));
  drawChart("environment", environmentChartRef.value, pieOption(environmentOrders));
  drawChart("cabling", cablingChartRef.value, barOption(cabling, "米", palette[5]));
  drawChart("device-ops", deviceOpsChartRef.value, barOption(deviceOps, "台", palette[3]));
}

function drawChart(key: string, host: HTMLElement | null, option: EChartsCoreOption): void {
  if (!host) return;
  let chart = charts.get(key);
  if (!chart) {
    chart = init(host);
    charts.set(key, chart);
  }
  chart.setOption(option, true);
}

function donutOption(data: NamedValue[], unit: string, total: number, totalLabel: string): EChartsCoreOption {
  return {
    color: palette,
    tooltip: { trigger: "item", valueFormatter: (value: unknown) => `${formatNumber(Number(value))} ${unit}` },
    legend: { bottom: 0, left: "center", type: "scroll" },
    graphic: [
      { type: "text", left: "center", top: "41%", style: { text: formatNumber(total), fill: "#142a52", font: "700 24px sans-serif", textAlign: "center" } },
      { type: "text", left: "center", top: "52%", style: { text: totalLabel, fill: "#7c8ca8", font: "12px sans-serif", textAlign: "center" } }
    ],
    series: [{
      type: "pie",
      radius: ["46%", "68%"],
      center: ["50%", "44%"],
      avoidLabelOverlap: true,
      itemStyle: { borderColor: "#fff", borderWidth: 2 },
      label: { color: "#51627f", formatter: `{b}\n{d}%`, fontSize: 11 },
      labelLine: { length: 10, length2: 8 },
      data
    }]
  };
}

function pieOption(data: NamedValue[]): EChartsCoreOption {
  return {
    color: [palette[0], palette[1]],
    tooltip: { trigger: "item", valueFormatter: (value: unknown) => `${formatNumber(Number(value))} 单` },
    legend: { bottom: 0, left: "center" },
    series: [{
      type: "pie",
      radius: ["16%", "68%"],
      center: ["50%", "44%"],
      itemStyle: { borderColor: "#fff", borderWidth: 2 },
      label: { formatter: "{b}\n{c} 单（{d}%）", color: "#51627f" },
      data
    }]
  };
}

function barOption(data: NamedValue[], unit: string, color: string): EChartsCoreOption {
  return {
    color: [color],
    tooltip: { trigger: "axis", axisPointer: { type: "shadow" }, valueFormatter: (value: unknown) => `${formatNumber(Number(value))} ${unit}` },
    grid: chartGrid(24, 54),
    xAxis: categoryAxis(data.map((item) => item.name)),
    yAxis: valueAxis(unit),
    series: [{
      type: "bar",
      barMaxWidth: 42,
      data: data.map((item) => item.value),
      itemStyle: { borderRadius: [4, 4, 0, 0] },
      label: { show: true, position: "top", color: "#60708d", formatter: `{c}` }
    }]
  };
}

function categoryAxis(data: string[]): Record<string, unknown> {
  return {
    type: "category",
    data,
    axisTick: { show: false },
    axisLine: { lineStyle: { color: "#cfd8e7" } },
    axisLabel: { color: "#70809d", rotate: data.length > 12 ? 36 : 0, margin: 12 }
  };
}

function valueAxis(name: string, opposite = false): Record<string, unknown> {
  return {
    type: "value",
    name,
    position: opposite ? "right" : "left",
    nameTextStyle: { color: "#8795ad", padding: [0, 0, 4, 0] },
    axisLabel: { color: "#70809d" },
    splitLine: { lineStyle: { color: "#edf1f7" } }
  };
}

function chartGrid(top: number, bottom: number): Record<string, unknown> {
  return { top, right: 36, bottom, left: 54, containLabel: false };
}

function aggregatePeriods(source: OutsourcingRecord[]): Array<{ key: string; label: string; orders: number; cost: number }> {
  const map = new Map<string, { key: string; label: string; orders: number; cost: number }>();
  source.forEach((item) => {
    const key = periodKey(item.month);
    const current = map.get(key) ?? { key, label: periodLabel(key), orders: 0, cost: 0 };
    current.orders += item.orders;
    current.cost += item.cost;
    map.set(key, current);
  });
  return [...map.values()].sort((left, right) => left.key.localeCompare(right.key));
}

function aggregateBy(
  source: OutsourcingRecord[],
  keySelector: (item: OutsourcingRecord) => string,
  valueSelector: (item: OutsourcingRecord) => number
): NamedValue[] {
  const map = new Map<string, number>();
  source.forEach((item) => map.set(keySelector(item), (map.get(keySelector(item)) ?? 0) + valueSelector(item)));
  return [...map.entries()].map(([name, value]) => ({ name, value: round(value, 2) })).sort((left, right) => right.value - left.value);
}

function periodRoomValue(
  source: OutsourcingRecord[],
  label: string,
  room: string,
  valueSelector: (item: OutsourcingRecord) => number
): number {
  return sum(source.filter((item) => periodLabel(periodKey(item.month)) === label && item.room === room), valueSelector);
}

function periodKey(month: string): string {
  if (granularity.value === "month") return month;
  const [year, monthText] = month.split("-");
  return `${year}-Q${Math.ceil(Number(monthText) / 3)}`;
}

function periodLabel(key: string): string {
  if (key.includes("-Q")) return key.replace("-Q", " Q");
  return `${Number(key.slice(5, 7))}月`;
}

function createRecords(): OutsourcingRecord[] {
  return monthKeys.flatMap((month, monthIndex) => rooms.map((room, roomIndex) => {
    const orders = 7 + ((monthIndex * 5 + roomIndex * 7) % 19);
    const activityFactor = 0.86 + ((monthIndex + roomIndex * 2) % 7) * 0.07;
    const roomFactor = [1.48, 1.24, 0.92, 1.68, 1.36][roomIndex];
    const providerIndex = (monthIndex + roomIndex * 2) % providers.length;
    return {
      month,
      room,
      provider: providers[providerIndex],
      workType: workTypes[(monthIndex * 2 + roomIndex) % workTypes.length],
      environment: (monthIndex + roomIndex) % 5 === 0 ? "测试环境" : "生产环境",
      orders,
      cost: Math.round(orders * (2950 + roomIndex * 420) * roomFactor * activityFactor),
      cablingMeters: Math.round(orders * (24 + roomIndex * 9) * activityFactor),
      deviceOps: Math.round(orders * (2.6 + roomIndex * 0.7) * activityFactor),
      passRate: 88 + ((monthIndex * 3 + roomIndex * 2) % 11)
    };
  }));
}

function createMonthKeys(startYear: number, startMonth: number, endYear: number, endMonth: number): string[] {
  const result: string[] = [];
  let year = startYear;
  let month = startMonth;
  while (year < endYear || (year === endYear && month <= endMonth)) {
    result.push(`${year}-${String(month).padStart(2, "0")}`);
    month += 1;
    if (month > 12) {
      month = 1;
      year += 1;
    }
  }
  return result;
}

function shiftMonth(monthKey: string, offset: number): string {
  const [year, month] = monthKey.split("-").map(Number);
  const date = new Date(year, month - 1 + offset, 1);
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}`;
}

function formatMonth(month: string): string {
  return `${month.slice(0, 4)}年${Number(month.slice(5, 7))}月`;
}

function formatCurrency(value: number): string {
  if (value >= 10000) return `¥${(value / 10000).toFixed(1)}万`;
  return `¥${formatNumber(Math.round(value))}`;
}

function formatNumber(value: number): string {
  return new Intl.NumberFormat("zh-CN", { maximumFractionDigits: 1 }).format(value);
}

function sum<T>(items: T[], selector: (item: T) => number): number {
  return items.reduce((total, item) => total + selector(item), 0);
}

function round(value: number, digits: number): number {
  const factor = 10 ** digits;
  return Math.round(value * factor) / factor;
}
</script>

<style scoped>
.it-dashboard {
  min-width: 0;
  color: #152b53;
}

.dashboard-toolbar {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 12px;
  padding: 18px 20px;
  border: 1px solid #dce5f2;
  border-left: 4px solid #2f6bff;
  border-radius: 6px;
  background: #fff;
}

.dashboard-title {
  min-width: 240px;
}

.dashboard-title span {
  color: #2f6bff;
  font-size: 12px;
  font-weight: 700;
}

.dashboard-title h2 {
  margin: 5px 0 0;
  color: #102a5c;
  font-size: 24px;
  line-height: 1.2;
  letter-spacing: 0;
}

.dashboard-title p,
.chart-panel header p {
  margin: 5px 0 0;
  color: #8090aa;
  font-size: 12px;
}

.dashboard-filters {
  display: flex;
  align-items: flex-end;
  justify-content: flex-end;
  gap: 12px;
  min-width: 0;
}

.filter-field {
  display: grid;
  gap: 6px;
}

.filter-field label {
  color: #667792;
  font-size: 12px;
  font-weight: 600;
}

.filter-field--range :deep(.el-date-editor) {
  width: 244px;
}

.dashboard-kpis {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 12px;
}

.kpi-item {
  position: relative;
  min-height: 92px;
  padding: 14px 16px;
  overflow: hidden;
  border: 1px solid #dfe6f1;
  border-radius: 6px;
  background: #fff;
}

.kpi-item::before {
  position: absolute;
  inset: 0 auto 0 0;
  width: 3px;
  background: #2f6bff;
  content: "";
}

.kpi-item span,
.kpi-item small {
  display: block;
}

.kpi-item span {
  color: #62728f;
  font-size: 13px;
  font-weight: 600;
}

.kpi-item strong {
  display: block;
  margin-top: 8px;
  color: #102a5c;
  font-size: 25px;
  line-height: 1;
}

.kpi-item small {
  overflow: hidden;
  margin-top: 8px;
  color: #8a98ae;
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.kpi-item--purple::before { background: #7762d8; }
.kpi-item--warning::before { background: #e08a21; }
.kpi-item--success::before { background: #23a26d; }

.chart-grid {
  display: grid;
  grid-template-columns: repeat(12, minmax(0, 1fr));
  gap: 12px;
}

.chart-panel {
  min-width: 0;
  padding: 14px 16px 10px;
  border: 1px solid #dfe6f1;
  border-radius: 6px;
  background: #fff;
}

.chart-panel--wide { grid-column: span 8; }
.chart-panel--side { grid-column: span 4; }
.chart-panel--half { grid-column: span 6; }
.chart-panel--full { grid-column: span 12; }

.chart-panel header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  min-height: 42px;
}

.chart-panel h3 {
  margin: 0;
  color: #1a315d;
  font-size: 15px;
  line-height: 1.35;
  letter-spacing: 0;
}

.chart-panel header > span {
  color: #91a0b5;
  font-size: 11px;
  white-space: nowrap;
}

.chart-host {
  width: 100%;
  height: 292px;
}

.chart-host--large {
  height: 320px;
}

@media (max-width: 1280px) {
  .dashboard-toolbar,
  .dashboard-filters {
    align-items: stretch;
    flex-direction: column;
  }

  .dashboard-filters {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .dashboard-kpis {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .chart-panel--wide,
  .chart-panel--side,
  .chart-panel--half {
    grid-column: span 6;
  }
}

@media (max-width: 860px) {
  .dashboard-toolbar {
    padding: 14px;
  }

  .dashboard-filters,
  .dashboard-kpis {
    grid-template-columns: 1fr;
  }

  .filter-field--range :deep(.el-date-editor),
  .filter-field :deep(.el-radio-group) {
    width: 100%;
  }

  .filter-field :deep(.el-radio-button) {
    flex: 1;
  }

  .filter-field :deep(.el-radio-button__inner) {
    width: 100%;
    padding-right: 8px;
    padding-left: 8px;
  }

  .chart-panel--wide,
  .chart-panel--side,
  .chart-panel--half,
  .chart-panel--full {
    grid-column: 1 / -1;
  }

  .chart-host,
  .chart-host--large {
    height: 280px;
  }
}
</style>
