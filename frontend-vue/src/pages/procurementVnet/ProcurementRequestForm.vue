<template>
  <VnetPage :title="isEditMode ? '编辑采购申请' : '新建采购申请'">
    <template #actions>
      <el-button @click="goBack">返回</el-button>
    </template>

    <div class="request-workspace">
      <main class="request-editor">
    <VnetPanel title="申请信息">
      <el-form ref="formRef" class="request-info-form" label-position="top" :model="formModel" :rules="formRules" v-loading="loading">
        <div class="request-info-grid">
          <el-form-item class="field-span-3" label="申请单号">
            <el-input v-model="formModel.reqNo" disabled :placeholder="requestNoLoading ? '正在分配单号' : ''" />
          </el-form-item>

          <el-form-item class="field-span-5" label="申请标题" prop="reqTitle">
            <el-input v-model="formModel.reqTitle" placeholder="例如：M6 机房办公用品采购" />
          </el-form-item>

          <el-form-item class="field-span-4" label="采购方式" prop="urgencyLevel">
            <div class="urgency-control">
              <span :class="['urgency-label', !emergencyPurchase && 'is-active']">普通采购</span>
              <el-switch v-model="emergencyPurchase" aria-label="切换普通采购或紧急采购" />
              <span :class="['urgency-label', emergencyPurchase && 'is-active']">紧急采购</span>
            </div>
          </el-form-item>

          <el-form-item class="field-span-5" label="申请部门/机房楼栋" prop="deptName">
            <el-select
              v-model="selectedRouteId"
              filterable
              placeholder="请选择部门/机房楼栋"
              style="width: 100%"
              @change="handleDepartmentChange"
            >
              <el-option
                v-for="option in departmentOptions"
                :key="option.id"
                :label="departmentOptionLabel(option)"
                :value="option.id"
              />
            </el-select>
          </el-form-item>

          <el-form-item class="field-span-3" label="收货城市">
            <el-input class="readonly-input" :model-value="selectedReceivingCity || '-'" disabled />
          </el-form-item>

          <el-form-item class="field-span-4" label="预算编码">
            <el-input class="readonly-input" v-model="formModel.budgetCode" disabled placeholder="选择部门后自动带出成本中心" />
          </el-form-item>

          <el-form-item class="field-span-3" label="需求日期">
            <el-date-picker v-model="formModel.needDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
          </el-form-item>

          <el-form-item class="field-span-3" label="申请人">
            <el-select
              v-if="isSystemAdmin"
              v-model="formModel.applicantUserId"
              filterable
              placeholder="选择代办申请人"
              style="width: 100%"
              @change="handleApplicantChange"
            >
              <el-option
                v-for="user in userOptions"
                :key="user.id"
                :label="userOptionLabel(user)"
                :value="user.id"
              />
            </el-select>
            <el-input v-else class="readonly-input" :model-value="applicantDisplay" disabled />
          </el-form-item>

          <el-form-item v-if="isSystemAdmin" class="field-span-3" label="代选原因">
            <el-input v-model="formModel.actingReason" clearable placeholder="替用户选择部门时填写" />
          </el-form-item>

          <el-form-item class="field-span-3" label="币种" prop="currency">
            <el-select v-model="formModel.currency">
              <el-option label="人民币 CNY" value="CNY" />
              <el-option label="美元 USD" value="USD" />
            </el-select>
          </el-form-item>

          <el-form-item class="field-span-3" label="中标规则">
            <el-input class="readonly-input" :model-value="automaticAwardStrategyLabel" disabled />
          </el-form-item>
        </div>
      </el-form>
    </VnetPanel>

    <VnetPanel title="采购明细">
      <template #actions>
        <el-button :loading="templateDownloading" @click="downloadImportTemplate">下载模板</el-button>
        <el-button :loading="itemImportLoading" @click="triggerItemImport">批量上传</el-button>
        <el-button type="primary" plain @click="openAddLine">新增明细</el-button>
        <input ref="itemImportInputRef" class="hidden-file-input" type="file" accept=".xlsx,.xls" @change="handleItemImportFileChange" />
      </template>

      <el-table :data="items" stripe empty-text="暂无采购明细">
        <el-table-column prop="lineNo" label="行号" width="70" />
        <el-table-column prop="itemCode" label="物料编码" min-width="140" />
        <el-table-column prop="itemName" label="商品名称" min-width="160" />
        <el-table-column prop="reqType" label="采购类型" min-width="190" />
        <el-table-column prop="specModel" label="规格型号" min-width="130" />
        <el-table-column prop="qty" label="数量" width="90" />
        <el-table-column prop="unit" label="单位" width="80" />
        <el-table-column label="单价" width="120">
          <template #default="{ row }">{{ formatMoney(row.unitPrice) }}</template>
        </el-table-column>
        <el-table-column label="金额" width="130">
          <template #default="{ row }">{{ formatMoney(row.qty * row.unitPrice) }}</template>
        </el-table-column>
        <el-table-column prop="needDate" label="需求日期" width="120" />
        <el-table-column prop="purpose" label="采购原因" min-width="190" />
        <el-table-column label="AI预审意见" min-width="250">
          <template #default="{ $index }">
            <div v-if="precheckItemOpinionAt($index)" class="item-precheck-opinion">
              <div class="item-precheck-head">
                <el-tag :type="precheckDecisionTagType(precheckItemOpinionAt($index)?.decisionSuggestion)" effect="light" size="small">
                  {{ precheckDecisionLabel(precheckItemOpinionAt($index)?.decisionSuggestion) }}
                </el-tag>
                <el-button
                  v-if="precheckItemOpinionAt($index)?.decisionSuggestion === 'SUPPLEMENT'"
                  link
                  type="primary"
                  :disabled="isPrecheckItemSuggestionAdopted($index)"
                  @click="adoptPrecheckItem($index)"
                >
                  {{ isPrecheckItemSuggestionAdopted($index) ? "已采用" : "采用填写框架" }}
                </el-button>
              </div>
              <p>{{ stripPrecheckDecisionPrefix(precheckItemOpinionAt($index)?.opinion) }}</p>
            </div>
            <span v-else class="item-precheck-empty">{{ precheckItemEmptyText }}</span>
          </template>
        </el-table-column>
        <el-table-column label="电商链接" min-width="120">
          <template #default="{ row }">
            <el-link v-if="normalizeUrl(row.ecommerceUrl)" :href="normalizeUrl(row.ecommerceUrl)" target="_blank" type="primary">打开</el-link>
            <span v-else>{{ row.ecommerceUrl ? "链接无效" : "-" }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ $index }">
            <div class="row-actions">
              <el-button link type="primary" @click="openEditLine($index)">编辑</el-button>
              <el-button link type="danger" @click="removeLine($index)">删除</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <section v-if="precheckOpinion" class="precheck-summary">
        <div class="precheck-summary-head">
          <div>
            <strong>AI预审结果</strong>
            <el-tag :type="precheckPassed ? 'success' : 'warning'" effect="light" size="small">
              {{ precheckPassed ? "预审通过" : "存在待处理" }}
            </el-tag>
          </div>
          <span>{{ precheckCompletedAt }}</span>
        </div>
        <el-alert
          v-if="precheckOutdated"
          type="warning"
          :closable="false"
          title="申请内容已变更，请重新预审后查看逐条意见"
        />
        <p>{{ precheckOpinion }}</p>
        <div class="precheck-summary-actions">
          <el-button
            v-if="actionablePrecheckItemOpinions.length > 0"
            type="primary"
            plain
            :disabled="precheckOutdated || allPrecheckItemSuggestionsAdopted"
            @click="adoptAllPrecheckItems"
          >
            {{ allPrecheckItemSuggestionsAdopted ? "已采用全部" : "一键采用全部填写框架" }}
          </el-button>
          <el-button :loading="precheckLoading" @click="runAiPrecheck">重新预审</el-button>
        </div>
      </section>

      <div class="summary-line">总金额：{{ formatMoney(totalAmount) }}</div>
      <div class="form-actions">
        <el-button @click="goBack">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="saveRequest(false)">保存草稿</el-button>
        <el-button :loading="precheckLoading" @click="runAiPrecheck">
          <el-icon><MagicStick /></el-icon>
          AI预审
        </el-button>
        <el-button type="warning" :loading="submitLoading" @click="saveRequest(true)">保存并提交</el-button>
      </div>
    </VnetPanel>
      </main>

    </div>

    <el-dialog v-model="itemDialogVisible" :title="editingItemIndex === null ? '新增采购明细' : '编辑采购明细'" width="620px">
      <el-form label-position="top" :model="itemForm">
        <el-row :gutter="14">
          <el-col :span="12">
            <el-form-item label="物料编码">
              <el-input v-model="itemForm.itemCode" disabled />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="商品名称" required>
              <el-input v-model="itemForm.itemName" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="采购类型" required>
              <el-select v-model="itemForm.reqType" style="width: 100%">
                <el-option v-for="option in purchaseTypeOptions" :key="option" :label="option" :value="option" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="规格型号">
              <el-input v-model="itemForm.specModel" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="单位" required>
              <ProcurementUnitSelect v-model="itemForm.unit" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="数量" required>
              <el-input-number v-model="itemForm.qty" :min="1" :precision="0" :step="1" step-strictly controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="单价" required>
              <el-input-number v-model="itemForm.unitPrice" :min="0.01" :precision="2" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="需求日期">
              <el-date-picker v-model="itemForm.needDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="电商链接">
              <el-input v-model="itemForm.ecommerceUrl" placeholder="https://..." />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item required>
              <template #label>
                <div class="purpose-field-label">
                  <span>采购原因</span>
                  <el-button
                    link
                    type="primary"
                    :loading="purposeOptimizing"
                    @click="optimizeItemPurpose"
                  >
                    AI优化
                  </el-button>
                </div>
              </template>
              <el-input v-model="itemForm.purpose" type="textarea" :rows="3" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="itemDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveLine">{{ editingItemIndex === null ? "确定" : "保存" }}</el-button>
      </template>
    </el-dialog>
  </VnetPage>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from "vue";
