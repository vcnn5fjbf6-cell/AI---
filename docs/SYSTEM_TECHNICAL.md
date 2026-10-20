# 数据中心第三方运营综合管理系统 — 系统技术文档

> 适用系统：数据中心第三方运营综合管理系统（酒仙桥区域运维平台）
> 文档范围：整体架构、模块划分、认证权限、业务流程、数据模型、外部集成、运行与部署
> 最后更新：2026-10-15
> 相关文档：`DEVELOPMENT_GUIDE.md`（开发指南）、`DEPLOYMENT_MANUAL.md`（部署手册）、`USER_MANUAL.md`（使用手册）、`HR_MANAGEMENT_TECHNICAL.md`（人力资源模块）、`FEISHU_INTEGRATION_SETUP.md`（飞书接入）、`system-architecture-by-module.md`（按模块架构）

---

## 1. 系统概述

本系统面向数据中心第三方运营场景，统一承载以下业务域：

| 业务域 | 主要内容 |
|--------|----------|
| 采购管理 | 采购申请、审批链路（楼长/经理/区域经理/总监）、月度需求池、供应商报价与比价、换品复核、超价复核、中标与订单协同、发票/签收单归档、售后（含 14 天退换货） |
| 工作台 | 统一请求队列、待办/已办/抄送、关注、公告、跨模块聚合 |
| 平台管理 | 用户/角色/权限、应用准入、部门与审批路由、资产目录、AI 知识与技能治理、集成与规则设置、上线管理、系统更新、各类日志、意见反馈、数据库管理 |
| 人力资源（HR） | 人员信息、考勤/请假/加班、绩效、成本结算、AI 智能问答、人员权限、飞书绑定与楼长月度更新提醒 |
| EHS | 隐患、环境与危废、PPE 与应急物资、事故事件、作业许可（含对外免登录申请/审批）、EHS 智能助手 |
| 工程外包 | 外包看板、工单、入场与人员、验收与结算、服务商与目录、配置、分析、工时 |
| 灾害预警 | 预警记录与预警设置（气象数据接入、定时巡检与推送） |
| 维保/续保等 | 平台内按应用编码（appCode）组织的其它业务应用 |

前端为单页应用（SPA），后端为单体 Spring Boot 服务；面向多个业务应用（采购、平台、维保、EHS、外包）通过**应用准入（appCode）**统一治理。

## 2. 技术栈

| 层 | 技术 |
|----|------|
| 前端 | Vue 3 + TypeScript + Vite 8、Vue Router、Pinia、Element Plus、ECharts、GridStack、SheetJS（本地 vendor） |
| 后端 | Spring Boot 3.4.x（Java 17 编译目标）、MyBatis-Plus、Spring Security Crypto（BCrypt）、JWT、Spring Scheduling、Spring Mail、springdoc-openapi |
| 数据库 | MySQL 8（当前开发库 95 张表） |
| 缓存/检索（可选） | Redis、Qdrant（AI 向量检索） |
| 集成 | 飞书（登录/机器人/审批卡片/通讯录）、Dify（AI 工作流与对话）、中央气象台预警接口、电商链接解析 |
| 部署 | Docker Compose（生产/测试）、GitHub Actions + Gitee Go（双流水线）、上线管理控制面 |

## 3. 总体架构

```
┌──────────────────────────── 浏览器（SPA）────────────────────────────┐
│  Vue3 + Element Plus  |  路由与菜单按角色/appCode 过滤  |  Pinia 状态  │
└───────────────┬──────────────────────────────────────────────────────┘
                │  /api/*（JWT Bearer）
┌───────────────▼──────────────────────────────────────────────────────┐
│                     后端 Spring Boot（单体，8080/8081）               │
│  AuthInterceptor（认证 + 应用准入 + API 权限）                        │
│  modules: auth / system / platform / org / procurement / supplier     │
│           approval / workbench / ehs / outsourcing / ai / integration │
│  定时任务：采购提醒、报价、超价复核、审批卡片、灾害预警、HR 提醒等     │
└───────┬───────────────────────┬──────────────────────┬───────────────┘
        │                       │                      │
   ┌────▼────┐            ┌─────▼─────┐          ┌─────▼─────┐
   │ MySQL 8 │            │  Redis    │          │  Qdrant   │
   │ 业务数据 │            │ 缓存/会话  │          │ AI 向量库  │
   └─────────┘            └───────────┘          └───────────┘
        │
   ┌────▼─────────────────────────────────────────────────────┐
   │ 外部系统：飞书开放平台 / Dify / 气象接口 / 邮件 SMTP       │
   └──────────────────────────────────────────────────────────┘
```

