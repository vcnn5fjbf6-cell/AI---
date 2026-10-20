<template>
  <VnetPage title="系统设置">
    <template #actions>
      <el-button :icon="Download" @click="exportData">导出数据</el-button>
      <el-button :icon="Upload" @click="importInput?.click()">导入数据</el-button>
      <el-button :loading="syncing" @click="syncPlatform">从平台同步人员</el-button>
      <el-button :icon="RefreshLeft" @click="recalcRates">重新计算费率</el-button>
      <el-button type="danger" plain :icon="RefreshLeft" @click="resetData">重置数据</el-button>
      <input ref="importInput" class="hr-hidden-input" type="file" accept=".json" @change="handleImport" />
    </template>

    <section class="hr-panel">
      <div class="hr-panel-head"><h3>数据看板</h3></div>
      <div class="hr-settings-counts">
        <div><span>人员总数</span><strong>{{ hrStore.employees.length }}</strong></div>
        <div><span>绩效记录</span><strong>{{ hrStore.performance.length }}</strong></div>
        <div><span>考勤</span><strong>{{ hrStore.attendance.length }}</strong></div>
        <div><span>请假</span><strong>{{ hrStore.leaveRequests.length }}</strong></div>
        <div><span>加班</span><strong>{{ hrStore.overtime.length }}</strong></div>
      </div>
    </section>

    <section class="hr-panel">
      <div class="hr-panel-head">
        <h3>飞书接入配置</h3>
        <el-button type="primary" :loading="feishuSaving" @click="saveFeishuConfig">保存飞书配置</el-button>
      </div>
      <el-table v-loading="feishuLoading" :data="feishuSettings" size="small" empty-text="暂无飞书配置项">
        <el-table-column prop="settingLabel" label="名称" min-width="180" />
        <el-table-column label="值" min-width="240">
          <template #default="{ row }">
            <el-switch v-if="row.valueType === 'BOOLEAN'" v-model="row.settingValue" active-value="true" inactive-value="false" active-text="启用" inactive-text="关闭" />
            <el-input v-else-if="row.valueType === 'PASSWORD'" v-model="row.settingValue" type="password" show-password />
            <el-input v-else v-model="row.settingValue" />
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="说明" min-width="260" show-overflow-tooltip />
      </el-table>
      <p class="hr-settings-note">飞书登录开关由后端环境变量 FEISHU_AUTH_ENABLED 控制（见项目根目录 feishu.env）；机器人、审批卡片等参数在此维护，保存后即时生效。</p>
    </section>

    <section class="hr-panel">
      <div class="hr-panel-head"><h3>飞书集成</h3></div>
      <div class="hr-settings-row">
        <el-input v-model="feishuWebhook" placeholder="飞书机器人 Webhook 地址" />
        <el-button type="primary" @click="saveFeishu">保存</el-button>
        <el-button @click="sendFeishu">发送测试</el-button>
      </div>
    </section>

    <section class="hr-panel">
      <div class="hr-panel-head"><h3>本地存储</h3></div>
      <pre class="hr-storage">{{ storageDetail }}</pre>
    </section>
  </VnetPage>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { Download, RefreshLeft, Upload } from "@element-plus/icons-vue";
import VnetPage from "../../components/vnet/VnetPage.vue";
import { useHrStore } from "../../stores/hr";
import { getUserPageApi } from "../../api/user";
import { getProcurementSettingsApi, updateProcurementSettingsApi } from "../../api/platform";
import type { ProcurementDepartmentLimitVO, ProcurementSettingItemVO } from "../../types/platform";
import { downloadText, readFileAsText } from "./hr-utils";

const hrStore = useHrStore();
const feishuWebhook = ref("");
const importInput = ref<HTMLInputElement>();
const syncing = ref(false);

const feishuSettingsRows = ref<ProcurementSettingItemVO[]>([]);
const departmentLimits = ref<ProcurementDepartmentLimitVO[]>([]);
const feishuLoading = ref(false);
const feishuSaving = ref(false);

const feishuSettings = computed(() => feishuSettingsRows.value.filter((item) => item.settingGroup === "FEISHU"));

async function loadFeishuConfig(): Promise<void> {
  feishuLoading.value = true;
  try {
    const data = await getProcurementSettingsApi();
    feishuSettingsRows.value = (data.settings || []).map((item) => ({ ...item, settingValue: String(item.settingValue ?? "") }));
    departmentLimits.value = (data.departmentLimits || []).map((item) => ({
      ...item,
      singleRequestLimit: Number(item.singleRequestLimit || 0),
      monthlyLimit: Number(item.monthlyLimit || 0),
      status: item.status ?? 1
    }));
  } catch {
    // 无权限或后端不可用时保持静默
  } finally {
    feishuLoading.value = false;
  }
}

