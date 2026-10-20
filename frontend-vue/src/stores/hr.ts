import { defineStore } from "pinia";
import { SAMPLE_EMPLOYEES } from "../pages/hr/hr-seed";
import { getUserPageApi } from "../api/user";

const NAMESPACE = "personnel_management_";
const PERFORMANCE_KEY = "personnel_performance";
const AI_CONFIG_KEY = "personnel_ai_config";
const AI_MESSAGES_KEY = "personnel_ai_messages";
const FEISHU_WEBHOOK_KEY = "personnel_feishu_webhook";

export interface HrEmployee {
  id: number;
  name: string;
  deptNo?: number;
  employeeNo?: string;
  platformUserId?: number;
  feishuUserId?: string;
  feishuOpenId?: string;
  feishuUnionId?: string;
  gender?: string;
  department?: string;
  position?: string;
  company?: string;
  personAttribute?: string;
  workAttribute?: string;
  phone?: string;
  email?: string;
  joinDate?: string;
  leaveDate?: string;
  region?: string;
  room?: string;
  building?: string;
  salary?: number | null;
  status?: string;
  aiProfile?: string;
  resumeData?: string;
  resumeName?: string;
  credentialData?: string;
  credentialName?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface HrAttendanceRecord {
  id: number;
  employeeId: number;
  date: string;
  checkIn?: string;
  checkOut?: string;
  status: "present" | "late" | "absent";
}

export interface HrLeaveRequest {
  id: number;
  employeeId: number;
  type: string;
  startDate: string;
  endDate: string;
  days: number;
  reason?: string;
  status: "pending" | "approved" | "rejected";
  lvDeduct?: number;
  lvRate?: string;
  createdAt?: string;
}

export interface HrOvertimeRecord {
  id: number;
  employeeId: number;
  date: string;
  hours: number;
  reason?: string;
  status: "pending" | "approved" | "rejected";
  otPay?: number;
  otRate?: string;
  createdAt?: string;
}

export interface HrUpdateLog {
  id: number;
  date: string;
  yearMonth: string;
  operator?: string;
  action: "add" | "update" | "remove";
  employeeName: string;
  building?: string;
  late: boolean;
  createdAt: string;
}

export interface HrSettlementRecord {
  employeeId: number;
  name: string;
  department?: string;
  position?: string;
  baseSalary: number;
  otHours: number;
  otPay: number;
  lvDays: number;
  lvHours: number;
  lvDeduct: number;
  otRate: string;
  lvRate: string;
  netPay: number;
}

export interface HrSettlement {
  yearMonth: string;
  totalEmployees: number;
  totalSalary: number;
  records: HrSettlementRecord[];
  createdAt: string;
}

export interface HrOrgNode {
  id: number;
  name: string;
  type: "company" | "department" | "team" | "position";
  children?: HrOrgNode[];
}

export interface HrPerformanceScore {
  id: number;
  employeeId: number;
  name: string;
  dept: string;
  pos: string;
  yearMonth: string;
  scores: Record<string, number>;
  total: number;
  createdAt: string;
}

export interface HrAiConfig {
  endpoint: string;
  model: string;
  apiKey: string;
  systemPrompt: string;
  maxTokens: number;
  temperature: number;
}

export interface HrChatMessage {
  role: "user" | "assistant";
  content: string;
}

function readJson<T>(key: string, fallback: T): T {
  try {
    const raw = localStorage.getItem(key);
    return raw ? (JSON.parse(raw) as T) : fallback;
  } catch {
    return fallback;
  }
}

function writeJson(key: string, value: unknown): void {
  try {
    localStorage.setItem(key, JSON.stringify(value));
  } catch {
    // 本地存储满或禁用时静默失败
  }
}

function today(): string {
  return new Date().toISOString().split("T")[0];
}

function monthOf(value: string): string {
  return value ? value.substring(0, 7) : "";
}

const SALARY_MAP: Record<string, number> = {
  交付运维主管: 18000,
  设施运维主管: 18000,
  设施运维值班长: 12000,
  高级交付运维工程师: 15000,
  交付运维工程师: 10000,
  设施运维工程师: 10000,
  交付运维值班工程师: 8000,
  设施运维值班工程师: 8000,
  "交付运维工程师-网银专职": 10000,
  前台: 6000,
  区域经理: 18000,
  区域总监: 30000
};

// 外包人员加班固定单价：标准工程师 72 元/小时，值班员 63.82 元/小时；人员性质为 420 的人员不涉及此标准。
const OT_FIXED_RATES = [
// 岗位名兼容长/短写法：交付运维工程师/交付工程师=标准工程师，值班(工程)师/值班员=值班员。
  { position: "交付运维工程师", company: "外包", rate: 72 },
  { position: "设施运维工程师", company: "外包", rate: 72 },
  { position: "交付运维值班工程师", company: "外包", rate: 63.82 },
  { position: "设施运维值班工程师", company: "外包", rate: 63.82 },
  { position: "交付工程师", company: "外包", rate: 72 },
  { position: "值班员", company: "外包", rate: 63.82 }
];

const LEAVE_DEDUCT_RATES = [{ company: "外包", rate: 63.35, hoursPerDay: 9 }];

export const useHrStore = defineStore("hr", {
  state: () => ({
    employees: [] as HrEmployee[],
    attendance: [] as HrAttendanceRecord[],
    leaveRequests: [] as HrLeaveRequest[],
    overtime: [] as HrOvertimeRecord[],
    updateLogs: [] as HrUpdateLog[],
    currentOperator: "",
    settlements: [] as HrSettlement[],
    departments: [] as string[],
    orgStructure: { id: 1, name: "华北一部酒仙桥", type: "company", children: [] } as HrOrgNode,
    stats: { totalEmployees: 0, todayPresent: 0, todayAbsent: 0, pendingLeave: 0, todayOTHours: 0, monthOTHours: 0 },
    performance: [] as HrPerformanceScore[],
    aiConfig: {
      endpoint: `${window.location.protocol}//${window.location.hostname}:11434/v1/chat/completions`,
      model: "mock-ai",
      apiKey: "",
      systemPrompt: "",
      maxTokens: 2048,
      temperature: 0.7
    } as HrAiConfig,
    aiMessages: [] as HrChatMessage[],
    feishuWebhook: ""
  }),

  getters: {
    monthUpdateLogs: (state) => {
      const ym = today().slice(0, 7);
      return state.updateLogs.filter((item) => item.yearMonth === ym);
    },
    lateUpdateCount: (state) => {
      const ym = today().slice(0, 7);
      return state.updateLogs.filter((item) => item.yearMonth === ym && item.late).length;
    },
    totalEmployees: (state) => state.employees.length,
    departmentList: (state) => {
      const names = new Set<string>();
      for (const dept of state.departments) {
        if (dept) names.add(dept);
      }
      for (const emp of state.employees) {
        if (emp.department) names.add(emp.department);
      }
      return Array.from(names);
    }
  },

  actions: {
    load(): void {
      this.employees = readJson<HrEmployee[]>(`${NAMESPACE}employees`, []);
      this.removeVirtualEmployeesIfOnlyVirtual();
      if (!localStorage.getItem(`${NAMESPACE}room_cleanup_20260811`)) {
        const removed = this.clearEmployeesByRooms(["M6", "M5", "M6V&M5", "M6V&M5二三期", "B28"]);
        localStorage.setItem(`${NAMESPACE}room_cleanup_20260811`, String(removed));
      }
      this.attendance = readJson<HrAttendanceRecord[]>(`${NAMESPACE}attendance`, []);
      this.leaveRequests = readJson<HrLeaveRequest[]>(`${NAMESPACE}leave_requests`, []);
      this.overtime = readJson<HrOvertimeRecord[]>(`${NAMESPACE}overtime`, []);
      this.updateLogs = readJson<HrUpdateLog[]>(`${NAMESPACE}update_logs`, []);
      this.settlements = readJson<HrSettlement[]>(`${NAMESPACE}settlements`, []);
      this.departments = readJson<string[]>(`${NAMESPACE}departments`, []);
      this.orgStructure = readJson<HrOrgNode>(`${NAMESPACE}org_structure`, {
        id: 1,
        name: "华北一部酒仙桥",
        type: "company",
        children: []
      });
      this.performance = readJson<HrPerformanceScore[]>(PERFORMANCE_KEY, []);
      const storedAiConfig = readJson<Partial<HrAiConfig>>(AI_CONFIG_KEY, {});
      const defaultAiEndpoint = `${window.location.protocol}//${window.location.hostname}:11434/v1/chat/completions`;
      if (!storedAiConfig.endpoint || storedAiConfig.endpoint.includes("localhost:11434")) {
        storedAiConfig.endpoint = defaultAiEndpoint;
        storedAiConfig.model = storedAiConfig.model || "mock-ai";
        writeJson(AI_CONFIG_KEY, storedAiConfig);
      }
      this.aiConfig = { ...this.aiConfig, ...storedAiConfig };
      this.aiMessages = readJson<HrChatMessage[]>(AI_MESSAGES_KEY, []);
      this.feishuWebhook = localStorage.getItem(FEISHU_WEBHOOK_KEY) || "";

      if (this.departments.length === 0) {
        this.departments = ["交付运维部", "设施运维部"];
      }
      let changed = false;
      for (const emp of this.employees) {
        if ((emp.status as string) === "离开") {
          emp.status = "离职";
          changed = true;
        }
        if (emp.room !== "酒仙桥") {
          emp.room = "酒仙桥";
          changed = true;
        }
        if (!emp.region) {
          emp.region = "华北一";
          changed = true;
        }
        if (!emp.building) {
          emp.building = "M6";
          changed = true;
        }
        if (!emp.status) {
          emp.status = "在职";
          changed = true;
        }
      }
      if (changed) this.persistEmployees();
      this.refreshRates();
      if (!localStorage.getItem(`${NAMESPACE}recent_cleanup_20260812`)) {
        const removed = this.deleteRecentLeaveOvertime(24);
        localStorage.setItem(`${NAMESPACE}recent_cleanup_20260812`, `${removed.leave}:${removed.overtime}`);
      }
      this.updateStats();
    },

    refreshRates(): void {
      let otChanged = false;
      for (const record of this.overtime) {
        const emp = this.employees.find((item) => item.id === record.employeeId);
        if (!emp) continue;
        const hours = parseFloat(String(record.hours)) || 0;
        const fixed = OT_FIXED_RATES.find(
          (item) => item.company && emp.company && emp.company.includes(item.company) && (emp.position || "").indexOf(item.position) >= 0 && (emp.company || "").trim() !== "420"
        );
        let otPay = 0;
        let otRate = "";
        if (fixed) {
          otPay = hours * fixed.rate;
          otRate = `${fixed.rate.toFixed(2)}元/h`;
        }
        const nextPay = parseFloat(otPay.toFixed(2));
        if (record.otPay !== nextPay || record.otRate !== otRate) {
          record.otPay = nextPay;
          record.otRate = otRate;
          otChanged = true;
        }
      }
      if (otChanged) writeJson(`${NAMESPACE}overtime`, this.overtime);
      let leaveChanged = false;
      for (const record of this.leaveRequests) {
        const emp = this.employees.find((item) => item.id === record.employeeId);
        if (!emp) continue;
        const days = parseInt(String(record.days), 10) || 0;
        const cfg = LEAVE_DEDUCT_RATES.find((item) => item.company && emp.company && emp.company.includes(item.company));
        let lvDeduct = 0;
        let lvRate = "";
        if (cfg) {
          lvDeduct = days * cfg.hoursPerDay * cfg.rate;
          lvRate = `${cfg.rate.toFixed(2)}元/h×${cfg.hoursPerDay}h/天`;
        }
        const nextDeduct = parseFloat(lvDeduct.toFixed(2));
        if (record.lvDeduct !== nextDeduct || record.lvRate !== lvRate) {
          record.lvDeduct = nextDeduct;
          record.lvRate = lvRate;
          leaveChanged = true;
        }
      }
      if (leaveChanged) writeJson(`${NAMESPACE}leave_requests`, this.leaveRequests);
      this.clearSettlementCache();
    },

    salaryFor(emp?: HrEmployee | null): number {
      if (!emp) return 0;
      if (emp.salary !== undefined && emp.salary !== null) return emp.salary;
      return SALARY_MAP[emp.position || ""] || 8000;
    },

    persistEmployees(): void {
      writeJson(`${NAMESPACE}employees`, this.employees);
      this.updateStats();
    },

    addEmployee(data: Partial<HrEmployee>): HrEmployee {
      const id = this.employees.length > 0 ? Math.max(...this.employees.map((e) => e.id || 0)) + 1 : 1;
      const emp: HrEmployee = {
        id,
        name: data.name || "",
        gender: data.gender || "男",
        department: data.department || "",
        position: data.position || "",
        company: data.company || "",
        phone: data.phone || "",
        email: data.email || "",
        joinDate: data.joinDate || today(),
        region: data.region || "华北一",
        room: data.room || "酒仙桥",
        building: data.building || "M6",
        status: data.status || "在职",
        salary: data.salary ?? SALARY_MAP[data.position || ""] ?? 8000,
        aiProfile: data.aiProfile || "",
        leaveDate: data.leaveDate || "",
        createdAt: new Date().toISOString()
      };
      this.employees.push(emp);
      this.logPersonnelChange("add", emp);
      this.persistEmployees();
      return emp;
    },

    mergePlatformUsers(users: Array<{
      id: number;
      username: string;
      realName: string;
      employeeNo?: string;
      deptName?: string;
      mobile?: string;
      email?: string;
      status?: number;
      feishuUserId?: string;
      feishuOpenId?: string;
      feishuUnionId?: string;
      userType?: string;
    }>): number {
      let added = 0;
      const byName = new Map<string, number>();
      const byNo = new Map<string, number>();
      this.employees.forEach((emp, index) => {
        if (emp.name) byName.set(emp.name, index);
        if (emp.employeeNo) byNo.set(emp.employeeNo, index);
      });
      const nextId = this.employees.length > 0 ? Math.max(...this.employees.map((e) => e.id)) + 1 : 1;
      const positionMap: Record<string, string> = {
        SUPER_ADMIN: "平台超管",
        ADMIN: "管理员",
        ENGINEER: "工程师",
        FLOOR_LEADER: "楼长",
        MANAGER: "经理",
        REGION_MANAGER: "区域经理",
        SENIOR_MANAGER: "高级经理",
        PURCHASER: "采购员"
      };
      users.forEach((user, index) => {
        if (user.status === 0) return;
        const name = user.realName || user.username;
        if (!name) return;
        const existingIndex = byNo.get(user.employeeNo || "") ?? byName.get(name);
        const payload: Partial<HrEmployee> = {
          name,
          employeeNo: user.employeeNo || "",
          platformUserId: user.id,
          phone: user.mobile || "",
          email: user.email || "",
          department: user.deptName || "未分配",
          position: positionMap[user.userType || ""] || "员工",
          status: "在职"
        };
        if (user.feishuUserId) payload.feishuUserId = user.feishuUserId;
        if (user.feishuOpenId) payload.feishuOpenId = user.feishuOpenId;
        if (user.feishuUnionId) payload.feishuUnionId = user.feishuUnionId;
        if (existingIndex !== undefined && existingIndex >= 0) {
          const emp = this.employees[existingIndex];
          Object.assign(emp, payload, { updatedAt: new Date().toISOString() });
        } else {
          const emp: HrEmployee = {
            id: nextId + added + index,
            ...payload,
            gender: "男",
            company: "",
            joinDate: new Date().toISOString().slice(0, 10),
            region: "华北一",
            room: "酒仙桥",
            building: "M6",
            salary: 8000,
            createdAt: new Date().toISOString()
          } as HrEmployee;
          this.employees.push(emp);
          byName.set(name, this.employees.length - 1);
          if (user.employeeNo) byNo.set(user.employeeNo, this.employees.length - 1);
          added += 1;
        }
      });
      this.persistEmployees();
      this.refreshRates();
      this.updateStats();
      return added;
    },

    async syncPlatformUsers(): Promise<number> {
      try {
        const data = await getUserPageApi({ pageNo: 1, pageSize: 500 });
        const added = this.mergePlatformUsers(data.records);
        return added;
      } catch {
        return 0;
      }
    },

    updateEmployee(id: number, data: Partial<HrEmployee>): void {
      const emp = this.employees.find((item) => item.id === id);
      if (!emp) return;
      Object.assign(emp, data, { updatedAt: new Date().toISOString() });
      this.logPersonnelChange("update", emp);
      this.persistEmployees();
      this.refreshRates();
      this.updateStats();
    },

    deleteEmployee(id: number): void {
      const removing = this.employees.find((e) => e.id === id);
      this.employees = this.employees.filter((e) => e.id !== id);
      this.attendance = this.attendance.filter((r) => r.employeeId !== id);
      this.leaveRequests = this.leaveRequests.filter((r) => r.employeeId !== id);
      this.overtime = this.overtime.filter((r) => r.employeeId !== id);
      if (removing) this.logPersonnelChange("remove", removing);
      this.persistEmployees();
      writeJson(`${NAMESPACE}attendance`, this.attendance);
      writeJson(`${NAMESPACE}leave_requests`, this.leaveRequests);
      writeJson(`${NAMESPACE}overtime`, this.overtime);
      this.clearSettlementCache();
      this.updateStats();
    },

    clearEmployees(): void {
      this.employees = [];
      this.attendance = [];
      this.leaveRequests = [];
      this.overtime = [];
      this.settlements = [];
      this.performance = [];
      localStorage.setItem(`${NAMESPACE}initialized`, "1");
      this.persistEmployees();
      writeJson(`${NAMESPACE}attendance`, this.attendance);
      writeJson(`${NAMESPACE}leave_requests`, this.leaveRequests);
      writeJson(`${NAMESPACE}overtime`, this.overtime);
      writeJson(`${NAMESPACE}settlements`, this.settlements);
      writeJson(PERFORMANCE_KEY, this.performance);
    },

    clearEmployeesByRooms(rooms: string[]): number {
      const roomSet = new Set(rooms.map((room) => room.replace(/机房\s*$/, "").trim()));
      const kept = this.employees.filter((emp) => !roomSet.has((emp.room || "").replace(/机房\s*$/, "").trim()));
      const removed = this.employees.length - kept.length;
      if (removed > 0) {
        this.employees = kept;
        const keptIds = new Set(kept.map((emp) => emp.id));
        this.attendance = this.attendance.filter((record) => keptIds.has(record.employeeId));
        this.leaveRequests = this.leaveRequests.filter((record) => keptIds.has(record.employeeId));
        this.overtime = this.overtime.filter((record) => keptIds.has(record.employeeId));
        this.persistEmployees();
        writeJson(`${NAMESPACE}attendance`, this.attendance);
        writeJson(`${NAMESPACE}leave_requests`, this.leaveRequests);
        writeJson(`${NAMESPACE}overtime`, this.overtime);
      }
      return removed;
    },

    removeVirtualEmployeesIfOnlyVirtual(): void {
      const virtualNames = new Set<string>([
        ...SAMPLE_EMPLOYEES.map((emp) => emp.name || ""),
        "工程师王五",
        "楼长赵六",
        "经理李四"
      ]);
      if (this.employees.length > 0 && this.employees.every((emp) => virtualNames.has(emp.name))) {
        this.clearEmployees();
      }
    },

    addAttendance(record: Omit<HrAttendanceRecord, "id">): void {
      this.attendance.push({ ...record, id: Date.now() });
      writeJson(`${NAMESPACE}attendance`, this.attendance);
      this.updateStats();
    },

    updateAttendance(id: number, data: Partial<HrAttendanceRecord>): void {
      const record = this.attendance.find((r) => r.id === id);
      if (!record) return;
      Object.assign(record, data);
      writeJson(`${NAMESPACE}attendance`, this.attendance);
      this.updateStats();
    },

    deleteAttendance(id: number): void {
      this.attendance = this.attendance.filter((r) => r.id !== id);
      writeJson(`${NAMESPACE}attendance`, this.attendance);
      this.updateStats();
    },

    addLeaveRequest(record: Omit<HrLeaveRequest, "id" | "lvDeduct" | "lvRate">): HrLeaveRequest {
      const id = Date.now();
      const emp = this.employees.find((e) => e.id === record.employeeId);
      const days = record.days || 0;
      let lvDeduct = 0;
      let lvRate = "";
      if (emp) {
        const cfg = LEAVE_DEDUCT_RATES.find((item) => item.company && emp.company && emp.company.includes(item.company));
        if (cfg) {
          lvDeduct = days * cfg.hoursPerDay * cfg.rate;
          lvRate = `${cfg.rate.toFixed(2)}元/h×${cfg.hoursPerDay}h/天`;
        }
      }
      const item: HrLeaveRequest = {
        ...record,
        id,
        status: record.status || "pending",
        lvDeduct,
        lvRate,
        createdAt: new Date().toISOString()
      };
      this.leaveRequests.push(item);
      writeJson(`${NAMESPACE}leave_requests`, this.leaveRequests);
      this.clearSettlementCache();
      this.updateStats();
      return item;
    },

    updateLeaveRequest(id: number, data: Partial<HrLeaveRequest>): void {
      const record = this.leaveRequests.find((r) => r.id === id);
      if (!record) return;
      Object.assign(record, data);
      writeJson(`${NAMESPACE}leave_requests`, this.leaveRequests);
      this.clearSettlementCache();
      this.updateStats();
    },

    deleteLeaveRequest(id: number): void {
      this.leaveRequests = this.leaveRequests.filter((r) => r.id !== id);
      writeJson(`${NAMESPACE}leave_requests`, this.leaveRequests);
      this.clearSettlementCache();
      this.updateStats();
    },

    addOvertime(record: Omit<HrOvertimeRecord, "id" | "otPay" | "otRate">): HrOvertimeRecord {
      const id = Date.now();
      const emp = this.employees.find((e) => e.id === record.employeeId);
      const hours = parseFloat(String(record.hours)) || 0;
      let otPay = 0;
      let otRate = "";
      if (emp) {
        const fixed = OT_FIXED_RATES.find(
          (item) => item.company && emp.company && emp.company.includes(item.company) && (emp.position || "").indexOf(item.position) >= 0 && (emp.company || "").trim() !== "420"
        );
        if (fixed) {
          otPay = hours * fixed.rate;
          otRate = `${fixed.rate.toFixed(2)}元/h`;
        }
      }
      const item: HrOvertimeRecord = {
        ...record,
        id,
        status: record.status || "pending",
        otPay,
        otRate,
        createdAt: new Date().toISOString()
      };
      this.overtime.push(item);
      writeJson(`${NAMESPACE}overtime`, this.overtime);
      this.clearSettlementCache();
      this.updateStats();
      return item;
    },

    updateOvertime(id: number, data: Partial<HrOvertimeRecord>): void {
      const record = this.overtime.find((r) => r.id === id);
      if (!record) return;
      Object.assign(record, data);
      writeJson(`${NAMESPACE}overtime`, this.overtime);
      this.clearSettlementCache();
      this.updateStats();
    },

    deleteOvertime(id: number): void {
      this.overtime = this.overtime.filter((r) => r.id !== id);
      writeJson(`${NAMESPACE}overtime`, this.overtime);
      this.clearSettlementCache();
      this.updateStats();
    },


    deleteRecentLeaveOvertime(hours: number): { leave: number; overtime: number } {
      const cutoff = Date.now() - hours * 3600000;
      const beforeLeave = this.leaveRequests.length;
      const beforeOvertime = this.overtime.length;
      this.leaveRequests = this.leaveRequests.filter((item) => {
        const createdAt = item.createdAt ? new Date(item.createdAt).getTime() : 0;
        return Number.isFinite(createdAt) && createdAt < cutoff;
      });
      this.overtime = this.overtime.filter((item) => {
        const createdAt = item.createdAt ? new Date(item.createdAt).getTime() : 0;
        return Number.isFinite(createdAt) && createdAt < cutoff;
      });
      writeJson(`${NAMESPACE}leave_requests`, this.leaveRequests);
      writeJson(`${NAMESPACE}overtime`, this.overtime);
      this.clearSettlementCache();
      this.updateStats();
      return { leave: beforeLeave - this.leaveRequests.length, overtime: beforeOvertime - this.overtime.length };
    },

    clearSettlementCache(): void {
      writeJson(`${NAMESPACE}settlements`, []);
      this.settlements = [];
    },

    deleteSettlement(yearMonth: string): void {
      this.settlements = this.settlements.filter((s) => s.yearMonth !== yearMonth);
      writeJson(`${NAMESPACE}settlements`, this.settlements);
    },

    calcSettlement(yearMonth: string): HrSettlement {
      const records: HrSettlementRecord[] = [];
      for (const emp of this.employees) {
        const baseSalary = this.salaryFor(emp);
        const monthOt = this.overtime.filter(
          (o) => o.employeeId === emp.id && monthOf(o.date) === yearMonth && o.status === "approved"
        );
        const otHours = monthOt.reduce((sum, o) => sum + (parseFloat(String(o.hours)) || 0), 0);
        const fixed = OT_FIXED_RATES.find(
          (item) => item.company && emp.company && emp.company.includes(item.company) && (emp.position || "").indexOf(item.position) >= 0 && (emp.company || "").trim() !== "420"
        );
        let otPay = 0;
        let otRateLabel = "";
        if (fixed) {
          otPay = otHours * fixed.rate;
          otRateLabel = `${fixed.rate.toFixed(2)}元/h`;
        }
        const monthLeave = this.leaveRequests.filter(
          (l) => l.employeeId === emp.id && l.status === "approved" && monthOf(l.startDate) === yearMonth
        );
        const lvDays = monthLeave.reduce((sum, l) => sum + (parseInt(String(l.days), 10) || 0), 0);
        const cfg = LEAVE_DEDUCT_RATES.find((item) => item.company && emp.company && emp.company.includes(item.company));
        let lvDeduct = 0;
        let lvHours = 0;
        let lvRateLabel = "";
        if (cfg) {
          lvHours = lvDays * cfg.hoursPerDay;
          lvDeduct = lvHours * cfg.rate;
          lvRateLabel = `${cfg.rate.toFixed(2)}元/h×${cfg.hoursPerDay}h/天`;
        }
        const netPay = Math.max(0, baseSalary + otPay - lvDeduct);
        records.push({
          employeeId: emp.id,
          name: emp.name,
          department: emp.department,
          position: emp.position,
          baseSalary,
          otHours: parseFloat(otHours.toFixed(1)),
          otPay: parseFloat(otPay.toFixed(2)),
          lvDays,
          lvHours: parseFloat(lvHours.toFixed(1)),
          lvDeduct: parseFloat(lvDeduct.toFixed(2)),
          otRate: otRateLabel,
          lvRate: lvRateLabel,
          netPay: parseFloat(netPay.toFixed(2))
        });
      }
      const totalSalary = records.reduce((sum, r) => sum + r.netPay, 0);
      const settlement: HrSettlement = {
        yearMonth,
        totalEmployees: this.employees.length,
        totalSalary: parseFloat(totalSalary.toFixed(2)),
        records,
        createdAt: new Date().toISOString()
      };
      this.settlements.push(settlement);
      writeJson(`${NAMESPACE}settlements`, this.settlements);
      return settlement;
    },

    getOrCalcSettlement(yearMonth: string): HrSettlement | null {
      const existing = this.settlements.find((s) => s.yearMonth === yearMonth);
      return existing || this.calcSettlement(yearMonth);
    },

    addDepartment(name: string): void {
      if (name && !this.departments.includes(name)) {
        this.departments.push(name);
        this.persistDepartments();
      }
    },

    removeDepartment(name: string): void {
      this.departments = this.departments.filter((d) => d !== name);
      this.persistDepartments();
    },

    persistDepartments(): void {
      writeJson(`${NAMESPACE}departments`, this.departments);
    },

    findOrgNode(id: number): HrOrgNode | null {
      const walk = (node: HrOrgNode): HrOrgNode | null => {
        if (node.id === id) return node;
        if (node.children) {
          for (const child of node.children) {
            const found = walk(child);
            if (found) return found;
          }
        }
        return null;
      };
      return walk(this.orgStructure);
    },

    findOrgParent(id: number): HrOrgNode | null {
      const walk = (node: HrOrgNode): HrOrgNode | null => {
        if (node.children) {
          for (const child of node.children) {
            if (child.id === id) return node;
            const found = walk(child);
            if (found) return found;
          }
        }
        return null;
      };
      return walk(this.orgStructure);
    },

    findOrgNodeByName(name: string): HrOrgNode | null {
      const walk = (node: HrOrgNode): HrOrgNode | null => {
        if (node.name === name) return node;
        if (node.children) {
          for (const child of node.children) {
            const found = walk(child);
            if (found) return found;
          }
        }
        return null;
      };
      return walk(this.orgStructure);
    },

    addOrgNode(parentId: number, data: Partial<HrOrgNode>): HrOrgNode | null {
      const parent = this.findOrgNode(parentId);
      if (!parent) return null;
      if (!parent.children) parent.children = [];
      const node: HrOrgNode = {
        id: Date.now(),
        name: data.name || "",
        type: data.type || "department",
        children: data.children || []
      };
      parent.children.push(node);
      writeJson(`${NAMESPACE}org_structure`, this.orgStructure);
      return node;
    },

    updateOrgNode(id: number, data: Partial<HrOrgNode>): void {
      const node = this.findOrgNode(id);
      if (!node) return;
      Object.assign(node, data);
      writeJson(`${NAMESPACE}org_structure`, this.orgStructure);
    },

    deleteOrgNode(id: number): boolean {
      if (id === 1) return false;
      const parent = this.findOrgParent(id);
      if (!parent || !parent.children) return false;
      parent.children = parent.children.filter((child) => child.id !== id);
      writeJson(`${NAMESPACE}org_structure`, this.orgStructure);
      return true;
    },

    savePerformance(scores: HrPerformanceScore[]): void {
      this.performance = scores;
      writeJson(PERFORMANCE_KEY, scores);
    },

    addPerformance(record: HrPerformanceScore): void {
      this.performance.push(record);
      this.savePerformance(this.performance);
    },

    saveAiConfig(config: Partial<HrAiConfig>): void {
      this.aiConfig = { ...this.aiConfig, ...config };
      writeJson(AI_CONFIG_KEY, this.aiConfig);
    },

    saveAiMessages(messages: HrChatMessage[]): void {
      this.aiMessages = messages.slice(-50);
      writeJson(AI_MESSAGES_KEY, this.aiMessages);
    },

    saveFeishuWebhook(url: string): void {
      this.feishuWebhook = url;
      localStorage.setItem(FEISHU_WEBHOOK_KEY, url);
    },

    setOperator(name: string): void {
      this.currentOperator = name || "";
    },

    logPersonnelChange(action: "add" | "update" | "remove", employee: { name?: string; building?: string }): void {
      const now = new Date();
      const date = now.toISOString().slice(0, 10);
      const log: HrUpdateLog = {
        id: Date.now(),
        date,
        yearMonth: date.slice(0, 7),
        operator: this.currentOperator,
        action,
        employeeName: employee.name || "",
        building: employee.building || "",
        late: now.getDate() > 10,
        createdAt: now.toISOString()
      };
      this.updateLogs = [log, ...this.updateLogs].slice(0, 500);
      writeJson(`${NAMESPACE}update_logs`, this.updateLogs);
    },

    updateStats(): void {
      const day = today();
      const ym = monthOf(day);
      const todayRecords = this.attendance.filter((r) => r.date === day);
      const monthOt = this.overtime.filter((o) => monthOf(o.date) === ym);
      this.stats = {
        totalEmployees: this.employees.length,
        todayPresent: todayRecords.filter((r) => r.status === "present" || r.status === "late").length,
        todayAbsent: todayRecords.filter((r) => r.status === "absent").length,
        pendingLeave: this.leaveRequests.filter((l) => l.status === "pending").length,
        todayOTHours: 0,
        monthOTHours: monthOt.reduce((sum, o) => sum + (parseFloat(String(o.hours)) || 0), 0)
      };
    },

    exportSnapshot(): Record<string, unknown> {
      return {
        employees: this.employees,
        attendance: this.attendance,
        leaveRequests: this.leaveRequests,
        overtime: this.overtime,
        settlements: this.settlements,
        departments: this.departments,
        orgStructure: this.orgStructure,
        performance: this.performance,
        exportedAt: new Date().toISOString()
      };
    },

    importSnapshot(data: Record<string, unknown>): boolean {
      if (!data || !Array.isArray(data.employees)) return false;
      this.employees = data.employees as HrEmployee[];
      this.attendance = Array.isArray(data.attendance) ? (data.attendance as HrAttendanceRecord[]) : [];
      this.leaveRequests = Array.isArray(data.leaveRequests) ? (data.leaveRequests as HrLeaveRequest[]) : [];
      this.overtime = Array.isArray(data.overtime) ? (data.overtime as HrOvertimeRecord[]) : [];
      this.settlements = Array.isArray(data.settlements) ? (data.settlements as HrSettlement[]) : [];
      this.departments = Array.isArray(data.departments) ? (data.departments as string[]) : ["交付运维部", "设施运维部"];
      this.orgStructure = data.orgStructure as HrOrgNode;
      this.performance = Array.isArray(data.performance) ? (data.performance as HrPerformanceScore[]) : [];
      writeJson(`${NAMESPACE}employees`, this.employees);
      writeJson(`${NAMESPACE}attendance`, this.attendance);
      writeJson(`${NAMESPACE}leave_requests`, this.leaveRequests);
      writeJson(`${NAMESPACE}overtime`, this.overtime);
      writeJson(`${NAMESPACE}settlements`, this.settlements);
      writeJson(`${NAMESPACE}departments`, this.departments);
      writeJson(`${NAMESPACE}org_structure`, this.orgStructure);
      writeJson(PERFORMANCE_KEY, this.performance);
      this.updateStats();
      return true;
    },

    resetData(): void {
      const keys = [
        "initialized",
        "employees",
        "attendance",
        "leave_requests",
        "overtime",
        "settlements",
        "departments",
        "org_structure",
        "stats"
      ];
      for (const key of keys) {
        localStorage.removeItem(NAMESPACE + key);
      }
      localStorage.removeItem(PERFORMANCE_KEY);
      localStorage.removeItem(AI_MESSAGES_KEY);
      this.load();
    },

  }
});
