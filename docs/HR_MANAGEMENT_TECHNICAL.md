# 人力资源管理模块技术文档

> 适用系统：数据中心第三方运营综合管理系统（酒仙桥区域运维平台）
> 适用范围：人力资源（HR）模块在**本仓库**中的实现与接入说明
> 最后更新：2026-09-03

---

## 1. 概述

人力资源模块是主系统内的一个**前端原生业务模块**，提供人员信息、考勤、请假、加班、绩效、成本结算、AI 智能问答、人员权限、系统设置等功能，并附带一个可选的**后端飞书提醒定时任务**。

当前实现特点：

- 业务数据默认保存在**浏览器 localStorage**（按访问地址隔离），尚未落到主系统 MySQL 业务表。
- 模块已完整接入主系统：路由、菜单、登录态、权限角色过滤与平台用户同步均可正常工作。
- 成本相关费率（外包加班费、请假扣费）与人员信息修改**自动重算并同步**到数据看板、成本、考勤等功能。

## 2. 模块形态与技术栈

| 项 | 说明 |
|----|------|
| 形态 | 主系统内原生 Vue 模块（非 iframe / 独立应用） |
| 前端 | Vue 3 + TypeScript + Vite 8 |
| UI | Element Plus |
| 状态 | Pinia（`useHrStore`） |
| 路由 | Vue Router（挂载在主布局 `/` 的 children 下） |
| 文件解析 | SheetJS（`src/vendor/xlsx.full.min.js` 本地拷贝） |
| 后端支撑 | Spring Boot 定时任务（可选部署）+ MySQL 增量脚本 |
| 本地 AI | Node 轻量 mock（OpenAI 兼容） |

## 3. 代码位置（本仓库实际路径）

```
frontend-vue/src/pages/hr/               # HR 页面
├── HrDashboard.vue        # 人力资源数据看板 /hr/dashboard
├── HrEmployees.vue        # 人力资源（员工）/hr/employees
├── HrAttendancePage.vue   # 考勤管理（考勤/请假/加班）/hr/attendance
├── HrPerformance.vue      # 绩效管理 /hr/performance
├── HrCost.vue             # 成本管理 /hr/cost
├── HrAiAssistant.vue      # AI 智能问答 /hr/ai
├── HrPermission.vue       # 人员权限分配 /hr/permission
├── HrSettings.vue         # 系统设置 /hr/settings
├── hr-seed.ts             # 示例员工数据（不自动入库，仅防误用清理）
└── hr-utils.ts            # 岗位分组/工具函数（Excel/CSV 解析、导出等）

frontend-vue/src/stores/hr.ts            # HR 数据层（Pinia + localStorage）
frontend-vue/src/stores/auth.ts          # 主认证仓库（含 HR 本地登录支持）
frontend-vue/src/pages/mobile/MobileLoginPage.vue  # 手机登录终端 /m/login
frontend-vue/src/pages/login/LoginPage.vue        # PC 登录页（账号/飞书）
frontend-vue/src/vendor/xlsx.full.min.js          # SheetJS 本地依赖
frontend-vue/src/vendor/xlsx.ts                   # xlsx 类型声明
frontend-vue/public/hr-bg.png            # HR 背景图（页面可选使用）
frontend-vue/src/router/index.ts         # 路由接入（见 §4）
frontend-vue/src/config/menu.ts          # 菜单接入（见 §4）

backend-springboot/src/main/java/com/company/procurement/modules/integration/feishu/scheduler/HrUpdateReminderScheduler.java  # 楼长更新提醒定时任务
backend-springboot/sql/zz_20260818_hr_update_reminder.sql   # hr_update_reminder_log 建表
backend-springboot/sql/zz_20260811_platform_admin_account.sql # 平台管理员 admin 账号（与 HR 无强耦合）
backend-springboot/sql/zz_20260902_create_admin_account.sql  # 管理员 admin2026 账号

docs/HR_MANAGEMENT_TECHNICAL.md          # 本文档
```

## 4. 系统接入方式

### 4.1 路由（`frontend-vue/src/router/index.ts`）

模块挂载在主布局 `/`（AppLayout）的 `children` 中，紧随 `/workbench` 之后：

