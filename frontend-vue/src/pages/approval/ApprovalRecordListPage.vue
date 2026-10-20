<template>
  <div class="page-container">
    <PageHero
      eyebrow="Approval"
      title="审批记录列表"
      description="回看每一次审批动作、状态迁移和意见留痕，让审计路径更平滑可读。"
    >
      <div class="hero-inline">
        <span class="hero-pill">当前 {{ total }} 条记录</span>
        <span class="hero-pill hero-pill--muted">筛选项 {{ activeFilterCount }} 个</span>
      </div>
      <template #aside>
        <div class="hero-stat-panel">
          <p class="hero-stat-kicker">Audit Trail</p>
          <p class="hero-stat-value">{{ total }}</p>
          <p class="hero-stat-note">支持按实例、申请、动作和时间窗口回看审批链路。</p>
        </div>
      </template>
    </PageHero>

    <el-card class="panel-card filter-form page-section" shadow="never">
      <el-form :inline="true" :model="queryModel">
        <el-form-item label="实例ID">
          <el-input-number v-model="queryModel.instanceId" :min="1" controls-position="right" />
        </el-form-item>
        <el-form-item label="申请ID">
          <el-input-number v-model="queryModel.purchaseRequestId" :min="1" controls-position="right" />
        </el-form-item>
        <el-form-item label="动作">
          <el-select v-model="queryModel.action" clearable placeholder="全部">
            <el-option label="提交" value="SUBMIT" />
            <el-option label="通过" value="APPROVE" />
            <el-option label="驳回" value="REJECT" />
            <el-option label="退回" value="RETURN" />
          </el-select>
        </el-form-item>
        <el-form-item label="记录状态">
          <el-select v-model="queryModel.status" clearable placeholder="全部">
            <el-option label="有效" value="ACTIVE" />
            <el-option label="作废" value="INVALID" />
          </el-select>
        </el-form-item>
        <el-form-item label="操作时间">
          <el-date-picker
            v-model="dateRange"
            type="datetimerange"
            value-format="YYYY-MM-DD HH:mm:ss"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
          />
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
          <h3 class="card-title">审批记录列表</h3>
          <p class="sub-text">保留原始字段和时间线信息，强调动作与状态迁移的可读性。</p>
        </div>
      </div>

      <el-table v-loading="loading" :data="tableData" stripe>
        <el-table-column prop="instanceId" label="实例ID" width="100" />
        <el-table-column prop="purchaseRequestId" label="申请ID" width="100" />
        <el-table-column prop="nodeNo" label="节点" width="80" />
        <el-table-column prop="nodeName" label="节点名称" min-width="140" />
        <el-table-column prop="approverUserId" label="审批人ID" width="110" />
        <el-table-column prop="action" label="动作" width="90" />
        <el-table-column prop="fromStatus" label="原状态" width="110" />
        <el-table-column prop="toStatus" label="目标状态" width="110" />
        <el-table-column prop="approvalComment" label="审批意见" min-width="180" />
        <el-table-column prop="operatedAt" label="操作时间" min-width="170" />
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
import PageHero from "../../components/PageHero.vue";
import { getApprovalRecordPageApi } from "../../api/approval";
import type { ApprovalRecordVO } from "../../types/approval";

const loading = ref(false);
const tableData = ref<ApprovalRecordVO[]>([]);
const total = ref(0);
const dateRange = ref<[string, string] | null>(null);

const queryModel = reactive({
  pageNo: 1,
  pageSize: 10,
  instanceId: null as number | null,
  purchaseRequestId: null as number | null,
  action: "",
  status: ""
});

const activeFilterCount = computed(
  () =>
    [
      queryModel.instanceId,
      queryModel.purchaseRequestId,
      queryModel.action,
      queryModel.status,
      dateRange.value
    ].filter((value) => value !== "" && value !== null).length
);

async function loadTableData(): Promise<void> {
  loading.value = true;
  try {
    const data = await getApprovalRecordPageApi({
      pageNo: queryModel.pageNo,
      pageSize: queryModel.pageSize,
      instanceId: queryModel.instanceId,
      purchaseRequestId: queryModel.purchaseRequestId,
      action: queryModel.action || undefined,
      status: queryModel.status || undefined,
      startTime: dateRange.value?.[0],
      endTime: dateRange.value?.[1]
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

function handleResetQuery(): void {
  queryModel.instanceId = null;
  queryModel.purchaseRequestId = null;
  queryModel.action = "";
  queryModel.status = "";
  dateRange.value = null;
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

onMounted(() => {
  loadTableData();
});
</script>
