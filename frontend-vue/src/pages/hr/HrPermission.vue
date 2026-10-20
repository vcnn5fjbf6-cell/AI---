<template>
  <VnetPage title="人员权限分配">
    <div class="hr-stat-grid">
      <div class="hr-stat info"><span>总员工</span><strong>{{ hrStore.employees.length }}</strong></div>
      <div class="hr-stat success"><span>已开通账号</span><strong>{{ stats.bound }}</strong></div>
      <div class="hr-stat primary"><span>管理员</span><strong>{{ stats.admins }}</strong></div>
      <div class="hr-stat warning"><span>普通用户</span><strong>{{ stats.users }}</strong></div>
    </div>

    <section v-if="orphanUsers.length" class="hr-panel hr-orphan">
      <div class="hr-panel-head"><h3>未绑定员工的账号</h3></div>
      <div class="hr-orphan-row">
        <el-tag v-for="username in orphanUsers" :key="username" closable @close="removeOrphan(username)">
          {{ username }}（{{ users[username]?.name || username }}）
        </el-tag>
      </div>
    </section>

    <section class="hr-panel">
      <div class="hr-panel-head"><h3>员工权限列表</h3></div>
      <el-table :data="employeeRows" size="small" empty-text="暂无员工数据，请先添加人员">
        <el-table-column prop="name" label="员工姓名" min-width="100" />
        <el-table-column prop="position" label="职位" min-width="130" />
        <el-table-column label="系统账号" min-width="130">
          <template #default="{ row }">
            <code v-if="row.account" class="hr-code">{{ row.account }}</code>
            <span v-else class="hr-muted">未分配</span>
          </template>
        </el-table-column>
        <el-table-column label="角色" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.role" :type="row.role === 'admin' ? 'primary' : 'info'" size="small">
              {{ row.role === "admin" ? "管理员" : "普通用户" }}
            </el-tag>
            <el-tag v-else type="warning" size="small">未开通</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <template v-if="row.account">
              <el-button link type="primary" @click="openRole(row)">角色</el-button>
              <el-button link @click="openBind(row)">绑定</el-button>
              <el-button link type="danger" @click="unbind(row)">解除</el-button>
            </template>
            <template v-else>
              <el-button link type="success" @click="openCreate(row)">创建账号</el-button>
              <el-button link @click="openBind(row)">绑定已有</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="createVisible" :title="`创建系统账号 - ${createTarget?.name || ''}`" width="420px">
      <el-form label-width="90px">
        <el-form-item label="登录账号" required>
          <el-input v-model="createForm.username" placeholder="请输入登录账号" />
        </el-form-item>
        <el-form-item label="密码" required>
          <el-input v-model="createForm.password" type="password" show-password placeholder="默认 123456，至少 6 位" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="createForm.role" style="width: 100%">
            <el-option label="普通用户" value="user" />
            <el-option label="管理员" value="admin" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" @click="saveCreate">创建账号</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="bindVisible" :title="`绑定系统账号 - ${bindTarget?.name || ''}`" width="420px">
      <el-form label-width="90px">
        <el-form-item label="选择账号" required>
          <el-select v-model="bindUsername" style="width: 100%">
            <el-option v-for="username in bindableUsers" :key="username" :label="username" :value="username" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="bindVisible = false">取消</el-button>
        <el-button type="primary" @click="saveBind">确认绑定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="roleVisible" :title="`修改权限 - ${roleTarget?.name || ''}`" width="420px">
      <el-form label-width="90px">
        <el-form-item label="系统账号">
          <el-input :model-value="roleUsername" disabled />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="roleForm.role" style="width: 100%">
            <el-option label="普通用户" value="user" />
            <el-option label="管理员" value="admin" />
          </el-select>
        </el-form-item>
        <el-form-item label="新密码">
          <el-input v-model="roleForm.password" type="password" show-password placeholder="留空则不修改" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="roleVisible = false">取消</el-button>
        <el-button type="primary" @click="saveRole">保存</el-button>
      </template>
    </el-dialog>
  </VnetPage>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import VnetPage from "../../components/vnet/VnetPage.vue";
import { useHrStore, type HrEmployee } from "../../stores/hr";

interface HrAuthUser {
  password: string;
  name: string;
  role: "admin" | "user";
  employeeId?: number;
  createdAt?: string;
}

const hrStore = useHrStore();
const users = ref<Record<string, HrAuthUser>>({});
const createVisible = ref(false);
const bindVisible = ref(false);
const roleVisible = ref(false);
const createTarget = ref<HrEmployee | null>(null);
const bindTarget = ref<HrEmployee | null>(null);
const roleTarget = ref<(HrEmployee & { account?: string }) | null>(null);
const bindUsername = ref("");
const roleUsername = ref("");

const createForm = reactive({ username: "", password: "123456", role: "user" as "admin" | "user" });
const roleForm = reactive({ role: "user" as "admin" | "user", password: "" });

function loadUsers(): void {
  try {
    users.value = JSON.parse(localStorage.getItem("personnel_users") || "{}");
  } catch {
    users.value = {};
  }
  if (Object.keys(users.value).length === 0) {
    users.value = {
      admin: { password: "admin123", name: "管理员", role: "admin", createdAt: new Date().toISOString() }
    };
    saveUsers();
  }
}

function saveUsers(): void {
  localStorage.setItem("personnel_users", JSON.stringify(users.value));
}

