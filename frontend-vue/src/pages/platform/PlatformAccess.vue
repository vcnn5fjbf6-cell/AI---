<template>
  <VnetPage title="我的权限">
    <template #actions>
      <el-button type="primary" plain @click="openPasswordDialog">修改密码</el-button>
      <el-button :icon="Refresh" :loading="loading" @click="loadData">刷新</el-button>
    </template>

    <div class="metric-grid">
      <VnetMetricCard label="已开通入口" :value="String(enabledCount)" />
      <VnetMetricCard label="待审批申请" :value="String(pendingCount)" tone="warning" />
      <VnetMetricCard label="可申请入口" :value="String(availableCount)" tone="success" />
    </div>

    <el-alert
      v-if="passwordChangeRequired"
      class="password-alert"
      type="warning"
      :closable="false"
      show-icon
      :title="passwordPolicyTitle"
    />

    <div class="access-layout">
      <VnetPanel title="系统入口">
        <div v-loading="loading" class="system-list">
          <button
            v-for="item in systemCards"
            :key="item.appCode"
            type="button"
            class="system-row"
            :class="{ 'is-selected': selectedAppCode === item.appCode, 'is-enabled': item.enabled }"
            @click="selectSystem(item.appCode)"
          >
            <span class="system-icon" :class="`system-icon--${item.tone}`">
              <el-icon><component :is="item.icon" /></el-icon>
            </span>
            <span class="system-main">
              <strong>{{ item.appName }}</strong>
              <small>{{ item.summary }}</small>
            </span>
            <span class="system-side">
              <el-tag :type="item.tagType" effect="light">{{ item.statusText }}</el-tag>
              <el-button
                v-if="item.enabled"
                link
                type="primary"
                :icon="ArrowRight"
                @click.stop="goSystem(item.appCode)"
              >
                进入
              </el-button>
            </span>
          </button>
        </div>
      </VnetPanel>

      <VnetPanel title="入口申请">
        <div v-if="selectedSystem" class="request-pane">
          <div class="request-head">
            <span class="system-icon" :class="`system-icon--${selectedSystem.tone}`">
              <el-icon><component :is="selectedSystem.icon" /></el-icon>
            </span>
            <div>
              <h3>{{ selectedSystem.appName }}</h3>
              <p>{{ selectedSystem.summary }}</p>
            </div>
          </div>

          <el-alert
            v-if="selectedSystem.enabled"
            type="success"
            :closable="false"
            show-icon
            title="当前账号已开通该系统入口"
          />
          <el-alert
            v-else-if="selectedSystem.pending"
            type="warning"
            :closable="false"
            show-icon
            title="该系统已有待审批申请"
          />
          <el-alert
            v-else-if="!selectedSystem.canApply"
            type="info"
            :closable="false"
            show-icon
            title="当前暂无可提交的申请范围"
          />

          <el-form
            ref="formRef"
            :model="formModel"
            :rules="rules"
            label-position="top"
            class="request-form"
          >
            <el-form-item label="申请系统">
              <el-input :model-value="selectedSystem.appName" disabled />
            </el-form-item>
            <el-form-item label="申请原因" prop="reason">
              <el-input
                v-model="formModel.reason"
                type="textarea"
                :rows="5"
                maxlength="300"
                show-word-limit
                placeholder="说明需要开通该系统入口的业务原因"
                :disabled="!canSubmitSelected"
              />
            </el-form-item>
            <div class="request-meta">
              <span>审批通过后开通系统入口</span>
              <span>具体角色和管理权限由管理员在用户/角色后台维护</span>
            </div>
            <el-button
              type="primary"
              :icon="Promotion"
              :loading="submitting"
              :disabled="!canSubmitSelected"
              @click="submitRequest"
            >
              提交申请
            </el-button>
          </el-form>
        </div>
        <el-empty v-else description="暂无可申请系统" :image-size="76" />
      </VnetPanel>
    </div>

    <VnetPanel title="最近申请">
      <el-table :data="myAccess.recentRequests" stripe>
        <el-table-column prop="createdAt" label="申请时间" min-width="160" />
        <el-table-column prop="appName" label="系统" min-width="130" />
        <el-table-column prop="reason" label="申请原因" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">{{ row.reason || "-" }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="requestStatusType(row.status)" effect="light">{{ requestStatusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="reviewComment" label="审批意见" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.reviewComment || "-" }}</template>
        </el-table-column>
        <el-table-column prop="reviewedAt" label="处理时间" min-width="160">
          <template #default="{ row }">{{ formatDateTime(row.reviewedAt) || "-" }}</template>
        </el-table-column>
      </el-table>
    </VnetPanel>

    <PasswordChangeDialog v-model="passwordDialogVisible" :required="passwordChangeRequired" />
  </VnetPage>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage, type FormInstance, type FormRules } from "element-plus";
