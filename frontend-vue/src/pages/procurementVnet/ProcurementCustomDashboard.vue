<template>
  <VnetPage title="自定义采购分析">
    <template #actions>
      <el-button type="primary" plain @click="emit('back')">返回经营分析</el-button>
      <el-button :icon="Refresh" :loading="loading" @click="reloadAll">刷新</el-button>
      <el-button :icon="Operation" :type="editing ? 'primary' : 'default'" @click="toggleEditing">
        {{ editing ? "完成调整" : "调整看板" }}
      </el-button>
      <el-dropdown trigger="click" @command="addWidget">
        <el-button :type="editing ? 'primary' : 'default'">
          添加组件<el-icon class="el-icon--right"><ArrowDown /></el-icon>
        </el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="metric">指标卡</el-dropdown-item>
            <el-dropdown-item command="table">组合表</el-dropdown-item>
            <el-dropdown-item command="detail">明细表</el-dropdown-item>
            <el-dropdown-item command="bar">柱状图</el-dropdown-item>
            <el-dropdown-item command="pie">饼图</el-dropdown-item>
            <el-dropdown-item command="donut">环形图</el-dropdown-item>
            <el-dropdown-item command="line">折线图</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
      <el-button v-if="editing" @click="resetLayout">重置</el-button>
      <el-button v-if="editing" type="primary" @click="saveLayout">保存</el-button>
      <el-button type="primary" @click="$router.push('/procurement/requests/create')">新建采购申请</el-button>
      <el-button @click="$router.push('/procurement/approval')">处理审批</el-button>
    </template>

    <div v-loading="loading" class="bi-shell" :class="{ 'bi-shell--editing': editing }">
      <section class="bi-board">
        <div ref="gridRef" class="grid-stack bi-grid">
          <div
            v-for="widget in visibleWidgets"
            :key="widget.id"
            class="grid-stack-item"
            :gs-id="widget.id"
            :gs-x="widget.x"
            :gs-y="widget.y"
            :gs-w="widget.w"
            :gs-h="widget.h"
          >
            <div
              class="grid-stack-item-content bi-widget"
              :class="[
                `bi-widget--${widget.type}`,
                `bi-widget--theme-${widget.theme || 'default'}`,
                { 'bi-widget--selected': editing && selectedWidgetId === widget.id }
              ]"
              @click.stop="selectWidget(widget.id)"
            >
              <button v-if="editing" class="widget-handle" title="拖动组件">
                <span />
              </button>

              <div v-if="editing && selectedWidgetId === widget.id" class="select-frame">
                <i class="select-dot select-dot--tl" />
                <i class="select-dot select-dot--tr" />
                <i class="select-dot select-dot--bl" />
                <i class="select-dot select-dot--br" />
              </div>

              <header class="widget-head">
                <div>
                  <h3>{{ widget.title || "未命名组件" }}</h3>
                  <span>{{ widgetSubtitle(widget) }}</span>
                </div>
                <div v-if="editing && selectedWidgetId === widget.id" class="widget-actions">
                  <el-button text :icon="CopyDocument" title="复制" @click.stop="duplicateWidget(widget)" />
                  <el-button text :icon="Close" title="隐藏" @click.stop="hideWidget(widget.id)" />
                </div>
              </header>

              <main class="widget-body">
                <el-skeleton v-if="widgetState(widget).loading" animated :rows="widget.type === 'metric' ? 2 : 5" />
                <el-empty
                  v-else-if="widgetState(widget).error"
                  :description="widgetState(widget).error"
                  :image-size="72"
                />

                <template v-else-if="widget.type === 'metric'">
                  <div class="metric-value">{{ metricDisplay(widget).value }}</div>
                  <div class="metric-note">{{ metricDisplay(widget).label }}</div>
                </template>

                <el-table
                  v-else-if="widget.type === 'table' || widget.type === 'detail'"
                  :data="widgetState(widget).rows"
                  class="widget-table"
                  stripe
                  height="100%"
                  size="small"
                >
                  <el-table-column
                    v-for="column in widgetState(widget).columns"
                    :key="column.code"
                    :prop="column.code"
                    :label="column.label"
                    min-width="128"
                    show-overflow-tooltip
                    :align="column.role === 'MEASURE' ? 'right' : 'left'"
                  >
                    <template #default="{ row }">{{ formatCell(row[column.code], column) }}</template>
                  </el-table-column>
                </el-table>

                <div
                  v-else
                  :ref="(el) => setChartRef(widget.id, el)"
                  class="chart-host"
                />
              </main>
            </div>
          </div>
        </div>
      </section>

      <aside v-if="editing" class="bi-config">
        <template v-if="selectedWidget">
          <div class="config-head">
            <strong>{{ selectedWidget.title || "未命名组件" }}</strong>
            <el-tag size="small" effect="plain">{{ widgetTypeName(selectedWidget.type) }}</el-tag>
          </div>

          <el-tabs v-model="activeConfigTab" class="config-tabs">
            <el-tab-pane label="基础配置" name="base">
              <div class="config-section">
                <label>
                  <span>标题</span>
                  <el-input v-model="selectedWidget.title" @change="markDirty" />
                </label>

                <label>
                  <span>组件类型</span>
                  <el-select v-model="selectedWidget.type" @change="handleWidgetTypeChange">
                    <el-option v-for="type in widgetTypeOptions" :key="type.value" :label="type.label" :value="type.value" />
                  </el-select>
                </label>

                <label>
                  <span>数据源</span>
                  <el-select v-model="selectedWidget.datasetCode" @change="handleDatasetChange">
                    <el-option
                      v-for="dataset in datasets"
                      :key="dataset.datasetCode"
                      :label="dataset.datasetName"
                      :value="dataset.datasetCode"
                    />
                  </el-select>
                </label>

                <label>
                  <span>时间范围</span>
                  <el-date-picker
                    v-model="selectedWidget.timeRange"
                    class="config-date-range"
                    type="daterange"
                    unlink-panels
                    clearable
                    value-format="YYYY-MM-DD"
                    range-separator="至"
                    start-placeholder="开始日期"
                    end-placeholder="结束日期"
                    @change="handleWidgetConfigChange"
                  />
                </label>

                <label v-if="selectedWidget.type === 'detail'">
                  <span>显示列</span>
                  <el-select
                    :key="`fields-${selectedWidget.id}-${selectedWidget.datasetCode}`"
                    v-model="selectedWidget.fields"
                    multiple
                    filterable
                    collapse-tags
                    collapse-tags-tooltip
                    placeholder="选择要展示的列"
                    @change="handleWidgetConfigChange"
                  >
                    <el-option
                      v-for="field in activeFields"
                      :key="field.fieldCode"
                      :label="field.fieldName"
                      :value="field.fieldCode"
                    />
                  </el-select>
                </label>

                <label v-if="selectedWidget.type !== 'metric' && selectedWidget.type !== 'detail'">
                  <span>按什么分组</span>
                  <el-select
                    :key="`dimensions-${selectedWidget.id}-${selectedWidget.datasetCode}`"
                    v-model="selectedWidget.dimensions"
                    multiple
                    filterable
                    collapse-tags
                    collapse-tags-tooltip
                    placeholder="选择分类字段"
                    @change="handleWidgetConfigChange"
                  >
                    <el-option
                      v-for="field in activeDimensionFields"
                      :key="field.fieldCode"
                      :label="field.fieldName"
                      :value="field.fieldCode"
                    />
                  </el-select>
                </label>

                <label v-if="selectedWidget.type !== 'detail'">
                  <span>统计什么数值</span>
                  <el-select
                    :key="`metrics-${selectedWidget.id}-${selectedWidget.datasetCode}`"
                    :model-value="selectedMetricCodes"
                    multiple
                    filterable
                    collapse-tags
                    collapse-tags-tooltip
                    placeholder="选择统计数值"
                    @change="handleMetricSelectionChange"
                  >
                    <el-option
                      v-for="field in activeMeasureFields"
                      :key="field.fieldCode"
                      :label="field.fieldName"
                      :value="field.fieldCode"
                    />
                  </el-select>
                </label>

                <label>
                  <span>展示前 N 条</span>
                  <el-input-number v-model="selectedWidget.limit" :min="1" :max="1000" controls-position="right" @change="handleWidgetConfigChange" />
                </label>
              </div>
            </el-tab-pane>

            <el-tab-pane label="自定义配置" name="style">
              <div class="config-section">
                <label>
                  <span>主题色</span>
                  <el-select v-model="selectedWidget.theme" @change="handleWidgetConfigChange">
                    <el-option label="默认" value="default" />
                    <el-option label="商务蓝" value="blue" />
                    <el-option label="运营绿" value="green" />
                    <el-option label="告警橙" value="orange" />
                    <el-option label="中性灰" value="slate" />
                  </el-select>
                </label>

                <div v-if="isChart(selectedWidget)" class="config-checks">
                  <el-checkbox v-model="selectedWidget.showLegend" @change="handleWidgetConfigChange">图例</el-checkbox>
                  <el-checkbox v-model="selectedWidget.showLabel" @change="handleWidgetConfigChange">数据标签</el-checkbox>
                </div>
              </div>
            </el-tab-pane>

            <el-tab-pane label="汇总算法" name="metrics">
              <div class="metric-config-list">
                <label v-for="metric in selectedWidget.metrics || []" :key="metric.fieldCode">
                  <span>{{ fieldName(selectedWidget.datasetCode, metric.fieldCode) }}</span>
                  <el-select v-model="metric.aggregation" @change="handleWidgetConfigChange">
                    <el-option label="求和" value="SUM" />
                    <el-option label="平均" value="AVG" />
                    <el-option label="最大" value="MAX" />
                    <el-option label="最小" value="MIN" />
                    <el-option label="计数" value="COUNT" />
                    <el-option label="去重数" value="COUNT_DISTINCT" />
                  </el-select>
                </label>
                <el-empty v-if="!selectedWidget.metrics?.length" description="暂无统计数值" :image-size="80" />
              </div>
            </el-tab-pane>
          </el-tabs>
        </template>
        <el-empty v-else description="请选择组件" :image-size="96" />
      </aside>
    </div>

    <el-dialog
      v-model="drillDetail.visible"
      class="drill-dialog"
      width="86vw"
      top="6vh"
      destroy-on-close
    >
      <template #header>
        <div class="drill-head">
          <strong>{{ drillDetail.title }}</strong>
          <span>{{ drillDetail.subtitle }}</span>
        </div>
      </template>
      <div class="drill-tags" v-if="drillDetail.filters.length">
        <el-tag v-for="item in drillDetail.filters" :key="item" effect="plain">{{ item }}</el-tag>
      </div>
      <div class="drill-toolbar">
        <el-input
          v-model="drillDetail.keyword"
          class="drill-search"
          :prefix-icon="Search"
          clearable
          placeholder="筛选当前明细"
        />
        <el-select
          v-model="drillDetail.visibleColumnCodes"
          class="drill-column-select"
          multiple
          filterable
          collapse-tags
          collapse-tags-tooltip
          placeholder="选择显示列"
        >
          <el-option
            v-for="column in drillDetail.columns"
            :key="column.code"
            :label="column.label"
            :value="column.code"
          />
        </el-select>
      </div>
      <el-table
        v-loading="drillDetail.loading"
        :data="drillFilteredRows"
        stripe
        border
        height="62vh"
        size="small"
      >
        <el-table-column
          v-for="column in drillVisibleColumns"
          :key="column.code"
          :prop="column.code"
          :label="column.label"
          min-width="136"
          show-overflow-tooltip
          :align="column.role === 'MEASURE' ? 'right' : 'left'"
        >
          <template #default="{ row }">{{ formatCell(row[column.code], column) }}</template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </VnetPage>
