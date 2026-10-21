<template>
  <VnetPage title="人力资源">
    <el-alert
      v-if="showPersonnelUpdateReminder"
      class="hr-update-reminder"
      type="warning"
      show-icon
      :closable="false"
      title="每栋楼长人员信息更新提醒"
      description="请各楼栋楼长在每月10日前完成本月入职、离职及人员信息更新。"
    />
    <el-alert
      v-if="visibleUpdateLogs.length > 0"
      class="hr-update-reminder"
      :type="lateUpdateCount > 0 ? 'warning' : 'success'"
      show-icon
      :closable="false"
      :title="`本月人员更新：${visibleUpdateLogs.length} 次`"
      :description="`其中逾期更新 ${lateUpdateCount} 次（每月 10 号后更新计为逾期）`"
    />
    <el-alert
      v-if="isFloorLeader && leaderBuildings.length === 0"
      type="warning"
      show-icon
      :closable="false"
      title="楼长账号未配置负责楼栋"
      description="当前账号暂时无法查看或维护人员信息，请联系管理员配置负责楼栋。"
    />
    <template #actions>
      <el-button type="primary" :icon="Plus" @click="openDialog()">添加人员</el-button>
      <el-button :icon="Upload" @click="importInput?.click()">批量导入</el-button>
      <el-button :icon="Download" @click="downloadTemplate">生成模板</el-button>
      <el-button v-if="!isFloorLeader" type="danger" plain :icon="Delete" @click="clearAll">清空人员</el-button>
      <input ref="importInput" class="hr-hidden-input" type="file" accept=".csv,.xlsx,.xls,.txt" @change="handleImport" />
    </template>

    <div class="hr-summary">
      <div class="hr-summary-item">
        <span>总人数</span>
        <strong>{{ totalEmployees }}</strong>
      </div>
      <div class="hr-summary-item">
        <span>在职</span>
        <strong>{{ activeCount }}</strong>
      </div>
      <div class="hr-summary-item">
        <span>管理岗</span>
        <strong>{{ managerCount }}</strong>
      </div>
      <div class="hr-summary-item">
        <span>离职</span>
        <strong>{{ offCount }}</strong>
      </div>
    </div>

    <div class="hr-toolbar">
      <el-input v-model="nameKeyword" clearable placeholder="搜索姓名" style="width: 150px" :prefix-icon="Search" />
      <el-select v-model="positionFilter" clearable placeholder="全部职位" style="width: 130px">
        <el-option v-for="item in positionOptions" :key="item" :label="item" :value="item" />
      </el-select>
      <el-select v-model="genderFilter" clearable placeholder="全部性别" style="width: 110px">
        <el-option label="男" value="男" />
        <el-option label="女" value="女" />
      </el-select>
      <el-select v-model="statusFilter" clearable placeholder="全部状态" style="width: 120px">
        <el-option label="在职" value="在职" />
        <el-option label="离职" value="离职" />
      </el-select>
      <el-select v-model="buildingFilter" clearable placeholder="全部楼栋" style="width: 120px">
        <el-option v-for="item in buildingOptions" :key="item" :label="item" :value="item" />
      </el-select>
      <el-select v-model="companyFilter" clearable placeholder="全部人员性质" style="width: 140px">
        <el-option v-for="item in companyOptions" :key="item" :label="item" :value="item" />
      </el-select>
      <el-select v-model="feishuFilter" clearable placeholder="飞书绑定" style="width: 120px">
        <el-option label="已绑定" value="bound" />
        <el-option label="未绑定" value="unbound" />
      </el-select>
      <el-checkbox v-model="managerOnly">只看管理岗</el-checkbox>
      <span>大区：华北一 · 机房：酒仙桥</span>
    </div>



    <div class="hr-table-card">
      <el-table :data="filteredEmployees" size="small" :row-class-name="rowClassName" :row-style="rowStyle" empty-text="暂无人员">
        <el-table-column label="编号" width="56">
          <template #default="{ row }">{{ String(row.deptNo).padStart(3, "0") }}</template>
        </el-table-column>
        <el-table-column prop="name" label="姓名" min-width="130">
          <template #default="{ row }">
            <div class="hr-name-cell">
              <span class="hr-avatar" :style="{ background: avatarColor(row.name) }">{{ row.name?.charAt(0) }}</span>
              <div class="hr-name-main">
                <strong :style="nameStyle(row)">{{ row.name }}</strong>
                <el-tag v-if="tagOf(row.position)" size="small" :color="`${tagOf(row.position)!.color}22`" :style="{ color: tagOf(row.position)!.color }">
                  {{ tagOf(row.position)!.label }}
                </el-tag>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="position" label="工作岗位" min-width="120" show-overflow-tooltip />
        <el-table-column prop="company" label="人员性质" min-width="90" />
        <el-table-column prop="gender" label="性别" width="56" />
        <el-table-column prop="phone" label="手机" min-width="110" />
        <el-table-column label="飞书绑定" width="100">
          <template #default="{ row }">
            <el-tooltip v-if="row.feishuOpenId || row.feishuUserId" :content="feishuBindTitle(row)" placement="top">
              <el-tag type="success" size="small">已绑定</el-tag>
            </el-tooltip>
            <el-tag v-else type="info" size="small">未绑定</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === '在职' ? 'success' : 'info'" size="small">{{ row.status || "在职" }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
            <el-button link type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑人员' : '添加人员'" width="640px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="88px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="姓名" prop="name">
              <el-input v-model="form.name" placeholder="请输入姓名" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="性别">
              <el-select v-model="form.gender" style="width: 100%">
                <el-option label="男" value="男" />
                <el-option label="女" value="女" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="工作岗位">
              <el-select v-model="form.position" style="width: 100%">
                <el-option v-for="work in workPositionOptions" :key="work" :label="work" :value="work" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="人员性质">
              <el-select v-model="form.company" style="width: 100%">
                <el-option label="420" value="420" />
                <el-option label="外包" value="外包" />
                <el-option label="102" value="102" />
                <el-option label="102实习生" value="102实习生" />
                <el-option label="426" value="426" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="人员属性">
              <el-select v-model="form.personAttribute" style="width: 100%">
                <el-option label="交付" value="交付" />
                <el-option label="设施" value="设施" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="工作属性">
              <el-select v-model="form.workAttribute" style="width: 100%">
                <el-option label="5*8白班" value="5*8白班" />
                <el-option label="7*24值班" value="7*24值班" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="手机">
              <el-input v-model="form.phone" placeholder="请输入手机号" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="邮箱">
              <el-input v-model="form.email" placeholder="请输入邮箱" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="区域">
              <el-select v-model="form.region" style="width: 100%">
                <el-option label="华北一" value="华北一" />
                <el-option label="华北二" value="华北二" />
                <el-option label="华东一" value="华东一" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="机房">
              <el-select v-model="form.room" style="width: 100%">
                <el-option label="酒仙桥" value="酒仙桥" />
                <el-option label="腾仁" value="腾仁" />
                <el-option label="怀来" value="怀来" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="楼栋">
              <el-select v-model="form.building" :disabled="isFloorLeader" style="width: 100%">
                <el-option v-for="item in editableBuildingOptions" :key="item" :label="item" :value="item" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-select v-model="form.status" style="width: 100%">
                <el-option label="在职" value="在职" />
                <el-option label="离职" value="离职" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="入职日期">
              <el-date-picker v-model="form.joinDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="离职日期">
              <el-date-picker v-model="form.leaveDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-divider content-position="left">飞书绑定</el-divider>
          </el-col>
          <el-col :span="12">
            <el-form-item label="飞书 open_id">
              <el-input v-model="form.feishuOpenId" clearable placeholder="ou_xxx，审批卡片默认使用" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="飞书 user_id">
              <el-input v-model="form.feishuUserId" clearable placeholder="飞书通讯录 user_id" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="飞书 union_id">
              <el-input v-model="form.feishuUnionId" clearable placeholder="可选" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="简历附件">
              <div class="hr-resume-row">
                <input ref="resumeInput" class="hr-hidden-input" type="file" accept=".pdf,.doc,.docx,.txt" @change="handleResumeChange" />
                <el-button :icon="Upload" @click="resumeInput?.click()">选择文件</el-button>
                <el-button v-if="resumeData" :icon="Download" @click="downloadResume">下载</el-button>
                <el-button v-if="resumeData" type="danger" plain @click="clearResume">删除</el-button>
                <span v-if="resumeName" class="hr-resume-name">{{ resumeName }}</span>
                <span v-else class="hr-resume-empty">未上传简历</span>
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="证件上传">
              <div class="hr-resume-row">
                <input ref="credentialInput" class="hr-hidden-input" type="file" accept=".pdf,.doc,.docx,.txt,.jpg,.jpeg,.png" @change="handleCredentialChange" />
                <el-button :icon="Upload" @click="credentialInput?.click()">选择文件</el-button>
                <el-button v-if="credentialData" :icon="Download" @click="downloadCredential">下载</el-button>
                <el-button v-if="credentialData" type="danger" plain @click="clearCredential">删除</el-button>
                <span v-if="credentialName" class="hr-resume-name">{{ credentialName }}</span>
                <span v-else class="hr-resume-empty">未上传证件</span>
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="AI 简介">
              <el-input v-model="form.aiProfile" type="textarea" :rows="3" placeholder="AI 生成或手工填写的人员简介" />
              <div class="hr-ai-profile-actions">
                <el-button type="primary" plain :loading="aiGenerating" @click="autoGenerateProfile">
                  AI 自动生成简介
                </el-button>
                <span class="hr-ai-profile-tip">上传简历后自动识别并生成重要技能简介</span>
              </div>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </VnetPage>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from "element-plus";