import { ArrowRight, Box, Connection, Cpu, FirstAidKit, Promotion, Refresh } from "@element-plus/icons-vue";
import PasswordChangeDialog from "../../components/account/PasswordChangeDialog.vue";
import VnetMetricCard from "../../components/vnet/VnetMetricCard.vue";
import VnetPage from "../../components/vnet/VnetPage.vue";
import VnetPanel from "../../components/vnet/VnetPanel.vue";
import {
  createPlatformPermissionRequestApi,
  getMyPlatformAccessApi,
  getPlatformAppsApi
} from "../../api/platform";
import { useAuthStore } from "../../stores/auth";
import type {
  PlatformAppVO,
  PlatformMyAccessVO,
  PlatformPermissionRequestForm,
  PlatformPermissionRequestVO,
  PlatformUnitVO
} from "../../types/platform";

type TagType = "success" | "warning" | "danger" | "info" | "primary";

interface SystemCard extends PlatformAppVO {
  summary: string;
  tone: "primary" | "success" | "warning";
  icon: typeof Box;
  enabled: boolean;
  pending: boolean;
  rejected: boolean;
  canApply: boolean;
  unit: PlatformUnitVO | null;
  statusText: string;
  tagType: TagType;
}

const router = useRouter();
const authStore = useAuthStore();
const apps = ref<PlatformAppVO[]>([]);
const myAccess = ref<PlatformMyAccessVO>({
  appCodes: [],
  units: [],
  recentRequests: []
});
const loading = ref(false);
const submitting = ref(false);
const selectedAppCode = ref("");
const formRef = ref<FormInstance>();
const passwordDialogVisible = ref(false);

const formModel = reactive<PlatformPermissionRequestForm>({
  unitId: null,
  appCode: "",
  roleCode: "",
  reason: ""
});

const rules: FormRules<PlatformPermissionRequestForm> = {
  reason: [{ required: true, message: "请填写申请原因", trigger: "blur" }]
};

const enabledCount = computed(() => systemCards.value.filter((item) => item.enabled).length);
const pendingCount = computed(() => myAccess.value.recentRequests.filter((item) => item.status === "PENDING").length);
const availableCount = computed(() => systemCards.value.filter((item) => item.canApply && !item.enabled && !item.pending).length);
const selectedSystem = computed(() => systemCards.value.find((item) => item.appCode === selectedAppCode.value) ?? systemCards.value[0]);
const canSubmitSelected = computed(() => Boolean(selectedSystem.value?.canApply && !selectedSystem.value.enabled && !selectedSystem.value.pending));
const passwordChangeRequired = computed(() => Boolean(authStore.user?.passwordExpired || authStore.user?.mustChangePassword === 1));
const passwordPolicyTitle = computed(() =>
  passwordChangeRequired.value
    ? "当前密码已超过 90 天或被要求修改，请先修改密码后继续使用系统。"
    : ""
);