</template>

<script setup lang="ts">
import { ArrowDown, Close, CopyDocument, Operation, Refresh, Search } from "@element-plus/icons-vue";
import { ElMessage } from "element-plus";
import { BarChart, LineChart, PieChart } from "echarts/charts";
import { GridComponent, LegendComponent, TooltipComponent } from "echarts/components";
import { init, use, type ECharts, type EChartsCoreOption } from "echarts/core";
import { CanvasRenderer } from "echarts/renderers";
import { GridStack } from "gridstack";
import "gridstack/dist/gridstack.min.css";
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch, type ComponentPublicInstance } from "vue";
import VnetPage from "../../components/vnet/VnetPage.vue";
import {
  getProcurementBiDashboardApi,
  getProcurementBiDatasetsApi,
  queryProcurementBiApi,
  saveProcurementBiDashboardApi,
  type BiWidgetType,
  type ProcurementBiColumnVO,
  type ProcurementBiDatasetVO,
  type ProcurementBiFieldVO,
  type ProcurementBiFilterDTO,
  type ProcurementBiLayout,
  type ProcurementBiMetricDTO,
  type ProcurementBiQueryDTO,
  type ProcurementBiQueryResultVO,
  type ProcurementBiWidgetConfig
} from "../../api/procurement-bi";

use([BarChart, LineChart, PieChart, GridComponent, LegendComponent, TooltipComponent, CanvasRenderer]);

const emit = defineEmits<{
  back: [];
}>();

interface WidgetState {
  loading: boolean;
  error: string;
  columns: ProcurementBiColumnVO[];
  rows: Record<string, unknown>[];
}

interface DrillDetailState {
  visible: boolean;
  title: string;
  subtitle: string;
  filters: string[];
  keyword: string;
  visibleColumnCodes: string[];
  loading: boolean;
  columns: ProcurementBiColumnVO[];
  rows: Record<string, unknown>[];
}

const widgetTypeOptions: Array<{ label: string; value: BiWidgetType }> = [
  { label: "指标卡", value: "metric" },
  { label: "组合表", value: "table" },
  { label: "明细表", value: "detail" },
  { label: "柱状图", value: "bar" },
  { label: "饼图", value: "pie" },
  { label: "环形图", value: "donut" },
  { label: "折线图", value: "line" }
];

const localLayoutKey = "procurement-bi-dashboard-layout";
const gridRef = ref<HTMLElement | null>(null);
const loading = ref(false);
const editing = ref(false);
const activeConfigTab = ref("base");
const selectedWidgetId = ref<string>("");
const datasets = ref<ProcurementBiDatasetVO[]>([]);
const widgets = ref<ProcurementBiWidgetConfig[]>([]);
const states = reactive<Record<string, WidgetState>>({});
const drillDetail = reactive<DrillDetailState>({
  visible: false,
  title: "明细数据",
  subtitle: "",
  filters: [],
  keyword: "",
  visibleColumnCodes: [],
  loading: false,
  columns: [],
  rows: []
});
const chartRefs = new Map<string, HTMLElement>();
const chartInstances = new Map<string, ECharts>();
let grid: GridStack | null = null;

const visibleWidgets = computed(() => widgets.value.filter((widget) => widget.visible !== false));
const selectedWidget = computed(() => widgets.value.find((widget) => widget.id === selectedWidgetId.value));
const activeDataset = computed(() => selectedWidget.value ? datasetByCode(selectedWidget.value.datasetCode) : undefined);
const activeFields = computed(() => activeDataset.value?.fields || []);
const activeDimensionFields = computed(() => activeFields.value.filter((field) => field.role === "DIMENSION" && field.groupable));
const activeMeasureFields = computed(() => activeFields.value.filter((field) => field.role === "MEASURE"));
const selectedMetricCodes = computed(() => selectedWidget.value?.metrics?.map((metric) => metric.fieldCode) || []);
const drillVisibleColumns = computed(() => {
  const selected = new Set(drillDetail.visibleColumnCodes);
  return selected.size ? drillDetail.columns.filter((column) => selected.has(column.code)) : drillDetail.columns;
});
const drillFilteredRows = computed(() => {
  const keyword = drillDetail.keyword.trim().toLowerCase();
  if (!keyword) {
    return drillDetail.rows;
  }
  return drillDetail.rows.filter((row) => drillDetail.columns.some((column) => String(row[column.code] ?? "").toLowerCase().includes(keyword)));
});

onMounted(async () => {
  await loadDashboard();
  window.addEventListener("resize", resizeCharts);
});

onBeforeUnmount(() => {
  window.removeEventListener("resize", resizeCharts);
  destroyGrid();
  disposeAllCharts();
});

watch(editing, async () => {
  await nextTick();
  if (grid) {
    grid.setStatic(!editing.value);
  }
  resizeCharts();
});

async function loadDashboard(): Promise<void> {
  loading.value = true;
  try {
    const [datasetList, dashboard] = await Promise.all([
      getProcurementBiDatasetsApi(),
      getProcurementBiDashboardApi()
    ]);
    datasets.value = datasetList;
    const localLayout = readLocalLayout();
    widgets.value = normalizeWidgets(localLayout?.widgets?.length ? localLayout.widgets : dashboard.layout.widgets || []);
    selectedWidgetId.value = widgets.value[0]?.id || "";
    await nextTick();
    initGrid();
    await reloadAll();
  } catch {
    datasets.value = fallbackDatasets();
    const localLayout = readLocalLayout();
    widgets.value = normalizeWidgets(localLayout?.widgets?.length ? localLayout.widgets : fallbackLayout().widgets || []);
    selectedWidgetId.value = widgets.value[0]?.id || "";
    await nextTick();
    initGrid();
    await reloadAll();
  } finally {
    loading.value = false;
  }
}

async function reloadAll(): Promise<void> {
  await Promise.allSettled(visibleWidgets.value.map((widget) => loadWidget(widget)));
  await nextTick();
  renderCharts();
}

async function loadWidget(widget: ProcurementBiWidgetConfig): Promise<void> {
  const state = widgetState(widget);
  if (!widget.datasetCode) {
    state.error = "未选择数据源";
    return;
  }
  state.loading = true;
  state.error = "";
  try {
    const result = await queryProcurementBiApi(toQuery(widget));
    state.columns = result.columns || [];
    state.rows = result.rows || [];
    await nextTick();
    renderWidgetChart(widget, result);
  } catch (error) {
    if (import.meta.env.DEV) {
      const fallback = sampleQueryResult(widget);
      state.columns = fallback.columns;
      state.rows = fallback.rows;
      state.error = "";
      await nextTick();
      renderWidgetChart(widget, fallback);
    } else {
      state.error = error instanceof Error ? error.message : "查询失败";
    }
  } finally {
    state.loading = false;
  }
}

function toQuery(widget: ProcurementBiWidgetConfig): ProcurementBiQueryDTO {
  if (widget.type === "detail") {
    return {
      datasetCode: widget.datasetCode,
      detail: true,
      fields: widget.fields || [],
      filters: effectiveFilters(widget),
      sorts: widget.sorts || [],
      limit: widget.limit || 100
    };
  }
  return {
    datasetCode: widget.datasetCode,
    dimensions: widget.type === "metric" ? [] : widget.dimensions || [],
    metrics: widget.metrics || [],
    filters: effectiveFilters(widget),
    sorts: widget.sorts || [],
    limit: widget.limit || (widget.type === "table" ? 100 : 10)
  };
}

