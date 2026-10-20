<template>
  <VnetPage
    eyebrow="Platform Governance"
    title="平台权限管理"
    description="统一维护哪些账号能登录、能进入哪些系统、哪个单元能申请哪些系统权限，以及权限申请审批。"
  >
    <div class="metric-grid">
      <VnetMetricCard label="平台账号" :value="String(summary.totalUsers)" note="系统用户总数" />
      <VnetMetricCard label="允许登录" :value="String(summary.loginEnabledUsers)" note="由账号状态控制" tone="success" />
      <VnetMetricCard label="系统授权" :value="String(summary.accessBindings)" note="用户和系统入口绑定数" tone="warning" />
      <VnetMetricCard label="待审批" :value="String(summary.pendingRequests)" note="权限申请待处理" tone="danger" />
    </div>

    <VnetPanel>
      <el-tabs v-model="activeTab">
        <el-tab-pane label="账号准入" name="users">
          <el-table v-loading="loading" :data="userAccessList" stripe>
            <el-table-column label="账号" min-width="220">
              <template #default="{ row }">
                <div class="identity-cell">
                  <strong>{{ row.realName }}</strong>
                  <span>{{ row.username }} / {{ row.email || row.mobile || "未绑定飞书信息" }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="deptName" label="部门/单元" min-width="140" />
            <el-table-column label="允许登录" width="120">
              <template #default="{ row }">
                <el-switch v-model="row.loginEnabled" :active-value="1" :inactive-value="0" />
              </template>
            </el-table-column>
            <el-table-column label="可进入系统" min-width="340">
              <template #default="{ row }">
                <el-checkbox-group v-model="row.appCodes">
                  <el-checkbox v-for="app in apps" :key="app.appCode" :label="app.appCode">
                    {{ app.appName }}
                  </el-checkbox>
                </el-checkbox-group>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="100" fixed="right">
              <template #default="{ row }">
                <el-button type="primary" link @click="saveUserAccess(row)">保存</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="单元申请范围" name="units">
          <div class="table-toolbar">
            <div>
              <h3>单元配置</h3>
              <p>控制某个组织单元允许申请哪些业务系统。</p>
            </div>
            <el-button type="primary" @click="openUnitDialog()">新增单元</el-button>
          </div>
          <el-table :data="units" stripe>
            <el-table-column prop="unitCode" label="单元编码" width="140" />
            <el-table-column prop="unitName" label="单元名称" min-width="160" />
            <el-table-column label="允许申请系统" min-width="260">
              <template #default="{ row }">
                <el-tag v-for="code in row.appCodes" :key="code" class="tag-gap" effect="light">
                  {{ appName(code) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'success' : 'info'" effect="light">
                  {{ row.status === 1 ? "启用" : "停用" }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="140" fixed="right">
              <template #default="{ row }">
                <el-button type="primary" link @click="openUnitDialog(row)">编辑</el-button>
                <el-button type="danger" link @click="deleteUnit(row.id)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="权限审批" name="requests">
          <div class="table-toolbar">
            <div>
              <h3>申请列表</h3>
              <p>审批通过后会自动开通系统入口，并补齐申请角色。</p>
            </div>
            <el-select v-model="requestQuery.status" clearable placeholder="全部状态" @change="loadRequests">
              <el-option label="待审批" value="PENDING" />
              <el-option label="已通过" value="APPROVED" />
              <el-option label="已驳回" value="REJECTED" />
            </el-select>
          </div>
          <el-table v-loading="requestLoading" :data="requests" stripe>
            <el-table-column prop="createdAt" label="申请时间" min-width="170" />
            <el-table-column label="申请人" min-width="180">
              <template #default="{ row }">
                <div class="identity-cell">
                  <strong>{{ row.applicantName }}</strong>
                  <span>{{ row.applicantEmail || row.applicantMobile }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="unitName" label="单元" min-width="130" />
            <el-table-column prop="appName" label="系统" min-width="120" />
            <el-table-column prop="roleCode" label="角色" min-width="120" />
            <el-table-column prop="reason" label="原因" min-width="180" />
            <el-table-column label="状态" width="110">
              <template #default="{ row }">
                <el-tag :type="requestStatusType(row.status)" effect="light">{{ requestStatusText(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="150" fixed="right">
              <template #default="{ row }">
                <template v-if="row.status === 'PENDING'">
                  <el-button type="primary" link @click="reviewRequest(row.id, 'APPROVE')">通过</el-button>
                  <el-button type="danger" link @click="reviewRequest(row.id, 'REJECT')">驳回</el-button>
                </template>
                <span v-else class="muted-text">已处理</span>
              </template>
            </el-table-column>
          </el-table>
          <div class="pager-box">
            <el-pagination
              background
              layout="total, prev, pager, next"
              :total="requestTotal"
              :current-page="requestQuery.pageNo"
              :page-size="requestQuery.pageSize"
              @current-change="handleRequestPageChange"
            />
          </div>
        </el-tab-pane>
      </el-tabs>
    </VnetPanel>

    <el-dialog v-model="unitDialogVisible" :title="unitForm.id ? '编辑单元' : '新增单元'" width="560px">
      <el-form ref="unitFormRef" :model="unitForm" :rules="unitRules" label-width="100px">
        <el-form-item label="单元编码" prop="unitCode">
          <el-input v-model="unitForm.unitCode" placeholder="例如 IT/PUR/ENG" />
        </el-form-item>
        <el-form-item label="单元名称" prop="unitName">
          <el-input v-model="unitForm.unitName" placeholder="请输入单元名称" />
        </el-form-item>
        <el-form-item label="允许申请">
          <el-checkbox-group v-model="unitForm.appCodes">
            <el-checkbox v-for="app in apps" :key="app.appCode" :label="app.appCode">{{ app.appName }}</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="unitForm.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="unitForm.remark" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="unitDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveUnit">保存</el-button>
      </template>
    </el-dialog>
  </VnetPage>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from "element-plus";
import VnetMetricCard from "../../components/vnet/VnetMetricCard.vue";
import VnetPage from "../../components/vnet/VnetPage.vue";
import VnetPanel from "../../components/vnet/VnetPanel.vue";
import {
  deletePlatformUnitApi,
  getPlatformAppsApi,
  getPlatformPermissionRequestPageApi,
  getPlatformSummaryApi,
  getPlatformUnitsApi,
  getPlatformUserAccessListApi,
  reviewPlatformPermissionRequestApi,
  savePlatformUnitApi,
  updatePlatformUserAccessApi
} from "../../api/platform";
import type {
  PlatformAppVO,
  PlatformPermissionRequestQuery,
  PlatformPermissionRequestVO,
  PlatformSummaryVO,
  PlatformUnitForm,
  PlatformUnitVO,
  PlatformUserAccessVO
} from "../../types/platform";

const activeTab = ref("users");
const loading = ref(false);
const requestLoading = ref(false);
const apps = ref<PlatformAppVO[]>([]);
const userAccessList = ref<PlatformUserAccessVO[]>([]);
const units = ref<PlatformUnitVO[]>([]);
const requests = ref<PlatformPermissionRequestVO[]>([]);
const requestTotal = ref(0);

const summary = reactive<PlatformSummaryVO>({
  totalUsers: 0,
  loginEnabledUsers: 0,
  accessBindings: 0,
  pendingRequests: 0
});

const requestQuery = reactive<PlatformPermissionRequestQuery>({
  pageNo: 1,
  pageSize: 10,
  status: "PENDING"
});

const unitDialogVisible = ref(false);
const unitFormRef = ref<FormInstance>();
const unitForm = reactive<PlatformUnitForm>({
  unitCode: "",
  unitName: "",
  parentId: null,
  status: 1,
  remark: "",
  appCodes: []
});

const unitRules: FormRules<PlatformUnitForm> = {
  unitCode: [{ required: true, message: "请输入单元编码", trigger: "blur" }],
  unitName: [{ required: true, message: "请输入单元名称", trigger: "blur" }]
};

function appName(appCode: string): string {
  return apps.value.find((app) => app.appCode === appCode)?.appName ?? appCode;
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

async function loadSummary(): Promise<void> {
  Object.assign(summary, await getPlatformSummaryApi());
}

async function loadUserAccess(): Promise<void> {
  loading.value = true;
  try {
    userAccessList.value = await getPlatformUserAccessListApi();
  } finally {
    loading.value = false;
  }
}

async function loadUnits(): Promise<void> {
  units.value = await getPlatformUnitsApi();
}

async function loadRequests(): Promise<void> {
  requestLoading.value = true;
  try {
    const page = await getPlatformPermissionRequestPageApi(requestQuery);
    requests.value = page.records;
    requestTotal.value = page.total;
  } finally {
    requestLoading.value = false;
  }
}

async function loadAll(): Promise<void> {
  const [appData] = await Promise.all([getPlatformAppsApi(), loadSummary(), loadUserAccess(), loadUnits(), loadRequests()]);
  apps.value = appData;
}

async function saveUserAccess(row: PlatformUserAccessVO): Promise<void> {
  await updatePlatformUserAccessApi(row.userId, {
    loginEnabled: row.loginEnabled,
    appCodes: row.appCodes
  });
  ElMessage.success("账号准入已保存");
  await loadSummary();
}

function resetUnitForm(): void {
  Object.assign(unitForm, {
    id: undefined,
    unitCode: "",
    unitName: "",
    parentId: null,
    status: 1,
    remark: "",
    appCodes: []
  });
}

function openUnitDialog(row?: PlatformUnitVO): void {
  resetUnitForm();
  if (row) {
    Object.assign(unitForm, { ...row, appCodes: [...row.appCodes] });
  }
  unitDialogVisible.value = true;
}

async function saveUnit(): Promise<void> {
  if (!unitFormRef.value) return;
  const valid = await unitFormRef.value.validate().catch(() => false);
  if (!valid) return;
  await savePlatformUnitApi(unitForm);
  ElMessage.success("单元配置已保存");
  unitDialogVisible.value = false;
  await loadUnits();
}

async function deleteUnit(id: number): Promise<void> {
  await ElMessageBox.confirm("删除后该单元不可再提交新申请，确认继续吗？", "删除单元", { type: "warning" });
  await deletePlatformUnitApi(id);
  ElMessage.success("单元已删除");
  await loadUnits();
}

async function reviewRequest(id: number, action: "APPROVE" | "REJECT"): Promise<void> {
  await reviewPlatformPermissionRequestApi(id, {
    action,
    comment: action === "APPROVE" ? "同意开通" : "暂不开通"
  });
  ElMessage.success(action === "APPROVE" ? "已通过申请" : "已驳回申请");
  await Promise.all([loadSummary(), loadRequests(), loadUserAccess()]);
}

function handleRequestPageChange(pageNo: number): void {
  requestQuery.pageNo = pageNo;
  void loadRequests();
}

onMounted(() => {
  void loadAll();
});
</script>

<style scoped>
.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
  margin-bottom: 16px;
}

.identity-cell {
  display: grid;
  gap: 4px;
}

.identity-cell span,
.muted-text {
  color: var(--app-text-soft);
  font-size: 13px;
}

.table-toolbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}

.table-toolbar h3 {
  margin: 0;
  color: var(--app-text);
  font-size: 16px;
}

.table-toolbar p {
  margin: 4px 0 0;
  color: var(--app-text-soft);
  font-size: 13px;
}

.tag-gap {
  margin-right: 6px;
}

.pager-box {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

@media (max-width: 1100px) {
  .metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .metric-grid {
    grid-template-columns: 1fr;
  }
}
</style>