**关键设计取舍**

- 后端为**单体模块化**：按业务域分包（`modules/*`），共享统一认证、权限、异常与返回结构，避免分布式复杂度。
- 前端为**单页应用**：菜单与路由均由 `roles` + `appCode` + 权限点驱动，同一套前端支撑多业务应用。
- 数据库变更采用**增量脚本 + 上线管理**：不使用 Flyway，历史环境通过 `backend-springboot/sql/zz_*.sql` 幂等脚本逐次演进。

## 4. 代码结构

### 4.1 后端（`backend-springboot`）

```
src/main/java/com/company/procurement/
├── ProcurementApplication.java          # 启动入口
├── common/                              # 通用能力
│   ├── security/   认证上下文、JWT、AuthInterceptor、密码加密
│   ├── config/     全局配置（Web、调度、跨域等）
│   ├── result/     统一返回 Result/PageResult
│   ├── exception/  业务异常与全局处理
│   └── export/ i18n/ handler/ dto/ entity/ controller/
└── modules/
    ├── auth/         登录（账号密码 / 飞书 OAuth）、会话与身份解析
    ├── system/       用户、角色、权限、部门
    ├── platform/     权限准入、反馈、数据库管理、开发者中心资料
    ├── org/          组织与部门审批路由（org_scope 等）
    ├── procurement/  采购申请、订单、资产目录、AI 助手、设置、BI
    ├── supplier/     供应商、报价、比价、中标、售后
    ├── approval/     审批实例/任务/记录（通用审批引擎）
    ├── workbench/    统一待办聚合、关注、公告
    ├── ehs/          EHS 各子域与对外免登录许可
    ├── outsourcing/  工程外包
    ├── ai/           AI 知识与技能治理（知识库、向量、提示词）
    └── integration/  飞书（登录/机器人/审批卡片/通讯录）等外部集成
```

- REST 控制器共 **36 个**，路径前缀集中在：`/api/auth`、`/api/workflow*`、`/api/procurement/*`、`/api/supplier`、`/api/admin/*`、`/api/ehs*`、`/api/public/ehs`、`/api/workbench`、`/api/integrations/*`。
- 统一返回结构：`{ success, code, message, data }`。
- 鉴权：`AuthInterceptor` 负责登录态、应用准入与 API 权限校验；管理员（`SUPER_ADMIN/SYS_ADMIN/ADMIN`）与 HR 管理角色具备相应放行规则。

### 4.2 前端（`frontend-vue`）

```
src/
├── main.ts / App.vue / styles.css
├── router/index.ts          # 全部路由（按角色/appCode 控制）
├── config/menu.ts           # 左侧菜单树与过滤规则（filterMenusByRoles）
├── layout/AppLayout.vue     # 布局：侧边栏、头部、消息、反馈
├── stores/                  # auth（登录态/飞书/HR 本地登录）、hr（HR 数据层）
├── api/                     # 按模块拆分的接口封装（axios 实例 http.ts）
├── components/              # 通用组件（vnet 面板、反馈弹窗等）
├── pages/                   # 页面（按业务域分目录）
└── vendor/                  # 本地第三方库（SheetJS）
```

页面目录：`admin, approval, dashboard, disaster, ehs, feishu, hr, login, maintenance, mobile, order, outsourcing, platform, procurement, procurementVnet, reconciliation, supplier, system, workbench`。

## 5. 认证与权限

### 5.1 登录方式

| 方式 | 说明 |
|------|------|
| 账号密码 | 用户名/姓名/工号 + 密码；BCrypt 校验，支持同名多账号二次选择身份 |
| 飞书授权登录 | OAuth 跳转飞书授权，回调 `/login?code&state` → 后端换取用户信息并匹配系统账号 |
| HR 本地账号 | 仅移动端/HR 场景，存浏览器 localStorage（`personnel_users`），token 形如 `hr-local-<username>` |

登录成功签发 JWT（含 userId、username、sessionSecret），后续请求通过 `Authorization: Bearer <token>`；`session_secret` 变化即旧会话失效。

### 5.2 权限模型