| 路径 | 页面组件 | 名称 | 可见角色 |
|------|----------|------|----------|
| `/hr-management`、`/hr` | 重定向到 `/hr/dashboard` | - | 非 SUPPLIER |
| `/hr/dashboard` | HrDashboard | HrDashboard | hrAdminRoles |
| `/hr/employees` | HrEmployees | HrEmployees | hrLeaderRoles |
| `/hr/attendance` | HrAttendancePage | HrAttendance | 所有（非 SUPPLIER） |
| `/hr/leave`、`/hr/overtime` | 重定向 `/hr/attendance` | - | 非 SUPPLIER |
| `/hr/performance` | HrPerformance | HrPerformance | hrAdminRoles |
| `/hr/cost` | HrCost | HrCost | hrAdminRoles |
| `/hr/org` | 重定向 `/hr/dashboard` | - | 非 SUPPLIER |
| `/hr/ai` | HrAiAssistant | HrAiAssistant | hrAdminRoles |
| `/hr/permission` | HrPermission | HrPermission | hrAdminRoles |
| `/hr/settings` | HrSettings | HrSettings | hrAdminRoles |

角色常量（router 与 menu 各自定义，保持一致）：

```ts
const adminRoles = ["SUPER_ADMIN"];
const hrAdminRoles = [...adminRoles, "ADMIN", "SYS_ADMIN"];
const hrLeaderRoles = [...hrAdminRoles, "FLOOR_LEADER"];
```

手机端路由（顶层）：

```ts
{ path: "/m", redirect: "/m/login" },
{ path: "/m/login", name: "MobileLogin", component: () => import("../pages/mobile/MobileLoginPage.vue"), meta: { title: "手机登录", public: true } }
```

### 4.2 导航入口

左侧栏保留「人力资源管理」一级菜单（`frontend-vue/src/config/menu.ts`，`path: "/hr"`，`hiddenForRoles: ["SUPPLIER"]`，`alwaysVisible: true`），含 7 个子菜单：数据看板 / 人力资源 / 考勤管理 / 绩效管理 / 成本管理 / 权限管理 / 系统设置。

**「AI 智能问答」不在左侧栏、也不在工作台**，入口放在人力资源模块的「数据看板」页内：`HrDashboard.vue` 隐藏了页内副标题“人力资源数据看板”标题栏（`VnetPage` 标题置空并隐藏其头部），把「AI 智能问答」卡片（`hr-ai-entry`）放在页面顶部原副标题位置；管理员点击卡片进入 `/hr/ai`。

HR 各页面路由保持不变，仍可直接访问 `/hr/*`。

### 4.3 认证仓库的 HR 本地登录支持（`stores/auth.ts`）

为支撑 HR 手机端本地账号，主认证仓库新增（不影响原飞书/账号登录）：

- 常量 `HR_LOCAL_PROFILE_KEY = "hr_local_profile"`。
- `loginWithHrLocalUser(username, password)`：从 localStorage `personnel_users` 校验本地账号；内置兜底 `admin/admin123`；登录 token 形如 `hr-local-<username>`，并写入本地档案。
- `fetchProfile()`：token 以 `hr-local-` 开头时从本地档案恢复用户（不请求 `/api/auth/me`）。
- `logoutLocal()`：清理 `hr_local_profile`。

> 说明：HR 本地账号仅存在于浏览器 localStorage，跨设备/清缓存会失效；正式多端登录仍走平台账号（`admin2026` 等）。

### 4.4 平台用户同步

HR「系统设置 → 从平台同步人员」调用主系统用户分页接口 `getUserPageApi({ pageNo:1, pageSize:500 })`（`/api/admin/users/page`），按姓名/工号去重后并入 HR 员工列表（`mergePlatformUsers`），并把 `userType` 映射为岗位（如 ENGINEER→工程师、FLOOR_LEADER→楼长等）。同步后自动重算费率与统计。

## 5. 数据模型与本地存储

### 5.1 localStorage 键（统一前缀 `personnel_management_`）