async function saveFeishuConfig(): Promise<void> {
  feishuSaving.value = true;
  try {
    await updateProcurementSettingsApi({
      settings: feishuSettingsRows.value.map((item) => ({ ...item, settingValue: String(item.settingValue ?? "") })),
      departmentLimits: departmentLimits.value
    });
    ElMessage.success("飞书配置已保存");
    await loadFeishuConfig();
  } finally {
    feishuSaving.value = false;
  }
}

const storageDetail = computed(() => {
  const lines: string[] = [];
  for (let i = 0; i < localStorage.length; i++) {
    const key = localStorage.key(i);
    if (key?.startsWith("personnel_management_") || key === "personnel_performance" || key === "personnel_ai_config") {
      const value = localStorage.getItem(key) || "";
      lines.push(`${key} = ${value.length > 120 ? `${value.slice(0, 120)}...` : value}`);
    }
  }
  return lines.join("\n") || "(空)";
});

function exportData(): void {
  const snapshot = hrStore.exportSnapshot();
  downloadText(`hr-backup-${new Date().toISOString().slice(0, 10)}.json`, JSON.stringify(snapshot, null, 2), "application/json");
  ElMessage.success("已导出");
}

async function handleImport(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  if (!file) return;
  try {
    const data = JSON.parse(await readFileAsText(file)) as Record<string, unknown>;
    if (!hrStore.importSnapshot(data)) {
      ElMessage.error("格式错误");
    } else {
      ElMessage.success("已导入");
    }
  } catch {
    ElMessage.error("导入失败");
  }
  input.value = "";
}

function recalcRates(): void {
  hrStore.refreshRates();
  ElMessage.success("加班/请假费率已重新计算");
}

function resetData(): void {
  ElMessageBox.confirm("确定重置所有数据？此操作不可恢复。", "重置确认", { type: "warning" })
    .then(() => {
      hrStore.resetData();
      ElMessage.success("已重置");
    })
    .catch(() => undefined);
}

async function syncPlatform(): Promise<void> {
  syncing.value = true;
  try {
    const data = await getUserPageApi({ pageNo: 1, pageSize: 500 });
    const added = hrStore.mergePlatformUsers(data.records);
    ElMessage.success(`已同步 ${data.records.length} 人，新增 ${added} 人`);
  } catch {
    ElMessage.error("平台后端未连接，请先启动后端服务后再同步");
  } finally {
    syncing.value = false;
  }
}

function saveFeishu(): void {
  hrStore.saveFeishuWebhook(feishuWebhook.value.trim());
  ElMessage.success("飞书 Webhook 已保存");
}

async function sendFeishu(): Promise<void> {
  const url = feishuWebhook.value.trim();
  if (!url) {
    ElMessage.warning("请先配置 Webhook");
    return;
  }
  try {
    await fetch(url, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ msg_type: "text", content: { text: "测试消息：华北一部酒仙桥人力资源管理平台已接入飞书！" } })
    });
    ElMessage.success("测试消息已发送");
  } catch {
    ElMessage.error("发送失败");
  }
}

onMounted(() => {
  hrStore.load();
  feishuWebhook.value = hrStore.feishuWebhook;
  loadFeishuConfig();
});
</script>

<style scoped>
.hr-hidden-input {
  display: none;
}

.hr-panel {
  overflow: hidden;
  margin-bottom: 16px;
  border: 1px solid var(--app-line);
  border-radius: var(--app-radius);
  background: #ffffff;
  box-shadow: var(--app-shadow);
}

.hr-panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--app-line-soft);
}

.hr-panel-head h3 {
  margin: 0;
  font-size: 15px;
}

.hr-filter-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.hr-settings-counts {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
  padding: 16px;
}

.hr-settings-counts div {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 14px;
  border-radius: var(--app-radius);
  background: var(--app-surface-muted);
}

.hr-settings-counts span {
  color: var(--app-text-muted);
  font-size: 12px;
}

.hr-settings-counts strong {
  font-size: 22px;
}

.hr-dept-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 16px;
}

.hr-settings-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 16px;
}

.hr-settings-row .el-input {
  flex: 1;
}

.hr-storage {
  max-height: 220px;
  margin: 0;
  padding: 16px;
  overflow: auto;
  color: var(--app-text-muted);
  font-size: 12px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-all;
}

@media (max-width: 760px) {
  .hr-settings-counts {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .hr-settings-row {
    flex-wrap: wrap;
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

.hr-settings-note {
  margin: 10px 0 0;
  color: var(--app-text-muted, #6b7280);
  font-size: 12px;
  line-height: 1.6;
}
</style>
