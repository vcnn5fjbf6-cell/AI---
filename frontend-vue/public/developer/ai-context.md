---
id: enterprise-procurement-platform-developer-context
schema_version: "1.0"
language: zh-CN
scope: internal-development
verified_date: 2026-07-27
contains_secrets: false
---

# 企业采购/维保平台 AI 开发上下文

本文是给 AI 和内部开发者的精简事实入口。它不授权生产操作，也不包含任何真实凭据、账号、生产数据或网络标识。执行任务前仍须核对目标分支的当前代码和 `AGENTS.md`。

## 1. 事实来源顺序

发生冲突时按以下顺序核对：

1. 当前任务要求与仓库根目录 `AGENTS.md`
2. 目标提交中的源码、路由、菜单、SQL 和部署配置
3. 当前部署版本生成的 `/v3/api-docs`
4. `docs/DEVELOPMENT_GUIDE.md` 与 `docs/MODULE_DEVELOPMENT_GUIDE.md`
5. `docs/system-architecture-by-module.md`
6. `docs/backend-api-routes.csv` 静态快照

不要仅凭页面文件、历史更新记录或静态接口表判断功能已经上线。

## 2. 技术与目录

- 前端：Vue 3、TypeScript、Vite 8、Vue Router 4、Pinia、Element Plus。
- 后端：Spring Boot 3、MyBatis Plus、MySQL 8、JWT、Spring Validation。
- 部署：Docker Compose；测试和生产使用独立发布边界。
- 前端源码：`frontend-vue/src/`
- 后端源码：`backend-springboot/src/main/java/com/company/procurement/`
- 数据库脚本：`backend-springboot/sql/`
- 部署配置：`deploy/`
- 项目文档：`docs/`

正式前端页面以 `frontend-vue/src/router/index.ts` 为准。存在页面文件不代表已经注册路由或获得访问权限。

## 3. 当前应用边界

当前应用准入编码：

- `PROCUREMENT`：采购、审批、报价、订单等采购业务。
- `MAINTENANCE`：维保业务。
- `PLATFORM`：平台、用户、权限和管理能力。
- `EHS`：当前代码中存在的测试环境条件模块；是否启用以目标部署的环境开关和应用准入为准，不代表已经进入生产。

`PLATFORM` 不是其他业务应用的通配权限。新增独立业务线时，应先完成方案评审，再使用独立且稳定的 `appCode`；不要为了省事复用 `PLATFORM`。

架构文档中的“规划”或“预留”不是已上线能力。模块清单必须明确状态为 `PLANNED`、`DEVELOPMENT`、`TEST` 或 `ENABLED`。

## 4. 模块最小交付物

新增或扩展模块至少提交：

1. 模块说明与 `module-manifest`。
2. 后端 Controller、Service、请求/响应模型和针对性测试。
3. 前端 API 封装、类型、页面、路由和菜单。
4. 应用准入、操作权限和对象/数据范围校验。
5. 必要的幂等 `zz_*.sql` 增量脚本。
6. OpenAPI 说明、人工验收用例和文档更新。

先判断是“扩展现有模块”还是“新增独立应用”。新增应用编码、工作台来源或流程分支属于设计决策，不能由 AI 静默创建。

## 5. 权限与数据边界

一个业务入口必须形成四层闭环：

1. 菜单：`frontend-vue/src/config/menu.ts` 标明目标应用。
2. 路由：`frontend-vue/src/router/index.ts` 使用 `meta.appCode` 或 `anyAppCodes`；需要时再加角色限制。
3. 接口：后端在 `AuthInterceptor` 的应用前缀映射或等价鉴权层登记准入要求。
4. 数据：Service 层按当前用户、部门、供应商、记录归属或流程任务校验对象范围。

必须遵守：

- 前端隐藏菜单、路由或按钮不是安全边界。
- 当前菜单与路由守卫对 `roles` 和 `permissions` 采用 OR；`roles` 为空会直接通过角色门槛，不能再依赖 `permissions` 收窄入口。
- 角色、应用准入、操作权限和对象范围是不同维度，不能互相替代。
- 直接 URL、非菜单入口、工作台列表、详情、审批、关注和导出都要应用相同的后端边界。
- 新增业务菜单或路由后必须运行 `npm.cmd run check:access`。
- 不信任前端传入的用户、部门、供应商或数据范围标识；后端应从当前身份和业务对象反查。

## 6. 流程接入红线

采购、审批、月度需求、报价、订单、售后、通知、飞书和 AI 入口必须复用已有业务服务、节点、状态和审计记录。