function effectiveFilters(widget: ProcurementBiWidgetConfig): ProcurementBiFilterDTO[] {
  return [
    ...(widget.filters || []),
    ...widgetTimeFilters(widget)
  ];
}

function widgetTimeFilters(widget: ProcurementBiWidgetConfig): ProcurementBiFilterDTO[] {
  const range = normalizeTimeRange(widget.timeRange);
  if (!range.length) {
    return [];
  }
  const fieldCode = timeFilterField();
  if (!fieldByCode(widget.datasetCode, fieldCode)) {
    return [];
  }
  return [{
    fieldCode,
    operator: "BETWEEN",
    values: range
  }];
}

function timeFilterField(): string {
  return "createdAt";
}

function initGrid(): void {
  if (!gridRef.value) {
    return;
  }
  destroyGrid();
  grid = GridStack.init({
    column: 12,
    cellHeight: 82,
    margin: 12,
    float: true,
    handle: ".widget-handle",
    staticGrid: !editing.value,
    minRow: 8,
    animate: true
  }, gridRef.value);
  grid.on("change", (_event, items) => {
    for (const item of items || []) {
      const id = item.el?.getAttribute("gs-id");
      const widget = widgets.value.find((entry) => entry.id === id);
      if (widget) {
        widget.x = Number(item.x || 0);
        widget.y = Number(item.y || 0);
        widget.w = Number(item.w || widget.w || 1);
        widget.h = Number(item.h || widget.h || 1);
      }
    }
    resizeCharts();
  });
}

function destroyGrid(): void {
  if (grid) {
    grid.destroy(false);
    grid = null;
  }
}

function selectWidget(id: string): void {
  selectedWidgetId.value = id;
  activeConfigTab.value = "base";
}

function toggleEditing(): void {
  editing.value = !editing.value;
}

async function addWidget(command: string | number | object): Promise<void> {
  const type = String(command) as BiWidgetType;
  const dataset = datasets.value[0];
  if (!dataset) {
    ElMessage.warning("暂无可用数据源");
    return;
  }
  const widget = createWidget(type, dataset.datasetCode);
  widgets.value.push(widget);
  selectedWidgetId.value = widget.id;
  editing.value = true;
  await nextTick();
  initGrid();
  await loadWidget(widget);
}

function createWidget(type: BiWidgetType, datasetCode: string): ProcurementBiWidgetConfig {
  const dataset = datasetByCode(datasetCode);
  const dimensions = defaultDimensionCodes(dataset);
  const measures = dataset?.fields.filter((field) => field.role === "MEASURE").slice(0, 1) || [];
  const y = widgets.value.reduce((max, widget) => Math.max(max, Number(widget.y || 0) + Number(widget.h || 1)), 0);
  const size = defaultWidgetSize(type);
  return normalizeWidget({
    id: `w-${Date.now()}`,
    title: widgetTypeName(type),
    type,
    datasetCode,
    x: 0,
    y,
    w: size.w,
    h: size.h,
    visible: true,
    detail: type === "detail",
    dimensions: type === "metric" || type === "detail" ? [] : dimensions,
    metrics: type === "detail" ? [] : measures.map((field) => ({ fieldCode: field.fieldCode, aggregation: field.defaultAggregation || "SUM" })),
    fields: type === "detail" ? dataset?.fields.slice(0, 8).map((field) => field.fieldCode) || [] : [],
    timeRange: [],
    limit: type === "detail" || type === "table" ? 100 : 10,
    theme: "default",
    showLegend: true,
    showLabel: true
  });
}

function defaultWidgetSize(type: BiWidgetType): { w: number; h: number } {
  if (type === "metric") return { w: 2, h: 2 };
  if (type === "table" || type === "detail") return { w: 6, h: 4 };
  return { w: 4, h: 4 };
}

async function duplicateWidget(widget: ProcurementBiWidgetConfig): Promise<void> {
  const copy = normalizeWidget({
    ...structuredClone(widget),
    id: `w-${Date.now()}`,
    title: `${widget.title} 副本`,
    x: Math.min(10, Number(widget.x || 0) + 1),
    y: Number(widget.y || 0) + 1,
    visible: true
  });
  widgets.value.push(copy);
  selectedWidgetId.value = copy.id;
  await nextTick();
  initGrid();
  await loadWidget(copy);
  ElMessage.success("已复制组件");
}

function hideWidget(id: string): void {
  const widget = widgets.value.find((entry) => entry.id === id);
  if (widget) {
    widget.visible = false;
  }
  selectedWidgetId.value = visibleWidgets.value[0]?.id || "";
  nextTick(() => initGrid());
}

async function resetLayout(): Promise<void> {
  localStorage.removeItem(localLayoutKey);
  disposeAllCharts();
  chartRefs.clear();
  widgets.value = normalizeWidgets(fallbackLayout().widgets || []);
  selectedWidgetId.value = widgets.value[0]?.id || "";
  await nextTick();
  initGrid();
  localStorage.setItem(localLayoutKey, JSON.stringify(currentLayout()));
  await reloadAll();
  ElMessage.success("已恢复默认看板");
}

async function saveLayout(): Promise<void> {
  updateWidgetsFromDom();
  const layout = currentLayout();
  localStorage.setItem(localLayoutKey, JSON.stringify(layout));
  try {
    await saveProcurementBiDashboardApi(layout);
    ElMessage.success("看板已保存");
  } catch {
    ElMessage.warning("已保存到本地，后端BI表初始化后会同步保存");
  }
}

function updateWidgetsFromDom(): void {
  const root = gridRef.value;
  if (!root) {
    return;
  }
  root.querySelectorAll<HTMLElement>(".grid-stack-item").forEach((el) => {
    const id = el.getAttribute("gs-id");
    const widget = widgets.value.find((entry) => entry.id === id);
    if (!widget) {
      return;
    }
    widget.x = Number(el.getAttribute("gs-x") || widget.x || 0);
    widget.y = Number(el.getAttribute("gs-y") || widget.y || 0);
    widget.w = Number(el.getAttribute("gs-w") || widget.w || 1);
    widget.h = Number(el.getAttribute("gs-h") || widget.h || 1);
  });
}

function currentLayout(): ProcurementBiLayout {
  return {
    version: 1,
    columns: 12,
    widgets: widgets.value.map((widget) => ({
      ...widget,
      filters: widget.filters || [],
      sorts: widget.sorts || []
    }))
  };
}

function readLocalLayout(): ProcurementBiLayout | null {
  try {
    const raw = localStorage.getItem(localLayoutKey);
    return raw ? JSON.parse(raw) as ProcurementBiLayout : null;
  } catch {
    return null;
  }
}

function normalizeTimeRange(value?: unknown): string[] {
  if (!Array.isArray(value) || value.length !== 2 || !value[0] || !value[1]) {
    return [];
  }
  return [String(value[0]), String(value[1])];
}

function fallbackLayout(): ProcurementBiLayout {
  return {
    version: 1,
    columns: 12,
    widgets: [
      metricPreset("w-demand-amount", "需求金额", "requestAmount", "SUM", 0, 0),
      metricPreset("w-award-amount", "中标金额", "awardAmount", "SUM", 2, 0),
      metricPreset("w-qty", "采购数量", "qty", "SUM", 4, 0),
      metricPreset("w-suppliers", "成交供应商", "supplierId", "COUNT_DISTINCT", 6, 0),
      metricPreset("w-lead", "平均交期", "leadTimeDays", "AVG", 8, 0),
      chartPreset("w-dept", "机房金额排行", "bar", ["roomName"], "requestAmount", "SUM", 0, 2, 5, 4),
      chartPreset("w-city", "收货城市占比", "pie", ["receivingCity"], "requestAmount", "SUM", 5, 2, 3, 4),
      chartPreset("w-supplier", "供应商中标金额", "bar", ["supplierName"], "awardAmount", "SUM", 8, 2, 4, 4),
      tablePreset("w-combo", "组合表", ["roomName", "receivingCity"], ["requestAmount", "awardAmount"], 0, 6, 6, 4),
      detailPreset("w-detail", "采购明细", 6, 6, 6, 4)
    ]
  };
}

function metricPreset(id: string, title: string, fieldCode: string, aggregation: string, x: number, y: number): ProcurementBiWidgetConfig {
  return {
    id,
    title,
    type: "metric",
    datasetCode: "purchase_items",
    x,
    y,
    w: 2,
    h: 2,
    visible: true,
    dimensions: [],
    metrics: [{ fieldCode, aggregation }],
    fields: [],
    limit: 10,
    theme: "default",
    showLegend: true,
    showLabel: true
  };
}

function chartPreset(id: string, title: string, type: BiWidgetType, dimensions: string[], fieldCode: string, aggregation: string, x: number, y: number, w: number, h: number): ProcurementBiWidgetConfig {
  return {
    id,
    title,
    type,
    datasetCode: "purchase_items",
    x,
    y,
    w,
    h,
    visible: true,
    dimensions,
    metrics: [{ fieldCode, aggregation }],
    fields: [],
    limit: 10,
    theme: "default",
    showLegend: true,
    showLabel: true
  };
}

function tablePreset(id: string, title: string, dimensions: string[], metrics: string[], x: number, y: number, w: number, h: number): ProcurementBiWidgetConfig {
  return {
    id,
    title,
    type: "table",
    datasetCode: "purchase_items",
    x,
    y,
    w,
    h,
    visible: true,
    dimensions,
    metrics: metrics.map((fieldCode) => ({ fieldCode, aggregation: "SUM" })),
    fields: [],
    limit: 100,
    theme: "default",
    showLegend: true,
    showLabel: true
  };
}

