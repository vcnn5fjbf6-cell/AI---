<template>
  <VnetPage :title="config.title">
    <template #actions>
      <input ref="fileInput" class="hidden-input" type="file" accept=".xlsx,.xls" @change="handleImport" />
      <el-button :icon="Download" @click="downloadTemplate">导入模板</el-button>
      <el-button :icon="Upload" :loading="importing" @click="fileInput?.click()">批量导入</el-button>
      <el-button :icon="Download" @click="exportRecords">导出台账</el-button>
      <el-button type="primary" :icon="Plus" @click="openCreate">新增{{ config.noun }}</el-button>
    </template>

    <div class="metric-grid">
      <VnetMetricCard :label="config.totalLabel" :value="String(stats.totalCount)" />
      <VnetMetricCard :label="config.activeLabel" :value="String(stats.activeCount)" tone="warning" />
      <VnetMetricCard :label="config.closedLabel" :value="String(stats.completedCount)" tone="success" />
      <VnetMetricCard label="当前预警" :value="String(stats.alertCount)" tone="danger" />
    </div>

    <VnetPanel :title="config.panelTitle">
      <el-form class="filter-bar" :inline="true" @submit.prevent="search">
        <el-form-item label="关键字">
          <el-input v-model="query.keyword" :placeholder="config.keywordPlaceholder" clearable @keyup.enter="search" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部状态" clearable>
            <el-option v-for="option in config.statuses" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="等级" v-if="config.showRiskLevel">
          <el-select v-model="query.riskLevel" placeholder="全部等级" clearable>
            <el-option label="低" value="LOW" />
            <el-option label="中" value="MEDIUM" />
            <el-option label="高" value="HIGH" />
            <el-option label="重大" value="CRITICAL" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="search">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="page.records" stripe @row-dblclick="openDetail">
        <el-table-column prop="recordNo" label="编号" min-width="150" fixed="left" />
        <el-table-column prop="title" :label="config.titleLabel" min-width="230" show-overflow-tooltip />
        <el-table-column prop="category" label="分类" min-width="130" show-overflow-tooltip />
        <el-table-column v-for="column in config.columns" :key="column.prop" :label="column.label" :min-width="column.width">
          <template #default="{ row }">{{ displayColumn(row, column.prop) }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" effect="light">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="removeRecord(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty><el-empty :description="`暂无${config.noun}记录`" :image-size="72" /></template>
      </el-table>
      <div class="pagination-row">
        <el-pagination
          v-model:current-page="query.pageNo"
          v-model:page-size="query.pageSize"
          :total="page.total"
          layout="total, sizes, prev, pager, next"
          @current-change="loadRecords"
          @size-change="search"
        />
      </div>
    </VnetPanel>

    <el-dialog v-model="editorVisible" :title="editingId ? `编辑${config.noun}` : `新增${config.noun}`" width="760px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px" class="record-form">
        <el-row :gutter="16">
          <el-col :span="24"><el-form-item :label="config.titleLabel" prop="title"><el-input v-model="form.title" type="textarea" :rows="2" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="分类"><el-input v-model="form.category" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="子类型"><el-input v-model="form.subType" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="楼栋"><el-input v-model="form.building" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="位置"><el-input v-model="form.location" /></el-form-item></el-col>
          <el-col v-if="config.showRiskLevel" :span="12">
            <el-form-item label="等级">
              <el-select v-model="form.riskLevel" clearable><el-option label="低" value="LOW" /><el-option label="中" value="MEDIUM" /><el-option label="高" value="HIGH" /><el-option label="重大" value="CRITICAL" /></el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12"><el-form-item label="状态" prop="status"><el-select v-model="form.status"><el-option v-for="option in config.statuses" :key="option.value" :label="option.label" :value="option.value" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="config.ownerLabel"><el-input v-model="form.ownerName" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="config.handlerLabel"><el-input v-model="form.handlerName" /></el-form-item></el-col>
          <el-col v-if="config.showQuantity" :span="12"><el-form-item label="数量"><el-input-number v-model="form.quantity" :min="0" :precision="3" controls-position="right" /></el-form-item></el-col>
          <el-col v-if="config.showQuantity" :span="12"><el-form-item label="单位"><el-input v-model="form.unit" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="config.occurredLabel"><el-date-picker v-model="form.occurredAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="config.deadlineLabel"><el-date-picker v-model="form.deadlineAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item></el-col>
          <el-col v-for="field in config.detailFields" :key="field.key" :span="field.full ? 24 : 12">
            <el-form-item :label="field.label">
              <el-input v-if="field.type === 'textarea'" v-model="form.details[field.key]" type="textarea" :rows="2" />
              <el-date-picker v-else-if="field.type === 'date'" v-model="form.details[field.key]" type="date" value-format="YYYY-MM-DD" />
              <el-input-number v-else-if="field.type === 'number'" v-model="form.details[field.key]" :min="0" :precision="3" controls-position="right" />
              <el-input v-else v-model="form.details[field.key]" />
            </el-form-item>
          </el-col>
          <el-col :span="24"><el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer><el-button @click="editorVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="saveRecord">保存</el-button></template>
    </el-dialog>

    <el-drawer v-model="detailVisible" :title="detail?.recordNo || `${config.noun}详情`" size="620px">
      <template v-if="detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item :label="config.titleLabel" :span="2">{{ detail.title }}</el-descriptions-item>
          <el-descriptions-item label="分类">{{ detail.category || "-" }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ statusLabel(detail.status) }}</el-descriptions-item>
          <el-descriptions-item label="楼栋">{{ detail.building || "-" }}</el-descriptions-item>
          <el-descriptions-item label="位置">{{ detail.location || "-" }}</el-descriptions-item>
          <el-descriptions-item :label="config.ownerLabel">{{ detail.ownerName || "-" }}</el-descriptions-item>
          <el-descriptions-item :label="config.handlerLabel">{{ detail.handlerName || "-" }}</el-descriptions-item>
          <el-descriptions-item :label="config.occurredLabel">{{ formatTime(detail.occurredAt) }}</el-descriptions-item>
          <el-descriptions-item :label="config.deadlineLabel">{{ formatTime(detail.deadlineAt) }}</el-descriptions-item>
          <el-descriptions-item v-for="field in config.detailFields" :key="field.key" :label="field.label" :span="field.full ? 2 : 1">
            {{ detail.details?.[field.key] ?? "-" }}
          </el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">{{ detail.remark || "-" }}</el-descriptions-item>
        </el-descriptions>
      </template>
    </el-drawer>
  </VnetPage>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from "vue";
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from "element-plus";
import { Download, Plus, Search, Upload } from "@element-plus/icons-vue";
import {
  createEhsRecordApi,
  deleteEhsRecordApi,
  downloadEhsImportTemplateApi,
  exportEhsRecordsApi,
  getEhsRecordStatsApi,
  importEhsRecordsApi,
  pageEhsRecordsApi,
  updateEhsRecordApi
} from "../../api/ehs";
import VnetMetricCard from "../../components/vnet/VnetMetricCard.vue";
import VnetPage from "../../components/vnet/VnetPage.vue";
import VnetPanel from "../../components/vnet/VnetPanel.vue";
import type { EhsRegistryKey, EhsRegistryModule, EhsRegistryRecord, EhsRegistrySavePayload, EhsRegistryStats } from "../../types/ehs";

