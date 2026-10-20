<template>
  <VnetPage title="预警记录">
    <template #actions>
      <el-button :loading="loading" @click="loadPage">刷新</el-button>
    </template>

    <section class="weather-overview">
      <div class="weather-card primary">
        <span>监控区域</span>
        <strong>{{ settings.pushCity || "北京市朝阳区酒仙桥" }}</strong>
        <p>{{ settings.centerAddress || "北京市朝阳区酒仙桥东路1号M6楼" }}</p>
      </div>
      <div class="weather-card">
        <span>最新天气</span>
        <strong>{{ overview?.latestRecord?.todayWeatherInfo || "暂无记录" }}</strong>
        <p>{{ overview?.latestRecord?.createdAt || "-" }}</p>
      </div>
      <div class="weather-card">
        <span>湿球温度</span>
        <strong>{{ wetBulbText }}</strong>
        <p>{{ overview?.latestRecord?.wetBulbAdvice || "等待拉取天气数据" }}</p>
      </div>
      <div class="weather-card guard-card" :class="guardColorClass(overview?.latestRecord?.guardTag)">
        <span>戒备状态</span>
        <strong>{{ overview?.latestRecord?.guardTag || "常规观察" }}</strong>
        <p>{{ overview?.latestRecord?.warningInfo || "当前无记录预警" }}</p>
      </div>
    </section>

    <VnetPanel title="手动任务">
      <div class="config-tip">
        当前区域、和风天气 Key、正式值班群、测试群和重保预警规则请在“灾害预警 / 预警设置”中维护。自动任务：每天 09:00 推送今日预报，每周一 09:05 推送本周预报，预警任务每 1 分钟检查一次；本页手动发送只进入测试群。
      </div>

      <div class="panel-actions">
        <el-button :loading="triggering === 'DAILY_FETCH'" @click="triggerWeather('DAILY', false)">只拉取今日天气</el-button>
        <el-button type="primary" plain :loading="triggering === 'DAILY_SEND'" @click="triggerWeather('DAILY', true)">
          发送今日预报到测试群
        </el-button>
        <el-button type="primary" plain :loading="triggering === 'WEEKLY_SEND'" @click="triggerWeather('WEEKLY', true)">
          发送本周预报到测试群
        </el-button>
        <el-button type="warning" plain :loading="triggering === 'WARNING_SEND'" @click="triggerWeather('WARNING', true)">
          检查并推送预警到测试群
        </el-button>
      </div>
    </VnetPanel>

    <VnetPanel title="天气预警记录">
      <div class="filter-bar">
        <el-select v-model="query.recordType" clearable placeholder="记录类型" @change="handleFilterChange">
          <el-option label="今日预报" value="DAILY" />
          <el-option label="本周预报" value="WEEKLY" />
          <el-option label="天气预警" value="WARNING" />
        </el-select>
        <el-select v-model="query.sendStatus" clearable placeholder="发送状态" @change="handleFilterChange">
          <el-option label="未发送" value="NOT_SENT" />
          <el-option label="已发送" value="SENT" />
          <el-option label="发送失败" value="FAILED" />
          <el-option label="已跳过" value="SKIPPED" />
        </el-select>
        <el-select v-model="query.guardLevel" clearable placeholder="戒备等级" @change="handleFilterChange">
          <el-option label="一级戒备" value="一级" />
          <el-option label="二级戒备" value="二级" />
          <el-option label="三级戒备" value="三级" />
          <el-option label="常规观察" value="常规" />
        </el-select>
        <el-input
          v-model="query.keyword"
          clearable
          placeholder="记录编号 / 天气 / 预警 / 错误"
          @keyup.enter="handleFilterChange"
          @clear="handleFilterChange"
        />
        <el-button type="primary" @click="handleFilterChange">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="records" stripe>
        <el-table-column prop="createdAt" label="记录时间" min-width="170" />
        <el-table-column label="类型" width="100">
          <template #default="{ row }">{{ recordTypeText(row.recordType) }}</template>
        </el-table-column>
        <el-table-column label="天气" min-width="260">
          <template #default="{ row }">
            <div class="main-cell">
              <strong>{{ row.todayWeatherInfo || "-" }}</strong>
              <span>{{ row.wetBulbInfo || "-" }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="预警/戒备" min-width="220">
          <template #default="{ row }">
            <div class="main-cell">
              <el-tag :type="guardTagType(row.guardTag)" effect="dark" size="small">{{ row.guardTag || "常规观察" }}</el-tag>
              <span>{{ row.warningInfo || "无预警" }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="推送" width="130">
          <template #default="{ row }">
            <el-tag :type="sendStatusType(row.sendStatus)" effect="light">{{ sendStatusText(row.sendStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="错误" min-width="220">
          <template #default="{ row }">
            <span class="line-text">{{ row.lastError || "-" }}</span>
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

    <el-dialog v-model="detailVisible" title="天气预警记录详情" width="960px">
      <template v-if="currentRecord">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="记录编号">{{ currentRecord.queryRecordId }}</el-descriptions-item>
          <el-descriptions-item label="记录类型">{{ recordTypeText(currentRecord.recordType) }}</el-descriptions-item>
          <el-descriptions-item label="推送区域">{{ currentRecord.pushCity }}</el-descriptions-item>
          <el-descriptions-item label="推送群组">{{ currentRecord.pushGroup || "-" }}</el-descriptions-item>
          <el-descriptions-item label="今日天气" :span="2">{{ currentRecord.todayWeatherInfo || "-" }}</el-descriptions-item>
          <el-descriptions-item label="预警信息" :span="2">{{ currentRecord.warningInfo || "无预警" }}</el-descriptions-item>
          <el-descriptions-item label="戒备标签">
            <el-tag :type="guardTagType(currentRecord.guardTag)" effect="dark" size="small">{{ currentRecord.guardTag || "常规观察" }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="发送状态">{{ sendStatusText(currentRecord.sendStatus) }}</el-descriptions-item>
          <el-descriptions-item label="湿球温度">{{ currentRecord.wetBulbInfo || "-" }}</el-descriptions-item>
          <el-descriptions-item label="实时降雨量">{{ currentRecord.realTimePrecipitation ?? "-" }}</el-descriptions-item>
          <el-descriptions-item label="飞书消息ID" :span="2">{{ currentRecord.messageId || "-" }}</el-descriptions-item>
        </el-descriptions>

        <section class="detail-block">
          <h3>未来天气</h3>
          <ol>
            <li v-for="(day, index) in currentRecord.forecastDays.filter(Boolean)" :key="index">{{ day }}</li>
          </ol>
        </section>
        <section class="detail-block">
          <h3>湿球温度建议</h3>
          <pre>{{ currentRecord.wetBulbAdvice || "-" }}</pre>
        </section>
        <section class="detail-block">
          <h3>重保步骤</h3>
          <pre>{{ currentRecord.guardSteps || "-" }}</pre>
        </section>
        <section v-if="currentRecord.lastError" class="detail-block">
          <h3>错误信息</h3>
          <pre>{{ currentRecord.lastError }}</pre>
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
  getDisasterWeatherOverviewApi,
  getDisasterWeatherRecordsApi,
  triggerDisasterWeatherApi
} from "../../api/disaster-warning";
import type {
  DisasterWeatherOverviewVO,
  DisasterWeatherRecordQuery,
  DisasterWeatherRecordVO,
  DisasterWeatherSettingsForm
} from "../../types/disaster-warning";

const defaultSettings: DisasterWeatherSettingsForm & { qweatherKeyMasked?: string; qweatherWarningTokenMasked?: string } = {
  enabled: "true",
  qweatherKey: "",
  qweatherKeyMasked: "",
  qweatherHost: "https://devapi.qweather.com",
  qweatherWarningApiMode: "NMC",
  qweatherWarningHost: "https://www.nmc.cn",
  qweatherWarningToken: "",
  qweatherWarningTokenMasked: "",
  cityCode: "101010300",
  location: "116.50,39.98",
  centerAddress: "北京市朝阳区酒仙桥东路1号M6楼",
  pushCity: "北京市朝阳区酒仙桥",
  pushGroup: "值班群",
  dutyChatId: "",
  testChatId: "",
  dailyPushEnabled: "true",
  weeklyPushEnabled: "true",
  warningPushEnabled: "true",
  warningResolvePushEnabled: "true",
  newDeliveryPromotionEnabled: "false",
  rainySeasonEnabled: "true",
  rainySeasonMonths: "6,7,8,9",
  warningRulesJson: "",
  wetBulbRulesJson: "",
  wetBulbNote: ""
};

const overview = ref<DisasterWeatherOverviewVO | null>(null);
const settings = reactive({ ...defaultSettings });
const query = reactive<DisasterWeatherRecordQuery>({
  pageNo: 1,
  pageSize: 10,
  recordType: "",
  sendStatus: "",
  guardLevel: "",
  keyword: ""
});

const loading = ref(false);
const triggering = ref("");
const records = ref<DisasterWeatherRecordVO[]>([]);
const total = ref(0);
const detailVisible = ref(false);
const currentRecord = ref<DisasterWeatherRecordVO | null>(null);

const wetBulbText = computed(() => {
  const value = overview.value?.latestRecord?.wetBulbTemp;
  return value == null ? "暂无" : `${value}℃`;
});

onMounted(() => {
  void loadPage();
});

async function loadPage(): Promise<void> {
  loading.value = true;
  try {
    overview.value = await getDisasterWeatherOverviewApi();
    Object.assign(settings, {
      ...defaultSettings,
      ...overview.value.settings,
      qweatherKey: ""
    });
    await loadRecords();
  } finally {
    loading.value = false;
  }
}

async function loadRecords(): Promise<void> {
  const page = await getDisasterWeatherRecordsApi({
    pageNo: query.pageNo,
    pageSize: query.pageSize,
    recordType: query.recordType || undefined,
    sendStatus: query.sendStatus || undefined,
    guardLevel: query.guardLevel || undefined,
    keyword: query.keyword?.trim() || undefined
  });
  records.value = page.records;
  total.value = page.total;
}

async function triggerWeather(recordType: "DAILY" | "WEEKLY" | "WARNING", sendToFeishu: boolean): Promise<void> {
  const key = `${recordType}_${sendToFeishu ? "SEND" : "FETCH"}`;
  triggering.value = key;
  try {
    const record = await triggerDisasterWeatherApi({ recordType, sendToFeishu });
    ElMessage.success(sendToFeishu ? `已生成并尝试推送：${sendStatusText(record.sendStatus)}` : "已拉取天气并记录");
    overview.value = { settings: overview.value?.settings || settings, latestRecord: record } as DisasterWeatherOverviewVO;
    query.pageNo = 1;
    await loadRecords();
  } finally {
    triggering.value = "";
  }
}

function handleFilterChange(): void {
  query.pageNo = 1;
  void loadRecords();
}

function resetFilters(): void {
  query.recordType = "";
  query.sendStatus = "";
  query.guardLevel = "";
  query.keyword = "";
  handleFilterChange();
}

function handlePageChange(pageNo: number): void {
  query.pageNo = pageNo;
  void loadRecords();
}

function handleSizeChange(pageSize: number): void {
  query.pageSize = pageSize;
  query.pageNo = 1;
  void loadRecords();
}

function openDetail(row: DisasterWeatherRecordVO): void {
  currentRecord.value = row;
  detailVisible.value = true;
}

function recordTypeText(value: string): string {
  const map: Record<string, string> = {
    DAILY: "今日预报",
    WEEKLY: "本周预报",
    WARNING: "天气预警"
  };
  return map[value] || value || "-";
}

function sendStatusText(value: string): string {
  const map: Record<string, string> = {
    NOT_SENT: "未发送",
    SENT: "已发送",
    FAILED: "发送失败",
    SKIPPED: "已跳过"
  };
  return map[value] || value || "-";
}

function guardColorClass(guardTag?: string | null): string {
  if (!guardTag) return "guard-normal";
  if (guardTag.includes("一级")) return "guard-red";
  if (guardTag.includes("二级")) return "guard-orange";
  if (guardTag.includes("三级")) return "guard-yellow";
  return "guard-normal";
}

function guardTagType(guardTag?: string | null): "danger" | "warning" | "success" | "info" {
  if (!guardTag) return "info";
  if (guardTag.includes("一级")) return "danger";
  if (guardTag.includes("二级")) return "warning";
  if (guardTag.includes("三级")) return "success";
  return "info";
}

function sendStatusType(value: string): "success" | "warning" | "danger" | "info" {
  if (value === "SENT") return "success";
  if (value === "FAILED") return "danger";
  if (value === "SKIPPED") return "info";
  return "warning";
}
</script>

<style scoped>
.weather-overview {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.weather-card {
  min-height: 112px;
  padding: 16px;
  border: 1px solid #dbe5f2;
  border-radius: 8px;
  background: #fff;
}

.weather-card.primary {
  border-color: #2563eb;
  background: linear-gradient(180deg, #eff6ff 0%, #fff 100%);
}

.guard-card.guard-red {
  border-color: #ef4444;
  background: linear-gradient(180deg, #fef2f2 0%, #fff 100%);
}

.guard-card.guard-red strong {
  color: #ef4444;
}

.guard-card.guard-orange {
  border-color: #f97316;
  background: linear-gradient(180deg, #fff7ed 0%, #fff 100%);
}

.guard-card.guard-orange strong {
  color: #f97316;
}

.guard-card.guard-yellow {
  border-color: #eab308;
  background: linear-gradient(180deg, #fefce8 0%, #fff 100%);
}

.guard-card.guard-yellow strong {
  color: #ca8a04;
}

.guard-card.guard-normal {
  border-color: #22c55e;
  background: linear-gradient(180deg, #f0fdf4 0%, #fff 100%);
}

.guard-card.guard-normal strong {
  color: #16a34a;
}

.weather-card span {
  display: block;
  color: #64748b;
  font-size: 13px;
}

.weather-card strong {
  display: block;
  margin-top: 8px;
  color: #0f172a;
  font-size: 18px;
}

.weather-card p {
  margin: 8px 0 0;
  color: #475569;
  line-height: 1.5;
}

.config-tip {
  margin-bottom: 14px;
  padding: 10px 12px;
  border-radius: 6px;
  background: #f8fafc;
  color: #475569;
}

.panel-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  padding-top: 8px;
  border-top: 1px solid #e2e8f0;
}

.filter-bar {
  display: grid;
  grid-template-columns: 140px 140px 140px 1fr auto auto;
  gap: 10px;
  margin-bottom: 14px;
}

.main-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.main-cell span,
.line-text {
  color: #64748b;
}

.pager-box {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}

.detail-block {
  margin-top: 16px;
}

.detail-block h3 {
  margin: 0 0 8px;
  font-size: 15px;
}

.detail-block pre {
  margin: 0;
  padding: 10px;
  white-space: pre-wrap;
  word-break: break-word;
  border: 1px solid #dbe5f2;
  border-radius: 6px;
  background: #f8fafc;
}

.detail-block ol {
  margin: 0;
  padding-left: 20px;
}

@media (max-width: 1200px) {
  .weather-overview,
  .filter-bar {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
