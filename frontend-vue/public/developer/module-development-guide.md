# 模块开发与接入指南

> 稳定地址：`/developer/module-development-guide.md`
>
> 适用范围：企业采购/维保平台的本地开发和测试环境。本文是仓库 `docs/MODULE_DEVELOPMENT_GUIDE.md` 的下载精简版；发生冲突时，以目标提交的代码、实时 OpenAPI 和仓库完整指南为准。

## 1. 推荐读取顺序

1. `/llms.txt`
2. `/developer/ai-context.md` 或 `/developer/ai-context.json`
3. 本指南
4. `/developer/module-manifest.schema.json`
5. `/developer/module-manifest.example.json`
6. `/developer/pr-checklist.md`
7. 当前提交源码和 `/v3/api-docs`

不要从页面外观推断后端权限、数据库状态或流程行为。不要把示例模块当作已经上线的真实能力。

## 2. 开发前先确认

- 这是现有模块扩展，还是确实需要新的稳定 `appCode`。
- 能否复用现有页面、接口、权限、工作流服务、通知能力和审计记录。
- 谁能打开入口、能查看哪些对象、能执行哪些动作。
- 是否需要新增表、字段、索引或种子权限。
- 哪些内容仅测试环境可见，哪些内容可能进入生产候选。

当前稳定编码包括 `PROCUREMENT`、`MAINTENANCE`、`PLATFORM`，以及条件启用的测试环境 `EHS`。`EHS` 存在于当前代码并不代表已经进入生产；以目标部署的环境开关和应用准入为准。

涉及采购、审批、报价、飞书、AI、通知或月度需求时，必须先找到现有入口和服务。不能建立平行流程、影子状态、直接 SQL 状态跳转或绕过审计的回调。

## 3. 模块最小交付物

一个完整模块通常包含：

```text
frontend-vue/src/api/<module>.ts
frontend-vue/src/types/<module>.ts
frontend-vue/src/pages/<module>/
frontend-vue/src/router/index.ts
frontend-vue/src/config/menu.ts

backend-springboot/src/main/java/.../controller/
backend-springboot/src/main/java/.../service/
backend-springboot/src/main/java/.../mapper/
backend-springboot/src/main/java/.../entity/

backend-springboot/sql/zz_<module>.sql
docs/
```

并同步声明：

- 稳定 `appCode`
- 菜单和路由前缀
- API 前缀
- 权限码
- 对象或数据范围
- 数据库迁移
- 测试证据
- 回滚方式

先复制 `/developer/module-manifest.example.json`，再按 `/developer/module-manifest.schema.json` 校验字段。

## 4. 前端接入

### 4.1 API、类型和页面

- 请求统一放入 `frontend-vue/src/api/`，复用现有 HTTP 客户端和错误处理。
- 业务类型放入 `frontend-vue/src/types/`，不要在多个页面重复定义同一结构。
- 页面复用现有布局、组件和视觉规范，不新建第二套路由壳或权限系统。

### 4.2 菜单与路由

- 菜单配置和 Vue Router 必须同时更新。
- 路由的 `meta.appCode`、角色和权限要求应与菜单一致。
- 当前菜单与路由守卫把 `roles` 和 `permissions` 按“任一满足即可”处理；`roles` 为空会直接通过角色门槛，不能再用 `permissions` 收窄入口。权限优先入口可保留 `SUPER_ADMIN` 角色兜底并配置权限码。
- 前端隐藏只控制入口，不是安全边界。
- 仅测试环境开放的入口必须同时受运行时环境开关和测试 Nginx 控制。

### 4.3 工作台

需要进入统一工作台的模块应接入现有聚合机制，并由后端按当前用户的应用准入和数据范围过滤。不能通过前端写死记录或扩大可见范围。

## 5. 后端接入

### 5.1 分层

- Controller：参数接收、校验和响应。
- Service：业务规则、事务和状态变更。
- Mapper：数据访问。
- Entity/DTO/VO：持久化与接口边界。