function detailPreset(id: string, title: string, x: number, y: number, w: number, h: number): ProcurementBiWidgetConfig {
  return {
    id,
    title,
    type: "detail",
    detail: true,
    datasetCode: "purchase_items",
    x,
    y,
    w,
    h,
    visible: true,
    dimensions: [],
    metrics: [],
    fields: ["reqNo", "itemName", "specModel", "roomName", "supplierName", "receivingCity", "qty", "requestAmount", "awardAmount", "requestStatus"],
    limit: 100,
    theme: "default",
    showLegend: true,
    showLabel: true
  };
}

function fallbackDatasets(): ProcurementBiDatasetVO[] {
  const purchaseFields: ProcurementBiFieldVO[] = [
    dimensionField("reqNo", "申请单号", "BUSINESS_KEY"),
    dimensionField("reqTitle", "申请标题", "TITLE"),
    dimensionField("reqType", "申请类型", "ENUM"),
    dimensionField("roomName", "机房简称", "ROOM"),
    dimensionField("deptName", "完整部门路径", "ORG"),
    dimensionField("applicantName", "申请人", "USER"),
    dimensionField("itemCode", "物料编码", "PRODUCT"),
    dimensionField("itemName", "商品名称", "PRODUCT"),
    dimensionField("specModel", "规格型号", "PRODUCT_SPEC"),
    dimensionField("unit", "单位", "UNIT"),
    dimensionField("receivingCity", "收货城市", "CITY"),
    dimensionField("supplierName", "中标供应商", "PARTNER"),
    measureField("supplierId", "成交供应商", "PARTNER", "家", "COUNT_DISTINCT"),
    measureField("qty", "采购数量", "QUANTITY", "", "SUM"),
    measureField("requestAmount", "需求金额", "MONEY", "元", "SUM"),
    measureField("awardAmount", "中标金额", "MONEY", "元", "SUM"),
    measureField("leadTimeDays", "交期", "DAYS", "天", "AVG"),
    dimensionField("requestStatus", "采购状态", "ENUM"),
    dimensionField("approvalStatus", "审批状态", "ENUM"),
    dimensionField("supplierConfirmStatus", "供应商确认状态", "ENUM"),
    timeField("createdAt", "创建时间"),
    timeField("submittedAt", "提交时间"),
    timeField("approvedAt", "通过时间")
  ];
  const quoteItemFields: ProcurementBiFieldVO[] = [
    dimensionField("quoteNo", "报价单号", "BUSINESS_KEY"),
    dimensionField("reqNo", "申请单号", "BUSINESS_KEY"),
    dimensionField("reqTitle", "申请标题", "TITLE"),
    dimensionField("reqType", "申请类型", "ENUM"),
    dimensionField("roomName", "机房简称", "ROOM"),
    dimensionField("deptName", "完整部门路径", "ORG"),
    dimensionField("applicantName", "申请人", "USER"),
    dimensionField("supplierName", "供应商", "PARTNER"),
    dimensionField("itemCode", "物料编码", "PRODUCT"),
    dimensionField("itemName", "商品名称", "PRODUCT"),
    dimensionField("specModel", "规格型号", "PRODUCT_SPEC"),
    dimensionField("unit", "单位", "UNIT"),
    dimensionField("receivingCity", "收货城市", "CITY"),
    dimensionField("deliveryAddress", "送达地址", "ADDRESS"),
    dimensionField("awardStatus", "中标状态", "ENUM"),
    dimensionField("quoteStatus", "报价状态", "ENUM"),
    dimensionField("quoteItemStatus", "报价明细状态", "ENUM"),
    dimensionField("supplierConfirmStatus", "供应商确认状态", "ENUM"),
    dimensionField("receiptStatus", "收货状态", "ENUM"),
    measureField("quoteItemId", "报价明细数", "COUNT", "项", "COUNT_DISTINCT"),
    measureField("supplierId", "供应商数", "PARTNER", "家", "COUNT_DISTINCT"),
    measureField("qty", "报价数量", "QUANTITY", "", "SUM"),
    measureField("unitPrice", "报价单价", "MONEY", "元", "AVG"),
    measureField("quoteLineAmount", "报价金额", "MONEY", "元", "SUM"),
    measureField("requestAmount", "需求金额", "MONEY", "元", "SUM"),
    measureField("awarded", "中标明细数", "COUNT", "项", "SUM"),
    measureField("awardedAmount", "中标金额", "MONEY", "元", "SUM"),
    measureField("leadTimeDays", "交期", "DAYS", "天", "AVG"),
    measureField("replacementFlag", "替换商品数", "COUNT", "项", "SUM"),
    timeField("submittedAt", "报价提交时间"),
    timeField("receivedAt", "收货时间"),
    timeField("createdAt", "申请创建时间")
  ];
  const receiptFields: ProcurementBiFieldVO[] = [
    dimensionField("quoteNo", "报价单号", "BUSINESS_KEY"),
    dimensionField("reqNo", "申请单号", "BUSINESS_KEY"),
    dimensionField("reqTitle", "申请标题", "TITLE"),
    dimensionField("reqType", "申请类型", "ENUM"),
    dimensionField("roomName", "机房简称", "ROOM"),
    dimensionField("deptName", "完整部门路径", "ORG"),
    dimensionField("applicantName", "申请人", "USER"),
    dimensionField("supplierName", "供应商", "PARTNER"),
    dimensionField("itemCode", "物料编码", "PRODUCT"),
    dimensionField("itemName", "商品名称", "PRODUCT"),
    dimensionField("specModel", "规格型号", "PRODUCT_SPEC"),
    dimensionField("unit", "单位", "UNIT"),
    dimensionField("receivingCity", "收货城市", "CITY"),
    dimensionField("deliveryAddress", "送达地址", "ADDRESS"),
    dimensionField("supplierConfirmStatus", "供应商确认状态", "ENUM"),
    dimensionField("receiptStatus", "收货状态", "ENUM"),
    measureField("quoteItemId", "中标明细数", "COUNT", "项", "COUNT_DISTINCT"),
    measureField("supplierId", "成交供应商", "PARTNER", "家", "COUNT_DISTINCT"),
    measureField("qty", "中标数量", "QUANTITY", "", "SUM"),
    measureField("unitPrice", "中标单价", "MONEY", "元", "AVG"),
    measureField("awardAmount", "中标金额", "MONEY", "元", "SUM"),
    measureField("leadTimeDays", "交期", "DAYS", "天", "AVG"),
    measureField("pendingReceipt", "待收货数", "COUNT", "项", "SUM"),
    measureField("receivedLine", "已收货数", "COUNT", "项", "SUM"),
    timeField("supplierConfirmedAt", "供应商确认时间"),
    timeField("receivedAt", "收货时间"),
    timeField("createdAt", "申请创建时间")
  ];
  const afterSaleFields: ProcurementBiFieldVO[] = [
    dimensionField("afterSaleNo", "售后单号", "BUSINESS_KEY"),
    dimensionField("reqNo", "申请单号", "BUSINESS_KEY"),
    dimensionField("quoteNo", "报价单号", "BUSINESS_KEY"),
    dimensionField("reqTitle", "申请标题", "TITLE"),
    dimensionField("reqType", "申请类型", "ENUM"),
    dimensionField("roomName", "机房简称", "ROOM"),
    dimensionField("deptName", "完整部门路径", "ORG"),
    dimensionField("supplierName", "供应商", "PARTNER"),
    dimensionField("itemCode", "物料编码", "PRODUCT"),
    dimensionField("itemName", "商品名称", "PRODUCT"),
    dimensionField("specModel", "规格型号", "PRODUCT_SPEC"),
    dimensionField("unit", "单位", "UNIT"),
    dimensionField("afterSaleType", "售后类型", "ENUM"),
    dimensionField("afterSaleStatus", "售后状态", "ENUM"),
    dimensionField("receiptStatus", "收货状态", "ENUM"),
    dimensionField("reason", "售后原因", "TEXT"),
    dimensionField("replacementItemName", "替换商品", "PRODUCT"),
    dimensionField("replacementSpecModel", "替换规格", "PRODUCT_SPEC"),
    dimensionField("replacementUnit", "替换单位", "UNIT"),
    dimensionField("replacementRemark", "替换说明", "TEXT"),
    dimensionField("requesterName", "售后申请人", "USER"),
    dimensionField("approverName", "售后审核人", "USER"),
    dimensionField("approvalRemark", "审核意见", "TEXT"),
    measureField("afterSaleId", "售后单数", "COUNT", "单", "COUNT_DISTINCT"),
    measureField("quoteItemId", "售后明细数", "COUNT", "项", "COUNT_DISTINCT"),
    measureField("requestedQty", "售后数量", "QUANTITY", "", "SUM"),
    measureField("awardAmount", "中标金额", "MONEY", "元", "SUM"),
    timeField("receivedAt", "收货时间"),
    timeField("createdAt", "创建时间"),
    timeField("approvedAt", "审核时间")
  ];
  return [
    {
      datasetCode: "purchase_items",
      datasetName: "采购明细表",
      description: "申请明细、机房、收货城市、中标供应商和金额交期",
      fields: purchaseFields
    },
    {
      datasetCode: "supplier_quote_items",
      datasetName: "供应商报价明细表",
      description: "供应商逐项报价、替换商品、中标结果、收货状态和确认状态",
      fields: quoteItemFields
    },
    {
      datasetCode: "receipt_items",
      datasetName: "中标收货明细表",
      description: "已中标商品的供应商确认、人工收货状态和交付周期",
      fields: receiptFields
    },
    {
      datasetCode: "after_sales",
      datasetName: "售后明细表",
      description: "售后申请、原商品、替换商品、供应商、收货状态和审核结果",
      fields: afterSaleFields
    }
  ];
}