type DetailField = { key: string; label: string; type?: "text" | "textarea" | "number" | "date"; full?: boolean };
type RegistryConfig = {
  moduleType: EhsRegistryModule; title: string; noun: string; panelTitle: string; titleLabel: string;
  totalLabel: string; activeLabel: string; closedLabel: string; keywordPlaceholder: string;
  ownerLabel: string; handlerLabel: string; occurredLabel: string; deadlineLabel: string;
  showRiskLevel: boolean; showQuantity: boolean;
  statuses: Array<{ value: string; label: string }>;
  columns: Array<{ prop: string; label: string; width: number }>;
  detailFields: DetailField[];
};

const props = defineProps<{ moduleKey: EhsRegistryKey }>();
const configs: Record<EhsRegistryKey, RegistryConfig> = {
  hazards: {
    moduleType: "HAZARD", title: "隐患管理", noun: "隐患", panelTitle: "隐患整改台账", titleLabel: "隐患描述",
    totalLabel: "隐患总数", activeLabel: "未闭环", closedLabel: "已闭环", keywordPlaceholder: "编号 / 隐患描述 / 区域 / 责任人",
    ownerLabel: "整改责任人", handlerLabel: "检查人", occurredLabel: "发现时间", deadlineLabel: "整改期限", showRiskLevel: true, showQuantity: false,
    statuses: [{ value: "OPEN", label: "待整改" }, { value: "RECTIFYING", label: "整改中" }, { value: "VERIFYING", label: "待验收" }, { value: "CLOSED", label: "已闭环" }],
    columns: [{ prop: "building", label: "楼栋", width: 120 }, { prop: "location", label: "区域", width: 150 }, { prop: "riskLevel", label: "风险等级", width: 100 }, { prop: "ownerName", label: "责任人", width: 110 }, { prop: "deadlineAt", label: "整改期限", width: 160 }],
    detailFields: [{ key: "source", label: "隐患来源" }, { key: "rectificationMeasures", label: "整改措施", type: "textarea", full: true }, { key: "verificationResult", label: "验收结论", type: "textarea", full: true }]
  },
  waste: {
    moduleType: "WASTE", title: "环境与危废", noun: "危废记录", panelTitle: "危废产生与转运台账", titleLabel: "废物名称",
    totalLabel: "台账记录", activeLabel: "未完成转运", closedLabel: "已转运", keywordPlaceholder: "编号 / 废物名称 / 产生区域 / 经办人",
    ownerLabel: "管理责任人", handlerLabel: "经办人", occurredLabel: "产生时间", deadlineLabel: "计划转运", showRiskLevel: false, showQuantity: true,
    statuses: [{ value: "STORED", label: "暂存中" }, { value: "PENDING_TRANSFER", label: "待转运" }, { value: "TRANSFERRED", label: "已转运" }],
    columns: [{ prop: "details.wasteCode", label: "废物代码", width: 130 }, { prop: "quantity", label: "库存数量", width: 110 }, { prop: "unit", label: "单位", width: 70 }, { prop: "location", label: "暂存位置", width: 150 }, { prop: "handlerName", label: "经办人", width: 110 }],
    detailFields: [{ key: "wasteCode", label: "废物代码" }, { key: "sourceProcess", label: "产生工序" }, { key: "generatedKg", label: "累计产生(kg)", type: "number" }, { key: "transferredKg", label: "已转运(kg)", type: "number" }, { key: "disposalUnit", label: "处置单位" }, { key: "manifestNo", label: "联单编号" }]
  },
  ppe: {
    moduleType: "PPE", title: "PPE 与应急物资", noun: "物资", panelTitle: "PPE 与应急物资台账", titleLabel: "物资名称",
    totalLabel: "物资种类", activeLabel: "需关注", closedLabel: "库存正常", keywordPlaceholder: "编号 / 物资名称 / 存放位置 / 管理人",
    ownerLabel: "管理人", handlerLabel: "领用人", occurredLabel: "入库/领用时间", deadlineLabel: "更换/检查日期", showRiskLevel: false, showQuantity: true,
    statuses: [{ value: "NORMAL", label: "正常" }, { value: "LOW_STOCK", label: "库存不足" }, { value: "EXPIRING", label: "即将到期" }],
    columns: [{ prop: "subType", label: "物资类型", width: 120 }, { prop: "quantity", label: "当前库存", width: 100 }, { prop: "unit", label: "单位", width: 70 }, { prop: "location", label: "存放位置", width: 150 }, { prop: "ownerName", label: "管理人", width: 110 }],
    detailFields: [{ key: "specification", label: "规格型号" }, { key: "minStock", label: "最低库存", type: "number" }, { key: "expiryDate", label: "有效期", type: "date" }, { key: "replacementDate", label: "更换日期", type: "date" }]
  },
  incidents: {
    moduleType: "INCIDENT", title: "事故事件", noun: "事故事件", panelTitle: "事故事件调查与整改台账", titleLabel: "事件概述",
    totalLabel: "事件总数", activeLabel: "处理中", closedLabel: "已结案", keywordPlaceholder: "编号 / 事件概述 / 地点 / 报告人",
    ownerLabel: "整改责任人", handlerLabel: "报告人", occurredLabel: "发生时间", deadlineLabel: "整改期限", showRiskLevel: true, showQuantity: false,
    statuses: [{ value: "OPEN", label: "待处理" }, { value: "INVESTIGATING", label: "调查中" }, { value: "RECTIFYING", label: "整改中" }, { value: "CLOSED", label: "已结案" }],
    columns: [{ prop: "category", label: "事件类别", width: 120 }, { prop: "riskLevel", label: "事件等级", width: 100 }, { prop: "location", label: "发生地点", width: 150 }, { prop: "handlerName", label: "报告人", width: 110 }, { prop: "occurredAt", label: "发生时间", width: 160 }],
    detailFields: [{ key: "immediateAction", label: "即时处置", type: "textarea", full: true }, { key: "rootCause", label: "原因分析", type: "textarea", full: true }, { key: "rectificationProgress", label: "整改进展", type: "textarea", full: true }]
  }
};

