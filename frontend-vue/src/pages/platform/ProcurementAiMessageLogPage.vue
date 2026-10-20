<template>
  <VnetPage title="AI助手消息日志">
    <template #actions>
      <el-button :loading="loading" @click="loadLogs">刷新</el-button>
    </template>

    <VnetPanel title="消息记录">
      <div class="filter-bar">
        <el-select v-model="query.processStatus" clearable placeholder="处理状态" @change="handleFilterChange">
          <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-select v-model="query.intentCode" clearable placeholder="识别意图" @change="handleFilterChange">
          <el-option v-for="item in intentOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-select v-model="query.messageType" clearable placeholder="消息类型" @change="handleFilterChange">
          <el-option label="文本" value="text" />
          <el-option label="图片" value="image" />
          <el-option label="富文本" value="post" />
        </el-select>
        <el-input
          v-model="query.sessionNo"
          clearable
          placeholder="会话编号"
          @keyup.enter="handleFilterChange"
          @clear="handleFilterChange"
        />
        <el-input
          v-model="query.keyword"
          clearable
          placeholder="用户消息 / 回复 / 错误 / 账号"
          @keyup.enter="handleFilterChange"
          @clear="handleFilterChange"
        />
        <el-button type="primary" @click="handleFilterChange">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="logs" stripe>
        <el-table-column prop="occurredAt" label="接收时间" min-width="170" />
        <el-table-column label="发送人" min-width="150">
          <template #default="{ row }">
            <div class="main-cell">
              <strong>{{ accountText(row) }}</strong>
              <span>{{ row.sourceUserKey || "-" }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="用户消息" min-width="280">
          <template #default="{ row }">
            <span class="line-text">{{ row.rawText || row.rawContent || "-" }}</span>
          </template>
        </el-table-column>
        <el-table-column label="意图" width="150">
          <template #default="{ row }">
            {{ intentLabel(row.intentCode, row.intentName) }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusType(row.processStatus)" effect="light">{{ statusText(row.processStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="关联会话" min-width="160">
          <template #default="{ row }">
            <div class="main-cell">
              <strong>{{ row.sessionNo || "-" }}</strong>
              <span v-if="row.purchaseRequestNo">{{ row.purchaseRequestNo }}</span>
              <span v-else-if="row.monthlyDemandNo">{{ row.monthlyDemandNo }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="耗时" width="90">
          <template #default="{ row }">
            {{ row.durationMs == null ? "-" : `${row.durationMs}ms` }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="openDetail(row)">详情</el-button>
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

    <el-dialog v-model="detailVisible" title="AI助手消息详情" width="980px">
      <template v-if="currentLog">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="处理状态">
            <el-tag :type="statusType(currentLog.processStatus)" effect="light">
              {{ statusText(currentLog.processStatus) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="识别意图">{{ intentLabel(currentLog.intentCode, currentLog.intentName) }}</el-descriptions-item>
          <el-descriptions-item label="接收时间">{{ currentLog.occurredAt || "-" }}</el-descriptions-item>
          <el-descriptions-item label="耗时">{{ currentLog.durationMs == null ? "-" : `${currentLog.durationMs}ms` }}</el-descriptions-item>
          <el-descriptions-item label="发送人">{{ accountText(currentLog) }}</el-descriptions-item>
          <el-descriptions-item label="消息类型">{{ currentLog.messageType || "-" }}</el-descriptions-item>
          <el-descriptions-item label="飞书消息ID" :span="2">{{ currentLog.messageId || "-" }}</el-descriptions-item>
          <el-descriptions-item label="来源用户" :span="2">{{ currentLog.sourceUserKey || "-" }}</el-descriptions-item>
          <el-descriptions-item label="AI会话">{{ currentLog.sessionNo || "-" }}</el-descriptions-item>
          <el-descriptions-item label="关联单据">
            {{ currentLog.purchaseRequestNo || currentLog.purchaseRequestId || currentLog.monthlyDemandNo || currentLog.monthlyDemandId || "-" }}
          </el-descriptions-item>
        </el-descriptions>

        <section class="detail-block">
          <h3>用户消息</h3>
          <pre>{{ currentLog.rawText || "-" }}</pre>
        </section>
        <section class="detail-block">
          <h3>系统回复</h3>
          <pre>{{ currentLog.replyText || "-" }}</pre>
        </section>
        <section class="detail-block">
          <h3>处理结果</h3>
          <pre>{{ currentLog.resultSummary || "-" }}</pre>
        </section>
        <section class="detail-block">
          <h3>Dify 请求</h3>
          <pre>{{ formattedDifyRequest }}</pre>
        </section>
        <section class="detail-block">
          <h3>Dify 原始响应</h3>
          <pre>{{ formattedDifyResponse }}</pre>
        </section>
        <section class="detail-block">
          <h3>Dify 解析结果</h3>
          <pre>{{ formattedDifyParsed }}</pre>
        </section>
        <section v-if="currentLog.errorMessage || currentLog.errorCode" class="detail-block">
          <h3>错误信息</h3>
          <pre>{{ [currentLog.errorCode, currentLog.errorMessage].filter(Boolean).join("\n") }}</pre>
        </section>
        <section class="detail-block">
          <h3>飞书 content</h3>
          <pre>{{ currentLog.rawContent || "-" }}</pre>
        </section>
        <section class="detail-block">
          <h3>脱敏事件 payload</h3>
          <pre>{{ formattedPayload }}</pre>
        </section>
      </template>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </VnetPage>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import VnetPage from "../../components/vnet/VnetPage.vue";
import VnetPanel from "../../components/vnet/VnetPanel.vue";
import {
  getProcurementAiMessageLogDetailApi,
  getProcurementAiMessageLogPageApi
} from "../../api/platform";
import type { ProcurementAiMessageLogQuery, ProcurementAiMessageLogVO } from "../../types/platform";

const statusOptions = [
  { label: "已接收", value: "RECEIVED" },
  { label: "处理成功", value: "SUCCESS" },
  { label: "处理失败", value: "FAILED" },
  { label: "已忽略", value: "IGNORED" }
];

const intentOptions = [
  { label: "创建采购候选", value: "CREATE_CANDIDATE" },
  { label: "查看待提交候选", value: "PREVIEW_WAITING" },
  { label: "确认提交全部", value: "CONFIRM_ALL" },
  { label: "确认单条候选", value: "CONFIRM_ONE" },
  { label: "修改用途", value: "UPDATE_PURPOSE" },
  { label: "删除候选", value: "CANCEL_ITEM" },
  { label: "账号未绑定", value: "USER_UNBOUND" }
];

const query = reactive<ProcurementAiMessageLogQuery>({
  pageNo: 1,
  pageSize: 10,
  processStatus: "",
  intentCode: "",
  messageType: "",
  sessionNo: "",
  keyword: ""
});

const loading = ref(false);
const logs = ref<ProcurementAiMessageLogVO[]>([]);
const total = ref(0);
const detailVisible = ref(false);
const currentLog = ref<ProcurementAiMessageLogVO | null>(null);

const formattedPayload = computed(() => {
  return formatJsonBlock(currentLog.value?.rawPayload);
});

const formattedDifyRequest = computed(() => formatJsonBlock(currentLog.value?.difyRequestJson));
const formattedDifyResponse = computed(() => formatJsonBlock(currentLog.value?.difyResponseText));
const formattedDifyParsed = computed(() => formatJsonBlock(currentLog.value?.difyParsedJson));

onMounted(() => {
  void loadLogs();
});

async function loadLogs(): Promise<void> {
  loading.value = true;
  try {
    const page = await getProcurementAiMessageLogPageApi({
      pageNo: query.pageNo,
      pageSize: query.pageSize,
      processStatus: query.processStatus || undefined,
      intentCode: query.intentCode || undefined,
      messageType: query.messageType || undefined,
      sessionNo: query.sessionNo?.trim() || undefined,
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
  query.processStatus = "";
  query.intentCode = "";
  query.messageType = "";
  query.sessionNo = "";
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

async function openDetail(row: ProcurementAiMessageLogVO): Promise<void> {
  currentLog.value = await getProcurementAiMessageLogDetailApi(row.id);
  detailVisible.value = true;
}

function accountText(row: ProcurementAiMessageLogVO): string {
  return row.realName || row.username || row.applicantUserId?.toString() || "-";
}

function intentLabel(intentCode: string, intentName: string): string {
  return intentOptions.find((item) => item.value === intentCode)?.label || intentName || intentCode || "-";
}

function statusText(status: string): string {
  if (status === "SUCCESS") return "成功";
  if (status === "FAILED") return "失败";
  if (status === "IGNORED") return "忽略";
  return "已接收";
}

function statusType(status: string): "success" | "warning" | "danger" | "info" {
  if (status === "SUCCESS") return "success";
  if (status === "FAILED") return "danger";
  if (status === "IGNORED") return "info";
  return "warning";
}

function formatJsonBlock(value?: string | null): string {
  if (!value) {
    return "-";
  }
  try {
    return JSON.stringify(JSON.parse(value), null, 2);
  } catch {
    return value;
  }
}
</script>

<style scoped>
.filter-bar {
  display: grid;
  grid-template-columns: 140px 170px 130px 160px minmax(240px, 1fr) auto auto;
  gap: 10px;
  margin-bottom: 14px;
}

.main-cell {
  display: grid;
  gap: 4px;
}

.main-cell span {
  color: var(--app-text-soft);
  font-size: 12px;
}

.line-text {
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

@media (max-width: 1100px) {
  .filter-bar {
    grid-template-columns: 1fr 1fr;
  }
}

@media (max-width: 700px) {
  .filter-bar {
    grid-template-columns: 1fr;
  }
}
</style>
