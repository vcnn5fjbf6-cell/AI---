<template>
  <VnetPage title="灾害预警设置">
    <template #actions>
      <el-button :loading="loading" @click="loadSettings">刷新</el-button>
      <el-button type="warning" plain :loading="testingWarning" @click="testWarningPush">测试预警推送</el-button>
      <el-button type="primary" :loading="saving" @click="saveSettings">保存设置</el-button>
    </template>

    <VnetPanel title="区域与接口">
      <div class="config-tip">
        这里维护当前监控区域、普通天气接口、实时预警接口、正式值班群、测试群和自动推送开关。定时任务只推正式值班群；测试按钮和手动任务只推测试群。
      </div>

      <el-form :model="settings" label-position="top" class="settings-grid">
        <el-form-item label="启用灾害预警">
          <el-switch v-model="settings.enabled" active-value="true" inactive-value="false" />
        </el-form-item>
        <el-form-item label="和风天气 Key">
          <el-input v-model="settings.qweatherKey" type="password" show-password placeholder="留空不修改；页面不回显完整 Key" />
          <small v-if="settings.qweatherKeyMasked">当前已配置：{{ settings.qweatherKeyMasked }}</small>
        </el-form-item>
        <el-form-item label="普通天气 API 地址">
          <el-input v-model="settings.qweatherHost" placeholder="https://devapi.qweather.com" />
          <small>普通天气 API 域名，用于 /v7/weather/now 和 /v7/weather/7d。</small>
        </el-form-item>
        <el-form-item label="预警 API 模式">
          <el-select v-model="settings.qweatherWarningApiMode">
            <el-option label="中央气象台当前预警" value="NMC" />
            <el-option label="自动：有 Token 走新版，无 Token 走旧版" value="AUTO" />
            <el-option label="新版 Weather Alert API" value="WEATHER_ALERT" />
            <el-option label="旧版 /v7/warning/now" value="V7_WARNING" />
          </el-select>
        </el-form-item>
        <el-form-item label="预警 API 地址">
          <el-input v-model="settings.qweatherWarningHost" placeholder="https://www.nmc.cn" />
          <small>NMC 模式填写中央气象台域名；和风新版模式填写 /weatheralert/v1/current/纬度/经度 所在域名。</small>
        </el-form-item>
        <el-form-item label="预警 API Token">
          <el-input v-model="settings.qweatherWarningToken" type="password" show-password placeholder="留空不修改；新版预警接口使用 Bearer Token" />
          <small v-if="settings.qweatherWarningTokenMasked">当前已配置：{{ settings.qweatherWarningTokenMasked }}</small>
        </el-form-item>
        <el-form-item label="城市 Code">
          <el-input v-model="settings.cityCode" placeholder="101010300" />
        </el-form-item>
        <el-form-item label="中心经纬度">
          <el-input v-model="settings.location" placeholder="116.50,39.98" />
        </el-form-item>
        <el-form-item label="中心地址">
          <el-input v-model="settings.centerAddress" placeholder="监控中心详细地址" />
        </el-form-item>
        <el-form-item label="推送区域名称">
          <el-input v-model="settings.pushCity" placeholder="例如：北京市朝阳区酒仙桥" />
        </el-form-item>
        <el-form-item label="值班群名称">
          <el-input v-model="settings.pushGroup" placeholder="值班群" />
        </el-form-item>
        <el-form-item label="值班群 chat_id">
          <el-input v-model="settings.dutyChatId" placeholder="oc_xxx" />
          <small>正式自动日报、周报和预警推送目标群。</small>
        </el-form-item>
        <el-form-item label="测试群 chat_id">
          <el-input v-model="settings.testChatId" placeholder="oc_xxx" />
          <small>测试预警和手动推送只发送到该群；未配置时测试会失败，不会回退到正式值班群。</small>
        </el-form-item>
        <el-form-item label="每日9点预报">
          <el-switch v-model="settings.dailyPushEnabled" active-value="true" inactive-value="false" />
        </el-form-item>
        <el-form-item label="周一周报">
          <el-switch v-model="settings.weeklyPushEnabled" active-value="true" inactive-value="false" />
        </el-form-item>
        <el-form-item label="天气预警推送">
          <el-switch v-model="settings.warningPushEnabled" active-value="true" inactive-value="false" />
        </el-form-item>
        <el-form-item label="预警解除推送">
          <el-switch v-model="settings.warningResolvePushEnabled" active-value="true" inactive-value="false" />
          <small>开启后，只有收到官方解除预警通知时，才会向正式值班群推送一次解除通知。</small>
        </el-form-item>
        <el-form-item label="新交付楼栋提级">
          <el-switch v-model="settings.newDeliveryPromotionEnabled" active-value="true" inactive-value="false" />
          <small>未真实经历过极端天气的新交付楼栋开启；按制度将预警等级提升一级执行。</small>
        </el-form-item>
        <el-form-item label="雨季专项提醒">
          <el-switch v-model="settings.rainySeasonEnabled" active-value="true" inactive-value="false" />
          <small>开启后，雨季月份内会在处置建议中加入防水、排水和防汛物资检查要求。</small>
        </el-form-item>
        <el-form-item label="雨季月份">
          <el-input v-model="settings.rainySeasonMonths" placeholder="6,7,8,9" />
          <small>逗号分隔月份；按当地历史雨季并经区域负责人确认后维护。</small>
        </el-form-item>
      </el-form>

      <div class="color-test-panel">
        <span class="color-test-label">卡片颜色测试</span>
        <el-button
          v-for="item in colorTestButtons"
          :key="item.color"
          size="small"
          :class="['color-test-button', `color-${item.color}`]"
          :loading="testingColor === item.color"
          @click="testColorPush(item.color)"
        >
          {{ item.label }}
        </el-button>
      </div>
    </VnetPanel>

    <VnetPanel title="湿球温度建议规则">
      <div class="rule-toolbar">
        <div class="config-tip compact">
          后端按室外湿球温度命中下方规则，命中多条时会合并为天气卡片和记录里的处置建议；边界为空表示不限制。
        </div>
        <div class="rule-actions">
          <el-button @click="addWetBulbRule">新增规则</el-button>
          <el-button @click="resetWetBulbRules">恢复默认规则</el-button>
        </div>
      </div>

      <el-table :data="wetBulbRules" border class="wet-bulb-rule-table">
        <el-table-column label="启用" width="80" align="center">
          <template #default="{ row }">
            <el-switch v-model="row.enabled" />
          </template>
        </el-table-column>
        <el-table-column label="下限℃" width="120">
          <template #default="{ row }">
            <el-input-number v-model="row.min" :controls="false" :precision="1" :step="0.5" :min="-50" :max="60" placeholder="不限" />
          </template>
        </el-table-column>
        <el-table-column label="含下限" width="92" align="center">
          <template #default="{ row }">
            <el-switch v-model="row.includeMin" />
          </template>
        </el-table-column>
        <el-table-column label="上限℃" width="120">
          <template #default="{ row }">
            <el-input-number v-model="row.max" :controls="false" :precision="1" :step="0.5" :min="-50" :max="60" placeholder="不限" />
          </template>
        </el-table-column>
        <el-table-column label="含上限" width="92" align="center">
          <template #default="{ row }">
            <el-switch v-model="row.includeMax" />
          </template>
        </el-table-column>
        <el-table-column label="处置建议" min-width="360">
          <template #default="{ row }">
            <el-input v-model="row.advice" type="textarea" :rows="2" maxlength="260" show-word-limit />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ $index }">
            <el-button type="danger" link @click="removeWetBulbRule($index)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-form :model="settings" label-position="top" class="wet-bulb-note">
        <el-form-item label="湿球温度说明">
          <el-input v-model="settings.wetBulbNote" type="textarea" :rows="2" maxlength="300" show-word-limit />
        </el-form-item>
      </el-form>
    </VnetPanel>

    <VnetPanel title="重保天气预警规则">
      <div class="rule-toolbar">
        <div class="config-tip compact">
          自动预警只推送命中规则且重保类型为一级、二级或三级戒备的预警；未命中的预警只写入记录，不打扰值班群。
        </div>
        <div class="rule-actions">
          <el-button @click="addRule">新增规则</el-button>
          <el-button @click="resetRules">恢复默认规则</el-button>
        </div>
      </div>

      <el-table :data="warningRules" border>
        <el-table-column label="启用" width="80" align="center">
          <template #default="{ row }">
            <el-switch v-model="row.enabled" />
          </template>
        </el-table-column>
        <el-table-column label="预警类型" min-width="130">
          <template #default="{ row }">
            <el-input v-model="row.type" placeholder="暴雨" />
          </template>
        </el-table-column>
        <el-table-column label="预警颜色" width="150">
          <template #default="{ row }">
            <div class="color-cell">
              <span class="color-dot" :class="dotClass(row.color)"></span>
              <el-select v-model="row.color" class="color-select">
                <el-option label="红色" value="红色" />
                <el-option label="橙色" value="橙色" />
                <el-option label="黄色" value="黄色" />
                <el-option label="蓝色" value="蓝色" />
              </el-select>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="重保类型" width="150">
          <template #default="{ row }">
            <el-select v-model="row.guard">
              <el-option label="一级戒备" value="一级戒备" />
              <el-option label="二级戒备" value="二级戒备" />
              <el-option label="三级戒备" value="三级戒备" />
              <el-option label="无匹配标签" value="无匹配标签" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="卡片颜色" width="150">
          <template #default="{ row }">
            <div class="color-cell">
              <span class="color-dot" :class="dotClass(row.card)"></span>
              <el-select v-model="row.card" class="color-select">
                <el-option label="红色" value="red" />
                <el-option label="橙色" value="orange" />
                <el-option label="黄色" value="yellow" />
                <el-option label="蓝色" value="blue" />
              </el-select>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ $index }">
            <el-button type="danger" link @click="removeRule($index)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </VnetPanel>
    <el-dialog v-model="testResultVisible" title="预警测试结果预览" width="560px">
      <template v-if="testResult">
        <div class="test-preview-card" :class="guardColorClass(testResult.guardTag, testResult.guardAlertTag)">
          <div class="preview-header">
            <span class="preview-tag">{{ testResult.guardTag || "常规观察" }}</span>
            <span class="preview-city">{{ testResult.pushCity || "-" }}</span>
          </div>
          <div class="preview-body">
            <div class="preview-row">
              <span class="preview-label">预警信息</span>
              <strong>{{ testResult.warningInfo || "当前无预警" }}</strong>
            </div>
            <div class="preview-row">
              <span class="preview-label">今日天气</span>
              <strong>{{ testResult.todayWeatherInfo || "-" }}</strong>
            </div>
            <div class="preview-row">
              <span class="preview-label">湿球温度</span>
              <strong>{{ testResult.wetBulbInfo || "-" }}</strong>
            </div>
            <div class="preview-row">
              <span class="preview-label">发送状态</span>
              <strong>{{ sendStatusText(testResult.sendStatus) }}</strong>
            </div>
          </div>
        </div>
        <div class="color-legend">
          <div class="legend-item"><span class="color-dot dot-red"></span>一级戒备 / 红色预警</div>
          <div class="legend-item"><span class="color-dot dot-orange"></span>二级戒备 / 橙色预警</div>
          <div class="legend-item"><span class="color-dot dot-yellow"></span>三级戒备 / 黄色预警</div>
          <div class="legend-item"><span class="color-dot dot-blue"></span>蓝色预警</div>
          <div class="legend-item"><span class="color-dot dot-green"></span>常规观察（无预警）</div>
        </div>
      </template>
      <template #footer>
        <el-button @click="testResultVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </VnetPage>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import VnetPage from "../../components/vnet/VnetPage.vue";