const config = computed(() => configs[props.moduleKey]);
const loading = ref(false); const saving = ref(false); const importing = ref(false);
const editorVisible = ref(false); const detailVisible = ref(false); const editingId = ref<number>();
const detail = ref<EhsRegistryRecord>(); const fileInput = ref<HTMLInputElement>(); const formRef = ref<FormInstance>();
const query = reactive({ pageNo: 1, pageSize: 10, keyword: "", status: "", riskLevel: "" });
const page = reactive({ total: 0, records: [] as EhsRegistryRecord[] });
const stats = reactive<EhsRegistryStats>({ totalCount: 0, activeCount: 0, completedCount: 0, alertCount: 0, statusCounts: {}, categoryCounts: {} });
const form = reactive<EhsRegistrySavePayload>(emptyForm("HAZARD"));
const rules: FormRules = { title: [{ required: true, message: "请填写名称", trigger: "blur" }], status: [{ required: true, message: "请选择状态", trigger: "change" }] };

function emptyForm(moduleType: EhsRegistryModule): EhsRegistrySavePayload {
  const cfg = Object.values(configs).find((item) => item.moduleType === moduleType)!;
  return { moduleType, title: "", status: cfg.statuses[0].value, details: {} };
}
function resetForm() { Object.assign(form, emptyForm(config.value.moduleType)); form.details = {}; editingId.value = undefined; }
function openCreate() { resetForm(); editorVisible.value = true; }
function openEdit(row: EhsRegistryRecord) {
  editingId.value = row.id;
  Object.assign(form, { ...row, details: { ...(row.details || {}) } });
  editorVisible.value = true;
}
function openDetail(row: EhsRegistryRecord) { detail.value = row; detailVisible.value = true; }

