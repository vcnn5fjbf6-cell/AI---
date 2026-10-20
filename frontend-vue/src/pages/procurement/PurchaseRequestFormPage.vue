<template>
  <div class="page-container">
    <PageHero
      eyebrow="Procurement"
      :title="isEditMode ? '编辑采购申请' : '新增采购申请'"
      description="由工程师发起需求，保存草稿后提交进入逐级审批。"
    >
      <div class="hero-inline">
        <span class="hero-pill">明细 {{ itemCount }} 条</span>
        <span class="hero-pill hero-pill--muted">币种 {{ formModel.currency }}</span>
      </div>
      <template #actions>
        <el-button @click="goBack">返回列表</el-button>
      </template>
      <template #aside>
        <div class="hero-stat-panel">
          <p class="hero-stat-kicker">Total Amount</p>
          <p class="hero-stat-value">{{ formatMoney(formModel.totalAmount) }}</p>
          <p class="hero-stat-note">金额按明细自动汇总，保存后可继续编辑或立即提交审批。</p>
        </div>
      </template>
    </PageHero>

    <el-card class="panel-card table-panel page-section" shadow="never">
      <div class="table-toolbar">
        <div>
          <h3 class="card-title">申请信息</h3>
          <p class="sub-text">申请人由当前登录账号自动带出，重点填写需求内容、区域和预算信息。</p>
        </div>
      </div>

      <el-form ref="formRef" :model="formModel" :rules="formRules" label-width="120px" v-loading="loading">
        <el-row :gutter="14">
          <el-col :span="12">
            <el-form-item label="申请标题" prop="reqTitle">
              <el-input v-model="formModel.reqTitle" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="申请类型" prop="reqType">
              <el-select v-model="formModel.reqType">
                <el-option label="工程项目" value="工程项目" />
                <el-option label="实物资产/无形资产/长摊等" value="实物资产/无形资产/长摊等" />
                <el-option label="服务/费用/低值易耗等" value="服务/费用/低值易耗等" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="申请人">
              <el-input :model-value="applicantDisplay" disabled />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="部门编码">
              <el-input v-model="formModel.deptCode" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="部门名称">
              <el-input v-model="formModel.deptName" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="预算编码">
              <el-input v-model="formModel.budgetCode" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="币种" prop="currency">
              <el-select v-model="formModel.currency">
                <el-option label="人民币 CNY" value="CNY" />
                <el-option label="美元 USD" value="USD" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="紧急程度" prop="urgencyLevel">
              <el-select v-model="formModel.urgencyLevel">
                <el-option label="一般" :value="1" />
                <el-option label="紧急" :value="2" />
                <el-option label="特急" :value="3" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="需求日期">
              <el-date-picker v-model="formModel.needDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="中标策略" prop="awardStrategy">
              <el-select v-model="formModel.awardStrategy">
                <el-option label="总价最低中标" value="TOTAL_LOWEST" />
                <el-option label="单项最低中标" value="ITEM_LOWEST" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注">
              <el-input v-model="formModel.remark" type="textarea" rows="2" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <el-divider />

      <div class="table-toolbar">
        <h3 class="card-title">采购明细</h3>
        <el-button type="primary" plain @click="addItemRow">新增明细行</el-button>
      </div>

      <el-table :data="formModel.items" stripe>
        <el-table-column type="index" label="行号" width="60" />
        <el-table-column label="物料编码" min-width="130">
          <template #default="{ row }">
            <el-input v-model="row.itemCode" disabled />
          </template>
        </el-table-column>
        <el-table-column label="商品名称" min-width="160">
          <template #default="{ row }">
            <el-input v-model="row.itemName" />
          </template>
        </el-table-column>
        <el-table-column label="规格型号" min-width="150">
          <template #default="{ row }">
            <el-input v-model="row.specModel" />
          </template>
        </el-table-column>
        <el-table-column label="单位" width="120">
          <template #default="{ row }">
            <ProcurementUnitSelect v-model="row.unit" />
          </template>
        </el-table-column>
        <el-table-column label="数量" width="110">
          <template #default="{ row }">
            <el-input-number v-model="row.qty" :min="0.01" :precision="2" controls-position="right" style="width: 100%" />
          </template>
        </el-table-column>
        <el-table-column label="单价" width="120">
          <template #default="{ row }">
            <el-input-number v-model="row.unitPrice" :min="0.01" :precision="2" controls-position="right" style="width: 100%" />
          </template>
        </el-table-column>
        <el-table-column label="税率" width="100">
          <template #default="{ row }">
            <el-input-number v-model="row.taxRate" :min="0" :max="1" :precision="2" controls-position="right" style="width: 100%" />
          </template>
        </el-table-column>
        <el-table-column label="金额" width="140">
          <template #default="{ row }">{{ calcLineAmount(row) }}</template>
        </el-table-column>
        <el-table-column label="需求日期" width="140">
          <template #default="{ row }">
            <el-date-picker v-model="row.needDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
          </template>
        </el-table-column>
        <el-table-column label="采购原因" min-width="200">
          <template #default="{ row }">
            <el-input v-model="row.purpose" />
          </template>
        </el-table-column>
        <el-table-column label="电商链接" min-width="190">
          <template #default="{ row }">
            <el-input v-model="row.ecommerceUrl" placeholder="https://..." />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{ $index }">
            <el-button link type="danger" :disabled="formModel.items.length <= 1" @click="removeItemRow($index)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="summary-box">
        总金额：<strong>{{ formatMoney(formModel.totalAmount) }}</strong>
      </div>

      <div class="footer-actions">
        <el-button @click="goBack">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSave(false)">保存草稿</el-button>
        <el-button type="warning" :loading="submitLoading" @click="handleSave(true)">保存并提交</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from "vue";
