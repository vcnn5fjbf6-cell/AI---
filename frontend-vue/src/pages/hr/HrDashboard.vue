<template>
  <VnetPage title="">
    <div class="hr-ai-entry" role="button" tabindex="0" @click="goAi" @keyup.enter="goAi">
      <span class="hr-ai-entry-icon"><el-icon><ChatDotRound /></el-icon></span>
      <span class="hr-ai-entry-copy">
        <strong>AI 智能问答</strong>
        <span>考勤 / 请假 / 加班 / 成本智能问答，点击进入</span>
      </span>
      <el-icon class="hr-ai-entry-arrow"><ArrowRight /></el-icon>
    </div>
    <el-alert
      v-if="showPersonnelUpdateReminder"
      class="hr-update-reminder"
      type="warning"
      show-icon
      :closable="false"
      title="每栋楼长人员信息更新提醒"
      description="请各楼栋楼长在每月10日前完成本月入职、离职及人员信息更新。"
    />
    <div class="hr-metric-grid">
      <div v-for="card in metricCards" :key="card.label" class="hr-metric" :style="{ background: card.color }">
        <span>{{ card.label }}</span>
        <strong>{{ card.value }}</strong>
        <small v-if="card.note">{{ card.note }}</small>
      </div>
    </div>

    <div class="hr-panel-grid">
      <section class="hr-panel">
        <div class="hr-panel-head">
          <h3>请假扣款概览</h3>
          <span>本月合计 -¥{{ leaveDeductTotal }}</span>
        </div>
        <el-table :data="monthLeaves" size="small" empty-text="本月暂无请假扣款">
          <el-table-column prop="employeeName" label="姓名" min-width="90" />
          <el-table-column label="日期" min-width="150">
            <template #default="{ row }">{{ row.startDate }}{{ row.startDate !== row.endDate ? ` ~ ${row.endDate}` : "" }}</template>
          </el-table-column>
          <el-table-column prop="days" label="天数" width="70" />
          <el-table-column prop="lvRate" label="标准" min-width="130" />
          <el-table-column label="金额" width="90" align="right">
            <template #default="{ row }">-¥{{ (row.lvDeduct || 0).toFixed(0) }}</template>
          </el-table-column>
        </el-table>
      </section>
      <section class="hr-panel">
        <div class="hr-panel-head">
          <h3>加班费用概览</h3>
          <span>本月合计 +¥{{ overtimePayTotal }}</span>
        </div>
        <el-table :data="monthOvertime" size="small" empty-text="本月暂无加班费用">
          <el-table-column prop="employeeName" label="姓名" min-width="90" />
          <el-table-column prop="date" label="日期" min-width="110" />
          <el-table-column prop="hours" label="时长" width="70" />
          <el-table-column prop="otRate" label="单价" min-width="100" />
          <el-table-column label="金额" width="90" align="right">
            <template #default="{ row }">+¥{{ (row.otPay || 0).toFixed(0) }}</template>
          </el-table-column>
        </el-table>
      </section>
    </div>
    <section class="hr-panel">
      <div class="hr-panel-head">
        <h3>楼栋总人数概览</h3>
        <span>共 {{ totalEmployees }} 人</span>
      </div>
      <div class="hr-building-grid">
        <div v-for="item in buildingHeadcount" :key="item.building" class="hr-building-card">
          <span>{{ item.building }}</span>
          <small>{{ item.leader || "未配置楼长" }}</small>
          <strong>{{ item.count }}</strong>
        </div>
      </div>
    </section>

    <section class="hr-panel">
      <div class="hr-panel-head">
        <h3>👥 本月人员变化</h3>
        <span>入职 {{ monthChanges.filter((item) => item.type === "入职").length }} · 离职 {{ monthChanges.filter((item) => item.type === "离职").length }}</span>
      </div>
      <div class="hr-change-grid">
        <div v-for="item in buildingChanges" :key="item.building" class="hr-change-card">
          <strong>{{ item.building }}</strong>
          <span>新增 <b class="up">+{{ item.added }}</b></span>
          <span>减少 <b class="down">-{{ item.removed }}</b></span>
        </div>
      </div>
    </section>

    <section class="hr-panel">
      <div class="hr-panel-head">
        <h3>本月人员更新</h3>
        <span>按时 {{ monthUpdateLogs.length - lateUpdateCount }} 次 · 逾期 {{ lateUpdateCount }} 次（每月 10 号后更新计为逾期）</span>
      </div>
      <el-table :data="recentUpdateLogs" size="small" empty-text="本月暂无人员更新记录">
        <el-table-column prop="date" label="日期" width="110" />
        <el-table-column prop="operator" label="操作人" width="110" />
        <el-table-column label="操作" width="90">
          <template #default="{ row }">{{ actionLabel(row.action) }}</template>
        </el-table-column>
        <el-table-column prop="employeeName" label="人员" min-width="110" />
        <el-table-column prop="building" label="楼栋" width="110" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.late ? 'danger' : 'success'" size="small">{{ row.late ? "逾期更新" : "按时更新" }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </section>
  </VnetPage>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import VnetPage from "../../components/vnet/VnetPage.vue";
