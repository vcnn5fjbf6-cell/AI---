<template>
  <main class="feishu-intake">
    <section class="intake-shell">
      <header class="intake-head">
        <div>
          <p>{{ currentMonth }} 月度采购</p>
          <h1>采购需求</h1>
        </div>
        <el-tag :type="formModel.emergency ? 'danger' : 'success'" effect="light">
          {{ formModel.emergency ? "紧急采购" : "月度汇总" }}
        </el-tag>
      </header>

      <el-form ref="formRef" :model="formModel" :rules="rules" label-position="top" class="intake-form">
        <el-form-item label="申请标题" prop="reqTitle">
          <el-input v-model="formModel.reqTitle" clearable />
        </el-form-item>

        <div class="two-col">
          <el-form-item label="采购类型" prop="reqType">
            <el-select v-model="formModel.reqType" style="width: 100%">
              <el-option label="工程项目" value="工程项目" />
              <el-option label="实物资产/无形资产/长摊等" value="实物资产/无形资产/长摊等" />
              <el-option label="服务/费用/低值易耗等" value="服务/费用/低值易耗等" />
            </el-select>
          </el-form-item>
          <el-form-item label="处理方式">
            <el-switch
              v-model="formModel.emergency"
              active-text="紧急"
              inactive-text="月度"
              @change="syncUrgency"
            />
          </el-form-item>
        </div>

        <div class="two-col">
          <el-form-item label="申请部门">
            <el-select
              v-model="selectedRouteId"
              filterable
              placeholder="请选择申请部门"
              style="width: 100%"
              @change="handleDepartmentChange"
            >
              <el-option
                v-for="option in departmentOptions"
                :key="option.id"
                :label="departmentOptionLabel(option)"
                :value="option.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="预算编码">
            <el-input v-model="formModel.budgetCode" disabled />
          </el-form-item>
        </div>

        <div v-if="isSystemAdmin" class="two-col">
          <el-form-item label="代办申请人">
            <el-select
              v-model="formModel.applicantUserId"
              filterable
              placeholder="不选则使用当前账号"
              style="width: 100%"
              @change="handleApplicantChange"
            >
              <el-option
                v-for="user in userOptions"
                :key="user.id"
                :label="userOptionLabel(user)"
                :value="user.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="代选原因">
            <el-input v-model="formModel.actingReason" clearable />
          </el-form-item>
        </div>

        <el-divider />

        <el-form-item label="商品名称" prop="itemName">
          <el-input v-model="formModel.itemName" clearable />
        </el-form-item>
        <el-form-item label="规格型号">
          <el-input v-model="formModel.specModel" clearable />
        </el-form-item>

        <div class="three-col">
          <el-form-item label="数量" prop="qty">
            <el-input-number v-model="formModel.qty" :min="0.01" :precision="2" controls-position="right" style="width: 100%" />
          </el-form-item>
          <el-form-item label="单位" prop="unit">
            <ProcurementUnitSelect v-model="formModel.unit" />
          </el-form-item>
          <el-form-item label="单价" prop="unitPrice">
            <el-input-number v-model="formModel.unitPrice" :min="0.01" :precision="2" controls-position="right" style="width: 100%" />
          </el-form-item>
        </div>

        <el-form-item label="需求日期">
          <el-date-picker v-model="formModel.needDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="电商链接">
          <el-input v-model="formModel.ecommerceUrl" clearable placeholder="https://..." />
        </el-form-item>
        <el-form-item label="采购原因" prop="purchaseReason">
          <el-input v-model="formModel.purchaseReason" type="textarea" :rows="4" />
        </el-form-item>
      </el-form>

      <section class="intake-summary">
        <span>预计金额</span>
        <strong>{{ formatMoney(lineAmount) }}</strong>
      </section>

      <div class="submit-bar">
        <el-button type="primary" size="large" :loading="submitting" @click="submitDemand">提交</el-button>
      </div>

      <section v-if="latestDemands.length" class="recent-list">
        <h2>最近提交</h2>
        <div v-for="item in latestDemands" :key="item.id" class="recent-item">
          <div>
            <strong>{{ item.itemName }}</strong>
            <span>{{ item.demandNo }}</span>
          </div>
          <el-tag :type="statusTagType(item.status)" effect="light">{{ statusText(item.status) }}</el-tag>
        </div>
      </section>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import type { FormInstance, FormRules } from "element-plus";