import type { FormInstance, FormRules } from "element-plus";
import { ElMessage, ElMessageBox } from "element-plus";
import { MagicStick } from "@element-plus/icons-vue";
import { useRoute, useRouter } from "vue-router";
import VnetPage from "../../components/vnet/VnetPage.vue";
import VnetPanel from "../../components/vnet/VnetPanel.vue";
import ProcurementUnitSelect from "../../components/procurement/ProcurementUnitSelect.vue";
import {
  createWorkflowRequestApi,
  exportPurchaseItemTemplateApi,
  getNextWorkflowRequestNoApi,
  getProcurementDepartmentOptionsApi,
  getWorkflowDetailApi,
  importPurchaseItemPreviewApi,
  optimizeWorkflowPurposeApi,
  precheckWorkflowRequestApi,
  previewSubmitWorkflowRequestApi,
  submitWorkflowRequestApi,
  updateWorkflowRequestApi
} from "../../api/workflow";
import type { WorkflowPrecheckItemOpinion } from "../../api/workflow";
import { getUserListApi } from "../../api/user";
import { useAuthStore } from "../../stores/auth";
import type { PurchaseRequestForm, PurchaseRequestItem } from "../../types/purchase";
import type { ProcurementApprovalRouteVO } from "../../types/platform";
import type { UserVO } from "../../types/system";
import { ecommerceUrlHref, normalizeEcommerceUrlInput } from "../../utils/ecommerce-url";
import { confirmSubmitNextApprovalDialog } from "../../utils/workflow-submit";

const route = useRoute();
const router = useRouter();
const authStore = useAuthStore();

const loading = ref(false);
const submitLoading = ref(false);
const requestNoLoading = ref(false);
const templateDownloading = ref(false);
const itemImportLoading = ref(false);
const precheckLoading = ref(false);
const purposeOptimizing = ref(false);
const precheckOpinion = ref("");
const precheckItemOpinions = ref<WorkflowPrecheckItemOpinion[]>([]);
const precheckCompletedAt = ref("");
const precheckFingerprint = ref("");
const adoptedPrecheckLineNos = ref<number[]>([]);
const formRef = ref<FormInstance>();
const itemImportInputRef = ref<HTMLInputElement>();
const itemDialogVisible = ref(false);
const editingItemIndex = ref<number | null>(null);
const departmentOptions = ref<ProcurementApprovalRouteVO[]>([]);
const selectedRouteId = ref<number | null>(null);
const userOptions = ref<UserVO[]>([]);
let requestNoLoadSeq = 0;
const purchaseTypeOptions = ["工程项目", "实物资产/无形资产/长摊等", "服务/费用/低值易耗等"];
const defaultPurchaseType = "服务/费用/低值易耗等";

const isEditMode = computed(() => Boolean(route.params.id));
const isSystemAdmin = computed(() => authStore.hasAnyRole(["SUPER_ADMIN", "SYS_ADMIN", "ADMIN"]));
const totalAmount = computed(() => Number(items.value.reduce((sum, item) => sum + Number(item.qty || 0) * Number(item.unitPrice || 0), 0).toFixed(2)));
const automaticAwardStrategy = computed(() => (totalAmount.value >= 10000 ? "ITEM_LOWEST" : "TOTAL_LOWEST"));
const automaticAwardStrategyLabel = computed(() => {
  if (!emergencyPurchase.value) {
    return "月度汇总后自动判定（≥1万元单项最低，<1万元总价最低）";
  }
  const label = automaticAwardStrategy.value === "ITEM_LOWEST" ? "单项最低中标" : "总价最低中标";
  return `${label}（紧急采购按本单金额判断）`;
});
const precheckPassed = computed(() => precheckOpinion.value.startsWith("建议通过："));
const actionablePrecheckItemOpinions = computed(() => precheckItemOpinions.value
  .filter((item) => item.decisionSuggestion === "SUPPLEMENT"));
