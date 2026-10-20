<template>
  <VnetPage title="导入文件记录">
    <template #actions>
      <el-button :loading="loading" @click="loadLogs">刷新</el-button>
    </template>

    <VnetPanel title="导入记录">
      <div class="retention-note">
        原文件当前不落库、不留存，只记录文件名、大小、SHA-256、来源和处理结果；保留天数在“平台集成设置”的“导入文件记录保留天数”中维护，默认 90 天。
      </div>

      <div class="filter-bar">
        <el-select v-model="query.sourceChannel" clearable placeholder="来源" @change="handleFilterChange">
          <el-option v-for="item in sourceOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-select v-model="query.importType" clearable placeholder="导入类型" @change="handleFilterChange">
          <el-option v-for="item in importTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-select v-model="query.processStatus" clearable placeholder="状态" @change="handleFilterChange">
          <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-input
          v-model="query.keyword"
          clearable
          placeholder="文件名 / 上传人 / 飞书消息 / 结果"
          @keyup.enter="handleFilterChange"
          @clear="handleFilterChange"
        />
        <el-button type="primary" @click="handleFilterChange">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="logs" stripe>
        <el-table-column prop="occurredAt" label="导入时间" min-width="170" />
        <el-table-column label="导入类型" min-width="170">
          <template #default="{ row }">
            <div class="main-cell">
              <strong>{{ row.importTypeName || importTypeLabel(row.importType) }}</strong>
              <span>{{ row.importType || "-" }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="文件" min-width="260">
          <template #default="{ row }">
            <div class="main-cell">
              <strong>{{ row.fileName || "-" }}</strong>
              <span>{{ fileMeta(row) }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="来源" width="120">
          <template #default="{ row }">
            <el-tag effect="light">{{ sourceLabel(row.sourceChannel) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="上传人" min-width="150">
          <template #default="{ row }">
            <div class="main-cell">
              <strong>{{ uploaderText(row) }}</strong>
              <span>{{ row.sourceUserKey || row.uploaderUsername || "-" }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="行数" width="150">
          <template #default="{ row }">
            {{ countText(row) }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.processStatus)" effect="light">{{ statusText(row.processStatus) }}</el-tag>
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

    <el-dialog v-model="detailVisible" title="导入文件详情" width="920px">
      <template v-if="currentLog">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="导入类型">
            {{ currentLog.importTypeName || importTypeLabel(currentLog.importType) }}
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusType(currentLog.processStatus)" effect="light">
              {{ statusText(currentLog.processStatus) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="来源">{{ sourceLabel(currentLog.sourceChannel) }}</el-descriptions-item>
          <el-descriptions-item label="导入时间">{{ currentLog.occurredAt || "-" }}</el-descriptions-item>
          <el-descriptions-item label="文件名" :span="2">{{ currentLog.fileName || "-" }}</el-descriptions-item>
          <el-descriptions-item label="文件大小">{{ formatFileSize(currentLog.fileSize) }}</el-descriptions-item>
          <el-descriptions-item label="扩展名">{{ currentLog.fileExt || "-" }}</el-descriptions-item>
          <el-descriptions-item label="留存方式">
            {{ currentLog.storageMode === "NOT_STORED" ? "仅记录元数据，不保存原文件" : currentLog.storageMode || "-" }}
          </el-descriptions-item>
          <el-descriptions-item label="上传人">{{ uploaderText(currentLog) }}</el-descriptions-item>
          <el-descriptions-item label="有效行数">{{ currentLog.rowCount ?? "-" }}</el-descriptions-item>
          <el-descriptions-item label="成功/失败">{{ countText(currentLog) }}</el-descriptions-item>
          <el-descriptions-item label="飞书消息ID" :span="2">{{ currentLog.messageId || "-" }}</el-descriptions-item>
          <el-descriptions-item label="AI消息日志ID">{{ currentLog.messageLogId || "-" }}</el-descriptions-item>
          <el-descriptions-item label="来源用户">{{ currentLog.sourceUserKey || "-" }}</el-descriptions-item>
          <el-descriptions-item label="关联业务" :span="2">
            {{ [currentLog.relatedBizType, currentLog.relatedBizNo || currentLog.relatedBizId].filter(Boolean).join(" / ") || "-" }}
          </el-descriptions-item>
          <el-descriptions-item label="SHA-256" :span="2">{{ currentLog.fileSha256 || "-" }}</el-descriptions-item>
        </el-descriptions>

        <section class="detail-block">
          <h3>处理结果</h3>
          <pre>{{ currentLog.resultSummary || "-" }}</pre>
        </section>
        <section v-if="currentLog.errorMessage" class="detail-block">
          <h3>错误信息</h3>
          <pre>{{ currentLog.errorMessage }}</pre>
        </section>
      </template>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </VnetPage>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import VnetPage from "../../components/vnet/VnetPage.vue";
import VnetPanel from "../../components/vnet/VnetPanel.vue";
import { getImportFileLogDetailApi, getImportFileLogPageApi } from "../../api/platform";
import type { ImportFileLogQuery, ImportFileLogVO } from "../../types/platform";

const sourceOptions = [
  { label: "网页上传", value: "WEB" },
  { label: "飞书机器人", value: "FEISHU_BOT" }
];

const importTypeOptions = [
  { label: "用户批量导入", value: "USER_IMPORT" },
  { label: "部门审批配置导入", value: "APPROVAL_ROUTE_IMPORT" },
  { label: "采购明细导入预览", value: "PURCHASE_ITEM_PREVIEW" },
  { label: "供应商报价导入预览", value: "SUPPLIER_QUOTE_PREVIEW" },
  { label: "飞书采购助手 Excel 附件", value: "FEISHU_EXCEL_ATTACHMENT" }
];

const statusOptions = [
  { label: "已接收", value: "RECEIVED" },
  { label: "成功", value: "SUCCESS" },
  { label: "失败", value: "FAILED" }
];

const query = reactive<ImportFileLogQuery>({
  pageNo: 1,
  pageSize: 10,
  sourceChannel: "",
  importType: "",
  processStatus: "",
  keyword: ""
});

const loading = ref(false);
const logs = ref<ImportFileLogVO[]>([]);
const total = ref(0);
const detailVisible = ref(false);
const currentLog = ref<ImportFileLogVO | null>(null);

onMounted(() => {
  void loadLogs();
});

async function loadLogs(): Promise<void> {
  loading.value = true;
  try {
    const page = await getImportFileLogPageApi({
      pageNo: query.pageNo,
      pageSize: query.pageSize,
      sourceChannel: query.sourceChannel || undefined,
      importType: query.importType || undefined,
      processStatus: query.processStatus || undefined,
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
  query.sourceChannel = "";
  query.importType = "";
  query.processStatus = "";
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

async function openDetail(row: ImportFileLogVO): Promise<void> {
  currentLog.value = await getImportFileLogDetailApi(row.id);
  detailVisible.value = true;
}

function sourceLabel(value: string): string {
  return sourceOptions.find((item) => item.value === value)?.label || value || "-";
}

function importTypeLabel(value: string): string {
  return importTypeOptions.find((item) => item.value === value)?.label || value || "-";
}

function statusText(status: string): string {
  if (status === "SUCCESS") return "成功";
  if (status === "FAILED") return "失败";
  return "已接收";
}

function statusType(status: string): "success" | "warning" | "danger" {
  if (status === "SUCCESS") return "success";
  if (status === "FAILED") return "danger";
  return "warning";
}

function uploaderText(row: ImportFileLogVO): string {
  return row.uploaderRealName || row.uploaderUsername || row.uploaderUserId?.toString() || "-";
}

function countText(row: ImportFileLogVO): string {
  const rowCount = row.rowCount ?? "-";
  const success = row.successCount ?? "-";
  const failed = row.failureCount ?? "-";
  return `${rowCount} 行 / 成功 ${success} / 失败 ${failed}`;
}

function fileMeta(row: ImportFileLogVO): string {
  return [formatFileSize(row.fileSize), row.contentType || row.fileExt].filter(Boolean).join(" / ") || "-";
}

function formatFileSize(size: number | null | undefined): string {
  if (!size || size <= 0) {
    return "0 B";
  }
  if (size < 1024) {
    return `${size} B`;
  }
  if (size < 1024 * 1024) {
    return `${(size / 1024).toFixed(1)} KB`;
  }
  return `${(size / 1024 / 1024).toFixed(1)} MB`;
}
</script>

<style scoped>
.retention-note {
  margin-bottom: 14px;
  border: 1px solid #bfdbfe;
  border-radius: 6px;
  background: #eff6ff;
  color: #1e3a8a;
  font-size: 13px;
  line-height: 1.6;
  padding: 10px 12px;
}

.filter-bar {
  display: grid;
  grid-template-columns: 140px 190px 120px minmax(260px, 1fr) auto auto;
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
  max-height: 240px;
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
