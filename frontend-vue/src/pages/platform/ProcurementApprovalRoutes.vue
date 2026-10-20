<template>
  <VnetPage title="部门管理">
    <template #actions>
      <el-button :loading="rulesLoading" @click="openRuleDrawer">查看平台规则</el-button>
      <el-dropdown v-if="canImportRoute" trigger="click" @command="handleBatchCommand">
        <el-button :loading="templateExporting || templateImporting">批量维护</el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="export">导出维护模板</el-dropdown-item>
            <el-dropdown-item command="import">批量导入修改</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
      <el-button :loading="loading" @click="loadConfig">刷新</el-button>
      <el-button v-if="canManageRoute" type="primary" @click="openCreateRoute">新增部门</el-button>
      <input ref="routeImportInputRef" class="hidden-file-input" type="file" accept=".xlsx,.xls" @change="handleRouteImportFile" />
    </template>

    <section class="department-overview" aria-label="部门配置概览">
      <div class="overview-card">
        <span>部门总数</span>
        <strong>{{ routeCount }}</strong>
        <small>当前查询范围</small>
      </div>
      <div class="overview-card">
        <span>正常启用</span>
        <strong>{{ enabledRouteCount }}</strong>
        <small>{{ disabledRouteCount }} 个部门已停用</small>
      </div>
      <div class="overview-card" :class="{ 'is-warning': incompleteRouteCount > 0 }">
        <span>配置待完善</span>
        <strong>{{ incompleteRouteCount }}</strong>
        <small>缺少地址或必要审批人</small>
      </div>
      <div class="overview-card">
        <span>覆盖机房</span>
        <strong>{{ coveredRoomCount }}</strong>
        <small>按机房名称去重</small>
      </div>
    </section>

    <VnetPanel title="部门准入与审批配置">
      <template #actions>
        <span class="result-count">当前 {{ routes.length }} 条</span>
      </template>
      <div class="route-filters">
        <el-input v-model="filters.keyword" clearable placeholder="搜索部门、编码、机房或审批人" @keyup.enter="loadConfig" />
        <el-input v-model="filters.roomName" clearable placeholder="筛选机房" @keyup.enter="loadConfig" />
        <el-input v-model="filters.regionName" clearable placeholder="筛选区域" @keyup.enter="loadConfig" />
        <el-select v-model="filters.enabled" clearable placeholder="状态">
          <el-option label="启用" :value="1" />
          <el-option label="停用" :value="0" />
        </el-select>
        <el-button type="primary" :loading="loading" @click="loadConfig">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="routes" stripe empty-text="暂无符合条件的部门">
        <el-table-column label="配置状态" width="96">
          <template #default="{ row }">
            <div class="status-stack">
              <el-tag :type="row.enabled === 1 ? 'success' : 'info'" effect="plain">
                {{ row.enabled === 1 ? "启用" : "停用" }}
              </el-tag>
              <el-tooltip v-if="row.enabled === 1 && configurationIssues(row).length" :content="configurationIssues(row).join('；')" placement="right">
                <el-tag type="warning" effect="plain">待完善</el-tag>
              </el-tooltip>
              <el-tag v-else-if="row.enabled === 1" type="success" effect="plain">配置完整</el-tag>
              <el-tag v-else type="info" effect="plain">未生效</el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="部门与业务范围" min-width="300">
          <template #default="{ row }">
            <el-tooltip :content="row.deptName || '-'" placement="top-start" :show-after="500">
              <div class="primary-text department-name">{{ row.deptName || "-" }}</div>
            </el-tooltip>
            <div class="sub-text">{{ row.roomName || "未配置机房" }} / {{ row.buildingName || "未配置楼栋" }} · {{ row.regionName || "未配置区域" }}</div>
            <div class="sub-text">成本中心 {{ row.costCenterCode || "未配置" }} · 部门编号 {{ row.deptCode || "未配置" }}</div>
          </template>
        </el-table-column>
        <el-table-column label="供应商送达地址" min-width="230">
          <template #default="{ row }">
            <div class="primary-text">{{ row.address || "未配置详细地址" }}</div>
            <div class="sub-text">{{ formatAddressHierarchy(row) || "未拆分省市区" }}</div>
          </template>
        </el-table-column>
        <el-table-column label="审批链与门槛" min-width="340">
          <template #default="{ row }">
            <div class="approval-overview">
              <strong>{{ enabledLevelCount(row) }} 个启用节点</strong>
              <el-tag v-if="missingApproverLevelCount(row)" type="danger" effect="plain">
                {{ missingApproverLevelCount(row) }} 个节点缺少审批人
              </el-tag>
            </div>
            <div v-if="approvalPreview(row).length" class="approval-preview">
              <span v-for="level in approvalPreview(row)" :key="level.levelNo">
                {{ level.levelCode }} {{ level.levelName }}：{{ level.candidateNames || "按节点规则处理" }}
              </span>
            </div>
            <div v-else class="approval-preview">
              <span>{{ row.approvalPreviewText || "点击配置查看完整审批链" }}</span>
            </div>
            <div class="rule-summary">每月 {{ row.poolPushDay }} 日 · 区域经理 {{ money(row.regionThreshold) }} · 总监 {{ money(row.directorThreshold) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :loading="routeDetailLoadingId === row.id" @click="openEditRoute(row)">配置</el-button>
            <el-button v-if="canManageRoute" link type="danger" @click="deleteRoute(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </VnetPanel>

    <el-dialog
      v-model="routeDialogVisible"
      :title="editingRouteId ? '编辑部门配置' : '新增部门'"
      width="min(1180px, calc(100vw - 40px))"
      top="4vh"
      class="department-dialog"
      destroy-on-close
    >
      <div class="dialog-summary">
        <div>
          <span>当前部门</span>
          <strong>{{ computedDepartmentName || "完善机房与楼栋后自动生成" }}</strong>
        </div>
        <div class="dialog-summary-tags">
          <el-tag :type="routeForm.enabled === 1 ? 'success' : 'info'" effect="plain">
            {{ routeForm.enabled === 1 ? "启用" : "停用" }}
          </el-tag>
          <el-tag effect="plain" type="info">{{ routeForm.roomName || "未配置机房" }} / {{ routeForm.buildingName || "未配置楼栋" }}</el-tag>
        </div>
      </div>

      <el-form ref="routeFormRef" :model="routeForm" :rules="routeFormRules" label-position="top" class="department-form">
        <el-tabs v-model="routeDialogTab" class="department-tabs">
          <el-tab-pane label="部门与机房" name="department">
            <div class="form-section-head">
              <h4>部门准入信息</h4>
              <span>部门名称根据组织、区域、机房和楼栋自动生成</span>
            </div>
            <el-row :gutter="16">
              <el-col :span="6">
                <el-form-item label="状态" prop="enabled">
                  <el-switch v-model="routeForm.enabled" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" />
                </el-form-item>
              </el-col>
              <el-col :span="6">
                <el-form-item label="区域">
                  <el-input v-model="routeForm.regionName" clearable placeholder="如：华北一" />
                </el-form-item>
              </el-col>
              <el-col :span="6">
                <el-form-item label="机房" prop="roomName">
                  <el-input v-model="routeForm.roomName" clearable placeholder="如：酒仙桥" />
                </el-form-item>
              </el-col>
              <el-col :span="6">
                <el-form-item label="楼栋" prop="buildingName">
                  <el-input v-model="routeForm.buildingName" clearable placeholder="如：中化雄安" />
                </el-form-item>
              </el-col>
              <el-col :span="6">
                <el-form-item label="机房性质">
                  <el-input v-model="routeForm.roomNature" clearable placeholder="如：代维、自建" />
                </el-form-item>
              </el-col>
              <el-col :span="6">
                <el-form-item label="成本中心编码">
                  <el-input v-model="routeForm.costCenterCode" clearable placeholder="用于组织归属识别" />
                </el-form-item>
              </el-col>
              <el-col :span="6">
                <el-form-item label="部门编号">
                  <el-input v-model="routeForm.deptCode" clearable placeholder="没有可暂不填写" />
                </el-form-item>
              </el-col>
              <el-col :span="6">
                <el-form-item label="每月进入需求池日期" prop="poolPushDay">
                  <el-input-number v-model="routeForm.poolPushDay" :min="1" :max="31" controls-position="right" />
                </el-form-item>
              </el-col>
              <el-col :span="24">
                <el-form-item label="系统部门名称" prop="deptName">
                  <el-input :model-value="computedDepartmentName" readonly />
                </el-form-item>
              </el-col>
            </el-row>
          </el-tab-pane>

          <el-tab-pane label="送达地址" name="address">
            <div class="form-section-head">
              <h4>供应商送达地址</h4>
              <span>用于中标通知和订单送达信息</span>
            </div>
            <el-row :gutter="16">
              <el-col :span="6">
                <el-form-item label="省份">
                  <el-input v-model="routeForm.province" clearable placeholder="如：河北省" />
                </el-form-item>
              </el-col>
              <el-col :span="6">
                <el-form-item label="城市">
                  <el-input v-model="routeForm.city" clearable placeholder="如：雄安新区" />
                </el-form-item>
              </el-col>
              <el-col :span="6">
                <el-form-item label="区县">
                  <el-input v-model="routeForm.districtName" clearable placeholder="如：安新县" />
                </el-form-item>
              </el-col>
              <el-col :span="6">
                <el-form-item label="乡镇 / 街道">
                  <el-input v-model="routeForm.townName" clearable placeholder="可选" />
                </el-form-item>
              </el-col>
              <el-col :span="24">
                <el-form-item label="详细地址">
                  <el-input v-model="routeForm.address" clearable placeholder="楼宇、楼层、房间号等完整送达信息" />
                </el-form-item>
              </el-col>
            </el-row>
            <div class="address-preview">
              <span>供应商可见地址</span>
              <strong>{{ deliveryAddressPreview || "尚未填写送达地址" }}</strong>
            </div>
          </el-tab-pane>

          <el-tab-pane label="审批路径" name="approval">
            <div class="form-section-head">
              <h4>V1-V8 审批路径</h4>
              <span>启用节点必须配置审批人，或明确设置为无审批人时自动跳过</span>
            </div>
            <div class="approval-editor-summary">
              <div><span>启用节点</span><strong>{{ activeFormLevelCount }}</strong></div>
              <div :class="{ 'is-danger': missingFormApproverLevels.length > 0 }">
                <span>待配置审批人</span><strong>{{ missingFormApproverLevels.length }}</strong>
              </div>
              <div><span>区域经理门槛</span><strong>{{ money(formLevelAmount(5)) }}</strong></div>
              <div><span>总监门槛</span><strong>{{ money(formLevelAmount(6)) }}</strong></div>
            </div>
            <el-alert
              v-if="missingFormApproverLevels.length"
              type="warning"
              :closable="false"
              show-icon
              :title="`${missingFormApproverLevels.map((level) => level.levelCode).join('、')} 尚未配置审批人`"
            />
            <el-table :data="routeForm.approvalLevels" border class="level-editor" :row-class-name="approvalLevelRowClass">
              <el-table-column label="启用" width="72" align="center" fixed="left">
                <template #default="{ row }">
                  <el-switch v-model="row.enabled" :active-value="1" :inactive-value="0" />
                </template>
              </el-table-column>
              <el-table-column label="审批节点" width="190" fixed="left">
                <template #default="{ row }">
                  <div class="level-node-cell">
                    <span>{{ row.levelCode }}</span>
                    <el-input v-model="row.levelName" :disabled="row.enabled !== 1" />
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="进入条件" width="160">
                <template #default="{ row }">
                  <el-select v-model="row.triggerMode" :disabled="row.enabled !== 1">
                    <el-option label="始终进入" value="ALWAYS" />
                    <el-option label="专业采购时进入" value="BY_SPECIALTY" />
                    <el-option label="超过金额时进入" value="AMOUNT_GT" />
                    <el-option label="手动启用" value="MANUAL" />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column label="审批人" min-width="240">
                <template #default="{ row }">
                  <el-select
                    v-model="row.candidateUserIdList"
                    multiple
                    filterable
                    collapse-tags
                    collapse-tags-tooltip
                    :disabled="row.enabled !== 1"
                    placeholder="请选择审批人"
                  >
                    <el-option
                      v-for="user in approvalUserOptions"
                      :key="user.id"
                      :label="formatUserLabel(user)"
                      :value="user.id"
                    />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column label="通过条件" width="150">
                <template #default="{ row }">
                  <el-select v-model="row.passRule" :disabled="row.enabled !== 1">
                    <el-option label="任意一人通过" value="ANY_ONE" />
                    <el-option label="全部通过" value="ALL" />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column label="金额门槛" width="150">
                <template #default="{ row }">
                  <el-input-number
                    v-model="row.amountMin"
                    :min="0"
                    :precision="2"
                    controls-position="right"
                    :disabled="row.enabled !== 1 || row.triggerMode !== 'AMOUNT_GT'"
                  />
                </template>
              </el-table-column>
              <el-table-column label="无审批人时" width="150">
                <template #default="{ row }">
                  <el-select v-model="row.skipIfEmpty" :disabled="row.enabled !== 1">
                    <el-option label="阻止流转" :value="0" />
                    <el-option label="自动跳过" :value="1" />
                  </el-select>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>

          <el-tab-pane label="协作与备注" name="collaboration">
            <div class="form-section-head">
              <h4>部门协作人员</h4>
              <span>协作人员可查看本部门申请，但不参与审批</span>
            </div>
            <el-row :gutter="16" class="route-extra">
              <el-col :span="24">
                <el-form-item label="可查看本部门申请的协作人员">
                  <el-select
                    v-model="routeForm.assistantUserIdList"
                    multiple
                    filterable
                    collapse-tags
                    collapse-tags-tooltip
                    placeholder="请选择协作人员"
                  >
                    <el-option
                      v-for="user in approvalUserOptions"
                      :key="user.id"
                      :label="formatUserLabel(user)"
                      :value="user.id"
                    />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="24">
                <el-form-item label="管理备注">
                  <el-input v-model="routeForm.remark" type="textarea" :rows="5" placeholder="记录部门配置的特殊说明或维护原因" />
                </el-form-item>
              </el-col>
            </el-row>
          </el-tab-pane>
        </el-tabs>
      </el-form>
      <template #footer>
        <el-button @click="routeDialogVisible = false">取消</el-button>
        <el-button
          v-if="routeDialogTab === 'address' && editingRouteId && canEditRouteAddress"
          :loading="addressSaving"
          @click="submitRouteAddress"
        >
          仅保存送达地址
        </el-button>
        <el-button v-if="canManageRoute" type="primary" :loading="routeSaving" @click="submitRoute">保存部门配置</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="ruleDrawerVisible" title="平台规则参考" size="min(920px, 92vw)">
      <div v-loading="rulesLoading" class="rule-drawer-content">
        <div class="rule-drawer-intro">以下为全平台只读规则。部门差异请在部门列表中进入“配置”维护。</div>
        <el-tabs v-model="activeTab">
          <el-tab-pane label="审批路径" name="paths">
            <ConfigTable :data="workflowPaths" />
          </el-tab-pane>
          <el-tab-pane label="角色可见范围" name="roles">
            <ConfigTable :data="rolePermissions" />
          </el-tab-pane>
          <el-tab-pane label="采购规则" name="rules">
            <ConfigTable :data="approvalRules" />
          </el-tab-pane>
          <el-tab-pane label="供应商边界" name="supplier">
            <ConfigTable :data="supplierPermissions" />
          </el-tab-pane>
          <el-tab-pane label="字段归属" name="fields">
            <ConfigTable :data="fieldMappings" />
          </el-tab-pane>
        </el-tabs>
      </div>
    </el-drawer>
  </VnetPage>
</template>

<script setup lang="ts">
import { computed, defineComponent, h, onMounted, reactive, ref, watch } from "vue";
import type { PropType } from "vue";
import type { FormInstance, FormRules } from "element-plus";
import { ElMessage, ElMessageBox, ElTable, ElTableColumn } from "element-plus";
import VnetPage from "../../components/vnet/VnetPage.vue";
import VnetPanel from "../../components/vnet/VnetPanel.vue";
import {
  createProcurementApprovalRouteApi,
  deleteProcurementApprovalRouteApi,
  exportProcurementApprovalRouteTemplateApi,
  getProcurementApprovalRouteConfigApi,
  importProcurementApprovalRouteTemplateApi,
  updateProcurementApprovalRouteAddressApi,
  updateProcurementApprovalRouteApi
} from "../../api/platform";
import { getUserListApi } from "../../api/user";
import type {
  ApprovalLevelForm,
  ApprovalLevelVO,
  ApprovalRouteConfigItemVO,
  ProcurementApprovalRouteForm,
  ProcurementApprovalRouteQuery,
  ProcurementApprovalRouteVO
} from "../../types/platform";
import type { UserVO } from "../../types/system";
import { parseIdList, resolveCandidateUserIdList } from "../../utils/approval-route-candidates";
import { hasPermission } from "../../utils/permission";
import { useAuthStore } from "../../stores/auth";

const loading = ref(false);
const routeSaving = ref(false);
const addressSaving = ref(false);
const templateExporting = ref(false);
const templateImporting = ref(false);
const rulesLoading = ref(false);
const rulesLoaded = ref(false);
const activeTab = ref("paths");
const routeDialogTab = ref("department");
const ruleDrawerVisible = ref(false);
const routeDialogVisible = ref(false);
const editingRouteId = ref<number | null>(null);
const routeDetailLoadingId = ref<number | null>(null);
const routeFormRef = ref<FormInstance>();
const routeImportInputRef = ref<HTMLInputElement>();
const authStore = useAuthStore();
const filters = ref<ProcurementApprovalRouteQuery>({
  keyword: "",
  roomName: "",
  regionName: "",
  enabled: null
});

const routes = ref<ProcurementApprovalRouteVO[]>([]);
const userOptions = ref<UserVO[]>([]);
const approvalUserOptions = computed(() => userOptions.value.filter((user) => user.userType !== "SUPPLIER"));
const workflowPaths = ref<ApprovalRouteConfigItemVO[]>([]);
const rolePermissions = ref<ApprovalRouteConfigItemVO[]>([]);
const approvalRules = ref<ApprovalRouteConfigItemVO[]>([]);
const supplierPermissions = ref<ApprovalRouteConfigItemVO[]>([]);
const fieldMappings = ref<ApprovalRouteConfigItemVO[]>([]);

const routeForm = reactive<ProcurementApprovalRouteForm>(createEmptyRouteForm());
const routeCount = computed(() => routes.value.length);
const enabledRouteCount = computed(() => routes.value.filter((item) => item.enabled === 1).length);
const disabledRouteCount = computed(() => routeCount.value - enabledRouteCount.value);
const incompleteRouteCount = computed(() => routes.value.filter((item) => configurationIssues(item).length > 0).length);
const coveredRoomCount = computed(
  () => new Set(routes.value.map((item) => clean(item.roomName)).filter((item): item is string => Boolean(item))).size
);
const computedDepartmentName = computed(() => buildDepartmentName());
const deliveryAddressPreview = computed(() => fullDeliveryAddress(routeForm));
const missingFormApproverLevels = computed(() => missingApproverLevels(routeForm));
const activeFormLevelCount = computed(() => (routeForm.approvalLevels || []).filter((level) => level.enabled === 1).length);
const SYSTEM_ADMIN_ROLES = ["SUPER_ADMIN", "SYS_ADMIN", "ADMIN"];
const isAdminRole = computed(() => authStore.hasAnyRole(SYSTEM_ADMIN_ROLES));
const canManageRoute = computed(() => isAdminRole.value || hasPermission("procurement_route:manage"));
const canEditRouteAddress = computed(() => canManageRoute.value || hasPermission("procurement_route:address"));
const canImportRoute = computed(() => isAdminRole.value || hasPermission("procurement_route:import"));

const routeFormRules: FormRules<ProcurementApprovalRouteForm> = {
  roomName: [{ required: true, message: "请输入机房", trigger: "blur" }],
  buildingName: [{ required: true, message: "请输入楼栋", trigger: "blur" }],
  deptName: [{ required: true, message: "请先完善区域、机房和楼栋信息", trigger: "change" }],
  poolPushDay: [{ required: true, message: "请输入需求池推送日", trigger: "change" }]
};

const ConfigTable = defineComponent({
  props: {
    data: {
      type: Array as PropType<ApprovalRouteConfigItemVO[]>,
      default: () => []
    }
  },
  setup(props) {
    return () =>
      h(
        ElTable,
        { data: props.data, stripe: true, emptyText: "暂无规则数据" },
        {
          default: () => [
            h(ElTableColumn, { prop: "code", label: "编码", width: 170 }),
            h(ElTableColumn, { prop: "name", label: "名称", width: 160 }),
            h(ElTableColumn, { prop: "source", label: "来源/触发", minWidth: 210 }),
            h(ElTableColumn, { prop: "scope", label: "范围/条件", minWidth: 220 }),
            h(ElTableColumn, { prop: "permissions", label: "权限/动作", minWidth: 260 }),
            h(ElTableColumn, { prop: "rule", label: "规则/备注", minWidth: 260 })
          ]
        }
      );
  }
});

onMounted(() => {
  loadUserOptions();
  loadConfig();
});

async function loadUserOptions(): Promise<void> {
  userOptions.value = await getUserListApi({ status: 1 });
}

async function loadConfig(): Promise<void> {
  loading.value = true;
  try {
    const data = await getProcurementApprovalRouteConfigApi({
      keyword: clean(filters.value.keyword),
      roomName: clean(filters.value.roomName),
      regionName: clean(filters.value.regionName),
      enabled: filters.value.enabled,
      includeRules: false,
      includeApprovalLevels: false
    });
    routes.value = data.routes || [];
  } catch (error) {
    ElMessage.error("加载部门配置失败");
    throw error;
  } finally {
    loading.value = false;
  }
}

async function openRuleDrawer(): Promise<void> {
  ruleDrawerVisible.value = true;
  if (rulesLoaded.value || rulesLoading.value) {
    return;
  }
  rulesLoading.value = true;
  try {
    const data = await getProcurementApprovalRouteConfigApi({
      includeRoutes: false,
      includeRules: true,
      includeApprovalLevels: false
    });
    workflowPaths.value = data.workflowPaths || [];
    rolePermissions.value = data.rolePermissions || [];
    approvalRules.value = data.approvalRules || [];
    supplierPermissions.value = data.supplierPermissions || [];
    fieldMappings.value = data.fieldMappings || [];
    rulesLoaded.value = true;
  } catch (error) {
    ElMessage.error("加载平台规则失败");
    throw error;
  } finally {
    rulesLoading.value = false;
  }
}

function currentRouteQuery(): ProcurementApprovalRouteQuery {
  return {
    keyword: clean(filters.value.keyword),
    roomName: clean(filters.value.roomName),
    regionName: clean(filters.value.regionName),
    enabled: filters.value.enabled
  };
}

function resetFilters(): void {
  filters.value = {
    keyword: "",
    roomName: "",
    regionName: "",
    enabled: null
  };
  loadConfig();
}

function handleBatchCommand(command: string): void {
  if (command === "export") {
    downloadRouteTemplate();
    return;
  }
  if (command === "import") {
    triggerRouteImport();
  }
}

async function downloadRouteTemplate(): Promise<void> {
  templateExporting.value = true;
  try {
    const blob = await exportProcurementApprovalRouteTemplateApi(currentRouteQuery());
    downloadBlob(blob, "部门审批配置批量维护模板.xlsx");
  } finally {
    templateExporting.value = false;
  }
}

function triggerRouteImport(): void {
  routeImportInputRef.value?.click();
}

async function handleRouteImportFile(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  input.value = "";
  if (!file) {
    return;
  }
  await ElMessageBox.confirm(
    "导入会按模板批量修改部门审批配置。系统会先校验整张表，任一行部门识别列或人员信息不匹配，本次导入都不会更新任何数据。",
    "批量导入修改",
    {
      confirmButtonText: "开始导入",
      cancelButtonText: "取消",
      type: "warning"
    }
  );
  templateImporting.value = true;
  try {
    const count = await importProcurementApprovalRouteTemplateApi(file);
    ElMessage.success(`导入完成，已更新 ${count} 条部门配置`);
    await loadConfig();
  } finally {
    templateImporting.value = false;
  }
}

function openCreateRoute(): void {
  editingRouteId.value = null;
  Object.assign(routeForm, createEmptyRouteForm());
  syncDepartmentIdentity();
  routeDialogTab.value = "department";
  routeDialogVisible.value = true;
}

async function openEditRoute(row: ProcurementApprovalRouteVO): Promise<void> {
  let route = row;
  if (!row.approvalLevels?.length) {
    routeDetailLoadingId.value = row.id;
    try {
      const data = await getProcurementApprovalRouteConfigApi({
        routeId: row.id,
        includeRules: false,
        includeApprovalLevels: true
      });
      route = data.routes?.[0] || row;
      const routeIndex = routes.value.findIndex((item) => item.id === row.id);
      if (routeIndex >= 0) {
        routes.value[routeIndex] = { ...routes.value[routeIndex], ...route };
      }
    } catch (error) {
      ElMessage.error("加载部门详情失败");
      throw error;
    } finally {
      routeDetailLoadingId.value = null;
    }
  }
  editingRouteId.value = route.id;
  const levels = normalizeLevels(route.approvalLevels, route);
  Object.assign(routeForm, {
    enabled: route.enabled,
    routeKey: route.routeKey,
    roomNature: route.roomNature || "",
    roomName: route.roomName || "",
    buildingName: route.buildingName || "",
    costCenterCode: route.costCenterCode || "",
    deptCode: route.deptCode || "",
    province: route.province || "",
    city: route.city || "",
    districtName: route.districtName || "",
    townName: route.townName || "",
    address: route.address || "",
    deptName: route.deptName || "",
    floorLeaderNames: route.floorLeaderNames || "",
    floorLeaderRule: route.floorLeaderRule || "任意一人通过",
    managerNames: route.managerNames || "",
    managerRule: route.managerRule || "任意一人通过",
    assistantUserIds: route.assistantUserIds || "",
    assistantNames: route.assistantNames || "",
    assistantUserIdList: parseIdList(route.assistantUserIds),
    regionName: route.regionName || "",
    regionManagerNames: route.regionManagerNames || "",
    regionManagerRule: route.regionManagerRule || "任意一人通过",
    directorNames: route.directorNames || "",
    directorRule: route.directorRule || "任意一人通过",
    poolPushDay: route.poolPushDay || 20,
    regionThreshold: route.regionThreshold || 5000,
    directorThreshold: route.directorThreshold || 100000,
    overPriceRule: route.overPriceRule || "单项实际价 > 参考价 * 110%",
    replacementConfirmNames: route.replacementConfirmNames || "",
    supplierQuoteScope: route.supplierQuoteScope || "仅可见推送给自己的商品明细",
    approvalLevels: levels,
    remark: route.remark || ""
  });
  syncDepartmentIdentity();
  routeDialogTab.value = "department";
  routeDialogVisible.value = true;
}

async function submitRoute(): Promise<void> {
  syncDepartmentIdentity();
  const valid = await routeFormRef.value?.validate().catch(() => false);
  if (!valid) {
    routeDialogTab.value = "department";
    return;
  }
  if (missingFormApproverLevels.value.length) {
    routeDialogTab.value = "approval";
    ElMessage.warning(`${missingFormApproverLevels.value.map((level) => level.levelCode).join("、")} 未配置审批人，请选择审批人或改为自动跳过`);
    return;
  }
  syncLegacyFieldsFromLevels();
  routeSaving.value = true;
  try {
    const payload = buildRoutePayload();
    if (editingRouteId.value) {
      await updateProcurementApprovalRouteApi(editingRouteId.value, payload);
      ElMessage.success("部门审批配置已更新");
    } else {
      await createProcurementApprovalRouteApi(payload);
      ElMessage.success("部门审批配置已新增");
    }
    routeDialogVisible.value = false;
    await loadConfig();
  } finally {
    routeSaving.value = false;
  }
}

async function submitRouteAddress(): Promise<void> {
  if (!editingRouteId.value) {
    return;
  }
  addressSaving.value = true;
  try {
    await updateProcurementApprovalRouteAddressApi(editingRouteId.value, buildAddressPayload());
    ElMessage.success("送达地址已更新");
    routeDialogVisible.value = false;
    await loadConfig();
  } finally {
    addressSaving.value = false;
  }
}

function buildRoutePayload(): ProcurementApprovalRouteForm {
  const payload = { ...routeForm };
  delete payload.assistantUserIdList;
  const assistantIds = (routeForm.assistantUserIdList || []).filter((id) => Number.isFinite(Number(id)));
  return {
    ...payload,
    assistantUserIds: assistantIds.join(","),
    assistantNames: assistantIds.map((id) => formatUserLabelById(id)).filter(Boolean).join(","),
    approvalLevels: (routeForm.approvalLevels || []).map((level) => {
      const payloadLevel = { ...level };
      delete payloadLevel.candidateUserIdList;
      return payloadLevel;
    })
  };
}

function buildAddressPayload(): ProcurementApprovalRouteForm {
  const payload: ProcurementApprovalRouteForm = {
    ...createEmptyRouteForm(),
    enabled: routeForm.enabled,
    routeKey: routeForm.routeKey,
    roomName: routeForm.roomName,
    buildingName: routeForm.buildingName,
    costCenterCode: routeForm.costCenterCode,
    deptCode: routeForm.deptCode,
    deptName: computedDepartmentName.value,
    province: routeForm.province,
    city: routeForm.city,
    districtName: routeForm.districtName,
    townName: routeForm.townName,
    address: routeForm.address
  };
  return payload;
}

async function deleteRoute(row: ProcurementApprovalRouteVO): Promise<void> {
  await ElMessageBox.confirm(`确认删除 ${row.roomName || "-"} / ${row.buildingName || "-"} 的部门审批配置？`, "删除部门审批配置", {
    confirmButtonText: "删除",
    cancelButtonText: "取消",
    type: "warning"
  });
  await deleteProcurementApprovalRouteApi(row.id);
  ElMessage.success("部门审批配置已删除");
  await loadConfig();
}

type RouteConfigLike = Partial<ProcurementApprovalRouteVO> | Partial<ProcurementApprovalRouteForm>;

function enabledLevels(row: ProcurementApprovalRouteVO): ApprovalLevelForm[] {
  return normalizeLevels(row.approvalLevels, row).filter((level) => level.enabled === 1);
}

function enabledLevelCount(row: ProcurementApprovalRouteVO): number {
  return typeof row.enabledApprovalLevelCount === "number" ? row.enabledApprovalLevelCount : enabledLevels(row).length;
}

function approvalPreview(row: ProcurementApprovalRouteVO): ApprovalLevelForm[] {
  if (!row.approvalLevels?.length) {
    return [];
  }
  return enabledLevels(row).slice(0, 3);
}

function missingApproverLevels(row: RouteConfigLike): ApprovalLevelForm[] {
  const levels = normalizeLevels(row.approvalLevels, row);
  return levels.filter((level) => level.enabled === 1 && level.skipIfEmpty !== 1 && !levelHasApprover(level));
}

function missingApproverLevelCount(row: RouteConfigLike): number {
  const summaryCount = (row as ProcurementApprovalRouteVO).missingApprovalLevelCount;
  return typeof summaryCount === "number" ? summaryCount : missingApproverLevels(row).length;
}

function levelHasApprover(level: ApprovalLevelVO | ApprovalLevelForm): boolean {
  return Boolean((level.candidateUserIdList || []).length || parseIdList(level.candidateUserIds).length);
}

function configurationIssues(row: ProcurementApprovalRouteVO): string[] {
  if (row.enabled !== 1) {
    return [];
  }
  const issues: string[] = [];
  if (!clean(row.roomName) || !clean(row.buildingName) || !clean(row.deptName)) {
    issues.push("部门或机房信息不完整");
  }
  if (!clean(row.address)) {
    issues.push("未配置供应商送达地址");
  }
  const missingLevels = row.approvalLevels?.length ? missingApproverLevels(row) : [];
  const missingCount = missingApproverLevelCount(row);
  if (missingLevels.length) {
    issues.push(`${missingLevels.map((level) => level.levelCode).join("、")} 缺少审批人`);
  } else if (missingCount) {
    issues.push(`${missingCount} 个审批节点缺少审批人`);
  }
  return issues;
}

function formLevelAmount(levelNo: number): number {
  return Number((routeForm.approvalLevels || []).find((level) => level.levelNo === levelNo)?.amountMin || 0);
}

function approvalLevelRowClass({ row }: { row: ApprovalLevelForm }): string {
  return row.enabled === 1 ? "" : "is-disabled-level";
}

function normalizeLevels(
  levels: Array<ApprovalLevelVO | ApprovalLevelForm> | undefined,
  row?: RouteConfigLike
): ApprovalLevelForm[] {
  const base = defaultLevels(row);
  const incoming = levels || [];
  return base.map((item) => {
    const hit = incoming.find((level) => level.levelNo === item.levelNo);
    if (!hit) {
      return item;
    }
    const candidateUserIdList = resolveCandidateUserIdList(hit, item.candidateUserIds);
    const candidateUserIds = candidateUserIdList.join(",");
    return {
      ...item,
      ...hit,
      amountMin: hit.amountMin ?? item.amountMin ?? null,
      candidateNames: candidateUserIds ? hit.candidateNames || item.candidateNames || "" : "",
      candidateUserIds,
      candidateUserIdList
    };
  });
}

function defaultLevels(row?: RouteConfigLike): ApprovalLevelForm[] {
  return [
    level(1, "值班长审核", "DUTY_LEADER", 0, "MANUAL", "ANY_ONE", "", "", null, 1),
    level(2, "专业工程师审核", "PROFESSIONAL_ENGINEER", 1, "BY_SPECIALTY", "ANY_ONE", "", "", null, 1),
    level(3, "楼长审核", "FLOOR_LEADER", 1, "ALWAYS", passRule(row?.floorLeaderRule), "", "", null, 0),
    level(4, "经理审核", "MANAGER", 1, "ALWAYS", passRule(row?.managerRule), "", "", null, 0),
    level(5, "区域经理审核", "REGION_MANAGER", 1, "AMOUNT_GT", passRule(row?.regionManagerRule), "", "", row?.regionThreshold || 5000, 0),
    level(6, "总监审核", "SENIOR_MANAGER", 1, "AMOUNT_GT", passRule(row?.directorRule), "", "", row?.directorThreshold || 100000, 0),
    level(7, "V7 审核", "APPROVAL_V7", 0, "MANUAL", "ANY_ONE", "", "", null, 1),
    level(8, "V8 审核", "APPROVAL_V8", 0, "MANUAL", "ANY_ONE", "", "", null, 1)
  ];
}

function level(
  levelNo: number,
  levelName: string,
  roleCode: string,
  enabled: number,
  triggerMode: string,
  rule: string,
  candidateNames: string,
  candidateUserIds: string,
  amountMin: number | null,
  skipIfEmpty: number
): ApprovalLevelForm {
  return {
    levelNo,
    levelCode: `V${levelNo}`,
    levelName,
    roleCode,
    enabled,
    triggerMode,
    passRule: rule,
    amountMin,
    amountMax: null,
    skipIfEmpty,
    candidateNames,
    candidateUserIds,
    candidateUserIdList: parseIdList(candidateUserIds),
    remark: ""
  };
}

function syncLegacyFieldsFromLevels(): void {
  const levels = routeForm.approvalLevels || [];
  levels.forEach(syncLevelCandidates);
  const floor = levels.find((item) => item.levelNo === 3);
  const manager = levels.find((item) => item.levelNo === 4);
  const region = levels.find((item) => item.levelNo === 5);
  const director = levels.find((item) => item.levelNo === 6);
  routeForm.floorLeaderNames = floor?.candidateNames || "";
  routeForm.floorLeaderRule = ruleText(floor?.passRule);
  routeForm.managerNames = manager?.candidateNames || "";
  routeForm.managerRule = ruleText(manager?.passRule);
  routeForm.regionManagerNames = region?.candidateNames || "";
  routeForm.regionManagerRule = ruleText(region?.passRule);
  routeForm.directorNames = director?.candidateNames || "";
  routeForm.directorRule = ruleText(director?.passRule);
  routeForm.regionThreshold = Number(region?.amountMin || routeForm.regionThreshold || 5000);
  routeForm.directorThreshold = Number(director?.amountMin || routeForm.directorThreshold || 100000);
  routeForm.overPriceRule = "单项实际价 > 参考价 * 110%";
  routeForm.replacementConfirmNames = floor?.candidateNames || "";
  routeForm.supplierQuoteScope = "仅可见推送给自己的商品明细";
  const assistantIds = (routeForm.assistantUserIdList || []).filter((id) => Number.isFinite(Number(id)));
  routeForm.assistantUserIds = assistantIds.join(",");
  routeForm.assistantNames = assistantIds.map((id) => formatUserLabelById(id)).filter(Boolean).join(",");
}

function syncLevelCandidates(level: ApprovalLevelForm): void {
  const ids = (level.candidateUserIdList || []).filter((id) => Number.isFinite(Number(id)));
  level.candidateUserIds = ids.join(",");
  level.candidateNames = ids.map((id) => formatUserLabelById(id)).filter(Boolean).join(",");
}

function formatUserLabel(user: UserVO): string {
  return user.realName || user.username || String(user.id || "");
}

function formatUserLabelById(id: number): string {
  const user = userOptions.value.find((item) => item.id === id);
  return user ? formatUserLabel(user) : "";
}

function passRule(text?: string): string {
  return text && text.includes("全部") ? "ALL" : text === "ALL" ? "ALL" : "ANY_ONE";
}

function ruleText(rule?: string): string {
  return rule === "ALL" ? "全部通过" : "任意一人通过";
}

function cleanAddressPart(value: unknown): string {
  return String(value || "").trim();
}

function addressPathFor(row: RouteConfigLike): string[] {
  return [row.province, row.city, row.districtName, row.townName]
    .map(cleanAddressPart)
    .filter((value, index, values) => Boolean(value) && value !== values[index - 1]);
}

function formatAddressHierarchy(row: RouteConfigLike): string {
  return addressPathFor(row).join(" / ");
}

function fullDeliveryAddress(row: RouteConfigLike): string {
  const hierarchy = addressPathFor(row).join("");
  const detail = cleanAddressPart(row.address);
  if (!hierarchy) {
    return detail;
  }
  if (!detail) {
    return hierarchy;
  }
  const firstPart = addressPathFor(row)[0];
  return firstPart && detail.includes(firstPart) ? detail : `${hierarchy}${detail}`;
}

function clean(value: unknown): string | undefined {
  const text = String(value || "").trim();
  return text || undefined;
}

function downloadBlob(blob: Blob, fileName: string): void {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = fileName;
  anchor.click();
  URL.revokeObjectURL(url);
}

function normalizeDepartmentRegion(regionName: string): string {
  const region = regionName.trim();
  if (!region || region.endsWith("部")) {
    return region;
  }
  if (/^(华北|华东|华南|华中|西北|西南|东北).+/.test(region)) {
    return `${region}部`;
  }
  return region;
}

function buildDepartmentName(): string {
  const code = clean(routeForm.costCenterCode) || clean(routeForm.deptCode);
  const region = normalizeDepartmentRegion(clean(routeForm.regionName) || "");
  const room = clean(routeForm.roomName) || "";
  const building = clean(routeForm.buildingName) || "";
  const locationParts = [region, room];
  if (building && building !== room) {
    locationParts.push(building);
  }
  const orgParts = ["世纪互联-业务共享中台-DC运维事业部", ...locationParts].filter(Boolean);
  const name = orgParts.join("-");
  return code ? `[${code}]${name}` : name;
}

function syncDepartmentIdentity(): void {
  routeForm.deptName = computedDepartmentName.value;
}

watch(
  () => [routeForm.costCenterCode, routeForm.deptCode, routeForm.regionName, routeForm.roomName, routeForm.buildingName],
  () => syncDepartmentIdentity()
);

function money(value: number | string | null | undefined): string {
  const amount = Number(value || 0);
  return amount.toLocaleString("zh-CN", {
    style: "currency",
    currency: "CNY",
    maximumFractionDigits: 0
  });
}

function createEmptyRouteForm(): ProcurementApprovalRouteForm {
  return {
    enabled: 1,
    routeKey: "",
    roomNature: "",
    roomName: "",
    buildingName: "",
    costCenterCode: "",
    deptCode: "",
    province: "",
    city: "",
    districtName: "",
    townName: "",
    address: "",
    deptName: "",
    floorLeaderNames: "",
    floorLeaderRule: "任意一人通过",
    managerNames: "",
    managerRule: "任意一人通过",
    assistantUserIds: "",
    assistantNames: "",
    assistantUserIdList: [],
    regionName: "",
    regionManagerNames: "",
    regionManagerRule: "任意一人通过",
    directorNames: "",
    directorRule: "任意一人通过",
    poolPushDay: 20,
    regionThreshold: 5000,
    directorThreshold: 100000,
    overPriceRule: "单项实际价 > 参考价 * 110%",
    replacementConfirmNames: "",
    supplierQuoteScope: "仅可见推送给自己的商品明细",
    approvalLevels: defaultLevels(),
    remark: ""
  };
}
</script>

<style scoped>
.vnet-page {
  display: grid;
  gap: 16px;
}

.department-overview {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.overview-card {
  min-height: 96px;
  padding: 16px;
  border: 1px solid #d8e0ea;
  border-radius: 8px;
  background: #ffffff;
}

.overview-card.is-warning {
  border-color: #f2c879;
  background: #fffaf0;
}

.overview-card span,
.overview-card small {
  display: block;
  color: #667085;
  font-size: 13px;
}

.overview-card strong {
  display: block;
  margin: 6px 0 4px;
  color: #101828;
  font-size: 24px;
  line-height: 1.2;
}

.overview-card.is-warning strong {
  color: #b54708;
}

.result-count {
  color: #667085;
  font-size: 13px;
}

.route-filters {
  display: grid;
  grid-template-columns: minmax(240px, 1.6fr) minmax(140px, 1fr) minmax(140px, 1fr) 120px auto auto;
  gap: 10px;
  margin-bottom: 12px;
}

.primary-text {
  color: #101828;
  font-weight: 600;
  line-height: 1.45;
}

.sub-text {
  margin-top: 4px;
  color: #667085;
  font-size: 12px;
  line-height: 1.4;
}

.department-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.status-stack {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 5px;
}

.approval-overview {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 6px;
}

.approval-overview strong {
  color: #101828;
  font-size: 13px;
}

.approval-preview {
  display: grid;
  gap: 3px;
  color: #667085;
  font-size: 12px;
  line-height: 1.45;
}

.approval-preview span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rule-summary {
  margin-top: 6px;
  padding-top: 6px;
  border-top: 1px dashed #d8e0ea;
  color: #475467;
  font-size: 12px;
  line-height: 1.45;
}

.rule-drawer-content {
  min-height: 360px;
}

.dialog-summary {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 14px;
  border-left: 3px solid #155eef;
  background: #f5f8ff;
}

.dialog-summary span {
  display: block;
  color: #667085;
  font-size: 12px;
}

.dialog-summary strong {
  display: block;
  max-width: 780px;
  margin-top: 4px;
  color: #101828;
  font-size: 14px;
  line-height: 1.45;
  overflow-wrap: anywhere;
}

.dialog-summary-tags {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.department-tabs {
  margin-top: 10px;
}

.department-tabs :deep(.el-tabs__header) {
  margin-bottom: 16px;
}

.form-section-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
  padding-bottom: 10px;
  border-bottom: 1px solid #e4e9f0;
}

.form-section-head h4 {
  margin: 0;
  color: #101828;
  font-size: 15px;
}

.form-section-head span {
  color: #667085;
  font-size: 12px;
}

.department-form :deep(.el-input-number),
.department-form :deep(.el-select),
.level-editor :deep(.el-input-number),
.level-editor :deep(.el-select) {
  width: 100%;
}

.address-preview {
  padding: 14px 16px;
  border-left: 3px solid #2e90fa;
  background: #f5f8fc;
}

.address-preview span {
  display: block;
  color: #667085;
  font-size: 12px;
}

.address-preview strong {
  display: block;
  margin-top: 6px;
  color: #101828;
  font-size: 14px;
  line-height: 1.5;
  overflow-wrap: anywhere;
}

.approval-editor-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
  margin-bottom: 12px;
}

.approval-editor-summary > div {
  min-height: 64px;
  padding: 10px 12px;
  border: 1px solid #d8e0ea;
  border-radius: 6px;
  background: #ffffff;
}

.approval-editor-summary span,
.approval-editor-summary strong {
  display: block;
}

.approval-editor-summary span {
  color: #667085;
  font-size: 12px;
}

.approval-editor-summary strong {
  margin-top: 5px;
  color: #101828;
  font-size: 17px;
}

.approval-editor-summary .is-danger {
  border-color: #fda29b;
  background: #fff6f5;
}

.approval-editor-summary .is-danger strong {
  color: #b42318;
}

.level-editor {
  margin-top: 12px;
}

.level-node-cell {
  display: grid;
  grid-template-columns: 32px minmax(0, 1fr);
  align-items: center;
  gap: 8px;
}

.level-node-cell > span {
  color: #155eef;
  font-size: 12px;
  font-weight: 700;
}

.level-editor :deep(.is-disabled-level td) {
  background: #f7f9fc;
}

.route-extra {
  margin-top: 4px;
}

.rule-drawer-intro {
  margin-bottom: 12px;
  padding: 10px 12px;
  border-left: 3px solid #2e90fa;
  background: #f5f8fc;
  color: #475467;
  font-size: 13px;
  line-height: 1.5;
}

.hidden-file-input {
  display: none;
}

:global(.department-dialog .el-dialog__body) {
  max-height: calc(92vh - 138px);
  overflow-y: auto;
  padding-top: 10px;
}

@media (max-width: 1180px) {
  .department-overview {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .route-filters {
    grid-template-columns: 1fr 1fr;
  }
}

@media (max-width: 720px) {
  .department-overview,
  .route-filters,
  .approval-editor-summary {
    grid-template-columns: 1fr;
  }

  .dialog-summary,
  .form-section-head {
    flex-direction: column;
  }

  .dialog-summary-tags {
    justify-content: flex-start;
  }

  .department-form :deep(.el-col-6),
  .department-form :deep(.el-col-12) {
    flex: 0 0 100%;
    max-width: 100%;
  }
}
</style>