const allPrecheckItemSuggestionsAdopted = computed(() => {
  return actionablePrecheckItemOpinions.value.length > 0
    && actionablePrecheckItemOpinions.value.every((item) => adoptedPrecheckLineNos.value.includes(item.lineNo));
});
const precheckOutdated = computed(() => Boolean(
  precheckFingerprint.value && precheckFingerprint.value !== currentPrecheckFingerprint()
));
const precheckItemEmptyText = computed(() => {
  if (!precheckOpinion.value) {
    return "点击下方 AI预审生成";
  }
  return precheckOutdated.value ? "内容已变更，请重新预审" : "本条暂无预审意见";
});
const emergencyPurchase = computed({
  get: () => Number(formModel.urgencyLevel || 1) >= 2,
  set: (value: boolean) => {
    formModel.urgencyLevel = value ? 2 : 1;
  }
});

interface HeaderForm {
  reqNo: string;
  reqTitle: string;
  reqType: string;
  applicantUserId: number | null;
  purchaserUserId: number | null;
  regionId: number | null;
  deptCode: string;
  deptName: string;
  budgetCode: string;
  actingReason: string;
  currency: string;
  urgencyLevel: number;
  needDate: string;
  awardStrategy: string;
  remark: string;
  copyUserIds: number[];
}

interface ValidImportItemRow {
  itemCode: string;
  itemName: string;
  reqType: string;
  specModel: string;
  unit: string;
  qty: number;
  unitPrice: number;
  needDate: string;
  purpose: string;
  ecommerceUrl: string;
}

const createDefaultHeader = (): HeaderForm => ({
  reqNo: "",
  reqTitle: "",
  reqType: defaultPurchaseType,
  applicantUserId: authStore.user?.userId ?? 0,
  purchaserUserId: authStore.user?.userId ?? 0,
  regionId: authStore.user?.regionId ?? 1,
  deptCode: authStore.user?.deptCode || "",
  deptName: authStore.user?.deptName || "",
  budgetCode: "",
  actingReason: "",
  currency: "CNY",
  urgencyLevel: 1,
  needDate: "",
  awardStrategy: "TOTAL_LOWEST",
  remark: "",
  copyUserIds: []
});

const createEmptyItem = (): PurchaseRequestItem => ({
  lineNo: items.value.length + 1,
  itemCode: generateMaterialCode(),
  itemName: "",
  reqType: defaultPurchaseType,
  specModel: "",
  itemBrand: "",
  unit: "件",
  qty: 1,
  unitPrice: 0,
  taxRate: 0,
  needDate: formModel.needDate,
  purpose: "",
  ecommerceUrl: "",
  status: "ACTIVE",
  remark: ""
});

const formModel = reactive<HeaderForm>(createDefaultHeader());
const items = ref<PurchaseRequestItem[]>([]);
const selectedApplicant = computed(() => userOptions.value.find((user) => user.id === formModel.applicantUserId) || null);
const applicantDisplay = computed(() => selectedApplicant.value?.realName || authStore.realName || authStore.username || "当前登录账号");
const selectedDepartmentRoute = computed(() => {
  return departmentOptions.value.find((option) => option.id === selectedRouteId.value) || matchDepartmentRoute(formModel);
});
const selectedReceivingCity = computed(() => selectedDepartmentRoute.value?.city || "");
const itemForm = reactive<PurchaseRequestItem>({
  lineNo: 1,
  itemCode: generateMaterialCode(),
  itemName: "",
  reqType: defaultPurchaseType,
  specModel: "",
  itemBrand: "",
  unit: "件",
  qty: 1,
  unitPrice: 0,
  taxRate: 0,
  needDate: "",
  purpose: "",
  ecommerceUrl: "",
  status: "ACTIVE",
  remark: ""
});

const formRules: FormRules<HeaderForm> = {
  reqTitle: [{ required: true, message: "请输入申请标题", trigger: "blur" }],
  deptName: [{ required: true, message: "请选择申请部门/机房楼栋", trigger: "change" }],
  currency: [{ required: true, message: "请选择币种", trigger: "change" }],
  urgencyLevel: [{ required: true, message: "请选择紧急程度", trigger: "change" }]
};

watch(
  () => formModel.urgencyLevel,
  async () => {
    if (!isEditMode.value) {
      await allocateRequestNo();
    }
  }
);

function openAddLine(): void {
  editingItemIndex.value = null;
  Object.assign(itemForm, createEmptyItem());
  itemDialogVisible.value = true;
}

function openEditLine(index: number): void {
  const item = items.value[index];
  if (!item) {
    return;
  }
  editingItemIndex.value = index;
  Object.assign(itemForm, {
    ...item,
    itemCode: item.itemCode || generateMaterialCode(),
    itemName: item.itemName || "",
    reqType: normalizePurchaseType(item.reqType) || defaultPurchaseType,
    specModel: item.specModel || "",
    itemBrand: item.itemBrand || "",
    unit: item.unit || "件",
    qty: Number(item.qty || 1),
    unitPrice: Number(item.unitPrice || 0),
    taxRate: Number(item.taxRate ?? 0),
    needDate: item.needDate || formModel.needDate || "",
    purpose: item.purpose || "",
    ecommerceUrl: item.ecommerceUrl || "",
    status: item.status || "ACTIVE",
    remark: item.remark || ""
  });
  itemDialogVisible.value = true;
}

async function optimizeItemPurpose(): Promise<void> {
  const originalPurpose = String(itemForm.purpose || "").trim();
  if (!originalPurpose) {
    ElMessage.warning("请先填写采购原因，再使用AI优化");
    return;
  }
  purposeOptimizing.value = true;
  try {
    const result = await optimizeWorkflowPurposeApi(originalPurpose);
    const optimizedContent = String(result.optimizedContent || "").trim();
    if (!optimizedContent) {
      ElMessage.warning("未生成可用的优化内容，请稍后重试");
      return;
    }
    if (originalPurpose.includes(optimizedContent)) {
      ElMessage.info("当前采购原因已包含本次优化内容");
      return;
    }
    itemForm.purpose = `${originalPurpose}\n${optimizedContent}`;
    ElMessage.success(containsPurposePlaceholder(optimizedContent)
      ? "已按预审标准追加待补框架，请补全后保存"
      : "已按预审标准优化采购原因，请确认后保存");
  } finally {
    purposeOptimizing.value = false;
  }
}