import { ElMessage } from "element-plus";
import ProcurementUnitSelect from "../../components/procurement/ProcurementUnitSelect.vue";
import { getMyMonthlyDemandsApi, submitMonthlyDemandApi } from "../../api/monthly-procurement";
import { getProcurementDepartmentOptionsApi } from "../../api/workflow";
import { getUserListApi } from "../../api/user";
import { useAuthStore } from "../../stores/auth";
import type { MonthlyDemandCreateForm, MonthlyDemandVO } from "../../types/monthly-procurement";
import type { ProcurementApprovalRouteVO } from "../../types/platform";
import type { UserVO } from "../../types/system";

const formRef = ref<FormInstance>();
const submitting = ref(false);
const latestDemands = ref<MonthlyDemandVO[]>([]);
const currentMonth = new Date().toISOString().slice(0, 7);
const authStore = useAuthStore();
const departmentOptions = ref<ProcurementApprovalRouteVO[]>([]);
const selectedRouteId = ref<number | null>(null);
const userOptions = ref<UserVO[]>([]);
const isSystemAdmin = computed(() => authStore.hasAnyRole(["SUPER_ADMIN", "SYS_ADMIN", "ADMIN"]));

const formModel = reactive<MonthlyDemandCreateForm>({
  planMonth: currentMonth,
  reqTitle: "",
  reqType: "服务/费用/低值易耗等",
  applicantUserId: authStore.user?.userId || null,
  deptName: "",
  budgetCode: "",
  actingReason: "",
  urgencyLevel: 1,
  emergency: false,
  itemName: "",
  specModel: "",
  unit: "件",
  qty: 1,
  unitPrice: 0,
  taxRate: 0,
  needDate: "",
  purchaseReason: "",
  ecommerceUrl: ""
});

const rules: FormRules<MonthlyDemandCreateForm> = {
  reqTitle: [{ required: true, message: "请输入申请标题", trigger: "blur" }],
  reqType: [{ required: true, message: "请选择采购类型", trigger: "change" }],
  itemName: [{ required: true, message: "请输入商品名称", trigger: "blur" }],
  unit: [{ required: true, message: "请选择单位", trigger: "change" }],
  qty: [{ required: true, message: "请输入数量", trigger: "change" }],
  unitPrice: [{ required: true, message: "请输入单价", trigger: "change" }],
  purchaseReason: [{ required: true, message: "请输入采购原因", trigger: "blur" }]
};

const lineAmount = computed(() => Number(formModel.qty || 0) * Number(formModel.unitPrice || 0));

function syncUrgency(): void {
  formModel.urgencyLevel = formModel.emergency ? 2 : 1;
}

async function submitDemand(): Promise<void> {
  if (!formRef.value) {
    return;
  }
  const valid = await formRef.value.validate().catch(() => false);
  if (!valid) {
    return;
  }
  if (!isPositiveMoney(formModel.unitPrice)) {
    ElMessage.warning("单价必须大于 0");
    return;
  }

  submitting.value = true;
  try {
    const result = await submitMonthlyDemandApi({ ...formModel });
    if (result.emergency) {
      ElMessage.success("紧急采购已提交审批");
    } else {
      ElMessage.success("已进入月度采购池");
    }
    resetForm();
    await loadLatest();
  } finally {
    submitting.value = false;
  }
}

function resetForm(): void {
  formModel.reqTitle = "";
  formModel.itemName = "";
  formModel.specModel = "";
  formModel.qty = 1;
  formModel.unitPrice = 0;
  formModel.needDate = "";
  formModel.purchaseReason = "";
  formModel.ecommerceUrl = "";
}

async function loadLatest(): Promise<void> {
  const page = await getMyMonthlyDemandsApi({ pageNo: 1, pageSize: 5 });
  latestDemands.value = page.records;
}

async function loadDepartmentOptions(): Promise<void> {
  departmentOptions.value = await getProcurementDepartmentOptionsApi();
  const ownRoute = matchDepartmentRoute(authStore.user?.deptCode || "", authStore.user?.deptName || "");
  if (ownRoute) {
    applyDepartmentRoute(ownRoute);
    return;
  }
  if (departmentOptions.value.length === 1) {
    applyDepartmentRoute(departmentOptions.value[0]);
  }
}

async function loadUserOptions(): Promise<void> {
  if (!isSystemAdmin.value) {
    return;
  }
  userOptions.value = await getUserListApi({ pageNo: 1, pageSize: 500, status: 1, accountCategory: "EMPLOYEE" });
}

