<template>
  <VnetPage title="个人劳保申领">
    <template #actions>
      <el-button :loading="loading" @click="loadPageData">刷新</el-button>
    </template>

    <VnetPanel title="申领信息">
      <el-form label-position="top">
        <div class="labor-header-grid">
          <el-form-item label="申请部门/机房楼栋" required>
            <el-select
              v-model="selectedRouteId"
              filterable
              :disabled="departmentLocked"
              placeholder="请选择部门/机房楼栋"
              style="width: 100%"
              @change="handleRouteChange"
            >
              <el-option
                v-for="option in departmentOptions"
                :key="option.id"
                :label="departmentOptionLabel(option)"
                :value="option.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="收货城市">
            <el-input :model-value="selectedRoute?.city || '-'" disabled />
          </el-form-item>
          <el-form-item label="预算编码">
            <el-input :model-value="selectedRoute?.costCenterCode || '-'" disabled />
          </el-form-item>
          <el-form-item label="需求日期">
            <el-date-picker v-model="formModel.needDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
          </el-form-item>
          <el-form-item label="采购方式">
            <div class="mode-switch">
              <span :class="['mode-label', !formModel.emergency && 'is-active']">周期申领</span>
              <el-switch v-model="formModel.emergency" @change="filterInvalidSelection" />
              <span :class="['mode-label', formModel.emergency && 'is-active']">紧急购买</span>
            </div>
          </el-form-item>
          <el-form-item label="申请标题">
            <el-input v-model="formModel.requestTitle" clearable placeholder="留空时系统自动生成" />
          </el-form-item>
          <el-form-item class="span-2" label="申领说明">
            <el-input
              v-model="formModel.reason"
              type="textarea"
              :rows="3"
              maxlength="300"
              show-word-limit
              :placeholder="formModel.emergency ? '请填写紧急购买原因' : '可填写本次劳保申领说明'"
            />
          </el-form-item>
        </div>
      </el-form>
    </VnetPanel>

    <VnetPanel title="劳保用品">
      <el-alert
        class="labor-alert"
        type="info"
        :closable="false"
        show-icon
        title="周期申领默认只能在到期前 30 天内提交；紧急购买会按紧急采购进入原审批流程，确认领用后重新计算下一轮周期。"
      />
      <el-table v-loading="loading" :data="laborItems" stripe empty-text="暂无可申领的劳保用品">
        <el-table-column label="申领" width="70" fixed="left">
          <template #default="{ row }">
            <el-checkbox
              :model-value="selectedCodes.includes(row.itemCode)"
              :disabled="!rowSelectable(row)"
              @update:model-value="toggleRowValue(row, $event)"
            />
          </template>
        </el-table-column>
        <el-table-column label="用品" min-width="180">
          <template #default="{ row }">
            <div class="item-main">
              <span>{{ row.itemName }}</span>
              <el-tag v-if="row.forced" type="danger" effect="light">强制监管</el-tag>
            </div>
            <div class="item-sub">
              周期 {{ row.cycleDays }} 天；提前 {{ row.advanceDays }} 天可申领；限 {{ formatQty(row.quantityLimit) }}{{ row.unit }}
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="140">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" effect="light">{{ row.statusName }}</el-tag>
            <div v-if="row.purchaseRequestNo" class="item-sub">{{ row.purchaseRequestNo }}</div>
          </template>
        </el-table-column>
        <el-table-column label="上次/下次" min-width="160">
          <template #default="{ row }">
            <div>上次：{{ row.lastReceivedDate || "-" }}</div>
            <div>下次：{{ row.nextDueDate || "-" }}</div>
            <div v-if="row.daysUntilDue != null" class="item-sub">
              {{ row.daysUntilDue >= 0 ? `还剩 ${row.daysUntilDue} 天` : `已超 ${Math.abs(row.daysUntilDue)} 天` }}
            </div>
          </template>
        </el-table-column>
        <el-table-column label="规格型号" min-width="150">
          <template #default="{ row }">
            <el-input v-model="lineForm(row.itemCode).specModel" :disabled="!selectedCodes.includes(row.itemCode)" clearable />
          </template>
        </el-table-column>
        <el-table-column label="数量" width="130">
          <template #default="{ row }">
            <el-input-number
              v-model="lineForm(row.itemCode).qty"
              :min="1"
              :max="Number(row.quantityLimit || 9999)"
              :precision="0"
              step-strictly
              controls-position="right"
              :disabled="!selectedCodes.includes(row.itemCode)"
            />
          </template>
        </el-table-column>
        <el-table-column label="参考单价" width="150">
          <template #default="{ row }">
            <el-input-number
              v-model="lineForm(row.itemCode).unitPrice"
              :min="0.01"
              :precision="2"
              controls-position="right"
              disabled
            />
          </template>
        </el-table-column>
        <el-table-column label="用途" min-width="190">
          <template #default="{ row }">
            <el-input
              v-model="lineForm(row.itemCode).purpose"
              :disabled="!selectedCodes.includes(row.itemCode)"
              clearable
              placeholder="例如：配电室巡检"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.canConfirmReceived"
              type="success"
              link
              :loading="confirmingRecordId === row.recordId"
              @click="confirmReceived(row)"
            >
              确认已领用
            </el-button>
            <span v-else class="item-sub">-</span>
          </template>
        </el-table-column>
      </el-table>

      <div class="summary-bar">
        <div>
          已选 {{ selectedRows.length }} 项，预计金额 {{ formatMoney(totalAmount) }}
        </div>
        <div class="summary-actions">
          <el-button @click="clearSelection">清空选择</el-button>
          <el-button type="primary" :loading="submitting" @click="submitRequest">提交申领</el-button>
        </div>
      </div>
    </VnetPanel>
  </VnetPage>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import VnetPage from "../../components/vnet/VnetPage.vue";