不要在 Controller 中直接拼接核心流程，不要让前端提交的角色、归属或状态替代后端判断。

### 5.2 四层访问控制

新增接口至少检查：

1. 登录身份。
2. 应用准入 `appCode`。
3. 操作权限码。
4. 对象或数据范围。

菜单可见、按钮可见或角色名称均不能替代后端校验。查询、导出、下载和批量接口也必须使用相同的数据范围规则。

### 5.3 流程复用

流程型功能必须复用现有领域服务、状态、审批任务和审计记录。若无法复用且需要新增流程分支，应先取得明确设计确认，再开始实现。

## 6. 数据库迁移

- `schema.sql` 只用于独立、可丢弃的全新本地数据库。
- 已存在环境只使用经过评审、可重复执行的 `backend-springboot/sql/zz_*.sql`。
- 不修改已经执行过的历史脚本来伪装新迁移。
- 变更表结构时同步考虑索引、旧数据兼容、权限种子和回滚边界。
- 脚本不得写入真实凭据、个人信息或生产业务数据。

## 7. OpenAPI 与接口调试

测试环境提供：

- `/v3/api-docs`：实时 OpenAPI JSON。
- `/swagger-ui/index.html`：人工接口调试。

以当前部署返回的 OpenAPI 为接口契约。静态 API 清单只能作为导航，不能证明接口仍存在。Swagger 不会自动读取平台 `localStorage` 中的登录凭据；点击 **Authorize** 后只粘贴当前测试会话的短期 JWT，再优先执行 `GET /api/auth/me`。调试会修改数据的接口前先确认测试数据范围，不在生产环境开启 Swagger 作为验收手段。

接口统一返回 `{ success, code, message, data }`。当前部分鉴权、校验和业务异常仍使用 HTTP 200 包装失败结果，验收必须以 `success` 与 `code` 为准；只有 `success=true` 且 `code=0` 表示调用成功。

## 8. 最小验证

后端：

```powershell
cd backend-springboot
mvn.cmd -q -DskipTests compile
```

前端：

```powershell
cd frontend-vue
npm.cmd run check:access
npm.cmd run check:developer-center
npm.cmd run build
```

仓库：

```powershell
git diff --check
```

再按改动范围执行相关单元、集成或页面测试。至少覆盖：

- 有准入且有权限。
- 无应用准入。
- 有准入但没有操作权限或对象范围。
- 正常提交。
- 关键无效输入。
- 直接访问 API 无法绕过前端限制。

## 9. PR 与发布

PR 应说明：

- 目标和范围。
- 复用的既有模块、服务或流程节点。
- 菜单、路由、API、权限和数据范围变化。
- 数据库迁移及重复执行结果。
- 实际运行的检查和结果。
- 配置与回滚方式。

测试环境用于验证已完成发布前检查的候选版本。测试通过不等于获得生产发布授权；生产仍使用独立的上线流程和已验收构建产物。

## 10. 生产红线

- 开发者中心、`/llms.txt`、`/developer/*` 和在线接口调试仅供本地或测试环境使用。
- 普通开发任务不得访问或修改生产主机、数据库、凭据、通知目标或发布工作流。
- 不把测试数据、测试配置或临时调试开关复制到生产。
- 不在文档、源码、日志或提交记录中写入真实凭据、个人资料或受限网络信息。

## 11. 完成定义

模块只有同时满足以下条件才算完成测试接入：

- 页面、菜单、路由和 API 接入完整。
- 应用准入、操作权限和数据范围由后端执行。
- 流程复用既有服务、状态和审计。
- 数据库脚本幂等且范围明确。
- OpenAPI 与模块清单反映当前实现。
- 自动门禁和权限场景验证通过。
- 测试环境冒烟通过。
- 文档更新且不含敏感信息。

提交前使用 `/developer/pr-checklist.md` 逐项核对。