import { Delete, Download, Plus, Search, Upload } from "@element-plus/icons-vue";
import VnetPage from "../../components/vnet/VnetPage.vue";
import { useHrStore, type HrEmployee } from "../../stores/hr";
import { useAuthStore } from "../../stores/auth";
import { avatarColor, downloadText, formatMoney, isManager, managerTag, parseExcelOrCsvFile, readFileAsText } from "./hr-utils";

const hrStore = useHrStore();
const authStore = useAuthStore();
const positionFilter = ref("");
const genderFilter = ref("");
const statusFilter = ref("");
const buildingFilter = ref("");
const companyFilter = ref("");
const managerOnly = ref(false);
const nameKeyword = ref("");
const feishuFilter = ref("");
const importInput = ref<HTMLInputElement>();
const resumeInput = ref<HTMLInputElement>();
const credentialInput = ref<HTMLInputElement>();
const dialogVisible = ref(false);
const formRef = ref<FormInstance>();
const editingId = ref<number | null>(null);
const resumeData = ref("");
const resumeName = ref("");
const resumeTextCache = ref("");
const aiGenerating = ref(false);

const buildingLeaders = [
  { building: "百度M1", leaders: "张耀" },
  { building: "人寿", leaders: "陈瑶瑶" },
  { building: "B28", leaders: "张乐斌、乔涛、郭鑫存" },
  { building: "M3", leaders: "张正勤" },
  { building: "M5", leaders: "何金强、段宝光" },
  { building: "M6", leaders: "赵新、马金礼" },
  { building: "M6V&M5二三期", leaders: "张士令、刘燚" }
];

