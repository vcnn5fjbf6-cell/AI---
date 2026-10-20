<template>
  <VnetPage title="错误台账">
    <template #actions>
      <el-button :loading="loading" @click="loadLogs">刷新</el-button>
    </template>

    <VnetPanel title="错误记录">
      <div class="filter-bar">
        <el-select v-model="query.moduleCode" clearable placeholder="模块" @change="handleFilterChange">
          <el-option v-for="item in moduleOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-select v-model="query.status" clearable placeholder="状态" @change="handleFilterChange">
          <el-option label="待处理" value="OPEN" />
          <el-option label="已修复" value="FIXED" />
          <el-option label="已忽略" value="IGNORED" />
        </el-select>
        <el-input
          v-model="query.keyword"
          clearable
          placeholder="错误内容 / 接口 / 用户"
          @keyup.enter="handleFilterChange"
          @clear="handleFilterChange"
        />
        <el-button type="primary" @click="handleFilterChange">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="logs" stripe>
        <el-table-column prop="occurredAt" label="发生时间" min-width="170" />
        <el-table-column label="模块" width="120">
          <template #default="{ row }">
            <el-tag effect="light">{{ moduleLabel(row.moduleCode) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="功能/接口" min-width="260">
          <template #default="{ row }">
            <div class="request-cell">
              <strong>{{ row.functionName || "-" }}</strong>
              <span>{{ row.requestMethod || "-" }} {{ row.requestUri || "-" }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="errorCode" label="错误码" min-width="150" />
        <el-table-column label="报错内容" min-width="320">
          <template #default="{ row }">
            <span class="message-text">{{ row.errorMessage || "-" }}</span>
          </template>
        </el-table-column>
        <el-table-column label="触发账号" min-width="140">
          <template #default="{ row }">
            {{ row.username || row.userId || "-" }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" effect="light">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="210" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="openDetail(row)">详情</el-button>
            <template v-if="row.status === 'OPEN'">
              <el-button type="success" link @click="changeStatus(row, 'FIXED')">标记修复</el-button>
              <el-button type="warning" link @click="changeStatus(row, 'IGNORED')">忽略</el-button>
            </template>
            <el-button v-else type="primary" link @click="changeStatus(row, 'OPEN')">重开</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager-box">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next"
          :total="total"
          :current-page="query.pageNo"
          :page-size="query.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </VnetPanel>

    <el-dialog v-model="detailVisible" title="错误详情" width="860px">
      <template v-if="currentLog">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="模块">{{ moduleLabel(currentLog.moduleCode) }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ statusText(currentLog.status) }}</el-descriptions-item>
          <el-descriptions-item label="发生时间">{{ currentLog.occurredAt || "-" }}</el-descriptions-item>
          <el-descriptions-item label="触发账号">{{ currentLog.username || currentLog.userId || "-" }}</el-descriptions-item>
          <el-descriptions-item label="请求方式">{{ currentLog.requestMethod || "-" }}</el-descriptions-item>
          <el-descriptions-item label="客户端 IP">{{ currentLog.clientIp || "-" }}</el-descriptions-item>
          <el-descriptions-item label="请求路径" :span="2">{{ currentLog.requestUri || "-" }}</el-descriptions-item>
          <el-descriptions-item label="异常类" :span="2">{{ currentLog.exceptionClass || "-" }}</el-descriptions-item>
          <el-descriptions-item label="修复说明" :span="2">{{ currentLog.fixRemark || "-" }}</el-descriptions-item>
        </el-descriptions>

        <section class="detail-block">
          <h3>报错内容</h3>
          <pre>{{ currentLog.errorMessage || "-" }}</pre>
        </section>

        <section class="detail-block">
          <h3>异常堆栈</h3>
          <pre>{{ currentLog.stackTrace || "-" }}</pre>
        </section>
      </template>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <template v-if="currentLog?.status === 'OPEN'">
          <el-button type="success" @click="changeStatus(currentLog, 'FIXED')">标记修复</el-button>
          <el-button type="warning" @click="changeStatus(currentLog, 'IGNORED')">忽略</el-button>
        </template>
        <el-button v-else-if="currentLog" type="primary" @click="changeStatus(currentLog, 'OPEN')">重开</el-button>
      </template>
    </el-dialog>
  </VnetPage>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import VnetPage from "../../components/vnet/VnetPage.vue";
import VnetPanel from "../../components/vnet/VnetPanel.vue";
import {
  getSystemErrorLogDetailApi,
  getSystemErrorLogPageApi,
  updateSystemErrorLogStatusApi
} from "../../api/platform";
import type { SystemErrorLogQuery, SystemErrorLogVO } from "../../types/platform";

type ErrorLogStatus = "OPEN" | "FIXED" | "IGNORED";

const moduleOptions = [
  { label: "平台", value: "PLATFORM" },
  { label: "认证", value: "AUTH" },
  { label: "采购", value: "PROCUREMENT" },
  { label: "供应商", value: "SUPPLIER" },
  { label: "集成", value: "INTEGRATION" },
  { label: "未知", value: "UNKNOWN" }
];

const query = reactive<SystemErrorLogQuery>({
  pageNo: 1,
  pageSize: 10,
  moduleCode: "",
  status: "OPEN",
  keyword: ""
});

const loading = ref(false);
const logs = ref<SystemErrorLogVO[]>([]);
const total = ref(0);
const detailVisible = ref(false);
const currentLog = ref<SystemErrorLogVO | null>(null);

onMounted(() => {
  void loadLogs();
});

async function loadLogs(): Promise<void> {
  loading.value = true;
  try {
    const page = await getSystemErrorLogPageApi({
      pageNo: query.pageNo,
      pageSize: query.pageSize,
      moduleCode: query.moduleCode || undefined,
      status: query.status || undefined,
      keyword: query.keyword?.trim() || undefined
    });
    logs.value = page.records;
    total.value = page.total;
  } finally {
    loading.value = false;
  }
}

function handleFilterChange(): void {
  query.pageNo = 1;
  void loadLogs();
}

function resetFilters(): void {
  query.moduleCode = "";
  query.status = "OPEN";
  query.keyword = "";
  handleFilterChange();
}

function handlePageChange(pageNo: number): void {
  query.pageNo = pageNo;
  void loadLogs();
}

function handleSizeChange(pageSize: number): void {
  query.pageSize = pageSize;
  query.pageNo = 1;
  void loadLogs();
}

async function openDetail(row: SystemErrorLogVO): Promise<void> {
  currentLog.value = await getSystemErrorLogDetailApi(row.id);
  detailVisible.value = true;
}

async function changeStatus(row: SystemErrorLogVO, status: ErrorLogStatus): Promise<void> {
  const result = await ElMessageBox.prompt(promptMessage(status), promptTitle(status), {
    confirmButtonText: "确认",
    cancelButtonText: "取消",
    inputType: "textarea",
    inputValue: status === "OPEN" ? "" : row.fixRemark || "",
    inputPlaceholder: "处理说明"
  }).catch(() => null);
  if (!result) {
    return;
  }

  await updateSystemErrorLogStatusApi(row.id, {
    status,
    fixRemark: String(result.value || "").trim()
  });
  ElMessage.success(statusSuccessText(status));
  await loadLogs();
  if (detailVisible.value) {
    currentLog.value = await getSystemErrorLogDetailApi(row.id);
  }
}

function moduleLabel(moduleCode: string): string {
  return moduleOptions.find((item) => item.value === moduleCode)?.label || moduleCode || "未知";
}

function statusText(status: string): string {
  if (status === "FIXED") return "已修复";
  if (status === "IGNORED") return "已忽略";
  return "待处理";
}

function statusType(status: string): "success" | "warning" | "info" {
  if (status === "FIXED") return "success";
  if (status === "IGNORED") return "info";
  return "warning";
}

function promptTitle(status: ErrorLogStatus): string {
  if (status === "FIXED") return "标记修复";
  if (status === "IGNORED") return "忽略错误";
  return "重开错误";
}

function promptMessage(status: ErrorLogStatus): string {
  if (status === "FIXED") return "填写本次修复说明";
  if (status === "IGNORED") return "填写忽略原因";
  return "填写重开原因";
}

function statusSuccessText(status: ErrorLogStatus): string {
  if (status === "FIXED") return "已标记修复";
  if (status === "IGNORED") return "已忽略";
  return "已重开";
}
</script>

<style scoped>
.filter-bar {
  display: grid;
  grid-template-columns: 150px 150px minmax(240px, 1fr) auto auto;
  gap: 10px;
  margin-bottom: 14px;
}

.request-cell {
  display: grid;
  gap: 4px;
}

.request-cell span {
  color: var(--app-text-soft);
  font-size: 13px;
}

.message-text {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pager-box {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.detail-block {
  margin-top: 16px;
}

.detail-block h3 {
  margin: 0 0 8px;
  color: var(--app-text);
  font-size: 15px;
}

.detail-block pre {
  max-height: 260px;
  margin: 0;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
  background: #f8fafc;
  color: var(--app-text);
  font-size: 12px;
  line-height: 1.6;
  padding: 12px;
}

@media (max-width: 900px) {
  .filter-bar {
    grid-template-columns: 1fr;
  }
}
</style>