import VnetPanel from "../../components/vnet/VnetPanel.vue";
import {
  confirmPersonalLaborReceivedApi,
  getPersonalLaborItemsApi,
  submitPersonalLaborRequestApi,
  type PersonalLaborItemStatusVO
} from "../../api/personal-labor";
import { getProcurementDepartmentOptionsApi } from "../../api/workflow";
import { useAuthStore } from "../../stores/auth";
import type { ProcurementApprovalRouteVO } from "../../types/platform";

interface HeaderForm {
  requestTitle: string;
  reason: string;
  needDate: string;
  emergency: boolean;
}

interface LineForm {
  specModel: string;
  qty: number;
  unitPrice: number;
  purpose: string;
}

const authStore = useAuthStore();
const loading = ref(false);
const submitting = ref(false);
const confirmingRecordId = ref<number | null>(null);
const departmentOptions = ref<ProcurementApprovalRouteVO[]>([]);
const selectedRouteId = ref<number | null>(null);
const departmentLocked = ref(false);
const laborItems = ref<PersonalLaborItemStatusVO[]>([]);
const selectedCodes = ref<string[]>([]);
const lineForms = reactive<Record<string, LineForm>>({});
const defaultReferenceUnitPrice = 0.01;
const formModel = reactive<HeaderForm>({
  requestTitle: "",
  reason: "",
  needDate: defaultNeedDate(),
  emergency: false
});

const selectedRoute = computed(() => departmentOptions.value.find((item) => item.id === selectedRouteId.value) || null);
const selectedRows = computed(() => laborItems.value.filter((item) => selectedCodes.value.includes(item.itemCode)));
const totalAmount = computed(() =>
  selectedRows.value.reduce((sum, row) => {
    const form = lineForm(row.itemCode);
    return sum + Number(form.qty || 0) * Number(form.unitPrice || 0);
  }, 0)
);

onMounted(() => {
  loadPageData();
});

