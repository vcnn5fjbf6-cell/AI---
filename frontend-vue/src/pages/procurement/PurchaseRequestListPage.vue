<template>
  <div class="page-container">
    <PageHero
      eyebrow="Procurement"
      title="采购申请列表"
      description="按工程师提报、逐级审批和供应商报价链路查看采购申请。"
    >
      <div class="hero-inline">
        <span class="hero-pill">当前 {{ total }} 条记录</span>
        <span class="hero-pill hero-pill--muted">筛选项 {{ activeFilterCount }} 个</span>
      </div>
      <template #actions>
        <el-button v-if="canCreateRequest" type="primary" @click="goCreate">新增采购申请</el-button>
      </template>
      <template #aside>
        <div class="hero-stat-panel">
          <p class="hero-stat-kicker">Pipeline</p>
          <p class="hero-stat-value">{{ total }}</p>
          <p class="hero-stat-note">从草稿、待审批到询价阶段，按状态收窄列表。</p>
        </div>
      </template>
    </PageHero>

    <el-card class="panel-card filter-form page-section" shadow="never">
      <el-form :inline="true" :model="queryModel">
        <el-form-item label="申请单号">
          <el-input v-model="queryModel.reqNo" placeholder="请输入申请单号" clearable />
        </el-form-item>
        <el-form-item label="申请标题">
          <el-input v-model="queryModel.reqTitle" placeholder="请输入申请标题" clearable />
        </el-form-item>
        <el-form-item label="业务状态">
          <el-select v-model="queryModel.status" clearable placeholder="全部">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="待审批" value="PENDING_APPROVAL" />
            <el-option label="需求池" value="DEMAND_POOL" />
            <el-option label="询价中" value="QUOTING" />
            <el-option label="已通过" value="APPROVED" />
            <el-option label="已驳回" value="REJECTED" />
            <el-option label="已退回" value="RETURNED" />
          </el-select>
        </el-form-item>
        <el-form-item label="审批状态">
          <el-select v-model="queryModel.approvalStatus" clearable placeholder="全部">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="待审批" value="PENDING" />
            <el-option label="通过" value="APPROVED" />
            <el-option label="驳回" value="REJECTED" />
            <el-option label="退回" value="RETURNED" />
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
          <h3 class="card-title">采购申请列表</h3>
          <p class="sub-text">面向工程师、审批人和管理者统一查看当前采购进度。</p>
        </div>
      </div>

      <el-table v-loading="loading" :data="tableData" stripe>
        <el-table-column prop="reqNo" label="申请单号" min-width="180" />
        <el-table-column prop="reqTitle" label="申请标题" min-width="220" />
        <el-table-column label="申请人" min-width="130">
          <template #default="{ row }">{{ applicantLabel(row) }}</template>
        </el-table-column>
        <el-table-column prop="totalAmount" label="申请金额" width="130">
          <template #default="{ row }">{{ formatMoney(row.totalAmount) }}</template>
        </el-table-column>
        <el-table-column prop="currentNodeNo" label="当前节点" width="140">
          <template #default="{ row }">{{ requestCurrentNodeText(row) }}</template>
        </el-table-column>
        <el-table-column prop="approvalStatus" label="审批状态" width="120">
          <template #default="{ row }">
            <el-tag :type="approvalTagType(row.approvalStatus)">{{ row.approvalStatus }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="业务状态" width="120">
          <template #default="{ row }">{{ businessStatusText(row.status) }}</template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" min-width="170" />
        <el-table-column label="操作" fixed="right" width="220">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button link type="info" @click="goDetail(row.id)">详情</el-button>
              <el-button v-if="canManageRequest(row)" link type="primary" @click="goEdit(row.id)">编辑</el-button>
              <el-button
                v-if="canManageRequest(row) && (row.status === 'DRAFT' || row.status === 'RETURNED')"
                link
                type="warning"
                @click="handleSubmitRequest(row.id)"
              >
                提交审批
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
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import PageHero from "../../components/PageHero.vue";
import { getWorkflowPageApi, previewSubmitWorkflowRequestApi, submitWorkflowRequestApi } from "../../api/workflow";
import { useAuthStore } from "../../stores/auth";
import type { PurchaseRequestVO } from "../../types/purchase";
import { confirmSubmitNextApprovalDialog } from "../../utils/workflow-submit";

const router = useRouter();
const authStore = useAuthStore();
const loading = ref(false);
const tableData = ref<PurchaseRequestVO[]>([]);
const total = ref(0);

const queryModel = reactive({
  pageNo: 1,
  pageSize: 10,
  reqNo: "",
  reqTitle: "",
  status: "",
  approvalStatus: ""
});

const activeFilterCount = computed(
  () => [queryModel.reqNo, queryModel.reqTitle, queryModel.status, queryModel.approvalStatus].filter(Boolean).length
);
const SYSTEM_ADMIN_ROLES = ["SUPER_ADMIN", "SYS_ADMIN", "ADMIN"];
const canCreateRequest = computed(() => authStore.hasAnyRole([...SYSTEM_ADMIN_ROLES, "ENGINEER", "PURCHASER"]));

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

function canManageRequest(row: PurchaseRequestVO): boolean {
  return authStore.hasAnyRole(SYSTEM_ADMIN_ROLES) || authStore.user?.userId === row.applicantUserId;
}

function applicantLabel(row: PurchaseRequestVO): string {
  if (!row.applicantName && !row.applicantEmployeeNo) {
    return "-";
  }
  return row.applicantName || row.applicantEmployeeNo || "-";
}

function handleSearch(): void {
  queryModel.pageNo = 1;
  loadTableData();
}

function handleResetQuery(): void {
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
  router.push("/purchase-requests/create");
}

function goEdit(id: number): void {
  router.push(`/purchase-requests/${id}/edit`);
}

function goDetail(id: number): void {
  router.push(`/purchase-requests/${id}`);
}

async function handleSubmitRequest(id: number): Promise<void> {
  const previewResult = await previewSubmitWorkflowRequestApi(id);
  const confirmed = await confirmSubmitNextApprovalDialog(previewResult);
  if (!confirmed) {
    return;
  }
  await submitWorkflowRequestApi(id);
  ElMessage.success("提交成功");
  await loadTableData();
}

function formatMoney(value: number): string {
  return Number(value ?? 0).toLocaleString("zh-CN", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  });
}

function approvalTagType(status: string): "warning" | "success" | "danger" | "info" {
  if (status === "PENDING") return "warning";
  if (status === "APPROVED") return "success";
  if (status === "REJECTED") return "danger";
  return "info";
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
    RETURNED: "已退回"
  };
  return map[status] || status || "-";
}

function requestCurrentNodeText(row: Partial<PurchaseRequestVO>): string {
  if (row.currentNodeName) {
    return row.currentNodeName;
  }
  if (row.status === "PENDING_APPROVAL" && row.approvalStatus === "PENDING") {
    return nodeLabel(row.currentNodeNo);
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
  if (row.approvalStatus === "REJECTED" || row.status === "REJECTED") return "已驳回";
  if (row.approvalStatus === "RETURNED" || row.status === "RETURNED") return "已退回";
  if (row.approvalStatus === "TERMINATED" || row.status === "TERMINATED") return "已终止";
  return nodeLabel(row.currentNodeNo);
}

function nodeLabel(nodeNo?: number): string {
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

onMounted(() => {
  loadTableData();
});
</script>
