<template>
  <div class="page-container">
    <PageHero
      eyebrow="Overview"
      :title="isSupplierUser ? '供应商报价总览' : '采购流程总览'"
      :description="heroDescription"
    >
      <div class="hero-inline">
        <span class="hero-pill">{{ focusLabel }} {{ focusValue }}</span>
        <span class="hero-pill hero-pill--muted">{{ secondaryLabel }} {{ secondaryValue }}</span>
      </div>
      <template #actions>
        <el-button v-if="showCreateButton" type="primary" @click="goCreate">新建采购申请</el-button>
      </template>
      <template #aside>
        <div class="hero-stat-panel">
          <p class="hero-stat-kicker">{{ heroStatKicker }}</p>
          <p class="hero-stat-value">{{ focusValue }}</p>
          <p class="hero-stat-note">{{ heroStatNote }}</p>
        </div>
      </template>
    </PageHero>

    <div class="metric-grid">
      <section class="metric-card metric-card--base">
        <div class="metric-label">{{ isSupplierUser ? "可报价需求" : "我的申请" }}</div>
        <div class="metric-value">{{ isSupplierUser ? dashboardMetrics.quotingRequestTotal : dashboardMetrics.myRequestTotal }}</div>
        <div class="metric-foot">{{ isSupplierUser ? "当前已进入供应商报价阶段的需求数" : "由我发起的采购申请总数" }}</div>
      </section>
      <section class="metric-card metric-card--warning">
        <div class="metric-label">{{ isSupplierUser ? "已参与报价" : "待我审批" }}</div>
        <div class="metric-value">{{ isSupplierUser ? dashboardMetrics.mySubmittedQuoteTotal : dashboardMetrics.pendingMyApprovalTotal }}</div>
        <div class="metric-foot">{{ isSupplierUser ? "我方已提交过报价的需求数" : "等待我处理的审批任务" }}</div>
      </section>
      <section class="metric-card metric-card--success">
        <div class="metric-label">{{ isSupplierUser ? "比价需求" : "审批中" }}</div>
        <div class="metric-value">{{ isSupplierUser ? dashboardMetrics.comparedRequestTotal : dashboardMetrics.pendingApprovalTotal }}</div>
        <div class="metric-foot">{{ isSupplierUser ? "已有报价可供甲方比价的需求数" : "仍处于审批链中的采购申请" }}</div>
      </section>
      <section class="metric-card metric-card--dark">
        <div class="metric-label">{{ isSupplierUser ? "最近清单" : "询价中" }}</div>
        <div class="metric-value">{{ isSupplierUser ? supplierRequests.length : dashboardMetrics.quotingRequestTotal }}</div>
        <div class="metric-foot">{{ isSupplierUser ? "当前列表中展示的报价清单数量" : "已流转到供应商报价阶段的需求" }}</div>
      </section>
    </div>

    <el-card class="panel-card table-panel page-section" shadow="never">
      <div class="table-toolbar">
        <div>
          <h3 class="card-title">{{ isSupplierUser ? "待报价清单" : "最新采购申请" }}</h3>
          <p class="sub-text">{{ isSupplierUser ? "查看可报价需求并快速进入报价动作。" : "实时展示最近提交或修改的采购申请。" }}</p>
        </div>
      </div>

      <el-table v-if="isSupplierUser" v-loading="loading" :data="supplierRequests" stripe>
        <el-table-column prop="reqNo" label="申请单号" min-width="180" />
        <el-table-column prop="reqTitle" label="申请标题" min-width="220" />
        <el-table-column prop="requestAmount" label="需求金额" width="140">
          <template #default="{ row }">{{ formatMoney(row.requestAmount) }}</template>
        </el-table-column>
        <el-table-column label="报价状态" width="120">
          <template #default="{ row }">
            <el-tag :type="quoteStatusTagType(row)">{{ quoteStatusLabel(row) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="我的报价" width="140">
          <template #default="{ row }">{{ row.myQuoteAmount ? formatMoney(row.myQuoteAmount) : "-" }}</template>
        </el-table-column>
        <el-table-column prop="needDate" label="需求日期" width="120" />
      </el-table>

      <el-table v-else v-loading="loading" :data="latestRequests" stripe>
        <el-table-column prop="reqNo" label="申请单号" min-width="180" />
        <el-table-column prop="reqTitle" label="申请标题" min-width="220" />
        <el-table-column prop="regionId" label="区域" width="90" />
        <el-table-column prop="totalAmount" label="金额" width="130">
          <template #default="{ row }">{{ formatMoney(row.totalAmount) }}</template>
        </el-table-column>
        <el-table-column prop="approvalStatus" label="审批状态" width="120">
          <template #default="{ row }">
            <el-tag :type="approvalTagType(row.approvalStatus)">{{ row.approvalStatus }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="业务状态" width="120" />
        <el-table-column prop="createdAt" label="创建时间" min-width="170" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import PageHero from "../../components/PageHero.vue";
import { getQuoteRequestsPageApi } from "../../api/supplier";
import { getWorkflowDashboardApi, getWorkflowPageApi } from "../../api/workflow";
import { useAuthStore } from "../../stores/auth";
import type { PurchaseRequestVO } from "../../types/purchase";
import type { SupplierQuoteRequestVO } from "../../types/supplier";

const router = useRouter();
const authStore = useAuthStore();

const loading = ref(false);
const latestRequests = ref<PurchaseRequestVO[]>([]);
const supplierRequests = ref<SupplierQuoteRequestVO[]>([]);

const dashboardMetrics = reactive({
  myRequestTotal: 0,
  pendingMyApprovalTotal: 0,
  pendingApprovalTotal: 0,
  quotingRequestTotal: 0,
  comparedRequestTotal: 0,
  mySubmittedQuoteTotal: 0
});

const isSupplierUser = computed(() => authStore.effectiveRoleCodes.includes("SUPPLIER"));
const SYSTEM_ADMIN_ROLES = ["SUPER_ADMIN", "SYS_ADMIN", "ADMIN"];
const showCreateButton = computed(() => !isSupplierUser.value && authStore.hasAnyRole([...SYSTEM_ADMIN_ROLES, "ENGINEER", "PURCHASER"]));

const heroDescription = computed(() =>
  isSupplierUser.value
    ? "围绕待报价清单、我的报价和比价结果，快速完成供应商侧协同。"
    : "围绕工程师提报、逐级审批、供应商报价和最低价比对展示关键状态。"
);

const focusLabel = computed(() => (isSupplierUser.value ? "待报价" : "待我审批"));
const focusValue = computed(() =>
  isSupplierUser.value ? dashboardMetrics.quotingRequestTotal : dashboardMetrics.pendingMyApprovalTotal
);
const secondaryLabel = computed(() => (isSupplierUser.value ? "已报价" : "询价中"));
const secondaryValue = computed(() =>
  isSupplierUser.value ? dashboardMetrics.mySubmittedQuoteTotal : dashboardMetrics.quotingRequestTotal
);
const heroStatKicker = computed(() => (isSupplierUser.value ? "Quote Queue" : "Pending Approval"));
const heroStatNote = computed(() =>
  isSupplierUser.value ? "优先处理未报价或需要更新报价的采购清单。" : "优先推动当前停留在您节点的采购申请。"
);

async function loadData(): Promise<void> {
  loading.value = true;
  try {
    const dashboard = await getWorkflowDashboardApi();
    Object.assign(dashboardMetrics, dashboard);

    if (isSupplierUser.value) {
      const quotePage = await getQuoteRequestsPageApi({ pageNo: 1, pageSize: 6 });
      supplierRequests.value = quotePage.records;
      latestRequests.value = [];
    } else {
      const requestPage = await getWorkflowPageApi({ pageNo: 1, pageSize: 6 });
      latestRequests.value = requestPage.records;
      supplierRequests.value = [];
    }
  } finally {
    loading.value = false;
  }
}

function goCreate(): void {
  router.push("/purchase-requests/create");
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

function quoteStatusLabel(row: SupplierQuoteRequestVO): string {
  return row.myQuoteStatus ? "已报价" : "待报价";
}

function quoteStatusTagType(row: SupplierQuoteRequestVO): "success" | "warning" {
  return row.myQuoteStatus ? "success" : "warning";
}

onMounted(() => {
  loadData();
});
</script>

<style scoped>
.metric-card--warning::before,
.metric-card--success::before,
.metric-card--dark::before,
.metric-card--base::before {
  content: "";
  position: absolute;
  inset: 0 auto 0 0;
  width: 4px;
  border-radius: 999px;
}

.metric-card--base::before {
  background: #0071e3;
}

.metric-card--warning::before {
  background: #c46b00;
}

.metric-card--success::before {
  background: #0b8a46;
}

.metric-card--dark {
  background: linear-gradient(160deg, rgba(29, 29, 31, 0.95), rgba(29, 29, 31, 0.86));
}

.metric-card--dark::before {
  background: rgba(255, 255, 255, 0.72);
}

.metric-card--dark .metric-label,
.metric-card--dark .metric-value,
.metric-card--dark .metric-foot {
  color: #fff;
}

.metric-card--dark .metric-foot {
  color: rgba(255, 255, 255, 0.68);
}
</style>
