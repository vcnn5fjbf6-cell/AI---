<template>
  <VnetPage title="采购申请">
    <template #actions>
      <el-button type="primary" @click="goCreate">新建采购申请</el-button>
    </template>

    <VnetPanel title="申请筛选">
      <el-form :inline="true" :model="queryModel" class="vnet-search-form">
        <el-form-item label="单号">
          <el-input v-model="queryModel.reqNo" clearable placeholder="输入申请单号" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="标题">
          <el-input v-model="queryModel.reqTitle" clearable placeholder="输入申请标题" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="业务状态">
          <el-select v-model="queryModel.status" clearable placeholder="全部状态">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="待审批" value="PENDING_APPROVAL" />
            <el-option label="需求池" value="DEMAND_POOL" />
            <el-option label="询价中" value="QUOTING" />
            <el-option label="已通过" value="APPROVED" />
            <el-option label="已驳回" value="REJECTED" />
            <el-option label="已退回" value="RETURNED" />
            <el-option label="已关闭" value="CANCELLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="审批状态">
          <el-select v-model="queryModel.approvalStatus" clearable placeholder="全部状态">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="待审批" value="PENDING" />
            <el-option label="通过" value="APPROVED" />
            <el-option label="驳回" value="REJECTED" />
            <el-option label="退回" value="RETURNED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </VnetPanel>

    <VnetPanel title="申请列表">
      <el-table v-loading="loading" :data="tableData" stripe>
        <el-table-column prop="reqNo" label="申请单号" min-width="170" />
        <el-table-column prop="reqTitle" label="申请标题" min-width="220" />
        <el-table-column prop="reqType" label="汇总类型" width="110" />
        <el-table-column prop="deptName" label="部门" min-width="130" />
        <el-table-column label="申请人" min-width="130">
          <template #default="{ row }">{{ applicantLabel(row) }}</template>
        </el-table-column>
        <el-table-column label="金额" width="130">
          <template #default="{ row }">{{ formatMoney(row.totalAmount) }}</template>
        </el-table-column>
        <el-table-column label="当前节点" width="120">
          <template #default="{ row }">{{ requestCurrentNodeText(row) }}</template>
        </el-table-column>
        <el-table-column prop="needDate" label="需求日期" width="120" />
        <el-table-column label="审批状态" width="110">
          <template #default="{ row }">
            <el-tag :type="approvalTagType(row.approvalStatus)" effect="light">
              {{ approvalStatusText(row) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="业务状态" width="110">
          <template #default="{ row }">{{ businessStatusText(row.status) }}</template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" min-width="160" />
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button link type="primary" @click="openDetail(row.id)">详情</el-button>
              <el-button v-if="canEdit(row)" link type="primary" @click="goEdit(row.id)">编辑</el-button>
              <el-button v-if="canEdit(row)" link type="warning" @click="submitRequest(row.id)">
                {{ row.status === "REJECTED" ? "重新提交" : "提交" }}
              </el-button>
              <el-button
                v-if="canClose(row)"
                link
                type="danger"
                :loading="closingRequestId === row.id"
                @click="closeRequest(row)"
              >
                关闭
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
    </VnetPanel>

    <el-dialog
      v-model="detailVisible"
      title="采购申请详情"
      width="92vw"
      top="4vh"
      destroy-on-close
      class="request-detail-dialog"
    >
      <div v-loading="detailLoading" class="request-detail-body">
      <el-descriptions v-if="currentDetail" :column="4" border class="detail-summary">
        <el-descriptions-item label="申请单号">{{ currentDetail.reqNo }}</el-descriptions-item>
        <el-descriptions-item label="当前节点">{{ requestCurrentNodeText(currentDetail) }}</el-descriptions-item>
        <el-descriptions-item label="审批状态">
          <el-tag :type="approvalTagType(currentDetail.approvalStatus)" effect="light">
            {{ approvalStatusText(currentDetail) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="业务状态">{{ businessStatusText(currentDetail.status) }}</el-descriptions-item>
        <el-descriptions-item label="申请标题" :span="4">{{ currentDetail.reqTitle }}</el-descriptions-item>
        <el-descriptions-item label="汇总类型">{{ currentDetail.reqType }}</el-descriptions-item>
        <el-descriptions-item label="总金额">{{ formatMoney(currentDetail.totalAmount) }}</el-descriptions-item>
        <el-descriptions-item label="申请人">{{ applicantLabel(currentDetail) }}</el-descriptions-item>
        <el-descriptions-item label="部门">{{ currentDetail.deptName || "-" }}</el-descriptions-item>
        <el-descriptions-item label="收货城市">{{ currentDetail.receivingCity || "-" }}</el-descriptions-item>
        <el-descriptions-item label="需求日期">{{ currentDetail.needDate || "-" }}</el-descriptions-item>
      </el-descriptions>

      <div v-if="currentDetail" class="detail-section-title">申请明细</div>
      <el-table v-if="currentDetail" :data="currentDetail.items || []" stripe class="detail-table" max-height="420">
        <el-table-column prop="lineNo" label="行号" width="70" />
        <el-table-column prop="itemCode" label="物料编码" min-width="130" />
        <el-table-column prop="itemName" label="商品名称" min-width="160" />
        <el-table-column prop="reqType" label="采购类型" min-width="170" show-overflow-tooltip />
        <el-table-column prop="specModel" label="规格型号" min-width="130" />
        <el-table-column prop="receivingCity" label="收货城市" width="110" />
        <el-table-column prop="qty" label="数量" width="90" />
        <el-table-column prop="unit" label="单位" width="80" />
        <el-table-column label="金额" width="120">
          <template #default="{ row }">{{ formatMoney(row.lineAmount || row.qty * row.unitPrice) }}</template>
        </el-table-column>
        <el-table-column label="最终状态" width="110">
          <template #default="{ row }">
            <el-tag :type="itemStatusTagType(row.status)" effect="plain">
              {{ itemStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="采购原因/退货或驳回原因" min-width="240" show-overflow-tooltip>
          <template #default="{ row }">{{ itemReasonText(row) }}</template>
        </el-table-column>
        <el-table-column prop="deliveryAddress" label="送达地址" min-width="220" show-overflow-tooltip />
        <el-table-column label="电商链接" min-width="120">
          <template #default="{ row }">
            <el-link v-if="row.ecommerceUrl" :href="normalizeUrl(row.ecommerceUrl)" target="_blank" type="primary">打开</el-link>
            <span v-else>-</span>
          </template>
        </el-table-column>
      </el-table>
      </div>
    </el-dialog>
  </VnetPage>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import VnetPage from "../../components/vnet/VnetPage.vue";
import VnetPanel from "../../components/vnet/VnetPanel.vue";
import {
  closeReturnedWorkflowRequestApi,
  getWorkflowDetailApi,
  getWorkflowPageApi,
  previewSubmitWorkflowRequestApi,
  submitWorkflowRequestApi
} from "../../api/workflow";
import { useAuthStore } from "../../stores/auth";
import type { PurchaseRequestItem, PurchaseRequestVO } from "../../types/purchase";
import { confirmSubmitNextApprovalDialog } from "../../utils/workflow-submit";

const router = useRouter();
const authStore = useAuthStore();

const loading = ref(false);
const tableData = ref<PurchaseRequestVO[]>([]);
const total = ref(0);
const detailVisible = ref(false);
const detailLoading = ref(false);
const closingRequestId = ref<number | null>(null);
const currentDetail = ref<PurchaseRequestVO | null>(null);
const SYSTEM_ADMIN_ROLES = ["SUPER_ADMIN", "SYS_ADMIN", "ADMIN"];

const queryModel = reactive({
  pageNo: 1,
  pageSize: 10,
  reqNo: "",
  reqTitle: "",
  status: "",
  approvalStatus: ""
});

async function loadTableData(): Promise<void> {
  loading.value = true;
  try {
    const data = await getWorkflowPageApi({
      pageNo: queryModel.pageNo,
      pageSize: queryModel.pageSize,
      reqNo: queryModel.reqNo || undefined,
      reqTitle: queryModel.reqTitle || undefined,
      status: queryModel.status || undefined,
      approvalStatus: queryModel.approvalStatus || undefined
    });
    tableData.value = data.records;
    total.value = data.total;
  } finally {
    loading.value = false;
  }
}

function handleSearch(): void {
  queryModel.pageNo = 1;
  loadTableData();
}

function resetQuery(): void {
  queryModel.reqNo = "";
  queryModel.reqTitle = "";
  queryModel.status = "";
  queryModel.approvalStatus = "";
  queryModel.pageNo = 1;
  loadTableData();
}

function handlePageChange(pageNo: number): void {
  queryModel.pageNo = pageNo;
  loadTableData();
}

function handleSizeChange(pageSize: number): void {
  queryModel.pageSize = pageSize;
  queryModel.pageNo = 1;
  loadTableData();
}

function goCreate(): void {
  router.push("/procurement/requests/create");
}

function goEdit(id: number): void {
  router.push(`/procurement/requests/${id}/edit`);
}

async function openDetail(id: number): Promise<void> {
  detailVisible.value = true;
  detailLoading.value = true;
  currentDetail.value = null;
  try {
    currentDetail.value = await getWorkflowDetailApi(id);
  } catch {
    detailVisible.value = false;
  } finally {
    detailLoading.value = false;
  }
}

function canEdit(row: PurchaseRequestVO): boolean {
  const editableStatus = row.status === "DRAFT" || row.status === "RETURNED" || row.status === "REJECTED";
  const owner = authStore.user?.userId === row.applicantUserId;
  return editableStatus && (owner || authStore.hasAnyRole(SYSTEM_ADMIN_ROLES));
}

function canClose(row: PurchaseRequestVO): boolean {
  const owner = authStore.user?.userId === row.applicantUserId;
  return row.status === "RETURNED" && (owner || authStore.hasAnyRole(SYSTEM_ADMIN_ROLES));
}

function applicantLabel(row: Partial<PurchaseRequestVO>): string {
  if (!row.applicantName && !row.applicantEmployeeNo) {
    return "-";
  }
  return row.applicantName || row.applicantEmployeeNo || "-";
}

async function submitRequest(id: number): Promise<void> {
  const previewResult = await previewSubmitWorkflowRequestApi(id);
  const confirmed = await confirmSubmitNextApprovalDialog(previewResult);
  if (!confirmed) {
    return;
  }
  await submitWorkflowRequestApi(id);
  ElMessage.success("提交成功");
  await loadTableData();
}

async function closeRequest(row: PurchaseRequestVO): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `确认关闭采购申请 ${row.reqNo}？关闭后不能再编辑或提交，历史记录仍会保留。`,
      "关闭退回申请",
      {
        type: "warning",
        confirmButtonText: "确认关闭",
        cancelButtonText: "取消"
      }
    );
  } catch {
    return;
  }

  closingRequestId.value = row.id;
  try {
    await closeReturnedWorkflowRequestApi(row.id);
    ElMessage.success("采购申请已关闭");
    await loadTableData();
  } finally {
    closingRequestId.value = null;
  }
}

function approvalStatusText(row: PurchaseRequestVO | string): string {
  if (typeof row !== "string" && isPartialApproved(row)) {
    return "部分通过";
  }
  if (typeof row !== "string" && row.status === "CANCELLED") {
    return "已关闭";
  }
  const status = typeof row === "string" ? row : row.approvalStatus;
  const map: Record<string, string> = {
    DRAFT: "草稿",
    PENDING: "待审批",
    APPROVED: "通过",
    REJECTED: "驳回",
    RETURNED: "退回",
    TERMINATED: "已终止"
  };
  return map[status] || status || "-";
}

function isPartialApproved(row: PurchaseRequestVO): boolean {
  if (row.approvalStatus !== "APPROVED" || !row.items?.length) {
    return false;
  }
  const hasRejected = row.items.some((item) => item.status === "REJECTED");
  const hasActive = row.items.some((item) => item.status !== "REJECTED");
  return hasRejected && hasActive;
}

function businessStatusText(status: string): string {
  const map: Record<string, string> = {
    DRAFT: "草稿",
    PENDING_APPROVAL: "待审批",
    DEMAND_POOL: "需求池",
    DEMAND_AGGREGATED: "已汇总",
    QUOTING: "询价中",
    APPROVED: "已通过",
    REJECTED: "已驳回",
    RETURNED: "已退回",
    CANCELLED: "已关闭",
    TERMINATED: "已终止"
  };
  return map[status] || status || "-";
}

function requestCurrentNodeText(row: Partial<PurchaseRequestVO>): string {
  if (row.currentNodeName) {
    return row.currentNodeName;
  }
  if (row.status === "PENDING_APPROVAL" && row.approvalStatus === "PENDING") {
    return nodeName(row.currentNodeNo);
  }
  if (row.approvalStatus === "DRAFT" || row.status === "DRAFT") {
    return "提交申请";
  }
  if (
    row.approvalStatus === "APPROVED" ||
    ["APPROVED", "DEMAND_POOL", "DEMAND_AGGREGATED", "QUOTING"].includes(row.status || "")
  ) {
    return "完成";
  }
  if (row.approvalStatus === "REJECTED" || row.status === "REJECTED") {
    return "已驳回";
  }
  if (row.approvalStatus === "RETURNED" || row.status === "RETURNED") {
    return "已退回";
  }
  if (row.status === "CANCELLED") {
    return "已关闭";
  }
  if (row.approvalStatus === "TERMINATED" || row.status === "TERMINATED") {
    return "已终止";
  }
  return nodeName(row.currentNodeNo);
}

function nodeName(nodeNo?: number): string {
  const map: Record<number, string> = {
    0: "提交申请",
    1: "值班长审核",
    2: "专业工程师审核",
    3: "楼长需求审核",
    4: "经理审核",
    5: "区域经理审核",
    6: "总监审核",
    7: "V7 审核",
    8: "V8 审核",
    10: "申请人替换商品确认"
  };
  return nodeNo == null ? "-" : map[nodeNo] || `节点 ${nodeNo}`;
}

function approvalTagType(status?: string): "warning" | "success" | "danger" | "info" {
  if (status === "PENDING") return "warning";
  if (status === "APPROVED") return "success";
  if (status === "REJECTED") return "danger";
  return "info";
}

function itemStatusText(status?: string): string {
  const map: Record<string, string> = {
    ACTIVE: "有效",
    REJECTED: "已驳回",
    RETURNED: "退货",
    CANCELLED: "已取消",
    WAIVED: "已放弃"
  };
  return map[status || ""] || status || "-";
}

function itemStatusTagType(status?: string): "success" | "danger" | "warning" | "info" {
  if (status === "ACTIVE") return "success";
  if (status === "REJECTED" || status === "RETURNED") return "danger";
  if (status === "WAIVED") return "warning";
  return "info";
}

function itemReasonText(row: PurchaseRequestItem): string {
  if (row.status === "REJECTED" || row.status === "RETURNED") {
    return row.remark || row.purpose || "-";
  }
  return row.purpose || row.remark || "-";
}

function formatMoney(value: number): string {
  return Number(value ?? 0).toLocaleString("zh-CN", { style: "currency", currency: "CNY", maximumFractionDigits: 0 });
}

function normalizeUrl(url: string): string {
  if (/^https?:\/\//i.test(url)) {
    return url;
  }
  return `https://${url}`;
}

onMounted(() => {
  loadTableData();
});
</script>

<style scoped>
:global(.request-detail-dialog .el-dialog__body) {
  max-height: calc(100vh - 148px);
  overflow: auto;
}

.detail-summary {
  margin-bottom: 16px;
}

.detail-section-title {
  margin: 18px 0 10px;
  color: #0f1f3d;
  font-size: 15px;
  font-weight: 700;
}

.detail-table {
  width: 100%;
}
</style>
