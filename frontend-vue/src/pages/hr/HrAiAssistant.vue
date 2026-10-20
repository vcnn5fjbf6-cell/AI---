<template>
  <VnetPage title="AI 智能问答">
    <template #actions>
      <el-button :icon="Setting" @click="openSettings">设置</el-button>
      <el-button :icon="Delete" @click="clearChat">清空</el-button>
    </template>

    <div class="hr-ai-shell">
      <div ref="messageList" class="hr-ai-messages">
        <div class="hr-ai-message system">
          <span class="hr-ai-avatar">🤖</span>
          <div class="hr-ai-content">
            <div>你好，我是人力资源智能助手，可以回答人员、考勤、加班、成本等问题。</div>
            <div class="hr-ai-suggestions">
              <el-tag v-for="suggestion in suggestions" :key="suggestion" class="hr-ai-suggestion" @click="quickAsk(suggestion)">
                {{ suggestion }}
              </el-tag>
            </div>
          </div>
        </div>
        <div v-for="(message, index) in hrStore.aiMessages" :key="index" class="hr-ai-message" :class="message.role">
          <span class="hr-ai-avatar">{{ message.role === "user" ? "👤" : "🤖" }}</span>
          <div class="hr-ai-content">
            <div class="hr-ai-role">{{ message.role === "user" ? "你" : "AI" }}</div>
            <div class="hr-ai-text">{{ message.content }}</div>
          </div>
        </div>
        <div v-if="loading" class="hr-ai-message assistant">
          <span class="hr-ai-avatar">🤖</span>
          <div class="hr-ai-content hr-ai-typing">正在思考...</div>
        </div>
      </div>
      <div class="hr-ai-input">
        <el-input
          v-model="input"
          type="textarea"
          :rows="2"
          resize="none"
          placeholder="输入问题，Enter 发送，Shift+Enter 换行"
          @keydown.enter.exact.prevent="send"
        />
        <el-button type="primary" :loading="loading" @click="send">发送</el-button>
      </div>
    </div>

    <el-dialog v-model="settingsVisible" title="AI 配置" width="480px">
      <el-form label-width="90px">
        <el-form-item label="API 地址">
          <el-input v-model="configForm.endpoint" placeholder="https://api.openai.com/v1/chat/completions" />
        </el-form-item>
        <el-form-item label="模型名称">
          <el-input v-model="configForm.model" placeholder="qwen2.5 / gpt-4o-mini / deepseek-chat" />
        </el-form-item>
        <el-form-item label="API Key">
          <el-input v-model="configForm.apiKey" type="password" show-password placeholder="sk-..." />
        </el-form-item>
        <el-form-item label="最大 Token">
          <el-input-number v-model="configForm.maxTokens" :min="256" :max="8192" style="width: 100%" />
        </el-form-item>
        <el-form-item label="温度">
          <el-input-number v-model="configForm.temperature" :min="0" :max="2" :step="0.1" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="settingsVisible = false">取消</el-button>
        <el-button type="primary" @click="saveSettings">保存</el-button>
      </template>
    </el-dialog>
  </VnetPage>
</template>

