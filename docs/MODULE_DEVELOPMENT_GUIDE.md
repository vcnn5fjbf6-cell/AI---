# 模块开发与接入指南

最后核对：2026-07-27

本指南说明如何在现有企业采购/维保平台中增加模块，并把它安全地接入前端、后端、权限、数据库、测试和发布链路。平台采用“代码模块 + PR + 编译部署”，不是运行时热插拔插件平台。

本文不包含任何账号、密码、令牌、生产数据或真实网络标识。操作前先阅读仓库根目录 `AGENTS.md`，并以目标分支当前代码为准。

## 1. 先判断接入类型

| 类型 | 例子 | 默认做法 | 是否需要额外评审 |
| --- | --- | --- | --- |
| 扩展现有页面或 API | 采购列表增加筛选条件 | 复用原模块、权限和 Service | 一般不需要新应用设计 |
| 在现有应用中增加子模块 | 采购管理增加一组后台页面 | 复用现有 `appCode`，新增细粒度权限 | 需要确认菜单与数据范围 |
| 新增独立业务应用 | 新业务线进入统一平台 | 新建稳定 `appCode`、菜单、路由、后端准入和工作台来源 | 必须先评审 |
| 新增流程分支或状态机 | 改变采购或审批流转 | 优先复用现有节点；无法复用时暂停实现 | 必须取得业务确认 |

当前应用准入编码：

- `PROCUREMENT`：采购、审批、报价和订单。
- `MAINTENANCE`：维保业务。
- `PLATFORM`：平台、用户、权限和管理能力。
- `EHS`：当前代码中存在的测试环境条件模块；是否启用以目标部署的环境开关和应用准入为准，不代表已经进入生产。

`PLATFORM` 不是其他业务应用的通配权限。不要为了少配一个应用编码，把新业务线挂到 `PLATFORM`。

## 2. 开发前准备

1. 查看工作树状态和目标文件差异：

   ```powershell
   git status --short --branch
   git diff -- <目标文件>
   ```

2. 阅读：

   - `AGENTS.md`
   - `docs/DEVELOPMENT_GUIDE.md`
   - `docs/system-architecture-by-module.md`
   - 目标模块当前 Controller、Service、页面、路由和 SQL
   - 本地或测试环境 `/v3/api-docs`

3. 确定改动所有权。同一文件已有他人未完成改动时，先协调合并顺序，不覆盖、不恢复他人的工作。
4. 从 `/developer/module-manifest.example.json` 复制一份模块清单，按 `/developer/module-manifest.schema.json` 填写。
5. 把成功标准写成可验证结果，例如：

   - 有准入且有权限的账号可以访问。
   - 无应用准入的账号不能通过直接 URL 或 API 绕过。
   - 有应用准入但超出对象范围的账号看不到无权记录。
   - 数据库脚本重复执行不会失败或重复污染数据。

## 3. 一个模块的最小交付物

每个模块至少包含：

- 模块说明和 `module-manifest`。
- 后端 Controller、Service、请求/响应模型和测试。
- 前端 API 封装、类型、页面、路由和菜单。
- 应用准入、操作权限和对象/数据范围校验。
- 必要的幂等 `backend-springboot/sql/zz_*.sql`。
- OpenAPI 说明、人工验收用例和文档更新。

推荐目录：

```text
backend-springboot/src/main/java/com/company/procurement/modules/<domain>/
├─ controller/
├─ service/
├─ dto/
├─ vo/
├─ entity/
└─ mapper/

frontend-vue/src/
├─ api/<module>.ts
├─ pages/<module>/
├─ types/
├─ router/index.ts
└─ config/menu.ts
```

只创建当前需求使用的目录和代码，不为未来假设提前增加抽象层。

## 4. 前端接入

### 4.1 API 与类型

- API 请求统一放在 `frontend-vue/src/api/`，不要在页面中散落请求地址。
- 请求与响应字段使用 TypeScript 类型。
- 前端只负责交互提示，不替代后端参数、权限和数据范围校验。

### 4.2 页面与路由

正式页面以 `frontend-vue/src/router/index.ts` 为准。只创建 `.vue` 文件不代表功能已经上线。

业务路由至少声明：