function saveLine(): void {
  if (!itemForm.itemName.trim()) {
    ElMessage.warning("请填写商品名称");
    return;
  }
  const itemReqType = normalizePurchaseType(itemForm.reqType);
  if (!isValidPurchaseType(itemReqType)) {
    ElMessage.warning("请选择采购类型");
    return;
  }
  if (!itemForm.unit.trim()) {
    ElMessage.warning("请选择单位");
    return;
  }
  if (!itemForm.purpose.trim()) {
    ElMessage.warning("每行采购明细都要填写采购原因");
    return;
  }
  if (containsPurposePlaceholder(itemForm.purpose)) {
    ElMessage.warning("请先补全采购原因中的待填写内容");
    return;
  }
  if (!isPositiveInteger(itemForm.qty)) {
    ElMessage.warning("数量必须是大于 0 的整数");
    return;
  }
  if (!isPositiveMoney(itemForm.unitPrice)) {
    ElMessage.warning("单价必须大于 0");
    return;
  }
  const normalizedNeedDate = normalizeRequestDate(itemForm.needDate);
  if (normalizedNeedDate === null) {
    ElMessage.warning("需求日期格式应为 2026-07-10");
    return;
  }
  const ecommerceUrlResult = normalizeEcommerceUrlInput(itemForm.ecommerceUrl);
  if (ecommerceUrlResult.error) {
    ElMessage.warning(ecommerceUrlResult.error);
    return;
  }
  const qty = Number(itemForm.qty);
  const normalizedItem: PurchaseRequestItem = {
    ...itemForm,
    lineNo: editingItemIndex.value === null ? items.value.length + 1 : editingItemIndex.value + 1,
    itemCode: itemForm.itemCode || generateMaterialCode(),
    itemName: itemForm.itemName.trim(),
    reqType: itemReqType,
    specModel: itemForm.specModel.trim(),
    qty,
    unit: itemForm.unit.trim(),
    needDate: normalizedNeedDate,
    purpose: itemForm.purpose.trim(),
    ecommerceUrl: ecommerceUrlResult.value,
    lineAmount: Number((qty * Number(itemForm.unitPrice)).toFixed(2))
  };
  if (editingItemIndex.value === null) {
    items.value.push(normalizedItem);
  } else {
    items.value[editingItemIndex.value] = normalizedItem;
    items.value = items.value.map((item, itemIndex) => ({ ...item, lineNo: itemIndex + 1 }));
  }
  editingItemIndex.value = null;
  itemDialogVisible.value = false;
  if (ecommerceUrlResult.changed) {
    ElMessage.success(ecommerceUrlResult.shortLink ? "已提取短链接，保存后可直接打开" : "已自动整理为标准商品链接");
  }
}

async function removeLine(index: number): Promise<void> {
  if (index < 0 || index >= items.value.length) {
    return;
  }
  if (items.value.length === 1) {
    try {
      await ElMessageBox.confirm("删除后采购明细将为空，需要重新新增明细后才能保存或提交。确认删除吗？", "删除明细", {
        type: "warning",
        confirmButtonText: "删除",
        cancelButtonText: "取消"
      });
    } catch {
      return;
    }
  }
  items.value.splice(index, 1);
  items.value = items.value.map((item, itemIndex) => ({ ...item, lineNo: itemIndex + 1 }));
  if (editingItemIndex.value === index) {
    editingItemIndex.value = null;
    itemDialogVisible.value = false;
  }
}

async function downloadImportTemplate(): Promise<void> {
  templateDownloading.value = true;
  try {
    const blob = await exportPurchaseItemTemplateApi();
    downloadBlob(blob, "采购明细批量导入模板.xlsx");
  } finally {
    templateDownloading.value = false;
  }
}

function triggerItemImport(): void {
  itemImportInputRef.value?.click();
}

async function handleItemImportFileChange(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  input.value = "";
  if (!file) {
    return;
  }
  if (!/\.(xlsx|xls)$/i.test(file.name)) {
    ElMessage.warning("请上传 Excel 文件");
    return;
  }
  itemImportLoading.value = true;
  try {
    const rows = await importPurchaseItemPreviewApi(file);
    applyImportedItems(rows);
  } finally {
    itemImportLoading.value = false;
  }
}