import VnetPanel from "../../components/vnet/VnetPanel.vue";
import { getDisasterWeatherSettingsApi, triggerDisasterWeatherApi, updateDisasterWeatherSettingsApi } from "../../api/disaster-warning";
import type { DisasterWarningRule, DisasterWeatherRecordVO, DisasterWeatherSettingsForm, DisasterWetBulbRule } from "../../types/disaster-warning";

const defaultRules: DisasterWarningRule[] = [
  ...["暴雨", "暴雪", "台风", "洪水"].map((type) => rule(type, "红色", "一级戒备", "red")),
  ...["雷电", "冰雹", "寒潮", "霜冻", "高温"].map((type) => rule(type, "红色", "二级戒备", "red")),
  ...["暴雨", "暴雪", "台风", "洪水", "雷电", "冰雹", "寒潮", "霜冻", "高温"].map((type) =>
    rule(type, "橙色", ["暴雨", "暴雪", "台风", "洪水"].includes(type) ? "二级戒备" : "三级戒备", "orange")
  ),
  ...["暴雨", "暴雪", "台风", "洪水"].map((type) => rule(type, "黄色", "三级戒备", "yellow")),
  ...["雷电", "冰雹", "寒潮", "霜冻", "高温"].map((type) => rule(type, "黄色", "无匹配标签", "yellow")),
  ...["暴雨", "暴雪", "台风", "洪水"].map((type) => rule(type, "蓝色", "三级戒备", "blue")),
  ...["雷电", "冰雹", "寒潮", "霜冻", "高温"].map((type) => rule(type, "蓝色", "无匹配标签", "blue"))
];

