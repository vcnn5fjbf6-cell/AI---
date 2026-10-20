<template>
  <div class="page-container">
    <PageHero
      eyebrow="Supplier Base"
      title="供应商管理"
      description="维护供应商主数据、准入状态和绑定账号。供应商编码支持留空自动生成，账号维护可一键跳转到系统用户管理。"
    >
      <div class="hero-inline">
        <span class="hero-pill">当前 {{ total }} 家供应商</span>
        <span class="hero-pill hero-pill--muted">已绑定账号 {{ linkedAccountTotal }} 个</span>
        <span class="hero-pill hero-pill--muted">筛选条件 {{ activeFilterCount }} 项</span>
      </div>
      <template #actions>
        <el-button v-perm="'supplier:create'" type="primary" @click="openCreate">新增供应商</el-button>
      </template>
      <template #aside>
        <div class="hero-stat-panel">
          <p class="hero-stat-kicker">Supplier Master</p>
          <p class="hero-stat-value">{{ total }}</p>
          <p class="hero-stat-note">供应商主数据和供应商门户账号已经接入同一套维护链路，减少线上账号与主数据脱节。</p>
        </div>
      </template>
    </PageHero>

    <el-card class="panel-card filter-form page-section" shadow="never">
      <el-form :inline="true" :model="queryModel">
        <el-form-item label="供应商编码">
          <el-input v-model="queryModel.supplierCode" clearable placeholder="输入供应商编码" />
        </el-form-item>
        <el-form-item label="供应商名称">
          <el-input v-model="queryModel.supplierName" clearable placeholder="输入供应商名称" />
        </el-form-item>
        <el-form-item label="准入状态">
          <el-select v-model="queryModel.onboardingStatus" class="supplier-filter-select" clearable placeholder="全部">
            <el-option label="待审核" value="PENDING" />
            <el-option label="已通过" value="APPROVED" />
            <el-option label="已拒绝" value="REJECTED" />
          </el-select>
        </el-form-item>
        <el-form-item label="启用状态">
          <el-select v-model="queryModel.status" class="supplier-filter-select" clearable placeholder="全部">
            <el-option label="启用" value="ACTIVE" />
            <el-option label="停用" value="INACTIVE" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleResetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="panel-card table-panel page-section" shadow="never">
      <div class="table-toolbar">
        <div>
          <h3 class="card-title">供应商列表</h3>
          <p class="sub-text">除主数据外，直接显示关联账号情况，方便从供应商维度追溯报价账号和门户使用状态。</p>
        </div>
      </div>

      <el-table v-loading="loading" :data="tableData" stripe>
        <el-table-column label="供应商" min-width="240">
          <template #default="{ row }">
            <div class="supplier-cell">
              <div class="supplier-title">{{ row.supplierName }}</div>
              <div class="supplier-subtitle">{{ row.supplierCode }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="supplierType" label="类型" width="120" />
        <el-table-column prop="contactName" label="联系人" width="120" />
        <el-table-column prop="contactPhone" label="联系电话" min-width="140" />
        <el-table-column label="关联账号" min-width="220">
          <template #default="{ row }">
            <div class="linked-users">
              <span v-if="!row.linkedUserCount" class="table-placeholder">未绑定账号</span>
              <template v-else>
                <el-tag v-for="username in (row.linkedUsernames || []).slice(0, 2)" :key="username" effect="light">
                  {{ username }}
                </el-tag>
                <span class="linked-count">共 {{ row.linkedUserCount }} 个</span>
              </template>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="准入状态" width="120">
          <template #default="{ row }">
            <el-tag :type="onboardingTagType(row.onboardingStatus)" effect="light">{{ row.onboardingStatus }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'danger'" effect="light">
              {{ row.status === "ACTIVE" ? "启用" : "停用" }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="投标状态" width="170">
          <template #default="{ row }">
            <el-tag v-if="isSupplierBanned(row)" type="danger" effect="light">
              已封禁
            </el-tag>
            <el-tag v-else-if="isSupplierBiddingSuspended(row)" type="warning" effect="light">
              暂停至 {{ formatDateTime(row.bidSuspendedUntil) }}
            </el-tag>
            <el-tag v-else type="success" effect="light">正常</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" min-width="280">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button link type="primary" @click="manageUsers(row.id)">管理账号</el-button>
              <el-button
                v-if="!isSupplierBanned(row)"
                v-perm="'supplier:update'"
                link
                type="danger"
                @click="handleBanSupplier(row)"
              >
                封禁
              </el-button>
              <el-button
                v-else
                v-perm="'supplier:update'"
                link
                type="success"
                @click="handleUnbanSupplier(row)"
              >
                解封
              </el-button>
              <el-button
                v-if="isSupplierBiddingSuspended(row)"
                v-perm="'supplier:update'"
                link
                type="warning"
                @click="handleReleaseBiddingSuspension(row)"
              >
                解除暂停
              </el-button>
              <el-button v-perm="'supplier:update'" link type="primary" @click="openEdit(row.id)">编辑</el-button>
              <el-button
                v-perm="'supplier:delete'"
                link
                type="danger"
                :disabled="Boolean(row.linkedUserCount)"
                @click="handleDelete(row)"
              >
                删除
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager-box">
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
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogMode === 'create' ? '新增供应商' : '编辑供应商'" width="900px">
      <el-form ref="formRef" :model="formModel" :rules="formRules" label-width="118px" class="dialog-form">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="供应商编码">
              <el-input v-model="formModel.supplierCode" clearable placeholder="留空自动生成" />
              <div class="field-tip">系统会自动生成唯一供应商编码，适合初始化快速建档。</div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="供应商名称" prop="supplierName">
              <el-input v-model="formModel.supplierName" placeholder="请输入供应商名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="供应商简称">
              <el-input v-model="formModel.supplierShortName" clearable placeholder="选填" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="税号" prop="taxNo">
              <el-input v-model="formModel.taxNo" placeholder="请输入税号" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="供应商类型" prop="supplierType">
              <el-select v-model="formModel.supplierType">
                <el-option label="生产商" value="MANUFACTURER" />
                <el-option label="经销商" value="DISTRIBUTOR" />
                <el-option label="服务商" value="SERVICE" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="区域 ID" prop="regionId">
              <el-input-number v-model="formModel.regionId" :min="1" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系人" prop="contactName">
              <el-input v-model="formModel.contactName" placeholder="请输入联系人" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系电话" prop="contactPhone">
              <el-input v-model="formModel.contactPhone" placeholder="请输入联系电话" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系邮箱">
              <el-input v-model="formModel.contactEmail" clearable placeholder="选填" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="准入状态">
              <el-select v-model="formModel.onboardingStatus">
                <el-option label="待审核" value="PENDING" />
                <el-option label="已通过" value="APPROVED" />
                <el-option label="已拒绝" value="REJECTED" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="启用状态">
              <el-select v-model="formModel.status">
                <el-option label="启用" value="ACTIVE" />
                <el-option label="停用" value="INACTIVE" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="封禁报价">
              <el-radio-group v-model="formModel.blacklistFlag">
                <el-radio :value="0">否</el-radio>
                <el-radio :value="1">是</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="数据隔离">
              <el-radio-group v-model="formModel.dataIsolationFlag">
                <el-radio :value="1">开启</el-radio>
                <el-radio :value="0">关闭</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="准入日期">
              <el-date-picker v-model="formModel.entryDate" type="date" value-format="YYYY-MM-DD" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="地址">
              <el-input v-model="formModel.address" clearable placeholder="选填" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注">
              <el-input v-model="formModel.remark" type="textarea" rows="3" placeholder="选填" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import type { FormInstance, FormRules } from "element-plus";
import { ElMessage, ElMessageBox } from "element-plus";
import PageHero from "../../components/PageHero.vue";
import {
  banSupplierApi,
  createSupplierApi,
  deleteSupplierApi,
  getSupplierDetailApi,
  getSupplierPageApi,
  releaseSupplierBiddingSuspensionApi,
  unbanSupplierApi,
  updateSupplierApi
} from "../../api/supplier";
import type { SupplierForm, SupplierVO } from "../../types/supplier";

const route = useRoute();
const router = useRouter();

const loading = ref(false);
const submitLoading = ref(false);
const tableData = ref<SupplierVO[]>([]);
const total = ref(0);

const queryModel = reactive({
  pageNo: 1,
  pageSize: 10,
  supplierCode: "",
  supplierName: "",
  onboardingStatus: "",
  status: ""
});

const activeFilterCount = computed(
  () => [queryModel.supplierCode, queryModel.supplierName, queryModel.onboardingStatus, queryModel.status].filter(Boolean).length
);

const linkedAccountTotal = computed(() => tableData.value.reduce((sum, item) => sum + (item.linkedUserCount || 0), 0));

const dialogVisible = ref(false);
const dialogMode = ref<"create" | "edit">("create");
const currentId = ref<number | null>(null);
const formRef = ref<FormInstance>();

const createDefaultForm = (): SupplierForm => ({
  supplierCode: "",
  supplierName: "",
  supplierShortName: "",
  taxNo: "",
  supplierType: "DISTRIBUTOR",
  regionId: 1,
  contactName: "",
  contactPhone: "",
  contactEmail: "",
  address: "",
  bankName: "",
  bankAccount: "",
  invoiceTitle: "",
  defaultPaymentTerm: "",
  ratingLevel: "",
  onboardingStatus: "PENDING",
  status: "ACTIVE",
  blacklistFlag: 0,
  dataIsolationFlag: 1,
  blacklistReason: "",
  entryDate: "",
  remark: ""
});

const formModel = reactive<SupplierForm>(createDefaultForm());

const formRules: FormRules<SupplierForm> = {
  supplierName: [{ required: true, message: "请输入供应商名称", trigger: "blur" }],
  taxNo: [{ required: true, message: "请输入税号", trigger: "blur" }],
  supplierType: [{ required: true, message: "请选择供应商类型", trigger: "change" }],
  regionId: [{ required: true, message: "请输入区域 ID", trigger: "change" }],
  contactName: [{ required: true, message: "请输入联系人", trigger: "blur" }],
  contactPhone: [{ required: true, message: "请输入联系电话", trigger: "blur" }]
};

function onboardingTagType(status: string): "warning" | "success" | "danger" | "info" {
  if (status === "APPROVED") {
    return "success";
  }
  if (status === "REJECTED") {
    return "danger";
  }
  if (status === "PENDING") {
    return "warning";
  }
  return "info";
}

function isSupplierBiddingSuspended(row: SupplierVO): boolean {
  return Boolean(row.bidSuspendedUntil && new Date(row.bidSuspendedUntil).getTime() > Date.now());
}

function isSupplierBanned(row: SupplierVO): boolean {
  return Number(row.blacklistFlag || 0) === 1;
}

function formatDateTime(value?: string | null): string {
  if (!value) {
    return "-";
  }
  return value.replace("T", " ").slice(0, 16);
}

async function loadTableData(): Promise<void> {
  loading.value = true;
  try {
    const data = await getSupplierPageApi({
      pageNo: queryModel.pageNo,
      pageSize: queryModel.pageSize,
      supplierCode: queryModel.supplierCode || undefined,
      supplierName: queryModel.supplierName || undefined,
      onboardingStatus: queryModel.onboardingStatus || undefined,
      status: queryModel.status || undefined
    });
    tableData.value = data.records;
    total.value = data.total;
  } finally {
    loading.value = false;
  }
}

function applyFocusFromRoute(): void {
  const focus = Number(route.query.focus ?? "");
  if (!Number.isFinite(focus) || focus <= 0) {
    return;
  }
  const hit = tableData.value.find((item) => item.id === focus);
  if (hit) {
    queryModel.supplierCode = hit.supplierCode;
  }
}

function handleSearch(): void {
  queryModel.pageNo = 1;
  void loadTableData();
}

function handleResetQuery(): void {
  queryModel.supplierCode = "";
  queryModel.supplierName = "";
  queryModel.onboardingStatus = "";
  queryModel.status = "";
  queryModel.pageNo = 1;
  void loadTableData();
}

function handlePageChange(pageNo: number): void {
  queryModel.pageNo = pageNo;
  void loadTableData();
}

function handleSizeChange(pageSize: number): void {
  queryModel.pageSize = pageSize;
  queryModel.pageNo = 1;
  void loadTableData();
}

function resetForm(): void {
  Object.assign(formModel, createDefaultForm());
}

function openCreate(): void {
  dialogMode.value = "create";
  currentId.value = null;
  resetForm();
  dialogVisible.value = true;
}

async function openEdit(id: number): Promise<void> {
  dialogMode.value = "edit";
  currentId.value = id;
  const detail = await getSupplierDetailApi(id);
  Object.assign(formModel, {
    ...createDefaultForm(),
    ...detail
  });
  dialogVisible.value = true;
}

async function handleSubmit(): Promise<void> {
  if (!formRef.value) {
    return;
  }
  const valid = await formRef.value.validate().catch(() => false);
  if (!valid) {
    return;
  }
  submitLoading.value = true;
  try {
    const payload: SupplierForm = {
      ...formModel,
      supplierCode: formModel.supplierCode?.trim() || undefined
    };
    if (dialogMode.value === "create") {
      await createSupplierApi(payload);
      ElMessage.success("新增供应商成功");
    } else if (currentId.value) {
      await updateSupplierApi(currentId.value, payload);
      ElMessage.success("更新供应商成功");
    }
    dialogVisible.value = false;
    await loadTableData();
  } finally {
    submitLoading.value = false;
  }
}

async function handleDelete(row: SupplierVO): Promise<void> {
  if (row.linkedUserCount) {
    ElMessage.warning("该供应商已关联账号，请先在用户管理中解绑账号后再删除");
    return;
  }
  await ElMessageBox.confirm(`删除供应商“${row.supplierName}”后不可恢复，确认继续吗？`, "删除确认", { type: "warning" });
  await deleteSupplierApi(row.id);
  ElMessage.success("删除成功");
  await loadTableData();
}

async function handleReleaseBiddingSuspension(row: SupplierVO): Promise<void> {
  await ElMessageBox.confirm(
    `确认解除供应商“${row.supplierName}”的暂停投标状态？解除后可重新参与报价、定标和递补。`,
    "解除暂停确认",
    {
      type: "warning",
      confirmButtonText: "解除暂停",
      cancelButtonText: "取消"
    }
  );
  await releaseSupplierBiddingSuspensionApi(row.id);
  ElMessage.success("已解除暂停投标");
  await loadTableData();
}

async function handleBanSupplier(row: SupplierVO): Promise<void> {
  const result = await ElMessageBox.prompt(
    `确认封禁供应商“${row.supplierName}”？封禁后该供应商无法提交报价，也不会参与自动定标和递补。`,
    "封禁供应商",
    {
      type: "warning",
      confirmButtonText: "封禁",
      cancelButtonText: "取消",
      inputType: "textarea",
      inputPlaceholder: "填写封禁原因，供应商进入报价页时会看到该提示"
    }
  ).catch(() => null);
  if (!result) {
    return;
  }
  await banSupplierApi(row.id, { banReason: result.value || null });
  ElMessage.success("已封禁供应商");
  await loadTableData();
}

async function handleUnbanSupplier(row: SupplierVO): Promise<void> {
  await ElMessageBox.confirm(
    `确认解封供应商“${row.supplierName}”？解封后如未处于暂停投标期，可重新参与报价。`,
    "解封供应商",
    {
      type: "warning",
      confirmButtonText: "解封",
      cancelButtonText: "取消"
    }
  );
  await unbanSupplierApi(row.id);
  ElMessage.success("已解封供应商");
  await loadTableData();
}

function manageUsers(supplierId: number): void {
  void router.push({ path: "/platform/users", query: { supplierId: String(supplierId) } });
}

watch(
  () => route.query.focus,
  () => {
    applyFocusFromRoute();
  }
);

onMounted(async () => {
  await loadTableData();
  applyFocusFromRoute();
});
</script>

<style scoped>
.supplier-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.supplier-title {
  font-weight: 600;
  color: var(--app-text);
}

.supplier-subtitle {
  font-size: 13px;
  color: var(--app-text-soft);
}

.linked-users {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.linked-count,
.table-placeholder {
  font-size: 13px;
  color: var(--app-text-soft);
}

.field-tip {
  margin-top: 8px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--app-text-soft);
}

.filter-form :deep(.supplier-filter-select) {
  width: 132px;
  min-width: 132px;
}

.filter-form :deep(.supplier-filter-select .el-select__wrapper) {
  min-height: 32px;
}

.filter-form :deep(.supplier-filter-select .el-select__caret) {
  width: 14px;
  font-size: 14px;
}
</style>