| 键 | 内容 |
|----|------|
| `personnel_management_employees` | 人员列表 |
| `personnel_management_attendance` | 考勤记录 |
| `personnel_management_leave_requests` | 请假记录 |
| `personnel_management_overtime` | 加班记录 |
| `personnel_management_settlements` | 月度结算缓存 |
| `personnel_management_update_logs` | 人员更新日志（含逾期标记，保留最近 500 条） |
| `personnel_management_departments` | 部门列表（已弱化） |
| `personnel_management_org_structure` | 组织架构（页面已停用） |
| `personnel_management_stats` | 统计缓存 |
| `personnel_management_initialized` | 初始化标记 |
| `personnel_performance` | 绩效评分 |
| `personnel_ai_config` / `personnel_ai_messages` | AI 配置与对话 |
| `personnel_feishu_webhook` | 飞书机器人 Webhook |
| `personnel_users` | HR 本地账号 |
| `hr_local_profile` | HR 本地登录档案 |

### 5.2 人员对象 HrEmployee（核心字段）

```
id, name, gender, position(工作岗位), company(人员性质), personAttribute(人员属性),
workAttribute(工作属性), salary(月薪), phone, email, region, room, building,
status(在职/离职), joinDate, leaveDate, department, employeeNo, platformUserId,
feishuUserId / feishuOpenId / feishuUnionId（飞书绑定）,
aiProfile, resumeData/Name, credentialData/Name, createdAt, updatedAt
```

### 5.3 业务记录对象

- 考勤 `HrAttendanceRecord`：`id, employeeId, date, checkIn?, checkOut?, status(present/late/absent)`
- 请假 `HrLeaveRequest`：`id, employeeId, type, startDate, endDate, days, reason?, status(pending/approved/rejected), lvDeduct?, lvRate?`
- 加班 `HrOvertimeRecord`：`id, employeeId, date, hours, reason?, status(pending/approved/rejected), otPay?, otRate?`
- 月度结算 `HrSettlement` / `HrSettlementRecord`：按人汇总 `baseSalary, otHours, otPay, lvDays, lvHours, lvDeduct, otRate, lvRate, netPay`
- 人员更新日志 `HrUpdateLog`：`id, date, yearMonth, operator?, action(add/update/remove), employeeName, building?, late, createdAt`

> 记录仅存 `employeeId`，页面展示的姓名/部门通过当前员工表实时解析，修改员工信息会自动反映到所有列表。

## 6. 业务规则（当前实现）

### 6.1 字典

- 人员性质 `company`：`外包`、`420`、`102`、`102实习生`、`426`（判断是否外包 = 字段含“外包”）。
- 人员属性 `personAttribute`：`交付`、`设施`。
- 工作属性 `workAttribute`：`5*8白班`、`7*24值班`。
- 岗位 `position`：交付工程师、值班员、交付值班员、交付运维工程师、设施运维工程师、交付运维值班工程师、设施运维值班工程师、值班长、高级工程师、消防/电气/暖通/弱电工程师（助理）、综合岗、前台、交付主管等（长/短名并存）。

### 6.2 加班费规则（`stores/hr.ts` 的 `OT_FIXED_RATES`）

只对**外包人员**启用固定单价；**人员性质为 420 的人员不涉及本标准**（费用 0）。岗位名按“包含”匹配，长/短名均可：

| 类别 | 匹配岗位 | 费率 |
|------|----------|------|
| 标准工程师 | 交付运维工程师、设施运维工程师、交付工程师 | 72 元/小时 |
| 值班员 | 交付运维值班工程师、设施运维值班工程师、值班员、交付值班员 | 63.82 元/小时 |

```ts
const OT_FIXED_RATES = [
  { position: "交付运维工程师", company: "外包", rate: 72 },
  { position: "设施运维工程师", company: "外包", rate: 72 },
  { position: "交付运维值班工程师", company: "外包", rate: 63.82 },
  { position: "设施运维值班工程师", company: "外包", rate: 63.82 },
  { position: "交付工程师", company: "外包", rate: 72 },
  { position: "值班员", company: "外包", rate: 63.82 }
];
// 匹配：company 含“外包” 且 position 包含上述关键字 且 company != "420"
```

未列入标准（值班长、高级工程师、消防/电气/暖通/弱电工程师等）及非外包、420 人员加班费用为 0。

### 6.3 请假扣费规则（`LEAVE_DEDUCT_RATES`）

只对**外包人员**（`company` 含“外包”）启用：

```
扣费 = 请假天数 × 9 小时/天 × 63.35 元/小时
```

非外包人员请假扣费为 0。（请假扣费不区分岗位。）

### 6.4 月度结算（`calcSettlement` / `getOrCalcSettlement`）

按月份、按员工汇总已批准（approved）的加班与请假：