const defaultWetBulbRules: DisasterWetBulbRule[] = [
  wetBulbRule(null, 6.5, true, true, "建议完全自由冷却模式，由板式换热器免费制冷。"),
  wetBulbRule(6.5, 12.5, false, true, "建议预冷制冷模式，冷水机组+板式换热器换热。"),
  wetBulbRule(12.5, null, false, true, "建议机组制冷模式，由冷水机组制冷。"),
  wetBulbRule(18, 24, false, false, "建议关注机房湿度情况，尽量加开除湿机组，保持运行。"),
  wetBulbRule(18, 24, false, false, "建议减少新风系统，或经过除湿后再进入机房。"),
  wetBulbRule(18, 24, false, false, "建议关注发电机房，避免湿度过高造成设备结露并影响运行。"),
  wetBulbRule(24, null, false, true, "建议关注冷却塔运行情况；冷却侧若按出水温度控制，建议按湿球温度+4℃逼近度设定出水温度，过渡季意义较大。逼近度一般按3-7℃并结合相对湿度估算。")
];

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
  wetBulbNote: "湿度关系：空气湿度越高，蒸发越慢，湿球温度与干球温度差值越小；反之，干燥环境下两者温差越大。"
};

const settings = reactive({ ...defaultSettings });
const warningRules = ref<DisasterWarningRule[]>([]);
const wetBulbRules = ref<DisasterWetBulbRule[]>([]);
const loading = ref(false);
const saving = ref(false);
const testingWarning = ref(false);
const testingColor = ref("");
const testResultVisible = ref(false);
const testResult = ref<DisasterWeatherRecordVO | null>(null);
const colorTestButtons: Array<{ color: "red" | "orange" | "yellow" | "blue" | "green"; label: string }> = [
  { color: "red", label: "红色测试" },
  { color: "orange", label: "橙色测试" },
  { color: "yellow", label: "黄色测试" },
  { color: "blue", label: "蓝色测试" },
  { color: "green", label: "常规测试" }
];