- 采购申请优先复用 `PurchaseRequestService`。
- 审批动作优先复用 `WorkflowService` 和现有审批任务。
- 飞书卡片和 AI 只承担输入、交互、提醒或建议，不得直接写正式流程状态。
- 不得用新表、直接 SQL、影子状态或外部回调绕过现有服务。
- 确需新增流程分支、状态机或并行写入路径时，先取得业务负责人明确确认。

## 7. 后端与接口

- 新代码放入匹配业务域的 `modules/<domain>/`。
- Controller 负责协议与校验，业务规则放在 Service；不要在 Controller 直接改流程状态。
- 请求模型使用后端校验，错误返回不暴露内部异常、SQL 或敏感配置。
- 新接口必须说明方法、路径、鉴权、请求、响应、错误码和对象权限。
- 实时接口合同以当前部署生成的 OpenAPI 为准。

OpenAPI 地址：

- 本地 JSON：`http://localhost:8080/v3/api-docs`
- 本地 Swagger：`http://localhost:8080/swagger-ui/index.html`
- 测试环境 JSON：`/v3/api-docs`
- 测试环境 Swagger：`/swagger-ui/index.html`

Swagger 通过 **Authorize** 使用 bearer JWT，不会自动读取平台 `localStorage` 中的登录凭据。只使用当前测试会话的短期 JWT，并先调用只读的 `GET /api/auth/me`。

接口统一返回 `{ success, code, message, data }`。部分鉴权、校验和业务失败仍使用 HTTP 200 包装，AI 和人工验收都必须读取 `success` 与 `code`，不能把 HTTP 200 直接判断为成功。

生产环境默认关闭 OpenAPI。不要为了调试把 Swagger 暴露到不受信任网络。

## 8. 数据库迁移

- `backend-springboot/sql/schema.sql` 会删除已有表，只能用于独立、可丢弃的全新本地数据库。
- 当前未启用 Flyway；`db/migration/V*.sql` 存在不代表应用启动会自动迁移。
- 常规受控增量放在 `backend-springboot/sql/zz_*.sql`，按文件名顺序执行。
- 只追加新脚本，不修改已经执行过的历史脚本来兼容新需求。
- 脚本必须可重复执行；结构和数据修复都要限定作用范围。
- 涉及历史数据修复时，说明选择条件、影响行和恢复依据。
- `manual_*.sql`、备份脚本和回滚脚本不是常规自动迁移入口。
- 不要把整个 SQL 目录当作一键初始化包。

## 9. 本地验证与 PR 门禁

后端：

```powershell
cd backend-springboot
mvn.cmd -q -DskipTests compile
mvn.cmd test
```

前端：

```powershell
cd frontend-vue
npm.cmd ci
npm.cmd run typecheck
npm.cmd run check:access
npm.cmd run build
```

仓库根目录：

```powershell
git diff --check
```

进入测试环境前还必须完成：

- 改动相关的单元或集成测试。
- 密钥与敏感信息扫描。
- 数据库增量脚本审查。
- OpenAPI 变化审查。
- PR 构建检查。
- 受影响角色、无权限角色和对象范围的人工验收。

`npm run build` 不包含 `check:access`。发布后冒烟检查不能替代发布前门禁。

## 10. 测试与生产

测试环境：

- 只用于团队联调、接口调试和业务验收。
- 使用独立数据、配置、容器和通知目标。
- 发布来源与步骤以仓库当前 CI/部署配置和维护者说明为准，不在本文固化主机或凭据。
- 发布后检查首页、登录、`/api/auth/me`、`/v3/api-docs`、变更页面/API及相邻权限角色。

生产环境：

- 只有用户明确提出生产发布并通过独立审批时才可操作。
- 测试通过、PR 合并或文档更新都不等于获得生产授权。
- 不得从开发任务访问或修改生产主机、数据库、凭据、通知目标或发布工作流。
- 生产发布必须使用已经验证的精确提交和不可变构建产物。

## 11. 文档与安全

行为、权限、主路由、接口、环境变量、数据库或部署拓扑变化时，同步更新相关文档和机器上下文。

禁止写入或输出：

- 密码、令牌、密钥、会话值或真实 `.env` 内容。
- 生产数据、个人信息、群组标识或真实网络地址。
- 可直接登录的共享测试账号。
- 把规划接口、示例模块或静态 CSV 描述为已上线事实。

完整接入步骤见仓库文件 `docs/MODULE_DEVELOPMENT_GUIDE.md`。