```
实发 = 基础工资(月薪或岗位档位) + 加班费 - 请假扣费（下限 0）
```

结算结果写入 `personnel_management_settlements` 作为缓存，任何记录增删改都会清空缓存，进入页面时按需重算。

### 6.5 费率自动同步（重要）

人员信息变化会自动同步到各功能，无需手动点“同步费率”：

| 触发操作 | 自动执行 |
|----------|----------|
| 人力资源页编辑人员（改岗位/性质/姓名/部门/月薪等） | `refreshRates()` 重算该人及全员加班费/请假扣费、清结算缓存；`updateStats()` 刷新统计 |
| 删除人员 | 级联删除其考勤/请假/加班，清结算缓存、刷统计 |
| 系统设置 → 从平台同步人员 | 同步完成后自动重算费率与统计 |

数据看板、成本管理、考勤管理、AI 问答等均读取同一 Pinia 仓库的实时数据，进入页面即一致。

### 6.6 楼长人员维护与「逾期更新」

- 楼长（`FLOOR_LEADER`）登录后进入「人力资源」只能看到**自己负责楼栋**的人员（按系统用户姓名匹配楼栋楼长名单）。
- 楼长可对本楼栋人员执行**新增 / 减少（删除）/ 修改**；跨楼栋操作会被拦截并提示“只能维护本楼栋人员”。
- 楼长**不能使用「清空人员」**（按钮隐藏且逻辑层二次拦截）。
- 人员新增/修改/删除都会写入更新日志（localStorage `personnel_management_update_logs`，保留最近 500 条）：日期、操作人、动作（add/update/remove）、人员、楼栋、是否逾期。
- **逾期判定：每月 10 号（含）之前更新为“按时更新”，11 号起更新为“逾期更新”。** 人力资源页顶部展示“本月人员更新 N 次 / 其中逾期更新 M 次”，管理员在数据看板可见明细。

## 7. 页面功能速览

| 页面 | 主要能力 |
|------|----------|
| 数据看板 | 总人数、本月费用/加班时长/加班费/扣费/待审批卡片；请假扣款概览、加班费用概览、楼栋人数、人员增减；每月 10 日前楼长更新提醒横幅 |
| 人力资源 | 员工增删改查、岗位/性质/属性/工作属性维护、**飞书绑定**（编辑弹窗绑定 open_id/user_id/union_id、列表显示已绑定/未绑定、按绑定状态筛选）、Excel/CSV 导入导出、AI 简介生成；**不展示月薪**（月薪数据保留，仅用于成本管理结算） |
| 考勤管理 | 打卡签到、一键到岗、考勤记录、请假申请与审批、加班申请与审批（本楼栋数据按角色过滤） |
| 绩效管理 | 手动评分、Excel/CSV 导入、排名 |
| 成本管理 | 月度结算、楼栋/公司汇总、同步费率 |
| AI 智能问答 | 本地 mock AI（OpenAI 兼容）对人员/出勤/请假/加班/成本等做问答；简历文本提取生成人员简介 |
| 人员权限分配 | HR 本地账号创建/绑定/角色分配 |
| 系统设置 | 数据导入导出(JSON)、从平台同步人员、重新计算费率、飞书 Webhook、重置数据、本地存储查看 |

## 8. 权限与可见性

| 角色 | 可见范围 |
|------|----------|
| SUPER_ADMIN / ADMIN / SYS_ADMIN | 全部 HR 功能 |
| FLOOR_LEADER（楼长） | 人力资源（仅本楼栋，可新增/减少/修改，不可清空全部）、考勤管理（本楼栋审批） |
| 普通员工 | 考勤管理中的签到、请假申请、加班申请 |
| SUPPLIER | HR 模块整体隐藏 |

楼长名单（配置于 HrDashboard / 相关页面）：百度M1-张耀；人寿-陈瑶瑶；B28-张乐斌、乔涛、郭鑫存；M3-张正勤；M5-何金强、段宝光；M6-赵新、马金礼；M6V&M5二三期-张士令、刘燚。

## 9. 后端支撑

### 9.1 楼长人员更新提醒（可选部署）