onMounted(() => {
  void loadSettings();
});

async function loadSettings(): Promise<void> {
  loading.value = true;
  try {
    const data = await getDisasterWeatherSettingsApi();
    Object.assign(settings, {
      ...defaultSettings,
      ...data,
      qweatherKey: ""
    });
    warningRules.value = parseRules(data.warningRulesJson);
    wetBulbRules.value = parseWetBulbRules(data.wetBulbRulesJson);
  } finally {
    loading.value = false;
  }
}

async function saveSettings(): Promise<void> {
  saving.value = true;
  try {
    await updateDisasterWeatherSettingsApi({
      enabled: settings.enabled,
      qweatherKey: settings.qweatherKey || undefined,
      qweatherHost: settings.qweatherHost,
      qweatherWarningApiMode: settings.qweatherWarningApiMode,
      qweatherWarningHost: settings.qweatherWarningHost,
      qweatherWarningToken: settings.qweatherWarningToken || undefined,
      cityCode: settings.cityCode,
      location: settings.location,
      centerAddress: settings.centerAddress,
      pushCity: settings.pushCity,
      pushGroup: settings.pushGroup,
      dutyChatId: settings.dutyChatId,
      testChatId: settings.testChatId,
      dailyPushEnabled: settings.dailyPushEnabled,
      weeklyPushEnabled: settings.weeklyPushEnabled,
      warningPushEnabled: settings.warningPushEnabled,
      warningResolvePushEnabled: settings.warningResolvePushEnabled,
      newDeliveryPromotionEnabled: settings.newDeliveryPromotionEnabled,
      rainySeasonEnabled: settings.rainySeasonEnabled,
      rainySeasonMonths: settings.rainySeasonMonths,
      warningRulesJson: JSON.stringify(normalizeRules(warningRules.value)),
      wetBulbRulesJson: JSON.stringify(normalizeWetBulbRules(wetBulbRules.value)),
      wetBulbNote: settings.wetBulbNote
    });
    settings.qweatherKey = "";
    settings.qweatherWarningToken = "";
    ElMessage.success("灾害预警设置已保存");
    await loadSettings();
  } finally {
    saving.value = false;
  }
}

