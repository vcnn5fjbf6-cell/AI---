<template>
  <VnetPage title="EHS 运行设置">
    <template #actions><el-button :icon="Refresh" :loading="loading" @click="loadOverview">刷新</el-button></template>
    <div class="settings-grid">
      <VnetPanel title="业务模块运行状态">
        <el-table v-loading="loading" :data="overview?.modules || []" stripe>
          <el-table-column prop="moduleName" label="模块" min-width="180" />
          <el-table-column prop="totalCount" label="总记录" width="100" />
          <el-table-column prop="activeCount" label="处理中" width="100" />
          <el-table-column prop="alertCount" label="预警" width="90"><template #default="{ row }"><span :class="{ danger: row.alertCount > 0 }">{{ row.alertCount }}</span></template></el-table-column>
          <el-table-column label="数据状态" width="130"><template #default><el-tag type="success" effect="light">已接入</el-tag></template></el-table-column>
        </el-table>
      </VnetPanel>
      <VnetPanel title="公共能力">
        <div class="capability-list">
          <div><span>业务数据库</span><el-tag type="success">MySQL 正式台账</el-tag></div>
          <div><span>统一审批实例/待办/记录</span><el-tag type="success">已启用</el-tag></div>
          <div><span>飞书通知</span><el-tag :type="overview?.sharedNotificationAvailable ? 'success' : 'info'">{{ overview?.sharedNotificationAvailable ? '已启用' : '当前未启用' }}</el-tag></div>
          <div><span>通知失败记录</span><strong :class="{ danger: (overview?.notificationFailureCount || 0) > 0 }">{{ overview?.notificationFailureCount || 0 }}</strong></div>
          <div><span>知识库文档</span><strong>{{ overview?.knowledgeDocumentCount || 0 }}</strong></div>
        </div>
        <el-divider />
        <div class="settings-actions"><el-button :icon="User" @click="router.push('/platform/users')">配置 EHS 审批人</el-button><el-button :icon="Connection" @click="router.push('/platform/integration-settings')">查看飞书配置</el-button></div>
      </VnetPanel>
    </div>
  </VnetPage>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { Connection, Refresh, User } from "@element-plus/icons-vue";
import { getEhsOverviewApi } from "../../api/ehs";
import VnetPage from "../../components/vnet/VnetPage.vue";
import VnetPanel from "../../components/vnet/VnetPanel.vue";
import type { EhsOverviewVO } from "../../types/ehs";

const router = useRouter(); const loading = ref(false); const overview = ref<EhsOverviewVO>();
async function loadOverview() { loading.value = true; try { overview.value = await getEhsOverviewApi(); } finally { loading.value = false; } }
onMounted(() => void loadOverview());
</script>

<style scoped>
.settings-grid { display: grid; grid-template-columns: minmax(560px, 1.2fr) minmax(340px, .8fr); gap: 16px; }
.capability-list { display: grid; gap: 7px; } .capability-list > div { display: flex; align-items: center; justify-content: space-between; min-height: 44px; padding: 0 8px; border-bottom: 1px solid #e6edf7; }
.settings-actions { display: flex; flex-wrap: wrap; gap: 8px; } .danger { color: #d13c3c; font-weight: 600; }
@media (max-width: 950px) { .settings-grid { grid-template-columns: 1fr; } }
</style>