<script setup lang="ts">
import { nextTick, onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { Delete, Setting } from "@element-plus/icons-vue";
import VnetPage from "../../components/vnet/VnetPage.vue";
import { useHrStore } from "../../stores/hr";

const hrStore = useHrStore();
const input = ref("");
const loading = ref(false);
const settingsVisible = ref(false);
const messageList = ref<HTMLElement>();
const suggestions = [
  "公司总共有多少人？",
  "今天出勤情况如何？",
  "本月加班总时长多少？",
  "有多少待审批请假？",
  "本月成本结算总额是多少？",
  "各岗位人数分布如何？",
  "本月新增/离职人员情况？",
  "帮我介绍下某个人员的技能简介"
];

const configForm = reactive({
  endpoint: hrStore.aiConfig.endpoint,
  model: hrStore.aiConfig.model,
  apiKey: hrStore.aiConfig.apiKey,
  maxTokens: hrStore.aiConfig.maxTokens,
  temperature: hrStore.aiConfig.temperature
});

function buildSystemPrompt(): string {
  const employees = hrStore.employees;
  const stats = hrStore.stats;
  const attendance = hrStore.attendance;
  const leaves = hrStore.leaveRequests;
  const overtime = hrStore.overtime;
  const month = new Date().toISOString().slice(0, 7);
  const companyCounts = new Map<string, number>();
  for (const emp of employees) {
    companyCounts.set(emp.company || "-", (companyCounts.get(emp.company || "-") || 0) + 1);
  }
  const monthAttendance = attendance.filter((item) => item.date?.startsWith(month));
  const monthPresent = monthAttendance.filter((item) => item.status === "present" || item.status === "late").length;
  const monthAbsent = monthAttendance.filter((item) => item.status === "absent").length;
  const approvedLeaves = leaves.filter((item) => item.status === "approved" && item.startDate?.startsWith(month)).length;
  const monthOtHours = overtime
    .filter((item) => item.date?.startsWith(month))
    .reduce((sum, item) => sum + (item.hours || 0), 0);
  const pendingOvertime = overtime.filter((item) => item.status === "pending").length;
  const monthApprovedLeaves = leaves.filter((item) => item.status === "approved" && item.startDate?.startsWith(month)).length;
  const settlement = hrStore.getOrCalcSettlement(month);
  const buildingCounts = new Map<string, number>();
  const positionCounts = new Map<string, number>();
  for (const emp of employees) {
    buildingCounts.set(emp.building || "未分配", (buildingCounts.get(emp.building || "未分配") || 0) + 1);
    positionCounts.set(emp.position || "未分配", (positionCounts.get(emp.position || "未分配") || 0) + 1);
  }
  const lines = [
    "你是华北一部酒仙桥人力资源管理平台的智能助手，请根据系统实时数据回答用户问题。",
    "",
    "【系统概况】",
    `总人数：${employees.length} 人`,
    `公司分布：${Array.from(companyCounts.entries()).map(([key, value]) => `${key} ${value}人`).join("、")}`,
    "",
    "【今日概览】",
    `日期：${new Date().toISOString().slice(0, 10)}`,
    `今日出勤：${stats.todayPresent} 人`,
    `今日缺勤：${stats.todayAbsent} 人`,
    `待审批请假：${stats.pendingLeave} 条`,
    "",
    `【本月数据（${month}）】`,
    `月度出勤：${monthPresent} 人次`,
    `月度缺勤：${monthAbsent} 人次`,
    `已批准请假：${approvedLeaves} 条`,
    `月度加班总时长：${monthOtHours.toFixed(1)} 小时`,
    `待审批加班：${pendingOvertime} 条`,
    `本月结算总额：¥${settlement?.totalSalary?.toFixed?.(0) ?? "0"}`,
    `楼栋人数：${Array.from(buildingCounts.entries()).map(([key, value]) => `${key} ${value}人`).join("、")}`,
    `岗位人数：${Array.from(positionCounts.entries()).map(([key, value]) => `${key} ${value}人`).join("、")}`,
    "",
    "【人员列表】",
    ...employees.slice(0, 50).map((emp) => `${emp.name} / ${emp.position || "-"} / ${emp.company || "-"}`),
    "",
    "请用中文回答，保持简洁准确。"
  ];
  return lines.join("\n");
}

async function send(): Promise<void> {
  const text = input.value.trim();
  if (!text || loading.value) return;
  input.value = "";
  hrStore.saveAiMessages([...hrStore.aiMessages, { role: "user", content: text }]);
  loading.value = true;
  await scrollToBottom();
  try {
    const config = hrStore.aiConfig;
    if (!config.apiKey) throw new Error("请先在 AI 设置中填写 API Key");
    const endpoint = config.endpoint || "https://api.openai.com/v1/chat/completions";
    const recent = hrStore.aiMessages.slice(-20, -1);
    const messages = [
      { role: "system", content: config.systemPrompt || buildSystemPrompt() },
      ...recent,
      { role: "user", content: text }
    ];
    const headers: Record<string, string> = { "Content-Type": "application/json" };
    if (config.apiKey) headers.Authorization = `Bearer ${config.apiKey}`;
    const response = await fetch(endpoint, {
      method: "POST",
      headers,
      body: JSON.stringify({
        model: config.model || "gpt-4o-mini",
        messages,
        max_tokens: config.maxTokens,
        temperature: config.temperature,
        stream: false
      })
    });
    if (!response.ok) {
      throw new Error(`API 请求失败 (${response.status})`);
    }
    const data = await response.json();
    const reply =
      (data.choices?.[0]?.message?.content as string | undefined) ||
      (typeof data.response === "string" ? data.response : JSON.stringify(data));
    hrStore.saveAiMessages([...hrStore.aiMessages, { role: "assistant", content: reply }]);
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    ElMessage.error(`AI 请求失败：${message}`);
    hrStore.saveAiMessages([...hrStore.aiMessages, { role: "assistant", content: `[错误] ${message}` }]);
  } finally {
    loading.value = false;
    await scrollToBottom();
  }
}

function quickAsk(text: string): void {
  input.value = text;
  void send();
}

function clearChat(): void {
  ElMessageBox.confirm("确定清空所有对话历史？", "清空确认", { type: "warning" })
    .then(() => {
      hrStore.saveAiMessages([]);
      ElMessage.success("对话已清空");
    })
    .catch(() => undefined);
}

function openSettings(): void {
  configForm.endpoint = hrStore.aiConfig.endpoint;
  configForm.model = hrStore.aiConfig.model;
  configForm.apiKey = hrStore.aiConfig.apiKey;
  configForm.maxTokens = hrStore.aiConfig.maxTokens;
  configForm.temperature = hrStore.aiConfig.temperature;
  settingsVisible.value = true;
}

function saveSettings(): void {
  hrStore.saveAiConfig({
    endpoint: configForm.endpoint.trim(),
    model: configForm.model.trim(),
    apiKey: configForm.apiKey.trim(),
    maxTokens: Number(configForm.maxTokens) || 2048,
    temperature: Number(configForm.temperature) || 0.7
  });
  ElMessage.success("AI 配置已保存");
  settingsVisible.value = false;
}

async function scrollToBottom(): Promise<void> {
  await nextTick();
  const el = messageList.value;
  if (el) el.scrollTop = el.scrollHeight;
}

onMounted(() => {
  hrStore.load();
  void scrollToBottom();
});
</script>

<style scoped>
.hr-ai-shell {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 220px);
  min-height: 520px;
  overflow: hidden;
  border: 1px solid var(--app-line);
  border-radius: var(--app-radius);
  background: #ffffff;
  box-shadow: var(--app-shadow);
}

