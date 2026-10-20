<template>
  <div class="page-container">
    <PageHero
      eyebrow="System"
      title="角色管理"
      description="通过更平滑的权限配置视图维护角色模板、审批范围和系统职责。"
    >
      <div class="hero-inline">
        <span class="hero-pill">当前 {{ total }} 个角色</span>
        <span class="hero-pill hero-pill--muted">筛选项 {{ activeFilterCount }} 个</span>
      </div>
      <template #actions>
        <el-button v-perm="'sys_role:create'" type="primary" @click="openCreate">新增角色</el-button>
      </template>
      <template #aside>
        <div class="hero-stat-panel">
          <p class="hero-stat-kicker">Roles</p>
          <p class="hero-stat-value">{{ total }}</p>
          <p class="hero-stat-note">角色定义、权限范围与审批阈值在同一处完成组合配置。</p>
        </div>
      </template>
    </PageHero>

    <el-card class="panel-card filter-form page-section" shadow="never">
      <el-form :inline="true" :model="queryModel">
        <el-form-item label="角色编码">
          <el-input v-model="queryModel.roleCode" placeholder="请输入角色编码" clearable />
        </el-form-item>
        <el-form-item label="角色名称">
          <el-input v-model="queryModel.roleName" placeholder="请输入角色名称" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryModel.status" clearable placeholder="全部">
            <el-option label="启用" :value="1" />
            <el-option label="停用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleResetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="panel-card table-panel page-section" shadow="never">
      <div class="table-toolbar">
        <div>
          <h3 class="card-title">角色列表</h3>
          <p class="sub-text">权限配置和金额边界保持不变，但呈现方式更接近系统级设置页。</p>
        </div>
      </div>

      <el-table v-loading="loading" :data="tableData" stripe>
        <el-table-column prop="roleCode" label="角色编码" min-width="130" />
        <el-table-column prop="roleName" label="角色名称" min-width="140" />
        <el-table-column prop="roleType" label="角色类型" width="120" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'">{{ row.status === 1 ? "启用" : "停用" }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="说明" min-width="200" />
        <el-table-column label="操作" fixed="right" width="180">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button v-perm="'sys_role:update'" link type="primary" @click="openEdit(row.id)">编辑</el-button>
              <el-button v-perm="'sys_role:delete'" link type="danger" @click="handleDelete(row.id)">删除</el-button>
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

    <el-dialog v-model="dialogVisible" :title="dialogMode === 'create' ? '新增角色' : '编辑角色'" width="760px">
      <el-form ref="formRef" :model="formModel" :rules="formRules" label-width="112px" class="dialog-form">
        <el-row :gutter="14">
          <el-col :span="12">
            <el-form-item label="角色编码" prop="roleCode">
              <el-input v-model="formModel.roleCode" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="角色名称" prop="roleName">
              <el-input v-model="formModel.roleName" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="角色类型" prop="roleType">
              <el-select v-model="formModel.roleType">
                <el-option label="管理员" value="ADMIN" :disabled="!isSuperAdminRoleCode" />
                <el-option label="业务角色" value="BUSINESS" />
                <el-option label="供应商角色" value="SUPPLIER" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="审批金额下限">
              <el-input-number v-model="formModel.approvalAmountMin" :min="0" :precision="2" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="审批金额上限">
              <el-input-number v-model="formModel.approvalAmountMax" :min="0" :precision="2" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态" prop="status">
              <el-radio-group v-model="formModel.status">
                <el-radio :value="1">启用</el-radio>
                <el-radio :value="0">停用</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="权限配置" prop="permissionIds">
              <el-select v-model="formModel.permissionIds" multiple filterable style="width: 100%">
                <el-option
                  v-for="perm in permissionOptions"
                  :key="perm.id"
                  :label="permissionOptionLabel(perm)"
                  :value="perm.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="说明">
              <el-input v-model="formModel.description" type="textarea" rows="2" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import type { FormInstance, FormRules } from "element-plus";
import { ElMessage, ElMessageBox } from "element-plus";
import PageHero from "../../components/PageHero.vue";
import { createRoleApi, deleteRoleApi, getPermissionListApi, getRoleDetailApi, getRolePageApi, updateRoleApi } from "../../api/role";
import type { PermissionVO, RoleForm, RoleVO } from "../../types/system";

const loading = ref(false);
const submitLoading = ref(false);
const tableData = ref<RoleVO[]>([]);
const permissionOptions = ref<PermissionVO[]>([]);
const total = ref(0);

const queryModel = reactive({
  pageNo: 1,
  pageSize: 10,
  roleCode: "",
  roleName: "",
  status: null as number | null
});

const activeFilterCount = computed(
  () => [queryModel.roleCode, queryModel.roleName, queryModel.status].filter((value) => value !== "" && value !== null).length
);

const dialogVisible = ref(false);
const dialogMode = ref<"create" | "edit">("create");
const currentId = ref<number | null>(null);
const formRef = ref<FormInstance>();

const createDefaultForm = (): RoleForm => ({
  roleCode: "",
  roleName: "",
  roleType: "BUSINESS",
  dataScopeType: "ORG",
  approvalAmountMin: null,
  approvalAmountMax: null,
  status: 1,
  description: "",
  permissionIds: []
});

const formModel = reactive<RoleForm>(createDefaultForm());
const isSuperAdminRoleCode = computed(() => formModel.roleCode.trim().toUpperCase() === "SUPER_ADMIN");

