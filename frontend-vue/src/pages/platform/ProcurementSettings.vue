<template>
  <VnetPage :title="pageTitle">
    <template #actions>
      <el-button type="primary" :loading="saving" @click="saveSettings">保存配置</el-button>
    </template>

    <VnetPanel v-if="showIntegrationSettings" title="API 变量">
      <el-table v-loading="loading" :data="apiSettings" stripe>
        <el-table-column prop="settingLabel" label="名称" width="220" />
        <el-table-column prop="settingKey" label="变量名" min-width="260" />
        <el-table-column label="变量值" min-width="420">
          <template #default="{ row }">
            <el-switch
              v-if="row.valueType === 'BOOLEAN'"
              v-model="row.settingValue"
              active-value="true"
              inactive-value="false"
              active-text="启用"
              inactive-text="关闭"
            />
            <el-input-number
              v-else-if="row.valueType === 'NUMBER'"
              v-model="row.settingValue"
              :min="0"
              :precision="0"
              controls-position="right"
              style="width: 100%"
            />
            <el-select
              v-else-if="row.settingKey === 'dify_app_type'"
              v-model="row.settingValue"
              clearable
              placeholder="推荐 chatflow"
              style="width: 100%"
            >
              <el-option label="Chatflow / 聊天流" value="chatflow" />
              <el-option label="Workflow / 工作流" value="workflow" />
            </el-select>
            <el-input
              v-else
              v-model="row.settingValue"
              :type="row.valueType === 'PASSWORD' ? 'password' : 'text'"
              :show-password="row.valueType === 'PASSWORD'"
              clearable
            />
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="说明" min-width="360" show-overflow-tooltip />
      </el-table>
    </VnetPanel>

    <VnetPanel v-if="showIntegrationSettings" title="系统运行设置">
      <el-table v-loading="loading" :data="systemSettings" stripe>
        <el-table-column prop="settingLabel" label="名称" width="220" />
        <el-table-column prop="settingKey" label="变量名" min-width="280" />
        <el-table-column label="变量值" min-width="420">
          <template #default="{ row }">
            <el-input
              v-model="row.settingValue"
              :type="row.valueType === 'PASSWORD' ? 'password' : 'text'"
              :show-password="row.valueType === 'PASSWORD'"
              clearable
            />
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="说明" min-width="360" show-overflow-tooltip />
      </el-table>
    </VnetPanel>

    <VnetPanel v-if="showIntegrationSettings" title="飞书应用与审批卡片">
      <template #actions>
        <el-button :loading="testingFeishu" @click="testFeishuApprovalCard">发送测试卡片</el-button>
        <el-button :loading="testingSupplierFeishu" @click="testFeishuSupplierQuoteCard">发送供应商报价样卡</el-button>
      </template>
      <el-table v-loading="loading" :data="feishuSettings" stripe>
        <el-table-column prop="settingLabel" label="名称" width="220" />
        <el-table-column prop="settingKey" label="变量名" min-width="280" />
        <el-table-column label="变量值" min-width="360">
          <template #default="{ row }">
            <el-switch
              v-if="row.valueType === 'BOOLEAN'"
              v-model="row.settingValue"
              active-value="true"
              inactive-value="false"
              active-text="启用"
              inactive-text="关闭"
            />
            <el-input-number
              v-else-if="row.valueType === 'NUMBER'"
              v-model="row.settingValue"
              :min="1"
              :precision="0"
              controls-position="right"
              style="width: 100%"
            />
            <el-select
              v-else-if="row.settingKey === 'feishu_approval_card_receive_id_type'"
              v-model="row.settingValue"
              clearable
              placeholder="默认 email"
              style="width: 100%"
            >
              <el-option label="open_id" value="open_id" />
              <el-option label="user_id" value="user_id" />
              <el-option label="union_id" value="union_id" />
              <el-option label="email" value="email" />
            </el-select>
            <el-select
              v-else-if="row.settingKey === 'feishu_department_id_type'"
              v-model="row.settingValue"
              clearable
              placeholder="默认 department_id"
              style="width: 100%"
            >
              <el-option label="department_id" value="department_id" />
              <el-option label="open_department_id" value="open_department_id" />
            </el-select>
            <el-select
              v-else-if="row.settingKey === 'feishu_root_department_id'"
              :model-value="splitMultiValue(row.settingValue)"
              multiple
              filterable
              remote
              reserve-keyword
              allow-create
              default-first-option
              clearable
              :remote-method="searchFeishuDepartments"
              :loading="searchingFeishuDepartments"
              placeholder="搜索部门名称或直接输入部门 ID"
              style="width: 100%"
              @update:model-value="updateMultiSetting(row, $event)"
            >
              <el-option
                v-for="department in feishuDepartmentOptions"
                :key="feishuDepartmentOptionValue(department)"
                :label="formatFeishuDepartmentOption(department)"
                :value="feishuDepartmentOptionValue(department)"
              />
            </el-select>
            <el-input
              v-else
              v-model="row.settingValue"
              :type="row.valueType === 'PASSWORD' ? 'password' : 'text'"
              :show-password="row.valueType === 'PASSWORD'"
              clearable
            />
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="说明" min-width="320" show-overflow-tooltip />
      </el-table>
    </VnetPanel>

    <VnetPanel v-if="showIntegrationSettings" title="飞书卡片日志">
      <template #actions>
        <el-button :loading="cardLogLoading" @click="loadCardLogs">刷新</el-button>
      </template>
      <div class="card-log-toolbar">
        <el-input
          v-model="cardLogQuery.keyword"
          clearable
          placeholder="申请单号 / 标题 / 审批人 / 接收 ID / 错误"
          @keyup.enter="searchCardLogs"
        />
        <el-select v-model="cardLogQuery.status" clearable placeholder="全部状态">
          <el-option v-for="status in cardStatusOptions" :key="status.value" :label="status.label" :value="status.value" />
        </el-select>
        <el-button type="primary" :loading="cardLogLoading" @click="searchCardLogs">查询</el-button>
        <el-button @click="resetCardLogQuery">重置</el-button>
      </div>
      <el-table v-loading="cardLogLoading" :data="cardLogPage.records" stripe>
        <el-table-column label="申请信息" min-width="260">
          <template #default="{ row }">
            <div class="card-log-main">{{ row.requestNo || `任务 ${row.approvalTaskId}` }}</div>
            <div class="card-log-sub">{{ row.requestTitle || "-" }}</div>
          </template>
        </el-table-column>
        <el-table-column label="审批节点" min-width="160">
          <template #default="{ row }">
            <div>{{ row.taskLevelName || "-" }}</div>
            <div class="card-log-sub">任务 {{ row.approvalTaskId }}</div>
          </template>
        </el-table-column>
        <el-table-column label="审批人" min-width="180">
          <template #default="{ row }">
            <div>{{ row.approverName || "-" }}</div>
            <div class="card-log-sub">{{ row.approverEmail || "-" }}</div>
            <div v-if="row.recipientName && row.recipientName !== row.approverName" class="card-log-sub">
              接收人：{{ row.recipientName }}
            </div>
          </template>
        </el-table-column>
        <el-table-column label="接收方式" min-width="190">
          <template #default="{ row }">
            <div>{{ row.receiveIdType || "-" }}</div>
            <div class="card-log-sub">{{ row.receiveId || "-" }}</div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="cardStatusTagType(row.status)" effect="light">
              {{ cardStatusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="retryCount" label="重试" width="80" />
        <el-table-column prop="sentAt" label="发送时间" width="180" />
        <el-table-column prop="lastError" label="最后错误" min-width="220" show-overflow-tooltip />
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button
              type="primary"
              link
              :loading="resendingCardLogId === row.id"
              :disabled="isFinalCardStatus(row.status)"
              @click="resendCardLog(row)"
            >
              重新发送
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        class="card-log-pagination"
        background
        layout="total, sizes, prev, pager, next, jumper"
        :current-page="cardLogQuery.pageNo"
        :page-size="cardLogQuery.pageSize"
        :page-sizes="[10, 20, 50]"
        :total="cardLogPage.total"
        @current-change="changeCardLogPage"
        @size-change="changeCardLogPageSize"
      />
    </VnetPanel>

    <VnetPanel v-if="showIntegrationSettings" title="供应商飞书卡片日志">
      <template #actions>
        <el-button :loading="supplierCardLogLoading" @click="loadSupplierCardLogs">刷新</el-button>
      </template>
      <div class="card-log-toolbar">
        <el-input
          v-model="supplierCardLogQuery.keyword"
          clearable
          placeholder="申请单号 / 供应商 / 接收 ID / 错误"
          @keyup.enter="searchSupplierCardLogs"
        />
        <el-select v-model="supplierCardLogQuery.status" clearable placeholder="全部状态">
          <el-option
            v-for="status in supplierNoticeStatusOptions"
            :key="status.value"
            :label="status.label"
            :value="status.value"
          />
        </el-select>
        <el-select v-model="supplierCardLogQuery.noticeType" clearable placeholder="全部类型">
          <el-option
            v-for="type in supplierNoticeTypeOptions"
            :key="type.value"
            :label="type.label"
            :value="type.value"
          />
        </el-select>
        <el-button type="primary" :loading="supplierCardLogLoading" @click="searchSupplierCardLogs">查询</el-button>
        <el-button @click="resetSupplierCardLogQuery">重置</el-button>
      </div>
      <el-table v-loading="supplierCardLogLoading" :data="supplierCardLogPage.records" stripe>
        <el-table-column label="申请信息" min-width="260">
          <template #default="{ row }">
            <div class="card-log-main">{{ row.requestNo || `申请 ${row.purchaseRequestId}` }}</div>
            <div class="card-log-sub">{{ row.requestTitle || "-" }}</div>
          </template>
        </el-table-column>
        <el-table-column label="供应商 / 抄送" min-width="220">
          <template #default="{ row }">
            <div>{{ row.supplierName || "-" }}</div>
            <div class="card-log-sub">{{ row.supplierCode || "-" }}</div>
            <div v-if="row.bindingId" class="card-log-sub">绑定 {{ row.bindingId }}</div>
            <div v-else-if="row.receiveIdType === 'admin_user'" class="card-log-sub">管理员抄送待配置</div>
          </template>
        </el-table-column>
        <el-table-column label="通知类型" width="130">
          <template #default="{ row }">{{ supplierNoticeTypeLabel(row.noticeType) }}</template>
        </el-table-column>
        <el-table-column label="接收方式" min-width="210">
          <template #default="{ row }">
            <div>{{ row.receiveIdType || "-" }}</div>
            <div class="card-log-sub">{{ row.receiveId || "-" }}</div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="supplierNoticeStatusTagType(row.status)" effect="light">
              {{ supplierNoticeStatusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="retryCount" label="重试" width="80" />
        <el-table-column prop="sentAt" label="发送时间" width="180" />
        <el-table-column prop="lastError" label="最后错误" min-width="240" show-overflow-tooltip />
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button
              type="primary"
              link
              :loading="resendingSupplierNoticeId === row.id"
              :disabled="row.receiveIdType === 'admin_user'"
              @click="resendSupplierNotice(row)"
            >
              重新发送
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        class="card-log-pagination"
        background
        layout="total, sizes, prev, pager, next, jumper"
        :current-page="supplierCardLogQuery.pageNo"
        :page-size="supplierCardLogQuery.pageSize"
        :page-sizes="[10, 20, 50]"
        :total="supplierCardLogPage.total"
        @current-change="changeSupplierCardLogPage"
        @size-change="changeSupplierCardLogPageSize"
      />
    </VnetPanel>

    <VnetPanel v-if="showIntegrationSettings" title="邮件服务器">
      <el-table v-loading="loading" :data="mailSettings" stripe>
        <el-table-column prop="settingLabel" label="名称" width="220" />
        <el-table-column prop="settingKey" label="变量名" min-width="260" />
        <el-table-column label="变量值" min-width="420">
          <template #default="{ row }">
            <el-switch
              v-if="row.valueType === 'BOOLEAN'"
              v-model="row.settingValue"
              active-value="true"
              inactive-value="false"
              active-text="启用"
              inactive-text="关闭"
            />
            <el-input-number
              v-else-if="row.valueType === 'NUMBER'"
              v-model="row.settingValue"
              :min="0"
              :precision="0"
              controls-position="right"
              style="width: 100%"
            />
            <el-input
              v-else
              v-model="row.settingValue"
              :type="row.valueType === 'PASSWORD' ? 'password' : 'text'"
              :show-password="row.valueType === 'PASSWORD'"
              clearable
            />
          </template>
        </el-table-column>
      </el-table>
    </VnetPanel>

    <VnetPanel v-if="showRuleSettings" title="个人劳保强制监管">
      <template #actions>
        <el-button type="primary" @click="addLaborRule">新增用品</el-button>
      </template>
      <el-alert
        class="labor-rule-alert"
        type="info"
        :closable="false"
        show-icon
        title="启用后，个人劳保申领页按周期、提前申请天数和数量限制校验；勾选强制监管的物品会作为安全合规重点提醒。"
      />
      <el-table v-loading="loading" :data="laborRules" stripe>
        <el-table-column label="用品编码" min-width="170">
          <template #default="{ row }">
            <el-input v-model="row.itemCode" clearable />
          </template>
        </el-table-column>
        <el-table-column label="用品名称" min-width="150">
          <template #default="{ row }">
            <el-input v-model="row.itemName" clearable />
          </template>
        </el-table-column>
        <el-table-column label="单位" width="90">
          <template #default="{ row }">
            <el-input v-model="row.unit" clearable />
          </template>
        </el-table-column>
        <el-table-column label="启用" width="110">
          <template #default="{ row }">
            <el-switch v-model="row.enabled" active-text="启用" inactive-text="停用" />
          </template>
        </el-table-column>
        <el-table-column label="强制监管" width="120">
          <template #default="{ row }">
            <el-switch v-model="row.forced" active-text="是" inactive-text="否" />
          </template>
        </el-table-column>
        <el-table-column label="周期天数" width="130">
          <template #default="{ row }">
            <el-input-number v-model="row.cycleDays" :min="1" :precision="0" controls-position="right" />
          </template>
        </el-table-column>
        <el-table-column label="提前申请" width="130">
          <template #default="{ row }">
            <el-input-number v-model="row.advanceDays" :min="0" :precision="0" controls-position="right" />
          </template>
        </el-table-column>
        <el-table-column label="提醒天数" width="130">
          <template #default="{ row }">
            <el-input-number v-model="row.reminderDays" :min="0" :precision="0" controls-position="right" />
          </template>
        </el-table-column>
        <el-table-column label="数量限制" width="130">
          <template #default="{ row }">
            <el-input-number v-model="row.quantityLimit" :min="1" :precision="0" controls-position="right" />
          </template>
        </el-table-column>
        <el-table-column label="紧急购买" width="120">
          <template #default="{ row }">
            <el-switch v-model="row.emergencyEnabled" active-text="开" inactive-text="关" />
          </template>
        </el-table-column>
        <el-table-column label="楼长/经理确认" width="150">
          <template #default="{ row }">
            <el-switch v-model="row.managerApprovalRequired" active-text="需要" inactive-text="不需要" />
          </template>
        </el-table-column>
        <el-table-column label="未领用提醒" width="130">
          <template #default="{ row }">
            <el-switch v-model="row.dailyUnclaimedReminderEnabled" active-text="开" inactive-text="关" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ $index }">
            <el-button type="danger" link @click="removeLaborRule($index)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </VnetPanel>

    <VnetPanel v-if="showRuleSettings" title="采购规则">
      <el-table v-loading="loading" :data="ruleSettings" stripe>
        <el-table-column prop="settingLabel" label="名称" min-width="220" />
        <el-table-column prop="settingKey" label="变量名" min-width="260" />
        <el-table-column label="变量值" min-width="260">
          <template #default="{ row }">
            <el-switch
              v-if="row.valueType === 'BOOLEAN'"
              v-model="row.settingValue"
              active-value="true"
              inactive-value="false"
              active-text="启用"
              inactive-text="关闭"
            />
            <el-input-number
              v-else-if="row.valueType === 'NUMBER'"
              v-model="row.settingValue"
              :min="0"
              :precision="0"
              controls-position="right"
              style="width: 100%"
            />
            <el-input v-else v-model="row.settingValue" clearable />
          </template>
        </el-table-column>
      </el-table>
    </VnetPanel>

    <VnetPanel v-if="showRuleSettings" title="部门采购上限">
      <template #actions>
        <el-button type="primary" @click="addDepartmentLimit">新增部门</el-button>
      </template>
      <el-table v-loading="loading" :data="departmentLimits" stripe>
        <el-table-column label="部门编码" min-width="160">
          <template #default="{ row }">
            <el-input v-model="row.deptCode" clearable />
          </template>
        </el-table-column>
        <el-table-column label="部门名称" min-width="180">
          <template #default="{ row }">
            <el-input v-model="row.deptName" clearable />
          </template>
        </el-table-column>
        <el-table-column label="单次上限" min-width="180">
          <template #default="{ row }">
            <el-input-number v-model="row.singleRequestLimit" :min="0" :precision="2" :step="1000" controls-position="right" />
          </template>
        </el-table-column>
        <el-table-column label="月度上限" min-width="180">
          <template #default="{ row }">
            <el-input-number v-model="row.monthlyLimit" :min="0" :precision="2" :step="1000" controls-position="right" />
          </template>
        </el-table-column>
        <el-table-column label="状态" width="140">
          <template #default="{ row }">
            <el-switch v-model="row.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" />
          </template>
        </el-table-column>
        <el-table-column label="备注" min-width="220">
          <template #default="{ row }">
            <el-input v-model="row.remark" clearable />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ $index }">
            <el-button type="danger" link @click="removeDepartmentLimit($index)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </VnetPanel>
  </VnetPage>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import VnetPage from "../../components/vnet/VnetPage.vue";
import VnetPanel from "../../components/vnet/VnetPanel.vue";
import {
  getFeishuApprovalCardNotificationsApi,
  getProcurementSettingsApi,
  getSupplierFeishuQuoteNoticesApi,
  resendFeishuApprovalCardNotificationApi,
  resendSupplierFeishuQuoteNoticeApi,
  sendFeishuApprovalCardTestApi,
  sendFeishuSupplierQuoteCardTestApi,
  updateProcurementSettingsApi
} from "../../api/platform";
import { searchFeishuDepartmentsApi } from "../../api/user";
import type { PageResult } from "../../types/common";
import type {
  FeishuApprovalCardNotificationQuery,
  FeishuApprovalCardNotificationVO,
  ProcurementDepartmentLimitVO,
  ProcurementSettingItemVO,
  SupplierFeishuQuoteNoticeQuery,
  SupplierFeishuQuoteNoticeVO
} from "../../types/platform";
import type { FeishuDepartmentSearchResult } from "../../types/system";

const loading = ref(false);
const saving = ref(false);
const testingFeishu = ref(false);
const testingSupplierFeishu = ref(false);
const searchingFeishuDepartments = ref(false);
const cardLogLoading = ref(false);
const resendingCardLogId = ref<number | null>(null);
const supplierCardLogLoading = ref(false);
const resendingSupplierNoticeId = ref<number | null>(null);
const settings = ref<ProcurementSettingItemVO[]>([]);
const departmentLimits = ref<ProcurementDepartmentLimitVO[]>([]);
const feishuDepartmentOptions = ref<FeishuDepartmentSearchResult[]>([]);
const cardLogQuery = ref<FeishuApprovalCardNotificationQuery>({
  pageNo: 1,
  pageSize: 10,
  status: "",
  keyword: ""
});
const cardLogPage = ref<PageResult<FeishuApprovalCardNotificationVO>>({
  pageNo: 1,
  pageSize: 10,
  total: 0,
  records: []
});
const supplierCardLogQuery = ref<SupplierFeishuQuoteNoticeQuery>({
  pageNo: 1,
  pageSize: 10,
  noticeType: "",
  status: "",
  keyword: ""
});
const supplierCardLogPage = ref<PageResult<SupplierFeishuQuoteNoticeVO>>({
  pageNo: 1,
  pageSize: 10,
  total: 0,
  records: []
});
const laborRules = ref<LaborRuleForm[]>([]);

type SettingsViewMode = "all" | "integration" | "rules";

interface LaborRuleForm {
  itemCode: string;
  itemName: string;
  unit: string;
  enabled: boolean;
  forced: boolean;
  cycleDays: number;
  quantityLimit: number;
  advanceDays: number;
  reminderDays: number;
  emergencyEnabled: boolean;
  managerApprovalRequired: boolean;
  dailyUnclaimedReminderEnabled: boolean;
}

const props = withDefaults(defineProps<{ viewMode?: SettingsViewMode }>(), {
  viewMode: "all"
});

const pageTitle = computed(() => {
  if (props.viewMode === "integration") {
    return "平台集成设置";
  }
  if (props.viewMode === "rules") {
    return "采购规则设置";
  }
  return "平台设置";
});
const showIntegrationSettings = computed(() => props.viewMode !== "rules");
const showRuleSettings = computed(() => props.viewMode !== "integration");

const cardStatusOptions = [
  { value: "PENDING", label: "待发送", type: "info" },
  { value: "SENT", label: "已发送", type: "success" },
  { value: "FAILED", label: "发送失败", type: "danger" },
  { value: "APPROVED", label: "已同意", type: "primary" },
  { value: "RETURNED", label: "已退回", type: "warning" },
  { value: "CLOSED", label: "已关闭", type: "info" }
] as const;

const supplierNoticeStatusOptions = [
  { value: "PENDING", label: "待发送", type: "info" },
  { value: "SENT", label: "已发送", type: "success" },
  { value: "FAILED", label: "发送失败", type: "danger" },
  { value: "SKIPPED", label: "已跳过", type: "warning" }
] as const;

const supplierNoticeTypeOptions = [
  { value: "QUOTE_TASK", label: "报价任务" },
  { value: "AWARD_CONFIRM", label: "中标确认" }
] as const;

const laborRulesSettingKey = "personal_labor_item_rules_json";
const defaultLaborRules: LaborRuleForm[] = [
  createLaborRule("LABOR_SHOES", "劳保鞋", "双", 365, 1, false),
  createLaborRule("LABOR_GLOVES", "劳保手套", "副", 365, 2, false),
  createLaborRule("INSULATED_GLOVES", "绝缘手套", "副", 180, 1, true),
  createLaborRule("FLASHLIGHT", "手电筒", "个", 365, 1, false),
  createLaborRule("TEST_PEN", "电笔", "支", 365, 1, true),
  createLaborRule("ANTI_STATIC_GLOVES", "防静电手套", "副", 365, 2, false),
  createLaborRule("ELECTRICAL_INSULATING_SHOES", "电绝缘鞋", "双", 365, 1, true)
];

const apiSettings = computed(() => settings.value.filter((item) => item.settingGroup === "API"));
const systemSettings = computed(() => settings.value.filter((item) => item.settingGroup === "SYSTEM"));
const feishuSettings = computed(() => settings.value.filter((item) => item.settingGroup === "FEISHU"));
const feishuDepartmentIdType = computed(() => {
  const setting = settings.value.find((item) => item.settingKey === "feishu_department_id_type");
  return String(setting?.settingValue || "department_id").trim() || "department_id";
});
const mailSettings = computed(() => settings.value.filter((item) => item.settingGroup === "MAIL"));
const ruleSettings = computed(() => settings.value.filter((item) => item.settingGroup === "RULE"));
onMounted(() => {
  loadSettings();
  if (showIntegrationSettings.value) {
    loadCardLogs();
    loadSupplierCardLogs();
  }
});

async function loadSettings(): Promise<void> {
  loading.value = true;
  try {
    const data = await getProcurementSettingsApi();
    settings.value = (data.settings || []).map((item) => ({
      ...item,
      settingValue: item.settingValue ?? ""
    }));
    parseLaborRulesFromSettings();
    departmentLimits.value = (data.departmentLimits || []).map((item) => ({
      ...item,
      singleRequestLimit: Number(item.singleRequestLimit || 0),
      monthlyLimit: Number(item.monthlyLimit || 0),
      status: item.status ?? 1
    }));
  } finally {
    loading.value = false;
  }
}

function addDepartmentLimit(): void {
  departmentLimits.value.push({
    deptCode: "",
    deptName: "",
    singleRequestLimit: 0,
    monthlyLimit: 0,
    status: 1,
    remark: ""
  });
}

function removeDepartmentLimit(index: number): void {
  departmentLimits.value.splice(index, 1);
}

function createLaborRule(
  itemCode = "",
  itemName = "",
  unit = "件",
  cycleDays = 365,
  quantityLimit = 1,
  forced = false
): LaborRuleForm {
  return {
    itemCode,
    itemName,
    unit,
    enabled: true,
    forced,
    cycleDays,
    quantityLimit,
    advanceDays: 30,
    reminderDays: 15,
    emergencyEnabled: true,
    managerApprovalRequired: forced,
    dailyUnclaimedReminderEnabled: true
  };
}

function parseLaborRulesFromSettings(): void {
  const setting = settings.value.find((item) => item.settingKey === laborRulesSettingKey);
  if (!setting || !String(setting.settingValue || "").trim()) {
    laborRules.value = defaultLaborRules.map((item) => ({ ...item }));
    ensureLaborRulesSetting();
    return;
  }
  try {
    const parsed = JSON.parse(String(setting.settingValue)) as Partial<LaborRuleForm>[];
    laborRules.value = normalizeLaborRules(parsed);
  } catch {
    laborRules.value = defaultLaborRules.map((item) => ({ ...item }));
    ElMessage.warning("个人劳保规则 JSON 格式异常，已按默认规则显示，请检查后保存");
  }
}

function normalizeLaborRules(rules: Partial<LaborRuleForm>[]): LaborRuleForm[] {
  const source = Array.isArray(rules) && rules.length > 0 ? rules : defaultLaborRules;
  return source
    .filter((item) => String(item.itemCode || "").trim() && String(item.itemName || "").trim())
    .map((item) => ({
      itemCode: String(item.itemCode || "").trim().toUpperCase(),
      itemName: String(item.itemName || "").trim(),
      unit: String(item.unit || "件").trim(),
      enabled: item.enabled !== false,
      forced: item.forced === true,
      cycleDays: Math.max(1, Number(item.cycleDays || 365)),
      quantityLimit: Math.max(1, Number(item.quantityLimit || 1)),
      advanceDays: Math.max(0, Number(item.advanceDays ?? 30)),
      reminderDays: Math.max(0, Number(item.reminderDays ?? 15)),
      emergencyEnabled: item.emergencyEnabled !== false,
      managerApprovalRequired: item.managerApprovalRequired === true,
      dailyUnclaimedReminderEnabled: item.dailyUnclaimedReminderEnabled !== false
    }));
}

function addLaborRule(): void {
  laborRules.value.push(createLaborRule(`LABOR_CUSTOM_${laborRules.value.length + 1}`, "新劳保用品"));
}

function removeLaborRule(index: number): void {
  laborRules.value.splice(index, 1);
}

function ensureLaborRulesSetting(): ProcurementSettingItemVO {
  let setting = settings.value.find((item) => item.settingKey === laborRulesSettingKey);
  if (!setting) {
    setting = {
      settingKey: laborRulesSettingKey,
      settingValue: "",
      settingGroup: "LABOR",
      settingLabel: "个人劳保用品规则",
      valueType: "TEXT",
      sortNo: 10,
      remark: "个人劳保申领的品类、周期、数量、提前申领、提醒和强制监管规则"
    };
    settings.value.push(setting);
  }
  return setting;
}

function syncLaborRulesToSetting(): boolean {
  const normalized = normalizeLaborRules(laborRules.value);
  if (normalized.length === 0) {
    ElMessage.warning("请至少保留一个劳保用品规则");
    return false;
  }
  const codeSet = new Set<string>();
  for (const rule of normalized) {
    if (!rule.itemCode || !rule.itemName || !rule.unit) {
      ElMessage.warning("请补齐劳保用品编码、名称和单位");
      return false;
    }
    if (codeSet.has(rule.itemCode)) {
      ElMessage.warning(`劳保用品编码重复：${rule.itemCode}`);
      return false;
    }
    codeSet.add(rule.itemCode);
    if (rule.advanceDays > rule.cycleDays) {
      ElMessage.warning(`${rule.itemName} 的提前申请天数不能大于周期天数`);
      return false;
    }
    if (rule.reminderDays > rule.cycleDays) {
      ElMessage.warning(`${rule.itemName} 的提醒天数不能大于周期天数`);
      return false;
    }
  }
  laborRules.value = normalized;
  const setting = ensureLaborRulesSetting();
  setting.settingValue = JSON.stringify(normalized);
  return true;
}

function splitMultiValue(value: unknown): string[] {
  return String(value ?? "")
    .split(/[,，;；\s]+/)
    .map((item) => item.trim())
    .filter(Boolean);
}

function updateMultiSetting(row: ProcurementSettingItemVO, value: unknown): void {
  const values = Array.isArray(value) ? value : [];
  row.settingValue = Array.from(new Set(values.map((item) => String(item || "").trim()).filter(Boolean))).join(",");
}

async function searchFeishuDepartments(keyword: string): Promise<void> {
  const query = String(keyword || "").trim();
  if (!query) {
    feishuDepartmentOptions.value = [];
    return;
  }
  searchingFeishuDepartments.value = true;
  try {
    feishuDepartmentOptions.value = await searchFeishuDepartmentsApi(query);
    if (feishuDepartmentOptions.value.length === 0) {
      ElMessage.warning("没有查到可见部门，请确认应用通讯录授权范围包含该部门");
    }
  } finally {
    searchingFeishuDepartments.value = false;
  }
}

function feishuDepartmentOptionValue(department: FeishuDepartmentSearchResult): string {
  const idType = feishuDepartmentIdType.value;
  if (idType === "open_department_id") {
    return department.openDepartmentId || department.departmentId || "";
  }
  return department.departmentId || department.openDepartmentId || "";
}

function formatFeishuDepartmentOption(department: FeishuDepartmentSearchResult): string {
  const value = feishuDepartmentOptionValue(department);
  const path = department.path && department.path !== department.name ? ` / ${department.path}` : "";
  return `${department.name || value}${path}（${value}）`;
}

async function testFeishuApprovalCard(): Promise<void> {
  if (!(await saveSettings())) {
    return;
  }
  testingFeishu.value = true;
  try {
    const result = await sendFeishuApprovalCardTestApi();
    if (result.triggered) {
      ElMessage.success(result.message);
    } else {
      ElMessage.warning(result.message);
    }
  } finally {
    testingFeishu.value = false;
  }
}

async function testFeishuSupplierQuoteCard(): Promise<void> {
  if (!(await saveSettings())) {
    return;
  }
  const resultInput = await ElMessageBox.prompt(
    "填写测试接收目标，格式为 email:邮箱、chat_id:群ID、open_id:用户OpenID、user_id:用户ID 或 union_id:UnionID。",
    "供应商报价通知样卡",
    {
      confirmButtonText: "发送",
      cancelButtonText: "取消",
      inputPlaceholder: "例如 email:name@example.com 或 chat_id:oc_xxx",
      inputPattern: /^(open_id|user_id|union_id|email|chat_id):\S+$/i,
      inputErrorMessage: "格式应为 email:xxx、chat_id:xxx、open_id:xxx、user_id:xxx 或 union_id:xxx"
    }
  ).catch(() => null);
  if (!resultInput) {
    return;
  }
  const [receiveIdType, ...idParts] = String(resultInput.value || "").split(":");
  const receiveId = idParts.join(":").trim();
  testingSupplierFeishu.value = true;
  try {
    const result = await sendFeishuSupplierQuoteCardTestApi({
      receiveIdType: receiveIdType.trim(),
      receiveId
    });
    if (result.triggered) {
      ElMessage.success(result.message);
    } else {
      ElMessage.warning(result.message);
    }
  } finally {
    testingSupplierFeishu.value = false;
  }
}

async function loadCardLogs(): Promise<void> {
  cardLogLoading.value = true;
  try {
    cardLogPage.value = await getFeishuApprovalCardNotificationsApi({
      ...cardLogQuery.value,
      status: cardLogQuery.value.status || undefined,
      keyword: String(cardLogQuery.value.keyword || "").trim() || undefined
    });
  } finally {
    cardLogLoading.value = false;
  }
}

async function loadSupplierCardLogs(): Promise<void> {
  supplierCardLogLoading.value = true;
  try {
    supplierCardLogPage.value = await getSupplierFeishuQuoteNoticesApi({
      ...supplierCardLogQuery.value,
      noticeType: supplierCardLogQuery.value.noticeType || undefined,
      status: supplierCardLogQuery.value.status || undefined,
      keyword: String(supplierCardLogQuery.value.keyword || "").trim() || undefined
    });
  } finally {
    supplierCardLogLoading.value = false;
  }
}

function searchCardLogs(): void {
  cardLogQuery.value.pageNo = 1;
  loadCardLogs();
}

function searchSupplierCardLogs(): void {
  supplierCardLogQuery.value.pageNo = 1;
  loadSupplierCardLogs();
}

function resetCardLogQuery(): void {
  cardLogQuery.value = {
    pageNo: 1,
    pageSize: 10,
    status: "",
    keyword: ""
  };
  loadCardLogs();
}

function resetSupplierCardLogQuery(): void {
  supplierCardLogQuery.value = {
    pageNo: 1,
    pageSize: 10,
    noticeType: "",
    status: "",
    keyword: ""
  };
  loadSupplierCardLogs();
}

function changeCardLogPage(pageNo: number): void {
  cardLogQuery.value.pageNo = pageNo;
  loadCardLogs();
}

function changeSupplierCardLogPage(pageNo: number): void {
  supplierCardLogQuery.value.pageNo = pageNo;
  loadSupplierCardLogs();
}

function changeCardLogPageSize(pageSize: number): void {
  cardLogQuery.value.pageNo = 1;
  cardLogQuery.value.pageSize = pageSize;
  loadCardLogs();
}

function changeSupplierCardLogPageSize(pageSize: number): void {
  supplierCardLogQuery.value.pageNo = 1;
  supplierCardLogQuery.value.pageSize = pageSize;
  loadSupplierCardLogs();
}

function cardStatusLabel(status: string): string {
  return cardStatusOptions.find((item) => item.value === status)?.label || status || "-";
}

function cardStatusTagType(status: string): string {
  return cardStatusOptions.find((item) => item.value === status)?.type || "info";
}

function supplierNoticeStatusLabel(status: string): string {
  return supplierNoticeStatusOptions.find((item) => item.value === status)?.label || status || "-";
}

function supplierNoticeStatusTagType(status: string): string {
  return supplierNoticeStatusOptions.find((item) => item.value === status)?.type || "info";
}

function supplierNoticeTypeLabel(noticeType: string): string {
  return supplierNoticeTypeOptions.find((item) => item.value === noticeType)?.label || noticeType || "-";
}

function isFinalCardStatus(status: string): boolean {
  return ["APPROVED", "RETURNED", "CLOSED"].includes(status);
}

async function resendCardLog(row: FeishuApprovalCardNotificationVO): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `确认重新发送 ${row.requestNo || `任务 ${row.approvalTaskId}`} 的飞书审批卡片？`,
      "重新发送卡片",
      {
        type: "warning",
        confirmButtonText: "重新发送",
        cancelButtonText: "取消"
      }
    );
  } catch {
    return;
  }
  resendingCardLogId.value = row.id;
  try {
    const result = await resendFeishuApprovalCardNotificationApi(row.id);
    if (result.triggered) {
      ElMessage.success(result.message);
    } else {
      ElMessage.warning(result.message);
    }
    await loadCardLogs();
  } finally {
    resendingCardLogId.value = null;
  }
}