function dimensionField(fieldCode: string, fieldName: string, semanticType: string): ProcurementBiFieldVO {
  return {
    fieldCode,
    fieldName,
    dataType: "STRING",
    semanticType,
    role: "DIMENSION",
    filterable: true,
    groupable: true,
    sortable: true
  };
}

function timeField(fieldCode: string, fieldName: string): ProcurementBiFieldVO {
  return {
    ...dimensionField(fieldCode, fieldName, "TIME"),
    dataType: "DATETIME"
  };
}

function measureField(fieldCode: string, fieldName: string, semanticType: string, unit: string, defaultAggregation: string): ProcurementBiFieldVO {
  return {
    fieldCode,
    fieldName,
    dataType: "NUMBER",
    semanticType,
    role: "MEASURE",
    unit,
    defaultAggregation,
    filterable: true,
    groupable: false,
    sortable: true
  };
}

function normalizeWidgets(source: ProcurementBiWidgetConfig[]): ProcurementBiWidgetConfig[] {
  return (source || []).map((widget) => {
    const normalized = normalizeWidget(widget);
    ensureWidgetSelection(normalized);
    return normalized;
  });
}

function normalizeWidget(widget: ProcurementBiWidgetConfig): ProcurementBiWidgetConfig {
  const normalized = {
    ...widget,
    type: widget.type || "table",
    x: Number(widget.x || 0),
    y: Number(widget.y || 0),
    w: Number(widget.w || defaultWidgetSize(widget.type || "table").w),
    h: Number(widget.h || defaultWidgetSize(widget.type || "table").h),
    visible: widget.visible !== false,
    dimensions: widget.dimensions || [],
    metrics: widget.metrics || [],
    fields: widget.fields || [],
    filters: widget.filters || [],
    timeRange: normalizeTimeRange(widget.timeRange),
    sorts: widget.sorts || [],
    limit: widget.limit || 50,
    theme: widget.theme || "default",
    showLegend: widget.showLegend !== false,
    showLabel: widget.showLabel !== false
  };
  states[normalized.id] ||= emptyState();
  return normalized;
}

function emptyState(): WidgetState {
  return { loading: false, error: "", columns: [], rows: [] };
}

function widgetState(widget: ProcurementBiWidgetConfig): WidgetState {
  states[widget.id] ||= emptyState();
  return states[widget.id];
}

function datasetByCode(code?: string): ProcurementBiDatasetVO | undefined {
  return datasets.value.find((dataset) => dataset.datasetCode === code);
}

function fieldByCode(datasetCode: string, fieldCode: string): ProcurementBiFieldVO | undefined {
  return datasetByCode(datasetCode)?.fields.find((field) => field.fieldCode === fieldCode);
}

function fieldName(datasetCode: string, fieldCode: string): string {
  return fieldByCode(datasetCode, fieldCode)?.fieldName || fieldCode;
}

function widgetSubtitle(widget: ProcurementBiWidgetConfig): string {
  const datasetName = datasetByCode(widget.datasetCode)?.datasetName || "未选择数据源";
  const state = widgetState(widget);
  return `${datasetName} · ${state.rows.length}条`;
}

function widgetTypeName(type: BiWidgetType): string {
  return widgetTypeOptions.find((option) => option.value === type)?.label || "组件";
}

function handleWidgetTypeChange(): void {
  const widget = selectedWidget.value;
  if (!widget) return;
  const size = defaultWidgetSize(widget.type);
  widget.w = Math.max(widget.w || 1, size.w);
  widget.h = Math.max(widget.h || 1, size.h);
  widget.detail = widget.type === "detail";
  ensureWidgetSelection(widget, true);
  handleWidgetConfigChange();
  nextTick(() => initGrid());
}

function handleDatasetChange(): void {
  const widget = selectedWidget.value;
  if (!widget) return;
  widget.dimensions = [];
  widget.metrics = [];
  widget.fields = [];
  ensureWidgetSelection(widget, true);
  handleWidgetConfigChange();
}

function handleMetricSelectionChange(values: string[]): void {
  const widget = selectedWidget.value;
  if (!widget) return;
  widget.metrics = values.map((fieldCode) => {
    const existing = widget.metrics?.find((metric) => metric.fieldCode === fieldCode);
    return existing || {
      fieldCode,
      aggregation: fieldByCode(widget.datasetCode, fieldCode)?.defaultAggregation || "SUM"
    };
  });
  handleWidgetConfigChange();
}

function ensureWidgetSelection(widget: ProcurementBiWidgetConfig, forceDefault = false): void {
  const dataset = datasetByCode(widget.datasetCode);
  if (!dataset) return;
  const dimensions = dataset.fields.filter((field) => field.role === "DIMENSION" && field.groupable);
  const measures = dataset.fields.filter((field) => field.role === "MEASURE");
  const dimensionCodes = new Set(dimensions.map((field) => field.fieldCode));
  const measureCodes = new Set(measures.map((field) => field.fieldCode));
  const allFieldCodes = new Set(dataset.fields.map((field) => field.fieldCode));
  if (widget.type === "detail") {
    const currentFields = (widget.fields || [])
      .map((fieldCode) => normalizeLegacyFieldCode(widget.datasetCode, fieldCode))
      .filter((fieldCode) => allFieldCodes.has(fieldCode));
    widget.fields = !forceDefault && currentFields.length
      ? currentFields
      : dataset.fields.slice(0, 8).map((field) => field.fieldCode);
    widget.metrics = [];
    widget.dimensions = [];
    return;
  }

  const currentMetrics = (widget.metrics || [])
    .map((metric) => ({
      ...metric,
      fieldCode: normalizeLegacyFieldCode(widget.datasetCode, metric.fieldCode)
    }))
    .filter((metric) => measureCodes.has(metric.fieldCode));
  widget.metrics = !forceDefault && currentMetrics.length ? currentMetrics : measures.slice(0, 1).map((field) => ({
    fieldCode: field.fieldCode,
    aggregation: field.defaultAggregation || "SUM"
  }));

  const currentDimensions = (widget.dimensions || [])
    .map((fieldCode) => normalizeLegacyFieldCode(widget.datasetCode, fieldCode))
    .filter((fieldCode) => dimensionCodes.has(fieldCode));
  widget.dimensions = widget.type === "metric"
    ? []
    : !forceDefault && currentDimensions.length ? currentDimensions : defaultDimensionCodes(dataset);
}

function handleWidgetConfigChange(): void {
  const widget = selectedWidget.value;
  if (!widget) return;
  widget.timeRange = normalizeTimeRange(widget.timeRange);
  ensureWidgetSelection(widget);
  markDirty();
  loadWidget(widget);
}

function defaultDimensionCodes(dataset?: ProcurementBiDatasetVO): string[] {
  if (!dataset) return [];
  const groupable = dataset.fields.filter((field) => field.role === "DIMENSION" && field.groupable);
  const preferred = groupable.find((field) => field.fieldCode === "roomName");
  return [(preferred || groupable[0])?.fieldCode].filter((fieldCode): fieldCode is string => Boolean(fieldCode));
}

function normalizeLegacyFieldCode(datasetCode: string, fieldCode: string): string {
  if (fieldCode === "deptName" && ["purchase_items", "supplier_quotes", "approval_tasks", "after_sales"].includes(datasetCode)) {
    return "roomName";
  }
  return fieldCode;
}

function markDirty(): void {
  updateWidgetsFromDom();
  localStorage.setItem(localLayoutKey, JSON.stringify(currentLayout()));
}

function isChart(widget: ProcurementBiWidgetConfig): boolean {
  return ["bar", "pie", "donut", "line"].includes(widget.type);
}

function setChartRef(id: string, el: Element | ComponentPublicInstance | null): void {
  if (el instanceof HTMLElement) {
    chartRefs.set(id, el);
    const existing = chartInstances.get(id);
    if (existing && existing.getDom() !== el) {
      existing.dispose();
      chartInstances.delete(id);
    }
  } else {
    chartRefs.delete(id);
    const existing = chartInstances.get(id);
    if (existing) {
      existing.dispose();
      chartInstances.delete(id);
    }
  }
}

function renderCharts(): void {
  for (const widget of visibleWidgets.value) {
    if (isChart(widget)) {
      renderWidgetChart(widget);
    }
  }
}

function renderWidgetChart(widget: ProcurementBiWidgetConfig, result?: ProcurementBiQueryResultVO): void {
  if (!isChart(widget)) return;
  const host = chartRefs.get(widget.id);
  if (!host) return;
  const state = widgetState(widget);
  const data = result || {
    datasetCode: widget.datasetCode,
    columns: state.columns,
    rows: state.rows
  };
  let chart = chartInstances.get(widget.id);
  if (chart && chart.getDom() !== host) {
    chart.dispose();
    chartInstances.delete(widget.id);
    chart = undefined;
  }
  if (!chart) {
    chart = init(host);
    chartInstances.set(widget.id, chart);
  }
  chart.setOption(buildChartOption(widget, data), true);
  chart.off("click");
  chart.on("click", (params) => {
    if (editing.value) return;
    const click = params as { dataIndex?: number; seriesIndex?: number; seriesName?: string };
    const dataIndex = typeof click.dataIndex === "number" ? click.dataIndex : -1;
    const seriesIndex = typeof click.seriesIndex === "number" ? click.seriesIndex : 0;
    void openChartDetails(widget, data, dataIndex, seriesIndex, click.seriesName);
  });
  host.style.cursor = editing.value ? "default" : "pointer";
  chart.resize();
}