const stats = computed(() => {
  let bound = 0;
  let admins = 0;
  let userCount = 0;
  for (const username of Object.keys(users.value)) {
    const user = users.value[username];
    if (user.employeeId) bound += 1;
    if (user.role === "admin") admins += 1;
    else userCount += 1;
  }
  return { bound, admins, users: userCount };
});

const employeeRows = computed(() =>
  hrStore.employees.map((emp) => {
    const account = Object.keys(users.value).find((username) => users.value[username].employeeId === emp.id);
    return {
      ...emp,
      account,
      role: account ? users.value[account].role : ""
    };
  })
);

const orphanUsers = computed(() =>
  Object.keys(users.value).filter((username) => !users.value[username].employeeId && username !== "admin")
);

const bindableUsers = computed(() =>
  Object.keys(users.value).filter((username) => !users.value[username].employeeId || username === "admin")
);

function openCreate(emp: HrEmployee): void {
  createTarget.value = emp;
  createForm.username = emp.name.toLowerCase();
  createForm.password = "123456";
  createForm.role = "user";
  createVisible.value = true;
}

function saveCreate(): void {
  const emp = createTarget.value;
  if (!emp) return;
  const username = createForm.username.trim();
  if (!username || createForm.password.length < 6) {
    ElMessage.warning("请输入账号，密码至少 6 位");
    return;
  }
  if (users.value[username]) {
    ElMessage.warning(`账号 ${username} 已存在`);
    return;
  }
  users.value[username] = {
    password: createForm.password,
    name: emp.name,
    role: createForm.role,
    employeeId: emp.id,
    createdAt: new Date().toISOString()
  };
  saveUsers();
  ElMessage.success(`账号 ${username} 已创建并绑定`);
  createVisible.value = false;
}

function openBind(emp: HrEmployee): void {
  bindTarget.value = emp;
  bindUsername.value = bindableUsers.value[0] || "";
  bindVisible.value = true;
}

function saveBind(): void {
  const emp = bindTarget.value;
  if (!emp || !bindUsername.value) return;
  if (!users.value[bindUsername.value]) {
    ElMessage.warning("账号不存在");
    return;
  }
  users.value[bindUsername.value].employeeId = emp.id;
  saveUsers();
  ElMessage.success(`账号 ${bindUsername.value} 已绑定到员工`);
  bindVisible.value = false;
}

async function unbind(row: HrEmployee & { account?: string }): Promise<void> {
  if (!row.account || row.account === "admin") {
    ElMessage.warning("不能解绑默认管理员");
    return;
  }
  try {
    await ElMessageBox.confirm(`确定解除 ${row.account} 与员工的绑定？`, "解除确认", { type: "warning" });
  } catch {
    return;
  }
  delete users.value[row.account].employeeId;
  saveUsers();
  ElMessage.success("已解除绑定");
}

function openRole(emp: HrEmployee & { account?: string }): void {
  if (!emp.account) return;
  roleTarget.value = emp;
  roleUsername.value = emp.account;
  roleForm.role = users.value[emp.account].role;
  roleForm.password = "";
  roleVisible.value = true;
}

function saveRole(): void {
  const emp = roleTarget.value;
  if (!emp?.account) return;
  if (emp.account === "admin") {
    ElMessage.warning("不能修改默认管理员");
    return;
  }
  if (roleForm.password && roleForm.password.length < 6) {
    ElMessage.warning("密码至少 6 位");
    return;
  }
  users.value[emp.account].role = roleForm.role;
  if (roleForm.password) users.value[emp.account].password = roleForm.password;
  saveUsers();
  ElMessage.success("权限已更新");
  roleVisible.value = false;
}

async function removeOrphan(username: string): Promise<void> {
  try {
    await ElMessageBox.confirm(`确定删除未绑定账号 ${username}？`, "删除确认", { type: "warning" });
  } catch {
    return;
  }
  delete users.value[username];
  saveUsers();
  ElMessage.success("已删除");
}

onMounted(() => {
  hrStore.load();
  loadUsers();
});
</script>

<style scoped>
.hr-stat-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.hr-stat {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 16px;
  border: 1px solid var(--app-line);
  border-radius: var(--app-radius);
  background: #ffffff;
  box-shadow: var(--app-shadow);
}

.hr-stat span {
  color: var(--app-text-muted);
  font-size: 12px;
}

.hr-stat strong {
  font-size: 24px;
  line-height: 1;
}

.hr-stat.primary strong,
.hr-stat.info strong {
  color: var(--app-primary);
}

.hr-stat.success strong {
  color: var(--app-success);
}

.hr-stat.warning strong {
  color: var(--app-warning);
}

.hr-panel {
  overflow: hidden;
  margin-top: 16px;
  border: 1px solid var(--app-line);
  border-radius: var(--app-radius);
  background: #ffffff;
  box-shadow: var(--app-shadow);
}

.hr-panel-head {
  padding: 12px 16px;
  border-bottom: 1px solid var(--app-line-soft);
}

.hr-panel-head h3 {
  margin: 0;
  font-size: 15px;
}

.hr-orphan {
  border-left: 4px solid var(--app-warning);
}

.hr-orphan-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 14px 16px;
}

.hr-code {
  font-size: 12px;
}

.hr-muted {
  color: var(--app-text-soft);
  font-size: 12px;
}

@media (max-width: 760px) {
  .hr-stat-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
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