import type { FormInstance, FormRules } from "element-plus";
import { ElMessage } from "element-plus";
import { useRoute, useRouter } from "vue-router";
import PageHero from "../../components/PageHero.vue";
import ProcurementUnitSelect from "../../components/procurement/ProcurementUnitSelect.vue";
import {
  createWorkflowRequestApi,
  getWorkflowDetailApi,
  previewSubmitWorkflowRequestApi,
  submitWorkflowRequestApi,
  updateWorkflowRequestApi
} from "../../api/workflow";
import { useAuthStore } from "../../stores/auth";
import type { PurchaseRequestForm, PurchaseRequestItem } from "../../types/purchase";
import { normalizeEcommerceUrlInput } from "../../utils/ecommerce-url";
import { confirmSubmitNextApprovalDialog } from "../../utils/workflow-submit";

const route = useRoute();
const router = useRouter();
const authStore = useAuthStore();

const loading = ref(false);
const submitLoading = ref(false);
const formRef = ref<FormInstance>();

const isEditMode = computed(() => Boolean(route.params.id));
const itemCount = computed(() => formModel.items.length);
const applicantDisplay = computed(() => authStore.realName || authStore.username || "当前登录账号");

const createDefaultItem = (): PurchaseRequestItem => ({
  itemCode: generateMaterialCode(),
  reqType: "服务/费用/低值易耗等",
  itemName: "",
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

const createDefaultForm = (): PurchaseRequestForm => ({
  reqTitle: "",
  reqType: "服务/费用/低值易耗等",
  applicantUserId: authStore.user?.userId ?? 0,
  purchaserUserId: authStore.user?.userId ?? 0,
  regionId: authStore.user?.regionId ?? 1,
  deptCode: "",
  deptName: "",
  budgetCode: "",
  currency: "CNY",
  totalAmount: 0,
  urgencyLevel: 1,
  needDate: "",
  reqReason: "",
  awardStrategy: "TOTAL_LOWEST",
  remark: "",
  items: [createDefaultItem()]
});

const formModel = reactive<PurchaseRequestForm>(createDefaultForm());

const formRules: FormRules<PurchaseRequestForm> = {
  reqTitle: [{ required: true, message: "请输入申请标题", trigger: "blur" }],
  reqType: [{ required: true, message: "请选择申请类型", trigger: "change" }],
  currency: [{ required: true, message: "请选择币种", trigger: "change" }],
  urgencyLevel: [{ required: true, message: "请选择紧急程度", trigger: "change" }]
};

watch(
  () => formModel.items,
  () => {
    formModel.totalAmount = Number(
      formModel.items
        .reduce((sum, item) => sum + Number(item.qty || 0) * Number(item.unitPrice || 0), 0)
        .toFixed(2)
    );
  },
  { deep: true }
);

function calcLineAmount(item: PurchaseRequestItem): string {
  const lineAmount = Number(item.qty || 0) * Number(item.unitPrice || 0);
  item.lineAmount = Number(lineAmount.toFixed(2));
  return formatMoney(item.lineAmount);
}

function formatMoney(value: number): string {
  return Number(value ?? 0).toLocaleString("zh-CN", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  });
}

function addItemRow(): void {
  formModel.items.push(createDefaultItem());
}

function removeItemRow(index: number): void {
  formModel.items.splice(index, 1);
}

function generateMaterialCode(): string {
  const suffix = Math.random().toString(36).slice(2, 8).toUpperCase();
  return `MAT-${suffix}`;
}

function goBack(): void {
  router.push("/purchase-requests");
}

async function loadDetail(): Promise<void> {
  if (!isEditMode.value) {
    Object.assign(formModel, createDefaultForm());
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
      ...createDefaultForm(),
      ...detail,
      items: detail.items && detail.items.length > 0 ? detail.items : [createDefaultItem()]
    });
  } finally {
    loading.value = false;
  }
}