function disposeAllCharts(): void {
  chartInstances.forEach((chart) => chart.dispose());
  chartInstances.clear();
}

function resizeCharts(): void {
  chartInstances.forEach((chart) => chart.resize());
}

async function openChartDetails(
  widget: ProcurementBiWidgetConfig,
  result: ProcurementBiQueryResultVO,
  dataIndex: number,
  seriesIndex = 0,
  seriesName?: string
): Promise<void> {
  const row = result.rows?.[dataIndex];
  if (!row) {
    return;
  }
  const dimensionColumns = result.columns.filter((column) => column.role === "DIMENSION");
  const metricColumns = result.columns.filter((column) => column.role === "MEASURE");
  const clickedMetric = metricColumns[seriesIndex] || metricColumns.find((column) => column.label === seriesName) || metricColumns[0];
  if (!dimensionColumns.length) {
    ElMessage.info("这个图表没有可钻取的分组字段");
    return;
  }
  const filters = buildDrillFilters(widget, dimensionColumns, row, clickedMetric);
  if (!filters.length) {
    ElMessage.info("当前分类没有可用的明细过滤条件");
    return;
  }

  const filterLabels = dimensionColumns
    .map((column) => {
      const value = row[column.code];
      return value == null || value === "" ? "" : `${column.label}: ${String(value)}`;
    })
    .filter(Boolean);
  drillDetail.visible = true;
  drillDetail.title = `${widget.title || "图表"} 明细`;
  drillDetail.subtitle = `${datasetByCode(widget.datasetCode)?.datasetName || "数据源"} · 最多显示 500 条`;
  const metricLabel = clickedMetric ? `${clickedMetric.label}: 大于 0` : "";
  drillDetail.filters = [...filterLabels, metricLabel].filter(Boolean);
  drillDetail.loading = true;
  drillDetail.columns = [];
  drillDetail.rows = [];
  drillDetail.keyword = "";
  drillDetail.visibleColumnCodes = [];

  try {
    const detail = await queryProcurementBiApi({
      datasetCode: widget.datasetCode,
      detail: true,
      fields: detailFieldCodes(widget.datasetCode),
      filters,
      limit: 500
    });
    setDrillDetailResult(widget, detail);
  } catch (error) {
    if (import.meta.env.DEV) {
      const fallback = sampleDrillDetail(widget, filters);
      setDrillDetailResult(widget, fallback);
    } else {
      ElMessage.error(error instanceof Error ? error.message : "明细查询失败");
      drillDetail.visible = false;
    }
  } finally {
    drillDetail.loading = false;
  }
}

function buildDrillFilters(
  widget: ProcurementBiWidgetConfig,
  dimensionColumns: ProcurementBiColumnVO[],
  row: Record<string, unknown>,
  metricColumn?: ProcurementBiColumnVO
): ProcurementBiFilterDTO[] {
  const filters: ProcurementBiFilterDTO[] = effectiveFilters(widget);
  for (const column of dimensionColumns) {
    const value = row[column.code];
    if (value == null || value === "") {
      continue;
    }
    filters.push({
      fieldCode: column.code,
      operator: "EQ",
      value
    });
  }
  const metricFieldCode = metricColumn ? metricSourceFieldCode(metricColumn.code) : "";
  const metricField = metricFieldCode ? fieldByCode(widget.datasetCode, metricFieldCode) : undefined;
  if (metricField?.role === "MEASURE") {
    filters.push({
      fieldCode: metricFieldCode,
      operator: ">",
      value: 0
    });
  }
  return filters;
}

function metricSourceFieldCode(outputCode: string): string {
  return outputCode.replace(/__(sum|avg|min|max|count|count_distinct)$/i, "");
}

function setDrillDetailResult(widget: ProcurementBiWidgetConfig, result: ProcurementBiQueryResultVO): void {
  drillDetail.columns = result.columns || [];
  drillDetail.rows = result.rows || [];
  drillDetail.visibleColumnCodes = defaultDrillVisibleColumnCodes(widget.datasetCode, drillDetail.columns);
}

function defaultDrillVisibleColumnCodes(datasetCode: string, columns: ProcurementBiColumnVO[]): string[] {
  const preferred = defaultVisibleDrillFields(datasetCode);
  const available = new Set(columns.map((column) => column.code));
  const chosen = preferred.filter((fieldCode) => available.has(fieldCode));
  if (chosen.length) {
    return chosen;
  }
  return columns.slice(0, 10).map((column) => column.code);
}

function defaultVisibleDrillFields(datasetCode: string): string[] {
  if (datasetCode === "supplier_quote_items") {
    return ["quoteNo", "reqNo", "supplierName", "itemName", "specModel", "roomName", "qty", "unitPrice", "quoteLineAmount", "awardedAmount", "awardStatus", "leadTimeDays"];
  }
  if (datasetCode === "receipt_items") {
    return ["reqNo", "quoteNo", "supplierName", "itemName", "specModel", "roomName", "receivingCity", "qty", "awardAmount", "leadTimeDays", "receiptStatus", "receivedAt"];
  }
  if (datasetCode === "supplier_quotes") {
    return ["quoteNo", "reqNo", "supplierName", "roomName", "quoteAmount", "awardedAmount", "leadTimeDays", "quoteStatus", "supplierConfirmStatus", "submittedAt"];
  }
  if (datasetCode === "after_sales") {
    return ["afterSaleNo", "reqNo", "quoteNo", "supplierName", "itemName", "specModel", "roomName", "afterSaleType", "afterSaleStatus", "requestedQty", "requesterName", "createdAt"];
  }
  if (datasetCode === "approval_tasks") {
    return ["reqNo", "reqTitle", "roomName", "nodeName", "assigneeName", "taskStatus", "createdAt", "completedAt"];
  }
  return ["reqNo", "reqTitle", "supplierName", "itemName", "specModel", "roomName", "receivingCity", "qty", "requestAmount", "awardAmount", "leadTimeDays"];
}

function detailFieldCodes(datasetCode: string): string[] {
  const dataset = datasetByCode(datasetCode);
  if (!dataset) return [];
  const preferred = [
    "reqNo",
    "quoteNo",
    "afterSaleNo",
    "reqTitle",
    "supplierName",
    "itemName",
    "specModel",
    "roomName",
    "receivingCity",
    "qty",
    "unitPrice",
    "requestAmount",
    "quoteLineAmount",
    "awardAmount",
    "leadTimeDays",
    "awardStatus",
    "receiptStatus",
    "supplierConfirmStatus",
    "afterSaleType",
    "afterSaleStatus",
    "requestedQty",
    "replacementItemName",
    "requesterName",
    "approverName",
    "createdAt",
    "submittedAt",
    "receivedAt",
    "approvedAt"
  ];
  const available = new Set(dataset.fields.map((field) => field.fieldCode));
  const chosen = preferred.filter((fieldCode) => available.has(fieldCode));
  for (const field of dataset.fields) {
    if (chosen.length >= 18) break;
    if (!chosen.includes(field.fieldCode)) {
      chosen.push(field.fieldCode);
    }
  }
  return chosen;
}

function buildChartOption(widget: ProcurementBiWidgetConfig, result: ProcurementBiQueryResultVO): EChartsCoreOption {
  const dimension = result.columns.find((column) => column.role === "DIMENSION") || result.columns[0];
  const metricColumns = result.columns.filter((column) => column.role === "MEASURE");
  const metric = metricColumns[0] || result.columns[result.columns.length - 1];
  const rows = (result.rows || []).slice(0, widget.limit || 10);
  const names = rows.map((row) => String(dimension ? row[dimension.code] ?? "未命名" : "合计"));
  const palette = paletteFor(widget.theme);
  const seriesData = names.map((name, index) => ({ name, value: Number(metric ? rows[index]?.[metric.code] || 0 : 0) }));

  if (widget.type === "pie" || widget.type === "donut") {
    return {
      color: palette,
      tooltip: { trigger: "item" },
      legend: { show: widget.showLegend !== false, bottom: 0, type: "scroll" },
      series: [{
        name: metric?.label || widget.title,
        type: "pie",
        radius: widget.type === "donut" ? ["45%", "70%"] : "70%",
        center: ["50%", "45%"],
        label: { show: widget.showLabel !== false },
        data: seriesData
      }]
    };
  }

  if (widget.type === "line") {
    return {
      color: palette,
      tooltip: { trigger: "axis" },
      grid: { left: 42, right: 18, top: 28, bottom: widget.showLegend === false ? 34 : 56 },
      legend: { show: widget.showLegend !== false, bottom: 0 },
      xAxis: { type: "category", data: names, axisLabel: { interval: 0, rotate: names.length > 5 ? 25 : 0 } },
      yAxis: { type: "value" },
      series: metricColumns.map((column) => ({
        name: column.label || widget.title,
        type: "line",
        smooth: true,
        symbolSize: 7,
        label: { show: widget.showLabel === true, position: "top" },
        data: rows.map((row, index) => ({ name: names[index], value: Number(row[column.code] || 0) }))
      }))
    };
  }

  return {
    color: palette,
    tooltip: { trigger: "axis" },
    grid: { left: 42, right: 18, top: 28, bottom: widget.showLegend === false ? 34 : 56 },
    legend: { show: widget.showLegend !== false, bottom: 0 },
    xAxis: { type: "category", data: names, axisLabel: { interval: 0, rotate: names.length > 5 ? 25 : 0 } },
    yAxis: { type: "value" },
    series: metricColumns.map((column) => ({
      name: column.label || widget.title,
      type: "bar",
      barMaxWidth: 42,
      label: { show: widget.showLabel === true, position: "top" },
      data: rows.map((row, index) => ({ name: names[index], value: Number(row[column.code] || 0) }))
    }))
  };
}