1. **角色（RBAC）**：`sys_role` / `sys_user_role` / `sys_permission` / `sys_role_permission`。
2. **应用准入（appCode）**：`platform_user_app_access` 控制账号可进入哪些应用（`PROCUREMENT`/`PLATFORM`/`MAINTENANCE`/`EHS`/`OUTSOURCING`）。
3. **数据范围**：`data_scope_type`（`ALL`/`REGION`/`SELF`）与部门/机房范围（`org_scope`、`org_subject_scope`）决定可见数据；
4. **管理员判定**：`user_type` 或角色命中 `SUPER_ADMIN/SYS_ADMIN/ADMIN` 即视为系统管理员，自动获得通配权限 `*:*:*` 与全部应用准入。
5. **前端表现**：菜单 `roles/appCode/hiddenForRoles`、路由 `meta.roles`、按钮 `v-perm` 与工具函数 `utils/permission.ts` 统一控制。

## 6. 核心业务流程（采购主线）

```
需求人提交采购申请
   → 楼长初审（任一通过）
   → 常规：进入当月需求池（每月 20 日推送经理）/ 紧急：直接进入报价
   → 经理审批（可按单项驳回）→ 区域经理（>5000 元）→ 总监（>10 万元）
   → 供应商报价（≥3 家；支持替换商品、单项放弃）
   → 报价复核 / 换品复核（原商品与替换商品对比，涨幅超 13% 标红）
   → 中标确认（供应商确认）→ 订单协同（备货、发货、签收）
   → 发票与签收单归档 → 付款/结算
   → 售后（14 天退换货、退款）
```

要点：

- **月度需求池**：每月固定日聚合、按机房/城市汇总推送供应商。
- **超价复核**：中标单价高于参考价 13% 时进入楼长 24 小时复核（剩余 12h/2h/逾期提醒）。
- **换品复核**：供应商替换商品需楼长确认，替换报价不参与基准价计算。
- **审批引擎**：`approval_instance/approval_task/approval_record` 支撑通用审批；按金额门槛（区域 5000、总监 100000）与部门路由（`procurement_approval_route`）逐级流转。
- 其它业务域：EHS 作业许可（含对外免登录申请/审批）、工程外包（工单→入场→验收→结算）、灾害预警（定时巡检→记录→推送）、HR（人员/考勤/请假/加班/绩效/成本 + 楼长月度更新）。

## 7. 数据模型（开发库 95 张表，按域归类）

| 域 | 主要表 |
|----|--------|
| 认证与系统 | `sys_user`、`sys_role`、`sys_permission`、`sys_user_role`、`sys_role_permission`、`sys_region`、`sys_department` |
| 平台与准入 | `platform_unit`、`platform_unit_app_scope`、`platform_user_app_access`、`platform_permission_request`、`platform_feedback(_attachment)`、`platform_import_file_log` |
| 组织与路由 | `org_scope`、`org_subject`、`org_subject_scope`、`procurement_approval_route`、`procurement_approval_level(_user)`、`procurement_approval_policy/relation` |
| 采购申请 | `purchase_request`、`purchase_request_item`、`purchase_request_no_sequence`、`monthly_procurement_demand`、`procurement_asset_catalog_item`、`procurement_department_limit`、`personal_labor_item_record` |
| 审批 | `approval_instance`、`approval_task`、`approval_record` |
| 供应商与报价 | `supplier`、`supplier_qualification`、`supplier_quote`、`supplier_quote_item`、`temporary_supplier_recommendation(_item)` |
| 订单与履约 | `order_invoice_submission`、`order_invoice_attachment`、`procurement_after_sale` |
| 工作台 | `workbench_follow`、`workbench_copy`、`workbench_notice`、`procurement_message_read` |
| AI 与 BI | `ai_knowledge_base/chunk/vector_document/prompt_template/skill_definition/query_log/...`、`bi_dataset/metric/dashboard/widget/...`、`procurement_ai_session/candidate/message_log` |
| EHS | `ehs_registry_record`、`ehs_permit`、`ehs_public_access_token`、`ehs_notification_log`、`ehs_knowledge_document`、`ehs_ai_session/message` |
| 灾害预警 | `disaster_weather_record` |
| 集成与设置 | `procurement_setting`（分组 API/RULE/FEISHU 等）、`supplier_feishu_binding`、`supplier_feishu_quote_notice`、`feishu_approval_card_notification`、`hr_update_reminder_log` |

> HR 模块的人员/考勤/请假/加班/绩效数据默认存浏览器 localStorage（`personnel_management_*`），仅楼长更新提醒日志落库。

## 8. 外部集成

