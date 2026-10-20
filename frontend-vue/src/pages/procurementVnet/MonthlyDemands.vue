<template>
  <VnetPage title="本月需求">
    <template #actions>
      <el-button v-if="canTestPublish" type="warning" :icon="Upload" :loading="testPublishing" @click="handleTestPublish">
        测试推送
      </el-button>
      <el-button v-if="canTestTimeout" type="danger" plain :icon="Timer" :loading="testTimingOut" @click="handleTestTimeout">
        测试超时
      </el-button>
      <el-button :icon="Refresh" @click="loadData">刷新</el-button>
    </template>

    <VnetPanel title="需求筛选">
      <el-form :model="queryModel" class="filter-form" inline>
        <el-form-item label="月份">
          <el-date-picker
            v-model="queryModel.planMonth"
            type="month"
            value-format="YYYY-MM"
            format="YYYY-MM"
            placeholder="选择月份"
            :clearable="false"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryModel.status" placeholder="全部状态" clearable class="filter-select">
            <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="queryModel.reqType" placeholder="全部类型" clearable class="filter-select">
            <el-option v-for="item in reqTypeOptions" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </VnetPanel>

    <VnetPanel title="需求池" class="monthly-demand-panel">
      <el-table v-loading="loading" :data="tableData" stripe>
        <el-table-column prop="demandNo" label="需求编号" min-width="150" />
        <el-table-column prop="itemName" label="商品名称" min-width="180" />
        <el-table-column prop="specModel" label="规格型号" min-width="140" />
        <el-table-column prop="reqTitle" label="需求标题" min-width="220" />
        <el-table-column prop="deptName" label="部门/机房" min-width="140" />
        <el-table-column prop="reqType" label="类型" width="100" />
        <el-table-column label="数量" width="110">
          <template #default="{ row }">{{ formatQty(row.qty) }} {{ row.unit }}</template>
        </el-table-column>
        <el-table-column label="参考金额" width="130">
          <template #default="{ row }">{{ formatMoney(row.lineAmount) }}</template>
        </el-table-column>
        <el-table-column prop="needDate" label="需求日期" width="120" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" effect="light">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="提交时间" min-width="160">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-space wrap>
              <el-tooltip :disabled="row.status === 'DEMAND_POOL'" content="只有需求池内商品可以补充" placement="top">
                <span>
                  <el-button
                    type="primary"
                    link
                    :icon="Plus"
                    :disabled="row.status !== 'DEMAND_POOL'"
                    :loading="supplementingId === row.id"
                    @click="handleSupplement(row)"
                  >
                    新增补采申请
                  </el-button>
                </span>
              </el-tooltip>
              <el-button
                v-if="canManageDemand(row)"
                type="primary"
                link
                :icon="Edit"
                :loading="quantityUpdatingId === row.id"
                @click="handleReduceQuantity(row)"
              >
                改数量
              </el-button>
              <el-button
                v-if="canManageDemand(row)"
                type="danger"
                link
                :icon="Delete"
                :loading="deletingId === row.id"
                @click="handleDeleteDemand(row)"
              >
                删除
              </el-button>
            </el-space>
          </template>
        </el-table-column>
      </el-table>

      <div class="table-footer">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next, jumper"
          :total="total"
          :current-page="queryModel.pageNo"
          :page-size="queryModel.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </VnetPanel>

    <el-dialog v-model="supplementDialogVisible" title="新增补采申请" width="560px" destroy-on-close>
      <el-form :model="supplementForm" label-width="86px" class="supplement-form">
        <el-form-item label="商品名称">
          <el-input :model-value="supplementSource?.itemName || ''" disabled />
        </el-form-item>
        <el-form-item label="规格型号">
          <el-input :model-value="supplementSource?.specModel || '-'" disabled />
        </el-form-item>
        <el-form-item label="购买数量" required>
          <div class="qty-editor">
            <el-input-number
              v-model="supplementForm.qty"
              :min="0.01"
              :precision="2"
              :step="1"
              controls-position="right"
            />
            <span class="qty-unit">{{ supplementSource?.unit || '' }}</span>
            <span class="amount-preview">预计 {{ formatMoney(supplementAmount) }}</span>
          </div>
        </el-form-item>
        <el-form-item label="购买原因" required>
          <el-input
            v-model="supplementForm.purchaseReason"
            type="textarea"
            :rows="4"
            maxlength="500"
            show-word-limit
            placeholder="请填写本次补采的购买原因"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="supplementDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="supplementingId === supplementSource?.id" @click="submitSupplement">
          确认新增
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="quantityDialogVisible" title="减少需求数量" width="520px" destroy-on-close>
      <el-form :model="quantityForm" label-width="96px" class="supplement-form">
        <el-form-item label="商品名称">
          <el-input :model-value="quantitySource?.itemName || ''" disabled />
        </el-form-item>
        <el-form-item label="当前数量">
          <el-input :model-value="`${formatQty(quantitySource?.qty)} ${quantitySource?.unit || ''}`" disabled />
        </el-form-item>
        <el-form-item label="调整后数量" required>
          <div class="qty-editor">
            <el-input-number
              v-model="quantityForm.qty"
              :min="0.01"
              :max="Number(quantitySource?.qty || 0)"
              :precision="2"
              :step="1"
              controls-position="right"
            />
            <span class="qty-unit">{{ quantitySource?.unit || '' }}</span>
            <span class="amount-preview">预计 {{ formatMoney(quantityAmount) }}</span>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="quantityDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="quantityUpdatingId === quantitySource?.id" @click="submitQuantityUpdate">
          保存
        </el-button>
      </template>
    </el-dialog>
  </VnetPage>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { Delete, Edit, Plus, Refresh, Timer, Upload } from "@element-plus/icons-vue";
