<template>
  <VnetPage title="绩效管理">
    <template #actions>
      <el-button type="primary" :icon="Plus" @click="openManualDialog">手动评分</el-button>
      <el-button :icon="Upload" @click="fileInput?.click()">上传考核表</el-button>
      <el-button :icon="Download" @click="downloadTemplate">下载模板</el-button>
      <input ref="fileInput" class="hr-hidden-input" type="file" accept=".csv,.xlsx,.xls,.txt" @change="handleImport" />
    </template>

    <div class="hr-toolbar">
      <el-date-picker v-model="monthFilter" type="month" value-format="YYYY-MM" clearable placeholder="选择月份" />
      <span>共 {{ visibleScores.length }} 条记录</span>
      <el-button v-if="monthFilter" link type="danger" @click="clearMonth">清空本月</el-button>
    </div>

    <section class="hr-panel">
      <div class="hr-panel-head">
        <h3>评分表</h3>
        <span>按总分降序展示前五名</span>
      </div>
      <el-table :data="visibleScores" size="small" empty-text="暂无评分数据">
        <el-table-column prop="name" label="姓名" min-width="100" />
        <el-table-column v-for="dim in dimensions" :key="dim" :prop="`scores.${dim}`" :label="dim" width="90" align="center">
          <template #default="{ row }">
            <span :class="scoreClass(row.scores[dim])">{{ row.scores[dim] || 0 }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="total" label="总分" width="90" align="center">
          <template #default="{ row }"><strong>{{ row.total || 0 }}</strong></template>
        </el-table-column>
        <el-table-column label="操作" width="130">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEditDialog(row)">编辑</el-button>
            <el-button link type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <section class="hr-panel">
      <div class="hr-panel-head"><h3>排名</h3><span>Top 5</span></div>
      <div class="hr-rank-list">
        <div v-for="(row, index) in topScores" :key="row.id" class="hr-rank-item" :style="rankStyle(index)">
          <span class="hr-rank-no">{{ medal(index) }}</span>
          <div class="hr-rank-main">
            <strong>{{ row.name }}</strong>
          </div>
          <span class="hr-rank-total">{{ row.total || 0 }}分</span>
        </div>
        <el-empty v-if="topScores.length === 0" description="暂无评分数据" />
      </div>
    </section>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑评分' : '手动评分'" width="460px">
      <el-form label-width="90px">
        <el-form-item label="员工" required>
          <el-select v-model="form.employeeId" style="width: 100%">
            <el-option v-for="emp in hrStore.employees" :key="emp.id" :label="emp.name" :value="emp.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="月份" required>
          <el-date-picker v-model="form.yearMonth" type="month" value-format="YYYY-MM" style="width: 100%" />
        </el-form-item>
        <el-form-item v-for="dim in manualDims" :key="dim" :label="dim">
          <el-input-number v-model="form.scores[dim]" :min="0" :max="100" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </VnetPage>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { Download, Plus, Upload } from "@element-plus/icons-vue";
import VnetPage from "../../components/vnet/VnetPage.vue";
import { useHrStore, type HrPerformanceScore } from "../../stores/hr";
import { downloadText, parseExcelOrCsvFile } from "./hr-utils";

const hrStore = useHrStore();
const monthFilter = ref("");
const fileInput = ref<HTMLInputElement>();
const dialogVisible = ref(false);
const editingId = ref<number | null>(null);
const manualDims = ["效率", "质量", "出勤", "协作"];

const form = reactive({
  employeeId: 0,
  yearMonth: new Date().toISOString().slice(0, 7),
  scores: { 效率: 85, 质量: 85, 出勤: 90, 协作: 80 } as Record<string, number>
});

const visibleScores = computed(() => {
  const scores = monthFilter.value
    ? hrStore.performance.filter((item) => item.yearMonth === monthFilter.value)
    : hrStore.performance;
  return scores.slice().sort((a, b) => (b.total || 0) - (a.total || 0));
});

const dimensions = computed(() => {
  const dims = new Set<string>();
  for (const score of hrStore.performance) {
    for (const key of Object.keys(score.scores || {})) dims.add(key);
  }
  return Array.from(dims);
});

const topScores = computed(() => visibleScores.value.slice(0, 5));

function scoreClass(value: number | undefined): string {
  const score = value || 0;
  if (score >= 90) return "hr-score-good";
  if (score >= 70) return "hr-score-warn";
  return "hr-score-bad";
}

function medal(index: number): string {
  return ["🥇", "🥈", "🥉", "4", "5"][index] || String(index + 1);
}

function rankStyle(index: number): Record<string, string> {
  const colors = ["#059669", "#d97706", "#92400e", "#64748b", "#64748b"];
  const backgrounds = ["#ecfdf5", "#fffbeb", "#fef3c7", "#f8fafc", "#f8fafc"];
  return { background: backgrounds[index], color: colors[index], borderColor: index < 3 ? colors[index] : "#f1f5f9" };
}

function openManualDialog(): void {
  if (hrStore.employees.length === 0) {
    ElMessage.warning("请先添加人员");
    return;
  }
  editingId.value = null;
  form.employeeId = hrStore.employees[0].id;
  form.yearMonth = new Date().toISOString().slice(0, 7);
  form.scores = { 效率: 85, 质量: 85, 出勤: 90, 协作: 80 };
  dialogVisible.value = true;
}

function openEditDialog(score: HrPerformanceScore): void {
  editingId.value = score.id;
  form.employeeId = score.employeeId;
  form.yearMonth = score.yearMonth;
  form.scores = { ...score.scores };
  dialogVisible.value = true;
}

function save(): void {
  const emp = hrStore.employees.find((item) => item.id === form.employeeId);
  if (!emp || !form.yearMonth) {
    ElMessage.warning("请选择员工和月份");
    return;
  }
  const total = Object.values(form.scores).reduce((sum, value) => sum + (Number(value) || 0), 0);
  const record: HrPerformanceScore = {
    id: editingId.value || Date.now(),
    employeeId: emp.id,
    name: emp.name,
    dept: emp.department || "",
    pos: emp.position || "",
    yearMonth: form.yearMonth,
    scores: { ...form.scores },
    total,
    createdAt: new Date().toISOString()
  };
  if (editingId.value) {
    const all = hrStore.performance.map((item) => (item.id === editingId.value ? record : item));
    hrStore.savePerformance(all);
    ElMessage.success("已更新");
  } else {
    const all = hrStore.performance.filter((item) => !(item.employeeId === emp.id && item.yearMonth === form.yearMonth));
    all.push(record);
    hrStore.savePerformance(all);
    ElMessage.success("已添加");
  }
  dialogVisible.value = false;
}

async function handleImport(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  if (!file) return;
  let rows: string[][];
  try {
    rows = await parseExcelOrCsvFile(file);
  } catch (error) {
    ElMessage.error(`文件解析失败：${error instanceof Error ? error.message : String(error)}`);
    input.value = "";
    return;
  }
  if (rows.length < 2) {
    ElMessage.warning("CSV 数据不足：第一行表头为 姓名,部门,考核维度...");
    input.value = "";
    return;
  }
  const header = rows[0];
  const dims = header.slice(2);
  const ym = monthFilter.value || new Date().toISOString().slice(0, 7);
  const added: HrPerformanceScore[] = [];
  let unmatched = 0;
  for (let i = 1; i < rows.length; i++) {
    const cols = rows[i];
    const name = (cols[0] || "").trim();
    if (!name) continue;
    const emp = hrStore.employees.find((item) => item.name === name);
    if (!emp) {
      unmatched += 1;
      continue;
    }
    const scores: Record<string, number> = {};
    let total = 0;
    for (let j = 0; j < dims.length; j++) {
      const value = parseFloat(cols[j + 2]) || 0;
      scores[dims[j].trim()] = value;
      total += value;
    }
    added.push({
      id: Date.now() + i,
      employeeId: emp.id,
      name: emp.name,
      dept: emp.department || "",
      pos: emp.position || "",
      yearMonth: ym,
      scores,
      total,
      createdAt: new Date().toISOString()
    });
  }
  if (added.length === 0) {
    ElMessage.warning(`未导入任何记录${unmatched ? `，${unmatched} 人未匹配` : ""}`);
    input.value = "";
    return;
  }
  const all = hrStore.performance.filter((item) => item.yearMonth !== ym);
  hrStore.savePerformance([...all, ...added]);
  ElMessage.success(`导入 ${added.length} 条${unmatched ? `，${unmatched} 人未匹配` : ""}`);
  input.value = "";
}

function downloadTemplate(): void {
  const text = "姓名,部门,效率,质量,出勤,协作\n示例员工,交付运维部,85,85,90,80\n";
  downloadText("绩效考核模板.csv", text, "text/csv;charset=utf-8");
}

async function remove(score: HrPerformanceScore): Promise<void> {
  try {
    await ElMessageBox.confirm("确定删除该评分记录？", "删除确认", { type: "warning" });
  } catch {
    return;
  }
  hrStore.savePerformance(hrStore.performance.filter((item) => item.id !== score.id));
  ElMessage.success("已删除");
}

function clearMonth(): void {
  if (!monthFilter.value) return;
  ElMessageBox.confirm(`确定清空 ${monthFilter.value} 的评分记录？`, "清空确认", { type: "warning" })
    .then(() => {
      hrStore.savePerformance(hrStore.performance.filter((item) => item.yearMonth !== monthFilter.value));
      ElMessage.success("已清空");
    })
    .catch(() => undefined);
}

onMounted(() => hrStore.load());
</script>

<style scoped>
.hr-hidden-input {
  display: none;
}

.hr-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  padding: 12px 16px;
  border: 1px solid var(--app-line);
  border-radius: var(--app-radius);
  background: #ffffff;
  box-shadow: var(--app-shadow);
}