function applyImportedItems(rows: string[][]): void {
  const nonEmptyRows = rows
    .map((cells, index) => ({ cells, rowNumber: index + 1 }))
    .filter((row) => row.cells.some((cell) => String(cell || "").trim()));
  if (nonEmptyRows.length === 0) {
    ElMessage.warning("未读取到可导入的采购明细");
    return;
  }

  const header = nonEmptyRows[0].cells;
  const hasHeader = header.some((part) => /商品名称|物料名称|采购类型|规格型号|采购原因|数量|单价/.test(part));
  const indexMap = hasHeader ? buildImportHeaderIndex(header) : null;
  if (indexMap) {
    const missingHeaders = ["商品名称", "采购类型", "单位", "数量", "采购原因"].filter((name) => indexMap[normalizeImportHeaderName(name)] === undefined);
    if (missingHeaders.length > 0) {
      void ElMessageBox.alert(`导入模板缺少必填列：${missingHeaders.join("、")}。请先下载最新模板后再填写。`, "导入失败", {
        type: "error",
        confirmButtonText: "知道了"
      });
      return;
    }
  }

  const dataRows = hasHeader ? nonEmptyRows.slice(1) : nonEmptyRows;
  const errors: string[] = [];
  const validRows: ValidImportItemRow[] = [];
  for (const row of dataRows) {
    const parts = row.cells;
    const itemName = readImportValue(parts, indexMap, "商品名称", 0) || readImportValue(parts, indexMap, "物料名称", 0);
    const rawReqType = readImportValue(parts, indexMap, "采购类型", -1);
    const reqType = normalizePurchaseType(rawReqType || (hasHeader ? "" : defaultPurchaseType));
    const specModel = readImportValue(parts, indexMap, "规格型号", hasHeader ? -1 : 1);
    const unit = readImportValue(parts, indexMap, "单位", hasHeader ? -1 : 2);
    const qty = parseImportNumber(readImportValue(parts, indexMap, "数量", hasHeader ? -1 : 3));
    const unitPrice = parseImportNumber(readImportValue(parts, indexMap, "单价", hasHeader ? -1 : 4));
    const rawNeedDate = readImportValue(parts, indexMap, "需求日期", hasHeader ? -1 : 5) || formModel.needDate || "";
    const needDate = normalizeRequestDate(rawNeedDate);
    const purpose = readImportValue(parts, indexMap, "采购原因", hasHeader ? -1 : 6);
    const rawEcommerceUrl = readImportValue(parts, indexMap, "电商链接", hasHeader ? -1 : 7);
    const ecommerceUrlResult = normalizeEcommerceUrlInput(rawEcommerceUrl);
    const itemCode = readImportValue(parts, indexMap, "物料编码", hasHeader ? -1 : 8);

    if (!itemName) {
      errors.push(`第 ${row.rowNumber} 行缺少商品名称`);
    }
    if (hasHeader && !rawReqType) {
      errors.push(`第 ${row.rowNumber} 行缺少采购类型`);
    } else if (!isValidPurchaseType(reqType)) {
      errors.push(`第 ${row.rowNumber} 行采购类型只能是工程项目、实物资产/无形资产/长摊等、服务/费用/低值易耗等`);
    }
    if (!unit) {
      errors.push(`第 ${row.rowNumber} 行缺少单位`);
    }
    if (!purpose) {
      errors.push(`第 ${row.rowNumber} 行缺少采购原因`);
    }
    if (!isPositiveInteger(qty)) {
      errors.push(`第 ${row.rowNumber} 行数量必须是大于 0 的整数`);
      continue;
    }
    if (!isPositiveMoney(unitPrice)) {
      errors.push(`第 ${row.rowNumber} 行单价必须大于 0`);
    }
    if (rawNeedDate && needDate === null) {
      errors.push(`第 ${row.rowNumber} 行需求日期格式应为 2026-07-10，当前为 ${rawNeedDate}`);
    }
    if (ecommerceUrlResult.error) {
      errors.push(`第 ${row.rowNumber} 行电商链接：${ecommerceUrlResult.error}`);
    }
    if (errors.length > 0 && validRows.length + errors.length >= 30) {
      continue;
    }
    if (itemName && isValidPurchaseType(reqType) && unit && purpose && isPositiveInteger(qty) && isPositiveMoney(unitPrice)
      && needDate !== null && !ecommerceUrlResult.error) {
      validRows.push({
        itemCode,
        itemName,
        reqType,
        specModel,
        unit,
        qty,
        unitPrice,
        needDate,
        purpose,
        ecommerceUrl: ecommerceUrlResult.value
      });
    }
  }

  if (errors.length > 0) {
    const message = errors.slice(0, 12).join("\n") + (errors.length > 12 ? `\n还有 ${errors.length - 12} 条错误未展示` : "");
    void ElMessageBox.alert(message, "导入失败", {
      type: "error",
      confirmButtonText: "知道了"
    });
    return;
  }
  if (validRows.length === 0) {
    ElMessage.warning("未读取到可导入的采购明细");
    return;
  }

  const importedItems = validRows.map((row, index) => {
    const lineNo = items.value.length + index + 1;
    return {
      lineNo,
      itemCode: row.itemCode || generateMaterialCode(),
      itemName: row.itemName,
      reqType: row.reqType,
      specModel: row.specModel,
      itemBrand: "",
      unit: row.unit,
      qty: row.qty,
      unitPrice: row.unitPrice,
      taxRate: 0,
      lineAmount: Number((row.qty * row.unitPrice).toFixed(2)),
      needDate: row.needDate,
      purpose: row.purpose,
      ecommerceUrl: row.ecommerceUrl,
      status: "ACTIVE",
      remark: ""
    };
  });
  items.value = [...items.value, ...importedItems];
  ElMessage.success(`已导入 ${importedItems.length} 条采购明细`);
}

function buildImportHeaderIndex(header: string[]): Record<string, number> {
  return header.reduce<Record<string, number>>((map, name, index) => {
    const normalized = normalizeImportHeaderName(name);
    map[normalized] = index;
    if (normalized === "物料名称") {
      map[normalizeImportHeaderName("商品名称")] = index;
    }
    return map;
  }, {});
}

function readImportValue(parts: string[], indexMap: Record<string, number> | null, header: string, fallbackIndex: number): string {
  const index = indexMap?.[normalizeImportHeaderName(header)] ?? fallbackIndex;
  return String(parts[index] || "").trim();
}

function normalizeImportHeaderName(value: string): string {
  return String(value || "").replace(/\s/g, "").trim();
}

function normalizePurchaseType(value: string | null | undefined): string {
  const text = String(value || "").trim();
  if (!text) {
    return "";
  }
  if (["资产", "实物资产", "无形资产", "长摊", "长期待摊", "实物资产/无形资产/长摊等"].includes(text)) {
    return "实物资产/无形资产/长摊等";
  }
  if (["低值易耗", "服务", "费用", "其他", "服务/费用/低值易耗等"].includes(text)) {
    return "服务/费用/低值易耗等";
  }
  if (text === "工程项目") {
    return "工程项目";
  }
  return text;
}

function isValidPurchaseType(value: string | null | undefined): boolean {
  return purchaseTypeOptions.includes(normalizePurchaseType(value));
}

function deriveRequestReqType(list: PurchaseRequestItem[]): string {
  const distinctTypes = Array.from(new Set(list.map((item) => normalizePurchaseType(item.reqType)).filter(isValidPurchaseType)));
  if (distinctTypes.length === 1) {
    return distinctTypes[0];
  }
  if (distinctTypes.length > 1) {
    return "混合采购";
  }
  return defaultPurchaseType;
}

function parseImportNumber(value: string): number {
  const normalized = String(value || "").replace(/[,\s￥¥]/g, "");
  if (!normalized) {
    return Number.NaN;
  }
  return Number(normalized);
}

function isPositiveInteger(value: unknown): boolean {
  const numericValue = Number(value);
  return Number.isInteger(numericValue) && numericValue > 0;
}

function isPositiveMoney(value: unknown): boolean {
  const numericValue = Number(value);
  return Number.isFinite(numericValue) && numericValue > 0;
}

function normalizeRequestDate(value: string | null | undefined): string | null {
  const text = String(value || "").trim();
  if (!text) {
    return "";
  }
  const fullYearMatch = text.match(/^(\d{4})[-/.](\d{1,2})[-/.](\d{1,2})$/);
  if (fullYearMatch) {
    return formatDateParts(Number(fullYearMatch[1]), Number(fullYearMatch[2]), Number(fullYearMatch[3]));
  }
  const shortYearMatch = text.match(/^(\d{1,2})[-/](\d{1,2})[-/](\d{2})$/);
  if (shortYearMatch) {
    return formatDateParts(2000 + Number(shortYearMatch[3]), Number(shortYearMatch[1]), Number(shortYearMatch[2]));
  }
  return null;
}

function formatDateParts(year: number, month: number, day: number): string | null {
  const date = new Date(year, month - 1, day);
  if (date.getFullYear() !== year || date.getMonth() !== month - 1 || date.getDate() !== day) {
    return null;
  }
  return `${year}-${String(month).padStart(2, "0")}-${String(day).padStart(2, "0")}`;
}