import { useHrStore, type HrSettlement } from "../../stores/hr";
import { formatMoney } from "./hr-utils";
import { useRouter } from "vue-router";
import { ArrowRight, ChatDotRound } from "@element-plus/icons-vue";

const hrStore = useHrStore();
const router = useRouter();

function goAi(): void {
  void router.push("/hr/ai");
}
const month = new Date().toISOString().slice(0, 7);
const settlement = ref<HrSettlement | null>(null);

const totalEmployees = computed(() => hrStore.employees.length);
const showPersonnelUpdateReminder = computed(() => new Date().getDate() <= 10);
const buildingLeaders = [
  { building: "百度M1", leaders: "张耀" },
  { building: "人寿", leaders: "陈瑶瑶" },
  { building: "B28", leaders: "张乐斌、乔涛、郭鑫存" },
  { building: "M3", leaders: "张正勤" },
  { building: "M5", leaders: "何金强、段宝光" },
  { building: "M6", leaders: "赵新、马金礼" },
  { building: "M6V&M5二三期", leaders: "张士令、刘燚" }
];

const buildingHeadcount = computed(() => {
  const map = new Map<string, { building: string; leader?: string; count: number }>();
  for (const item of buildingLeaders) {
    map.set(item.building, { building: item.building, leader: item.leaders, count: 0 });
  }
  for (const emp of hrStore.employees) {
    const building = emp.building || "未分配";
    const current = map.get(building) || { building, count: 0 };
    current.count += 1;
    map.set(building, current);
  }
  return Array.from(map.values()).sort((left, right) => left.building.localeCompare(right.building));
});
const metricCards = computed(() => {
  const pendingLeave = hrStore.leaveRequests.filter((item) => item.status === "pending").length;
  const pendingOt = hrStore.overtime.filter((item) => item.status === "pending").length;
  return [
    { label: "👥 总人数", value: String(hrStore.stats.totalEmployees), color: "linear-gradient(135deg,#3b82f6,#2563eb)" },
    { label: "💰 本月总费用", value: formatMoney(settlement.value?.totalSalary || 0), color: "linear-gradient(135deg,#10b981,#059669)" },
    { label: "⏰ 本月加班", value: `${hrStore.stats.monthOTHours.toFixed(1)}h`, note: `${monthOvertimeCount.value} 人次`, color: "linear-gradient(135deg,#f59e0b,#d97706)" },
    { label: "💵 加班费", value: `+${formatMoney(overtimePayTotal.value)}`, note: `待审批 ${pendingOt} 条`, color: "linear-gradient(135deg,#8b5cf6,#7c3aed)" },
    { label: "📉 扣除费", value: `-${formatMoney(leaveDeductTotal.value)}`, note: "请假扣款", color: "linear-gradient(135deg,#ef4444,#dc2626)" },
    { label: "⏳ 待审批", value: String(pendingLeave + pendingOt), note: `请假 ${pendingLeave} · 加班 ${pendingOt}`, color: "linear-gradient(135deg,#06b6d4,#0891b2)" }
  ];
});

const monthLeaves = computed(() => {
  const employees = hrStore.employees;
  return hrStore.leaveRequests
    .filter((item) => item.status === "approved" && item.startDate?.startsWith(month))
    .map((item) => ({
      ...item,
      employeeName: employees.find((emp) => emp.id === item.employeeId)?.name || "(已删除)",
      department: employees.find((emp) => emp.id === item.employeeId)?.department || "-"
    }));
});

const monthOvertime = computed(() => {
  const employees = hrStore.employees;
  return hrStore.overtime
    .filter((item) => item.status === "approved" && item.date?.startsWith(month))
    .map((item) => ({
      ...item,
      employeeName: employees.find((emp) => emp.id === item.employeeId)?.name || "(已删除)",
      department: employees.find((emp) => emp.id === item.employeeId)?.department || "-"
    }));
});

const monthOvertimeCount = computed(() => monthOvertime.value.length);
const overtimePayTotal = computed(() => monthOvertime.value.reduce((sum, item) => sum + (item.otPay || 0), 0));
const leaveDeductTotal = computed(() => monthLeaves.value.reduce((sum, item) => sum + (item.lvDeduct || 0), 0));

const monthChanges = computed(() => {
  const rows: Array<{ name: string; building: string; type: "入职" | "离职"; date: string }> = [];
  for (const emp of hrStore.employees) {
    if ((emp.joinDate || "").startsWith(month)) {
      rows.push({ name: emp.name, building: emp.building || "-", type: "入职", date: emp.joinDate || "" });
    }
    if ((emp.leaveDate || "").startsWith(month)) {
      rows.push({ name: emp.name, building: emp.building || "-", type: "离职", date: emp.leaveDate || "" });
    }
  }
  return rows;
});