async function loadRecords() {
  loading.value = true;
  try {
    const [result, metric] = await Promise.all([
      pageEhsRecordsApi({ ...query, moduleType: config.value.moduleType }),
      getEhsRecordStatsApi(config.value.moduleType)
    ]);
    page.total = result.total; page.records = result.records; Object.assign(stats, metric);
  } finally { loading.value = false; }
}
function search() { query.pageNo = 1; void loadRecords(); }
function resetFilters() { query.keyword = ""; query.status = ""; query.riskLevel = ""; search(); }

async function saveRecord() {
  await formRef.value?.validate(); saving.value = true;
  try {
    const payload = JSON.parse(JSON.stringify(form)) as EhsRegistrySavePayload;
    if (editingId.value) await updateEhsRecordApi(editingId.value, payload); else await createEhsRecordApi(payload);
    ElMessage.success("已保存"); editorVisible.value = false; await loadRecords();
  } finally { saving.value = false; }
}
async function removeRecord(row: EhsRegistryRecord) {
  await ElMessageBox.confirm(`确认删除 ${row.recordNo}？`, "删除记录", { type: "warning" });
  await deleteEhsRecordApi(row.id); ElMessage.success("已删除"); await loadRecords();
}
async function handleImport(event: Event) {
  const input = event.target as HTMLInputElement; const file = input.files?.[0]; input.value = ""; if (!file) return;
  importing.value = true;
  try { const count = await importEhsRecordsApi(config.value.moduleType, file); ElMessage.success(`已导入 ${count} 条记录`); await loadRecords(); }
  finally { importing.value = false; }
}
async function downloadTemplate() { downloadBlob(await downloadEhsImportTemplateApi(config.value.moduleType), `${config.value.title}-导入模板.xlsx`); }
async function exportRecords() { downloadBlob(await exportEhsRecordsApi(config.value.moduleType), `${config.value.title}-台账.xlsx`); }
function downloadBlob(blob: Blob, name: string) { const url = URL.createObjectURL(blob); const a = document.createElement("a"); a.href = url; a.download = name; a.click(); URL.revokeObjectURL(url); }