async function saveRequest(needSubmit: boolean): Promise<void> {
  if (!isEditMode.value && !formModel.reqNo) {
    await allocateRequestNo();
  }
  if (!await validateRequestInput()) {
    return;
  }
  const selectedDepartment = departmentOptions.value.find((option) => option.id === selectedRouteId.value);
  if (!selectedDepartment) {
    ElMessage.warning("请选择申请部门/机房楼栋");
    return;
  }
  applyDepartmentRoute(selectedDepartment);

  submitLoading.value = true;
  try {
    const payload = buildPayload();
    let requestId = Number(route.params.id);
    const wasEditMode = isEditMode.value;
    if (isEditMode.value) {
      await updateWorkflowRequestApi(requestId, payload);
    } else {
      requestId = await createWorkflowRequestApi(payload);
    }
    if (needSubmit) {
      const previewResult = await previewSubmitWorkflowRequestApi(requestId);
      const confirmed = await confirmSubmitNextApprovalDialog(previewResult);
      if (!confirmed) {
        ElMessage.info("已取消提交，申请已保存为草稿");
        if (!wasEditMode) {
          await router.replace(`/procurement/requests/${requestId}/edit`);
        }
        return;
      }
      await submitWorkflowRequestApi(requestId);
      ElMessage.success("采购申请已提交审批");
    } else {
      ElMessage.success(isEditMode.value ? "采购申请更新成功" : "采购申请创建成功");
    }
    goBack();
  } finally {
    submitLoading.value = false;
  }
}

async function validateRequestInput(): Promise<boolean> {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) {
    return false;
  }
  if (items.value.length === 0) {
    ElMessage.warning("请至少添加一条采购明细");
    return false;
  }
  if (items.value.some((item) => !item.itemName.trim() || !item.unit.trim() || !item.purpose.trim())) {
    ElMessage.warning("每行采购明细都要填写商品名称、单位和采购原因");
    return false;
  }

  if (items.value.some((item) => containsPurposePlaceholder(item.purpose))) {
    ElMessage.warning("请先补全采购原因中的待填写内容");
    return false;
  }

  if (items.value.some((item) => !isValidPurchaseType(item.reqType))) {
    ElMessage.warning("每行采购明细都要选择采购类型");
    return false;
  }

  if (items.value.some((item) => !isPositiveInteger(item.qty))) {
    ElMessage.warning("每行采购明细数量必须是大于 0 的整数");
    return false;
  }

  if (items.value.some((item) => !isPositiveMoney(item.unitPrice))) {
    ElMessage.warning("每行采购明细单价必须大于 0");
    return false;
  }

  const headerNeedDate = normalizeRequestDate(formModel.needDate);
  if (headerNeedDate === null) {
    ElMessage.warning("申请需求日期格式应为 2026-07-10");
    return false;
  }
  const invalidDateLine = items.value.find((item) => normalizeRequestDate(item.needDate) === null);
  if (invalidDateLine) {
    ElMessage.warning(`第 ${invalidDateLine.lineNo || "-"} 行需求日期格式应为 2026-07-10`);
    return false;
  }
  for (let index = 0; index < items.value.length; index++) {
    const ecommerceUrlResult = normalizeEcommerceUrlInput(items.value[index].ecommerceUrl);
    if (ecommerceUrlResult.error) {
      ElMessage.warning(`第 ${index + 1} 行电商链接：${ecommerceUrlResult.error}`);
      return false;
    }
    items.value[index].ecommerceUrl = ecommerceUrlResult.value;
  }
  return true;
}

async function runAiPrecheck(): Promise<void> {
  if (!await validateRequestInput()) {
    return;
  }
  const selectedDepartment = departmentOptions.value.find((option) => option.id === selectedRouteId.value);
  if (!selectedDepartment) {
    ElMessage.warning("请选择申请部门/机房楼栋");
    return;
  }
  applyDepartmentRoute(selectedDepartment);
  const payload = buildPayload();
  precheckLoading.value = true;
  try {
    const result = await precheckWorkflowRequestApi(payload);
    precheckOpinion.value = result.overallOpinion;
    precheckItemOpinions.value = result.itemOpinions || [];
    precheckCompletedAt.value = `预审时间 ${new Date().toLocaleString("zh-CN", { hour12: false })}`;
    precheckFingerprint.value = currentPrecheckFingerprint();
    adoptedPrecheckLineNos.value = [];
  } finally {
    precheckLoading.value = false;
  }
}

function precheckItemOpinionAt(index: number): WorkflowPrecheckItemOpinion | undefined {
  if (precheckOutdated.value) {
    return undefined;
  }
  return precheckItemOpinions.value.find((item) => item.lineNo === index + 1);
}

function precheckDecisionLabel(decision: WorkflowPrecheckItemOpinion["decisionSuggestion"] | undefined): string {
  if (decision === "APPROVE") {
    return "建议通过";
  }
  if (decision === "RETURN") {
    return "建议退回";
  }
  return "建议补充";
}

function precheckDecisionTagType(decision: WorkflowPrecheckItemOpinion["decisionSuggestion"] | undefined): "success" | "warning" | "danger" {
  if (decision === "APPROVE") {
    return "success";
  }
  if (decision === "RETURN") {
    return "danger";
  }
  return "warning";
}

function isPrecheckItemSuggestionAdopted(index: number): boolean {
  const itemOpinion = precheckItemOpinions.value.find((item) => item.lineNo === index + 1);
  return Boolean(itemOpinion && adoptedPrecheckLineNos.value.includes(itemOpinion.lineNo));
}

function stripPrecheckDecisionPrefix(opinion: string | undefined): string {
  return String(opinion || "").replace(/^建议(?:通过|补充|退回)[：:]\s*/, "");
}

function adoptPrecheckItem(index: number): void {
  const itemOpinion = precheckItemOpinionAt(index);
  const item = items.value[index];
  if (!itemOpinion || itemOpinion.decisionSuggestion !== "SUPPLEMENT" || !item) {
    return;
  }
  items.value[index] = {
    ...item,
    purpose: purposeCompletionTemplate(item.purpose, itemOpinion.missingFields, itemOpinion.opinion)
  };
  if (!adoptedPrecheckLineNos.value.includes(itemOpinion.lineNo)) {
    adoptedPrecheckLineNos.value = [...adoptedPrecheckLineNos.value, itemOpinion.lineNo];
  }
  precheckFingerprint.value = currentPrecheckFingerprint();
  ElMessage.success(`已为第 ${index + 1} 条回填填写框架，请补全待填写内容`);
}