const workPositionOptions = [
  "交付工程师",
  "值班员",
  "运维主管",
  "消防工程师",
  "消防工程师（助理）",
  "电气工程师",
  "电气工程师（助理）",
  "交付值班员",
  "暖通工程师",
  "暖通工程师（助理）",
  "弱电工程师",
  "值班长",
  "综合岗",
  "前台",
  "交付主管"
];
const credentialData = ref("");
const credentialName = ref("");

const form = reactive({
  name: "",
  gender: "男",
  position: "",
  company: "",
  personAttribute: "交付",
  workAttribute: "5*8白班",
  phone: "",
  email: "",
  joinDate: new Date().toISOString().slice(0, 10),
  leaveDate: "",
  region: "华北一",
  room: "酒仙桥",
  building: "M6",
  status: "在职",
  salary: 8000,
  aiProfile: "",
  feishuUserId: "",
  feishuOpenId: "",
  feishuUnionId: ""
});

const rules: FormRules = {
  name: [{ required: true, message: "请输入姓名", trigger: "blur" }]
};

const totalEmployees = computed(() => visibleEmployees.value.length);
const showPersonnelUpdateReminder = computed(() => new Date().getDate() <= 10);
const visibleUpdateLogs = computed(() =>
  hrStore.monthUpdateLogs.filter((item) => !isFloorLeader.value || leaderBuildings.value.includes(item.building || ""))
);
const lateUpdateCount = computed(() => visibleUpdateLogs.value.filter((item) => item.late).length);
const allBuildingOptions = ["M6", "M3", "M5", "B28", "M6V&M5二三期", "百度M1", "中化雄安", "人寿"];
const allEmployees = computed(() => hrStore.employees);