function buildSubmitPayload(): PurchaseRequestForm {
  return {
    ...formModel,
    applicantUserId: formModel.applicantUserId || authStore.user?.userId || 0,
    purchaserUserId: formModel.purchaserUserId || authStore.user?.userId || 0,
    regionId: formModel.regionId || authStore.user?.regionId || 1,
    totalAmount: formModel.totalAmount,
    items: formModel.items.map((item) => ({
      ...item,
      itemCode: item.itemCode || generateMaterialCode(),
      reqType: item.reqType || formModel.reqType,
      qty: Number(item.qty),
      unitPrice: Number(item.unitPrice),
      taxRate: Number(item.taxRate),
      lineAmount: Number((Number(item.qty) * Number(item.unitPrice)).toFixed(2))
    }))
  };
}

async function handleSave(needSubmit: boolean): Promise<void> {
  if (!formRef.value) {
    return;
  }
  const valid = await formRef.value.validate().catch(() => false);
  if (!valid) {
    return;
  }
  if (formModel.items.length === 0) {
    ElMessage.warning("请至少添加一条采购明细");
    return;
  }
  if (formModel.items.some((item) => !item.purpose.trim())) {
    ElMessage.warning("每行采购明细都要填写采购原因");
    return;
  }
  if (formModel.items.some((item) => !isPositiveMoney(item.unitPrice))) {
    ElMessage.warning("每行采购明细单价必须大于 0");
    return;
  }
  for (let index = 0; index < formModel.items.length; index++) {
    const ecommerceUrlResult = normalizeEcommerceUrlInput(formModel.items[index].ecommerceUrl);
    if (ecommerceUrlResult.error) {
      ElMessage.warning(`第 ${index + 1} 行电商链接：${ecommerceUrlResult.error}`);
      return;
    }
    formModel.items[index].ecommerceUrl = ecommerceUrlResult.value;
  }

  submitLoading.value = true;
  try {
    const payload = buildSubmitPayload();
    let requestId: number;
    const wasEditMode = isEditMode.value;
    if (isEditMode.value) {
      requestId = Number(route.params.id);
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

onMounted(() => {
  loadDetail();
});

function isPositiveMoney(value: unknown): boolean {
  const numericValue = Number(value);
  return Number.isFinite(numericValue) && numericValue > 0;
}
</script>

<style scoped>
.summary-box {
  margin-top: 16px;
  text-align: right;
  font-size: 16px;
  color: rgba(29, 29, 31, 0.72);
}

.summary-box strong {
  color: #0071e3;
}

.footer-actions {
  margin-top: 22px;
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