- 类：`HrUpdateReminderScheduler`（`integration.feishu.scheduler`），`@Scheduled(cron="0 0 9 * * ?", zone="Asia/Shanghai")`。
- 逻辑：每月 1–10 日每天 09:00 向所有启用的楼长（`FLOOR_LEADER`）发送飞书提醒“请在 10 日前完成本月人员信息更新”；11 日起把当月未完成记录标记为 `LATE`（对应 HR 侧的“逾期更新”判定）。
- 依赖表：`hr_update_reminder_log`（由 `backend-springboot/sql/zz_20260818_hr_update_reminder.sql` 创建，幂等）。
- 唯一键：`(user_id, year_month, remind_date)`，避免同日重复发送。
- 兼容性：MySQL 8.0.42 将 `year_month` 视为保留字，建表与 Java 原生 SQL 均已用反引号 `` `year_month` `` 包裹（兼容所有版本）。
- 前置条件：① 飞书应用已配置（`FEISHU_APP_ID/SECRET` 或集成设置）；② 楼长在系统用户中存在且 `user_type=FLOOR_LEADER`，并有邮箱/手机号或已绑定飞书 open_id/user_id，否则消息无法送达。

### 9.2 相关增量脚本

- `zz_20260818_hr_update_reminder.sql`：HR 提醒日志表。
- `zz_20260811_platform_admin_account.sql`：启用 `admin` 平台管理员（随机初始密码、首次强制改密）。
- `zz_20260902_create_admin_account.sql`：创建管理员 `admin2026`（BCrypt 密码、SUPER_ADMIN、平台/采购/维保入口）。

## 10. AI 智能问答

- 默认对接本地 mock OpenAI 兼容服务（`http://<当前访问地址>:11434/v1/chat/completions`，模型名 `mock-ai`）。
- 支持问题：总人数、出勤、请假、加班、成本、楼栋/岗位人数、人员技能简介等。
- 简历上传后提取文本并调用 AI 生成“人员技能简介”存到员工 `aiProfile`。
- 服务脚本：`frontend-vue/scripts/mock-ai-server.mjs`（独立开发辅助，不随构建打包）。
- 入口位置：**人力资源 → 数据看板** 顶部「AI 智能问答」卡片（左侧栏不含该项），点击进入 `/hr/ai`。

## 11. 手机登录

- 地址：`/m/login`（`MobileLoginPage.vue`，public）。
- 支持平台账号（调用 `authStore.login`）与 HR 本地账号（`loginWithHrLocalUser`，读 `personnel_users`）。
- 本地默认管理员：`admin / admin123`。
- HR 本地登录态 token 以 `hr-local-` 开头，仅存内存/本地档案，刷新需重新登录（或由 fetchProfile 恢复）。

## 12. 本地开发 / 验证

```bash
cd frontend-vue
npm run typecheck     # 类型检查
npm run build         # 生产构建
```

一键启动本机全栈（数据库/后端/前端）：双击项目根目录 `start-system.bat`；停止：`stop-system.bat`。
访问 `http://localhost:5173`，管理员账号由部署管理员初始化，初始密码通过安全渠道提供，本文不记录明文口令。
楼长测试账号仅用于测试环境；登录凭据由部署管理员通过安全渠道配置，本文不记录明文口令。

## 13. 已知限制与后续演进

- 业务数据存 localStorage，按浏览器访问地址隔离（`http://localhost:5173` 与其它地址互不可见），更换设备/清缓存会丢失；需通过「系统设置 → 导出数据 / 导入数据」迁移。
- HR 本地账号按浏览器隔离，正式多端登录应使用平台账号。
- 楼长提醒依赖后端部署与飞书审批卡片/机器人配置。
- mock AI 非真实大模型，仅用于界面演示。
- 建议后续演进：把员工/考勤/请假/加班/结算落到主系统 MySQL（可复用现有权限与审计），将费率做成可配置（当前为代码常量 `OT_FIXED_RATES` / `LEAVE_DEDUCT_RATES`）。

## 14. 维护注意

1. 修改费率时同步更新 `stores/hr.ts` 常量与本文档 §6.2/6.3。
2. 新增 HR 页面需同步维护 `router/index.ts`、`config/menu.ts` 与本文档 §4/§7；并保持角色常量一致。
3. 涉及数据库时按 `backend-springboot/sql/zz_*.sql` 幂等脚本规范新增，不要改 `schema.sql` 历史。
4. 本地验证后执行 `npm run typecheck`；真实部署链路由 CI（deploy）承载。