const leaderBuildings = computed(() => {
  const name = authStore.realName.trim();
  if (!name) return [];
  return buildingLeaders
    .filter((item) => item.leaders.split("、").map((leader) => leader.trim()).includes(name))
    .map((item) => item.building);
});
const isFloorLeader = computed(() =>
  !authStore.hasAnyRole(["SUPER_ADMIN", "ADMIN", "SYS_ADMIN"]) && authStore.hasAnyRole(["FLOOR_LEADER"])
);

const visibleEmployees = computed(() => {
  const buildings = leaderBuildings.value;
  const employees = isFloorLeader.value
    ? allEmployees.value.filter((emp) => buildings.includes(emp.building || ""))
    : allEmployees.value;
  return employees.map((emp, index) => ({ ...emp, deptNo: index + 1 }));
});

const positionOptions = computed(() => Array.from(new Set(visibleEmployees.value.map((item) => item.position).filter(Boolean))));
const buildingOptions = computed(() => isFloorLeader.value ? leaderBuildings.value : Array.from(new Set(visibleEmployees.value.map((item) => item.building).filter(Boolean))));
const editableBuildingOptions = computed(() => isFloorLeader.value ? leaderBuildings.value : allBuildingOptions);
const companyOptions = computed(() => Array.from(new Set(visibleEmployees.value.map((item) => item.company).filter(Boolean))));

const filteredEmployees = computed(() => {
  return visibleEmployees.value.filter((emp) => {
    if (nameKeyword.value.trim() && !(emp.name || "").toLowerCase().includes(nameKeyword.value.trim().toLowerCase())) return false;
    if (feishuFilter.value === "bound" && !(emp.feishuOpenId || emp.feishuUserId)) return false;
    if (feishuFilter.value === "unbound" && (emp.feishuOpenId || emp.feishuUserId)) return false;
    if (positionFilter.value && emp.position !== positionFilter.value) return false;
    if (genderFilter.value && emp.gender !== genderFilter.value) return false;
    if (statusFilter.value && emp.status !== statusFilter.value) return false;
    if (buildingFilter.value && emp.building !== buildingFilter.value) return false;
    if (companyFilter.value && emp.company !== companyFilter.value) return false;
    if (managerOnly.value && !isManager(emp.position)) return false;
    return true;
  });
});

const managerCount = computed(() => filteredEmployees.value.filter((item) => isManager(item.position)).length);
const activeCount = computed(() => filteredEmployees.value.filter((item) => item.status !== "离职").length);
const offCount = computed(() => filteredEmployees.value.filter((item) => item.status === "离职").length);


function feishuBindTitle(row: HrEmployee): string {
  return "open_id: " + (row.feishuOpenId || "-") + " / user_id: " + (row.feishuUserId || "-");
}

function rowClassName({ row }: { row: HrEmployee }): string {
  const tag = managerTag(row.position);
  return tag ? "hr-manager-row" : "";
}

function rowStyle({ row }: { row: HrEmployee }): Record<string, string> {
  const tag = managerTag(row.position);
  return tag ? { background: `${tag.color}14`, borderLeft: `3px solid ${tag.color}` } : {};
}

function nameStyle(row: HrEmployee): Record<string, string> {
  const tag = managerTag(row.position);
  return tag ? { color: tag.color, fontWeight: "700" } : {};
}

function tagOf(position?: string) {
  return managerTag(position);
}

function openDialog(emp?: HrEmployee): void {
  if (isFloorLeader.value && leaderBuildings.value.length === 0) {
    ElMessage.warning("当前账号未配置负责楼栋，请联系管理员");
    return;
  }
  if (isFloorLeader.value && emp && !leaderBuildings.value.includes(emp.building || "")) {
    ElMessage.warning("只能维护本楼栋人员");
    return;
  }
  editingId.value = emp?.id ?? null;
  resumeData.value = emp?.resumeData || "";
  resumeName.value = emp?.resumeName || "";
  credentialData.value = emp?.credentialData || "";
  credentialName.value = emp?.credentialName || "";
  Object.assign(form, {
    name: emp?.name || "",
    gender: emp?.gender || "男",
    position: emp?.position || "",
    company: emp?.company || "",
    personAttribute: emp?.personAttribute || "交付",
    workAttribute: emp?.workAttribute || "5*8白班",
    phone: emp?.phone || "",
    email: emp?.email || "",
    joinDate: emp?.joinDate || new Date().toISOString().slice(0, 10),
    leaveDate: emp?.leaveDate || "",
    region: emp?.region || "华北一",
    room: emp?.room || "酒仙桥",
    building: emp?.building || leaderBuildings.value[0] || "M6",
    status: emp?.status === "离职" || emp?.status === "离开" ? "离职" : "在职",
    salary: emp ? hrStore.salaryFor(emp) : 8000,
    aiProfile: emp?.aiProfile || "",
    feishuUserId: emp?.feishuUserId || "",
    feishuOpenId: emp?.feishuOpenId || "",
    feishuUnionId: emp?.feishuUnionId || ""
  });
  dialogVisible.value = true;
}