async function testWarningPush(): Promise<void> {
  testingWarning.value = true;
  try {
    const record = await triggerDisasterWeatherApi({ recordType: "WARNING", sendToFeishu: true });
    testResult.value = record;
    testResultVisible.value = true;
    ElMessage.success(`已生成预警测试记录：${record.queryRecordId}，测试群发送状态：${sendStatusText(record.sendStatus)}`);
  } finally {
    testingWarning.value = false;
  }
}

async function testColorPush(color: "red" | "orange" | "yellow" | "blue" | "green"): Promise<void> {
  testingColor.value = color;
  try {
    const record = await triggerDisasterWeatherApi({ recordType: "WARNING", sendToFeishu: true, testCardColor: color });
    testResult.value = record;
    testResultVisible.value = true;
    ElMessage.success(`已发送${colorName(color)}测试卡片：${record.queryRecordId}，测试群发送状态：${sendStatusText(record.sendStatus)}`);
  } finally {
    testingColor.value = "";
  }
}

function addRule(): void {
  warningRules.value.push(rule("", "黄色", "三级戒备", "yellow"));
}

function removeRule(index: number): void {
  warningRules.value.splice(index, 1);
}

function resetRules(): void {
  warningRules.value = cloneRules(defaultRules);
}

function addWetBulbRule(): void {
  wetBulbRules.value.push(wetBulbRule(null, null, true, true, ""));
}

function removeWetBulbRule(index: number): void {
  wetBulbRules.value.splice(index, 1);
}

function resetWetBulbRules(): void {
  wetBulbRules.value = cloneWetBulbRules(defaultWetBulbRules);
}

function parseRules(raw?: string): DisasterWarningRule[] {
  try {
    const rows = raw ? (JSON.parse(raw) as DisasterWarningRule[]) : [];
    const normalized = normalizeRules(rows);
    return normalized.length > 0 ? normalized : cloneRules(defaultRules);
  } catch {
    return cloneRules(defaultRules);
  }
}