const systemCards = computed<SystemCard[]>(() => {
  const orderedCodes = ["PROCUREMENT", "MAINTENANCE", "OUTSOURCING", "EHS", "PLATFORM"];
  return [...apps.value]
    .sort((left, right) => orderedCodes.indexOf(left.appCode) - orderedCodes.indexOf(right.appCode))
    .map((app) => {
      const latest = latestRequest(app.appCode);
      const enabled = myAccess.value.appCodes.includes(app.appCode);
      const pending = latest?.status === "PENDING";
      const rejected = latest?.status === "REJECTED";
      const unit = availableUnit(app.appCode);
      return {
        ...app,
        summary: systemSummary(app.appCode),
        tone: systemTone(app.appCode),
        icon: systemIcon(app.appCode),
        enabled,
        pending,
        rejected,
        canApply: Boolean(unit),
        unit,
        statusText: enabled ? "已开通" : pending ? "审批中" : rejected ? "已驳回" : "未开通",
        tagType: enabled ? "success" : pending ? "warning" : rejected ? "danger" : "info"
      };
    });
});

function latestRequest(appCode: string): PlatformPermissionRequestVO | null {
  return myAccess.value.recentRequests.find((item) => item.appCode === appCode) ?? null;
}

function availableUnit(appCode: string): PlatformUnitVO | null {
  return myAccess.value.units.find((unit) => unit.status === 1 && unit.appCodes.includes(appCode)) ?? null;
}

function selectSystem(appCode: string): void {
  selectedAppCode.value = appCode;
  syncFormForSelected();
}

function syncFormForSelected(): void {
  const selected = selectedSystem.value;
  formModel.appCode = selected?.appCode ?? "";
  formModel.unitId = selected?.unit?.id ?? null;
  formModel.roleCode = selected ? defaultRoleCode(selected.appCode) : "";
}

function systemSummary(appCode: string): string {
  if (appCode === "PROCUREMENT") return "采购申请、审批、供应商报价";
  if (appCode === "MAINTENANCE") return "维保台账、设备资产、巡检处理";
  if (appCode === "EHS") return "隐患、危废、物资、事故和作业许可";
  if (appCode === "OUTSOURCING") return "外包派单、入场、验收和独立结算";
  if (appCode === "PLATFORM") return "账号、角色、部门和系统设置";
  return "业务系统入口";
}

function systemTone(appCode: string): "primary" | "success" | "warning" {
  if (appCode === "PROCUREMENT") return "primary";
  if (appCode === "MAINTENANCE") return "success";
  if (appCode === "EHS") return "warning";
  if (appCode === "OUTSOURCING") return "primary";
  return "warning";
}

function systemIcon(appCode: string): typeof Box {
  if (appCode === "PROCUREMENT") return Box;
  if (appCode === "MAINTENANCE") return Cpu;
  if (appCode === "EHS") return FirstAidKit;
  if (appCode === "OUTSOURCING") return Promotion;
  return Connection;
}

function defaultRoleCode(appCode: string): string {
  if (appCode === "PROCUREMENT") return "PURCHASER";
  if (appCode === "MAINTENANCE") return "ENGINEER";
  if (appCode === "EHS") return "ENGINEER";
  if (appCode === "OUTSOURCING") return "ENGINEER";
  return "";
}

function requestStatusText(status: string): string {
  if (status === "APPROVED") return "已通过";
  if (status === "REJECTED") return "已驳回";
  return "待审批";
}

function requestStatusType(status: string): "success" | "danger" | "warning" {
  if (status === "APPROVED") return "success";
  if (status === "REJECTED") return "danger";
  return "warning";
}

async function goSystem(appCode: string): Promise<void> {
  const pathMap: Record<string, string> = {
    PROCUREMENT: "/procurement/dashboard",
    MAINTENANCE: "/maintenance/dashboard",
    EHS: "/ehs/dashboard",
    OUTSOURCING: "/engineering-outsourcing/workbench",
    PLATFORM: "/platform/admin"
  };
  await router.push(pathMap[appCode] || "/workbench");
}