function adoptAllPrecheckItems(): void {
  const targetLineNos = actionablePrecheckItemOpinions.value
    .map((item) => item.lineNo)
    .filter((lineNo) => !adoptedPrecheckLineNos.value.includes(lineNo));
  if (targetLineNos.length === 0) {
    return;
  }
  const targets = new Set(targetLineNos);
  items.value = items.value.map((item, index) => {
    const itemOpinion = precheckItemOpinions.value.find((opinion) => opinion.lineNo === index + 1);
    return targets.has(index + 1)
      ? { ...item, purpose: purposeCompletionTemplate(item.purpose, itemOpinion?.missingFields, itemOpinion?.opinion) }
      : item;
  });
  adoptedPrecheckLineNos.value = Array.from(new Set([...adoptedPrecheckLineNos.value, ...targetLineNos]));
  precheckFingerprint.value = currentPrecheckFingerprint();
  ElMessage.success(`已为 ${targetLineNos.length} 条明细回填填写框架，请补全待填写内容`);
}

function purposeCompletionTemplate(
  value: string,
  missingFields?: WorkflowPrecheckItemOpinion["missingFields"],
  opinion?: string
): string {
  const current = String(value || "").trim().replace(/[；;，,。\s]+$/, "");
  if (containsPurposePlaceholder(current)) {
    return current;
  }
  const fieldLabels = {
    USE_SCENE: "使用场景：【请补充】",
    CURRENT_IMPACT: "现状问题及影响：【请补充】",
    NECESSITY: "采购必要性：【请补充】",
    QUANTITY_BASIS: "数量或覆盖依据：【请补充】",
    URGENCY_BASIS: "紧急影响及时限：【请补充】"
  } as const;
  const requestedFields = new Set(missingFields || []);
  const reviewText = String(opinion || "");
  if (requestedFields.size === 0) {
    requestedFields.add("USE_SCENE");
    requestedFields.add("CURRENT_IMPACT");
    requestedFields.add("NECESSITY");
    if (/(数量|覆盖|批量|多人|多点|整批|成套|备用比例)/.test(reviewText)) {
      requestedFields.add("QUANTITY_BASIS");
    }
    if (/(紧急|急用|突发|时限|到货时间)/.test(reviewText)) {
      requestedFields.add("URGENCY_BASIS");
    }
  }
  const fields = Object.entries(fieldLabels)
    .filter(([code]) => requestedFields.has(code as keyof typeof fieldLabels))
    .map(([, label]) => label);
  return `${current}；${fields.join("；")}`;
}

function containsPurposePlaceholder(value: string | null | undefined): boolean {
  return /【请补充】/.test(String(value || ""));
}

function currentPrecheckFingerprint(): string {
  return JSON.stringify({
    header: formModel,
    selectedRouteId: selectedRouteId.value,
    items: items.value,
    awardStrategy: automaticAwardStrategy.value
  });
}

function buildPayload(): PurchaseRequestForm {
  const reqType = deriveRequestReqType(items.value);
  return {
    ...formModel,
    reqNo: formModel.reqNo,
    reqType,
    awardStrategy: automaticAwardStrategy.value,
    actingReason: isSystemAdmin.value ? formModel.actingReason.trim() : "",
    applicantUserId: formModel.applicantUserId || authStore.user?.userId || 0,
    purchaserUserId: formModel.purchaserUserId || authStore.user?.userId || 0,
    regionId: formModel.regionId || authStore.user?.regionId || 1,
    needDate: normalizeRequestDate(formModel.needDate) || "",
    totalAmount: totalAmount.value,
    reqReason: "",
    items: items.value.map((item, index) => ({
      ...item,
      lineNo: index + 1,
      itemCode: item.itemCode || generateMaterialCode(),
      reqType: normalizePurchaseType(item.reqType) || defaultPurchaseType,
      qty: Number(item.qty),
      unitPrice: Number(item.unitPrice),
      taxRate: Number(item.taxRate ?? 0),
      lineAmount: Number((Number(item.qty) * Number(item.unitPrice)).toFixed(2)),
      needDate: normalizeRequestDate(item.needDate) || "",
      purpose: item.purpose.trim(),
      ecommerceUrl: item.ecommerceUrl.trim(),
      status: "ACTIVE",
      remark: item.status === "REJECTED" ? "" : item.remark
    }))
  };
}

async function loadDetail(): Promise<void> {
  if (!isEditMode.value) {
    Object.assign(formModel, createDefaultHeader());
    items.value = [];
    await allocateRequestNo();
    return;
  }
  const id = Number(route.params.id);
  if (!id) {
    return;
  }
  loading.value = true;
  try {
    const detail = await getWorkflowDetailApi(id);
    Object.assign(formModel, {
      reqNo: detail.reqNo,
      reqTitle: detail.reqTitle,
      reqType: detail.reqType,
      applicantUserId: detail.applicantUserId,
      purchaserUserId: detail.purchaserUserId,
      regionId: detail.regionId,
      deptCode: detail.deptCode,
      deptName: detail.deptName,
      budgetCode: detail.budgetCode,
      actingReason: detail.actingReason || "",
      currency: detail.currency,
      urgencyLevel: detail.urgencyLevel,
      needDate: detail.needDate,
      awardStrategy: detail.awardStrategy || "TOTAL_LOWEST",
      remark: detail.remark,
      copyUserIds: detail.copyUserIds || []
    });
    const detailItems = detail.items || [];
    const hasRejectedItems = detailItems.some((item) => item.status === "REJECTED");
    const hasNonRejectedItems = detailItems.some((item) => item.status !== "REJECTED");
    const rejectedOnlyEdit = route.query.rejectedOnly === "1";
    const editableItems = rejectedOnlyEdit && hasRejectedItems && hasNonRejectedItems
      ? detailItems.filter((item) => item.status === "REJECTED")
      : detailItems;
    items.value = editableItems.map((item, index) => ({
      ...item,
      lineNo: index + 1,
      itemCode: item.itemCode || generateMaterialCode(),
      reqType: normalizePurchaseType(item.reqType || detail.reqType) || defaultPurchaseType
    }));
    selectedRouteId.value = matchDepartmentRoute(detail)?.id ?? null;
  } finally {
    loading.value = false;
  }
}

async function allocateRequestNo(): Promise<void> {
  const loadSeq = ++requestNoLoadSeq;
  requestNoLoading.value = true;
  try {
    const result = await getNextWorkflowRequestNoApi(formModel.urgencyLevel || 1);
    if (loadSeq === requestNoLoadSeq) {
      formModel.reqNo = result.reqNo;
    }
  } finally {
    if (loadSeq === requestNoLoadSeq) {
      requestNoLoading.value = false;
    }
  }
}

async function loadDepartmentOptions(): Promise<void> {
  departmentOptions.value = await getProcurementDepartmentOptionsApi();
  if (isEditMode.value) {
    const matchedRoute = matchDepartmentRoute(formModel);
    selectedRouteId.value = matchedRoute?.id ?? selectedRouteId.value;
    if (matchedRoute && !formModel.budgetCode) {
      formModel.budgetCode = matchedRoute.costCenterCode || matchedRoute.deptCode || "";
    }
    return;
  }
  const ownDepartmentRoute = matchDepartmentRoute({
    deptCode: authStore.user?.deptCode || formModel.deptCode,
    deptName: authStore.user?.deptName || formModel.deptName
  });
  if (!selectedRouteId.value && ownDepartmentRoute) {
    applyDepartmentRoute(ownDepartmentRoute);
    return;
  }
  if (!selectedRouteId.value && departmentOptions.value.length === 1) {
    applyDepartmentRoute(departmentOptions.value[0]);
  }
}

