<template>
  <div class="page-container">
    <PageHero
      eyebrow="Approval"
      title="待办审批任务"
      description="把审批动作保持在一条简洁的任务流里，减少在列表和弹窗之间切换时的认知负担。"
    >
      <div class="hero-inline">
        <span class="hero-pill">待处理 {{ total }} 条</span>
        <span class="hero-pill hero-pill--muted">筛选项 {{ activeFilterCount }} 个</span>
      </div>
      <template #aside>
        <div class="hero-stat-panel">
          <p class="hero-stat-kicker">Pending</p>
          <p class="hero-stat-value">{{ total }}</p>
          <p class="hero-stat-note">建议优先处理金额高、轮次多或停留时间长的申请。</p>
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
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="panel-card table-panel page-section" shadow="never">
      <div class="table-toolbar">
        <div>
          <h3 class="card-title">待办审批任务</h3>
          <p class="sub-text">查询审批状态为 PENDING 的采购申请并处理审批动作</p>
        </div>
      </div>

      <el-table v-loading="loading" :data="tableData" stripe>
        <el-table-column prop="reqNo" label="申请单号" min-width="180" />
        <el-table-column prop="reqTitle" label="申请标题" min-width="200" />
        <el-table-column label="申请人" min-width="130">
          <template #default="{ row }">{{ applicantLabel(row) }}</template>
        </el-table-column>
        <el-table-column prop="totalAmount" label="金额" width="130">
          <template #default="{ row }">{{ formatMoney(row.totalAmount) }}</template>
        </el-table-column>
        <el-table-column prop="submitCount" label="提交轮次" width="100" />
        <el-table-column prop="currentNodeNo" label="当前节点" width="100" />
        <el-table-column prop="approvalStatus" label="审批状态" width="120">
          <template #default="{ row }">
            <el-tag :type="row.approvalStatus === 'PENDING' ? 'warning' : 'info'">{{ row.approvalStatus }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button link type="primary" @click="goDetail(row.id)">详情</el-button>
              <el-button v-perm="'approval:action'" link type="warning" @click="openActionDialog(row)">审批处理</el-button>
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

    <el-dialog v-model="dialogVisible" title="审批操作" width="560px">
      <el-alert type="info" :closable="false" show-icon style="margin-bottom: 16px">
        当前申请：{{ actionTarget.reqNo || "-" }} / {{ actionTarget.reqTitle || "-" }}，金额 ¥{{ formatMoney(actionTarget.totalAmount) }}
      </el-alert>
      <el-form ref="formRef" :model="actionForm" :rules="formRules" label-width="100px" class="dialog-form">
        <el-form-item label="审批动作" prop="action">
          <el-radio-group v-model="actionForm.action">
            <el-radio-button value="APPROVE">通过</el-radio-button>
            <el-radio-button value="RETURN">退回</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="审批意见">
          <el-input v-model="actionForm.approvalComment" type="textarea" :rows="3" placeholder="请输入审批意见（选填）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmitAction">提交审批</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import type { FormInstance, FormRules } from "element-plus";
import { ElMessage } from "element-plus";
import { useRouter } from "vue-router";
import PageHero from "../../components/PageHero.vue";
import { getApprovalTasksPageApi, approveRequestApi } from "../../api/workflow";
import type { PurchaseRequestVO } from "../../types/purchase";

const router = useRouter();

const loading = ref(false);
const submitLoading = ref(false);
const total = ref(0);
const tableData = ref<PurchaseRequestVO[]>([]);
const dialogVisible = ref(false);
const formRef = ref<FormInstance>();

const queryModel = reactive({
  pageNo: 1,
  pageSize: 10,
  reqNo: "",
  reqTitle: ""
});

const activeFilterCount = computed(
  () => [queryModel.reqNo, queryModel.reqTitle].filter((value) => value !== "").length
);

const actionTarget = reactive<Partial<PurchaseRequestVO>>({});

const actionForm = reactive({
  action: "APPROVE" as "APPROVE" | "RETURN",
  approvalComment: ""
});

const formRules: FormRules<typeof actionForm> = {
  action: [{ required: true, message: "请选择审批动作", trigger: "change" }]
};

async function loadTableData(): Promise<void> {
  loading.value = true;
  try {
    const data = await getApprovalTasksPageApi({
      pageNo: queryModel.pageNo,
      pageSize: queryModel.pageSize,
      reqNo: queryModel.reqNo || undefined,
      reqTitle: queryModel.reqTitle || undefined
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

function handleReset(): void {
  queryModel.reqNo = "";
  queryModel.reqTitle = "";
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

function goDetail(id: number): void {
  router.push(`/purchase-requests/${id}`);
}

function openActionDialog(row: PurchaseRequestVO): void {
  Object.assign(actionTarget, row);
  actionForm.action = "APPROVE";
  actionForm.approvalComment = "";
  dialogVisible.value = true;
}

async function handleSubmitAction(): Promise<void> {
  if (!formRef.value) return;
  const valid = await formRef.value.validate().catch(() => false);
  if (!valid) return;
  submitLoading.value = true;
  try {
    await approveRequestApi(actionTarget.id!, {
      action: actionForm.action,
      approvalComment: actionForm.approvalComment || undefined
    });
    ElMessage.success("审批操作成功");
    dialogVisible.value = false;
    await loadTableData();
  } finally {
    submitLoading.value = false;
  }
}

function formatMoney(value: number | null | undefined): string {
  return Number(value ?? 0).toLocaleString("zh-CN", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  });
}

function applicantLabel(row: PurchaseRequestVO): string {
  if (!row.applicantName && !row.applicantEmployeeNo) {
    return "-";
  }
  return row.applicantName || row.applicantEmployeeNo || "-";
}

onMounted(() => {
  loadTableData();
});
</script>