async function resendSupplierNotice(row: SupplierFeishuQuoteNoticeVO): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `确认重新发送 ${row.requestNo || `申请 ${row.purchaseRequestId}`} 给 ${row.supplierName || row.receiveId || "接收人"}？`,
      "重新发送供应商报价卡片",
      {
        type: "warning",
        confirmButtonText: "重新发送",
        cancelButtonText: "取消"
      }
    );
  } catch {
    return;
  }
  resendingSupplierNoticeId.value = row.id;
  try {
    const result = await resendSupplierFeishuQuoteNoticeApi(row.id);
    if (result.triggered) {
      ElMessage.success(result.message);
    } else {
      ElMessage.warning(result.message);
    }
    await loadSupplierCardLogs();
  } finally {
    resendingSupplierNoticeId.value = null;
  }
}

async function saveSettings(): Promise<boolean> {
  if (showRuleSettings.value && !syncLaborRulesToSetting()) {
    return false;
  }
  const invalidLimit = showRuleSettings.value
    ? departmentLimits.value.find((item) => !String(item.deptName || "").trim())
    : null;
  if (invalidLimit) {
    ElMessage.warning("请填写部门名称");
    return false;
  }

  saving.value = true;
  try {
    await updateProcurementSettingsApi({
      settings: settings.value.map((item) => ({
        ...item,
        settingValue: String(item.settingValue ?? "")
      })),
      departmentLimits: departmentLimits.value.map((item) => ({
        ...item,
        deptCode: String(item.deptCode || "").trim(),
        deptName: String(item.deptName || "").trim(),
        singleRequestLimit: Number(item.singleRequestLimit || 0),
        monthlyLimit: Number(item.monthlyLimit || 0),
        status: item.status ?? 1,
        remark: String(item.remark || "")
      }))
    });
    ElMessage.success("配置已保存");
    await loadSettings();
    return true;
  } finally {
    saving.value = false;
  }
}
</script>

<style scoped>
.vnet-page {
  display: grid;
  gap: 16px;
}

:deep(.vnet-panel + .vnet-panel) {
  margin-top: 16px;
}

:deep(.el-input-number) {
  width: 100%;
}

.labor-rule-alert {
  margin-bottom: 12px;
}

.card-log-toolbar {
  display: grid;
  grid-template-columns: minmax(260px, 1fr) 180px auto auto;
  gap: 10px;
  margin-bottom: 12px;
}

.card-log-main {
  font-weight: 600;
  color: #14213d;
}

.card-log-sub {
  margin-top: 4px;
  color: #6b7a90;
  font-size: 12px;
  line-height: 1.35;
  word-break: break-all;
}

.card-log-pagination {
  justify-content: flex-end;
  margin-top: 12px;
}

@media (max-width: 900px) {
  .card-log-toolbar {
    grid-template-columns: 1fr;
  }
}
</style>