async function save(): Promise<void> {
  if (isFloorLeader.value && (leaderBuildings.value.length === 0 || !leaderBuildings.value.includes(form.building))) {
    ElMessage.warning("只能维护本楼栋人员");
    return;
  }
  if (!formRef.value) return;
  const valid = await formRef.value.validate().catch(() => false);
  if (!valid) return;
  const payload: Partial<HrEmployee> = {
    ...form,
    status: form.status === "离职" ? "离职" : "在职",
    resumeData: resumeData.value,
    resumeName: resumeName.value,
    credentialData: credentialData.value,
    credentialName: credentialName.value
  };
  if (editingId.value) {
    payload.salary = Number(form.salary) || 0;
  } else {
    delete payload.salary;
  }
  if (editingId.value) {
    const target = hrStore.employees.find((item) => item.id === editingId.value);
    if (isFloorLeader.value && (!target || !leaderBuildings.value.includes(target.building || ""))) {
      ElMessage.warning("只能维护本楼栋人员");
      return;
    }
    hrStore.updateEmployee(editingId.value, payload);
    ElMessage.success("人员信息已更新");
  } else {
    hrStore.addEmployee(payload);
    ElMessage.success("人员添加成功");
  }
  dialogVisible.value = false;
}

async function handleResumeChange(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  if (!file) return;
  const reader = new FileReader();
  reader.onload = () => {
    resumeData.value = String(reader.result || "");
    resumeName.value = file.name;
  };
  reader.readAsDataURL(file);
  try {
    resumeTextCache.value = await readFileAsText(file);
    await autoGenerateProfile();
  } catch {
    resumeTextCache.value = "";
    ElMessage.warning("无法自动提取简历文本，可粘贴到 AI 简介后手动生成");
  }
  input.value = "";
}

async function autoGenerateProfile(): Promise<void> {
  if (!resumeTextCache.value || resumeTextCache.value.trim().length < 10) {
    ElMessage.warning("请先上传可读取文本的简历");
    return;
  }
      const config = hrStore.aiConfig;
      const mockEndpoint = `${window.location.protocol}//${window.location.hostname}:11434/v1/chat/completions`;
      const endpoint = config.model === "mock-ai" || !config.endpoint || config.endpoint.includes("localhost:11434") ? mockEndpoint : config.endpoint;
  if (!endpoint) {
    ElMessage.warning("请先在 AI 智能问答中配置 API");
    return;
  }
  aiGenerating.value = true;
  try {
    const prompt = "你是专业HR分析师，请根据简历内容生成约100字的人员简介，重点提炼重要技能、技术栈和工作背景。\n\n简历：\n" + resumeTextCache.value.slice(0, 6000);
    const headers: Record<string, string> = { "Content-Type": "application/json" };
    if (config.apiKey) headers.Authorization = `Bearer ${config.apiKey}`;
    const response = await fetch(endpoint, {
      method: "POST",
      headers,
      body: JSON.stringify({
        model: config.model,
        messages: [{ role: "user", content: prompt }],
        max_tokens: 512,
        temperature: 0.7,
        stream: false
      })
    });
    if (!response.ok) {
      throw new Error(`API 请求失败 (${response.status})`);
    }
    const data = await response.json();
    const reply = data.choices?.[0]?.message?.content || data.response || "";
    if (reply) {
      form.aiProfile = reply;
      ElMessage.success("AI 简介已自动生成");
    } else {
      ElMessage.info("AI 未返回内容");
    }
  } catch (error) {
    ElMessage.error("AI 请求失败：请先在 AI 智能问答中配置可访问的 API 地址和模型后重试");
  } finally {
    aiGenerating.value = false;
  }
}

