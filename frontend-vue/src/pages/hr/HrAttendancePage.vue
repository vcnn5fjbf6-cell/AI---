<template>
  <VnetPage title="考勤 / 请假 / 加班">
    <div v-if="isHrAdmin" class="hr-attendance-dashboard">
      <div class="hr-quick-actions">
        <el-button type="primary" :icon="Plus" @click="openCheckin">打卡签到</el-button>
        <el-button type="success" :icon="Plus" @click="openLeaveDialog">请假申请</el-button>
        <el-button type="warning" :icon="Plus" @click="openOvertimeDialog()">加班申请</el-button>
        <el-button :icon="Check" @click="allPresent">一键全部到岗</el-button>
      </div>
      <section class="hr-panel">
        <div class="hr-panel-head">
          <h3>加班与调休汇总</h3>
          <span>按已批准记录累计；请假按 1 天 = 8 小时折算</span>
        </div>
        <div class="hr-table-scroll">
          <el-table :data="overtimeBalanceRows" size="small" empty-text="暂无人员记录">
            <el-table-column prop="employeeName" label="员工" min-width="110" />
            <el-table-column prop="overtimeHours" label="加班总小时数" width="140">
              <template #default="{ row }">{{ row.overtimeHours }} 小时</template>
            </el-table-column>
            <el-table-column prop="leaveHours" label="请假扣减" width="120">
              <template #default="{ row }">{{ row.leaveHours }} 小时</template>
            </el-table-column>
            <el-table-column prop="availableHours" label="可调休时长" width="130">
              <template #default="{ row }">{{ row.availableHours }} 小时</template>
            </el-table-column>
          </el-table>
        </div>
      </section>
      <section class="hr-panel">
        <div class="hr-panel-head">
          <h3>请假记录</h3>
        </div>
        <div class="hr-table-scroll">
          <el-table :data="filteredLeaves" size="small" empty-text="暂无请假记录">
            <el-table-column prop="employeeName" label="员工" min-width="110" />
            <el-table-column prop="typeName" label="类型" width="90" />
            <el-table-column label="起止日期" min-width="220">
              <template #default="{ row }">{{ row.startDate }} ~ {{ row.endDate }}</template>
            </el-table-column>
            <el-table-column prop="days" label="天数" width="60" />
            <el-table-column prop="reason" label="原因" min-width="220" show-overflow-tooltip />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="leaveStatusType(row.status)" size="small">{{ leaveStatusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="140">
              <template #default="{ row }">
                <template v-if="row.status === 'pending'">
                  <el-button link type="success" @click="approveLeave(row)">批准</el-button>
                  <el-button link type="danger" @click="rejectLeave(row)">拒绝</el-button>
                </template>
                <span v-else>-</span>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </section>
      <section class="hr-panel">
        <div class="hr-panel-head">
          <h3>加班记录</h3>
        </div>
        <div class="hr-table-scroll">
          <el-table :data="overtimeRows" size="small" empty-text="暂无加班记录">
            <el-table-column prop="date" label="日期" width="120" />
            <el-table-column prop="employeeName" label="员工" min-width="110" />
            <el-table-column prop="hours" label="时长" width="80" />
            <el-table-column prop="reason" label="原因" min-width="220" show-overflow-tooltip />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="leaveStatusType(row.status)" size="small">{{ leaveStatusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="180">
              <template #default="{ row }">
                <template v-if="row.status === 'pending'">
                  <el-button link type="success" @click="approveOvertime(row)">批准</el-button>
                  <el-button link type="danger" @click="rejectOvertime(row)">拒绝</el-button>
                </template>
                <el-button link type="primary" @click="openOvertimeDialog(row)">编辑</el-button>
                <el-button link type="danger" @click="removeOvertime(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </section>
      <section class="hr-panel">
        <div class="hr-panel-head">
          <h3>考勤记录</h3>
          <div class="hr-filter-row">
            <el-date-picker v-model="attDate" type="date" value-format="YYYY-MM-DD" placeholder="按日期筛选" />
            <el-button @click="showMonthlyReport">本月统计</el-button>
          </div>
        </div>
        <div class="hr-table-scroll">
          <el-table :data="filteredAttendance" size="small" empty-text="暂无考勤记录">
            <el-table-column prop="date" label="日期" min-width="120" />
            <el-table-column prop="employeeName" label="员工" min-width="160" />
            <el-table-column prop="checkIn" label="签到" min-width="120" />
            <el-table-column prop="checkOut" label="签退" min-width="120" />
            <el-table-column label="状态" min-width="100">
              <template #default="{ row }">
                <el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" min-width="120">
              <template #default="{ row }">
                <el-button link type="danger" @click="removeAttendance(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </section>
    </div>
    <div v-else class="hr-employee-panel">
      <div class="hr-employee-head">
        <strong>考勤管理</strong>
        <span>打卡签到、请假申请、加班申请</span>
      </div>
      <div class="hr-employee-actions">
        <el-button type="primary" size="large" @click="openCheckin">打卡签到</el-button>
        <el-button type="success" size="large" @click="openLeaveDialog">请假申请</el-button>
        <el-button type="warning" size="large" @click="openOvertimeDialog()">加班申请</el-button>
      </div>
    </div>


    <el-dialog v-model="checkinVisible" title="签到" width="420px">
      <el-form label-width="90px">
        <el-form-item label="员工" required>
          <el-select v-model="checkin.employeeId" style="width: 100%">
            <el-option v-for="emp in visibleEmployees" :key="emp.id" :label="emp.name" :value="emp.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="日期" required>
          <el-date-picker v-model="checkin.date" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="签到时间">
          <el-time-picker v-model="checkin.checkIn" value-format="HH:mm" format="HH:mm" style="width: 100%" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="checkin.status" style="width: 100%">
            <el-option label="出勤" value="present" />
            <el-option label="迟到" value="late" />
            <el-option label="缺勤" value="absent" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="checkinVisible = false">取消</el-button>
        <el-button type="primary" @click="saveCheckin">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="checkoutVisible" title="签退" width="360px">
      <el-form label-width="90px">
        <el-form-item label="员工">
          <el-input :model-value="checkoutEmployeeName" disabled />
        </el-form-item>
        <el-form-item label="签退时间">
          <el-time-picker v-model="checkoutTime" value-format="HH:mm" format="HH:mm" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="checkoutVisible = false">取消</el-button>
        <el-button type="primary" @click="saveCheckout">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="leaveDialogVisible" title="申请请假" width="480px">
      <el-form label-width="90px">
        <el-form-item label="员工" required>
          <el-select v-model="leaveForm.employeeId" style="width: 100%">
            <el-option v-for="emp in visibleEmployees" :key="emp.id" :label="emp.name" :value="emp.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="类型" required>
          <el-select v-model="leaveForm.type" style="width: 100%">
            <el-option v-for="item in leaveTypes" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="起止日期" required>
          <el-date-picker v-model="leaveRange" type="daterange" value-format="YYYY-MM-DD" start-placeholder="开始日期" end-placeholder="结束日期" style="width: 100%" />
        </el-form-item>
        <el-form-item label="原因">
          <el-input v-model="leaveForm.reason" type="textarea" :rows="2" placeholder="请输入请假原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="leaveDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveLeave">提交申请</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="overtimeDialogVisible" :title="overtimeEditingId ? '编辑加班记录' : '申请加班'" width="440px">
      <el-form label-width="100px">
        <el-form-item label="员工" required>
          <el-select v-model="overtimeForm.employeeId" style="width: 100%">
            <el-option v-for="emp in visibleEmployees" :key="emp.id" :label="emp.name" :value="emp.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="加班日期" required>
          <el-date-picker v-model="overtimeForm.date" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="时长（小时）" required>
          <el-input-number v-model="overtimeForm.hours" :min="0.5" :step="0.5" style="width: 100%" />
        </el-form-item>
        <el-form-item label="原因">
          <el-input v-model="overtimeForm.reason" placeholder="请输入加班原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="overtimeDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveOvertime">{{ overtimeEditingId ? "保存" : "提交" }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="reportVisible" title="本月考勤统计" width="820px">
      <el-table :data="monthlyReport" size="small">
        <el-table-column prop="name" label="员工" min-width="100" />
        <el-table-column prop="company" label="公司" min-width="110" />
        <el-table-column prop="present" label="出勤" width="80" />
        <el-table-column prop="late" label="迟到" width="80" />
        <el-table-column prop="absent" label="缺勤" width="80" />
        <el-table-column label="出勤率" width="100">
          <template #default="{ row }">{{ row.rate }}%</template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="reportVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </VnetPage>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { Check, Plus } from "@element-plus/icons-vue";
import VnetPage from "../../components/vnet/VnetPage.vue";
import { useHrStore, type HrAttendanceRecord, type HrOvertimeRecord } from "../../stores/hr";
import { useAuthStore } from "../../stores/auth";

const props = defineProps<{ tab?: string }>();
const hrStore = useHrStore();
const authStore = useAuthStore();
const isHrAdmin = computed(() => authStore.hasAnyRole(["SUPER_ADMIN", "ADMIN", "SYS_ADMIN", "FLOOR_LEADER"]));
const buildingLeaders = [
  { building: "百度M1", leaders: "张耀" },
  { building: "人寿", leaders: "陈瑶瑶" },
  { building: "B28", leaders: "张乐斌、乔涛、郭鑫存" },
  { building: "M3", leaders: "张正勤" },
  { building: "M5", leaders: "何金强、段宝光" },
  { building: "M6", leaders: "赵新、马金礼" },
  { building: "M6V&M5二三期", leaders: "张士令、刘燚" }
];
const leaderBuildings = computed(() => {
  const name = authStore.realName;
  if (!name) return [];
  return buildingLeaders.filter((item) => item.leaders.includes(name)).map((item) => item.building);
});
const visibleEmployees = computed(() => {
  const ids = visibleEmployeeIds.value;
  return hrStore.employees.filter((emp) => ids.has(emp.id));
});

const visibleEmployeeIds = computed(() => {
  const buildings = leaderBuildings.value;
  if (buildings.length === 0) return new Set(hrStore.employees.map((emp) => emp.id));
  return new Set(hrStore.employees.filter((emp) => buildings.includes(emp.building || "")).map((emp) => emp.id));
});

const myEmployeeId = computed(() => {
  const name = authStore.realName;
  if (!name) return 0;
  return hrStore.employees.find((emp) => emp.name === name)?.id || hrStore.employees[0]?.id || 0;
});
const activeTab = ref(props.tab || "attendance");
watch(
  () => props.tab,
  (value) => {
    if (value) activeTab.value = value;
  }
);

const today = new Date().toISOString().slice(0, 10);
const month = today.slice(0, 7);
const attDate = ref("");
const checkinVisible = ref(false);
const checkoutVisible = ref(false);
const leaveDialogVisible = ref(false);
const overtimeDialogVisible = ref(false);
const reportVisible = ref(false);
const checkoutRecord = ref<HrAttendanceRecord | null>(null);
const checkoutTime = ref("");
const overtimeEditingId = ref<number | null>(null);

const checkin = reactive({
  employeeId: 0,
  date: today,
  checkIn: new Date().toTimeString().slice(0, 5),
  status: "present" as HrAttendanceRecord["status"]
});

const leaveForm = reactive({
  employeeId: 0,
  type: "sick",
  reason: ""
});
const leaveRange = ref<[string, string]>([today, today]);
const leaveTypes = [
  { value: "sick", label: "病假" },
  { value: "personal", label: "事假" },
  { value: "annual", label: "年假" },
  { value: "marriage", label: "婚假" },
  { value: "maternity", label: "产假" },
  { value: "other", label: "其他" }
];

const overtimeForm = reactive({
  employeeId: 0,
  date: today,
  hours: 2,
  reason: ""
});

const todayPresent = computed(() => todayRecords.value.filter((record) => record.status === "present").length);
const todayLate = computed(() => todayRecords.value.filter((record) => record.status === "late").length);
const todayAbsent = computed(() => todayRecords.value.filter((record) => record.status === "absent").length);
const todayNotChecked = computed(() => Math.max(0, visibleEmployeeIds.value.size - todayRecords.value.length));

const todayRecords = computed(() =>
  hrStore.attendance
    .filter((record) => record.date === today)
    .map((record) => withEmployee(record))
    .sort((a, b) => b.id - a.id)
);

const monthAttendance = computed(() => hrStore.attendance.filter((record) => record.date.startsWith(month) && visibleEmployeeIds.value.has(record.employeeId)));
const monthPresent = computed(() => monthAttendance.value.filter((record) => record.status === "present").length);
const monthLate = computed(() => monthAttendance.value.filter((record) => record.status === "late").length);
const monthAbsent = computed(() => monthAttendance.value.filter((record) => record.status === "absent").length);
const attendanceRate = computed(() => {
  const total = monthAttendance.value.length;
  return total > 0 ? Math.round((monthPresent.value / total) * 100) : 0;
});
const attendanceEmployeeCount = computed(() => new Set(monthAttendance.value.map((record) => record.employeeId)).size);

const filteredAttendance = computed(() => {
  const date = attDate.value;
  return hrStore.attendance
    .filter((record) => !date || record.date === date)
    .map((record) => withEmployee(record))
    .sort((a, b) => b.id - a.id);
});

const filteredLeaves = computed(() =>
  hrStore.leaveRequests
    .slice()
    .sort((a, b) => b.id - a.id)
    .map((item) => ({
      ...item,
      employeeName: employeeOf(item.employeeId)?.name || "(已删除)",
      department: employeeOf(item.employeeId)?.department || "-",
      typeName: leaveTypes.find((type) => type.value === item.type)?.label || item.type
    }))
);

const todayOvertime = computed(() => hrStore.overtime.filter((record) => record.date === today && visibleEmployeeIds.value.has(record.employeeId)));
const todayOtHours = computed(() => todayOvertime.value.reduce((sum, record) => sum + (record.hours || 0), 0));

const overtimeRows = computed(() =>
  hrStore.overtime
    .slice()
    .sort((a, b) => b.id - a.id)
    .map((item) => ({
      ...item,
      employeeName: employeeOf(item.employeeId)?.name || "(已删除)",
      department: employeeOf(item.employeeId)?.department || "-"
    }))
);

const overtimeBalanceRows = computed(() =>
  visibleEmployees.value.map((employee) => {
    const overtimeHours = hrStore.overtime
      .filter((record) => record.employeeId === employee.id && record.status === "approved")
      .reduce((sum, record) => sum + (Number(record.hours) || 0), 0);
    const leaveHours = hrStore.leaveRequests
      .filter((record) => record.employeeId === employee.id && record.status === "approved")
      .reduce((sum, record) => sum + (Number(record.days) || 0) * 8, 0);
    return {
      employeeName: employee.name,
      overtimeHours: Number(overtimeHours.toFixed(2)),
      leaveHours: Number(leaveHours.toFixed(2)),
      availableHours: Number(Math.max(0, overtimeHours - leaveHours).toFixed(2))
    };
  })
);

const monthlyReport = computed(() => {
  const records = hrStore.attendance.filter((record) => record.date.startsWith(month) && visibleEmployeeIds.value.has(record.employeeId));
  const map = new Map<number, { present: number; late: number; absent: number }>();
  for (const record of records) {
    const current = map.get(record.employeeId) || { present: 0, late: 0, absent: 0 };
    current[record.status] += 1;
    map.set(record.employeeId, current);
  }
  return Array.from(map.entries()).map(([employeeId, value]) => {
    const emp = employeeOf(employeeId);
    const total = value.present + value.late + value.absent;
    return {
      name: emp?.name || "(已删除)",
      dept: emp?.department || "-",
      company: emp?.company || "-",
      ...value,
      rate: total > 0 ? Math.round((value.present / total) * 100) : 0
    };
  });
});

const checkoutEmployeeName = computed(() => {
  const record = checkoutRecord.value;
  return record ? employeeOf(record.employeeId)?.name || "" : "";
});

function employeeOf(id: number) {
  return hrStore.employees.find((emp) => emp.id === id);
}

function withEmployee(record: HrAttendanceRecord) {
  const emp = employeeOf(record.employeeId);
  return { ...record, employeeName: emp?.name || "(已删除)", department: emp?.department || "-" };
}

function statusLabel(status: string): string {
  return status === "present" ? "出勤" : status === "late" ? "迟到" : "缺勤";
}

function statusType(status: string): "success" | "warning" | "danger" {
  return status === "present" ? "success" : status === "late" ? "warning" : "danger";
}

function leaveStatusLabel(status: string): string {
  return status === "pending" ? "待审批" : status === "approved" ? "已批准" : "已拒绝";
}

function leaveStatusType(status: string): "warning" | "success" | "danger" {
  return status === "pending" ? "warning" : status === "approved" ? "success" : "danger";
}

function leaveCount(status: string): number {
  return hrStore.leaveRequests.filter((item) => item.status === status).length;
}

function overtimeCount(status: string): number {
  return hrStore.overtime.filter((item) => item.status === status).length;
}

function openCheckin(): void {
  if (visibleEmployees.value.length === 0) {
    ElMessage.warning("请先添加人员信息");
    return;
  }
  checkin.employeeId = isHrAdmin.value ? visibleEmployees.value[0]?.id : myEmployeeId.value;
  checkin.date = today;
  checkin.checkIn = new Date().toTimeString().slice(0, 5);
  checkinVisible.value = true;
}

function saveCheckin(): void {
  if (!checkin.employeeId) {
    ElMessage.warning("请选择员工");
    return;
  }
  if (hrStore.attendance.some((record) => record.employeeId === checkin.employeeId && record.date === checkin.date)) {
    ElMessage.warning("该员工当天已有考勤记录");
    return;
  }
  hrStore.addAttendance({
    employeeId: checkin.employeeId,
    date: checkin.date,
    checkIn: checkin.checkIn,
    status: checkin.status
  });
  ElMessage.success("考勤记录已添加");
  checkinVisible.value = false;
}

function openCheckout(record: HrAttendanceRecord): void {
  checkoutRecord.value = record;
  checkoutTime.value = new Date().toTimeString().slice(0, 5);
  checkoutVisible.value = true;
}

function saveCheckout(): void {
  if (!checkoutRecord.value) return;
  hrStore.updateAttendance(checkoutRecord.value.id, { checkOut: checkoutTime.value });
  ElMessage.success("签退成功");
  checkoutVisible.value = false;
}

async function removeAttendance(record: HrAttendanceRecord): Promise<void> {
  try {
    await ElMessageBox.confirm("确定删除此考勤记录？", "删除确认", { type: "warning" });
  } catch {
    return;
  }
  hrStore.deleteAttendance(record.id);
  ElMessage.success("已删除");
}

function allPresent(): void {
  const missing = visibleEmployees.value.filter(
    (emp) => !hrStore.attendance.some((record) => record.employeeId === emp.id && record.date === today)
  );
  if (missing.length === 0) {
    ElMessage.info("今日所有员工均已考勤");
    return;
  }
  ElMessageBox.confirm(`将为 ${missing.length} 名未考勤员工添加「出勤」记录，确认？`, "一键到岗", { type: "info" })
    .then(() => {
      const time = new Date().toTimeString().slice(0, 5);
      for (const emp of missing) {
        hrStore.addAttendance({ employeeId: emp.id, date: today, checkIn: time, status: "present" });
      }
      ElMessage.success(`已为 ${missing.length} 人添加出勤记录`);
    })
    .catch(() => undefined);
}

function showMonthlyReport(): void {
  reportVisible.value = true;
}

async function removeRecentLeave(): Promise<void> {
  try {
    await ElMessageBox.confirm("确定删除最近 24 小时内添加的请假和加班记录？", "删除确认", { type: "warning" });
  } catch {
    return;
  }
  const result = hrStore.deleteRecentLeaveOvertime(24);
  ElMessage.success(`已删除请假 ${result.leave} 条、加班 ${result.overtime} 条`);
}


function openLeaveDialog(): void {
  if (visibleEmployees.value.length === 0) {
    ElMessage.warning("请先添加人员信息");
    return;
  }
  leaveForm.employeeId = isHrAdmin.value ? visibleEmployees.value[0]?.id : myEmployeeId.value;
  leaveForm.type = "sick";
  leaveForm.reason = "";
  leaveRange.value = [today, today];
  leaveDialogVisible.value = true;
}

function saveLeave(): void {
  const [start, end] = leaveRange.value;
  if (!start || !end || end < start) {
    ElMessage.warning("请选择正确的起止日期");
    return;
  }
  const days = Math.floor((new Date(end).getTime() - new Date(start).getTime()) / 86400000) + 1;
  hrStore.addLeaveRequest({
    employeeId: leaveForm.employeeId,
    type: leaveForm.type,
    startDate: start,
    endDate: end,
    days,
    reason: leaveForm.reason,
    status: "pending"
  });
  ElMessage.success("请假申请已提交，等待审批");
  leaveDialogVisible.value = false;
}

async function approveLeave(row: { id: number }): Promise<void> {
  try {
    await ElMessageBox.confirm("确定批准该请假申请？", "审批确认", { type: "info" });
  } catch {
    return;
  }
  hrStore.updateLeaveRequest(row.id, { status: "approved" });
  ElMessage.success("已批准");
}

async function rejectLeave(row: { id: number }): Promise<void> {
  try {
    await ElMessageBox.confirm("确定拒绝该请假申请？", "审批确认", { type: "warning" });
  } catch {
    return;
  }
  hrStore.updateLeaveRequest(row.id, { status: "rejected" });
  ElMessage.info("已拒绝");
}

function openOvertimeDialog(record?: HrOvertimeRecord): void {
  overtimeEditingId.value = record?.id ?? null;
  overtimeForm.employeeId = record?.employeeId || (isHrAdmin.value ? visibleEmployees.value[0]?.id : myEmployeeId.value) || 0;
  overtimeForm.date = record?.date || today;
  overtimeForm.hours = record?.hours || 2;
  overtimeForm.reason = record?.reason || "";
  overtimeDialogVisible.value = true;
}

function saveOvertime(): void {
  if (!overtimeForm.employeeId || !overtimeForm.date || overtimeForm.hours <= 0) {
    ElMessage.warning("请填写完整信息");
    return;
  }
  if (overtimeEditingId.value) {
    hrStore.updateOvertime(overtimeEditingId.value, {
      date: overtimeForm.date,
      hours: overtimeForm.hours,
      reason: overtimeForm.reason
    });
    ElMessage.success("已更新");
  } else {
    hrStore.addOvertime({
      employeeId: overtimeForm.employeeId,
      date: overtimeForm.date,
      hours: overtimeForm.hours,
      reason: overtimeForm.reason,
      status: "pending"
    });
    ElMessage.success("加班申请已提交");
  }
  overtimeDialogVisible.value = false;
}

async function approveOvertime(row: HrOvertimeRecord): Promise<void> {
  try {
    await ElMessageBox.confirm("确定批准该加班记录？", "审批确认", { type: "info" });
  } catch {
    return;
  }
  hrStore.updateOvertime(row.id, { status: "approved" });
  ElMessage.success("已批准");
}

async function rejectOvertime(row: HrOvertimeRecord): Promise<void> {
  try {
    await ElMessageBox.confirm("确定拒绝该加班记录？", "审批确认", { type: "warning" });
  } catch {
    return;
  }
  hrStore.updateOvertime(row.id, { status: "rejected" });
  ElMessage.info("已拒绝");
}

async function removeOvertime(row: HrOvertimeRecord): Promise<void> {
  try {
    await ElMessageBox.confirm("确定删除此加班记录？", "删除确认", { type: "warning" });
  } catch {
    return;
  }
  hrStore.deleteOvertime(row.id);
  ElMessage.success("已删除");
}

onMounted(() => hrStore.load());
</script>

<style scoped>
.hr-tabs :deep(.el-tabs__header) {
  margin-bottom: 16px;
}

.hr-today-overview {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  padding: 16px;
}

.hr-overview-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 16px;
  border: 1px solid var(--app-line);
  border-radius: var(--app-radius);
  background: var(--app-surface-muted);
}

.hr-overview-item span {
  color: var(--app-text-muted);
  font-size: 13px;
}

.hr-overview-item strong {
  font-size: 28px;
  line-height: 1;
}

.hr-overview-item.success strong {
  color: var(--app-success);
}

.hr-overview-item.warning strong {
  color: var(--app-warning);
}

.hr-overview-item.danger strong {
  color: var(--app-danger);
}

@media (max-width: 760px) {
  .hr-today-overview {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

.hr-attendance-dashboard {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.hr-quick-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  padding: 14px;
  border: 1px solid var(--app-line);
  border-radius: var(--app-radius);
  background: #ffffff;
  box-shadow: var(--app-shadow);
}

.hr-quick-actions .el-button:last-child {
  margin-left: auto;
}

.hr-table-scroll {
  width: 100%;
  overflow-x: auto;
}

.hr-table-scroll .el-table {
  min-width: 100%;
}

.hr-stat-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
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

.hr-stat.success strong {
  color: var(--app-success);
}

.hr-stat.warning strong {
  color: var(--app-warning);
}

.hr-stat.danger strong {
  color: var(--app-danger);
}

.hr-stat.primary strong,
.hr-stat.info strong {
  color: var(--app-primary);
}

.hr-stat span {
  color: var(--app-text-muted);
  font-size: 12px;
}

.hr-stat strong {
  font-size: 24px;
  line-height: 1;
}

.hr-panel {
  overflow: hidden;
  margin-bottom: 16px;
  border: 1px solid var(--app-line);
  border-radius: var(--app-radius);
  background: #ffffff;
  box-shadow: var(--app-shadow);
}

.hr-panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--app-line-soft);
}

.hr-panel-head h3 {
  margin: 0;
  font-size: 15px;
}

.hr-filter-row {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.hr-three-columns {
  display: grid;
  grid-template-columns: 1fr;
  gap: 16px;
  align-items: start;
}

.hr-three-columns > section:nth-child(1) {
  order: 3;
}

.hr-three-columns > section:nth-child(2) {
  order: 1;
}

.hr-three-columns > section:nth-child(3) {
  order: 2;
}

@media (max-width: 1200px) {
  .hr-three-columns {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 1000px) {
  .hr-stat-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .hr-stat-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
.hr-employee-panel {
  padding: 24px;
  border: 1px solid var(--app-line);
  border-radius: var(--app-radius);
  background: #ffffff;
  box-shadow: var(--app-shadow);
}

.hr-employee-head {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 20px;
}

.hr-employee-head strong {
  font-size: 18px;
}

.hr-employee-head span {
  color: var(--app-text-muted);
  font-size: 13px;
}

.hr-employee-actions {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.hr-employee-actions .el-button {
  height: 56px;
  font-size: 15px;
  font-weight: 700;
}

@media (max-width: 760px) {
  .hr-employee-actions {
    grid-template-columns: 1fr;
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