async function loadPageData(): Promise<void> {
  loading.value = true;
  try {
    const [departments, items] = await Promise.all([getProcurementDepartmentOptionsApi(), getPersonalLaborItemsApi()]);
    departmentOptions.value = departments || [];
    laborItems.value = items || [];
    initializeDepartmentSelection();
    initializeLineForms();
    filterInvalidSelection();
  } finally {
    loading.value = false;
  }
}

function initializeDepartmentSelection(): void {
  if (selectedRouteId.value && departmentOptions.value.some((item) => item.id === selectedRouteId.value)) {
    return;
  }
  const userDeptCode = authStore.user?.deptCode;
  const userDeptName = authStore.user?.deptName;
  const matched =
    departmentOptions.value.find((item) => userDeptCode && item.deptCode === userDeptCode) ||
    departmentOptions.value.find((item) => userDeptName && item.deptName === userDeptName);
  selectedRouteId.value = matched?.id ?? null;
  departmentLocked.value = Boolean(matched);
}

function initializeLineForms(): void {
  for (const row of laborItems.value) {
    if (!lineForms[row.itemCode]) {
      lineForms[row.itemCode] = {
        specModel: row.specModel || "",
        qty: 1,
        unitPrice: defaultReferenceUnitPrice,
        purpose: ""
      };
    } else {
      lineForms[row.itemCode].unitPrice = defaultReferenceUnitPrice;
    }
  }
}

function lineForm(itemCode: string): LineForm {
  if (!lineForms[itemCode]) {
    lineForms[itemCode] = {
      specModel: "",
      qty: 1,
      unitPrice: defaultReferenceUnitPrice,
      purpose: ""
    };
  } else {
    lineForms[itemCode].unitPrice = defaultReferenceUnitPrice;
  }
  return lineForms[itemCode];
}

function rowSelectable(row: PersonalLaborItemStatusVO): boolean {
  if (!row.enabled) {
    return false;
  }
  return formModel.emergency ? row.canEmergencyPurchase : row.canApply;
}

function toggleRow(row: PersonalLaborItemStatusVO, checked: boolean): void {
  if (checked && !rowSelectable(row)) {
    return;
  }
  if (checked) {
    selectedCodes.value = Array.from(new Set([...selectedCodes.value, row.itemCode]));
    return;
  }
  selectedCodes.value = selectedCodes.value.filter((code) => code !== row.itemCode);
}

function toggleRowValue(row: PersonalLaborItemStatusVO, value: string | number | boolean): void {
  toggleRow(row, Boolean(value));
}

function filterInvalidSelection(): void {
  selectedCodes.value = selectedCodes.value.filter((code) => {
    const row = laborItems.value.find((item) => item.itemCode === code);
    return row ? rowSelectable(row) : false;
  });
}

function clearSelection(): void {
  selectedCodes.value = [];
}

function handleRouteChange(): void {
  const route = selectedRoute.value;
  if (route && !formModel.requestTitle) {
    formModel.requestTitle = `${route.roomName || route.deptName || "个人"}劳保用品申领`;
  }
}