import {
  deleteMonthlyDemandApi,
  getCurrentMonthlyDemandsApi,
  supplementMonthlyDemandApi,
  updateMonthlyDemandQuantityApi
} from "../../api/monthly-procurement";
import { processApprovalTimeoutsForTestApi, publishDemandPoolForTestApi } from "../../api/workflow";
import VnetPage from "../../components/vnet/VnetPage.vue";
import VnetPanel from "../../components/vnet/VnetPanel.vue";
import { useAuthStore } from "../../stores/auth";
import type { MonthlyDemandQuery, MonthlyDemandVO } from "../../types/monthly-procurement";

const statusOptions = [
  { label: "需求池", value: "DEMAND_POOL" }
];
const reqTypeOptions = ["工程项目", "实物资产/无形资产/长摊等", "服务/费用/低值易耗等"];

const authStore = useAuthStore();
const loading = ref(false);
const testPublishing = ref(false);
const testTimingOut = ref(false);
const supplementingId = ref<number | null>(null);
const quantityUpdatingId = ref<number | null>(null);
const deletingId = ref<number | null>(null);
const supplementDialogVisible = ref(false);
const quantityDialogVisible = ref(false);
const supplementSource = ref<MonthlyDemandVO | null>(null);
const quantitySource = ref<MonthlyDemandVO | null>(null);
const supplementForm = reactive({
  qty: 1,
  purchaseReason: ""
});
const quantityForm = reactive({
  qty: 1
});
const tableData = ref<MonthlyDemandVO[]>([]);
const total = ref(0);
const queryModel = reactive<MonthlyDemandQuery>({
  pageNo: 1,
  pageSize: 10,
  planMonth: currentMonth(),
  status: "DEMAND_POOL",
  reqType: ""
});
const SYSTEM_ADMIN_ROLES = ["SUPER_ADMIN", "SYS_ADMIN", "ADMIN"];
const canTestPublish = computed(() => authStore.hasAnyRole([...SYSTEM_ADMIN_ROLES, "PURCHASER"]));
const canTestTimeout = computed(() => authStore.hasAnyRole(SYSTEM_ADMIN_ROLES));
const supplementAmount = computed(() => Number(supplementForm.qty || 0) * Number(supplementSource.value?.unitPrice || 0));
const quantityAmount = computed(() => Number(quantityForm.qty || 0) * Number(quantitySource.value?.unitPrice || 0));

function currentMonth(): string {
  const now = new Date();
  const month = String(now.getMonth() + 1).padStart(2, "0");
  return `${now.getFullYear()}-${month}`;
}

async function loadData(): Promise<void> {
  loading.value = true;
  try {
    const result = await getCurrentMonthlyDemandsApi({
      pageNo: queryModel.pageNo,
      pageSize: queryModel.pageSize,
      planMonth: queryModel.planMonth,
      status: queryModel.status || undefined,
      reqType: queryModel.reqType || undefined
    });
    tableData.value = result.records || [];
    total.value = result.total || 0;
  } finally {
    loading.value = false;
  }
}

function handleSearch(): void {
  queryModel.pageNo = 1;
  loadData();
}

function handleReset(): void {
  queryModel.pageNo = 1;
  queryModel.pageSize = 10;
  queryModel.planMonth = currentMonth();
  queryModel.status = "DEMAND_POOL";
  queryModel.reqType = "";
  loadData();
}

function handlePageChange(pageNo: number): void {
  queryModel.pageNo = pageNo;
  loadData();
}

function handleSizeChange(pageSize: number): void {
  queryModel.pageSize = pageSize;
  queryModel.pageNo = 1;
  loadData();
}

async function handleSupplement(row: MonthlyDemandVO): Promise<void> {
  if (row.status !== "DEMAND_POOL") {
    return;
  }
  supplementSource.value = row;
  supplementForm.qty = Number(row.qty || 1);
  supplementForm.purchaseReason = row.purchaseReason || "";
  supplementDialogVisible.value = true;
}

function canManageDemand(row: MonthlyDemandVO): boolean {
  return row.status === "DEMAND_POOL" && Number(row.applicantUserId) === Number(authStore.user?.userId);
}

async function handleReduceQuantity(row: MonthlyDemandVO): Promise<void> {
  if (!canManageDemand(row)) {
    return;
  }
  quantitySource.value = row;
  quantityForm.qty = Number(row.qty || 0);
  quantityDialogVisible.value = true;
}

