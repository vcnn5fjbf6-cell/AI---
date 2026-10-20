<template>
  <div class="page-container">
    <PageHero
      eyebrow="Procurement"
      title="采购申请详情"
      :description="`申请单号：${detail.reqNo || '-'}。在同一视图中查看需求、审批轨迹和供应商报价。`"
    >
      <div class="hero-inline">
        <span class="hero-pill">{{ detail.approvalStatus || "UNKNOWN" }}</span>
        <span class="hero-pill hero-pill--muted">{{ detail.status || "未同步状态" }}</span>
      </div>
      <template #actions>
        <div class="action-cluster">
          <el-button @click="goBack">返回列表</el-button>
          <el-button v-if="canManageRequest" type="primary" @click="goEdit">编辑</el-button>
          <el-button
            v-if="canManageRequest && (detail.status === 'DRAFT' || detail.status === 'RETURNED')"
            type="warning"
            @click="handleSubmit"
          >
            提交审批
          </el-button>
        </div>
      </template>
      <template #aside>
        <div class="hero-stat-panel">
          <p class="hero-stat-kicker">Request Value</p>
          <p class="hero-stat-value">{{ formatMoney(detail.totalAmount) }}</p>
          <p class="hero-stat-note">已提交 {{ detail.submitCount ?? 0 }} 次，最近更新时间 {{ detail.updatedAt || "-" }}。</p>
        </div>
      </template>
    </PageHero>

    <el-card class="panel-card table-panel page-section" shadow="never" v-loading="loading">
      <div class="table-toolbar">
        <div>
          <h3 class="card-title">申请信息</h3>
          <p class="sub-text">从金额、部门到审批状态，完整展示当前采购申请。</p>
        </div>
      </div>

      <el-descriptions :column="4" border>
        <el-descriptions-item label="申请标题">{{ detail.reqTitle || "-" }}</el-descriptions-item>
        <el-descriptions-item label="申请类型">{{ detail.reqType || "-" }}</el-descriptions-item>
        <el-descriptions-item label="申请人">{{ applicantLabel(detail) }}</el-descriptions-item>
        <el-descriptions-item label="部门">{{ detail.deptName || "-" }}</el-descriptions-item>
        <el-descriptions-item label="收货城市">{{ detail.receivingCity || "-" }}</el-descriptions-item>
        <el-descriptions-item label="预算编码">{{ detail.budgetCode || "-" }}</el-descriptions-item>
        <el-descriptions-item label="币种">{{ detail.currency || "-" }}</el-descriptions-item>
        <el-descriptions-item label="申请金额">{{ formatMoney(detail.totalAmount) }}</el-descriptions-item>
        <el-descriptions-item label="审批状态">
          <el-tag :type="approvalTagType(detail.approvalStatus)">{{ detail.approvalStatus || "-" }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="业务状态">{{ detail.status || "-" }}</el-descriptions-item>
        <el-descriptions-item label="紧急程度">{{ urgencyLabel(detail.urgencyLevel) }}</el-descriptions-item>
        <el-descriptions-item label="当前节点">{{ requestCurrentNodeText(detail) }}</el-descriptions-item>
        <el-descriptions-item label="需求日期">{{ detail.needDate || "-" }}</el-descriptions-item>
        <el-descriptions-item label="提交次数">{{ detail.submitCount ?? 0 }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ detail.createdAt || "-" }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ detail.updatedAt || "-" }}</el-descriptions-item>
      </el-descriptions>

      <el-divider />

      <h4 class="section-title">采购明细</h4>
      <el-table :data="detail.items || []" stripe>
        <el-table-column prop="lineNo" label="行号" width="80" />
        <el-table-column prop="itemCode" label="物料编码" min-width="130" />
        <el-table-column prop="itemName" label="商品名称" min-width="180" />
        <el-table-column prop="specModel" label="规格型号" min-width="150" />
        <el-table-column prop="unit" label="单位" width="90" />
        <el-table-column prop="qty" label="数量" width="100" />
        <el-table-column prop="unitPrice" label="单价" width="120">
          <template #default="{ row }">{{ formatMoney(row.unitPrice) }}</template>
        </el-table-column>
        <el-table-column prop="lineAmount" label="行金额" width="120">
          <template #default="{ row }">{{ formatMoney(row.lineAmount) }}</template>
        </el-table-column>
        <el-table-column label="最终状态" width="110">
          <template #default="{ row }">
            <el-tag :type="itemStatusTagType(row.status)" effect="plain">
              {{ itemStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="purpose" label="采购原因" min-width="200" />
        <el-table-column label="电商链接" min-width="130">
          <template #default="{ row }">
            <el-link v-if="row.ecommerceUrl" :href="normalizeUrl(row.ecommerceUrl)" target="_blank" type="primary">打开链接</el-link>
            <span v-else>-</span>
          </template>
        </el-table-column>
      </el-table>

      <el-divider />

      <h4 class="section-title">审批轨迹</h4>
      <el-timeline>
        <el-timeline-item
          v-for="record in approvalRecords"
          :key="record.id"
          :timestamp="record.operatedAt"
          :type="timelineType(record.action)"
        >
          <div class="timeline-title">{{ record.nodeName }} - {{ record.action }}</div>
          <div class="timeline-desc">审批人ID：{{ record.approverUserId }}，状态：{{ record.fromStatus }} -> {{ record.toStatus }}</div>
          <div class="timeline-desc">{{ record.approvalComment || "无审批意见" }}</div>
        </el-timeline-item>
      </el-timeline>
      <el-empty v-if="approvalRecords.length === 0" description="暂无审批记录" :image-size="80" />

      <el-divider />

      <h4 class="section-title">供应商报价与比价</h4>
      <div class="metric-grid quote-summary-grid">
        <section class="metric-card metric-card--base">
          <div class="metric-label">已报价供应商</div>
          <div class="metric-value">{{ quoteSummary?.supplierQuoteCount ?? 0 }}</div>
          <div class="metric-foot">当前已提交报价的供应商数量。</div>
        </section>
        <section class="metric-card metric-card--success">
          <div class="metric-label">最低报价</div>
          <div class="metric-value">{{ quoteDetailsVisible && quoteSummary?.lowestAmount ? formatMoney(quoteSummary.lowestAmount) : "-" }}</div>
          <div class="metric-foot">{{ quoteDetailsVisible ? "自动按金额升序排序后识别出的最低价。" : "中标结果产生后显示。" }}</div>
        </section>
        <section class="metric-card metric-card--dark">
          <div class="metric-label">最低价供应商</div>
          <div class="metric-value quote-supplier-name">{{ quoteDetailsVisible ? quoteSummary?.lowestSupplierName || "-" : "-" }}</div>
          <div class="metric-foot">{{ quoteDetailsVisible ? "如存在多家报价，请结合交期和备注综合判断。" : "非管理员未中标前不可见。" }}</div>
        </section>
      </div>

      <el-alert
        v-if="quoteSummary && !quoteDetailsVisible"
        class="quote-hidden-alert"
        type="warning"
        :closable="false"
        show-icon
        title="中标结果产生后可查看供应商报价明细"
        description="非管理员在未中标前只能查看报价供应商数量，不能查看供应商名称、报价金额、交期和报价明细。"
      />

      <el-table v-if="quoteDetailsVisible" :data="quoteSummary?.quotes || []" stripe style="margin-top: 18px">
        <el-table-column prop="supplierName" label="供应商" min-width="180" />
        <el-table-column prop="quoteNo" label="报价单号" min-width="180" />
        <el-table-column prop="totalAmount" label="报价金额" width="140">
          <template #default="{ row }">{{ formatMoney(row.totalAmount) }}</template>
        </el-table-column>
        <el-table-column prop="leadTimeDays" label="交期(天)" width="110" />
        <el-table-column prop="status" label="状态" width="110" />
        <el-table-column prop="submittedAt" label="提交时间" min-width="170" />
        <el-table-column prop="quoteRemark" label="备注" min-width="220" />
      </el-table>
      <el-empty v-if="quoteDetailsVisible && !quoteSummary?.quotes?.length" description="暂无供应商报价" :image-size="80" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import PageHero from "../../components/PageHero.vue";
import {
  getApprovalRecordsApi,
  getQuoteSummaryApi,
  getWorkflowDetailApi,
  previewSubmitWorkflowRequestApi,
  submitWorkflowRequestApi
} from "../../api/workflow";
import { useAuthStore } from "../../stores/auth";
import type { ApprovalRecordVO } from "../../types/approval";
import type { PurchaseRequestVO } from "../../types/purchase";
import type { SupplierQuoteSummaryVO } from "../../types/supplier";
import { confirmSubmitNextApprovalDialog } from "../../utils/workflow-submit";

const route = useRoute();
const router = useRouter();
const authStore = useAuthStore();

const loading = ref(false);
const approvalRecords = ref<ApprovalRecordVO[]>([]);
const quoteSummary = ref<SupplierQuoteSummaryVO | null>(null);
const detail = reactive<Partial<PurchaseRequestVO>>({
  items: []
});

const SYSTEM_ADMIN_ROLES = ["SUPER_ADMIN", "SYS_ADMIN", "ADMIN"];
const canManageRequest = computed(
  () => authStore.hasAnyRole(SYSTEM_ADMIN_ROLES) || (detail.applicantUserId != null && detail.applicantUserId === authStore.user?.userId)
);
const quoteDetailsVisible = computed(() => quoteSummary.value?.quoteDetailsVisible !== false);

async function loadData(): Promise<void> {
  const id = Number(route.params.id);
  if (!id) {
    return;
  }
  loading.value = true;
  try {
    const [detailData, recordsData, quotesData] = await Promise.all([
      getWorkflowDetailApi(id),
      getApprovalRecordsApi(id),
      getQuoteSummaryApi(id)
    ]);
    Object.assign(detail, detailData);
    approvalRecords.value = recordsData;
    quoteSummary.value = quotesData;
  } finally {
    loading.value = false;
  }
}

function goBack(): void {
  router.push("/purchase-requests");
}

function goEdit(): void {
  if (!detail.id) {
    return;
  }
  router.push(`/purchase-requests/${detail.id}/edit`);
}

async function handleSubmit(): Promise<void> {
  if (!detail.id) {
    return;
  }
  const previewResult = await previewSubmitWorkflowRequestApi(detail.id);
  const confirmed = await confirmSubmitNextApprovalDialog(previewResult);
  if (!confirmed) {
    return;
  }
  await submitWorkflowRequestApi(detail.id);
  ElMessage.success("提交成功");
  await loadData();
}

function formatMoney(value?: number): string {
  return Number(value ?? 0).toLocaleString("zh-CN", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  });
}

function applicantLabel(row: Partial<PurchaseRequestVO>): string {
  if (!row.applicantName && !row.applicantEmployeeNo) {
    return "-";
  }
  return row.applicantName || row.applicantEmployeeNo || "-";
}

function normalizeUrl(url: string): string {
  if (/^https?:\/\//i.test(url)) {
    return url;
  }
  return `https://${url}`;
}

function approvalTagType(status?: string): "warning" | "success" | "danger" | "info" {
  if (status === "PENDING") return "warning";
  if (status === "APPROVED") return "success";
  if (status === "REJECTED") return "danger";
  return "info";
}

function timelineType(action: string): "primary" | "success" | "warning" | "danger" | "info" {
  if (action === "APPROVE") return "success";
  if (action === "REJECT") return "danger";
  if (action === "RETURN") return "warning";
  return "primary";
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

function urgencyLabel(level?: number): string {
  if (level === 3) return "特急";
  if (level === 2) return "紧急";
  if (level === 1) return "一般";
  return "-";
}

onMounted(() => {
  loadData();
});
</script>

<style scoped>
.timeline-title {
  font-weight: 600;
  margin-bottom: 4px;
  color: #1d1d1f;
}

.timeline-desc {
  font-size: 13px;
  color: rgba(29, 29, 31, 0.6);
  margin-bottom: 2px;
}

.quote-summary-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.quote-supplier-name {
  font-size: 24px;
  line-height: 1.2;
}

.quote-hidden-alert {
  margin-top: 18px;
}

@media (max-width: 1200px) {
  .quote-summary-grid {
    grid-template-columns: 1fr;
  }
}
</style>