- 页面组件。
- 目标 `meta.appCode`，或需要任一应用时声明 `anyAppCodes`。
- 确有入口限制时再声明角色或权限。当前菜单与路由守卫把 `roles` 和 `permissions` 按“任一满足即可”处理；`roles` 为空会直接通过角色门槛，不能再依靠 `permissions` 收窄入口。权限优先入口可保留 `SUPER_ADMIN` 角色兜底并配置权限码。
- 页面标题等现有路由元数据。

直接 URL、飞书内嵌页等非菜单入口也必须有应用准入，不能因为侧边栏没有入口而省略。

### 4.3 菜单与按钮

- 在 `frontend-vue/src/config/menu.ts` 添加菜单时，应用编码必须与路由一致。
- 按钮可按权限码控制显示和禁用，但这只是交互层。
- 后端仍须校验操作权限和具体业务对象。
- 历史兼容路由只做跳转，不应继续承载新菜单和新能力。

### 4.4 工作台

新增工作台来源时：

- 先设计稳定的来源类型。
- 在 `WorkbenchServiceImpl` 或现有来源映射中接入。
- 列表、详情、审批、关注和取消关注使用一致的应用与对象范围。
- 未获目标应用准入的账号不能通过工作台看到该来源。

完成后运行：

```powershell
cd frontend-vue
npm.cmd run typecheck
npm.cmd run check:access
npm.cmd run build
```

`npm run build` 不包含 `check:access`，两者都要执行。

## 5. 后端接入

### 5.1 模块边界

- 新代码进入匹配业务域的 `modules/<domain>/`。
- Controller 负责 HTTP 协议、参数接收和校验。
- 业务规则、事务和对象权限放在 Service。
- 数据访问通过现有 Mapper/Repository 模式实现。
- 不在 Controller 或定时任务中直接修改正式流程状态。

### 5.2 接口准入

每个业务 API 都要明确：

- HTTP 方法和路径。
- 所需应用编码。
- 所需操作权限码。
- 对象/数据范围。
- 请求、响应和错误码。

新 API 前缀要接入 `AuthInterceptor#requiredAppCode` 或当前等价的后端准入机制。路由的 `appCode` 与后端要求必须一致。

登录、权限申请或受控集成入口是否属于公共接口，应复用现有白名单规则，不得自行把业务 API 加入匿名白名单。

### 5.3 对象与数据范围

后端不得信任前端提交的用户、部门、供应商、区域或权限范围标识。应从当前登录身份和目标业务对象反查。

至少覆盖：

- 列表查询范围。
- 单条详情。
- 新增与修改。
- 审批或状态动作。
- 删除、导出和附件下载。

角色、应用准入、操作权限和对象范围是四个不同维度，不能互相替代。

### 5.4 流程复用

采购、审批、月度需求、报价、订单、售后、通知、飞书和 AI 必须复用已有服务、节点、状态和审计记录：

- 采购申请优先复用 `PurchaseRequestService`。
- 审批动作优先复用 `WorkflowService` 和现有审批任务。
- 飞书卡片和 AI 只承担输入、交互、提醒或建议。
- 定时任务复用内部事务实现，但不要依赖交互式登录上下文。

禁止：

- 直接 SQL 改正式流程状态。
- 建立影子状态、旁路表或第二套审批链。
- 让 AI、飞书回调或外部系统直接写正式业务状态。
- 为绕过权限而调用后台管理接口。

确需新增流程分支、状态机或并行写入路径时，停止实现并取得业务负责人明确确认。

## 6. 数据库迁移

### 6.1 本地初始化

`backend-springboot/sql/schema.sql` 开头会删除已有业务表，只能用于独立、可丢弃的全新本地数据库。禁止对生产库、共享测试库或需要保留数据的库执行。

仓库当前没有经过验证的一键式全新本地数据库 bootstrap。优先使用维护者提供的开发库快照，或由维护者确认所需脚本序列。

### 6.2 增量脚本

常规增量统一放在：

```text
backend-springboot/sql/zz_*.sql
```

规则：

- 只追加新脚本，不修改已经执行过的历史脚本。
- 按文件名顺序执行。
- 必须可重复执行。
- 结构变更使用当前 MySQL 支持的安全、幂等写法。
- 数据修复必须限定选择条件，并记录预计与实际影响。
- 需要恢复时，说明备份表或受影响记录的确定依据。
- 任一增量失败都应阻止继续发布。

当前未启用 Flyway。`src/main/resources/db/migration/V*.sql` 存在不代表 Spring Boot 启动会自动执行。

`manual_*.sql`、备份脚本和回滚脚本不是常规自动迁移入口。不要把整个 `backend-springboot/sql` 目录当作可无条件执行的初始化包。