function displayColumn(row: EhsRegistryRecord, prop: string): string {
  const value = prop.startsWith("details.") ? row.details?.[prop.slice(8)] : (row as unknown as Record<string, unknown>)[prop];
  if (prop.endsWith("At")) return formatTime(value as string); if (prop === "riskLevel") return riskLabel(String(value || ""));
  return value === undefined || value === null || value === "" ? "-" : String(value);
}
function statusLabel(value: string) { return config.value.statuses.find((item) => item.value === value)?.label || value; }
function statusType(value: string): "success" | "warning" | "danger" | "info" { if (["CLOSED", "TRANSFERRED", "NORMAL"].includes(value)) return "success"; if (["LOW_STOCK", "EXPIRING", "PENDING_TRANSFER", "OPEN"].includes(value)) return "danger"; return "warning"; }
function riskLabel(value: string) { return ({ LOW: "低", MEDIUM: "中", HIGH: "高", CRITICAL: "重大" } as Record<string, string>)[value] || value || "-"; }
function formatTime(value?: string) { return value ? value.replace("T", " ").slice(0, 16) : "-"; }

watch(() => props.moduleKey, () => { resetForm(); query.pageNo = 1; void loadRecords(); });
onMounted(() => void loadRecords());
</script>

<style scoped>
.hidden-input { display: none; }
.metric-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; min-height: 108px; margin-bottom: 16px; }
.filter-bar { display: flex; flex-wrap: wrap; align-items: flex-end; gap: 0 8px; padding: 12px 12px 0; margin-bottom: 14px; border: 1px solid #e1e8f2; border-radius: 6px; background: #f8fafc; }
.filter-bar :deep(.el-input) { width: min(280px, 60vw); }
.filter-bar :deep(.el-select) { width: 160px; }
.pagination-row { display: flex; justify-content: flex-end; padding-top: 14px; }
.record-form :deep(.el-select), .record-form :deep(.el-date-editor), .record-form :deep(.el-input-number) { width: 100%; }
@media (max-width: 900px) { .metric-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 620px) { .metric-grid { grid-template-columns: 1fr; } .filter-bar { display: block; } .filter-bar :deep(.el-input), .filter-bar :deep(.el-select) { width: 100%; } }
</style>
