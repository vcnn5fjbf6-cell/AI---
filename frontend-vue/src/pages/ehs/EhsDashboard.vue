<template>
  <VnetPage title="EHS 总览">
    <template #actions><el-button :icon="Refresh" :loading="loading" @click="loadOverview">刷新</el-button></template>
    <div class="metric-grid">
      <VnetMetricCard label="作业许可总票" :value="metric(overview?.permitTotalCount)" />
      <VnetMetricCard label="审批中许可" :value="metric(overview?.permitApprovingCount)" tone="warning" />
      <VnetMetricCard label="我的待审批" :value="metric(overview?.myPendingApprovalCount)" tone="danger" />
      <VnetMetricCard label="已关闭许可" :value="metric(overview?.permitCompletedCount)" tone="success" />
    </div>

    <div class="overview-grid">
      <VnetPanel title="业务模块">
        <div class="module-list">
          <button v-for="module in modules" :key="module.moduleCode" class="module-row" @click="router.push(module.path)">
            <el-icon class="module-icon"><component :is="module.icon" /></el-icon>
            <div class="module-copy"><strong>{{ module.moduleName }}</strong><small>共 {{ module.totalCount }} 条 · 处理中 {{ module.activeCount }} 条</small></div>
            <el-tag v-if="module.alertCount" type="danger" effect="light">{{ module.alertCount }} 项预警</el-tag><el-tag v-else type="success" effect="light">运行正常</el-tag>
            <el-icon class="module-arrow"><ArrowRight /></el-icon>
          </button>
        </div>
      </VnetPanel>
      <VnetPanel title="平台能力">
        <div class="capability-list">
          <div><span><el-icon><Connection /></el-icon>业务数据</span><el-tag type="success">MySQL 正式台账</el-tag></div>
          <div><span><el-icon><Stamp /></el-icon>统一审批</span><el-tag type="success">已启用</el-tag></div>
          <div><span><el-icon><Bell /></el-icon>飞书通知</span><el-tag :type="overview?.sharedNotificationAvailable ? 'success' : 'info'">{{ overview?.sharedNotificationAvailable ? '已启用' : '当前未启用' }}</el-tag></div>
          <div><span><el-icon><Reading /></el-icon>知识文档</span><strong>{{ metric(overview?.knowledgeDocumentCount) }}</strong></div>
          <div><span><el-icon><Warning /></el-icon>通知失败</span><strong :class="{ danger: (overview?.notificationFailureCount || 0) > 0 }">{{ metric(overview?.notificationFailureCount) }}</strong></div>
        </div>
        <p class="refresh-time">数据更新时间：{{ formatTime(overview?.refreshedAt) }}</p>
      </VnetPanel>
    </div>

    <VnetPanel title="当前风险队列">
      <el-table :data="overview?.riskQueue || []" stripe>
        <el-table-column prop="recordNo" label="编号" min-width="150" />
        <el-table-column prop="moduleType" label="模块" width="130"><template #default="{ row }">{{ moduleName(row.moduleType) }}</template></el-table-column>
        <el-table-column prop="title" label="风险事项" min-width="260" show-overflow-tooltip />
        <el-table-column prop="riskLevel" label="等级" width="100"><template #default="{ row }">{{ riskLabel(row.riskLevel) }}</template></el-table-column>
        <el-table-column prop="deadlineAt" label="截止时间" min-width="160"><template #default="{ row }">{{ formatTime(row.deadlineAt) }}</template></el-table-column>
        <el-table-column label="操作" width="100"><template #default="{ row }"><el-button link type="primary" @click="router.push(modulePath(row.moduleType))">打开台账</el-button></template></el-table-column>
        <template #empty><el-empty description="当前没有临期、逾期或库存预警" :image-size="64" /></template>
      </el-table>
    </VnetPanel>
  </VnetPage>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { ArrowRight, Bell, Connection, FirstAidKit, List, Reading, Refresh, Stamp, Tickets, Warning } from "@element-plus/icons-vue";