const formRules: FormRules<RoleForm> = {
  roleCode: [{ required: true, message: "请输入角色编码", trigger: "blur" }],
  roleName: [{ required: true, message: "请输入角色名称", trigger: "blur" }],
  roleType: [{ required: true, message: "请选择角色类型", trigger: "change" }],
  status: [{ required: true, message: "请选择状态", trigger: "change" }],
  permissionIds: [{ type: "array", required: true, message: "请至少选择一个权限", trigger: "change" }]
};

const permissionNameMap: Record<string, string> = {
  "sys_user:create": "新增用户",
  "sys_user:update": "编辑用户",
  "sys_user:delete": "删除用户",
  "sys_user:view": "查看用户",
  "sys_user:import": "批量导入用户",
  "sys_user:feishu_sync": "同步飞书组织",
  "sys_role:create": "新增角色",
  "sys_role:update": "编辑角色",
  "sys_role:delete": "删除角色",
  "sys_role:view": "查看角色",
  "supplier:create": "新增供应商",
  "supplier:update": "编辑供应商",
  "supplier:delete": "删除供应商",
  "supplier:view": "查看供应商",
  "purchase_request:create": "新增采购申请",
  "purchase_request:update": "编辑采购申请",
  "purchase_request:submit": "提交采购申请",
  "purchase_request:delete": "删除采购申请",
  "approval:action": "审批操作",
  "platform_permission:manage": "平台权限管理",
  "platform_permission:apply": "平台权限申请",
  "workbench_notice:manage": "工作台公告维护",
  "platform_error_log:manage": "错误台账管理",
  "platform_ai_message_log:manage": "AI\u52a9\u624b\u6d88\u606f\u65e5\u5fd7",
  "database_admin:view": "数据库管理-查看",
  "database_admin:create": "数据库管理-新增",
  "database_admin:update": "数据库管理-编辑",
  "database_admin:delete": "数据库管理-删除",
  "procurement_route:manage": "部门审批配置",
  "procurement_route:address": "部门地址维护",
  "procurement_route:import": "部门批量导入修改",
  "procurement_settings:manage": "平台/采购设置",
  "procurement:demand-pool-publish": "需求池正式推送",
  "procurement:demand-pool-publish-test": "需求池测试推送",
  "monthly_procurement:submit": "飞书采购需求提交",
  "monthly_procurement:view": "查看月度采购池",
  "monthly_procurement:aggregate": "月度采购汇总",
  "monthly_procurement:supplement": "本月需求补充采购需求",
  "procurement_ai:start": "AI采购助手发起",
  "procurement_ai:view": "AI采购助手查看",
  "procurement_ai:confirm": "AI采购助手确认"
};

function permissionOptionLabel(permission: PermissionVO): string {
  const name = permissionNameMap[permission.permCode] ?? permission.permName;
  return `${name} (${permission.moduleCode})`;
}

function resolveRoleDataScopeType(roleCode: string, roleType: string): string {
  if (roleType === "SUPPLIER") {
    return "SELF";
  }
  if (roleCode.trim().toUpperCase() === "SUPER_ADMIN") {
    return "ALL";
  }
  return "ORG";
}

function syncRoleDataScopeType(): void {
  formModel.dataScopeType = resolveRoleDataScopeType(formModel.roleCode, formModel.roleType);
}

async function loadPermissionOptions(): Promise<void> {
  permissionOptions.value = await getPermissionListApi();
}

async function loadTableData(): Promise<void> {
  loading.value = true;
  try {
    const data = await getRolePageApi({
      pageNo: queryModel.pageNo,
      pageSize: queryModel.pageSize,
      roleCode: queryModel.roleCode || undefined,
      roleName: queryModel.roleName || undefined,
      status: queryModel.status
    });
    tableData.value = data.records;
    total.value = data.total;
  } finally {
    loading.value = false;
  }
}

function handleSearch(): void {
  queryModel.pageNo = 1;
  loadTableData();
}

function handleResetQuery(): void {
  queryModel.roleCode = "";
  queryModel.roleName = "";
  queryModel.status = null;
  queryModel.pageNo = 1;
  loadTableData();
}

function handlePageChange(pageNo: number): void {
  queryModel.pageNo = pageNo;
  loadTableData();
}

function handleSizeChange(pageSize: number): void {
  queryModel.pageSize = pageSize;
  queryModel.pageNo = 1;
  loadTableData();
}

function resetForm(): void {
  Object.assign(formModel, createDefaultForm());
}

function openCreate(): void {
  dialogMode.value = "create";
  currentId.value = null;
  resetForm();
  dialogVisible.value = true;
}

async function openEdit(id: number): Promise<void> {
  dialogMode.value = "edit";
  currentId.value = id;
  const detail = await getRoleDetailApi(id);
  Object.assign(formModel, {
    ...createDefaultForm(),
    ...detail
  });
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
  submitLoading.value = true;
  try {
    syncRoleDataScopeType();
    if (dialogMode.value === "create") {
      await createRoleApi(formModel);
      ElMessage.success("新增角色成功");
    } else if (currentId.value) {
      await updateRoleApi(currentId.value, formModel);
      ElMessage.success("更新角色成功");
    }
    dialogVisible.value = false;
    loadTableData();
  } finally {
    submitLoading.value = false;
  }
}

async function handleDelete(id: number): Promise<void> {
  await ElMessageBox.confirm("删除角色后不可恢复，是否继续？", "删除确认", { type: "warning" });
  await deleteRoleApi(id);
  ElMessage.success("删除成功");
  loadTableData();
}

onMounted(async () => {
  await Promise.all([loadPermissionOptions(), loadTableData()]);
});
</script>