async function loadData(): Promise<void> {
  loading.value = true;
  try {
    const [appData, accessData] = await Promise.all([getPlatformAppsApi(), getMyPlatformAccessApi()]);
    apps.value = appData;
    myAccess.value = accessData;
    if (!selectedAppCode.value || !apps.value.some((app) => app.appCode === selectedAppCode.value)) {
      selectedAppCode.value = apps.value.find((app) => !accessData.appCodes.includes(app.appCode))?.appCode ?? apps.value[0]?.appCode ?? "";
    }
    syncFormForSelected();
  } finally {
    loading.value = false;
  }
}

async function submitRequest(): Promise<void> {
  if (!formRef.value || !selectedSystem.value) return;
  syncFormForSelected();
  const valid = await formRef.value.validate().catch(() => false);
  if (!valid) return;
  if (!formModel.unitId) {
    ElMessage.warning("当前系统暂无可提交的申请范围");
    return;
  }
  submitting.value = true;
  try {
    await createPlatformPermissionRequestApi({
      unitId: formModel.unitId,
      appCode: formModel.appCode,
      roleCode: formModel.roleCode,
      reason: formModel.reason
    });
    ElMessage.success("入口申请已提交");
    formModel.reason = "";
    await loadData();
  } finally {
    submitting.value = false;
  }
}

function openPasswordDialog(): void {
  passwordDialogVisible.value = true;
}

function formatDateTime(value?: string | null): string {
  if (!value) return "";
  return value.replace("T", " ").slice(0, 16);
}

onMounted(() => {
  void loadData();
});
</script>

<style scoped>
.metric-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 14px;
}

.access-layout {
  display: grid;
  grid-template-columns: minmax(420px, 1.1fr) minmax(360px, 0.9fr);
  gap: 16px;
  margin-bottom: 16px;
}

.password-alert {
  margin: -2px 0 14px;
}

.password-form {
  padding: 4px 4px 0;
}

.password-hint {
  margin-left: 96px;
  color: #71829d;
  font-size: 12px;
  line-height: 1.6;
}

.system-list {
  display: grid;
  gap: 10px;
}

.system-row {
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  width: 100%;
  min-height: 74px;
  padding: 12px;
  border: 1px solid #dbe5f3;
  border-radius: 6px;
  background: #f8fbff;
  color: #10233f;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.18s ease, background-color 0.18s ease, box-shadow 0.18s ease;
}

.system-row:hover,
.system-row.is-selected {
  border-color: #2f6bff;
  background: #fff;
  box-shadow: 0 8px 20px rgba(27, 83, 204, 0.08);
}

.system-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  border-radius: 6px;
  background: #e9f0ff;
  color: #2f6bff;
  font-size: 20px;
}

.system-icon--success {
  background: #e7f7ef;
  color: #209b62;
}

.system-icon--warning {
  background: #fff3dd;
  color: #b66d00;
}

.system-main {
  display: grid;
  min-width: 0;
  gap: 5px;
}

.system-main strong {
  overflow: hidden;
  font-size: 15px;
  line-height: 1.3;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.system-main small,
.request-head p,
.request-meta {
  color: #71829d;
  font-size: 12px;
  line-height: 1.5;
}

.system-side {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
}

.request-pane {
  display: grid;
  gap: 14px;
}

.request-head {
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr);
  align-items: center;
  gap: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid #e6edf7;
}

.request-head h3 {
  margin: 0 0 4px;
  color: #10233f;
  font-size: 17px;
  line-height: 1.3;
}

.request-head p {
  margin: 0;
}

.request-form {
  display: grid;
  gap: 2px;
}

.request-meta {
  display: grid;
  gap: 3px;
  margin: -4px 0 8px;
}

@media (max-width: 1080px) {
  .access-layout {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .metric-grid {
    grid-template-columns: 1fr;
  }

  .system-row {
    grid-template-columns: 38px minmax(0, 1fr);
  }

  .system-side {
    grid-column: 2;
    justify-content: flex-start;
  }
}
</style>