async function submitRequest(): Promise<void> {
  if (!selectedRoute.value) {
    ElMessage.warning("请选择申请部门/机房楼栋");
    return;
  }
  if (selectedRows.value.length === 0) {
    ElMessage.warning("请选择要申领的劳保用品");
    return;
  }
  if (formModel.emergency && !formModel.reason.trim()) {
    ElMessage.warning("紧急购买必须填写原因");
    return;
  }
  for (const row of selectedRows.value) {
    const form = lineForm(row.itemCode);
    if (!Number.isInteger(Number(form.qty)) || Number(form.qty) <= 0) {
      ElMessage.warning(`${row.itemName} 的数量必须是大于 0 的整数`);
      return;
    }
    if (!form.purpose.trim() && !formModel.reason.trim()) {
      ElMessage.warning(`${row.itemName} 请填写用途或申领说明`);
      return;
    }
  }

  try {
    await ElMessageBox.confirm(
      `确认提交 ${selectedRows.value.length} 项个人劳保${formModel.emergency ? "紧急购买" : "申领"}？`,
      "提交个人劳保申领",
      { type: "warning", confirmButtonText: "提交", cancelButtonText: "取消" }
    );
  } catch {
    return;
  }

  submitting.value = true;
  try {
    const route = selectedRoute.value;
    const result = await submitPersonalLaborRequestApi({
      requestTitle: formModel.requestTitle.trim(),
      deptCode: route?.deptCode || "",
      deptName: route?.deptName || "",
      budgetCode: route?.costCenterCode || "",
      receivingCity: route?.city || "",
      reason: formModel.reason.trim(),
      needDate: formModel.needDate,
      emergency: formModel.emergency,
      items: selectedRows.value.map((row) => {
        const form = lineForm(row.itemCode);
        return {
          itemCode: row.itemCode,
          specModel: form.specModel.trim(),
          qty: Number(form.qty),
          unitPrice: defaultReferenceUnitPrice,
          purpose: form.purpose.trim()
        };
      })
    });
    ElMessage.success(`${result.purchaseRequestNo || "采购申请"} 已提交，下一节点：${result.nextNodeName || "-"}`);
    selectedCodes.value = [];
    await loadPageData();
  } finally {
    submitting.value = false;
  }
}

async function confirmReceived(row: PersonalLaborItemStatusVO): Promise<void> {
  if (!row.recordId) {
    return;
  }
  try {
    await ElMessageBox.confirm(`确认已领用 ${row.itemName}？确认后会重新计算下一轮更换日期。`, "确认已领用", {
      type: "warning",
      confirmButtonText: "确认已领用",
      cancelButtonText: "取消"
    });
  } catch {
    return;
  }
  confirmingRecordId.value = row.recordId;
  try {
    await confirmPersonalLaborReceivedApi(row.recordId);
    ElMessage.success("已确认领用");
    await loadPageData();
  } finally {
    confirmingRecordId.value = null;
  }
}

function departmentOptionLabel(option: ProcurementApprovalRouteVO): string {
  const location = [option.roomName, option.buildingName].filter(Boolean).join(" / ");
  const dept = option.deptName || option.deptCode || option.costCenterCode || "";
  return dept ? `${location || option.routeKey} - ${dept}` : location || option.routeKey;
}

function statusTagType(status: string): "success" | "warning" | "danger" | "info" | "primary" {
  switch (status) {
    case "FIRST_AVAILABLE":
    case "AVAILABLE":
    case "RECEIVED":
      return "success";
    case "REMINDING":
    case "REQUESTED":
      return "warning";
    case "OVERDUE":
      return "danger";
    default:
      return "info";
  }
}

function formatQty(value?: number | null): string {
  return Number(value || 0).toLocaleString("zh-CN", { maximumFractionDigits: 2 });
}

function formatMoney(value: number): string {
  return Number(value || 0).toLocaleString("zh-CN", { style: "currency", currency: "CNY", minimumFractionDigits: 2 });
}

function defaultNeedDate(): string {
  const date = new Date();
  date.setDate(date.getDate() + 7);
  return date.toISOString().slice(0, 10);
}
</script>

<style scoped>
.labor-header-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px 16px;
}

.span-2 {
  grid-column: span 2;
}

.mode-switch {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 32px;
}

.mode-label {
  color: #6b7a90;
  font-size: 14px;
}

.mode-label.is-active {
  color: #165dff;
  font-weight: 600;
}

.labor-alert {
  margin-bottom: 12px;
}

.item-main {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  color: #14213d;
}

.item-sub {
  margin-top: 4px;
  color: #6b7a90;
  font-size: 12px;
  line-height: 1.35;
}

.summary-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-top: 14px;
  font-weight: 600;
}

.summary-actions {
  display: flex;
  gap: 10px;
}

@media (max-width: 1100px) {
  .labor-header-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .labor-header-grid,
  .span-2 {
    grid-template-columns: 1fr;
    grid-column: span 1;
  }

  .summary-bar {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