async function loadUserOptions(): Promise<void> {
  if (!isSystemAdmin.value) {
    return;
  }
  userOptions.value = await getUserListApi({ pageNo: 1, pageSize: 500, status: 1, accountCategory: "EMPLOYEE" });
  const currentUser = authStore.user;
  if (currentUser && !userOptions.value.some((user) => user.id === currentUser.userId)) {
    userOptions.value.unshift({
      id: currentUser.userId,
      username: currentUser.username,
      realName: currentUser.realName || currentUser.username,
      employeeNo: currentUser.employeeNo,
      userType: currentUser.userType,
      supplierId: currentUser.supplierId,
      regionId: currentUser.regionId,
      deptCode: currentUser.deptCode || "",
      deptName: currentUser.deptName || "",
      mobile: "",
      email: "",
      status: 1,
      dataScopeType: currentUser.dataScopeType,
      mustChangePassword: currentUser.mustChangePassword || 0,
      remark: "",
      lastLoginAt: "",
      lastLoginIp: "",
      roleIds: [],
      createdAt: "",
      updatedAt: ""
    });
  }
}

function handleDepartmentChange(routeId: number): void {
  const selected = departmentOptions.value.find((option) => option.id === routeId);
  if (selected) {
    applyDepartmentRoute(selected);
  }
}

function handleApplicantChange(userId: number): void {
  const selected = userOptions.value.find((user) => user.id === userId);
  if (!selected) {
    return;
  }
  formModel.applicantUserId = selected.id;
  formModel.purchaserUserId = selected.id;
  formModel.regionId = selected.regionId || authStore.user?.regionId || 1;
  const applicantRoute = matchDepartmentRoute({
    deptCode: selected.deptCode || "",
    deptName: selected.deptName || ""
  });
  if (!selectedRouteId.value && applicantRoute) {
    applyDepartmentRoute(applicantRoute);
  }
}

function applyDepartmentRoute(option: ProcurementApprovalRouteVO): void {
  selectedRouteId.value = option.id;
  formModel.deptCode = option.deptCode || option.costCenterCode || "";
  formModel.deptName = option.deptName || [option.roomName, option.buildingName].filter(Boolean).join("-");
  formModel.budgetCode = option.costCenterCode || option.deptCode || "";
}

function matchDepartmentRoute(source: Pick<HeaderForm, "deptCode" | "deptName">): ProcurementApprovalRouteVO | undefined {
  const deptCode = String(source.deptCode || "").trim();
  const deptName = String(source.deptName || "").trim();
  return departmentOptions.value.find((option) => {
    return (
      (deptCode && (deptCode === option.deptCode || deptCode === option.costCenterCode)) ||
      (deptName && deptName === option.deptName)
    );
  });
}

function departmentOptionLabel(option: ProcurementApprovalRouteVO): string {
  const location = [option.roomName, option.buildingName].filter(Boolean).join(" / ");
  const dept = option.deptName || option.deptCode || option.costCenterCode || "";
  return dept ? `${location} - ${dept}` : location;
}

function userOptionLabel(user: UserVO): string {
  const name = user.realName || user.username;
  return user.employeeNo ? `${name}（${user.employeeNo}）` : name;
}

function goBack(): void {
  router.push("/procurement/requests");
}

function formatMoney(value: number): string {
  return Number(value ?? 0).toLocaleString("zh-CN", { style: "currency", currency: "CNY", maximumFractionDigits: 0 });
}

function normalizeUrl(url: string): string {
  return ecommerceUrlHref(url);
}

function downloadBlob(blob: Blob, fileName: string): void {
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = fileName;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}

function generateMaterialCode(): string {
  const suffix = Math.random().toString(36).slice(2, 8).toUpperCase();
  return `MAT-${suffix}`;
}

onMounted(async () => {
  await loadUserOptions();
  await loadDetail();
  await loadDepartmentOptions();
});
</script>

<style scoped>
.request-workspace {
  min-width: 0;
}

.request-editor {
  display: grid;
  gap: 14px;
  min-width: 0;
}

.item-precheck-opinion {
  display: grid;
  gap: 6px;
}

.item-precheck-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.item-precheck-opinion p {
  margin: 0;
  color: #334155;
  font-size: 13px;
  line-height: 1.55;
}

.item-precheck-empty {
  color: #94a3b8;
  font-size: 12px;
}

.precheck-summary {
  display: grid;
  gap: 10px;
  margin-top: 14px;
  padding: 14px 16px;
  border: 1px solid #dbe5f2;
  border-radius: 6px;
  background: #f8fbff;
}

.precheck-summary-head,
.precheck-summary-head > div,
.precheck-summary-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.precheck-summary-head {
  justify-content: space-between;
}

.precheck-summary-head > span {
  color: #7a8aa3;
  font-size: 12px;
}

.precheck-summary > p {
  margin: 0;
  color: #334155;
  font-size: 13px;
  line-height: 1.65;
}

.precheck-summary-actions {
  justify-content: flex-end;
}

.request-info-form {
  --form-field-gap: 14px;
}

.request-info-grid {
  display: grid;
  grid-template-columns: repeat(12, minmax(0, 1fr));
  gap: var(--form-field-gap) 16px;
  align-items: start;
}

.request-info-grid :deep(.el-form-item) {
  margin-bottom: 0;
}

.field-span-3 {
  grid-column: span 3;
}

.field-span-4 {
  grid-column: span 4;
}

.field-span-5 {
  grid-column: span 5;
}

.urgency-control {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 32px;
}

.urgency-label {
  color: #64748b;
  font-size: 14px;
  white-space: nowrap;
}

.urgency-label.is-active {
  color: #165dff;
  font-weight: 600;
}

.readonly-input :deep(.el-input__wrapper) {
  background: #f8fafc;
  box-shadow: 0 0 0 1px #e2e8f0 inset;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 16px;
}

.summary-line {
  margin-top: 14px;
  text-align: right;
  font-weight: 700;
  color: #0f172a;
}

.hidden-file-input {
  display: none;
}

.row-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  white-space: nowrap;
}

.purpose-field-label {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}

@media (max-width: 1280px) {
  .field-span-3,
  .field-span-4,
  .field-span-5 {
    grid-column: span 6;
  }
}

@media (max-width: 720px) {
  .field-span-3,
  .field-span-4,
  .field-span-5 {
    grid-column: 1 / -1;
  }
}
</style>