import { getEhsOverviewApi } from "../../api/ehs";
import VnetMetricCard from "../../components/vnet/VnetMetricCard.vue";
import VnetPage from "../../components/vnet/VnetPage.vue";
import VnetPanel from "../../components/vnet/VnetPanel.vue";
import type { EhsModuleCode, EhsOverviewVO, EhsRegistryModule } from "../../types/ehs";

const router = useRouter(); const loading = ref(false); const overview = ref<EhsOverviewVO>();
const moduleRoutes: Record<EhsModuleCode, { path: string; icon: typeof Warning }> = {
  HAZARD: { path: "/ehs/hazards", icon: Warning }, WASTE: { path: "/ehs/waste", icon: List }, PPE: { path: "/ehs/ppe", icon: FirstAidKit }, INCIDENT: { path: "/ehs/incidents", icon: Tickets }, WORK_PERMIT: { path: "/ehs/permits", icon: Stamp }
};
const modules = computed(() => (overview.value?.modules || []).map((module) => ({ ...module, ...moduleRoutes[module.moduleCode] })));
async function loadOverview() { loading.value = true; try { overview.value = await getEhsOverviewApi(); } finally { loading.value = false; } }
function metric(value?: number) { return typeof value === "number" ? String(value) : "--"; }
function formatTime(value?: string) { return value ? value.replace("T", " ").slice(0, 16) : "-"; }
function moduleName(type: EhsRegistryModule) { return ({ HAZARD: "隐患管理", WASTE: "环境与危废", PPE: "PPE与应急物资", INCIDENT: "事故事件" } as Record<string, string>)[type] || type; }
function modulePath(type: EhsRegistryModule) { return ({ HAZARD: "/ehs/hazards", WASTE: "/ehs/waste", PPE: "/ehs/ppe", INCIDENT: "/ehs/incidents" } as Record<string, string>)[type] || "/ehs/dashboard"; }
function riskLabel(value?: string) { return ({ LOW: "低", MEDIUM: "中", HIGH: "高", CRITICAL: "重大" } as Record<string, string>)[value || ""] || value || "-"; }
onMounted(() => void loadOverview());
</script>

<style scoped>
.metric-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; min-height: 108px; margin-bottom: 16px; }
.overview-grid { display: grid; grid-template-columns: minmax(540px, 1.2fr) minmax(320px, .8fr); gap: 16px; margin-bottom: 16px; }
.module-list, .capability-list { display: grid; gap: 9px; }
.module-row { display: grid; grid-template-columns: 38px minmax(0, 1fr) auto 18px; align-items: center; gap: 12px; width: 100%; min-height: 64px; padding: 10px 12px; border: 1px solid #dbe5f3; border-radius: 6px; background: #fff; color: #10233f; text-align: left; cursor: pointer; }
.module-row:hover { border-color: #2f6bff; background: #f8fbff; } .module-icon { display: inline-flex; align-items: center; justify-content: center; width: 36px; height: 36px; border-radius: 6px; background: #e9f0ff; color: #2f6bff; font-size: 18px; }
.module-copy { display: grid; min-width: 0; gap: 3px; } .module-copy small, .refresh-time { color: #71829d; font-size: 12px; } .module-arrow { color: #8da0bb; }
.capability-list > div { display: flex; align-items: center; justify-content: space-between; gap: 12px; min-height: 42px; border-bottom: 1px solid #e6edf7; } .capability-list span { display: inline-flex; align-items: center; gap: 8px; color: #314662; } .danger { color: #d13c3c; }
.refresh-time { margin: 12px 0 0; text-align: right; }
@media (max-width: 1000px) { .overview-grid { grid-template-columns: 1fr; } .metric-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 620px) { .metric-grid { grid-template-columns: 1fr; } .module-row { grid-template-columns: 36px minmax(0, 1fr) 18px; } .module-row .el-tag { grid-column: 2; justify-self: start; } }
</style>
