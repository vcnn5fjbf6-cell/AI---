<template>
  <VnetPage title="数据库管理">
    <template #actions>
      <el-button :loading="tableLoading" @click="loadTables">刷新表</el-button>
      <el-button v-perm="'database_admin:create'" type="primary" :disabled="!currentTableName" @click="openCreate">新增记录</el-button>
    </template>

    <div class="database-layout">
      <VnetPanel title="数据表">
        <div class="table-search">
          <el-input
            v-model="tableKeyword"
            clearable
            placeholder="搜索表名 / 备注"
            @keyup.enter="loadTables"
            @clear="loadTables"
          />
          <el-button type="primary" @click="loadTables">查询</el-button>
        </div>

        <el-scrollbar class="table-list" v-loading="tableLoading">
          <button
            v-for="table in tables"
            :key="table.tableName"
            class="table-list-item"
            :class="{ active: table.tableName === currentTableName }"
            type="button"
            @click="selectTable(table.tableName)"
          >
            <span>{{ tableDisplayName(table) }}</span>
            <small>{{ table.tableName }}</small>
            <em>{{ formatNumber(table.tableRows) }} 行</em>
          </button>
          <el-empty v-if="!tableLoading && tables.length === 0" description="暂无数据表" />
        </el-scrollbar>
      </VnetPanel>

      <div class="database-main">
        <VnetPanel :title="currentTableName ? `表结构：${currentTableTitle}` : '表结构'">
          <template v-if="detail">
            <el-descriptions :column="4" border class="table-meta">
              <el-descriptions-item label="表名">{{ detail.table.tableName }}</el-descriptions-item>
              <el-descriptions-item label="中文名称">{{ tableDisplayName(detail.table) }}</el-descriptions-item>
              <el-descriptions-item label="引擎">{{ detail.table.engine || "-" }}</el-descriptions-item>
              <el-descriptions-item label="排序规则">{{ formatCollation(detail.table.tableCollation) }}</el-descriptions-item>
              <el-descriptions-item label="估算行数">{{ formatNumber(detail.table.tableRows) }}</el-descriptions-item>
              <el-descriptions-item label="数据大小">{{ formatBytes(detail.table.dataLength) }}</el-descriptions-item>
              <el-descriptions-item label="索引大小">{{ formatBytes(detail.table.indexLength) }}</el-descriptions-item>
              <el-descriptions-item label="创建时间">{{ detail.table.createTime || "-" }}</el-descriptions-item>
              <el-descriptions-item label="更新时间">{{ detail.table.updateTime || "-" }}</el-descriptions-item>
            </el-descriptions>

            <el-alert
              v-if="!canEditRows"
              class="primary-key-alert"
              title="该表没有主键，当前页面不开放编辑和删除。"
              type="warning"
              show-icon
              :closable="false"
            />

            <el-table v-loading="detailLoading" :data="columns" stripe class="column-table">
              <el-table-column prop="ordinalPosition" label="#" width="70" />
              <el-table-column prop="columnName" label="字段" min-width="220">
                <template #default="{ row }">
                  <div class="column-name">
                    <strong>{{ columnDisplayName(row) }}</strong>
                    <span>{{ row.columnName }}</span>
                    <el-tag v-if="isPrimaryKey(row.columnName)" size="small" type="warning" effect="light">主键</el-tag>
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="类型" min-width="180">
                <template #default="{ row }">{{ formatColumnType(row) }}</template>
              </el-table-column>
              <el-table-column label="允许空" width="90">
                <template #default="{ row }">
                  <el-tag :type="row.nullable === 'YES' ? 'info' : 'danger'" effect="light">
                    {{ row.nullable === "YES" ? "是" : "否" }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="defaultValue" label="默认值" min-width="140">
                <template #default="{ row }">{{ formatDefaultValue(row.defaultValue) }}</template>
              </el-table-column>
              <el-table-column label="索引" min-width="120">
                <template #default="{ row }">{{ formatColumnKey(row.columnKey) }}</template>
              </el-table-column>
              <el-table-column label="额外属性" min-width="160">
                <template #default="{ row }">{{ formatExtra(row.extra) }}</template>
              </el-table-column>
              <el-table-column label="备注" min-width="220">
                <template #default="{ row }">{{ cleanText(row.comment, "-") }}</template>
              </el-table-column>
            </el-table>
          </template>
          <el-empty v-else description="请选择数据表" />
        </VnetPanel>

        <VnetPanel :title="currentTableName ? `表数据：${currentTableTitle}` : '表数据'">
          <template #actions>
            <el-button :disabled="!currentTableName" :loading="rowLoading" @click="loadRows">刷新数据</el-button>
          </template>

          <div class="row-toolbar">
            <el-input
              v-model="rowKeyword"
              clearable
              placeholder="按文本字段搜索"
              @keyup.enter="handleRowSearch"
              @clear="handleRowSearch"
            />
            <el-button type="primary" :disabled="!currentTableName" @click="handleRowSearch">查询</el-button>
            <el-button :disabled="!currentTableName" @click="resetRowSearch">重置</el-button>
          </div>

          <el-table v-loading="rowLoading" :data="rows" stripe border class="row-table" max-height="560">
            <el-table-column
              v-for="column in columns"
              :key="column.columnName"
              :prop="column.columnName"
              :label="columnDisplayName(column)"
              min-width="160"
              show-overflow-tooltip
            >
              <template #default="{ row }">
                <span :class="{ 'null-cell': isNullValue(row[column.columnName]) }">
                  {{ formatCell(row[column.columnName]) }}
                </span>
              </template>
            </el-table-column>
            <el-table-column v-if="currentTableName" label="操作" width="150" fixed="right">
              <template #default="{ row }">
                <el-button v-perm="'database_admin:update'" link type="primary" :disabled="!canEditRows" @click="openEdit(row)">编辑</el-button>
                <el-button v-perm="'database_admin:delete'" link type="danger" :disabled="!canEditRows" @click="deleteRow(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>

          <div class="pager-box">
            <el-pagination
              background
              layout="total, sizes, prev, pager, next"
              :total="total"
              :current-page="rowQuery.pageNo"
              :page-size="rowQuery.pageSize"
              :page-sizes="[10, 20, 50, 100]"
              @current-change="handlePageChange"
              @size-change="handleSizeChange"
            />
          </div>
        </VnetPanel>
      </div>
    </div>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="860px" destroy-on-close>
      <el-form label-width="150px" class="row-form">
        <el-row :gutter="14">
          <el-col v-for="column in dialogColumns" :key="column.columnName" :span="isLongColumn(column) ? 24 : 12">
            <el-form-item :label="columnDisplayName(column)">
              <el-input
                v-model="formValues[column.columnName]"
                :type="isLongColumn(column) ? 'textarea' : 'text'"
                :autosize="{ minRows: 2, maxRows: 5 }"
                :disabled="dialogMode === 'edit' && isPrimaryKey(column.columnName)"
                clearable
              />
              <div class="field-meta">
                <span>{{ column.columnName }}</span>
                <span>{{ formatColumnType(column) }}</span>
                <span>{{ column.nullable === "YES" ? "可空" : "必填" }}</span>
                <span v-if="cleanText(column.comment, '')">{{ cleanText(column.comment, '') }}</span>
              </div>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="submitRow">保存</el-button>
      </template>
    </el-dialog>
  </VnetPage>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import VnetPage from "../../components/vnet/VnetPage.vue";
import VnetPanel from "../../components/vnet/VnetPanel.vue";
import {
  createDatabaseRowApi,
  deleteDatabaseRowApi,
  getDatabaseRowsApi,
  getDatabaseTableDetailApi,
  getDatabaseTablesApi,
  updateDatabaseRowApi
} from "../../api/platform";
import type { DatabaseColumnVO, DatabaseRow, DatabaseTableDetailVO, DatabaseTableVO } from "../../types/platform";

const tables = ref<DatabaseTableVO[]>([]);
const detail = ref<DatabaseTableDetailVO | null>(null);
const rows = ref<DatabaseRow[]>([]);
const total = ref(0);
const currentTableName = ref("");
const tableKeyword = ref("");
const rowKeyword = ref("");
const tableLoading = ref(false);
const detailLoading = ref(false);
const rowLoading = ref(false);
const submitLoading = ref(false);
const dialogVisible = ref(false);
const dialogMode = ref<"create" | "edit">("create");
const currentRow = ref<DatabaseRow | null>(null);
const originalNullColumns = ref<Set<string>>(new Set());
const formValues = reactive<Record<string, string>>({});

const rowQuery = reactive({
  pageNo: 1,
  pageSize: 20
});

const columns = computed(() => detail.value?.columns ?? []);
const primaryKeys = computed(() => detail.value?.primaryKeys ?? []);
const canEditRows = computed(() => primaryKeys.value.length > 0);
const currentTableTitle = computed(() => (detail.value ? tableDisplayName(detail.value.table) : currentTableName.value));
const dialogTitle = computed(() => (dialogMode.value === "create" ? "新增记录" : "编辑记录"));
const dialogColumns = computed(() =>
  columns.value.filter((column) => !isGeneratedColumn(column) && !(dialogMode.value === "create" && isAutoIncrementColumn(column)))
);

const tableNameMap: Record<string, string> = {
  approval_instance: "审批实例",
  approval_record: "审批记录",
  approval_task: "审批任务",
  feishu_approval_card_notification: "飞书审批卡片通知",
  bi_dashboard: "采购看板布局",
  org_scope: "组织范围",
  org_subject: "组织主体",
  platform_permission_request: "平台权限申请",
  platform_unit: "平台单元",
  platform_unit_app_scope: "平台单元应用范围",
  platform_user_app_access: "用户系统入口",
  procurement_after_sale: "采购售后",
  procurement_ai_candidate: "AI采购候选项",
  procurement_ai_session: "AI采购会话",
  procurement_approval_level: "采购审批节点",
  procurement_approval_level_user: "采购审批节点候选人",
  procurement_approval_policy: "采购审批策略",
  procurement_approval_route: "部门审批路线",
  procurement_department_limit: "部门采购限额",
  procurement_message_read: "采购消息已读状态",
  procurement_setting: "采购设置",
  purchase_request: "采购申请",
  purchase_request_item: "采购申请明细",
  purchase_request_no_sequence: "采购单号序列",
  supplier: "供应商",
  supplier_qualification: "供应商资质",
  supplier_quote: "供应商报价",
  supplier_quote_item: "供应商报价明细",
  sys_permission: "系统权限",
  sys_region: "区域",
  sys_role: "角色",
  sys_role_permission: "角色权限",
  sys_user: "用户",
  sys_user_password_history: "用户密码历史",
  sys_user_role: "用户角色",
  system_error_log: "系统错误台账",
  workbench_copy: "工作台抄送",
  workbench_follow: "工作台关注",
  workbench_notice: "工作台公告"
};

const columnNameMap: Record<string, string> = {
  action_type: "操作类型",
  address: "地址",
  applicant_name: "申请人",
  applicant_user_id: "申请人ID",
  app_code: "系统入口编码",
  approval_status: "审批状态",
  approval_task_id: "审批任务ID",
  approver_user_id: "审批人ID",
  recipient_user_id: "卡片接收人ID",
  acted_at: "处理时间",
  awarded: "中标标记",
  biz_id: "业务ID",
  biz_type: "业务类型",
  blacklist_flag: "黑名单标记",
  budget_code: "预算编码",
  building_name: "楼栋",
  candidate_user_id: "候选人ID",
  card_token: "卡片校验令牌",
  client_ip: "客户端IP",
  comment: "意见",
  created_at: "创建时间",
  created_by: "创建人",
  currency: "币种",
  current_approver_id: "当前审批人ID",
  current_node_no: "当前节点序号",
  data_scope_type: "数据范围",
  dept_code: "部门编码",
  dept_name: "部门名称",
  description: "说明",
  email: "邮箱",
  employee_no: "工号",
  enabled: "启用状态",
  error_code: "错误编码",
  error_message: "错误内容",
  exception_class: "异常类",
  feishu_open_id: "飞书 open_id",
  feishu_union_id: "飞书 union_id",
  feishu_user_id: "飞书 user_id",
  fixed_at: "处理时间",
  fixed_by: "处理人",
  fix_remark: "处理说明",
  function_name: "功能名称",
  id: "主键ID",
  instance_id: "实例ID",
  instance_no: "实例编号",
  is_deleted: "删除标记",
  item_code: "商品编码",
  item_name: "商品名称",
  last_login_at: "最后登录时间",
  last_login_ip: "最后登录IP",
  last_error: "最后错误",
  lead_time_days: "交期天数",
  line_amount: "行金额",
  line_no: "行号",
  mobile: "手机号",
  module_code: "模块编码",
  need_date: "需求日期",
  password_changed_at: "密码修改时间",
  password_hash: "密码哈希",
  path: "路径",
  perm_code: "权限编码",
  perm_name: "权限名称",
  perm_type: "权限类型",
  purchase_request_id: "采购申请ID",
  purchaser_user_id: "采购员ID",
  qualification_no: "资质编号",
  qualification_type: "资质类型",
  qty: "数量",
  receive_id: "飞书接收ID",
  receive_id_type: "飞书接收ID类型",
  quote_id: "报价ID",
  quote_no: "报价单号",
  real_name: "姓名",
  region_id: "区域ID",
  region_name: "区域名称",
  remark: "备注",
  request_method: "请求方法",
  request_uri: "请求路径",
  req_no: "申请单号",
  req_reason: "申请原因",
  req_title: "申请标题",
  req_type: "申请类型",
  role_code: "角色编码",
  role_id: "角色ID",
  role_name: "角色名称",
  role_type: "角色类型",
  session_secret: "会话密钥",
  sent_at: "发送时间",
  sort_no: "排序",
  stack_trace: "异常堆栈",
  status: "状态",
  submit_count: "提交次数",
  supplier_code: "供应商编码",
  supplier_id: "供应商ID",
  supplier_name: "供应商名称",
  supplier_short_name: "供应商简称",
  supplier_type: "供应商类型",
  tax_no: "税号",
  total_amount: "总金额",
  unit: "单位",
  unit_price: "单价",
  updated_at: "更新时间",
  updated_by: "更新人",
  urgency_level: "紧急程度",
  user_id: "用户ID",
  user_type: "用户类型",
  username: "账号",
  version: "版本号"
};

const wordMap: Record<string, string> = {
  action: "操作",
  address: "地址",
  admin: "管理员",
  ai: "AI",
  amount: "金额",
  app: "系统入口",
  approval: "审批",
  approved: "已审批",
  applicant: "申请人",
  at: "时间",
  award: "中标",
  awarded: "中标",
  blacklist: "黑名单",
  budget: "预算",
  building: "楼栋",
  candidate: "候选",
  city: "城市",
  code: "编码",
  comment: "备注",
  confirm: "确认",
  copy: "抄送",
  count: "数量",
  created: "创建",
  current: "当前",
  data: "数据",
  date: "日期",
  day: "天",
  deadline: "截止",
  delete: "删除",
  dept: "部门",
  detail: "明细",
  email: "邮箱",
  employee: "员工",
  enabled: "启用",
  error: "错误",
  exception: "异常",
  file: "文件",
  flag: "标记",
  id: "ID",
  instance: "实例",
  ip: "IP",
  is: "是否",
  item: "商品",
  key: "键",
  last: "最后",
  lead: "交期",
  level: "节点",
  line: "行",
  log: "日志",
  message: "消息",
  method: "方法",
  mobile: "手机号",
  module: "模块",
  name: "名称",
  no: "编号",
  node: "节点",
  password: "密码",
  permission: "权限",
  phone: "电话",
  policy: "策略",
  price: "价格",
  procurement: "采购",
  purchase: "采购",
  purchaser: "采购员",
  qty: "数量",
  qualification: "资质",
  quote: "报价",
  real: "真实",
  reason: "原因",
  record: "记录",
  region: "区域",
  remark: "备注",
  request: "申请",
  role: "角色",
  route: "路线",
  scope: "范围",
  secret: "密钥",
  session: "会话",
  sort: "排序",
  status: "状态",
  supplier: "供应商",
  tax: "税",
  time: "时间",
  title: "标题",
  total: "总计",
  type: "类型",
  unit: "单位",
  updated: "更新",
  uri: "路径",
  user: "用户",
  username: "账号",
  version: "版本"
};

onMounted(() => {
  void loadTables();
});

function tableDisplayName(table: DatabaseTableVO): string {
  return cleanText(table.tableComment, tableNameMap[table.tableName] || generateName(table.tableName));
}

function columnDisplayName(column: DatabaseColumnVO): string {
  return cleanText(column.comment, columnNameMap[column.columnName] || generateName(column.columnName));
}

function cleanText(value: string | null | undefined, fallback: string): string {
  const text = value?.trim() ?? "";
  if (!text || isLikelyGarbled(text)) {
    return fallback;
  }
  return text;
}

function isLikelyGarbled(value: string): boolean {
  return /[\uFFFD\u00C3\u00C2]|[\u00E7\u00E6\u00E5]{2,}|(?:\u9422|\u9359|\u6D60|\u7EE0|\u59AF|\u9418|\u7481|\u6DC7|\u5BEE|\u93B6|\u93C9|\u95BF|\u95B2|\u5BB8|\u7F01|\u7039|\u93C3|\u935A|\u934F|\u941E|\u8E47|\u6690|\u60E7|\u56EA|\u5598|\u55D8)/.test(value);
}

function generateName(name: string): string {
  const words = name
    .split("_")
    .filter(Boolean)
    .map((word) => wordMap[word.toLowerCase()] || word.toUpperCase());
  return words.length > 0 ? words.join("") : name;
}

function formatCollation(value: string | null | undefined): string {
  if (!value) {
    return "-";
  }
  if (value.startsWith("utf8mb4")) {
    return `${value}（中文/多语言字符集）`;
  }
  if (value.startsWith("utf8")) {
    return `${value}（UTF-8 字符集）`;
  }
  return value;
}

function formatColumnType(column: DatabaseColumnVO): string {
  const dataType = column.dataType?.toLowerCase() ?? "";
  const rawType = column.columnType?.toUpperCase() || column.dataType?.toUpperCase() || "-";
  const typeMap: Record<string, string> = {
    bigint: "大整数",
    binary: "二进制",
    bit: "位",
    blob: "二进制大对象",
    char: "定长字符串",
    date: "日期",
    datetime: "日期时间",
    decimal: "小数",
    double: "双精度小数",
    enum: "枚举",
    float: "浮点数",
    int: "整数",
    integer: "整数",
    json: "JSON 数据",
    longblob: "大二进制对象",
    longtext: "超长文本",
    mediumblob: "中等二进制对象",
    mediumint: "中等整数",
    mediumtext: "中等文本",
    smallint: "小整数",
    text: "长文本",
    time: "时间",
    timestamp: "时间戳",
    tinyblob: "短二进制对象",
    tinyint: "短整数",
    tinytext: "短文本",
    varbinary: "可变二进制",
    varchar: "字符串",
    year: "年份"
  };
  return `${typeMap[dataType] || "字段"}（${rawType}）`;
}

function formatColumnKey(value: string | null | undefined): string {
  if (value === "PRI") {
    return "主键";
  }
  if (value === "UNI") {
    return "唯一索引";
  }
  if (value === "MUL") {
    return "普通索引";
  }
  return "-";
}

function formatDefaultValue(value: string | null | undefined): string {
  if (value === null || value === undefined) {
    return "-";
  }
  const normalized = String(value).trim();
  if (!normalized) {
    return "空字符串";
  }
  if (normalized.toUpperCase() === "CURRENT_TIMESTAMP") {
    return "当前时间";
  }
  if (normalized.toUpperCase() === "NULL") {
    return "空值";
  }
  return normalized;
}

function formatExtra(value: string | null | undefined): string {
  if (!value) {
    return "-";
  }
  const lower = value.toLowerCase();
  const labels: string[] = [];
  if (lower.includes("auto_increment")) {
    labels.push("自增");
  }
  if (lower.includes("on update current_timestamp")) {
    labels.push("更新时自动刷新时间");
  }
  if (lower.includes("default_generated")) {
    labels.push("默认值自动生成");
  }
  if (lower.includes("generated")) {
    labels.push("生成列");
  }
  return labels.length > 0 ? labels.join("、") : value;
}

async function loadTables(): Promise<void> {
  tableLoading.value = true;
  try {
    tables.value = await getDatabaseTablesApi(tableKeyword.value.trim());
    const stillExists = tables.value.some((table) => table.tableName === currentTableName.value);
    if (!stillExists) {
      const firstTable = tables.value[0]?.tableName ?? "";
      if (firstTable) {
        await selectTable(firstTable);
      } else {
        currentTableName.value = "";
        detail.value = null;
        rows.value = [];
        total.value = 0;
      }
    }
  } finally {
    tableLoading.value = false;
  }
}

async function selectTable(tableName: string): Promise<void> {
  if (currentTableName.value === tableName && detail.value) {
    return;
  }
  currentTableName.value = tableName;
  rowKeyword.value = "";
  rowQuery.pageNo = 1;
  await loadDetail();
  await loadRows();
}

async function loadDetail(): Promise<void> {
  if (!currentTableName.value) {
    detail.value = null;
    return;
  }
  detailLoading.value = true;
  try {
    detail.value = await getDatabaseTableDetailApi(currentTableName.value);
  } finally {
    detailLoading.value = false;
  }
}

async function loadRows(): Promise<void> {
  if (!currentTableName.value) {
    rows.value = [];
    total.value = 0;
    return;
  }
  rowLoading.value = true;
  try {
    const page = await getDatabaseRowsApi(currentTableName.value, {
      pageNo: rowQuery.pageNo,
      pageSize: rowQuery.pageSize,
      keyword: rowKeyword.value.trim() || undefined
    });
    rows.value = page.records;
    total.value = page.total;
  } finally {
    rowLoading.value = false;
  }
}

function handleRowSearch(): void {
  rowQuery.pageNo = 1;
  void loadRows();
}

function resetRowSearch(): void {
  rowKeyword.value = "";
  handleRowSearch();
}

function handlePageChange(pageNo: number): void {
  rowQuery.pageNo = pageNo;
  void loadRows();
}

function handleSizeChange(pageSize: number): void {
  rowQuery.pageSize = pageSize;
  rowQuery.pageNo = 1;
  void loadRows();
}

function openCreate(): void {
  if (!currentTableName.value) {
    return;
  }
  dialogMode.value = "create";
  currentRow.value = null;
  originalNullColumns.value = new Set();
  resetFormValues();
  for (const column of dialogColumns.value) {
    formValues[column.columnName] = "";
  }
  dialogVisible.value = true;
}

function openEdit(row: DatabaseRow): void {
  if (!canEditRows.value) {
    return;
  }
  dialogMode.value = "edit";
  currentRow.value = row;
  originalNullColumns.value = new Set(
    columns.value
      .filter((column) => isNullValue(row[column.columnName]))
      .map((column) => column.columnName)
  );
  resetFormValues();
  for (const column of dialogColumns.value) {
    formValues[column.columnName] = stringifyEditValue(row[column.columnName]);
  }
  dialogVisible.value = true;
}

async function submitRow(): Promise<void> {
  if (!currentTableName.value) {
    return;
  }
  submitLoading.value = true;
  try {
    if (dialogMode.value === "create") {
      await createDatabaseRowApi(currentTableName.value, { values: buildCreateValues() });
      ElMessage.success("新增记录成功");
      await loadRows();
      await loadTables();
    } else if (currentRow.value) {
      await updateDatabaseRowApi(currentTableName.value, {
        keyValues: buildKeyValues(currentRow.value),
        values: buildUpdateValues()
      });
      ElMessage.success("记录已保存");
      await loadRows();
    }
    dialogVisible.value = false;
  } finally {
    submitLoading.value = false;
  }
}

async function deleteRow(row: DatabaseRow): Promise<void> {
  if (!canEditRows.value || !currentTableName.value) {
    return;
  }
  await ElMessageBox.confirm("删除后不可恢复，是否继续？", "删除记录", { type: "warning" });
  await deleteDatabaseRowApi(currentTableName.value, { keyValues: buildKeyValues(row) });
  ElMessage.success("记录已删除");
  await loadRows();
  await loadTables();
}

function buildCreateValues(): DatabaseRow {
  const values: DatabaseRow = {};
  for (const column of dialogColumns.value) {
    const value = formValues[column.columnName] ?? "";
    if (value === "" && column.nullable === "YES") {
      continue;
    }
    values[column.columnName] = value;
  }
  return values;
}

function buildUpdateValues(): DatabaseRow {
  const values: DatabaseRow = {};
  for (const column of dialogColumns.value) {
    if (isPrimaryKey(column.columnName)) {
      continue;
    }
    const value = formValues[column.columnName] ?? "";
    values[column.columnName] = originalNullColumns.value.has(column.columnName) && value === "" ? null : value;
  }
  return values;
}

function buildKeyValues(row: DatabaseRow): DatabaseRow {
  const values: DatabaseRow = {};
  for (const key of primaryKeys.value) {
    values[key] = row[key];
  }
  return values;
}

function resetFormValues(): void {
  Object.keys(formValues).forEach((key) => {
    delete formValues[key];
  });
}

function isPrimaryKey(columnName: string): boolean {
  return primaryKeys.value.includes(columnName);
}

function isGeneratedColumn(column: DatabaseColumnVO): boolean {
  return column.extra?.toLowerCase().includes("generated") ?? false;
}

function isAutoIncrementColumn(column: DatabaseColumnVO): boolean {
  return column.extra?.toLowerCase().includes("auto_increment") ?? false;
}

function isLongColumn(column: DatabaseColumnVO): boolean {
  const dataType = column.dataType?.toLowerCase() ?? "";
  return dataType.includes("text") || dataType === "json" || dataType === "longblob";
}

function isNullValue(value: unknown): boolean {
  return value === null || value === undefined;
}

function stringifyEditValue(value: unknown): string {
  if (isNullValue(value)) {
    return "";
  }
  if (typeof value === "object") {
    return JSON.stringify(value);
  }
  return String(value);
}

function formatCell(value: unknown): string {
  if (isNullValue(value)) {
    return "空值";
  }
  if (typeof value === "object") {
    return JSON.stringify(value);
  }
  return String(value);
}

function formatNumber(value: number | null | undefined): string {
  if (value === null || value === undefined) {
    return "-";
  }
  return value.toLocaleString("zh-CN");
}

function formatBytes(value: number | null | undefined): string {
  if (value === null || value === undefined) {
    return "-";
  }
  if (value < 1024) {
    return `${value} B`;
  }
  if (value < 1024 * 1024) {
    return `${(value / 1024).toFixed(1)} KB`;
  }
  return `${(value / 1024 / 1024).toFixed(1)} MB`;
}
</script>

<style scoped>
.database-layout {
  display: grid;
  grid-template-columns: minmax(260px, 320px) minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}

.database-main {
  display: grid;
  gap: 16px;
  min-width: 0;
}

.table-search {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px;
  margin-bottom: 12px;
}

.table-list {
  height: 680px;
}

.table-list-item {
  width: 100%;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 4px 10px;
  padding: 10px 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  background: #ffffff;
  color: var(--app-text);
  text-align: left;
  cursor: pointer;
}

.table-list-item + .table-list-item {
  margin-top: 8px;
}

.table-list-item.active {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.table-list-item span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 700;
}

.table-list-item small {
  grid-column: 1 / -1;
  overflow: hidden;
  color: var(--app-text-soft);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.table-list-item em {
  color: var(--app-text-soft);
  font-style: normal;
  font-size: 12px;
}

.table-meta {
  margin-bottom: 14px;
}

.primary-key-alert {
  margin-bottom: 14px;
}

.column-table {
  width: 100%;
}

.column-name {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 4px 8px;
  align-items: center;
}

.column-name span {
  grid-column: 1 / -1;
  overflow: hidden;
  color: var(--app-text-soft);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.row-toolbar {
  display: grid;
  grid-template-columns: minmax(240px, 360px) auto auto;
  gap: 10px;
  margin-bottom: 14px;
}

.row-table {
  width: 100%;
}

.null-cell {
  color: var(--app-text-soft);
  font-style: italic;
}

.pager-box {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.row-form {
  max-height: 60vh;
  overflow: auto;
  padding-right: 8px;
}

.field-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 4px;
  color: var(--app-text-soft);
  font-size: 12px;
  line-height: 1.5;
}

@media (max-width: 1100px) {
  .database-layout {
    grid-template-columns: 1fr;
  }

  .table-list {
    height: 360px;
  }
}

@media (max-width: 720px) {
  .table-search,
  .row-toolbar {
    grid-template-columns: 1fr;
  }
}
</style>