async function submitQuantityUpdate(): Promise<void> {
  const source = quantitySource.value;
  if (!source) {
    return;
  }
  const qty = Number(quantityForm.qty);
  const currentQty = Number(source.qty || 0);
  if (!Number.isFinite(qty) || qty <= 0) {
    ElMessage.warning("调整后数量必须大于 0");
    return;
  }
  if (qty >= currentQty) {
    ElMessage.warning("需求池商品数量只能减少，不能增加");
    return;
  }

  quantityUpdatingId.value = source.id;
  try {
    await updateMonthlyDemandQuantityApi(source.id, { qty });
    ElMessage.success("已调整数量");
    quantityDialogVisible.value = false;
    loadData();
  } finally {
    quantityUpdatingId.value = null;
  }
}

async function handleDeleteDemand(row: MonthlyDemandVO): Promise<void> {
  if (!canManageDemand(row)) {
    return;
  }
  await ElMessageBox.confirm(
    "删除后该商品不会进入本月需求池汇总报价。只能删除你自己提交且仍在需求池内的商品，确认删除？",
    "删除需求池商品",
    {
      confirmButtonText: "删除",
      cancelButtonText: "取消",
      type: "warning",
      confirmButtonClass: "el-button--danger"
    }
  );

  deletingId.value = row.id;
  try {
    await deleteMonthlyDemandApi(row.id);
    ElMessage.success("已删除需求池商品");
    loadData();
  } finally {
    deletingId.value = null;
  }
}

async function submitSupplement(): Promise<void> {
  const source = supplementSource.value;
  if (!source) {
    return;
  }
  const qty = Number(supplementForm.qty);
  const reason = supplementForm.purchaseReason.trim();
  if (!Number.isFinite(qty) || qty <= 0) {
    ElMessage.warning("购买数量必须大于 0");
    return;
  }
  if (!reason) {
    ElMessage.warning("请填写购买原因");
    return;
  }

  supplementingId.value = source.id;
  try {
    await supplementMonthlyDemandApi(source.id, {
      qty,
      purchaseReason: reason
    });
    ElMessage.success("已新增补采申请");
    supplementDialogVisible.value = false;
    loadData();
  } finally {
    supplementingId.value = null;
  }
}

async function handleTestPublish(): Promise<void> {
  await ElMessageBox.confirm(
    "测试推送会跳过月度推送日期限制，将当前需求池汇总成 HZCG 供应商报价单。确认继续？",
    "测试推送",
    {
      confirmButtonText: "确认推送",
      cancelButtonText: "取消",
      type: "warning"
    }
  );

  testPublishing.value = true;
  try {
    const count = await publishDemandPoolForTestApi();
    ElMessage.success(`已生成 ${count} 张 HZCG 供应商报价单`);
    loadData();
  } finally {
    testPublishing.value = false;
  }
}

async function handleTestTimeout(): Promise<void> {
  await ElMessageBox.confirm(
    "测试超时只统计当前超过后台配置确认期限的待审批单，不会自动退回或驳回；审批人仍可同意，最终采购顺延至下月。确认继续？",
    "测试审批超时",
    {
      confirmButtonText: "确认执行",
      cancelButtonText: "取消",
      type: "warning"
    }
  );

  testTimingOut.value = true;
  try {
    const count = await processApprovalTimeoutsForTestApi();
    ElMessage.success(`当前有 ${count} 个超时待审批`);
    loadData();
  } finally {
    testTimingOut.value = false;
  }
}

function statusText(status: string): string {
  const matched = statusOptions.find((item) => item.value === status);
  return matched?.label || status;
}

function statusTag(status: string): "success" | "warning" | "info" {
  if (status === "POOLING") {
    return "warning";
  }
  if (status === "DEMAND_POOL") {
    return "warning";
  }
  if (status === "REQUEST_CREATED") {
    return "success";
  }
  return "info";
}

function formatMoney(value?: number): string {
  return Number(value || 0).toLocaleString("zh-CN", { style: "currency", currency: "CNY", maximumFractionDigits: 2 });
}

function formatQty(value?: number): string {
  return Number(value || 0).toLocaleString("zh-CN", { maximumFractionDigits: 2 });
}

function formatDateTime(value?: string): string {
  return value ? value.replace("T", " ").slice(0, 19) : "-";
}

onMounted(loadData);
</script>

<style scoped>
.filter-form {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 12px;
}

.filter-select {
  width: 160px;
}

.monthly-demand-panel {
  margin-top: 16px;
}

.table-footer {
  display: flex;
  justify-content: flex-end;
  padding-top: 16px;
}

.supplement-form {
  padding-top: 4px;
}

.qty-editor {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
}

.qty-editor :deep(.el-input-number) {
  width: 180px;
}

.qty-unit,
.amount-preview {
  color: #64748b;
  font-size: 13px;
}

.amount-preview {
  margin-left: auto;
}

@media (max-width: 760px) {
  .filter-form {
    display: block;
  }

  .filter-select {
    width: 100%;
  }

  .table-footer {
    justify-content: flex-start;
    overflow-x: auto;
  }

  .qty-editor {
    align-items: flex-start;
    flex-direction: column;
  }

  .amount-preview {
    margin-left: 0;
  }
}
</style>