function paletteFor(theme?: string): string[] {
  if (theme === "green") return ["#14804a", "#2aa876", "#77c69f", "#b7e2ca", "#475569"];
  if (theme === "orange") return ["#c46b00", "#e49b31", "#f2c078", "#475569", "#94a3b8"];
  if (theme === "slate") return ["#334155", "#64748b", "#94a3b8", "#cbd5e1", "#2563eb"];
  if (theme === "blue") return ["#165dff", "#3b82f6", "#60a5fa", "#93c5fd", "#0f766e"];
  return ["#165dff", "#138a4d", "#c46b00", "#475569", "#8b5cf6", "#0f766e"];
}

function metricDisplay(widget: ProcurementBiWidgetConfig): { value: string; label: string } {
  const state = widgetState(widget);
  const metric = state.columns.find((column) => column.role === "MEASURE") || state.columns[0];
  const value = metric && state.rows[0] ? state.rows[0][metric.code] : 0;
  return {
    value: formatCell(value, metric),
    label: metric?.label || widget.title
  };
}

function sampleQueryResult(widget: ProcurementBiWidgetConfig): ProcurementBiQueryResultVO {
  const datasetCode = widget.datasetCode || "purchase_items";
  const dataset = datasetByCode(datasetCode) || fallbackDatasets()[0];
  const rowsInScope = applySampleFilters(samplePurchaseRows, effectiveFilters(widget));
  if (widget.type === "detail") {
    const fieldCodes = widget.fields?.length ? widget.fields : dataset.fields.slice(0, 8).map((field) => field.fieldCode);
    const columns = fieldCodes
      .map((fieldCode) => columnForField(datasetCode, fieldCode))
      .filter((column): column is ProcurementBiColumnVO => Boolean(column));
    const rows = rowsInScope.slice(0, widget.limit || 100).map((row) => pickRow(row, columns.map((column) => column.code)));
    return { datasetCode, columns, rows };
  }

  const dimensionCodes = widget.type === "metric" ? [] : widget.dimensions || [];
  const metrics = widget.metrics?.length ? widget.metrics : [{ fieldCode: "requestAmount", aggregation: "SUM" }];
  const dimensionColumns = dimensionCodes
    .map((fieldCode) => columnForField(datasetCode, fieldCode))
    .filter((column): column is ProcurementBiColumnVO => Boolean(column));
  const metricColumns = metrics
    .map((metric) => metricColumnForField(datasetCode, metric))
    .filter((column): column is ProcurementBiColumnVO => Boolean(column));
  const rows = aggregateSampleRows(rowsInScope, dimensionCodes, metrics).slice(0, widget.limit || 10);
  return {
    datasetCode,
    columns: [...dimensionColumns, ...metricColumns],
    rows
  };
}

function sampleDrillDetail(widget: ProcurementBiWidgetConfig, filters: ProcurementBiFilterDTO[]): ProcurementBiQueryResultVO {
  const datasetCode = widget.datasetCode || "purchase_items";
  const fieldCodes = detailFieldCodes(datasetCode);
  const columns = fieldCodes
    .map((fieldCode) => columnForField(datasetCode, fieldCode))
    .filter((column): column is ProcurementBiColumnVO => Boolean(column));
  const rows = samplePurchaseRows
    .filter((row) => matchesSampleFilters(row, filters))
    .slice(0, 500)
    .map((row) => pickRow(row, columns.map((column) => column.code)));
  return { datasetCode, columns, rows };
}

function applySampleFilters(rows: Record<string, unknown>[], filters: ProcurementBiFilterDTO[]): Record<string, unknown>[] {
  return rows.filter((row) => matchesSampleFilters(row, filters));
}

function matchesSampleFilters(row: Record<string, unknown>, filters: ProcurementBiFilterDTO[]): boolean {
  return filters.every((filter) => {
    const rowValue = row[filter.fieldCode];
    const operator = (filter.operator || "=").toUpperCase();
    if (operator === "EQ" || operator === "=") {
      return String(rowValue ?? "") === String(filter.value ?? "");
    }
    if (operator === "BETWEEN") {
      const values = filter.values || [];
      if (values.length < 2) return true;
      const current = String(rowValue ?? "").slice(0, 10);
      return current >= String(values[0]).slice(0, 10) && current <= String(values[1]).slice(0, 10);
    }
    if (operator === ">" || operator === "GT") {
      return Number(rowValue || 0) > Number(filter.value || 0);
    }
    return true;
  });
}

function columnForField(datasetCode: string, fieldCode: string): ProcurementBiColumnVO | null {
  const field = fieldByCode(datasetCode, fieldCode);
  if (!field) {
    return null;
  }
  return {
    code: field.fieldCode,
    label: field.fieldName,
    role: field.role,
    dataType: field.dataType,
    semanticType: field.semanticType,
    unit: field.unit || undefined,
    aggregation: field.defaultAggregation || undefined
  };
}

function metricColumnForField(datasetCode: string, metric: ProcurementBiMetricDTO): ProcurementBiColumnVO | null {
  const field = fieldByCode(datasetCode, metric.fieldCode);
  if (!field) {
    return null;
  }
  const aggregation = metric.aggregation || field.defaultAggregation || "SUM";
  return {
    code: metricOutputCode(metric.fieldCode, aggregation),
    label: `${field.fieldName}${aggregationLabel(aggregation)}`,
    role: "MEASURE",
    dataType: field.dataType,
    semanticType: field.semanticType,
    unit: field.unit || undefined,
    aggregation
  };
}

function aggregateSampleRows(rows: Record<string, unknown>[], dimensions: string[], metrics: ProcurementBiMetricDTO[]): Record<string, unknown>[] {
  const groups = new Map<string, { dimensions: Record<string, unknown>; values: Record<string, number[]> }>();
  for (const row of rows) {
    const key = dimensions.map((fieldCode) => String(row[fieldCode] || "-")).join("||") || "__total";
    if (!groups.has(key)) {
      groups.set(key, {
        dimensions: Object.fromEntries(dimensions.map((fieldCode) => [fieldCode, row[fieldCode] || "-"])),
        values: {}
      });
    }
    const group = groups.get(key)!;
    for (const metric of metrics) {
      const aggregation = metric.aggregation || "SUM";
      const outputCode = metricOutputCode(metric.fieldCode, aggregation);
      group.values[outputCode] ||= [];
      group.values[outputCode].push(Number(row[metric.fieldCode] || 0));
    }
  }
  return Array.from(groups.values()).map((group) => {
    const result: Record<string, unknown> = { ...group.dimensions };
    for (const metric of metrics) {
      const aggregation = metric.aggregation || "SUM";
      const outputCode = metricOutputCode(metric.fieldCode, aggregation);
      result[outputCode] = aggregateValues(group.values[outputCode] || [], aggregation);
    }
    return result;
  }).sort((left, right) => {
    const firstMetric = metrics[0];
    if (!firstMetric) return 0;
    const code = metricOutputCode(firstMetric.fieldCode, firstMetric.aggregation || "SUM");
    return Number(right[code] || 0) - Number(left[code] || 0);
  });
}

function aggregateValues(values: number[], aggregation: string): number {
  if (!values.length) return 0;
  if (aggregation === "AVG") return values.reduce((sum, value) => sum + value, 0) / values.length;
  if (aggregation === "MAX") return Math.max(...values);
  if (aggregation === "MIN") return Math.min(...values);
  if (aggregation === "COUNT") return values.length;
  if (aggregation === "COUNT_DISTINCT") return new Set(values.filter((value) => value > 0)).size;
  return values.reduce((sum, value) => sum + value, 0);
}

function metricOutputCode(fieldCode: string, aggregation: string): string {
  return `${fieldCode}__${aggregation.toLowerCase()}`;
}

function aggregationLabel(aggregation: string): string {
  if (aggregation === "AVG") return "（平均）";
  if (aggregation === "MAX") return "（最大）";
  if (aggregation === "MIN") return "（最小）";
  if (aggregation === "COUNT") return "（计数）";
  if (aggregation === "COUNT_DISTINCT") return "（去重数）";
  return "（求和）";
}

function pickRow(row: Record<string, unknown>, fields: string[]): Record<string, unknown> {
  return Object.fromEntries(fields.map((field) => [field, row[field]]));
}