.hr-ai-messages {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
}

.hr-ai-message {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
}

.hr-ai-message.user {
  justify-content: flex-end;
}

.hr-ai-avatar {
  display: grid;
  flex: 0 0 36px;
  width: 36px;
  height: 36px;
  place-items: center;
  border-radius: 8px;
  background: var(--app-primary-soft);
  font-size: 18px;
}

.hr-ai-message.user .hr-ai-avatar {
  background: #eef2ff;
}

.hr-ai-content {
  max-width: 78%;
  padding: 10px 14px;
  border: 1px solid var(--app-line-soft);
  border-radius: var(--app-radius);
  background: var(--app-surface-muted);
  font-size: 14px;
  line-height: 1.7;
}

.hr-ai-message.user .hr-ai-content {
  border-color: var(--app-primary);
  background: var(--app-primary);
  color: #ffffff;
}

.hr-ai-role {
  margin-bottom: 4px;
  color: var(--app-text-muted);
  font-size: 11px;
  font-weight: 700;
}

.hr-ai-message.user .hr-ai-role {
  color: rgba(255, 255, 255, 0.8);
}

.hr-ai-suggestions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

.hr-ai-suggestion {
  cursor: pointer;
}

.hr-ai-typing {
  color: var(--app-text-muted);
}

.hr-ai-input {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  padding: 14px;
  border-top: 1px solid var(--app-line-soft);
  background: #f8fafc;
}

.hr-ai-input .el-input {
  flex: 1;
}
:deep(.vnet-page) {
  background:
    linear-gradient(rgba(13, 32, 56, 0.55), rgba(13, 32, 56, 0.55)),
    url("/hr-bg.png") center / cover no-repeat;
  padding: 16px;
  border-radius: 12px;
  min-height: calc(100vh - 120px);
}

</style>