| 集成 | 用途 | 关键配置 |
|------|------|----------|
| 飞书登录 | 企业账号免密登录 | `FEISHU_AUTH_ENABLED`、`FEISHU_APP_ID`、`FEISHU_APP_SECRET`、`FEISHU_REDIRECT_URI`（见 `feishu.env`） |
| 飞书机器人 | 采购 AI 助手（图片/文字需求识别与回复） | `feishu_bot_enabled` + App 凭据 |
| 飞书审批卡片 | 审批任务与供应商报价/中标卡片推送、按钮回调 | `feishu_approval_card_*`（Verification Token、Encrypt Key、系统基址等） |
| 飞书通讯录 | 组织与人员同步、部门 ID 查询 | `feishu_root_department_id`、`feishu_department_id_type` |
| Dify | 采购需求 AI 分析/预审、AI 问答 | `DIFY_PROCUREMENT_*`（API Key、工作流/对话地址、响应模式） |
| 中央气象台 | 灾害预警数据源 | `disaster_weather_*` 设置项 |
| 邮件 SMTP | 通知类邮件 | `spring.mail.*` 环境变量 |
| Qdrant | AI 知识向量检索（可选） | `QDRANT_ENABLED`、`QDRANT_URL`、`QDRANT_COLLECTION_PREFIX` |

飞书与 AI 相关设置在系统中分两处维护：
- **平台管理 → 平台集成设置**：飞书应用、卡片、供应商通知、API 变量等；
- **人力资源管理 → 系统设置 → 飞书接入配置**：同一套飞书参数（HR 侧入口）。

## 9. 定时任务清单

| 任务类 | 作用 |
|--------|------|
| `WorkflowServiceImpl`（@Scheduled） | 审批链路的时效任务（超时/流转） |
| `MonthlyProcurementServiceImpl` | 月度需求池推送（如每月 20 日推经理） |
| `OverPriceReviewReminderScheduler` | 超价复核提醒（剩余 12h/2h/逾期） |
| `FeishuApprovalCardScheduler` | 审批卡片心跳推送 |
| `FeishuApprovalCardLongConnection` | 飞书卡片长连接回调（可选） |
| `SupplierFeishuNotificationServiceImpl` | 供应商报价/中标卡片通知 |
| `SupplierQuoteServiceImpl` / `SupplierQuoteAssetReviewService` | 报价倒计时与资产/成色复核 |
| `DisasterWeatherScheduler` | 灾害预警定时巡检与推送 |
| `HrUpdateReminderScheduler` | 每月 1–10 日 09:00 提醒楼长更新人员信息；11 日起标记逾期 |
| `ImportFileLogCleanupScheduler` | 导入文件日志清理 |

## 10. 配置项

### 10.1 环境变量（后端）

| 变量 | 说明 |
|------|------|
| `SERVER_PORT` | 服务端口（默认 8080；本机因端口冲突使用 8081） |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | MySQL 连接 |
| `REDIS_HOST/PORT/PASSWORD` | Redis（可选） |
| `JWT_SECRET` / `JWT_EXPIRE_SECONDS` | 令牌密钥与有效期 |
| `FEISHU_*` | 飞书登录/机器人/卡片/通讯录 |
| `DIFY_PROCUREMENT_*` | Dify AI 接入 |
| `QDRANT_*`、`M3_VISION_*` | 向量库与视觉模型 |
| `EHS_ENABLED`、`EHS_PUBLIC_ENABLED`、`OUTSOURCING_ENABLED` | 业务应用开关 |
| `APP_SCHEDULING_ENABLED`、`APP_SCHEDULING_POOL_SIZE` | 定时任务开关与线程池 |

### 10.2 数据库动态设置

`procurement_setting` 按 `setting_group` 分组（`API`/`RULE`/`FEISHU`/`MAIL` 等），平台管理页面维护，部分项留空时回退到环境变量。

## 11. 本地开发与运行

### 11.1 标准方式（见 `DEVELOPMENT_GUIDE.md`）

```powershell
# 后端
cd backend-springboot
mvn.cmd spring-boot:run

# 前端
cd frontend-vue
npm.cmd ci
npm.cmd run dev
```

- 前端默认 `http://localhost:5173`，`/api` 代理到后端。
- 健康检查：`/health`；开发环境 Swagger：`/swagger-ui/index.html`。

### 11.2 本机开发环境（当前工作机）