function downloadResume(): void {
  if (!resumeData.value) return;
  const link = document.createElement("a");
  link.href = resumeData.value;
  link.download = resumeName.value || "resume";
  link.click();
}

function clearResume(): void {
  resumeData.value = "";
  resumeName.value = "";
}

function handleCredentialChange(event: Event): void {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  if (!file) return;
  const reader = new FileReader();
  reader.onload = () => {
    credentialData.value = String(reader.result || "");
    credentialName.value = file.name;
  };
  reader.readAsDataURL(file);
  input.value = "";
}

function downloadCredential(): void {
  if (!credentialData.value) return;
  const link = document.createElement("a");
  link.href = credentialData.value;
  link.download = credentialName.value || "credential";
  link.click();
}

function clearCredential(): void {
  credentialData.value = "";
  credentialName.value = "";
}

async function remove(emp: HrEmployee): Promise<void> {
  if (isFloorLeader.value && (leaderBuildings.value.length === 0 || !leaderBuildings.value.includes(emp.building || ""))) {
    ElMessage.warning("只能维护本楼栋人员");
    return;
  }
  try {
    await ElMessageBox.confirm(`确定要删除「${emp.name}」吗？相关考勤、请假和加班记录也会同步删除。`, "删除确认", {
      type: "warning"
    });
  } catch {
    return;
  }
  hrStore.deleteEmployee(emp.id);
  ElMessage.success("已删除");
}

async function clearAll(): Promise<void> {
  if (isFloorLeader.value) {
    ElMessage.warning("楼长不能清空全部人员，请逐条维护本楼栋人员");
    return;
  }
  try {
    await ElMessageBox.confirm("确定清空全部人员信息？关联的考勤、请假、加班和绩效记录也会一并清空。", "清空确认", {
      type: "warning"
    });
  } catch {
    return;
  }
  hrStore.clearEmployees();
  ElMessage.success("人员信息已清空");
}
function downloadTemplate(): void {
  const text =
    "姓名,性别,工作岗位,人员性质,人员属性,工作属性,月薪,手机,邮箱,区域,机房,楼栋,状态,入职日期,离职日期\n" +
    "示例员工,男,交付工程师,420,交付,5*8白班,10000,13800000010,demo.user014@example.invalid,华北一,酒仙桥,M6,在职,2026-01-01,\n";
  downloadText("人员批量导入模板.csv", `\uFEFF${text}`, "text/csv;charset=utf-8");
}

async function handleImport(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  if (!file) return;
  if (isFloorLeader.value && leaderBuildings.value.length === 0) {
    ElMessage.warning("当前账号未配置负责楼栋，无法导入人员");
    input.value = "";
    return;
  }
  let rows: string[][];
  try {
    rows = await parseExcelOrCsvFile(file);
  } catch (error) {
    ElMessage.error(`文件解析失败：${error instanceof Error ? error.message : String(error)}`);
    input.value = "";
    return;
  }
  if (rows.length < 2) {
    ElMessage.warning("CSV 数据不足，第一行为表头，后续每行：姓名,性别,工作岗位,人员性质,人员属性,工作属性,月薪,手机,邮箱,区域,机房,楼栋,状态,入职日期,离职日期");
    input.value = "";
    return;
  }
  let added = 0;
  let skipped = 0;
  for (let i = 1; i < rows.length; i++) {
    const cols = rows[i];
    const name = (cols[0] || "").trim();
    if (!name) continue;
    const importedBuilding = (cols[11] || "").trim() || leaderBuildings.value[0] || "M6";
    if (isFloorLeader.value && !leaderBuildings.value.includes(importedBuilding)) {
      skipped += 1;
      continue;
    }
    if (hrStore.employees.some((emp) => emp.name === name)) {
      skipped += 1;
      continue;
    }
    hrStore.addEmployee({
      name,
      gender: (cols[1] || "").trim() || "男",
      position: (cols[2] || "").trim(),
      company: (cols[3] || "").trim(),
      personAttribute: (cols[4] || "").trim() || "交付",
      workAttribute: (cols[5] || "").trim() || "5*8白班",
      salary: parseFloat(cols[6]) || 8000,
      phone: (cols[7] || "").trim(),
      email: (cols[8] || "").trim(),
      region: (cols[9] || "").trim() || "华北一",
      room: (cols[10] || "").trim() || "酒仙桥",
      building: (cols[11] || "").trim() || leaderBuildings.value[0] || "M6",
      status: (cols[12] || "").trim() === "离开" ? "离职" : (cols[12] || "").trim() || "在职",
      joinDate: (cols[13] || "").trim() || new Date().toISOString().slice(0, 10),
      leaveDate: (cols[14] || "").trim()
    });
    added += 1;
  }
  ElMessage.success(`导入完成：新增 ${added} 人${skipped ? `，跳过 ${skipped} 人（已存在）` : ""}`);
  input.value = "";
}

