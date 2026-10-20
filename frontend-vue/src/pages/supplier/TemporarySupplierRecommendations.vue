<template>
  <div class="page-container">
    <PageHero
      eyebrow="Temporary Supplier"
      title="推荐供应商审批"
      description="楼栋可为本次采购推荐临时供应商报价；报价先进入现有供应商报价流程，推荐审批未通过前不能中标。"
    >
      <div class="hero-inline">
        <span class="hero-pill">共 {{ total }} 条</span>
        <span class="hero-pill hero-pill--muted">待我处理 {{ reviewableCount }} 条</span>
      </div>
      <template #actions>
        <el-button type="primary" @click="openCreateDialog">新增推荐</el-button>
      </template>
      <template #aside>
        <div class="hero-stat-panel">
          <p class="hero-stat-kicker">Scoped Quote</p>
          <p class="hero-stat-value">{{ reviewableCount }}</p>
          <p class="hero-stat-note">临时供应商只可报价被推荐的明细，且不参与三家报价加速截止规则。</p>
        </div>
      </template>
    </PageHero>

    <el-card class="panel-card filter-form page-section" shadow="never">
      <el-form :inline="true" :model="queryModel">
        <el-form-item label="关键词">
          <el-input
            v-model="queryModel.keyword"
            clearable
            placeholder="推荐编号 / 申请单号 / 供应商"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="采购申请 ID">
          <el-input-number v-model="queryModel.purchaseRequestId" :min="1" :controls="false" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryModel.status" clearable placeholder="全部状态" class="status-select">
            <el-option label="楼长待审" value="PENDING_ROOM_MANAGER" />
            <el-option label="区域经理待审" value="PENDING_REGION_MANAGER" />
            <el-option label="已通过" value="APPROVED" />
            <el-option label="已拒绝" value="REJECTED" />
            <el-option label="已取消" value="CANCELLED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="panel-card table-panel page-section" shadow="never">
      <div class="table-toolbar">
        <div>
          <h3 class="card-title">推荐记录</h3>
          <p class="sub-text">审批通过后，临时供应商的报价才可参与后续定标；未通过或未审批的报价只作候选参考。</p>
        </div>
      </div>

      <el-table v-loading="loading" :data="tableData" stripe>
        <el-table-column label="推荐信息" min-width="260">
          <template #default="{ row }">
            <div class="main-cell">
              <div class="main-title">{{ row.recommendationNo }}</div>
              <div class="main-subtitle">{{ row.requestNo || "-" }} / {{ row.requestTitle || "-" }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="临时供应商" min-width="220">
          <template #default="{ row }">
            <div class="main-cell">
              <div class="main-title">{{ row.supplierName }}</div>
              <div class="main-subtitle">{{ row.supplierCode || "-" }} / {{ row.contactName || "-" }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="deptName" label="部门/楼栋" min-width="180">
          <template #default="{ row }">{{ row.deptName || "-" }}</template>
        </el-table-column>
        <el-table-column prop="itemCount" label="明细数" width="90" />
        <el-table-column label="当前节点" min-width="150">
          <template #default="{ row }">
            <span>{{ row.currentRoleName || "-" }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" effect="light">
              {{ row.statusText || statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="推荐人" min-width="120">
          <template #default="{ row }">{{ row.recommendedByName || "-" }}</template>
        </el-table-column>
        <el-table-column label="更新时间" min-width="170">
          <template #default="{ row }">{{ row.updatedAt || row.createdAt || "-" }}</template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" width="220">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button link type="primary" @click="openDetailDialog(row.id)">详情</el-button>
              <el-button v-if="row.currentUserCanReview" link type="success" @click="handleApprove(row)">通过</el-button>
              <el-button v-if="row.currentUserCanReview" link type="danger" @click="handleReject(row)">拒绝</el-button>
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

    <el-dialog v-model="createVisible" title="新增推荐供应商" width="980px">
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="118px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="采购申请 ID" prop="purchaseRequestId">
              <div class="request-load-line">
                <el-input-number v-model="createForm.purchaseRequestId" :min="1" :controls="false" />
                <el-button :loading="requestLoading" @click="loadRequestItems">加载明细</el-button>
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="供应商名称" prop="supplierName">
              <el-input v-model="createForm.supplierName" placeholder="请输入临时供应商名称" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="联系人" prop="contactName">
              <el-input v-model="createForm.contactName" placeholder="请输入联系人" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="联系电话" prop="contactPhone">
              <el-input v-model="createForm.contactPhone" placeholder="请输入联系电话" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="联系邮箱">
              <el-input v-model="createForm.contactEmail" clearable placeholder="选填" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="推荐原因" prop="recommendReason">
              <el-input
                v-model="createForm.recommendReason"
                type="textarea"
                :rows="3"
                maxlength="300"
                show-word-limit
                placeholder="说明推荐理由，例如本楼栋现有供应商可提供更低价格或特殊交期。"
              />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注">
              <el-input v-model="createForm.remark" type="textarea" :rows="2" maxlength="300" show-word-limit />
            </el-form-item>
          </el-col>
        </el-row>

        <div class="request-summary" v-if="loadedRequest">
          <span>{{ loadedRequest.reqNo }}</span>
          <span>{{ loadedRequest.reqTitle }}</span>
          <span>{{ loadedRequest.deptName }}</span>
        </div>

        <el-table
          v-loading="requestLoading"
          class="item-table"
          :data="requestItems"
          max-height="360"
          stripe
          @selection-change="handleItemSelectionChange"
        >
          <el-table-column type="selection" width="48" />
          <el-table-column prop="lineNo" label="行号" width="70" />
          <el-table-column prop="itemName" label="商品名称" min-width="180" />
          <el-table-column prop="reqType" label="采购类型" min-width="150">
            <template #default="{ row }">{{ reqTypeLabel(row.reqType) }}</template>
          </el-table-column>
          <el-table-column prop="specModel" label="规格型号" min-width="150" />
          <el-table-column label="数量" width="110">
            <template #default="{ row }">{{ row.qty }} {{ row.unit }}</template>
          </el-table-column>
          <el-table-column prop="purpose" label="用途" min-width="180" />
        </el-table>
        <div class="selected-tip">已选择 {{ createForm.itemIds.length }} 条明细</div>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="submitCreate">提交推荐</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="detailVisible" title="推荐供应商详情" width="920px">
      <el-descriptions v-if="detailData" border :column="2">
        <el-descriptions-item label="推荐编号">{{ detailData.recommendationNo }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ detailData.statusText || statusLabel(detailData.status) }}</el-descriptions-item>
        <el-descriptions-item label="采购申请">{{ detailData.requestNo }} / {{ detailData.requestTitle }}</el-descriptions-item>
        <el-descriptions-item label="临时供应商">{{ detailData.supplierName }} / {{ detailData.supplierCode }}</el-descriptions-item>
        <el-descriptions-item label="联系人">{{ detailData.contactName }} / {{ detailData.contactPhone }}</el-descriptions-item>
        <el-descriptions-item label="推荐人">{{ detailData.recommendedByName || "-" }}</el-descriptions-item>
        <el-descriptions-item label="推荐原因" :span="2">{{ detailData.recommendReason || "-" }}</el-descriptions-item>
        <el-descriptions-item label="楼长意见">{{ detailData.roomManagerComment || "-" }}</el-descriptions-item>
        <el-descriptions-item label="区域经理意见">{{ detailData.regionManagerComment || "-" }}</el-descriptions-item>
        <el-descriptions-item v-if="detailData.rejectReason" label="拒绝原因" :span="2">
          {{ detailData.rejectReason }}
        </el-descriptions-item>
      </el-descriptions>
      <el-table v-if="detailData" class="item-table" :data="detailData.items || []" stripe>
        <el-table-column prop="lineNo" label="行号" width="70" />
        <el-table-column prop="itemName" label="商品名称" min-width="180" />
        <el-table-column prop="reqType" label="采购类型" min-width="150">
          <template #default="{ row }">{{ reqTypeLabel(row.reqType) }}</template>
        </el-table-column>
        <el-table-column prop="specModel" label="规格型号" min-width="150" />
        <el-table-column label="数量" width="110">
          <template #default="{ row }">{{ row.qty }} {{ row.unit }}</template>
        </el-table-column>
        <el-table-column prop="purpose" label="用途" min-width="180" />
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import type { FormInstance, FormRules } from "element-plus";
import { ElMessage, ElMessageBox } from "element-plus";
import PageHero from "../../components/PageHero.vue";
import { getWorkflowDetailApi } from "../../api/workflow";
import {
  approveTemporarySupplierRecommendationApi,
  createTemporarySupplierRecommendationApi,
  getTemporarySupplierRecommendationDetailApi,
  getTemporarySupplierRecommendationsPageApi,
  rejectTemporarySupplierRecommendationApi
} from "../../api/supplier";
import type { PurchaseRequestItem, PurchaseRequestVO } from "../../types/purchase";
import type {
  TemporarySupplierRecommendationCreateForm,
  TemporarySupplierRecommendationPageQuery,
  TemporarySupplierRecommendationVO
} from "../../types/supplier";

const queryModel = reactive<TemporarySupplierRecommendationPageQuery>({
  pageNo: 1,
  pageSize: 10,
  keyword: "",
  status: "",
  purchaseRequestId: null
});

const loading = ref(false);
const tableData = ref<TemporarySupplierRecommendationVO[]>([]);
const total = ref(0);
const reviewableCount = computed(() => tableData.value.filter((item) => item.currentUserCanReview).length);

const createVisible = ref(false);
const createFormRef = ref<FormInstance>();
const requestLoading = ref(false);
const submitLoading = ref(false);
const loadedRequest = ref<PurchaseRequestVO | null>(null);
const requestItems = ref<PurchaseRequestItem[]>([]);
const createForm = reactive<TemporarySupplierRecommendationCreateForm>({
  purchaseRequestId: null,
  supplierName: "",
  contactName: "",
  contactPhone: "",
  contactEmail: "",
  recommendReason: "",
  remark: "",
  itemIds: []
});

const createRules: FormRules = {
  purchaseRequestId: [{ required: true, message: "请填写采购申请 ID", trigger: "blur" }],
  supplierName: [{ required: true, message: "请填写临时供应商名称", trigger: "blur" }],
  contactName: [{ required: true, message: "请填写联系人", trigger: "blur" }],
  contactPhone: [{ required: true, message: "请填写联系电话", trigger: "blur" }],
  recommendReason: [{ required: true, message: "请填写推荐原因", trigger: "blur" }]
};

const detailVisible = ref(false);
const detailData = ref<TemporarySupplierRecommendationVO | null>(null);

onMounted(() => {
  fetchData();
});

async function fetchData(): Promise<void> {
  loading.value = true;
  try {
    const data = await getTemporarySupplierRecommendationsPageApi({
      ...queryModel,
      keyword: queryModel.keyword?.trim() || undefined,
      status: queryModel.status || undefined,
      purchaseRequestId: queryModel.purchaseRequestId || undefined
    });
    tableData.value = data.records || [];
    total.value = data.total || 0;
  } finally {
    loading.value = false;
  }
}

function handleSearch(): void {
  queryModel.pageNo = 1;
  fetchData();
}

function handleReset(): void {
  queryModel.pageNo = 1;
  queryModel.pageSize = 10;
  queryModel.keyword = "";
  queryModel.status = "";
  queryModel.purchaseRequestId = null;
  fetchData();
}

function handlePageChange(page: number): void {
  queryModel.pageNo = page;
  fetchData();
}

function handleSizeChange(size: number): void {
  queryModel.pageNo = 1;
  queryModel.pageSize = size;
  fetchData();
}

function openCreateDialog(): void {
  createVisible.value = true;
  loadedRequest.value = null;
  requestItems.value = [];
  Object.assign(createForm, {
    purchaseRequestId: null,
    supplierName: "",
    contactName: "",
    contactPhone: "",
    contactEmail: "",
    recommendReason: "",
    remark: "",
    itemIds: []
  });
  createFormRef.value?.clearValidate();
}

async function loadRequestItems(): Promise<void> {
  if (!createForm.purchaseRequestId) {
    ElMessage.warning("请先填写采购申请 ID");
    return;
  }
  requestLoading.value = true;
  try {
    const detail = await getWorkflowDetailApi(createForm.purchaseRequestId);
    loadedRequest.value = detail;
    requestItems.value = (detail.items || []).filter((item) => item.id && item.status !== "REJECTED");
    createForm.itemIds = [];
    if (!requestItems.value.length) {
      ElMessage.warning("该采购申请没有可推荐报价的有效明细");
    }
  } finally {
    requestLoading.value = false;
  }
}

function handleItemSelectionChange(rows: PurchaseRequestItem[]): void {
  createForm.itemIds = rows.map((row) => row.id).filter((id): id is number => typeof id === "number");
}

async function submitCreate(): Promise<void> {
  await createFormRef.value?.validate();
  if (!createForm.purchaseRequestId) {
    ElMessage.warning("请填写采购申请 ID");
    return;
  }
  if (!loadedRequest.value || loadedRequest.value.id !== createForm.purchaseRequestId) {
    ElMessage.warning("请先加载采购申请明细");
    return;
  }
  if (!createForm.itemIds.length) {
    ElMessage.warning("请选择至少一条报价明细");
    return;
  }
  submitLoading.value = true;
  try {
    await createTemporarySupplierRecommendationApi({
      ...createForm,
      contactEmail: createForm.contactEmail?.trim() || null,
      remark: createForm.remark?.trim() || null
    });
    ElMessage.success("已提交推荐供应商审批");
    createVisible.value = false;
    fetchData();
  } finally {
    submitLoading.value = false;
  }
}

async function openDetailDialog(id: number): Promise<void> {
  detailData.value = await getTemporarySupplierRecommendationDetailApi(id);
  detailVisible.value = true;
}

async function handleApprove(row: TemporarySupplierRecommendationVO): Promise<void> {
  const { value } = await ElMessageBox.prompt("请填写审批意见", "通过推荐供应商", {
    inputValue: "同意",
    inputPattern: /\S+/,
    inputErrorMessage: "审批意见不能为空",
    confirmButtonText: "通过",
    cancelButtonText: "取消",
    type: "success"
  });
  await approveTemporarySupplierRecommendationApi(row.id, { comment: value });
  ElMessage.success("已通过推荐供应商审批");
  fetchData();
}

async function handleReject(row: TemporarySupplierRecommendationVO): Promise<void> {
  const { value } = await ElMessageBox.prompt("请填写拒绝原因", "拒绝推荐供应商", {
    inputPattern: /\S+/,
    inputErrorMessage: "拒绝原因不能为空",
    confirmButtonText: "拒绝",
    cancelButtonText: "取消",
    type: "warning"
  });
  await rejectTemporarySupplierRecommendationApi(row.id, { comment: value });
  ElMessage.success("已拒绝推荐供应商");
  fetchData();
}

function statusLabel(status?: string | null): string {
  const map: Record<string, string> = {
    PENDING_ROOM_MANAGER: "楼长待审",
    PENDING_REGION_MANAGER: "区域经理待审",
    APPROVED: "已通过",
    REJECTED: "已拒绝",
    CANCELLED: "已取消"
  };
  return status ? map[status] || status : "-";
}

function statusTagType(status?: string | null): "success" | "warning" | "info" | "danger" {
  if (status === "APPROVED") {
    return "success";
  }
  if (status === "REJECTED" || status === "CANCELLED") {
    return "danger";
  }
  if (status?.startsWith("PENDING")) {
    return "warning";
  }
  return "info";
}

function reqTypeLabel(value?: string | null): string {
  const map: Record<string, string> = {
    PROJECT: "工程项目",
    ASSET: "实物资产/无形资产/长摊等",
    EXPENSE: "服务/费用/低值易耗等"
  };
  return value ? map[value] || value : "-";
}
</script>

<style scoped>
.status-select {
  width: 180px;
}

.main-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.main-title {
  font-weight: 700;
  color: #10244d;
}

.main-subtitle {
  color: #5d6f94;
  font-size: 12px;
}

.table-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.request-load-line {
  display: flex;
  gap: 8px;
  width: 100%;
}

.request-load-line :deep(.el-input-number) {
  flex: 1;
}

.request-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin: 4px 0 12px;
  padding: 10px 12px;
  border-radius: 6px;
  background: #f5f8ff;
  color: #26456f;
  font-size: 13px;
}

.item-table {
  margin-top: 12px;
}

.selected-tip {
  margin-top: 10px;
  color: #5d6f94;
  font-size: 13px;
}
</style>