| 组件 | 位置/端口 | 说明 |
|------|-----------|------|
| MySQL 8.0.42（便携版） | `C:\Users\admin\.devstack\mysql-8.0.42-winx64`（数据目录 `mysql-data`） | 用户目录部署，无需管理员权限；库名 `procurement_platform`，账号 `root/root` |
| 后端 | `http://localhost:8081` | 8080 被本机“内部知识平台”占用，故改用 8081 |
| 前端 | `http://localhost:5173` | 通过 `VITE_API_TARGET` 指向 8081 |
| 启动脚本 | `start-system.bat` / `stop-system.bat` | 启动/停止三件套；启动前自动加载 `feishu.env` |
| 常驻守护 | `service-supervisor.ps1` | 每 10 秒检查 MySQL、后端、前端，异常退出自动拉起；日志位于 `.runtime/logs` |
| 开机自启 | `install-autostart.bat` / `uninstall-autostart.bat` | 安装/移除 Windows 计划任务 `AI-HR-Platform-Supervisor`（SYSTEM 账户，开机自动运行） |

管理员与楼长测试账号的登录凭据由部署管理员通过安全渠道配置，本文不记录明文口令。
安装一次 `install-autostart.bat` 后，系统重启或服务异常退出都会自动恢复，无需反复运行 `start-system.bat`。
数据库初始化（新环境）：`sql/schema.sql` + `sql/seed.sql` + `sql/seed_identity.sql`，再执行全部 `sql/zz_*.sql` 幂等脚本。

## 12. 部署与发布

| 机制 | 说明 |
|------|------|
| 生产编排 | `deploy/docker-compose.prod.yml`（MySQL/Redis/Qdrant/后端/前端 Nginx；仅对外发布前端 80） |
| 测试编排 | `deploy/docker-compose.test.yml` + `deploy/db/test-init/`（独立测试库与安全脚本） |
| 上线控制面 | `deploy/docker-compose.release-control.yml` + `RELEASE_CONTROL_*` 环境变量 |
| CI/CD | `.github/workflows/deploy.yml`（生产）、`deploy-test.yml`（测试）、`deploy-release-control.yml`（控制面）、`verify-pr.yml`；`.workflow/gitee-test-*.yml`（Gitee 测试链路） |
| 数据库变更 | 新增/修改 `backend-springboot/sql/zz_*.sql`（幂等），由发布流程在切换版本时按提交差异执行 |
| 发布守则 | 生产发布需已通过测试批次、镜像 digest 与 SHA 校验；候选环境先联调再切换对外前端 |

## 13. 质量与验证

| 项目 | 命令/方式 |
|------|-----------|
| 前端类型检查 | `cd frontend-vue && npm run typecheck` |
| 前端构建 | `npm run build` |
| 前端专项检查 | `npm run check:access`、`check:developer-center`、`check:procurement-precheck` 等 |
| 后端编译/测试 | `mvn -q -DskipTests package` / `mvn test`（JUnit + Mockito） |
| 接口文档 | 开发环境 Swagger / `docs/backend-api-routes.csv` |
| 手工冒烟 | 登录 → 工作台 → 采购申请/订单 → 平台管理 → 人力资源 等主路径 |

## 14. 已知限制与改进建议

1. **HR 业务数据仅存浏览器 localStorage**：换设备/清缓存会丢失，跨端不同步；建议后续落库（可复用现有权限与审计体系）。
2. **HR 飞书绑定与人员更新日志同样存本地**，多端不可见；如需多人协作，应迁移到后端表。
3. **本机环境端口为 8081**（8080 被其它系统占用），部署到标准环境时应使用默认 8080 并将 Vite 代理指向后端。
4. **飞书登录依赖环境变量**（非数据库配置）：修改后必须重启后端；机器人/卡片还需要飞书可达的公网地址。
5. **单体架构**：随模块增多，构建与启动时间上升；如规模继续扩大，可按 `modules/*` 边界拆分服务。
6. **无 Flyway**：数据库演进依赖增量脚本规范，缺少自动版本校验，建议逐步引入迁移工具或基线快照。

## 15. 相关文档索引

| 文档 | 内容 |
|------|------|
| `docs/DEVELOPMENT_GUIDE.md` | 开发环境、代码规范、数据库说明、联调方式 |
| `docs/DEPLOYMENT_MANUAL.md` | 生产/测试部署、发布流程、服务器清单 |
| `docs/RELEASE_CONTROL_OPERATOR_GUIDE.md` | 上线管理操作说明 |
| `docs/MODULE_DEVELOPMENT_GUIDE.md` | 模块开发与接入规范 |
| `docs/USER_MANUAL.md` | 使用手册 |
| `docs/HR_MANAGEMENT_TECHNICAL.md` | 人力资源模块技术文档 |
| `docs/FEISHU_INTEGRATION_SETUP.md` | 飞书接入配置步骤 |
| `docs/system-architecture-by-module.md` | 按模块划分的系统架构 |
| `docs/AI_KNOWLEDGE_SKILL_GOVERNANCE.md` | AI 知识与技能治理 |