onMounted(() => {
  hrStore.load();
  hrStore.setOperator(authStore.realName);
});
</script>

<style scoped>
.hr-hidden-input {
  display: none;
}

.hr-resume-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  width: 100%;
}

.hr-resume-name {
  overflow: hidden;
  max-width: 220px;
  color: var(--app-text-muted);
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.hr-resume-empty {
  color: var(--app-text-soft);
  font-size: 13px;
}

.hr-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  padding: 12px 16px;
  border: 1px solid #bfdbfe;
  border-radius: var(--app-radius);
  background: #eff6ff;
  color: #1e3a8a;
  font-size: 13px;
  font-weight: 600;
}

.hr-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.hr-summary-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 14px 16px;
  border: 1px solid var(--app-line);
  border-radius: var(--app-radius);
  background: #ffffff;
  box-shadow: var(--app-shadow);
}

.hr-summary-item span {
  color: var(--app-text-muted);
  font-size: 12px;
}

.hr-summary-item strong {
  font-size: 24px;
  line-height: 1;
}

.hr-table-card {
  overflow: hidden;
  margin-top: 16px;
  border: 1px solid var(--app-line);
  border-radius: var(--app-radius);
  background: #ffffff;
  box-shadow: var(--app-shadow);
}

.hr-name-cell {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.hr-avatar {
  display: grid;
  flex: 0 0 30px;
  width: 30px;
  height: 30px;
  place-items: center;
  border-radius: 8px;
  color: #ffffff;
  font-size: 13px;
  font-weight: 700;
}

.hr-name-main {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.hr-name-main strong {
  overflow: hidden;
  max-width: 90px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.hr-total-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 16px 18px;
  border: 1px solid var(--app-line);
  border-radius: var(--app-radius);
  background: #ffffff;
  box-shadow: var(--app-shadow);
}

.hr-total-head strong {
  font-size: 18px;
}

.hr-total-head span {
  color: var(--app-text-muted);
  font-size: 13px;
}

.hr-room-section {
  margin-top: 16px;
}

.hr-room-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
  padding: 12px 16px;
  border-left: 4px solid var(--app-primary);
  border-radius: var(--app-radius);
  background: var(--app-primary-soft);
}

.hr-room-head strong {
  font-size: 16px;
}

.hr-room-head span {
  color: var(--app-text-muted);
  font-size: 13px;
}

.hr-dept-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  align-items: start;
}

.hr-dept-card {
  overflow: hidden;
  border: 1px solid var(--app-line);
  border-radius: var(--app-radius);
  background: #ffffff;
  box-shadow: var(--app-shadow);
}

.hr-dept-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--app-line-soft);
}

.hr-dept-head strong {
  font-size: 15px;
}

.hr-dept-head span {
  color: var(--app-text-muted);
  font-size: 12px;
}

:deep(.hr-manager-row td) {
  background: rgba(139, 92, 246, 0.08);
}

:deep(.el-table) {
  font-size: 12px;
}

:deep(.el-table .el-table__cell) {
  padding: 6px 8px;
}

:deep(.el-table th.el-table__cell) {
  padding: 8px;
}

@media (max-width: 1100px) {
  .hr-dept-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .hr-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
.hr-ai-profile-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
}

.hr-ai-profile-tip {
  color: var(--app-text-soft);
  font-size: 12px;
}

:deep(.vnet-page) {
  background:
    linear-gradient(rgba(13, 32, 56, 0.55), rgba(13, 32, 56, 0.55)),
    url("/hr-bg.png") center / cover no-repeat;
  padding: 16px;
  border-radius: 12px;
  min-height: calc(100vh - 120px);
}


.hr-update-reminder { margin-bottom: 16px; }
</style>