function parseWetBulbRules(raw?: string): DisasterWetBulbRule[] {
  try {
    const rows = raw ? (JSON.parse(raw) as DisasterWetBulbRule[]) : [];
    const normalized = normalizeWetBulbRules(rows);
    return normalized.length > 0 ? normalized : cloneWetBulbRules(defaultWetBulbRules);
  } catch {
    return cloneWetBulbRules(defaultWetBulbRules);
  }
}

function normalizeRules(rows: DisasterWarningRule[]): DisasterWarningRule[] {
  return rows
    .map((item) => ({
      type: String(item.type || "").trim(),
      color: String(item.color || "").trim(),
      guard: String(item.guard || "无匹配标签").trim(),
      card: String(item.card || colorToCard(item.color)).trim(),
      enabled: item.enabled !== false
    }))
    .filter((item) => item.type && item.color);
}

function normalizeWetBulbRules(rows: DisasterWetBulbRule[]): DisasterWetBulbRule[] {
  return rows
    .map((item) => ({
      min: normalizeNullableNumber(item.min),
      max: normalizeNullableNumber(item.max),
      includeMin: item.includeMin !== false,
      includeMax: item.includeMax !== false,
      advice: String(item.advice || "").trim(),
      enabled: item.enabled !== false
    }))
    .filter((item) => item.advice && (item.min !== null || item.max !== null));
}

function cloneRules(rows: DisasterWarningRule[]): DisasterWarningRule[] {
  return rows.map((item) => ({ ...item }));
}

function cloneWetBulbRules(rows: DisasterWetBulbRule[]): DisasterWetBulbRule[] {
  return rows.map((item) => ({ ...item }));
}

function rule(type: string, color: string, guard: string, card: string): DisasterWarningRule {
  return { type, color, guard, card, enabled: true };
}

function wetBulbRule(
  min: number | null,
  max: number | null,
  includeMin: boolean,
  includeMax: boolean,
  advice: string
): DisasterWetBulbRule {
  return { min, max, includeMin, includeMax, advice, enabled: true };
}

function normalizeNullableNumber(value: number | string | null | undefined): number | null {
  if (value === null || value === undefined || value === "") return null;
  const numberValue = Number(value);
  return Number.isFinite(numberValue) ? numberValue : null;
}

function colorToCard(color: string): string {
  if (color === "红色") return "red";
  if (color === "橙色") return "orange";
  if (color === "黄色") return "yellow";
  return "blue";
}

function dotClass(color: string): string {
  const map: Record<string, string> = {
    "红色": "dot-red", "red": "dot-red",
    "橙色": "dot-orange", "orange": "dot-orange",
    "黄色": "dot-yellow", "yellow": "dot-yellow",
    "蓝色": "dot-blue", "blue": "dot-blue"
  };
  return map[color] || "dot-blue";
}

function guardColorClass(guardTag?: string | null, cardColor?: string | null): string {
  if (cardColor === "red") return "guard-red";
  if (cardColor === "orange") return "guard-orange";
  if (cardColor === "yellow") return "guard-yellow";
  if (cardColor === "blue") return "guard-blue";
  if (cardColor === "green") return "guard-green";
  if (!guardTag) return "guard-green";
  if (guardTag.includes("一级")) return "guard-red";
  if (guardTag.includes("二级")) return "guard-orange";
  if (guardTag.includes("三级")) return "guard-yellow";
  if (guardTag.includes("蓝色")) return "guard-blue";
  return "guard-green";
}

function sendStatusText(status?: string | null): string {
  const map: Record<string, string> = {
    SENT: "已发送",
    FAILED: "发送失败",
    SKIPPED: "已跳过",
    NOT_SENT: "未发送"
  };
  return status ? map[status] || status : "-";
}

function colorName(color: string): string {
  const map: Record<string, string> = {
    red: "红色",
    orange: "橙色",
    yellow: "黄色",
    blue: "蓝色",
    green: "常规"
  };
  return map[color] || color;
}
</script>

