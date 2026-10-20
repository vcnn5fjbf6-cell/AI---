<template>
  <div class="page-container">
    <el-card class="panel-card filter-form page-section" shadow="never">
      <el-tabs v-model="accountCategory" class="account-tabs" @tab-change="handleAccountCategoryChange">
        <el-tab-pane label="自有员工账号" name="EMPLOYEE" />
        <el-tab-pane label="供应商账号" name="SUPPLIER" />
      </el-tabs>
      <el-form :inline="true" :model="queryModel">
        <el-form-item label="账号">
          <el-input v-model="queryModel.username" clearable placeholder="输入用户名或编码" />
        </el-form-item>
        <el-form-item label="姓名">
          <el-input v-model="queryModel.realName" clearable placeholder="输入姓名" />
        </el-form-item>
        <el-form-item v-if="accountCategory === 'EMPLOYEE'" label="工号">
          <el-input v-model="queryModel.employeeNo" clearable placeholder="输入工号" />
        </el-form-item>
        <el-form-item v-if="accountCategory === 'EMPLOYEE'" label="用户类型">
          <el-select v-model="queryModel.userType" class="filter-select" clearable placeholder="全部类型">
            <el-option v-for="item in employeeUserTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="accountCategory === 'SUPPLIER'" label="关联供应商">
          <el-select v-model="queryModel.supplierId" class="filter-select filter-select--supplier" clearable filterable placeholder="全部供应商">
            <el-option
              v-for="supplier in supplierOptions"
              :key="supplier.id"
              :label="formatSupplierLabel(supplier)"
              :value="supplier.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryModel.status" class="filter-select" clearable placeholder="全部状态">
            <el-option label="启用" :value="1" />
            <el-option label="停用" :value="0" />
            <el-option v-if="accountCategory === 'EMPLOYEE'" label="离职" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleResetQuery">重置</el-button>
          <el-button v-perm="'sys_user:feishu_sync'" :loading="feishuSyncLoading" @click="handleFeishuSync">
            同步组织架构
          </el-button>
          <el-button v-perm="'sys_user:feishu_sync'" :loading="feishuDepartmentSearchLoading" @click="openFeishuDepartmentSearch">
            查飞书部门ID
          </el-button>
          <el-button v-perm="'sys_user:create'" :loading="importLoading" @click="downloadImportTemplate">
            下载模板
          </el-button>
          <el-button v-perm="'sys_user:create'" :loading="importLoading" @click="triggerImport">
            批量导入
          </el-button>
          <input ref="importFileInput" class="hidden-file-input" type="file" accept=".xlsx,.xls" @change="handleImportFileChange" />
          <el-button v-perm="'sys_user:create'" type="primary" @click="openCreate">
            {{ accountCategory === "SUPPLIER" ? "新增供应商账号" : "新增员工账号" }}
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="panel-card table-panel page-section" shadow="never">
      <div class="table-toolbar">
        <div>
          <h3 class="card-title">{{ accountCategory === "SUPPLIER" ? "供应商账号列表" : "自有员工账号列表" }}</h3>
        </div>
      </div>

      <el-table v-loading="loading" :data="tableData" stripe>
        <el-table-column label="账号信息" min-width="220">
          <template #default="{ row }">
            <div class="identity-cell">
              <div class="identity-title">{{ row.realName }}</div>
              <div class="identity-subtitle">{{ row.employeeNo || "-" }} / {{ row.username }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column v-if="accountCategory === 'EMPLOYEE'" label="用户类型" width="140">
          <template #default="{ row }">
            <el-tag :type="tagTypeByUserType(row.userType)" effect="light">{{ formatUserType(row.userType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="accountCategory === 'SUPPLIER'" label="关联供应商" min-width="220">
          <template #default="{ row }">
            <div v-if="row.supplierId" class="identity-cell">
              <div class="identity-title">{{ row.supplierName || "已绑定供应商" }}</div>
              <div class="identity-subtitle">{{ row.supplierCode || `ID ${row.supplierId}` }}</div>
            </div>
            <span v-else class="table-placeholder">未绑定</span>
          </template>
        </el-table-column>
        <el-table-column v-if="accountCategory === 'EMPLOYEE'" prop="deptName" label="部门" min-width="140" />
        <el-table-column v-if="accountCategory === 'EMPLOYEE'" prop="mobile" label="手机号" min-width="140" />
        <el-table-column v-if="accountCategory === 'EMPLOYEE'" prop="email" label="邮箱" min-width="180" show-overflow-tooltip />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" effect="light">{{ formatUserStatus(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" min-width="180" />
        <el-table-column label="操作" fixed="right" min-width="240">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button v-perm="'sys_user:update'" link type="primary" @click="openEdit(row.id)">编辑</el-button>
              <el-button
                v-if="accountCategory === 'EMPLOYEE' && row.status !== 2"
                v-perm="'sys_user:offboard'"
                link
                type="warning"
                @click="openOffboarding(row.id)"
              >
                办理离职
              </el-button>
              <el-button
                v-if="row.supplierId"
                link
                type="primary"
                @click="jumpToSupplier(row.supplierId)"
              >
                查看供应商
              </el-button>
              <el-button v-perm="'sys_user:delete'" link type="danger" @click="handleDelete(row.id)">删除</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager-box">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next, jumper"
          :total="total"
          :current-page="queryModel.pageNo"
          :page-size="queryModel.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="860px">
      <el-form ref="formRef" :model="formModel" :rules="formRules" label-width="110px" class="dialog-form">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="用户名">
              <el-input :model-value="formModel.username" disabled :placeholder="isSupplierUserType ? '绑定供应商后自动生成' : '自动同步工号'" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="姓名" prop="realName">
              <el-input v-model="formModel.realName" placeholder="请输入真实姓名" />
            </el-form-item>
          </el-col>
          <el-col v-if="!isSupplierUserType" :span="12">
            <el-form-item label="工号" prop="employeeNo">
              <el-input v-model="formModel.employeeNo" placeholder="请输入工号，用户名将自动同步" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="dialogMode === 'create' ? '登录密码' : '重置密码'" prop="password" :required="dialogMode === 'create'">
              <el-input
                v-model="formModel.password"
                type="password"
                show-password
                :disabled="dialogMode === 'create'"
                :placeholder="dialogMode === 'edit' ? '留空则保持原密码' : '默认 123456，首次登录强制修改'"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="用户类型" prop="userType">
              <el-select v-model="formModel.userType" @change="handleUserTypeChange">
                <el-option v-for="item in userTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col v-if="isSupplierUserType" :span="12">
            <el-form-item label="绑定供应商" prop="supplierId">
              <el-select v-model="formModel.supplierId" filterable placeholder="请选择供应商" @change="handleSupplierChange">
                <el-option
                  v-for="supplier in supplierOptions"
                  :key="supplier.id"
                  :label="formatSupplierLabel(supplier)"
                  :value="supplier.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="手机号">
              <el-input v-model="formModel.mobile" clearable placeholder="选填" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="邮箱">
              <el-input v-model="formModel.email" clearable placeholder="选填" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <div class="dialog-section-title">飞书绑定</div>
          </el-col>
          <el-col :span="12">
            <el-form-item label="飞书 open_id">
              <el-input v-model="formModel.feishuOpenId" clearable placeholder="ou_xxx，审批卡片默认使用" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="飞书 user_id">
              <el-input v-model="formModel.feishuUserId" clearable placeholder="飞书通讯录 user_id" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="飞书 union_id">
              <el-input v-model="formModel.feishuUnionId" clearable placeholder="on_xxx，选填" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="绑定部门">
              <el-select
                v-model="selectedDepartmentRouteId"
                filterable
                clearable
                placeholder="请选择部门/机房楼栋"
                style="width: 100%"
                @change="handleDepartmentChange"
              >
                <el-option
                  v-for="department in departmentOptions"
                  :key="department.id"
                  :label="formatDepartmentLabel(department)"
                  :value="department.id"
                />
              </el-select>
              <div v-if="formModel.deptName" class="dept-bind-preview">
                {{ formModel.deptCode || "-" }} · {{ formModel.deptName }}
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态" prop="status">
              <el-radio-group v-model="formModel.status">
                <el-radio :value="1">启用</el-radio>
                <el-radio :value="0">停用</el-radio>
                <el-radio
                  v-if="dialogMode === 'edit' && !isSupplierUserType"
                  v-perm="'sys_user:offboard'"
                  :value="2"
                >
                  离职
                </el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="角色分配" prop="roleIds">
              <el-select v-model="formModel.roleIds" multiple collapse-tags :disabled="roleSelectionDisabled" placeholder="请选择角色">
                <el-option v-for="role in visibleRoleOptions" :key="role.id" :label="role.roleName" :value="role.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注">
              <el-input v-model="formModel.remark" type="textarea" rows="3" placeholder="选填" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="offboardingDialogVisible"
      title="办理员工离职"
      width="min(980px, calc(100vw - 32px))"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <div v-loading="offboardingLoading" class="offboarding-dialog">
        <template v-if="offboardingImpact">
          <div class="offboarding-person">
            <div>
              <span class="offboarding-label">离职人员</span>
              <strong>{{ offboardingImpact.userName }}</strong>
            </div>
            <el-tag type="danger" effect="light">确认后立即冻结全部系统权限</el-tag>
          </div>

          <div class="offboarding-summary">
            <div class="offboarding-summary-item">
              <span>长期审批配置</span>
              <strong>{{ offboardingImpact.approvalConfigCount }}</strong>
            </div>
            <div class="offboarding-summary-item">
              <span>当前待办审批</span>
              <strong>{{ offboardingImpact.pendingTaskCount }}</strong>
            </div>
            <div class="offboarding-summary-item">
              <span>待交接合计</span>
              <strong>{{ offboardingImpact.impacts.length }}</strong>
            </div>
          </div>

          <el-table v-if="offboardingImpact.impacts.length" :data="offboardingImpact.impacts" border>
            <el-table-column label="影响类型" width="130">
              <template #default="{ row }">
                <el-tag :type="row.impactType === 'PENDING_TASK' ? 'warning' : 'primary'" effect="light">
                  {{ row.impactType === "PENDING_TASK" ? "当前待办" : "长期配置" }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="审批范围 / 业务单据" min-width="260">
              <template #default="{ row }">
                <div class="impact-context">
                  <strong>{{ impactTitle(row) }}</strong>
                  <span>{{ impactSubtitle(row) }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="审批节点" min-width="150">
              <template #default="{ row }">
                <div class="impact-context">
                  <strong>{{ row.levelName || row.levelCode || "未命名节点" }}</strong>
                  <span>{{ row.levelCode || row.roleCode || "-" }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="逐项指定替换人员" min-width="260">
              <template #default="{ row }">
                <el-select
                  v-model="offboardingReplacementMap[row.impactKey]"
                  filterable
                  placeholder="请选择启用中的员工"
                  style="width: 100%"
                >
                  <el-option
                    v-for="user in offboardingReplacementOptions"
                    :key="user.id"
                    :label="formatReplacementUser(user)"
                    :value="user.id"
                  />
                </el-select>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-else description="该人员未关联审批配置或待办，可直接办理离职" :image-size="72" />
        </template>
      </div>
      <template #footer>
        <el-button @click="offboardingDialogVisible = false">取消</el-button>
        <el-button
          type="danger"
          :loading="offboardingSubmitLoading"
          :disabled="!canSubmitOffboarding"
          @click="confirmOffboarding"
        >
          {{ offboardingImpact?.impacts.length ? "确认交接并办理离职" : "确认办理离职" }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="feishuDepartmentSearchVisible" title="查飞书部门ID" width="920px">
      <div class="feishu-search-toolbar">
        <el-input
          v-model="feishuDepartmentKeyword"
          clearable
          placeholder="输入部门名称关键词，例如 酒仙桥 或 M6"
          @keyup.enter="searchFeishuDepartments"
        />
        <el-button type="primary" :loading="feishuDepartmentSearchLoading" @click="searchFeishuDepartments">搜索</el-button>
      </div>
      <el-table v-loading="feishuDepartmentSearchLoading" :data="feishuDepartmentResults" stripe>
        <el-table-column prop="name" label="部门名称" min-width="180" />
        <el-table-column prop="path" label="路径" min-width="260" show-overflow-tooltip />
        <el-table-column prop="departmentId" label="department_id" min-width="220" show-overflow-tooltip />
        <el-table-column prop="openDepartmentId" label="open_department_id" min-width="240" show-overflow-tooltip />
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :disabled="!row.departmentId" @click="copyFeishuDepartmentId(row.departmentId, 'department_id')">
              复制普通ID
            </el-button>
            <el-button link type="primary" :disabled="!row.openDepartmentId" @click="copyFeishuDepartmentId(row.openDepartmentId, 'open_department_id')">
              复制开放ID
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import type { FormInstance, FormRules } from "element-plus";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  createUserApi,
  deleteUserApi,
  downloadUserImportTemplateApi,
  getUserDetailApi,
  getUserListApi,
  getUserOffboardingImpactApi,
  getUserPageApi,
  importUsersApi,
  offboardUserApi,
  searchFeishuDepartmentsApi,
  syncFeishuUsersApi,
  updateUserApi
} from "../../api/user";
import type { ApiBusinessError } from "../../api/http";
import { getRoleListApi } from "../../api/role";
import { getSupplierListApi } from "../../api/supplier";
import { getProcurementDepartmentOptionsApi } from "../../api/workflow";
import type {
  FeishuDepartmentSearchResult,
  RoleVO,
  UserForm,
  UserOffboardingImpact,
  UserOffboardingImpactItem,
  UserVO
} from "../../types/system";
import type { ProcurementApprovalRouteVO } from "../../types/platform";
import type { SupplierVO } from "../../types/supplier";

const route = useRoute();
const router = useRouter();

const loading = ref(false);
const submitLoading = ref(false);
const feishuSyncLoading = ref(false);
const feishuDepartmentSearchVisible = ref(false);
const feishuDepartmentSearchLoading = ref(false);
const feishuDepartmentKeyword = ref("");
const feishuDepartmentResults = ref<FeishuDepartmentSearchResult[]>([]);
const importLoading = ref(false);
const importFileInput = ref<HTMLInputElement>();
const tableData = ref<UserVO[]>([]);
const total = ref(0);
const roleOptions = ref<RoleVO[]>([]);
const supplierOptions = ref<SupplierVO[]>([]);
const departmentOptions = ref<ProcurementApprovalRouteVO[]>([]);
const selectedDepartmentRouteId = ref<number | null>(null);
const DEFAULT_INITIAL_PASSWORD = "123456";

type UserTypeOption = {
  label: string;
  value: string;
};

type AccountCategory = "EMPLOYEE" | "SUPPLIER";

const accountCategory = ref<AccountCategory>("EMPLOYEE");

const primaryUserTypeCodes = [
  "SUPER_ADMIN",
  "ENGINEER",
  "FLOOR_LEADER",
  "MANAGER",
  "SENIOR_MANAGER",
  "REGION_MANAGER",
  "PURCHASER",
  "SUPPLIER"
];

const userTypeOptions = computed<UserTypeOption[]>(() => {
  const activeRoles = roleOptions.value.filter((role) => role.status === 1);
  const standardOptions = primaryUserTypeCodes
    .map((code) => {
      const role = activeRoles.find((item) => item.roleCode === code);
      if (role) {
        return { label: role.roleName, value: role.roleCode };
      }
      return roleOptions.value.length === 0 ? { label: code, value: code } : null;
    })
    .filter((item): item is UserTypeOption => Boolean(item));
  const customOptions = activeRoles
    .filter((role) => !primaryUserTypeCodes.includes(role.roleCode) && role.roleType !== "ADMIN")
    .map((role) => ({ label: role.roleName, value: role.roleCode }));
  return [...standardOptions, ...customOptions];
});

const employeeUserTypeOptions = computed(() => userTypeOptions.value.filter((item) => item.value !== "SUPPLIER"));

const queryModel = reactive({
  pageNo: 1,
  pageSize: 10,
  username: "",
  realName: "",
  employeeNo: "",
  userType: "",
  supplierId: null as number | null,
  status: null as number | null
});

const dialogVisible = ref(false);
const dialogMode = ref<"create" | "edit">("create");
const currentId = ref<number | null>(null);
const formRef = ref<FormInstance>();
const originalStatus = ref(1);
const offboardingDialogVisible = ref(false);
const offboardingLoading = ref(false);
const offboardingSubmitLoading = ref(false);
const offboardingImpact = ref<UserOffboardingImpact | null>(null);
const offboardingReplacementUsers = ref<UserVO[]>([]);
const offboardingReplacementMap = reactive<Record<string, number | null>>({});

const createDefaultForm = (): UserForm => ({
  username: "",
  password: DEFAULT_INITIAL_PASSWORD,
  realName: "",
  employeeNo: "",
  userType: "ENGINEER",
  supplierId: null,
  regionId: null,
  deptCode: "",
  deptName: "",
  mobile: "",
  email: "",
  feishuUserId: "",
  feishuOpenId: "",
  feishuUnionId: "",
  status: 1,
  dataScopeType: "ORG",
  mustChangePassword: 1,
  remark: "",
  roleIds: []
});

const formModel = reactive<UserForm>(createDefaultForm());

const isSupplierUserType = computed(() => formModel.userType === "SUPPLIER");
const roleSelectionDisabled = computed(() => formModel.userType === "SUPPLIER");
const dialogTitle = computed(() => {
  const subject = formModel.userType === "SUPPLIER" ? "供应商账号" : "员工账号";
  return dialogMode.value === "create" ? `新增${subject}` : `编辑${subject}`;
});

const offboardingReplacementOptions = computed(() =>
  offboardingReplacementUsers.value.filter((user) => user.id !== offboardingImpact.value?.userId)
);

const canSubmitOffboarding = computed(() => {
  if (offboardingLoading.value || !offboardingImpact.value) {
    return false;
  }
  return offboardingImpact.value.impacts.every((item) => Boolean(offboardingReplacementMap[item.impactKey]));
});

const visibleRoleOptions = computed(() => {
  if (formModel.userType === "SUPPLIER") {
    return roleOptions.value.filter((role) => role.roleCode === "SUPPLIER");
  }
  if (formModel.userType === "SUPER_ADMIN") {
    return roleOptions.value.filter((role) => role.roleCode === "SUPER_ADMIN");
  }
  return roleOptions.value.filter((role) => !["ADMIN", "SYS_ADMIN", "SUPER_ADMIN"].includes(role.roleCode));
});

const formRules: FormRules<UserForm> = {
  realName: [{ required: true, message: "请输入姓名", trigger: "blur" }],
  password: [
    {
      validator: (_rule, value, callback) => {
        if (dialogMode.value === "create" && !String(value ?? "").trim()) {
          callback(new Error("请输入初始密码"));
          return;
        }
        callback();
      },
      trigger: "blur"
    }
  ],
  employeeNo: [
    {
      required: true,
      validator: (_rule, value, callback) => {
        if (formModel.userType === "SUPPLIER") {
          callback();
          return;
        }
        if (!String(value ?? "").trim()) {
          callback(new Error("请输入工号"));
          return;
        }
        callback();
      },
      trigger: "blur"
    }
  ],
  userType: [{ required: true, message: "请选择用户类型", trigger: "change" }],
  supplierId: [
    {
      required: true,
      validator: (_rule, value, callback) => {
        if (formModel.userType === "SUPPLIER" && !value) {
          callback(new Error("供应商账号必须绑定供应商"));
          return;
        }
        callback();
      },
      trigger: "change"
    }
  ],
  status: [{ required: true, message: "请选择状态", trigger: "change" }],
  roleIds: [{ type: "array", required: true, message: "请至少选择一个角色", trigger: "change" }]
};

function isEmailClearConfirmError(error: unknown): error is ApiBusinessError {
  return error instanceof Error && (error as ApiBusinessError).code === "USER_EMAIL_CLEAR_CONFIRM_REQUIRED";
}

function formatUserType(userType: string): string {
  return userTypeOptions.value.find((item) => item.value === userType)?.label ?? userType;
}

function formatUserStatus(status: number): string {
  if (status === 1) {
    return "启用";
  }
  if (status === 2) {
    return "离职";
  }
  return "停用";
}

function statusTagType(status: number): "success" | "danger" | "info" {
  if (status === 1) {
    return "success";
  }
  return status === 2 ? "info" : "danger";
}

function impactTitle(item: UserOffboardingImpactItem): string {
  if (item.impactType === "PENDING_TASK") {
    return item.businessTitle || item.businessNo || "在途采购审批";
  }
  return item.scopeName || item.scopeCode || "部门审批配置";
}

function impactSubtitle(item: UserOffboardingImpactItem): string {
  if (item.impactType === "PENDING_TASK") {
    return [item.businessNo, item.scopeName].filter(Boolean).join(" · ") || "当前待办审批";
  }
  return item.scopeCode || (item.nodeEnabled === 1 ? "当前启用" : "当前停用");
}

function formatReplacementUser(user: UserVO): string {
  const identity = user.employeeNo || user.username;
  return `${user.realName}（${identity}${user.deptName ? ` · ${user.deptName}` : ""}）`;
}

function formatSupplierLabel(supplier: SupplierVO): string {
  return `${supplier.supplierName} (${supplier.supplierCode})`;
}

function formatDepartmentLabel(department: ProcurementApprovalRouteVO): string {
  const code = department.deptCode || department.costCenterCode || "-";
  const room = [department.roomName, department.buildingName].filter(Boolean).join("/");
  return `${department.deptName || room}（${code}）`;
}

function handleDepartmentChange(routeId: number | null): void {
  if (!routeId) {
    formModel.deptCode = "";
    formModel.deptName = "";
    return;
  }
  const department = departmentOptions.value.find((item) => item.id === routeId);
  if (!department) {
    return;
  }
  formModel.deptCode = department.deptCode || department.costCenterCode || "";
  formModel.deptName = department.deptName || [department.roomName, department.buildingName].filter(Boolean).join("-");
}

function matchDepartmentRoute(source: Pick<UserForm, "deptCode" | "deptName">): ProcurementApprovalRouteVO | undefined {
  const deptCode = normalizeDeptText(source.deptCode);
  const deptName = normalizeDeptText(source.deptName);
  if (!deptCode && !deptName) {
    return undefined;
  }

  const exactCodeMatch = uniqueDepartmentOption(departmentOptions.value.filter((item) =>
    deptCode && [item.deptCode, item.costCenterCode, item.routeKey].some((value) => deptCode === normalizeDeptText(value))
  ));
  if (exactCodeMatch) {
    return exactCodeMatch;
  }

  const exactNameMatch = uniqueDepartmentOption(departmentOptions.value.filter((item) =>
    deptName && [item.deptName, item.buildingName].some((value) => deptName === normalizeDeptText(value))
  ));
  if (exactNameMatch) {
    return exactNameMatch;
  }

  return uniqueBestDepartmentMatch(deptName, departmentOptions.value);
}

function uniqueBestDepartmentMatch(sourceName: string, options: ProcurementApprovalRouteVO[]): ProcurementApprovalRouteVO | undefined {
  if (!sourceName) {
    return undefined;
  }
  const matches = options
    .flatMap((item) => [item.deptName, item.costCenterCode, item.buildingName, item.roomName]
      .map((value, index) => ({ item, value: normalizeDeptText(value), priority: index })))
    .filter((entry) => entry.value && sourceName.includes(entry.value))
    .sort((left, right) => left.priority - right.priority || right.value.length - left.value.length || left.item.id - right.item.id);
  if (matches.length === 0) {
    return undefined;
  }
  const best = matches[0];
  const tied = matches.filter((entry) => entry.priority === best.priority && entry.value.length === best.value.length);
  const ids = new Set(tied.map((entry) => entry.item.id));
  return ids.size === 1 ? best.item : undefined;
}

function normalizeDeptText(value: unknown): string {
  return String(value ?? "").trim().toUpperCase();
}

function uniqueDepartmentOption(options: ProcurementApprovalRouteVO[]): ProcurementApprovalRouteVO | undefined {
  const ids = new Set(options.map((item) => item.id));
  return ids.size === 1 ? options[0] : undefined;
}

function tagTypeByUserType(userType: string): "primary" | "success" | "warning" | "info" {
  if (userType === "SUPPLIER") {
    return "warning";
  }
  if (userType === "SUPER_ADMIN") {
    return "primary";
  }
  return "success";
}

function pickPrimaryRoleIds(userType: string): number[] {
  const match = roleOptions.value.find((role) => role.roleCode === userType);
  return match ? [match.id] : [];
}

function syncFormByUserType(): void {
  if (formModel.userType === "SUPPLIER") {
    formModel.dataScopeType = "SELF";
    formModel.employeeNo = "";
    syncFormUsername();
  } else if (formModel.userType === "SUPER_ADMIN") {
    formModel.dataScopeType = "ALL";
    formModel.supplierId = null;
  } else {
    formModel.dataScopeType = "ORG";
    formModel.supplierId = null;
  }

  const primaryRoleIds = pickPrimaryRoleIds(formModel.userType);
  if (formModel.userType === "SUPPLIER") {
    formModel.roleIds = primaryRoleIds;
    return;
  }
  if (primaryRoleIds.length > 0 && !primaryRoleIds.every((id) => formModel.roleIds.includes(id))) {
    formModel.roleIds = Array.from(new Set([...primaryRoleIds, ...formModel.roleIds]));
  }
}

function handleUserTypeChange(): void {
  syncFormByUserType();
  formRef.value?.clearValidate(["employeeNo", "supplierId"]);
}

function handleSupplierChange(): void {
  syncFormUsername();
}

async function loadRoles(): Promise<void> {
  roleOptions.value = await getRoleListApi();
  syncFormByUserType();
}

async function loadSupplierOptions(): Promise<void> {
  supplierOptions.value = await getSupplierListApi({ status: "ACTIVE" });
}

async function loadDepartmentOptions(): Promise<void> {
  departmentOptions.value = await getProcurementDepartmentOptionsApi();
}

async function loadTableData(): Promise<void> {
  loading.value = true;
  try {
    const data = await getUserPageApi({
      pageNo: queryModel.pageNo,
      pageSize: queryModel.pageSize,
      accountCategory: accountCategory.value,
      username: queryModel.username || undefined,
      realName: queryModel.realName || undefined,
      employeeNo: accountCategory.value === "EMPLOYEE" ? queryModel.employeeNo || undefined : undefined,
      userType: accountCategory.value === "EMPLOYEE" ? queryModel.userType || undefined : undefined,
      supplierId: accountCategory.value === "SUPPLIER" ? queryModel.supplierId : null,
      status: queryModel.status
    });
    tableData.value = data.records;
    total.value = data.total;
  } finally {
    loading.value = false;
  }
}

function applySupplierFilterFromRoute(): void {
  const supplierId = Number(route.query.supplierId ?? "");
  queryModel.supplierId = Number.isFinite(supplierId) && supplierId > 0 ? supplierId : null;
  if (queryModel.supplierId) {
    accountCategory.value = "SUPPLIER";
  }
}

function handleAccountCategoryChange(): void {
  queryModel.pageNo = 1;
  queryModel.userType = "";
  queryModel.employeeNo = "";
  if (accountCategory.value === "EMPLOYEE") {
    queryModel.supplierId = null;
  }
  void loadTableData();
}

function handleSearch(): void {
  queryModel.pageNo = 1;
  void loadTableData();
}

function handleResetQuery(): void {
  queryModel.username = "";
  queryModel.realName = "";
  queryModel.employeeNo = "";
  queryModel.userType = "";
  queryModel.supplierId = null;
  queryModel.status = null;
  queryModel.pageNo = 1;
  void loadTableData();
}

function handlePageChange(pageNo: number): void {
  queryModel.pageNo = pageNo;
  void loadTableData();
}

function handleSizeChange(pageSize: number): void {
  queryModel.pageSize = pageSize;
  queryModel.pageNo = 1;
  void loadTableData();
}

function resetForm(): void {
  Object.assign(formModel, createDefaultForm());
  if (accountCategory.value === "SUPPLIER") {
    formModel.userType = "SUPPLIER";
    formModel.dataScopeType = "SELF";
  }
  selectedDepartmentRouteId.value = null;
  syncFormByUserType();
}

function syncFormUsername(): void {
  if (formModel.userType === "SUPPLIER") {
    const supplier = supplierOptions.value.find((item) => item.id === formModel.supplierId);
    formModel.username = supplier?.supplierName?.trim() || formModel.username?.trim() || "";
    return;
  }
  formModel.username = formModel.employeeNo?.trim() || "";
}

function openCreate(): void {
  dialogMode.value = "create";
  currentId.value = null;
  originalStatus.value = 1;
  resetForm();
  if (accountCategory.value === "SUPPLIER" || queryModel.supplierId) {
    formModel.userType = "SUPPLIER";
    formModel.supplierId = queryModel.supplierId;
    syncFormByUserType();
  }
  dialogVisible.value = true;
}

async function openEdit(id: number): Promise<void> {
  dialogMode.value = "edit";
  currentId.value = id;
  const detail = await getUserDetailApi(id);
  originalStatus.value = detail.status;
  Object.assign(formModel, {
    ...createDefaultForm(),
    ...detail,
    password: ""
  });
  syncFormUsername();
  selectedDepartmentRouteId.value = matchDepartmentRoute(formModel)?.id ?? null;
  syncFormByUserType();
  dialogVisible.value = true;
}

async function handleSubmit(): Promise<void> {
  if (!formRef.value) {
    return;
  }
  const valid = await formRef.value.validate().catch(() => false);
  if (!valid) {
    return;
  }
  if (
    dialogMode.value === "edit"
    && currentId.value
    && originalStatus.value !== 2
    && formModel.status === 2
  ) {
    await openOffboarding(currentId.value);
    return;
  }
  if (dialogMode.value === "create" && !formModel.password) {
    formModel.password = DEFAULT_INITIAL_PASSWORD;
  }

  syncFormUsername();
  const employeeNo = isSupplierUserType.value ? undefined : formModel.employeeNo?.trim() || undefined;
  const username = isSupplierUserType.value ? formModel.username?.trim() || undefined : employeeNo;
  submitLoading.value = true;
  try {
    const payload: UserForm = {
      ...formModel,
      username,
      employeeNo,
      feishuUserId: formModel.feishuUserId?.trim() || "",
      feishuOpenId: formModel.feishuOpenId?.trim() || "",
      feishuUnionId: formModel.feishuUnionId?.trim() || "",
      regionId: formModel.regionId ?? null
    };
    if (dialogMode.value === "create") {
      payload.password = DEFAULT_INITIAL_PASSWORD;
      payload.mustChangePassword = 1;
    }
    if (dialogMode.value === "create") {
      const created = await createUserWithEmailClearConfirm(payload);
      if (!created) {
        return;
      }
      ElMessage.success("新增账号成功");
    } else if (currentId.value) {
      await updateUserApi(currentId.value, payload);
      ElMessage.success("更新账号成功");
    }
    dialogVisible.value = false;
    await loadTableData();
  } finally {
    submitLoading.value = false;
  }
}

async function openOffboarding(userId: number): Promise<void> {
  offboardingDialogVisible.value = true;
  offboardingLoading.value = true;
  offboardingImpact.value = null;
  offboardingReplacementUsers.value = [];
  Object.keys(offboardingReplacementMap).forEach((key) => delete offboardingReplacementMap[key]);
  try {
    const [impact, activeEmployees] = await Promise.all([
      getUserOffboardingImpactApi(userId),
      getUserListApi({ accountCategory: "EMPLOYEE", status: 1 })
    ]);
    offboardingImpact.value = impact;
    offboardingReplacementUsers.value = activeEmployees;
    impact.impacts.forEach((item) => {
      offboardingReplacementMap[item.impactKey] = null;
    });
  } catch (error) {
    offboardingDialogVisible.value = false;
    throw error;
  } finally {
    offboardingLoading.value = false;
  }
}

async function confirmOffboarding(): Promise<void> {
  const impact = offboardingImpact.value;
  if (!impact || !canSubmitOffboarding.value) {
    ElMessage.warning("请为每一项审批影响选择替换人员");
    return;
  }
  offboardingSubmitLoading.value = true;
  try {
    await offboardUserApi(impact.userId, {
      replacements: impact.impacts.map((item) => ({
        impactKey: item.impactKey,
        replacementUserId: Number(offboardingReplacementMap[item.impactKey])
      }))
    });
    ElMessage.success(`${impact.userName} 已办理离职，账号权限已冻结`);
    originalStatus.value = 2;
    formModel.status = 2;
    offboardingDialogVisible.value = false;
    dialogVisible.value = false;
    await loadTableData();
  } finally {
    offboardingSubmitLoading.value = false;
  }
}

async function createUserWithEmailClearConfirm(payload: UserForm): Promise<boolean> {
  try {
    await createUserApi(payload);
    return true;
  } catch (error) {
    if (!isEmailClearConfirmError(error)) {
      throw error;
    }
    try {
      await ElMessageBox.confirm(
        `${error.message}\n\n确认后系统只会清空该账号的邮箱字段，不会删除账号记录；本次新增账号将继续使用当前邮箱。`,
        "邮箱占用确认",
        {
          type: "warning",
          confirmButtonText: "清空邮箱并继续",
          cancelButtonText: "取消"
        }
      );
    } catch {
      return false;
    }
    await createUserApi({
      ...payload,
      confirmClearOccupiedEmail: true
    });
    return true;
  }
}

async function handleDelete(id: number): Promise<void> {
  await ElMessageBox.confirm("删除后不可恢复，确认继续吗？", "删除确认", { type: "warning" });
  await deleteUserApi(id);
  ElMessage.success("删除成功");
  await loadTableData();
}

async function handleFeishuSync(): Promise<void> {
  await ElMessageBox.confirm(
    "将从飞书通讯录同步部门和人员到用户管理；已有账号只更新姓名、部门、邮箱和手机号，新账号按普通组织账号创建。",
    "同步飞书组织",
    { type: "warning" }
  );
  feishuSyncLoading.value = true;
  try {
    const result = await syncFeishuUsersApi({
      recursive: true,
      createMissing: true,
      updateExisting: true,
      includeInactive: false,
      defaultUserType: "ENGINEER",
      defaultRegionId: 1
    });
    const warningText = result.warnings?.length ? `，${result.warnings.length} 条需要人工检查` : "";
    ElMessage.success(
      `飞书同步完成：部门 ${result.departmentCount} 个，飞书用户 ${result.feishuUserCount} 个，新增 ${result.createdCount} 个，更新 ${result.updatedCount} 个，跳过 ${result.skippedCount} 个${warningText}`
    );
    if (result.warnings?.length) {
      await ElMessageBox.alert(result.warnings.join("\n"), "同步提示", { type: "warning" });
    }
    await loadTableData();
  } finally {
    feishuSyncLoading.value = false;
  }
}

function openFeishuDepartmentSearch(): void {
  feishuDepartmentSearchVisible.value = true;
}

async function searchFeishuDepartments(): Promise<void> {
  const keyword = feishuDepartmentKeyword.value.trim();
  if (!keyword) {
    ElMessage.warning("请输入部门名称关键词");
    return;
  }
  feishuDepartmentSearchLoading.value = true;
  try {
    feishuDepartmentResults.value = await searchFeishuDepartmentsApi(keyword);
    if (feishuDepartmentResults.value.length === 0) {
      ElMessage.warning("没有查到可见部门，请确认应用通讯录授权范围包含该部门");
    }
  } finally {
    feishuDepartmentSearchLoading.value = false;
  }
}

async function copyFeishuDepartmentId(value: string | undefined, idType: string): Promise<void> {
  if (!value) {
    return;
  }
  try {
    await navigator.clipboard.writeText(value);
    ElMessage.success(`已复制 ${idType}，请到平台集成设置填写飞书同步根部门 ID，并选择对应 ID 类型`);
  } catch {
    ElMessage.warning(`浏览器不允许自动复制，请手动复制 ${idType}`);
  }
}

async function downloadImportTemplate(): Promise<void> {
  const blob = await downloadUserImportTemplateApi();
  downloadBlob(blob, "人员批量导入模板.xlsx");
}

function triggerImport(): void {
  importFileInput.value?.click();
}

async function handleImportFileChange(event: Event): Promise<void> {
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

  importLoading.value = true;
  try {
    const result = await importUsersApi(file);
    ElMessage.success(
      `导入完成：成功 ${result.successCount} 条，新增 ${result.createdCount} 条，更新 ${result.updatedCount} 条，失败 ${result.failedCount} 条`
    );
    if (result.errors?.length) {
      await ElMessageBox.alert(result.errors.join("\n"), "导入失败明细", { type: "warning" });
    }
    await loadTableData();
  } finally {
    importLoading.value = false;
  }
}

function downloadBlob(blob: Blob, fileName: string): void {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = fileName;
  anchor.click();
  URL.revokeObjectURL(url);
}

function jumpToSupplier(supplierId: number): void {
  void router.push({ path: "/procurement/suppliers", query: { focus: String(supplierId) } });
}

watch(
  () => formModel.employeeNo,
  () => syncFormUsername()
);

watch(
  () => formModel.supplierId,
  () => {
    if (formModel.userType === "SUPPLIER") {
      syncFormUsername();
    }
  }
);

watch(
  () => route.query.supplierId,
  () => {
    applySupplierFilterFromRoute();
    void loadTableData();
  }
);

onMounted(async () => {
  applySupplierFilterFromRoute();
  await Promise.all([loadRoles(), loadSupplierOptions(), loadDepartmentOptions()]);
  await loadTableData();
});
</script>

<style scoped>
.identity-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.identity-title {
  font-weight: 600;
  color: var(--app-text);
}

.identity-subtitle {
  font-size: 13px;
  color: var(--app-text-soft);
}

.table-placeholder {
  color: var(--app-text-soft);
}

.account-tabs {
  margin-bottom: 12px;
}

.account-tabs :deep(.el-tabs__header) {
  margin-bottom: 0;
}

.account-tabs :deep(.el-tabs__nav-wrap::after) {
  height: 1px;
  background-color: var(--app-border);
}

.account-tabs :deep(.el-tabs__item) {
  height: 36px;
  padding: 0 18px;
  font-weight: 600;
}

.filter-form :deep(.el-form) {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0 12px;
}

.filter-form :deep(.el-form-item) {
  margin-right: 0;
}

.filter-form :deep(.filter-select) {
  width: 132px;
}

.filter-form :deep(.filter-select--supplier) {
  width: 160px;
}

.dept-bind-preview {
  width: 100%;
  margin-top: 6px;
  color: var(--app-text-soft);
  font-size: 12px;
  line-height: 1.5;
}

.dialog-section-title {
  margin: 2px 0 10px 110px;
  color: var(--app-text-soft);
  font-size: 13px;
  font-weight: 600;
}

.offboarding-dialog {
  min-height: 180px;
}

.offboarding-person {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 16px;
  margin-bottom: 12px;
  background: var(--app-surface-muted);
  border: 1px solid var(--app-line);
  border-radius: 6px;
}

.offboarding-label {
  margin-right: 12px;
  color: var(--app-text-soft);
}

.offboarding-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.offboarding-summary-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 54px;
  padding: 0 14px;
  border: 1px solid var(--app-line);
  border-radius: 6px;
}

.offboarding-summary-item span,
.impact-context span {
  color: var(--app-text-soft);
  font-size: 12px;
}

.offboarding-summary-item strong {
  font-size: 20px;
}

.impact-context {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.impact-context strong,
.impact-context span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 760px) {
  .offboarding-person {
    align-items: flex-start;
    flex-direction: column;
  }

  .offboarding-summary {
    grid-template-columns: 1fr;
  }
}

.feishu-search-toolbar {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px;
  margin-bottom: 12px;
}

.hidden-file-input {
  display: none;
}
</style>
