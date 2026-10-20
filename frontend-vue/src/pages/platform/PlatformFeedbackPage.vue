<template>
  <VnetPage title="意见反馈">
    <template #actions>
      <el-button :loading="loading" @click="loadFeedbacks">刷新</el-button>
    </template>

    <VnetPanel title="反馈记录">
      <div class="filter-bar">
        <el-select v-model="query.category" clearable placeholder="反馈类型" @change="handleFilterChange">
          <el-option v-for="item in categoryOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-select v-model="query.status" clearable placeholder="处理状态" @change="handleFilterChange">
          <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-input
          v-model="query.keyword"
          clearable
          placeholder="编号 / 标题 / 内容 / 链接 / 提交人"
          @keyup.enter="handleFilterChange"
          @clear="handleFilterChange"
        />
        <el-button type="primary" @click="handleFilterChange">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="feedbacks" stripe>
        <el-table-column label="反馈信息" min-width="270">
          <template #default="{ row }">
            <div class="main-cell">
              <strong>{{ row.feedbackNo }}</strong>
              <span>{{ row.title || "-" }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="110">
          <template #default="{ row }">
            <el-tag effect="light">{{ categoryLabel(row.category) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="提交人" min-width="160">
          <template #default="{ row }">
            <div class="main-cell">
              <strong>{{ submitterText(row) }}</strong>
              <span>{{ row.submitterDeptName || row.submitterUsername || "-" }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="附件" width="90">
          <template #default="{ row }">
            {{ row.attachmentCount || 0 }} 张
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" effect="light">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="提交时间" min-width="170" />
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

    <el-dialog v-model="detailVisible" title="意见反馈详情" width="960px" @closed="cleanupPreviewUrls">
      <template v-if="currentFeedback">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="反馈编号">{{ currentFeedback.feedbackNo }}</el-descriptions-item>
          <el-descriptions-item label="处理状态">
            <el-tag :type="statusType(currentFeedback.status)" effect="light">{{ statusLabel(currentFeedback.status) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="类型">{{ categoryLabel(currentFeedback.category) }}</el-descriptions-item>
          <el-descriptions-item label="提交时间">{{ currentFeedback.createdAt || "-" }}</el-descriptions-item>
          <el-descriptions-item label="提交人">{{ submitterText(currentFeedback) }}</el-descriptions-item>
          <el-descriptions-item label="部门">{{ currentFeedback.submitterDeptName || "-" }}</el-descriptions-item>
          <el-descriptions-item label="标题" :span="2">{{ currentFeedback.title || "-" }}</el-descriptions-item>
          <el-descriptions-item label="提交页面" :span="2">
            <a v-if="currentFeedback.pageUrl" :href="currentFeedback.pageUrl" target="_blank" rel="noreferrer">
              {{ currentFeedback.pageUrl }}
            </a>
            <span v-else>-</span>
          </el-descriptions-item>
        </el-descriptions>

        <section class="detail-block">
          <h3>文字说明</h3>
          <pre>{{ currentFeedback.content || "-" }}</pre>
        </section>

        <section class="detail-block">
          <h3>相关链接</h3>
          <div v-if="feedbackLinks.length > 0" class="link-list">
            <a v-for="link in feedbackLinks" :key="link" :href="link" target="_blank" rel="noreferrer">{{ link }}</a>
          </div>
          <pre v-else>-</pre>
        </section>

        <section class="detail-block">
          <h3>图片附件</h3>
          <div v-if="currentFeedback.attachments.length > 0" class="attachment-grid">
            <div v-for="attachment in currentFeedback.attachments" :key="attachment.id" class="attachment-item">
              <el-image
                v-if="isImageAttachment(attachment) && attachmentPreviewUrls[attachment.id]"
                :src="attachmentPreviewUrls[attachment.id]"
                :preview-src-list="Object.values(attachmentPreviewUrls)"
                fit="cover"
                class="attachment-preview"
              />
              <div v-else class="attachment-placeholder">{{ attachment.fileExt || "FILE" }}</div>
              <div class="attachment-meta">
                <strong>{{ attachment.fileName }}</strong>
                <span>{{ formatFileSize(attachment.fileSize) }}</span>
              </div>
              <el-button link type="primary" @click="downloadAttachment(attachment)">下载</el-button>
            </div>
          </div>
          <pre v-else>-</pre>
        </section>

        <section class="detail-block">
          <h3>处理</h3>
          <div class="status-form">
            <el-select v-model="statusForm.status" placeholder="处理状态">
              <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
            <el-input
              v-model="statusForm.handleComment"
              type="textarea"
              :rows="2"
              maxlength="500"
              show-word-limit
              placeholder="处理说明"
            />
            <el-button type="primary" :loading="savingStatus" @click="saveStatus">保存处理状态</el-button>
          </div>
          <p v-if="currentFeedback.handlerName || currentFeedback.handleComment" class="handle-summary">
            最近处理：{{ currentFeedback.handlerName || "-" }} /
            {{ currentFeedback.handledAt || "-" }} /
            {{ currentFeedback.handleComment || "-" }}
          </p>
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
import { ElMessage } from "element-plus";
import VnetPage from "../../components/vnet/VnetPage.vue";
import VnetPanel from "../../components/vnet/VnetPanel.vue";
import {
  downloadPlatformFeedbackAttachmentApi,
  getPlatformFeedbackDetailApi,
  getPlatformFeedbackPageApi,
  updatePlatformFeedbackStatusApi
} from "../../api/platform";
import type { PlatformFeedbackAttachmentVO, PlatformFeedbackQuery, PlatformFeedbackVO } from "../../types/platform";

const categoryOptions = [
  { label: "问题反馈", value: "BUG" },
  { label: "功能建议", value: "SUGGESTION" },
  { label: "数据问题", value: "DATA" },
  { label: "其他", value: "OTHER" }
];

const statusOptions = [
  { label: "待处理", value: "OPEN" },
  { label: "处理中", value: "PROCESSING" },
  { label: "已解决", value: "RESOLVED" },
  { label: "已忽略", value: "IGNORED" }
];

const query = reactive<PlatformFeedbackQuery>({
  pageNo: 1,
  pageSize: 10,
  category: "",
  status: "",
  keyword: ""
});

const loading = ref(false);
const feedbacks = ref<PlatformFeedbackVO[]>([]);
const total = ref(0);
const detailVisible = ref(false);
const currentFeedback = ref<PlatformFeedbackVO | null>(null);
const attachmentPreviewUrls = ref<Record<number, string>>({});
const savingStatus = ref(false);
const statusForm = reactive({
  status: "OPEN",
  handleComment: ""
});

const feedbackLinks = computed(() =>
  (currentFeedback.value?.links || "")
    .split(/\r?\n/)
    .map((item) => item.trim())
    .filter(Boolean)
);

onMounted(() => {
  void loadFeedbacks();
});

async function loadFeedbacks(): Promise<void> {
  loading.value = true;
  try {
    const page = await getPlatformFeedbackPageApi({
      pageNo: query.pageNo,
      pageSize: query.pageSize,
      category: query.category || undefined,
      status: query.status || undefined,
      keyword: query.keyword?.trim() || undefined
    });
    feedbacks.value = page.records;
    total.value = page.total;
  } finally {
    loading.value = false;
  }
}

function handleFilterChange(): void {
  query.pageNo = 1;
  void loadFeedbacks();
}

function resetFilters(): void {
  query.category = "";
  query.status = "";
  query.keyword = "";
  handleFilterChange();
}

function handlePageChange(pageNo: number): void {
  query.pageNo = pageNo;
  void loadFeedbacks();
}

function handleSizeChange(pageSize: number): void {
  query.pageSize = pageSize;
  query.pageNo = 1;
  void loadFeedbacks();
}

async function openDetail(row: PlatformFeedbackVO): Promise<void> {
  cleanupPreviewUrls();
  currentFeedback.value = await getPlatformFeedbackDetailApi(row.id);
  statusForm.status = currentFeedback.value.status || "OPEN";
  statusForm.handleComment = currentFeedback.value.handleComment || "";
  detailVisible.value = true;
  await loadAttachmentPreviews();
}

async function loadAttachmentPreviews(): Promise<void> {
  const feedback = currentFeedback.value;
  if (!feedback) {
    return;
  }
  const previews: Record<number, string> = {};
  await Promise.all(
    feedback.attachments
      .filter(isImageAttachment)
      .map(async (attachment) => {
        const blob = await downloadPlatformFeedbackAttachmentApi(attachment.id);
        previews[attachment.id] = URL.createObjectURL(blob);
      })
  );
  attachmentPreviewUrls.value = previews;
}

async function downloadAttachment(attachment: PlatformFeedbackAttachmentVO): Promise<void> {
  const blob = await downloadPlatformFeedbackAttachmentApi(attachment.id);
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = attachment.fileName || `feedback-${attachment.id}`;
  link.click();
  URL.revokeObjectURL(url);
}

async function saveStatus(): Promise<void> {
  if (!currentFeedback.value) {
    return;
  }
  savingStatus.value = true;
  try {
    await updatePlatformFeedbackStatusApi(currentFeedback.value.id, {
      status: statusForm.status,
      handleComment: statusForm.handleComment.trim()
    });
    ElMessage.success("处理状态已保存");
    currentFeedback.value = await getPlatformFeedbackDetailApi(currentFeedback.value.id);
    await loadFeedbacks();
  } finally {
    savingStatus.value = false;
  }
}

function cleanupPreviewUrls(): void {
  Object.values(attachmentPreviewUrls.value).forEach((url) => URL.revokeObjectURL(url));
  attachmentPreviewUrls.value = {};
}

function submitterText(row: PlatformFeedbackVO): string {
  return row.submitterRealName || row.submitterUsername || row.submitterUserId?.toString() || "-";
}

function categoryLabel(value: string): string {
  return categoryOptions.find((item) => item.value === value)?.label || value || "-";
}

function statusLabel(value: string): string {
  return statusOptions.find((item) => item.value === value)?.label || value || "-";
}

function statusType(value: string): "success" | "warning" | "danger" | "info" | "primary" {
  if (value === "RESOLVED") return "success";
  if (value === "PROCESSING") return "warning";
  if (value === "IGNORED") return "info";
  return "danger";
}

function isImageAttachment(attachment: PlatformFeedbackAttachmentVO): boolean {
  return Boolean(attachment.contentType?.startsWith("image/"));
}

function formatFileSize(size: number | null | undefined): string {
  if (!size || size <= 0) return "0 B";
  if (size < 1024) return `${size} B`;
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`;
  return `${(size / 1024 / 1024).toFixed(1)} MB`;
}
</script>

<style scoped>
.filter-bar {
  display: grid;
  grid-template-columns: 150px 150px minmax(260px, 1fr) auto auto;
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
  max-height: 220px;
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

.link-list {
  display: grid;
  gap: 6px;
}

.link-list a {
  color: var(--el-color-primary);
  word-break: break-all;
}

.attachment-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 12px;
}

.attachment-item {
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
  padding: 10px;
}

.attachment-preview,
.attachment-placeholder {
  width: 100%;
  height: 132px;
  border-radius: 4px;
  background: #f8fafc;
}

.attachment-placeholder {
  display: grid;
  place-items: center;
  color: var(--app-text-soft);
  font-weight: 700;
}

.attachment-meta {
  display: grid;
  gap: 4px;
  margin: 8px 0 4px;
  min-width: 0;
}

.attachment-meta strong,
.attachment-meta span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.attachment-meta span,
.handle-summary {
  color: var(--app-text-soft);
  font-size: 12px;
}

.status-form {
  display: grid;
  grid-template-columns: 150px minmax(260px, 1fr) auto;
  gap: 10px;
  align-items: start;
}

@media (max-width: 900px) {
  .filter-bar,
  .status-form {
    grid-template-columns: 1fr;
  }
}
</style>