.hr-toolbar span {
  color: var(--app-text-muted);
  font-size: 13px;
}

.hr-panel {
  overflow: hidden;
  margin-top: 16px;
  border: 1px solid var(--app-line);
  border-radius: var(--app-radius);
  background: #ffffff;
  box-shadow: var(--app-shadow);
}

.hr-panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid var(--app-line-soft);
}

.hr-panel-head h3 {
  margin: 0;
  font-size: 15px;
}

.hr-panel-head span {
  color: var(--app-text-muted);
  font-size: 12px;
}

.hr-score-good {
  color: var(--app-success);
  font-weight: 600;
}

.hr-score-warn {
  color: var(--app-warning);
  font-weight: 600;
}

.hr-score-bad {
  color: var(--app-danger);
  font-weight: 600;
}

.hr-rank-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 16px;
}

.hr-rank-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  border: 1px solid #f1f5f9;
  border-radius: 10px;
}

.hr-rank-no {
  display: grid;
  flex: 0 0 36px;
  width: 36px;
  height: 36px;
  place-items: center;
  border-radius: 8px;
  background: currentColor;
  color: #ffffff;
  font-size: 16px;
}

.hr-rank-main {
  display: flex;
  flex: 1;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
}

.hr-rank-main small {
  color: var(--app-text-muted);
  font-size: 12px;
}

.hr-rank-total {
  font-size: 18px;
  font-weight: 700;
}
:deep(.vnet-page) {
  background:
    linear-gradient(rgba(13, 32, 56, 0.55), rgba(13, 32, 56, 0.55)),
    url("/hr-bg.png") center / cover no-repeat;
  padding: 16px;
  border-radius: 12px;
  min-height: calc(100vh - 120px);
}

</style>