const buildingChanges = computed(() => {
  const map = new Map<string, { building: string; added: number; removed: number }>();
  for (const item of monthChanges.value) {
    const current = map.get(item.building) || { building: item.building, added: 0, removed: 0 };
    if (item.type === "入职") current.added += 1;
    else current.removed += 1;
    map.set(item.building, current);
  }
  return Array.from(map.values());
});

const monthUpdateLogs = computed(() => hrStore.monthUpdateLogs);
const lateUpdateCount = computed(() => hrStore.lateUpdateCount);
const recentUpdateLogs = computed(() => monthUpdateLogs.value.slice(0, 20));

function actionLabel(action: string): string {
  return action === "add" ? "新增" : action === "remove" ? "减少" : "修改";
}

function refresh(): void {
  settlement.value = hrStore.getOrCalcSettlement(month);
}

onMounted(() => {
  hrStore.load();
  refresh();
});
</script>

<style scoped>
.hr-metric-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 12px;
}

.hr-metric {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
  padding: 16px;
  border-radius: 8px;
  color: #ffffff;
  box-shadow: 0 4px 12px rgba(15, 23, 42, 0.12);
}

.hr-metric span {
  font-size: 12px;
  opacity: 0.9;
}

.hr-metric strong {
  font-size: 24px;
  line-height: 1;
}

.hr-metric small {
  font-size: 11px;
  opacity: 0.85;
}

.hr-panel-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  grid-auto-rows: 1fr;
  gap: 16px;
}

.hr-panel-grid .hr-panel {
  display: flex;
  height: 100%;
  min-height: 240px;
  flex-direction: column;
}

.hr-panel-grid .el-table {
  flex: 1;
}

.hr-panel {
  overflow: hidden;
  border: 1px solid var(--app-line);
  border-radius: var(--app-radius);
  background: #ffffff;
  box-shadow: var(--app-shadow);
}

.hr-panel + .hr-panel,
.hr-panel-grid + .hr-panel {
  margin-top: 16px;
}

.hr-panel-grid > .hr-panel + .hr-panel {
  margin-top: 0;
}

.hr-panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 16px;
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

.hr-change-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 12px;
  padding: 16px;
}

.hr-change-card {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 14px;
  border: 1px solid var(--app-line);
  border-radius: var(--app-radius);
  background: var(--app-surface-muted);
}

.hr-change-card strong {
  font-size: 15px;
}

.hr-change-card span {
  color: var(--app-text-muted);
  font-size: 13px;
}

.hr-change-card b.up {
  color: var(--app-success);
}

.hr-change-card b.down {
  color: var(--app-danger);
}

.hr-building-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 12px;
  padding: 16px;
}

.hr-building-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 14px 16px;
  border: 1px solid var(--app-line);
  border-radius: var(--app-radius);
  background: var(--app-surface-muted);
}

.hr-building-card span {
  color: var(--app-text);
  font-size: 14px;
  font-weight: 700;
  font-size: 13px;
}

.hr-building-card small {
  color: var(--app-primary);
  font-size: 13px;
  font-weight: 700;
}

.hr-building-card strong {
  font-size: 26px;
  line-height: 1;
  color: var(--app-primary);
}

@media (max-width: 1100px) {
  .hr-metric-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .hr-panel-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .hr-metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
:deep(.vnet-page) {
  background:
    linear-gradient(rgba(13, 32, 56, 0.55), rgba(13, 32, 56, 0.55)),
    url("/hr-bg.png") center / cover no-repeat;
  padding: 16px;
  border-radius: 12px;
  min-height: calc(100vh - 120px);
}


.hr-update-reminder { margin-bottom: 16px; }
.hr-ai-entry {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  padding: 14px 16px;
  border: 1px solid rgba(31, 116, 255, 0.25);
  border-radius: var(--app-radius, 8px);
  background: linear-gradient(135deg, rgba(31, 116, 255, 0.08), rgba(139, 92, 246, 0.08));
  cursor: pointer;
  transition:
    border-color 0.18s ease,
    box-shadow 0.18s ease;
}
.hr-ai-entry:hover {
  border-color: var(--app-primary, #1260e8);
  box-shadow: 0 2px 10px rgba(18, 96, 232, 0.15);
}
.hr-ai-entry-icon {
  display: grid;
  flex: 0 0 auto;
  place-items: center;
  width: 38px;
  height: 38px;
  border-radius: 10px;
  background: #1f74ff;
  color: #ffffff;
  font-size: 20px;
}
.hr-ai-entry-copy {
  display: grid;
  min-width: 0;
  flex: 1;
  gap: 2px;
}
.hr-ai-entry-copy strong {
  color: var(--app-text, #1f2937);
  font-size: 15px;
}
.hr-ai-entry-copy span {
  color: var(--app-text-muted, #6b7280);
  font-size: 12px;
}
.hr-ai-entry-arrow {
  flex: 0 0 auto;
  color: #1f74ff;
  font-size: 16px;
}
:deep(.vnet-page-head) {
  display: none;
}
</style>