const samplePurchaseRows: Record<string, unknown>[] = [
  {
    reqNo: "PTCG-100025",
    reqTitle: "2026-05全国月度需求汇总",
    reqType: "服务/费用/低值易耗等",
    deptName: "[N420D01430]世纪互联-业务共享中台-DC运维事业部-华北一部-酒仙桥-M6V&M5二三期",
    roomName: "M6V&M5二三期",
    applicantName: "刘笑菲",
    itemCode: "MAT-3ZZEAS",
    itemName: "交换机",
    specModel: "S5735",
    unit: "件",
    receivingCity: "北京",
    supplierId: 1,
    supplierName: "齐心",
    qty: 20,
    requestAmount: 25485,
    awardAmount: 26464,
    leadTimeDays: 7,
    requestStatus: "已中标",
    approvalStatus: "已通过",
    supplierConfirmStatus: "CONFIRMED",
    createdAt: "2026-05-28 17:29",
    submittedAt: "2026-05-28 17:29",
    approvedAt: "2026-05-29 09:12"
  },
  {
    reqNo: "PTCG-100024",
    reqTitle: "补充采购-2026-05全国月度需求汇总",
    reqType: "服务/费用/低值易耗等",
    deptName: "[N420N00077]世纪互联-业务共享中台-DC运维事业部-华北一部-酒仙桥-M6",
    roomName: "M6",
    applicantName: "苗壮壮",
    itemCode: "MAT-2LL752",
    itemName: "光模块",
    specModel: "10G",
    unit: "件",
    receivingCity: "北京",
    supplierId: 2,
    supplierName: "鑫方盛",
    qty: 200,
    requestAmount: 22200,
    awardAmount: 21080,
    leadTimeDays: 5,
    requestStatus: "已中标",
    approvalStatus: "已通过",
    supplierConfirmStatus: "CONFIRMED",
    createdAt: "2026-05-28 17:28",
    submittedAt: "2026-05-28 17:28",
    approvedAt: "2026-05-29 10:21"
  },
  {
    reqNo: "JJCG-100001",
    reqTitle: "紧急紧急",
    reqType: "紧急采购",
    deptName: "[N420D01430]世纪互联-业务共享中台-DC运维事业部-华北一部-酒仙桥-M6V&M5二三期",
    roomName: "M6V&M5二三期",
    applicantName: "张强",
    itemCode: "MAT-3ZZEAS",
    itemName: "电源线",
    specModel: "国标",
    unit: "件",
    receivingCity: "北京",
    supplierId: 3,
    supplierName: "海创",
    qty: 12,
    requestAmount: 1100,
    awardAmount: 1100,
    leadTimeDays: 7,
    requestStatus: "已中标",
    approvalStatus: "已通过",
    supplierConfirmStatus: "PENDING",
    createdAt: "2026-05-29 11:11",
    submittedAt: "2026-05-29 11:12",
    approvedAt: "2026-05-29 12:03"
  },
  {
    reqNo: "PTCG-100021",
    reqTitle: "2026-05全国月度需求汇总",
    reqType: "服务/费用/低值易耗等",
    deptName: "[N420N00077]世纪互联-业务共享中台-DC运维事业部-华北一部-酒仙桥-M6",
    roomName: "M6",
    applicantName: "刘笑菲",
    itemCode: "MAT-555",
    itemName: "扎带",
    specModel: "尼龙",
    unit: "包",
    receivingCity: "廊坊",
    supplierId: 1,
    supplierName: "齐心",
    qty: 40,
    requestAmount: 999,
    awardAmount: 920,
    leadTimeDays: 3,
    requestStatus: "已中标",
    approvalStatus: "已通过",
    supplierConfirmStatus: "CONFIRMED",
    createdAt: "2026-05-28 17:21",
    submittedAt: "2026-05-28 17:21",
    approvedAt: "2026-05-29 09:48"
  }
];

function formatCell(value: unknown, column?: ProcurementBiColumnVO): string {
  if (value == null || value === "") {
    return "-";
  }
  if (!column || column.role !== "MEASURE") {
    return String(value);
  }
  const numberValue = Number(value || 0);
  if (column.semanticType === "MONEY") {
    return numberValue.toLocaleString("zh-CN", { style: "currency", currency: "CNY", maximumFractionDigits: 0 });
  }
  if (column.semanticType === "DAYS") {
    return `${numberValue.toLocaleString("zh-CN", { maximumFractionDigits: 1 })}天`;
  }
  if (column.unit) {
    return `${numberValue.toLocaleString("zh-CN", { maximumFractionDigits: 2 })}${column.unit}`;
  }
  return numberValue.toLocaleString("zh-CN", { maximumFractionDigits: 2 });
}
</script>

<style scoped>
.bi-shell {
  min-height: calc(100vh - 150px);
}

.bi-shell--editing {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 340px;
  gap: 14px;
  align-items: start;
}

.bi-board {
  min-width: 0;
  overflow-x: auto;
  border: 1px solid #dbe7f7;
  border-radius: 8px;
  background:
    linear-gradient(#e6f0ff 1px, transparent 1px),
    linear-gradient(90deg, #e6f0ff 1px, transparent 1px),
    #f4f7fb;
  background-size: 82px 82px;
  padding: 12px;
}

.bi-grid {
  min-height: calc(100vh - 180px);
  min-width: 1180px;
}

.bi-widget {
  position: relative;
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  height: 100%;
  min-width: 0;
  overflow: hidden;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  background: #fff;
  box-shadow: 0 10px 30px rgba(15, 23, 42, 0.06);
  transition: border-color 0.16s ease, box-shadow 0.16s ease, transform 0.16s ease;
}

.bi-widget--selected {
  border-color: #165dff;
  box-shadow: 0 0 0 2px #165dff inset, 0 16px 38px rgba(22, 93, 255, 0.14);
}

.widget-handle {
  position: absolute;
  z-index: 4;
  top: 6px;
  left: 50%;
  display: inline-flex;
  width: 36px;
  height: 18px;
  align-items: center;
  justify-content: center;
  border: 0;
  border-radius: 6px;
  background: transparent;
  cursor: grab;
  transform: translateX(-50%);
}

.widget-handle:active {
  cursor: grabbing;
}

.widget-handle span,
.widget-handle span::before,
.widget-handle span::after {
  display: block;
  width: 3px;
  height: 3px;
  border-radius: 50%;
  background: #7b8492;
  content: "";
}

.widget-handle span {
  position: relative;
}

.widget-handle span::before {
  position: absolute;
  left: -8px;
}

.widget-handle span::after {
  position: absolute;
  right: -8px;
}

.select-frame {
  pointer-events: none;
}

.select-dot {
  position: absolute;
  z-index: 3;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #165dff;
}

.select-dot--tl {
  top: -1px;
  left: -1px;
}

.select-dot--tr {
  top: -1px;
  right: -1px;
}

.select-dot--bl {
  bottom: -1px;
  left: -1px;
}

.select-dot--br {
  right: -1px;
  bottom: -1px;
}

.widget-head {
  display: flex;
  min-width: 0;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  padding: 18px 18px 10px;
}

.widget-head h3 {
  margin: 0;
  color: var(--app-text);
  font-size: 18px;
  line-height: 1.3;
}

.widget-head span {
  display: block;
  margin-top: 4px;
  color: var(--app-text-muted);
  font-size: 12px;
}

.widget-actions {
  display: flex;
  align-items: center;
  gap: 2px;
}

.widget-body {
  display: flex;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
  padding: 0 18px 18px;
}

.bi-widget--metric .widget-body {
  flex-direction: column;
  justify-content: center;
}

.bi-widget--table .widget-body,
.bi-widget--detail .widget-body {
  flex-direction: column;
}

.widget-table {
  flex: 1 1 auto;
  min-height: 0;
}

.widget-table :deep(.el-table__inner-wrapper) {
  min-height: 0;
}

.widget-table :deep(.el-table__body-wrapper) {
  min-height: 0;
  overflow-y: auto;
}

.metric-value {
  color: #30343b;
  overflow: hidden;
  font-size: clamp(28px, 2.4vw, 46px);
  font-weight: 800;
  line-height: 0.98;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.metric-note {
  margin-top: 10px;
  color: var(--app-text-muted);
  font-size: 13px;
}

.chart-host {
  width: 100%;
  height: 100%;
  min-height: 170px;
}

:global(.drill-dialog .el-dialog__body) {
  padding-top: 0;
}

.drill-head {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 4px;
}

.drill-head strong {
  color: var(--app-text);
  font-size: 18px;
}

.drill-head span {
  color: var(--app-text-muted);
  font-size: 12px;
}

.drill-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}

.drill-toolbar {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.drill-search {
  width: 280px;
}

.drill-column-select {
  width: 340px;
}

.bi-widget--theme-blue {
  border-top: 3px solid #165dff;
}

.bi-widget--theme-green {
  border-top: 3px solid #138a4d;
}

.bi-widget--theme-orange {
  border-top: 3px solid #c46b00;
}

.bi-widget--theme-slate {
  border-top: 3px solid #475569;
}

.bi-config {
  position: sticky;
  top: 14px;
  max-height: calc(100vh - 120px);
  overflow: auto;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  background: #fff;
  box-shadow: 0 16px 36px rgba(15, 23, 42, 0.08);
}

.config-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 16px 16px 8px;
}

.config-head strong {
  min-width: 0;
  overflow: hidden;
  color: var(--app-text);
  font-size: 18px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.config-tabs :deep(.el-tabs__header) {
  margin: 0;
  padding: 0 16px;
}

.config-tabs :deep(.el-tabs__content) {
  padding: 14px 16px 18px;
}

.config-section {
  display: grid;
  gap: 14px;
}

.config-section label,
.metric-config-list label {
  display: grid;
  gap: 6px;
  min-width: 0;
}

.config-section label > span,
.metric-config-list label > span {
  color: var(--app-text);
  font-size: 13px;
  font-weight: 700;
}

.config-section :deep(.el-select),
.config-section :deep(.el-input-number),
.config-section :deep(.config-date-range) {
  width: 100%;
}

.config-section :deep(.config-date-range) {
  flex: 0 0 auto;
}

.config-checks {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.metric-config-list {
  display: grid;
  gap: 12px;
}

.metric-config-list label {
  grid-template-columns: minmax(0, 1fr) 126px;
  align-items: center;
}

.metric-config-list :deep(.el-select) {
  width: 126px;
}

@media (max-width: 900px) {
  .drill-toolbar {
    flex-direction: column;
  }

  .drill-search,
  .drill-column-select {
    width: 100%;
  }

  .bi-shell--editing {
    grid-template-columns: 1fr;
  }

  .bi-config {
    position: static;
    max-height: none;
  }
}
</style>