function handleDepartmentChange(routeId: number): void {
  const selected = departmentOptions.value.find((option) => option.id === routeId);
  if (selected) {
    applyDepartmentRoute(selected);
  }
}

function applyDepartmentRoute(option: ProcurementApprovalRouteVO): void {
  selectedRouteId.value = option.id;
  formModel.deptCode = option.deptCode || option.costCenterCode || "";
  formModel.deptName = option.deptName || [option.roomName, option.buildingName].filter(Boolean).join("-");
  formModel.budgetCode = option.costCenterCode || option.deptCode || "";
}

function handleApplicantChange(userId: number): void {
  const selected = userOptions.value.find((user) => user.id === userId);
  if (!selected) {
    return;
  }
  formModel.applicantUserId = selected.id;
  const route = matchDepartmentRoute(selected.deptCode || "", selected.deptName || "");
  if (!selectedRouteId.value && route) {
    applyDepartmentRoute(route);
  }
}

function matchDepartmentRoute(deptCode: string, deptName: string): ProcurementApprovalRouteVO | undefined {
  const normalizedDeptCode = String(deptCode || "").trim();
  const normalizedDeptName = String(deptName || "").trim();
  return departmentOptions.value.find((option) => (
    (normalizedDeptCode && (normalizedDeptCode === option.deptCode || normalizedDeptCode === option.costCenterCode)) ||
    (normalizedDeptName && normalizedDeptName === option.deptName)
  ));
}

function departmentOptionLabel(option: ProcurementApprovalRouteVO): string {
  const location = [option.roomName, option.buildingName].filter(Boolean).join(" / ");
  const dept = option.deptName || option.deptCode || option.costCenterCode || "";
  return dept ? `${location} - ${dept}` : location;
}

function userOptionLabel(user: UserVO): string {
  const name = user.realName || user.username;
  return user.employeeNo ? `${name}（${user.employeeNo}）` : name;
}

function formatMoney(value: number): string {
  return Number(value).toLocaleString("zh-CN", { style: "currency", currency: "CNY" });
}

function statusText(status: string): string {
  if (status === "POOLING") return "待汇总";
  if (status === "REQUEST_CREATED") return "已生成申请";
  if (status === "EMERGENCY_SUBMITTED") return "已提交审批";
  return status || "-";
}

function statusTagType(status: string): "success" | "warning" | "danger" | "info" {
  if (status === "POOLING") return "warning";
  if (status === "REQUEST_CREATED") return "success";
  if (status === "EMERGENCY_SUBMITTED") return "danger";
  return "info";
}

function isPositiveMoney(value: unknown): boolean {
  const numericValue = Number(value);
  return Number.isFinite(numericValue) && numericValue > 0;
}

onMounted(() => {
  void loadDepartmentOptions();
  void loadUserOptions();
  void loadLatest();
});
</script>

<style scoped>
.feishu-intake {
  min-height: 100vh;
  background: #f4f7fb;
  padding: 18px;
}

.intake-shell {
  max-width: 860px;
  margin: 0 auto;
  background: #fff;
  border: 1px solid #d9e2f1;
  border-radius: 8px;
  padding: 22px;
}

.intake-head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
  margin-bottom: 18px;
}

.intake-head p {
  margin: 0 0 6px;
  color: #2563eb;
  font-size: 13px;
  font-weight: 700;
}

.intake-head h1 {
  margin: 0;
  color: #0f172a;
  font-size: 26px;
}

.two-col,
.three-col {
  display: grid;
  gap: 14px;
}

.two-col {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.three-col {
  grid-template-columns: minmax(150px, 1fr) minmax(110px, 0.7fr) minmax(150px, 1fr);
}

.intake-summary {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 16px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  margin-top: 10px;
}

.intake-summary strong {
  color: #0f172a;
  font-size: 20px;
}

.submit-bar {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

.recent-list {
  margin-top: 22px;
}

.recent-list h2 {
  font-size: 18px;
  margin: 0 0 12px;
}

.recent-item {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
  border-top: 1px solid #e5e7eb;
  padding: 12px 0;
}

.recent-item div {
  display: grid;
  gap: 4px;
}

.recent-item span {
  color: #64748b;
  font-size: 13px;
}

@media (max-width: 720px) {
  .feishu-intake {
    padding: 10px;
  }

  .intake-shell {
    padding: 16px;
  }

  .two-col,
  .three-col {
    grid-template-columns: 1fr;
  }
}
</style>