<style scoped>
.config-tip {
  margin-bottom: 14px;
  padding: 10px 12px;
  border-radius: 6px;
  background: #f8fafc;
  color: #475569;
  line-height: 1.6;
}

.config-tip.compact {
  margin: 0;
}

.settings-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 2px 16px;
}

.settings-grid small {
  display: block;
  margin-top: 4px;
  color: #64748b;
}

.color-test-panel {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 14px;
  padding: 10px 12px;
  border-radius: 6px;
  background: #f8fafc;
}

.color-test-label {
  margin-right: 4px;
  color: #475569;
  font-weight: 600;
}

.color-test-button {
  border-color: transparent;
  color: #fff;
}

.color-test-button.color-red { background: #ef4444; }
.color-test-button.color-orange { background: #f97316; }
.color-test-button.color-yellow { background: #eab308; }
.color-test-button.color-blue { background: #3b82f6; }
.color-test-button.color-green { background: #22c55e; }

.color-test-button:hover,
.color-test-button:focus {
  color: #fff;
  filter: brightness(0.96);
}

.rule-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}

.rule-actions {
  display: flex;
  gap: 10px;
  white-space: nowrap;
}

.wet-bulb-rule-table {
  margin-bottom: 16px;
}

.wet-bulb-rule-table :deep(.el-input-number) {
  width: 100%;
}

.wet-bulb-note {
  max-width: 900px;
}

/* 颜色圆点 */
.color-dot {
  display: inline-block;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  flex-shrink: 0;
  border: 1px solid rgba(0, 0, 0, 0.1);
}

.color-dot.dot-red { background: #ef4444; }
.color-dot.dot-orange { background: #f97316; }
.color-dot.dot-yellow { background: #eab308; }
.color-dot.dot-blue { background: #3b82f6; }
.color-dot.dot-green { background: #22c55e; }

.color-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.color-cell .color-select {
  flex: 1;
}

/* 测试预览卡片 */
.test-preview-card {
  border-radius: 10px;
  overflow: hidden;
  border: 2px solid;
}

.test-preview-card.guard-red {
  border-color: #ef4444;
  background: linear-gradient(135deg, #fef2f2 0%, #fff 100%);
}

.test-preview-card.guard-orange {
  border-color: #f97316;
  background: linear-gradient(135deg, #fff7ed 0%, #fff 100%);
}

.test-preview-card.guard-yellow {
  border-color: #eab308;
  background: linear-gradient(135deg, #fefce8 0%, #fff 100%);
}

.test-preview-card.guard-blue {
  border-color: #3b82f6;
  background: linear-gradient(135deg, #eff6ff 0%, #fff 100%);
}

.test-preview-card.guard-green {
  border-color: #22c55e;
  background: linear-gradient(135deg, #f0fdf4 0%, #fff 100%);
}

.preview-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 18px;
  font-size: 15px;
}

.guard-red .preview-header { background: #ef4444; color: #fff; }
.guard-orange .preview-header { background: #f97316; color: #fff; }
.guard-yellow .preview-header { background: #eab308; color: #fff; }
.guard-blue .preview-header { background: #3b82f6; color: #fff; }
.guard-green .preview-header { background: #22c55e; color: #fff; }

.preview-tag {
  font-weight: 700;
  font-size: 16px;
}

.preview-city {
  font-size: 13px;
  opacity: 0.9;
}

.preview-body {
  padding: 16px 18px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.preview-row {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.preview-label {
  flex-shrink: 0;
  width: 70px;
  color: #64748b;
  font-size: 13px;
}

.preview-row strong {
  color: #0f172a;
  font-size: 14px;
}

/* 颜色图例 */
.color-legend {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 16px;
  padding: 12px;
  border-radius: 8px;
  background: #f8fafc;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #475569;
}

@media (max-width: 1200px) {
  .settings-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .rule-toolbar {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