## 7. OpenAPI 与接口调试

本地后端启动后：

- OpenAPI JSON：`http://localhost:8080/v3/api-docs`
- Swagger UI：`http://localhost:8080/swagger-ui/index.html`

测试环境使用同源地址：

- OpenAPI JSON：`/v3/api-docs`
- Swagger UI：`/swagger-ui/index.html`

完整业务联调使用维护者安全提供的测试账号和当前测试会话 JWT。Swagger 不会自动读取平台 `localStorage` 中的登录凭据；点击 **Authorize** 后只粘贴短期测试 JWT，再优先执行 `GET /api/auth/me`。不要把共享明文密码、Token 或真实 `.env` 写入 SQL、文档、日志或提交。

接口统一返回 `{ success, code, message, data }`。当前部分鉴权、校验和业务异常仍使用 HTTP 200 包装失败结果，因此验收必须以 `success` 与 `code` 为准；只有 `success=true` 且 `code=0` 表示调用成功。

生产 profile 默认关闭 OpenAPI。不得为了调试把 Swagger 暴露到不受信任网络。

仓库的 `docs/backend-api-routes.csv` 只适合离线查找，是静态快照。代码变更后应检查当前 `/v3/api-docs`，不要依赖 CSV 中可能漂移的源码行号。

## 8. 测试要求

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

还必须完成：

- 改动相关的单元或集成测试。
- 数据库增量脚本审查。
- OpenAPI 变化审查。
- 密钥与敏感信息扫描。
- PR 构建检查。
- 受影响页面的人工联调。

权限验收至少使用三类场景：

1. 有应用准入且有操作权限。
2. 无应用准入或无操作权限。
3. 有入口权限但超出具体对象/数据范围。

前端目前没有完整的自动化测试覆盖；构建成功不能替代受影响页面和角色的人工联调。

## 9. PR 与测试发布

PR 描述至少写明：

- 业务目的与可见变化。
- 修改的页面、接口、权限和数据表。
- 复用的现有 Service、流程节点和状态。
- 新增增量 SQL 及重复执行结果。
- 自动测试与人工验收结果。
- OpenAPI 与文档变化。
- 已知限制和回退依据。

进入测试环境前，编译、测试、前端构建、`check:access`、`git diff --check`、秘密扫描、专项测试和 PR 构建检查必须全部通过。任一门禁失败或缺失时，不发布。

测试发布后至少检查：

- 首页和登录。
- `/api/auth/me`。
- `/v3/api-docs`。
- 变更页面和 API 主路径。
- 有权限、无权限及对象越界场景。
- 增量 SQL 重复部署行为。
- 外部通知只发送到测试目标。

测试环境发布来源与操作步骤以仓库当前 CI/部署配置和维护者说明为准，不在文档中固化主机、账号或凭据。

## 10. 生产红线

- 测试通过、PR 合并、文档完成都不等于获得生产发布授权。
- 只有用户明确提出生产发布，并完成独立审批后，才能进入生产发布流程。
- 普通开发任务不得访问或修改生产主机、数据库、凭据、通知目标和生产工作流。
- 生产使用已经验证的精确提交和不可变构建产物，不在生产环境临时修改候选代码。
- 发布后冒烟检查是第二阶段，不能替代发布前门禁。

## 11. 机器可读资料

测试环境提供：

- `/llms.txt`
- `/developer/ai-context.md`
- `/developer/ai-context.json`
- `/developer/module-manifest.schema.json`
- `/developer/module-manifest.example.json`
- `/developer/pr-checklist.md`
- `/v3/api-docs`

其他 AI 应先读取 `llms.txt` 和 `ai-context`，再结合当前提交源码和 OpenAPI 工作。机器资料不得包含密码、令牌、生产数据、个人信息、群组标识或真实网络地址。

## 12. 完成定义

一个模块只有同时满足以下条件，才可视为完成测试接入：

- 页面、路由、菜单和 API 接入完整。
- 应用准入、操作权限和对象范围均由后端执行。
- 相关流程复用既有服务、状态和审计。
- 数据库脚本幂等且范围明确。
- OpenAPI 与模块清单反映当前实现。
- 自动门禁和三类权限场景验收通过。
- 测试环境冒烟通过。
- 文档已更新，且没有敏感信息。

PR 可直接使用 `/developer/pr-checklist.md` 逐项核对。
