# 开发者指南

最后核对：2026-07-27

> 本文档以当前仓库代码为准。第一次参与项目时先阅读本页的快速上手和 `AGENTS.md`；后续章节保留详细业务规则与历史迁移背景，不能替代代码、当前 SQL 文件和部署配置本身。

## 先从这里开始

### 开发模式

| 模式 | 启动内容 | 适用场景 | 主要限制 |
| --- | --- | --- | --- |
| 本地全栈开发 | MySQL 8、Spring Boot、Vue/Vite | 接口联调、流程开发、回归验证 | 需要独立开发库和受控开发账号 |
| 前端认证 mock | Vue/Vite | 登录态、布局和少量页面调试 | 只 mock 认证等少数接口，不是完整业务 mock |
| PR 测试部署 | 独立测试 Docker Compose | 团队联调、接口文档和验收 | PR 自动部署到 151，不连接生产数据或正式通知 |
| 生产 Docker | MySQL、Redis、Qdrant、后端、前端 | 生产部署和发布验证 | 使用 `deploy/`，不作为本地热开发方案 |

生产 Compose 只对外发布前端 `80` 端口，MySQL、Redis、Qdrant 和后端均在 Compose 内网；宿主机前后端联调默认采用本地 MySQL + `8080` 后端 + `5173` 前端。

### 最短上手路径

1. 安装 JDK 17、Maven 3.9+、MySQL 8，以及满足 Vite 8 要求的 Node.js `^20.19.0 || >=22.12.0`。
2. 创建独立、可丢弃的本地数据库，按[数据库说明](#6-数据库说明)初始化。`schema.sql` 会删除已有表，绝不能对生产库、共享库或已有数据的库执行。
3. 启动后端：

   ```powershell
   cd backend-springboot
   mvn.cmd spring-boot:run
   ```

4. 另开终端启动前端：

   ```powershell
   cd frontend-vue
   npm.cmd ci
   npm.cmd run dev
   ```

5. 打开 `http://localhost:5173`；后端存活入口为 `http://localhost:8080/health`，开发环境 Swagger 为 `http://localhost:8080/swagger-ui/index.html`。

种子脚本创建的账号使用不可登录的禁用密码值。完整联调应由维护者通过安全渠道提供或创建本地测试账号；不要在 SQL、文档或提交记录中加入共享明文密码。只做前端界面调试时，可使用[前端认证 mock](#75-登录账号与前端认证-mock)。

### 文档地图

- [协作与改动纪律](../AGENTS.md)
- [模块开发与接入指南](MODULE_DEVELOPMENT_GUIDE.md)
- [按模块划分的系统架构](system-architecture-by-module.md)
- [后端 API 路由清单](backend-api-routes.csv)
- [飞书卡片与供应商通知](feishu-approval-card-notification.md)
- [生产部署手册](DEPLOYMENT_MANUAL.md)
- [系统使用手册](USER_MANUAL.md)

### 测试环境开发者中心与 AI 稳定入口

本地开发和测试环境为内部开发者提供 `/developer-center`。页面按“读取上下文、声明模块、实现与测试、提交 PR”组织资料，并提供搜索、OpenAPI、Swagger 和原始文件下载入口。开发者中心不是生产功能：前端菜单与路由只在开发或测试环境注册，生产 Nginx 对开发者页面、机器资料、OpenAPI/Swagger 路径及开发者中心专用前端资源明确返回 404。

需要把项目交给其他 AI 时，优先提供相对于当前测试站点的稳定路径，不要复制带临时会话参数的页面地址：

- `/llms.txt`：AI 根索引和推荐读取顺序。
- `/developer/ai-context.md`：可直接放入模型上下文的平台事实与边界。
- `/developer/ai-context.json`：供自动化工具读取的结构化上下文。
- `/developer/module-development-guide.md`：面向下载的模块开发与接入指南。
- `/developer/module-manifest.schema.json`：模块声明 JSON Schema。
- `/developer/module-manifest.example.json`：不代表已上线模块的字段示例。
- `/developer/pr-checklist.md`：进入测试发布前的检查清单。
- `/v3/api-docs` 与 `/swagger-ui/index.html`：当前测试部署的实时接口契约和人工调试页。

原始资料不得包含账号资料、凭据、生产数据或真实网络标识。每次调整开发者中心、机器资料、环境门禁或链接时，执行：

```powershell
cd frontend-vue
npm.cmd run check:developer-center
```

### 开发红线

- 采购、审批、报价、月度需求、通知、飞书或 AI 入口必须复用现有工作流服务、状态、权限和审计记录，不能直接改状态或另建旁路流程。
- 新增业务页面时同步维护路由、菜单、应用准入和后端鉴权，并执行 `npm run check:access`；前端隐藏菜单或按钮不是安全边界。
- 数据库结构只通过经过评审的增量 SQL 变更；不修改已在线上执行的历史脚本来兼容新需求。
- `.env`、数据库密码、JWT、飞书/Dify 凭据和部署凭据只放本机环境或 CI Secret，不能提交到仓库或复制进文档。

## 1. 文档目标

本文档面向后续维护和二次开发，重点说明：

- 项目当前真实结构，而不是早期规划结构
- 前后端启动、构建、部署链路
- 飞书登录、平台权限、采购流程的关键落点
- 数据库初始化与增量脚本规则
- 后续代码变更时，哪些内容必须同步更新文档

## 2. 项目概览

仓库名仍然是 `enterprise-procurement-platform`，但当前代码实际承载的是一个多业务统一入口平台，包含以下能力：

- 维保管理：`MAINTENANCE`
- 采购管理：`PROCUREMENT`
- 平台管理：`PLATFORM`
- 灾害预警：独立一级菜单，当前仅限管理员配置和触发，复用平台登录、设置表、飞书应用凭证和定时任务能力，不进入采购审批流程。灾害预警配置只由 `DisasterWeatherServiceImpl` 的 WEATHER 默认值和“灾害预警 / 预警设置”维护，不能再把旧 WEATHER 默认项挂回通用采购设置服务，避免旧说明、旧区域或旧频率覆盖新配置。普通天气 API 和实时预警 API 必须分离：普通天气只用于和风 `/v7/weather/now`、`/v7/weather/7d`；实时预警默认使用中央气象台当前预警列表 `/rest/findAlarm`，按当前配置区域筛选北京市朝阳区和北京市级预警，并保留和风官方 `/weatheralert/v1/current/{latitude}/{longitude}`、旧 `/v7/warning/now` 作为后台可切回模式。后台配置的中心经纬度仍按“经度,纬度”保存，调用和风新版预警 API 时由后端转换成“纬度/经度”。定时任务只推送正式值班群；设置页"测试预警推送"、颜色测试按钮和预警记录页所有手动发送任务必须走“测试群 chat_id”，测试群未配置时必须失败，不能回退到正式值班群。颜色测试按钮通过 `DisasterWeatherTriggerDTO.testCardColor` 生成模拟测试记录，不依赖真实天气预警。预警记录页和设置页按戒备等级（一级红/二级橙/三级黄/常规绿）动态着色，预警/戒备列使用带色标签；设置页"测试预警推送"按钮点击后弹出带戒备等级颜色边框的预览卡片，展示预警类型、预警颜色、戒备等级和颜色图例，方便管理员验证颜色配置。真实飞书卡片不能完全复用浏览器 CSS，但 `DisasterWeatherServiceImpl.buildFeishuCard` 必须与设置页测试预览保持相同字段顺序、戒备颜色和版本号，常规观察/无预警统一使用绿色；`WARNING` 类型卡片必须以预警状态、重保等级、预警处置和预警接口状态为主，天气实况/湿球温度只能作为辅助信息，不展示未来天气列表。飞书卡片发送前会按当前记录生成 PNG 预览图并上传为卡片图片元素，图片上传失败时必须降级继续发送文字卡片，不能阻断正式预警通知；卡片底部保留“重保检查表填写完成审批”打开表单按钮，值班人员收到预警后可直接进入检查表补填和审批。预警记录每小时自动清理：无预警记录只保留 1 天；连续重复的有效预警按城市、预警类型/等级和戒备等级分段，只保留开始和结束/最新两条记录。
- 灾害预警连续有效预警去重必须按“连续预警段”判断，不能回到固定时间窗口内统计 `SENT` 记录的旧逻辑。原因是每小时清理会压缩连续预警段并删除中间记录，旧逻辑会丢失最近一次 `SENT` 去重依据，导致清理后的下一次分钟轮询重复推送正式值班群。当前标准为：同城市、同预警类型/等级、同戒备等级的上一条有效预警若已 `SENT` 或 `SKIPPED`，当前记录只标记跳过；只有无预警后重新出现、预警段变化或上一条失败时才允许重新推送。
- 灾害预警解除推送由 `disaster_weather_warning_resolve_push_enabled` 控制。轮询必须先识别接口返回的官方解除通知，例如“解除雷电黄色预警”，并把这类记录标记为绿色“预警解除”，不能再让它参与有效预警规则匹配；只有前序存在已发送或已跳过的同城市、同类型、同颜色重保预警时，才允许向正式值班群推送一次解除卡片。不能因为本轮接口未命中三级及以上重保规则就兜底推送“预警解除”，避免接口未返回区县预警、规则未匹配或仍存在低等级/其他类型预警时误通知值班群已解除；已成功发送过解除卡片后，后续无预警或重复解除通知不能重复推送。
- 重保天气预警规则必须覆盖中央气象台标题解析、和风旧接口 `warning[]` 和新版 `alerts[]` 的字段组合，并按《21V-DCO-MO-224 重保戒备管理制度 V3.3》执行默认映射：一类极端天气（暴雨、暴雪、台风、洪水）红/橙/黄蓝分别为一级/二级/三级戒备；二类极端天气（雷电、冰雹、寒潮、霜冻、高温）红/橙分别为二级/三级戒备，二类黄蓝默认配置为“无匹配标签”，不触发三级及以上重保推送。新交付楼栋只有开启 `disaster_weather_new_delivery_promotion_enabled` 后才执行制度提级，并可在未来预报出现中雨/雪及以上时触发三级戒备；雨季专项由 `disaster_weather_rainy_season_enabled` 和 `disaster_weather_rainy_season_months` 控制。新增或调整规则时要同步后端默认值、设置页默认规则、生产增量 SQL 和文档，避免接口已返回预警但因字段或规则缺失被标记为“当前无三级及以上重保天气预警”。

灾害预警湿球温度按后端统一计算，避免前端和自动化脚本各算一套：

- 饱和水汽压：`es(T)=6.112*exp(17.67*T/(T+243.5))`
- 实际水汽压：`e=RH/100*es(T)`
- 湿球温度：用二分法求解 `e = es(Tw) - 0.00066*(1+0.00115*Tw)*P*(T-Tw)`，默认气压 `P=1013.25 hPa`
- 露点温度：Magnus 公式，`a=17.625`，`b=243.04`

登录成功后，前端会根据用户 `appCodes` 决定默认首页：

- 有任一业务应用准入：进入统一工作台 `/workbench`，由工作台后端按 `appCodes` 聚合当前账号可看的来源
- 没有任何应用准入：进入 `/platform/access`，只能查看本人权限并发起申请

对应实现见：

- `frontend-vue/src/utils/access-home.ts`
- `frontend-vue/src/router/index.ts`

### 2.1 Excel 导入模板规范

凡是给业务人员下载后再上传的标准模板，必须在后端生成源头加约束，不允许只给普通空表：

- 必填列表头用红字标识，并在表头批注里说明填写规则。
- 枚举字段必须加 Excel 下拉，例如人员用户类型、启用状态、采购类型、审批节点触发方式、是否替换商品等。
- 需要保留原值或用于系统识别的列，必须在表头批注里写明“不要修改”。
- 新增模板时优先复用 `ExcelExportUtils.WorkbookOptions`，不要复制一套 Excel 生成逻辑。
- 人员批量导入模板必须包含 `用户名`、`供应商ID`、`区域ID`、`初始密码` 等导入端支持字段；新增用户初始密码统一为 `123456`，可不填初始密码，首次登录必须强制修改。
- 人员导入的 `用户类型` 允许使用中文下拉值，后端会规范化为系统编码。
- 人员导入必须考虑逻辑删除账号占用唯一键：普通页面查询仍过滤 `is_deleted=1`，但导入匹配和唯一性校验要能查到已删除用户；导入匹配到已删除账号时恢复为 `is_deleted=0` 并更新资料，恢复账号按新增账号规则重置为 `123456` 且强制改密。不要把数据库 `Duplicate entry` 异常直接返回给前端。

## 3. 当前技术栈

### 3.1 前端

- Vue 3
- Vite 8
- TypeScript
- Vue Router 4
- Pinia
- Element Plus

说明：

- 当前前端实现为 Vue 3 + Element Plus
- 采购页中有一部分页面已经按 VNET 风格重构到 `frontend-vue/src/pages/procurementVnet`
- 维保页当前也在同一套 Vue 3 工程中维护

### 3.2 后端

- Spring Boot 3.4.2
- MyBatis Plus 3.5.9
- MySQL 8
- JJWT 0.12.6
- Spring Validation

### 3.3 部署

- Docker Compose
- Nginx 容器承载前端静态资源
- Spring Boot 容器承载后端 API
- MySQL 容器承载业务数据
- Redis 容器提供缓存基础设施
- Qdrant 容器提供向量检索基础设施
- 远程发布脚本：`deploy/deploy_current.py`

## 4. 目录结构

```text
enterprise-procurement-platform/
├─ .github/workflows/          自动测试与生产发布工作流
├─ backend-springboot/          后端 Spring Boot 工程
├─ frontend-vue/                前端 Vue 3 工程
├─ deploy/                      Docker Compose 与发布脚本
├─ docs/                        文档
├─ AGENTS.md                    协作与改动纪律
└─ README.md                    仓库入口说明
```

### 4.1 前端核心目录

```text
frontend-vue/src/
├─ api/                         接口封装
├─ components/                  通用组件
├─ config/                      菜单和静态配置
├─ directives/                  权限等指令
├─ layout/                      应用壳层
├─ pages/                       页面
├─ router/                      路由守卫与路由表
├─ stores/                      Pinia 状态
├─ types/                       TS 类型
└─ utils/                       工具函数
```

`pages/` 当前主要分组：

- `workbench`：统一工作台
- `procurementVnet`、`procurement`：采购主业务页和历史页面
- `approval`、`order`、`supplier`、`reconciliation`：审批、订单、供应商与对账
- `maintenance`、`disaster`：维保与灾害预警
- `platform`、`system`、`admin`：平台准入、系统管理与后台页面
- `login`、`feishu`：登录与飞书采购入口

页面是否正式启用以 `frontend-vue/src/router/index.ts` 为准；目录中存在未挂路由的历史或备用页面，不能仅凭文件存在判断线上功能。

### 4.2 后端核心目录

```text
backend-springboot/src/main/java/com/company/procurement/
├─ common/                      通用安全、异常、返回体等
├─ modules/
│  ├─ approval/                 审批
│  ├─ auth/                     登录与飞书认证
│  ├─ integration/              飞书等外部集成
│  ├─ org/                      组织与数据范围
│  ├─ platform/                 平台权限
│  ├─ procurement/              采购主流程
│  ├─ supplier/                 供应商
│  ├─ system/                   用户、角色、权限
│  └─ workbench/                统一工作台
└─ ProcurementApplication.java  启动入口
```

## 5. 关键业务模块

## 5.1 登录与鉴权

后端入口：

- `POST /api/auth/login`
- `GET /api/auth/feishu/login-url`
- `POST /api/auth/feishu/callback`
- `GET /api/auth/me`
- `POST /api/auth/password/change`
- `POST /api/auth/logout`

对应代码：

- `backend-springboot/src/main/java/com/company/procurement/modules/auth/controller/AuthController.java`
- `backend-springboot/src/main/java/com/company/procurement/modules/auth/service/impl/AuthServiceImpl.java`

前端登录链路：

- 登录页：`frontend-vue/src/pages/login/LoginPage.vue`
- 状态管理：`frontend-vue/src/stores/auth.ts`
- Token 存储：`frontend-vue/src/utils/auth-storage.ts`

说明：

- 普通账号密码登录和飞书登录共用同一套 `/api/auth/me` 用户资料结构
- 飞书登录成功后，后端仍然生成系统 JWT，不直接把飞书 token 暴露给前端
- 个人改密由 `AuthServiceImpl#changePassword` 处理，要求校验旧密码、确认密码、当前密码不复用，并对最近 6 条 `sys_user_password_history` 做 BCrypt 匹配校验。
- 用户密码策略固定为 90 天到期。`AuthUserResolver` 会把 `mustChangePassword/passwordChangedAt/passwordExpired` 写入当前用户上下文；`AuthInterceptor` 对到期账号只放行 `/api/auth/me`、`/api/auth/password/change` 和个人权限页需要的基础接口。
- 后台新增账号、人员批量导入新增账号、飞书通讯录同步新增账号均使用统一初始密码 `123456`，并写入 `mustChangePassword=1`。编辑已有账号时，重置密码仍按管理员输入值处理；留空保持原密码。
- 改密成功会写入历史密码表、更新 `sys_user.password_changed_at`，并轮换 `session_secret` 让旧 token 失效。
- `POST /api/auth/logout` 必须携带有效 JWT；退出登录会轮换 `session_secret`，因此旧 token 会立即失效。
- 全局异常处理只把中性的 `SYSTEM_ERROR` 返回给前端，内部异常细节保留在后端错误日志中。

## 5.2 平台准入与首页路由

平台不是单一系统，而是多业务入口。后端登录返回的 `appCodes` 决定用户能进入哪些系统：

- `MAINTENANCE`
- `PROCUREMENT`
- `PLATFORM`

前端路由守卫处理：

- 未登录跳 `/login`
- 已登录访问 `/` 或 `/dashboard` 时按 `appCodes` 自动分流
- 路由上的 `meta.appCode` 用于模块级准入控制
- 路由上的 `meta.roles` 用于角色级控制

实现位置：

- `frontend-vue/src/router/index.ts`
- `frontend-vue/src/utils/access-home.ts`

### 5.2.1 导入文件记录

- 平台管理新增“导入文件记录”，对应后端表 `platform_import_file_log` 和接口 `/api/admin/import-file-logs/**`。
- 当前只记录导入元数据，不保存原文件内容；字段包括文件名、扩展名、大小、SHA-256、来源渠道、导入类型、上传人、飞书消息 ID、AI 消息日志 ID、处理状态、行数、成功/失败数量、结果摘要和错误信息。
- 已接入的来源包括：用户批量导入、部门审批配置导入、采购明细导入预览、供应商报价导入预览、飞书采购助手 Excel 附件。
- 保留周期由平台集成设置 `import_file_log_retention_days` 控制，默认 90 天；系统每天 03:30 物理删除早于该天数的导入记录，填 0 或负数表示不自动清理。
- 如果以后需要下载历史原文件，不能直接把文件内容塞进这张日志表；应先设计独立文件存储、访问权限和脱敏策略，再把 `storage_mode/storage_path` 接到文件存储。

## 5.3 采购模块

采购模块当前覆盖：

- 采购看板
- 采购申请列表
- 采购申请创建 / 编辑
- 审批操作
- 供应商管理
- 供应商报价

主要页面：

- `frontend-vue/src/pages/procurementVnet/ProcurementDashboard.vue`
- `frontend-vue/src/pages/procurementVnet/ProcurementRequests.vue`
- `frontend-vue/src/pages/procurementVnet/ProcurementRequestForm.vue`
- `frontend-vue/src/pages/procurementVnet/ProcurementApproval.vue`
- `frontend-vue/src/pages/procurementVnet/MyOrders.vue`
- `frontend-vue/src/pages/supplier/SupplierManagement.vue`
- `frontend-vue/src/pages/supplier/QuoteList.vue`

当前采购审批链按 V1-V8 节点运行：

- 审批候选人优先读取 `procurement_approval_level_user`，也就是“部门管理”里配置的 V1-V8 候选人。
- 没有新审批策略的历史部门仍可回退 `procurement_approval_route` 中的楼长、经理、区域经理、总监候选人姓名，但只要求候选人能匹配启用系统账号，不再要求账号绑定同名审批角色。
- `ProcurementWorkflow.roleCodeForNode` 中的 `FLOOR_LEADER`、`MANAGER`、`REGION_MANAGER`、`SENIOR_MANAGER` 等仅作为历史节点编码和待办记录字段保留，不再作为账号审批资格判断来源。
- V4 等后续节点未配置候选人时，系统只继续向 V5/V6/V7/V8 查找已配置候选人；上级节点也无人时直接提示维护 V1-V8 候选人，不再从角色管理中挑选兜底审批人。
- 最高管理员角色：`SUPER_ADMIN`。系统管理员可用于排障代处理已有待办，但不会被自动写入业务审批候选人。历史 `ADMIN`、`SYS_ADMIN` 只作为迁移兼容数据处理，不再作为全局最大权限判断。
- 采购申请、飞书采购入口和月度需求入口的部门选择必须通过 `ProcurementDepartmentAccessService`。普通用户只能选择本人账号匹配的采购部门；`SUPER_ADMIN`、`SYS_ADMIN`、`ADMIN` 可替用户选择申请人和采购部门，但必须写入 `purchase_request.acting_user_id / acting_user_name / acting_reason`，并在审批记录、飞书卡片、审批弹窗和采购操作订单 PDF 中展示“管理员代办”审计信息。

采购报价主流程当前基线：
- 工程师提交采购需求后，先进入楼长需求审核。
- 楼长通过后，采购需求进入供应商报价阶段。
- 非定向采购提交报价审核时，整张采购单仍要求至少 3 家供应商提交；供应商可以放弃个别明细，单项最低策略使用该明细剩余的有效报价继续比价。只有某个有效采购明细完全无人报价时才阻止提交，已单项驳回的采购明细不参与校验和中标计算。
- 普通采购进入需求池后，月度发布会全国汇总为一张供应商报价大单；汇总明细通过 `purchase_request_item.source_request_id/source_request_item_id` 保留来源需求，并按行保存 `receiving_city` 和 `delivery_address`。
- 采购类型是明细级字段：`purchase_request_item.req_type` 只能保存“工程项目”“实物资产/无形资产/长摊等”“服务/费用/低值易耗等”。`purchase_request.req_type` 只作为兼容和列表展示用汇总类型，单一类型时同步明细类型，多类型时写“混合采购”；新页面、新卡片、导入模板和 BI 明细都应优先读取明细 `req_type`，不要再把采购类型设计成申请头唯一字段。
- 采购申请页面分组必须使用业务口径，面向用户展示为“申请信息”“采购明细”等，不得把 `purchase_request` 主表、header、申请头等后端/数据库术语直接放到 UI 上。
- 供应商报价模板导入时，前端按行号、物料编码、商品名称、规格型号整表校验后才写入报价行；后端提交报价时再次校验 `purchaseRequestItemId` 必须属于当前采购申请。
- 替换商品链接支持按供应商配置内部商品编号规则。当前仅鑫方盛账号启用 `XFS_INTERNAL_SKU`：纯数字编号转换为 `https://vip.xfs.com/vip/productsku/{编号}.html`；其他供应商提交纯数字时必须继续拦截。前端手填、Excel 导入和后端提交必须使用同一规则，后端以当前账号绑定的 `supplierId` 查询供应商身份，不接受前端自行指定供应商。
- 报价明细通过 `original_condition/original_second_hand_remark` 和 `replacement_condition/replacement_second_hand_remark` 保存供应商成色声明。新报价必须选择 `NEW/USED`，二手必须有说明；历史报价允许字段为空以兼容已有数据。
- 资产判定以数据库表 `procurement_asset_catalog_item` 中已启用的目录为确定性来源；`procurement/asset-catalog-v2.1.json` 只负责首次补齐和数据库读取失败时兜底，不覆盖管理员修改或停用的目录。最高管理员通过“平台管理 / 资产目录管理”逐项维护或批量导入，变更在下一次报价复核时生效，历史报价结论不回写。
- M3 仅负责商品语义匹配和成色复核，模型返回的 `categoryCode/assetNameCode` 必须由 `AssetCatalogService.find` 再校验，禁止直接信任模型生成的资产编码。`SupplierQuoteAssetReviewService` 异步处理 `asset_review_status=PENDING` 的报价明细：命中启用目录为 `ASSET`（固定资产），完成复核后未命中目录为 `LOW_VALUE`（低值易耗）；模型调用失败或结果无法完成时为 `MANUAL_REVIEW`，不得回滚报价。
- 二手商品单价达到 1000 元时通过独立的 `*_special_approval_required` 字段提示特殊审批，不改变固定资产/低值易耗分类。资产复核结果展示在原供应商报价详情、订单中标明细和原飞书替换确认卡片中；特殊审批继续复用现有报价复审流程，不新增旁路审批服务。
- 供应商可以提交全放弃报价；提交记录会保存为 `SUBMITTED`，但有效报价判断仍要求每个采购明细都有 3 个 `ACTIVE` 报价项，所以全放弃记录不会触发倒计时或参与中标。
- 楼栋推荐的临时供应商必须接入原供应商报价流程：从“推荐供应商审批”创建 `temporary_supplier_recommendation` 和临时供应商主数据，临时供应商只允许查看、报价被推荐的采购明细；报价写入原 `supplier_quote/supplier_quote_item`，通过 `quote_source=TEMP_RECOMMENDED` 和 `recommendation_id` 标识来源，不允许新建独立报价库或直接进入月度需求池。
- 个人劳保申领只是一类采购申请入口，不是独立审批流程。页面 `/procurement/personal-labor` 读取 `procurement_setting.personal_labor_item_rules_json` 计算周期、提前申领、提醒、数量和强制监管规则；提交时后端 `PersonalLaborService` 会创建低值易耗采购明细并调用原 `WorkflowService.createRequest`、`WorkflowService.submitRequest`，后续仍走普通采购或紧急采购既有节点。`personal_labor_item_record` 只做个人领用周期台账和“确认已领用”后的下一轮日期计算，不能替代采购申请、审批、报价或收货流程。若需要统一调整现有人员可领用时间，优先按数据迁移修改/补齐 `next_due_date` 台账基线；不要在代码里加全局开始日限制，否则会误伤新入职人员。
- 临时推荐供应商可以先报价后审批，但审批未通过前不得中标；`TemporarySupplierRecommendationService.isQuoteAwardEligible` 是定标和递补时的统一资格判断入口。总价最低策略下，临时供应商只有覆盖整单有效明细才可参与整单中标；单项最低策略下只可参与其被推荐明细的单项中标。
- 临时推荐供应商的报价不计入“三家报价后 3 小时倒计时”触发数量；他们未报价、推荐被拒绝或只放弃报价，不作为正式供应商处罚依据。修改报价、定标、递补或供应商飞书通知时，都要复用 `TemporarySupplierRecommendationService.filterScopedItems/isQuoteCountedForFastCountdown/isQuoteAwardEligible`，避免范围越权或误中标。
- 供应商报价详情接口会过滤 `purchase_request_item.status=REJECTED` 的明细；被单项驳回的商品不展示给供应商，不允许报价。
- 供应商报价阶段至少需要 3 家供应商提交报价，需求人才能提交报价审核；低额紧急采购定向报价且报价未超过上限时除外。
- `GET /api/workflow/purchase-requests/{id}/quotes` 会按账号权限过滤报价明细：系统管理员可在未中标前查看完整供应商报价；其它内部账号在未产生中标结果前只返回 `supplierQuoteCount` 和 `quoteDetailsVisible=false`，不返回供应商名称、最低价、报价金额、交期或报价行明细。具备 `supplier_quote:view-all` 权限的供应商监督账号可跨供应商只读查看全部报价提交，但不能据此修改其他供应商报价、执行比价审核或导出报价审核表；该权限通过独立角色按账号授予，不加入普通 `SUPPLIER` 角色。`/quotes/export` 继续执行内部账号与报价可见性限制，避免通过导出扩大权限。
- 低额紧急采购定向报价规则由 `urgent_direct_award_enabled` 和 `urgent_direct_award_threshold` 控制。楼长通过后，若紧急采购有效金额低于阈值，系统先按当前明细的物料编码、商品名称和规格型号匹配历史中标报价项，选择历史单项中标次数最多且未封禁/未暂停/启用中的供应商写入 `purchase_request.direct_award_*` 字段；没有匹配历史时继续按全局历史中标次数兜底，仍无历史时选择当前可用供应商。定向单只允许该供应商看到并报价；定向供应商报价不超过阈值 130%（默认阈值 1000 元时上限 1300 元）才免三方比价，超过上限会清空定向字段并转回正常三方比价。
- 定向报价单不启动 3 家报价倒计时；定向供应商提交完整有效报价后，手动或定时任务可直接进入报价审核，并按总价最低标记该供应商为中标报价。
- 采购申请在报价有效期内被退回修改并重新提交时，历史报价不得只按行数判断是否完整。系统会按物料编码重新关联报价明细，但仅在商品名称、规格型号、单位和数量均未变化且匹配结果唯一时复用原报价；任一关键字段变化或存在歧义时必须重新报价。重新提交还要显式清空旧 `selected_quote_id`、定向中标、报价倒计时和审批确认字段，避免 MyBatis-Plus 忽略 `null` 后残留旧流程状态。自动报价审核必须记录申请单号、异常代码和异常信息，不能静默吞掉失败。
- 报价截止日期前已满足 3 家供应商提交报价时，`supplier/quotes` 提交逻辑会写入 `quote_countdown_started_at` 和 `quote_countdown_due_at`；定时任务每 5 分钟检查，到 3 小时倒计时截止后自动提交报价审核。
- 所有 `@Scheduled` 任务共用受控线程池，`app.scheduling.pool-size` 默认 4 且不得低于 2，避免天气、飞书等外部网络调用阻塞采购自动比价。外部天气请求必须同时设置连接和读取超时。退回重提的报价对账会把已删除或关键字段已变化商品的旧报价行置为 `INACTIVE`，按当前有效商品重算报价总额；缺少当前商品报价时不得进入中标审核，供应商重新提交后才可继续。
- 前端供应商报价页使用 `quoteCountdownDueAt` 做秒级实时倒计时展示，倒计时到期后提示等待系统自动比价。
- 每个采购需求支持 `awardStrategy` 中标策略：`TOTAL_LOWEST` 表示总价最低供应商整体中标，`ITEM_LOWEST` 表示每一项明细按单项最低价分别中标。
- 采购申请提交时，`purchase_request.total_amount` 和 `purchase_request_item.unit_price/line_amount` 表示当前有效金额，初始来源为申请参考价；`reference_total_amount/reference_unit_price/reference_line_amount` 固定保留申请时价格，`price_source=REFERENCE`。选定完整中标报价并进入报价复审后，必须统一调用 `AwardPriceSyncService` 把当前有效金额回写为 `supplier_quote_item` 的供应商报价（已同意替换商品时使用替换价格），同时把 `price_source` 改为 `SUPPLIER_QUOTE`。报价复审路由、网页审批、飞书卡片、订单和后续供应商递补都读取这套当前有效金额；任何重新定标或递补逻辑都必须再次同步，不能只更新 `selected_quote_id/awarded` 而保留旧参考价。
- 供应商替换商品字段属于报价明细，保存于 `supplier_quote_item.replacement_item_name/replacement_spec_model/replacement_ecommerce_url/replacement_unit_price/replacement_line_amount/replacement_reason/replacement_review_status`。供应商提交替换商品时，后端必须校验名称、规格型号、商品链接和替换原因非空、替换单价大于 0，并复用 `EcommerceUrlService` 规范化商品详情页链接；手工填写和批量导入执行同一套服务端校验。未中标替换商品仅作为报价记录，不触发审批。
- 系统按中标策略标记中标报价后，如果中标明细存在 `replacement_flag=1` 的替换商品，会先创建 `NODE_APPLICANT_REPLACEMENT_REVIEW` 申请人替换商品确认待办；待办候选人为采购申请的 `applicant_user_id`，确认页只返回真实替换商品报价行，并展示原商品、替换商品、报价供应商、联系电话、报价单价/金额、中标状态、交期和报价说明。申请人同意后 `replacement_review_status=APPROVED`，后续报价审批金额和订单明细按替换商品/替换价格执行；申请人不同意或 1 天超时未确认时写入 `REJECTED/AUTO_REJECTED`，按原商品和原报价继续。该流程必须复用原节点和原中标规则，不允许单独接旁路流程或自定义比价规则。
- 报价审核金额不超过 5000 元时由经理终审；超过 5000 元时继续进入区域经理审核；超过 100000 元只有在生产审批配置启用 V6 且存在候选人时才继续进入总监审核。
- 报价审核通过后，供应商在系统内或飞书卡片确认中标；确认后中标商品明细会按采购单部门/机房从 `org_scope.address` 带出送达地址。系统页面确认后自动下载供应商中标清单，飞书卡片确认后自动发送中标清单到当前会话，失败时保留手动下载按钮。中标清单和报价比价表均使用 Excel 导出。
- 全国汇总单需求确认阶段已取消：月度需求池发布生成 `HZCG-*` 后直接写入 `QUOTING/APPROVED` 并进入供应商报价，不创建 V4/V5 需求确认待办。供应商报价比对后的报价复核仍按金额门槛进入 V4/V5，V6 是否进入以生产审批配置是否启用为准。
- 工作台采购列表读取 `approval_task` 当前层级待办生成当前审批人展示；全国汇总单在报价复核 V4 阶段，如果本人就是当前待办候选人，只返回本人姓名/工号，避免同一汇总单暴露其他来源部门的经理。
- 增量 SQL `backend-springboot/sql/zz_zzzzzzzzzzzzzzzzzzzzzzzzzzzz_cleanup_legacy_duplicate_policies.sql` 会停用同部门重复的旧 `POLICY-*` 策略，并按新部门策略同步修正仍在 V4 的全国汇总待办。
- 全国汇总单报价复核 V4 明细过滤必须走 `resolveAvailableAssignmentFromNode` 的新策略优先逻辑，不再先读旧 `procurement_approval_route`；工作台申请明细返回 `roomName/deptName`，前端展示为“机房/部门”，让每名经理只能看到并核对自己来源机房的行。
- 全国汇总单创建时，节点 0 “需求池汇总直达报价”属于系统动作，`approval_record.approver_user_id`、`approval_instance.last_action_by` 和新汇总单 `purchase_request.applicant_user_id` 写入系统管理员账号；工作台 `purchaseRow` 对历史全国汇总单也按 `req_reason=需求池全国月度汇总生成` 或来源明细行识别为“系统管理员”。增量 SQL `backend-springboot/sql/zz_zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz_national_aggregate_admin_trigger.sql` 用于修正历史汇总单，`backend-springboot/sql/zz_zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz_20260702_hzcg_direct_supplier_quote.sql` 用于把无报价数据的历史待审 HZCG 修正为直接报价。
- 增量 SQL `backend-springboot/sql/zz_20260619_7_supplier_quote_replacement_fields.sql` 用于为供应商报价明细补充替换商品字段和申请人确认状态字段。
- 管理员代处理审批时，`ApprovalTaskService.resolveActionCandidateUserId` 会返回实际被关闭的待办候选人，`approval_record.approver_user_id` 和 `approval_instance.last_action_by` 按该候选人写入；增量 SQL `backend-springboot/sql/zz_zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz_approval_record_task_actor.sql` 用于修正旧记录中误显示为系统管理员的通过/退回处理人。
- 系统发起的全国月度汇总单不允许走 `RETURN`：`WorkflowServiceImpl#approveRequest` 会拒绝该动作，工作台和采购审批页同步隐藏退回按钮；增量 SQL `backend-springboot/sql/zz_zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz_national_aggregate_no_return.sql` 撤销历史误退回记录并恢复到被退回前最高审批节点。
- 全国月度汇总单明细过滤不只限 V4 待办态：`detailForAuth` 会按来源需求的申请人、部门助理、当前账号在汇总单当前待办节点以及本人已处理节点对应的来源机房过滤明细，普通账号在已办、流程结束、退回态再次查看时也只能看到自己有权限的机房行；管理员和采购角色保留全量排障/执行视图。
- `GET /api/workflow/purchase-requests/rejected-item-messages` 返回当前账号原始需求中被单项驳回的商品提醒，包含从全国汇总单反查来源申请的场景；右上角消息入口会合并整单驳回和单项驳回提醒。
- 单项驳回消息点击“重新提交”时由 `AppLayout.vue` 携带 `rejectedOnly=1` 进入 `ProcurementRequestForm.vue`，前端只展示 `status=REJECTED` 的明细，并在 `buildPayload` 中把本轮提交明细恢复为 `ACTIVE`；后端 `PurchaseRequestServiceImpl#update` 对存在驳回明细的部分通过单据开放编辑，并重置为草稿后进入新一轮提交。
- 全国汇总单在申请列表接口 `/api/workflow/purchase-requests/page` 中默认隐藏，只在审批待办、审批详情、报价等内部流程中保留；普通用户看到的是自己的来源采购申请。
- 全国汇总审批阶段单项驳回时，后端会按 `source_request_id/source_request_item_id` 同步回写来源采购明细状态并重算来源申请金额；来源申请仍有有效明细时保持 `approval_status=APPROVED`，由前端展示为“部分通过”，全部来源明细被驳回时才置为 `REJECTED`。
- 单项驳回消息按来源明细 ID 去重，避免同一商品同时通过全国汇总行和来源行重复提醒。
- 月度需求池发布的来源查询必须调用 `excludeNationalAggregates` 排除 `req_reason=需求池全国月度汇总生成` 或 `req_no LIKE 'HZCG-%'` 的全国汇总单；`copyDemandPoolItemsToAggregate` 也会兜底跳过全国汇总来源，防止历史异常单据被二次汇总后污染审批节点和来源明细。
- 增量 SQL `backend-springboot/sql/zz_zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz_national_aggregate_v4_final.sql` 会修正已无待办且尚未产生供应商报价的历史全国汇总异常单：普通异常汇总单进入 `QUOTING/APPROVED`，被嵌套进其他全国汇总单的历史汇总来源收口为 `DEMAND_AGGREGATED/APPROVED`。
- 工作台详情接口 `WorkbenchRequestDetailVO.currentApprovalDueAt` 返回当前账号待办的最早 `approval_task.due_at`；前端审批动作区据此提示最晚确认时间，超时确认仍可提交但采购执行顺延到下月。
- 工作台采购行状态由 `WorkbenchServiceImpl#purchaseStatusText` 按当前账号关系二次修正：`relation=done` 且单据仍处于 `PENDING_APPROVAL/PENDING` 时显示“待他人确认”，避免已办人员看到“待审批”误以为还需自己处理。
- 审批任务 `due_at` 用于判断审批处理期限：普通采购（PTCG）的 V3 楼长需求审核按 `monthly_aggregate_day`（默认 20 日）当月 23:59:59 截止，若创建待办时已过当月截止日则顺延到下月同日；其它审批阶段和全国汇总单默认 4 个工作日，由后台采购规则 `approval_confirm_workdays` 配置。定时任务只统计超期待办，不再自动关闭、退回或驳回；如果审批任务在 `action_at > due_at` 后同意，普通采购会把最终执行时间顺延到下月。全国汇总单已完成全部审批后保存为 `DEMAND_AGGREGATED/APPROVED` 并记录下月 `approved_at`，下月月度发布时直接释放为 `QUOTING`，不重新生成需求池审批。
- 报价审核通过后，系统为中标供应商写入 `supplier_confirm_due_at`，默认按 4 个工作日计算；定时任务每 30 分钟扫描超期未确认的中标报价，自动标记 `WAIVED` 并按原中标策略递补下一名。
- 供应商可通过 `POST /api/supplier/quotes/{purchaseRequestId}/award-waiver` 主动放弃待确认中标；主动放弃和超期自动放弃都会写入 `supplier_quote.supplier_confirm_status=WAIVED` 与 `supplier_confirm_remark`，前端报价详情按供应商区块展示放弃中标原因，并计入半年窗口，累计 3 次后写入 `supplier.bid_suspended_until` 暂停 3 个月投标。
- 暂停投标中的供应商不能提交新报价，需求人手动定标、自动报价审核和递补中标都会排除其报价。
- 管理端可通过 `POST /api/admin/suppliers/{id}/bidding-suspension/release` 手动解除暂停投标，接口复用供应商更新的数据范围校验。
- 单项驳回后的整单流转按“有效商品行数”判断，而不是按有效金额判断。只要存在未驳回商品行，审批结果视为通过或部分通过；普通采购进入 `DEMAND_POOL`，紧急采购进入 `QUOTING`。只有所有商品行都被驳回时，整单才置为 `REJECTED`。
- 工作台、采购申请列表和审批列表的“部分通过”是展示口径：后端仍保存 `approval_status=APPROVED`，前端/工作台根据同一采购单是否同时存在 `purchase_request_item.status=REJECTED` 和有效商品行显示“部分通过”。
- 统一工作台系统管理员视图不走本人相关过滤：`WorkbenchServiceImpl` 会补充加载全部采购申请和售后请求，前端 `WorkbenchPage.vue` 按 500 条/页循环拉取直到达到接口 `total`；普通账号仍由待办、已办、发起、抄送和部门助理授权关系控制可见范围。
- 部门助理权限保存在 `procurement_approval_route.assistant_user_ids/assistant_names`，只影响工作台“部门工单”查看范围和全国汇总单来源明细可见性，不进入 V1-V8 审批流。
- 统一工作台页面采用主队列全宽 + 底部三栏布局：`WorkbenchPage.vue` 的队列页签包含“我的关注”，底部三栏展示本周处理、审批进度和公告栏，不再保留右侧“我的关注/最近处理”独立卡片，也不展示“超时预警”指标。
- 工作台公告由 `workbench_notice` 表持久化，读取接口为 `GET /api/workbench/notices`，维护接口为 `/api/workbench/notices/manage`；维护动作必须具备 `workbench_notice:manage` 权限或 `SUPER_ADMIN` 身份，前端也用同一权限码控制“编辑”入口。
- 角色-权限关联表 `sys_role_permission` 不能用 MyBatis Plus 逻辑删除后立即重插同一权限，因为唯一键仍是 `(role_id, permission_id)`；角色更新和删除统一调用 `SysRolePermissionMapper#hardDeleteByRoleId` 物理清理绑定关系，再按去重后的权限 ID 重建，保证 `/api/admin/roles/{id}` 幂等保存。

平台应用准入基线：
- `PLATFORM` 只代表平台管理能力，不能作为所有业务应用的通配权限。前端菜单、路由守卫和后端工作台接口都必须按显式 `appCode` 判断，采购使用 `PROCUREMENT`，维保使用 `MAINTENANCE`，平台管理使用 `PLATFORM`。
- 新增请假、加班或其它业务线时，必须新增独立应用编码、菜单、路由和后端来源映射；未完成业务流程和权限设计前，不得因为用户拥有 `PLATFORM` 权限而开放到统一工作台或侧边栏。
- 统一工作台入口可以保持全员可见，但聚合来源必须由 `WorkbenchServiceImpl` 根据当前账号 `AuthUser.appCodes` 过滤；列表、详情、审批提交、关注/取消关注都要做同样后端校验，不能只依赖前端隐藏菜单。
- 前端 `/workbench` 路由和菜单使用 `anyAppCodes: ["PROCUREMENT", "PLATFORM", "MAINTENANCE"]` 表示任一已开放应用可进入；没有任何应用准入的账号只能进入 `/platform/access`，不能看到空工作台。
- 前端新增 `npm run check:access`，会扫描 `src/router/index.ts` 与 `src/config/menu.ts`，新增业务路由或菜单后必须通过该检查，避免未开放业务线或直接 URL 绕过应用准入。
- `/api/workbench/requests/**`、`/api/workbench/follows` 和 `/api/workbench/users/options` 属于业务聚合接口，`AuthInterceptor` 必须要求当前账号至少具备 `PROCUREMENT/PLATFORM/MAINTENANCE` 任一应用准入。`/api/workbench/notices` 是内部公告读取入口，可作为登录后公共信息；`/api/workbench/notices/manage/**` 属于平台维护入口，必须同时具备 `PLATFORM` 准入和 `workbench_notice:manage` 权限。
- 所有直接业务入口也必须带路由 `meta.appCode`。例如 `/feishu/procurement-intake` 虽然不在侧边栏里，但仍属于采购提交入口，必须要求 `PROCUREMENT` 准入。
- 业务 API 必须在 `AuthInterceptor#requiredAppCode` 登记前缀和应用编码。当前采购 API 包括 `/api/workflow`、`/api/procurement`、`/api/supplier`、`/api/admin/procurement`、`/api/admin/purchase-requests`、`/api/admin/suppliers`，统一要求 `PROCUREMENT`；平台管理 API 要求 `PLATFORM`；维保 API 预留 `MAINTENANCE`。登录、飞书回调、购物工具、权限申请和全员反馈提交属于公共或集成入口，不在业务应用准入拦截内。
- 采购流程角色的系统准入要与审批角色保持一致。工程师、楼长、采购员、经理、区域经理、总监、供应商和管理员默认需要 `PROCUREMENT` 准入；新增或修正默认准入时同步更新初始化 SQL 和幂等增量 SQL。
- 维保管理准入必须显式维护，不随 `SUPER_ADMIN`、工程师或楼长身份自动授予；当前生产要求除董博外关闭所有账号 `MAINTENANCE` 准入，对应最终兜底 SQL 为 `backend-springboot/sql/zz_zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz_20260706_maintenance_access_dongbo_only.sql`。

流程设计和并发开发基线：
- 任何 AI、Dify、飞书机器人、卡片或后台快捷入口都只能作为原流程的输入、提醒或辅助建议层，不能绕过原有采购申请、审批任务、权限校验、审计记录和状态流转。
- 涉及采购、审批、需求池、报价、库存、售后等流程时，先查找并复用现有服务和节点；确需新增分支、表或独立状态机时，必须先向业务负责人确认，不能自行接入新流程替代原流程。
- 飞书管理员抄送只是在正式审批卡片和供应商报价任务卡片发送链路上增加额外接收人，不是新审批节点。审批抄送记录使用 `feishu_approval_card_notification.recipient_user_id` 保存卡片接收人，`approver_user_id` 保留原审批人；管理员点击卡片后仍调用原 `WorkflowService.approveRequest`。供应商报价抄送也只能指向现有供应商报价页面，不能替代 `SupplierQuote`/`SupplierQuoteItem` 的导入、比价和中标流程。
- 管理员抄送不能依赖业务接收方是否绑定飞书。审批抄送只依赖审批任务和管理员账号；供应商报价任务抄送只依赖采购申请进入供应商报价阶段和管理员账号，供应商本人是否已在 `supplier_feishu_binding` 绑定外部会话只影响供应商本人收卡。管理员账号接收目标应优先使用后台配置的消息接收 ID 类型，缺失时回退 `user_id/open_id/union_id/email` 中已有值，并把失败原因写入对应日志。
- 正式审批卡片、供应商报价任务卡片和测试卡片都必须有管理员默认接收兜底。业务审批人缺少邮箱或飞书 ID 时，卡片可以投递到管理员邮箱/飞书 ID，但通知记录仍要保留原审批人或原供应商任务上下文；管理员抄送和兜底投递允许使用同一个管理员邮箱生成多条卡片，不能因为接收 ID 相同就把不同场景去重掉。供应商报价卡片日志必须用 `supplier_feishu_quote_notice.binding_id` 区分场景：真实供应商绑定写绑定表 ID，管理员抄送写 `0`，管理员兜底写 `-1`；唯一键必须包含 `binding_id`。
- 卡片发送成功后清理历史错误信息时不要写 `last_error = null` 依赖 ORM 默认更新策略；`feishu_approval_card_notification` 和 `supplier_feishu_quote_notice` 成功状态应显式写空字符串，历史已发送记录通过增量 SQL 清掉旧错误文本。
- 飞书 JSON 2.0 卡片必须使用根级 `schema/config/header/body`，正文元素放在 `body.elements`，不能再写根级 `elements`；跳转按钮使用 `behaviors: [{ type: "open_url", ... }]`，不要使用旧版根级 `url`。否则飞书会返回 `code=230099`、`unknown property: elements` 等创建卡片失败。
- 飞书采购 AI 助手确认提交时必须创建并提交正式 `purchase_request`，后续走普通采购或紧急采购既有审批链；普通采购是否进入月度需求池只能由楼长审核通过后的原流程决定，不能直接写入 `monthly_procurement_demand` 作为捷径。
- 飞书采购 AI 助手候选确认生成正式采购申请时，`purchase_request.req_title` 必须由后端按“机房/楼栋-采购类型采购申请”统一生成，类型标签目前按 `服务/费用/低值易耗等 -> 低值易耗`、`实物资产/无形资产/长摊等 -> 资产`、`工程项目 -> 工程项目` 映射；不能把 `procurement_ai_session.req_title`、用户聊天原文、候选商品名或“买 X 个吧”这类自然语言直接写入正式申请标题。
- 飞书采购 AI 助手面对缺少商品名称、数量、用途、规格或大于 0 的参考单价/预算的消息时，只能保存为 `procurement_ai_session.status=DRAFTING` 待补充草稿并追问，不能把“买一个本”等模糊表达直接变成采购候选或申请单。普通文本草稿补充后仍走 Dify 工作流判断；图片草稿如果已由 M3/MiniMax 识别出商品名称、规格和参考价，后续用户输入的任意整数 `X` 或自然语言补充必须交给 M3/MiniMax 结构化判断数量、单位和用途，后端只校验模型输出字段，不用正则枚举用户说法。只补齐数量但缺用途时继续保留草稿并追问用途，不创建候选。
- 飞书采购 AI 助手已有待确认候选时，后续自然语言必须先交给 M3/MiniMax 解析候选操作，输出 `UPDATE_QUANTITY`、`UPDATE_PURPOSE`、`CANCEL_ITEM`、`CANCEL_ALL`、`CONFIRM_ONE`、`CONFIRM_ALL`、`PREVIEW_WAITING`、`CREATE_NEW_DEMAND`、`CLARIFY` 或 `NONE`。后端只执行模型给出的结构化动作，并且必须复用 `ProcurementAiService` 的候选更新、撤回和确认方法；M3 不得直接写正式采购申请、审批任务、月度需求池或供应商报价。模型判断为 `CREATE_NEW_DEMAND` 或 `NONE` 时，才回到现有 Dify/普通采购候选生成链路。
- 飞书采购 AI 助手待确认候选的自然语言编辑必须支持多轮上下文合并。用户可能分多条消息补齐候选序号、动作、数量或用途，例如先说“第三个”、再说“修改数量”、最后说“100 个”；后端应把这些字段保存在短时待澄清上下文里，字段齐全后再调用 `ProcurementAiService.updateWaitingCandidateQuantity/updateWaitingCandidateReason/cancelWaitingSession/confirmBySessionNo` 等既有方法。不要把每条消息孤立处理，也不要因为用户上一条只说了序号就丢失上下文。
- 飞书采购 AI 助手候选撤回应按明细粒度处理。文本命令里的撤回、删除、提交、确认属于高风险动作，必须先记录待确认动作并追问，用户明确回复“确认/确定/是”等肯定词后才允许调用 `ProcurementAiService.cancelWaitingSession/cancelAllWaiting/confirmBySessionNo/confirmAllWaiting`；裸序号如“X号吧/第X项”只能作为上一轮澄清的候选选择，不能默认提交。飞书候选卡片按钮必须先做提交前临时标记，已标记明细保留在卡片中并用删除线展示，按钮切换为“恢复”，只有最终确认提交时才把已标记明细落库为取消；“撤回全部”必须使用飞书按钮确认弹窗。卡片行内操作可用 JSON 2.0 `column_set` 模拟，不能依赖 Markdown 内嵌按钮。
- 飞书采购 AI 助手候选卡片展示字段统一叫 `AI审核意见`；候选阶段可以解析 Dify `approvalReview.approvalCommentSuggestion/summary/reviewPoints` 作为草稿参考，Dify 只给摘要时必须本地生成兜底意见，不能把普通摘要或 Dify 技术错误直接展示给用户。正式提交、批量导入后提交和退回重提统一由 `procurement-compliance-review` Skill 生成最终意见并覆盖写入 `purchase_request.ai_review_opinion`；正式审核输入只能包含每条 `PurchaseRequestItem.purpose` 采购原因，申请标题、采购类型、部门、金额、紧急程度、需求日期、商品名称、规格、数量、单价和商品链接均不得进入模型上下文，也不得成为补充意见依据。这是正式审批阶段唯一的最终 AI 审核意见来源，不允许占用 `remark` 业务备注，也不允许保留第二套逻辑并行覆盖。结论必须以“建议通过：”“建议补充：”或“建议退回：”开头；字段、数量、价格、金额和审批路由等硬规则仍由 `PurchaseRequestService.submit` 确定性校验，MiniMax 只提供采购原因的辅助建议，失败或低置信度时不得阻断原审批流程。
- 运行时知识库和 Skill 统一通过 `/api/admin/ai-governance/**` 与“平台管理 / AI知识与技能”维护。模型只能为上下文中已存在的目标编码提交完整替换正文，后端去重后保存为 `PENDING` 建议；只有系统管理员审核通过才能更新正文、递增修订号并保存 `MODEL_APPROVED` 完整快照。静态 `src/main/resources/ai-skills` 仅作为数据库读取失败时的兜底，不能成为第二套长期生效配置。详细约束见 `docs/AI_KNOWLEDGE_SKILL_GOVERNANCE.md`。
- 采购原因编辑辅助统一调用 `/api/workflow/purchase-requests/purpose-optimize`，运行时每次读取启用的 `procurement-purpose-optimization` Skill。模型输入只包含当前采购原因，输出只追加到浏览器草稿，不能保存明细、提交申请或改变预审基线；数据库读取异常时才允许使用随包兜底，Skill 主动停用时必须保持不可用。
- 飞书审批卡片单项驳回有两个不同字段：`rejectItemId` 表示本次点击的明细，`rejectItemIds` 表示当前已标记列表。长连接回调和服务层 toggle 逻辑必须分开解析，不能先把 `rejectItemId` 合并进列表再 toggle，否则第一次点击会被立即移除，看起来按钮没有反应。只有最终提交单项驳回时才把旧版单选字段作为兼容兜底合并。
- 飞书卡片按钮 `value` 不允许写入 `null` 可选字段；AI 候选卡的 `sessionNo/candidateNo`、审批卡的分页和明细字段都应只在有值时写入。否则飞书客户端可能在点击时直接弹 `200673`，后端收不到 `card.action.trigger`，排查时会误以为长连接失效。大批量候选或审批明细不能一次性塞进一张卡片，AI 候选和审批明细都应每页 10 条左右并用卡片按钮翻页，刷新、撤回和翻页动作要携带当前页码。
- 飞书采购 AI 助手处理批量文本时必须保持明细粒度：用户一次发送多个实物商品，Dify 和后端兜底都应按商品、数量、单位、用途拆成多条 `procurement_ai_candidate`，不要把“办公用品及设备采购”“一批物资”等概括词写成明细 `itemName`。
- 飞书采购 AI 助手处理链接时不能只读 `content.text`；飞书富文本或预览中的 `title/href/url/link` 要递归提取并传入 Dify，Dify 输出候选时应保留到 `ecommerceUrl`。如果事件只提供 URL 且缺商品名称、数量或用途，仍然保存草稿并追问。
- 飞书采购 AI 助手处理 `.xlsx/.xls` 文件消息时，必须先通过飞书消息资源接口下载文件并用后端 Excel 解析工具转为结构化文本，再交给 Dify；不能把文件预览图当作图片识别，也不能让 AI 凭文件名猜表格内容。解析后的表格内容应写回 AI 助手消息日志的用户消息，便于排查。
- 标准采购明细 Excel 模板必须由后端按表头确定性解析作为兜底，字段映射至少包含商品名称、采购类型、规格型号、单位、数量、单价/预算、采购原因/用途和商品链接；Dify 可以提供 `approvalReview` 和更自然的补充追问，但不能成为唯一解析层。只要表格存在缺商品名、缺采购类型、缺规格、缺用途、数量小于等于 0 或单价/预算小于等于 0，就按行号追问更正，不允许退回到普通文本兜底生成概括候选。
- 飞书采购 AI 助手、Dify DSL、本地兜底和正式采购申请保存都必须要求单价大于 0；缺失价格、价格为 0 或小于 0 时应追问或报错，不允许用 `0` 作为待确认价格占位生成候选或申请。
- 价格校验不能只放在 AI 候选或前端页面；`PurchaseRequestService.submit` 和 `WorkflowService.approveRequest` 必须对持久化后的有效明细再次校验数量、单价和金额均大于 0，旧版飞书卡片或历史草稿点击通过时也必须被服务端拒绝。
- 飞书采购审批卡片的单项驳回应按系统审批弹窗的行为建模：明细行右侧按钮只负责在卡片 `value.rejectItemIds` 中标记或恢复待驳回项，卡片上用删除线展示已标记明细，驳回原因统一放在明细列表下方；真正提交时仍调用 `WorkflowService.approveRequest` 并写入 `itemDecisions`，不能新增绕过原审批服务的卡片专用状态流。
- 开始改动前必须执行 `git status --short` 并查看目标文件当前 diff；如果发现同一流程、同一服务或同一页面已有他人未完成改动，先停下来明确文件归属和合并顺序，不能用“恢复原文件”或大段重写覆盖对方改动。
- 两个任务确需并行时，按模块拆分所有权，例如“后端服务/SQL”和“前端页面/文档”分开提交；同一文件只能由一个任务负责，另一个任务通过小补丁或评审建议合入。
- 每个任务尽量形成小而完整的提交，提交说明写清楚触达的流程、接口、SQL 和页面；发布前再次检查是否误删了相邻任务新增的字段、接口、文档和版本记录。
- 每次正式发布必须把新版本放在 `frontend-vue/src/config/systemUpdates.ts` 首位，并按“问题修复、功能更新、体验优化”记录用户可感知的变化；系统左下角版本号读取同一份清单，不再单独维护。可由用户直接修正的参数、日期和表单校验问题应返回明确提示并加入 `GlobalExceptionHandler.NON_ALERT_BIZ_CODES` 对应分类，不写入错误台账；已上线的问题修复仍需在“平台管理 / 系统更新”保留版本记录。
- 生产发布统一从“平台管理 / 上线管理”创建批次：PR 构建通过后，批次必须在一次性干净数据库和共享测试站分别验证精确 SHA 与镜像 digest，再由上线管理员确认。页面可以内嵌独立上线控制面，但业务后端不得保存或调用个人 GitHub Token；GitHub App 私钥、生产 HMAC、上线管理员密码和 Runner 门禁都属于宿主机运维配置，不进入代码、数据库或 GitHub Environment。

编码和乱码处理基线：
- 发现疑似乱码时，先记录文件路径、命令输出片段和判断依据，再用 `Get-Content -Encoding UTF8` 或编辑器 UTF-8 读取复核，区分“终端编码显示错误”和“源码真实乱码”。
- 如果源码真实乱码出现在本次触达文件，必须随本次改动一起修复；如果是历史遗留且范围较大，记录到 `docs/CODE_GAP_CHECKLIST.md` 并说明本次是否触达。
- 每次收尾必须对本次触达的 Java/Vue/Markdown/YAML 文件做乱码搜索验证，至少检查常见 UTF-8 被 GBK 误读后的片段和 Unicode 替换字符；扫描脚本里的样例字符不要直接写进业务文档，避免后续误报，并在最终说明验证结果。

采购看板当前口径：
- 主要接口切换为 `/api/procurement/bi/*`：`GET /datasets` 获取中文数据集，`POST /query` 按白名单字段查询明细或聚合结果，`GET/POST /dashboards/default` 读取和保存当前账号采购看板布局。
- 后端实现位于 `ProcurementBiController`、`ProcurementBiServiceImpl`。查询不接受前端原始 SQL，只允许使用服务端 `DatasetSpec` 登记的数据集、字段、聚合函数、过滤和排序。
- 当前内置 6 个采购 BI 数据集：`purchase_items` 采购明细表、`supplier_quote_items` 供应商报价明细表、`receipt_items` 中标收货明细表、`after_sales` 售后明细表、`supplier_quotes` 供应商报价汇总表、`approval_tasks` 审批过程表；每个字段都带中文名称、字段类型、业务语义、单位和默认聚合方式。报价明细包含逐项报价、中标状态、收货状态和供应商确认状态；售后明细包含原商品、替换商品、申请人、审核人和收货信息。机房展示默认使用 `roomName`，由完整部门路径按最后一个 `-` 截取生成；`deptName` 保留为完整部门路径字段。
- BI 查询会按当前账号追加可见范围：`SUPER_ADMIN` 看全量，供应商账号按 `supplierId` 限制，内部账号只按本人 `deptCode/deptName` 所在部门以及新审批策略/机房路由中授权的机房收敛；看板不能再因为申请人、采购人、审批任务或审批记录关系扩大到整单其他机房，也不要使用区域兜底扩大内部账号可见范围。
- 前端 `ProcurementDashboard.vue` 使用 GridStack 12 列画布保存组件 `x/y/w/h`，编辑模式下通过组件顶部拖拽点排序、右下角手柄调整宽高，并采用画布选中态加右侧配置面板承载字段和图表配置。
- 表格类组件（组合表、采购明细）必须受 GridStack 卡片高度约束，`.widget-body` 和表格 wrapper 使用 `min-height: 0`，表格内容区内部滚动，避免空数据或长明细撑开整页。
- 组件标题、复制组件和新增组合表/图表均在前端布局模型内保存；复制出的组件使用独立 widget id，并分别存储数据源、时间范围、分组字段、统计数值、聚合、过滤、排序、展示数量、图表类型、主题色、图例、数据标签、坐标轴和网格线开关。前端配置面板使用“按什么分组 / 统计什么数值 / 汇总算法”降低业务用户理解成本，并会在切换数据源或组件类型时过滤无效字段；历史布局中的 `deptName` 会自动迁移为 `roomName`。
- 图表渲染使用 ECharts，当前支持指标卡、组合表、明细表、柱状图、饼图、环形图和折线图；非编辑模式点击图表数据项会用当前分组条件再次调用 `/api/procurement/bi/query` 明细查询并打开弹窗，钻取结果仍由后端统一做机房权限过滤；看板布局持久化到 `bi_dashboard.layout_schema`，开发环境接口不可用时前端仅用于本地预览的 fallback 数据不会参与生产保存。
- 前端组件级时间筛选通过 `ProcurementDashboard.vue#widgetTimeFilters` 从当前 widget 的 `timeRange` 追加 `createdAt BETWEEN` 条件；指标卡、图表、组合表、明细表和钻取明细都复用 `effectiveFilters`，不要绕过组件时间筛选单独拼查询条件。
- 图表钻取明细通过 `buildDrillFilters` 同时追加当前分组条件和点击的统计数值 `> 0` 条件；钻取弹窗用 `defaultVisibleDrillFields` 控制默认展示列，保留 `detailFieldCodes` 查询结果供用户自行勾选列和关键字筛选。
- 管理端可通过 `POST /api/admin/suppliers/{id}/ban` 和 `/unban` 封禁/解封供应商；封禁复用 `supplier.blacklist_flag/blacklist_reason`，报价提交、确认中标、自动定标和递补都会排除封禁供应商，封禁时会立即取消其待确认中标并递补。
- 中标确认邮件通过平台集成设置中的 `MAIL` 组 SMTP 配置发送；供应商收件地址来自供应商主数据 `contact_email`。
- 部门管理维护的地址字段落在 `org_scope.province/city/district_name/town_name/address`，页面按省、市、区县、乡镇/街道、详细地址录入；`procurement_route:address` 可用于单独授权部门地址维护。
- 部门管理批量维护复用 `procurement_route:manage` 权限，接口为 `GET /api/admin/procurement/departments/import-template` 和 `POST /api/admin/procurement/departments/import`；导入模板按部门 ID、路由编码、机房、楼栋、成本中心、部门编号、部门名称做只读识别校验，并在事务内先完成全表校验后再更新，任一行不匹配不会落库。

集采规则当前基线：
- 集采池仍由月度需求汇总策略生成采购申请，紧急需求仍直接生成并提交审批。
- 月度需求汇总标题面向当前酒仙桥区域展示为“YYYY-MM酒仙桥区域月度需求汇总”；历史数据库中仍含“全国月度需求汇总”的标题由 `V23__jiuxianqiao_monthly_aggregate_title.sql` 和部署增量 SQL 同步替换，服务层读取旧标题时也会兼容显示为新名称。
- `default_award_strategy` 控制集采生成采购申请时的默认中标策略，后续可在采购申请编辑页按单据手动调整。
- `monthly_aggregate_day` 控制每月集采审批截止日；定时任务每天 00:05 检查前一日是否命中该配置，命中后通过原需求池发布服务以系统任务身份归集，确保截止日 23:59:59 前完成审批的需求都能纳入本期。
- `monthly_purchase_start_day` 和 `monthly_purchase_end_day` 仍控制非紧急集采需求可提交窗口。

智能分析底座当前口径：
- 生产部署栈新增 Redis 和 Qdrant。Redis 先作为问答缓存、会话短期状态和热点答案加速层预留；Qdrant 先作为采购规则、字段字典、常见问题和业务知识片段的向量检索库预留。
- 后端配置项位于 `procurement.ai.qdrant.*`，生产环境默认读取 `QDRANT_ENABLED`、`QDRANT_URL` 和 `QDRANT_COLLECTION_PREFIX`；Dify 优先读取平台集成设置里的 `dify_api_base_url`、`dify_api_key`、`dify_app_type`，未填写时再使用 `procurement.ai.dify.*` / `DIFY_PROCUREMENT_*`。
- Dify Chatflow Start 节点的 `text-input` 变量会严格校验字符串类型；后端传给 Dify 的 `raw_text/text`、`req_type`、`plan_month`、`need_date`、`urgency_level`、`emergency` 等输入必须统一按字符串传递，避免 `invalid_param`。
- Dify Chatflow 使用 `chat-messages` 流式接口时，最终业务 JSON 可能出现在 SSE 的 `workflow_finished.data.outputs.text`，后端解析不能只看顶层 `answer` 或 `outputs`；`message_end` 只代表消息结束，不能覆盖前面 `workflow_finished` 的输出。
- Dify 联网搜索只作为商品候选参考来源，不得绕过采购申请、楼长审核、月度需求池和报价流程；如果要联网，优先在 Dify 的 `product_recommendation` 分支使用 Tool/HTTP Request 节点，并把结果整理到 `products/results/sources`，不要让机器人把“添加联网插件”这类配置建议回复给飞书用户。
- Dify DSL 的 `product_recommendation` 分支必须要求多商品拆分、重复粘贴去重、链接标题/URL 保留；后端本地兜底只做保守解析，不能替代 Dify 对缺失信息的追问和审核意见生成。
- 自动部署的前端审计步骤会解析 `npm audit --json` 结果：存在 moderate/high/critical 漏洞时失败；如果 npm registry 审计接口临时不可用且未返回漏洞元数据，只记录 warning 并继续执行 `npm run build`，避免生产发布被外部审计接口抖动阻断。
- BI 元数据表以 `bi_` 开头，核心包括 `bi_dataset`、`bi_dataset_field`、`bi_dataset_relation`、`bi_metric`、`bi_dashboard`、`bi_dashboard_widget`、`bi_dashboard_filter`、`bi_saved_view` 和 `bi_share_scope`。这些表用于把物理表字段转换成对外中文名称、约束可筛选/可分组/可聚合字段，并支撑后续用户自由组合表和看板。
- AI 元数据表以 `ai_` 开头，核心包括 `ai_question_answer`、`ai_question_variant`、`ai_knowledge_chunk`、`ai_prompt_template`、`ai_agent_tool`、`ai_agent_session`、`ai_agent_message`、`ai_query_log`、`ai_feedback`、`ai_vector_document`、`ai_vector_sync_job` 和 `ai_answer_cache_meta`。这些表用于常见问题复用、相似问法匹配、向量同步、智能体工具注册、会话留痕和反馈闭环。
- 当前版本完成服务和元数据底座部署，暂不直接开放新的 Agent 聊天页面。后续接入时应优先按 `bi_dataset_field.field_name` 对外展示中文字段，按当前用户权限过滤数据集，并在命中 `ai_question_answer` 或 Redis 缓存时直接返回，未命中再走向量检索和模型调用。

数据库增量：
- `backend-springboot/sql/zz_zzzzzzzzzzzzz_quote_fast_countdown.sql` 为历史环境补齐 3 家报价倒计时字段和索引；`backend-springboot/sql/zz_zzzzzzzzzzzzzzzzzz_urgent_direct_award.sql` 为历史环境补齐低额紧急定向报价字段、索引和后台设置项；`backend-springboot/sql/zz_zzzzzzzzzzzzzzzzzzzz_urgent_direct_award_repair.sql` 会把已进入报价、低于阈值且已有有效报价的紧急单补上定向供应商标记；`backend-springboot/sql/zz_zzzzzzzzzzzzzzzzzzzzz_department_route_import_permission.sql` 新增 `procurement_route:import` 独立权限，用于控制部门模板导出和批量导入修改；初始化库同步更新在 `backend-springboot/sql/schema.sql`。
- `backend-springboot/sql/zz_zzzzzzzzzzzzzz_supplier_award_confirm.sql` 为历史环境补齐供应商中标确认截止时间、邮件通知时间、递补扫描索引和邮件服务器设置项。
- `backend-springboot/sql/zz_zzzzzzzzzzzzzzz_supplier_bidding_suspension.sql` 为历史环境补齐供应商暂停投标字段和索引。
- `backend-springboot/sql/zz_zzzzzzzzzzzzzzzzzzzzzzz_procurement_after_sale.sql` 新增 `procurement_after_sale`，售后订单由中标 `supplier_quote_item` 推导，不单独复制订单主表。
- `backend-springboot/sql/zz_zzzzzzzzzzzzzzzzzzzzzzzz_user_password_policy.sql` 为历史环境补齐用户密码修改时间和历史密码表；发布后旧账号的 `password_changed_at` 会按 `updated_at/created_at/NOW()` 初始化，后续改密由代码维护。
- `backend-springboot/sql/zz_zzzzzzzzzzzzzzzzzzzzzzzzz_order_receipt_after_sale_windows.sql` 为历史环境补齐中标明细收货状态和 7 天无理由/14 天退换货规则设置；`zz_zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz_20260812_disable_auto_receipt.sql` 将历史已收货明细恢复为待收货、清空自动截止时间并移除自动收货设置。
- `backend-springboot/sql/zz_zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz_ai_bi_agent_foundation.sql` 新增智能分析底座表，初始化采购申请、采购明细、供应商、报价、报价明细和售后申请等数据集的中文字段字典，并预置采购看板、常用指标、AI 提示词模板和智能体工具注册。
- `backend-springboot/sql/zz_zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz_workbench_notice.sql` 新增 `workbench_notice` 表、`workbench_notice:manage` 权限和默认公告；初始化库同步更新在 `backend-springboot/sql/schema.sql`。

我的订单与售后当前基线：
- 接口入口为 `GET /api/procurement/orders/my/page`、`GET /api/procurement/orders/my/{quoteItemId}`、`GET /api/procurement/orders/my/groups/{requestNo}`、`GET /api/procurement/orders/my/groups/{requestNo}/export`、`GET /api/procurement/orders/my/{quoteItemId}/export`、`GET/POST /api/procurement/orders/my/{quoteItemId}/invoices`、`POST /api/procurement/orders/my/{quoteItemId}/receipt`、`POST /api/procurement/orders/my/{quoteItemId}/after-sales`、`GET /api/procurement/orders/after-sales/page`、`POST /api/procurement/orders/after-sales/{id}/approval`。订单资料接口保留原路径，通过 `documentType=INVOICE/DELIVERY_NOTE` 区分发票与送货单。
- 订单数据由 `ProcurementOrderQueryMapper` 从已中标 `supplier_quote_item`、`supplier_quote`、`purchase_request` 和 `purchase_request_item` 实时查询生成，订单号格式为 `申请单号-行号`；接口同时返回 `aggregateReqNo`、`sourceReqNo/sourceReqTitle/sourceReqType`，用于前端按集采/采购单展示和按来源申请盘点；报价审核已通过但供应商确认状态为 `PENDING` 的中标记录仍进入“我的订单”，前端显示为“待供应商确认”。
- `workflow-submit.ts` 统一维护采购提交和审批流转后的下一步审核人弹窗；弹窗内容口径只展示申请单号和下一步审核人，不展示审核节点、通过规则或说明性提示。
- 访问控制在 `ProcurementOrderServiceImpl` 中完成：申请人、采购员可看自己的订单，供应商只能看本供应商订单；售后审核由订单中标供应商处理，系统管理员保留兜底处理能力。
- 供应商确认中标后，`SupplierQuoteServiceImpl#confirmAward` 只把中标明细置为 `receipt_status=PENDING`；收货必须通过原订单人工确认入口完成，不再生成 `receipt_due_at`，也不再运行自动收货定时任务。
- 售后窗口按 `received_at` 计算：`NO_REASON_RETURN` 使用 `after_sale_no_reason_enabled/days`，`RETURN_EXCHANGE` 和历史换货/维修类型使用 `after_sale_return_exchange_enabled/days`；未收货或窗口过期时后端拒绝创建售后。
- 后端创建售后时会拦截同一 `quote_item_id` 已存在 `PENDING`、`PROPOSAL_PENDING` 或 `APPROVED` 售后的重复申请；`REJECTED` 售后不占用窗口，可在有效期内重新提交。
- 售后处理统一接入工作台：`WorkbenchServiceImpl` 将 `procurement_after_sale` 映射为 `AFTER_SALE` 来源。`PENDING` 表示待供应商给方案，按 `supplierId` 进入中标供应商“待我处理”；供应商提交方案后状态变为 `PROPOSAL_PENDING`，进入售后申请人/采购员待办；申请人同意后变为 `APPROVED`，不同意则回到 `PENDING` 让供应商重提方案。
- 供应商方案字段直接扩展在 `procurement_after_sale`：`supplier_proposal_*`、`supplier_replacement_*` 和 `requester_confirm_*`；不创建服务失单表。`ProcurementOrderServiceImpl` 对供应商更换商品方案做单价上限校验，方案单价不得高于当前订单单价 400 元以上。
- 前端 `MyOrders.vue` 按 `HZCG-*` 集采单、紧急采购和普通采购单聚合，列表顶部通过“全部 / 集采订单 / 紧急采购 / 普通订单”标签切换并按当前标签分页；点开后进入明细页表格，可按供应商、来源申请单、状态和商品关键词筛选，并通过批量导出接口导出当前账号可见的盘点 Excel。售后申请提交前显示“下一步：供应商售后审核 / 处理人”并二次确认；售后审核按钮集中在 `WorkbenchPage.vue`。
- 供应商已售管理入口复用 `MyOrders.vue` 和 `/api/procurement/orders/my/page`，路由为 `/procurement/sold-orders`；页面按供应商视角显示“已售批次与售后”，支持按商品、集采单号、订单号、申请人和供应商查询。普通供应商账号按 `sys_user.supplier_id` 限定本供应商；具备 `procurement_order:supplier-manage-all` 的供应商最高监督账号可跨供应商查看列表/详情、导出归档、上传发票与送货单并处理供应商侧售后。该权限不授予买方收货、发票审核、采购审批、系统管理或原始报价修改能力；未绑定供应商主档的供应商账号仍会被 `ProcurementOrderServiceImpl` 拒绝访问。
- `OrderInvoiceService` 继续承载订单资料闭环，不另建旁路：发票和送货单按 `document_type` 维护独立版本号及审核状态；中标供应商、来源申请对应的部门助理和最高管理员可上传，服务层按订单对象再次校验，普通内部账号仅有接口权限也不能越权上传。上传记录保存 `submitted_by_role`，供应商提交成功后在事务提交后通过现有 `FeishuApprovalCardService` 通知来源部门助理。
- 新库由 `V38__order_document_collection.sql` 扩展订单资料类型和联合唯一索引，已有环境使用 `backend-springboot/sql/zz_20260812_order_document_collection.sql` 幂等升级；附件仍保存到原 `order-invoices` 持久化目录并沿用摘要校验和鉴权下载。
- 退货类售后通过后由 `ProcurementOrderServiceImpl` 回写 `purchase_request_item.status=RETURNED` 并重算 `purchase_request.total_amount`；采购看板供应商维度按已通过售后统计退货率和换货率，退货率包含 `NO_REASON_RETURN/RETURN_EXCHANGE`，换货率包含 `REPLACE_ITEM`。

## 5.4 维保模块

维保模块页面入口已经在前端路由中注册：

- `/maintenance/dashboard`
- `/maintenance/sources`
- `/maintenance/upload`
- `/maintenance/review/:parseId`
- `/maintenance/assets`
- `/maintenance/renewal`
- `/maintenance/settings/people`
- `/maintenance/settings/rooms`

对应页面目录：

- `frontend-vue/src/pages/maintenance`

实际可运行页面以 `frontend-vue` 工程中的实现为准。

## 5.5 EHS 工作台（测试环境）

- 测试环境通过 Spring profile `test` 启用 `ehs.enabled=true`；主配置读取 `EHS_ENABLED` 且默认值为 `false`，因此生产环境不会自动注册 EHS 接口。
- 前端使用 `isTestEnvironment()` 注册 `/ehs/**` 路由和菜单；后端 EHS Controller 使用 `@ConditionalOnProperty(prefix = "ehs", name = "enabled")` 注册接口。
- EHS 使用独立应用编码 `EHS`，由现有平台准入接口分配。`AuthInterceptor` 对 `/api/ehs/**` 强制校验该应用编码，供应商菜单显式隐藏。
- 隐患、环境与危废、PPE 与应急物资、事故事件统一存储在 `ehs_registry_record`，以 `module_type` 区分业务台账；页面提供真实 CRUD、统计和 Excel 导入导出。
- 作业许可总票和六类专项票存储在 `ehs_permit`。申请人通过 `approval_route_id` 选择作业所属部门，`EhsPermitService` 使用 `ProcurementApprovalRouteResolver` 读取部门管理中的有效节点、候选人和 `ANY_ONE`/`ALL` 通过规则，再复用 `approval_instance`、`approval_task`、`approval_record` 和 `ApprovalTaskService` 创建统一待办，不建立 EHS 独立审批人配置或审批状态机。
- 所有专项票审批通过后总票才可开工；执行期受限空间票可记录气体采样；关单顺序为专项票先关闭、总票后关闭。
- 飞书通知复用现有 `FeishuApprovalCardService` 和平台飞书配置，发送结果写入 `ehs_notification_log`，通知失败不回滚业务事务。
- 外部页面路由为 `/ehs-public/apply`、`/ehs-public/permit`、`/ehs-public/review`，仅测试前端注册。对应 `/api/public/ehs/**` 不走平台登录拦截，但后端仍受 `ehs.public-enabled` 控制；主配置默认 `false`，`application-test.yml` 设置为 `true`。
- 测试 profile 为免登录审批通知提供默认平台地址 `http://192.168.132.151:18080`，可用 `FEISHU_APPROVAL_CARD_APP_BASE_URL` 覆盖；生产 profile 不提供默认外部地址。
- 外部申请人和审批人使用不同类型的随机访问令牌。数据库 `ehs_public_access_token` 只保存 SHA-256 哈希，申请人令牌有效期 180 天，审批人令牌有效期 7 天且审批后撤销；原始令牌放在 URL fragment 中，避免随常规 HTTP 请求和 Referer 发送。外部审批令牌绑定 `sys_user`，审批时仍以该用户身份写入共享审批任务和审批记录。
- 公共接口只返回外部办理所需字段，不返回附件、签名、内部用户标识或部门审批人名单；写接口有应用层限流。正式启用公网访问时还必须在网关增加分布式限流和访问日志脱敏。
- 智能助手的会话、消息和知识文档持久化到 EHS 表，回答只读取实时台账和知识库，不直接执行审批、开工或关单动作。
- EHS-only 账号可以进入 `/workbench`，但 `WorkbenchServiceImpl` 仍只在具备 `PROCUREMENT` 或 `PLATFORM` 准入时装载对应采购/平台数据，来源详情还会再次执行应用校验。

## 5.6 平台管理模块

平台管理当前主要包括：

- 我的权限页
- 平台权限管理
- 用户管理
- 平台集成设置
- 采购规则设置
- 资产目录管理
- 数据库管理
- 意见反馈

主要页面：

- `frontend-vue/src/pages/platform/PlatformAccess.vue`
- `frontend-vue/src/pages/platform/PlatformPermissionAdmin.vue`
- `frontend-vue/src/pages/system/UserManagement.vue`
- `frontend-vue/src/pages/platform/ProcurementSettings.vue`
- `frontend-vue/src/pages/platform/AssetCatalogManagement.vue`
- `frontend-vue/src/pages/platform/DatabaseManagement.vue`
- `frontend-vue/src/pages/platform/PlatformFeedbackPage.vue`

`ProcurementSettings.vue` 由路由 `props.viewMode` 复用为两个后台页：`/platform/integration-settings` 只展示 Dify、飞书、邮件、系统版本升级、导入文件保留周期和卡片日志等平台集成配置；`/platform/procurement-rule-settings` 只展示采购规则和部门采购上限，不再提供流程测试按钮。历史 `/platform/procurement-settings` 仅作兼容跳转，不要再把新的平台能力挂回旧菜单名。

资产目录管理接口位于 `/api/admin/platform/asset-catalog/**`，按平台管理应用准入且只允许 `SUPER_ADMIN` 操作。管理页支持逐项新增、编辑、启停和 Excel 批量维护；运行时 `AssetCatalogService` 每次读取数据库中的启用目录，管理员变更不需要重启服务。目录停用不得删除或回写历史报价的资产判定。

数据库管理接口位于 `backend-springboot/src/main/java/com/company/procurement/modules/platform/database`，统一挂载在 `/api/admin/database/**`。该模块只允许 `SUPER_ADMIN` 访问，并按 `database_admin:view/create/update/delete` 校验具体操作权限；表名和字段名必须先从当前库 `information_schema` 校验后再拼接 SQL，不提供任意 SQL 执行入口。权限初始化脚本为 `backend-springboot/sql/zz_zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz_database_admin_permissions.sql`。

意见反馈接口位于 `backend-springboot/src/main/java/com/company/procurement/modules/platform/feedback`。全员提交接口为 `POST /api/platform/feedbacks`，管理员接口统一挂载在 `/api/admin/platform/feedbacks/**`；文字、链接和提交页面写入 `platform_feedback`，图片附件元数据写入 `platform_feedback_attachment`，文件内容按 `platform.feedback.storage-dir` 保存到本地持久化目录。该功能只做问题收集和后台处理，不接入采购、审批、报价或月度需求主流程。

## 6. 数据库说明

## 6.1 初始化脚本

`backend-springboot/sql/schema.sql` 文件开头会删除已有业务表，只允许在独立、可丢弃的全新本地数据库中执行。生产库、共享测试库和任何需要保留数据的库都不能执行它。

全新本地库的基础执行顺序为：

- `backend-springboot/sql/schema.sql`
- `backend-springboot/sql/seed.sql`
- 按功能需要评审后执行 `backend-springboot/sql/seed_identity.sql`
- 按文件名顺序执行当前基线需要的 `backend-springboot/sql/zz_*.sql`

部分 `zz_*.sql` 同时承担生产数据修复，仓库目前没有一套经过验证的“一键本地 bootstrap”。新开发者应优先使用维护者提供的开发库快照，或让维护者确认本次所需的增量脚本；不要把 SQL 根目录整体当作可无条件执行的初始化包。

`seed.sql` 和 `seed_identity.sql` 中的初始化账号使用不可登录的禁用密码值，不能把它们当作可直接使用的开发账号。共享开发库应由维护者通过安全方式创建测试账号；不得为了方便在种子 SQL 中加入通用明文密码。

当前仓库没有启用 Flyway。`src/main/resources/db/migration/V*.sql` 不会随 Spring Boot 启动自动执行，不能把应用启动成功等同于数据库已迁移到当前基线。

## 6.2 增量脚本规则

增量脚本统一放在 `backend-springboot/sql/zz_*.sql`。

脚本清单以目录中的实际文件为准，不在本文维护容易过期的手工清单。生产发布脚本 `deploy/deploy_current.py` 会按文件名顺序执行所有 `zz_*.sql`，任一脚本失败即停止发布。`manual_*.sql`、备份脚本和回滚脚本不是常规自动迁移入口。

规则：

- 只追加新的增量脚本，不要改历史脚本逻辑来兼容线上
- 脚本必须可重复执行，至少做到幂等或近似幂等
- 结构性变更优先用 `ALTER TABLE IF EXISTS`、`ADD COLUMN IF NOT EXISTS` 这类安全写法
- 如果变更依赖数据修复，脚本里要写清楚修复范围
- 回滚脚本必须以备份表为准：`SELECT … FROM sys_user JOIN <backup_table>` 后再 `UPDATE`，确保只恢复确实被新脚本改过的账号

当前 `deploy/docker-compose.prod.yml` 会把整个 `backend-springboot/sql` 目录挂到 MySQL 初始化目录，因此不应把它当作全新本地数据库的一键初始化工具：目录中还包含手工审计脚本、种子脚本和生产数据修复脚本，执行集合与顺序并不等同于受控的本地 bootstrap。生产既有数据卷发布继续走 `deploy_current.py`；全新本地库应显式执行已评审的脚本序列。

## 6.3 历史记录：部门字典四级层级（sys_department）

> 本节保留 2026-06-29 的一次性数据迁移背景。下文提到的部分备份/回滚脚本已不在当前仓库中，不能作为今天的数据库初始化命令；实际结构与脚本始终以当前代码和 `backend-springboot/sql` 目录为准。

### 6.3.1 设计目标

把现有 `sys_user.dept_code / dept_name` 的扁平字符串统一升级成四级标准字典，便于：

- 部门下拉、审批楼栋绑定、供应商楼栋推荐统一引用 `sys_department.code`
- 后续按 L1-L4 做汇总统计、跨园区报表、AI 助手识别
- 飞书组织同步、采购申请部门选择、工作台筛选等场景不再依赖历史脏字符串

### 6.3.2 节点层级

| 层级 | `dept_type` | 说明 | 编码前缀 | id 段 |
| ---- | ----------- | ---- | -------- | ----- |
| L1 总部 | `HEADQUARTER` | 世纪互联总部 | `HQ-21VIANET` | `1` |
| L2 区域 | `REGION` | 华北一/二/三、华东一/二、华南、IT中心、云产线、产品销售部、美团、香山、IT中心-亚康 | `R-…` | `100-115` |
| L3 园区 | `PARK` | 酒仙桥、腾仁、太仓一期/二期/三期/四期、伊蒂雅、乌兰等 | `P-…` | `200-298` |
| L4 楼栋 | `BUILDING` | 楼栋 / 机房单体（包含网银、M6V&M5II&M5III 等特殊名） | `B-…` | `300-498` |

`sys_department` 字段：`id, code, full_name, short_name, dept_type, parent_id, sort_order, is_active, remark, created_at, updated_at, created_by, updated_by, is_deleted, version`。索引：`uk_sys_dept_code`、`idx_sys_dept_parent`、`idx_sys_dept_type`、`idx_sys_dept_active`、`idx_sys_dept_deleted`。

### 6.3.3 当时节点规模（v20260629.5 基线）

- HEADQUARTER：`1`（世纪互联）
- REGION：`12`（华北一/二/三、华东一/二、华南、IT中心、云产线、产品销售部、美团、香山、IT中心-亚康）
- PARK：`78`（覆盖全部已识别园区）
- BUILDING：`123`（覆盖全部已识别楼栋；酒仙桥下含 M5/M3/B28/M6/M6V&M5II&M5III/网银/中化雄安/百度M1）
- 合计：`214` 条

### 6.3.4 字典与 sys_user 回填关系

| 楼栋编码 | `dept_name` 关键字 | 落点 |
| -------- | ------------------ | ---- |
| `B-JXQ-M5` | `M5` | 16 人 |
| `B-JXQ-M3` | `M3` | 18 人 |
| `B-JXQ-B28` | `B28` | 24 人 |
| `B-JXQ-M6` | `M6` 或以 `[N420N00077]` 开头的完整全名 | 27 人（含 1 条全名解析） |
| `B-JXQ-M6V-M5` | `M6V&M5II`、`M6V&M5III`、`M6V&M5II&M5III` | 40 人 |
| `B-JXQ-WANG-YIN` | `网银` | 2 人（user_id 149 / 175） |

当时的回填执行记录：

1. `zz_20260629_sys_user_deptname_backup.sql` 创建 `sys_user_deptname_bak_20260629`，只备份 `is_deleted = 0` 的账号
2. `zz_20260629_sys_department.sql` 创建字典并按上述规则 `UPDATE sys_user`
3. 验证：网银 2 人、酒仙桥全部 127 人、`dept_name` 为空的 4 人（user_id 102/103/104/113）保持原样
4. 异常时执行 `zz_20260629_sys_user_deptname_rollback.sql` 按备份表恢复 `dept_code / dept_name / region_id`

人员新增、编辑和批量导入的部门字段纠正必须以审批路由为主：先匹配 `procurement_approval_route`，再匹配 `org_scope`，最后才使用 `sys_department` 做辅助识别；即使通过 `sys_department.code` 命中，也要尽量反查审批路由并写回路由的 `dept_code/dept_name`。现有数据修复脚本 `zz_20260629_user_department_route_autofix.sql` 只更新能唯一落到审批路由的账号，模糊命中或多条同优先级命中时保留原值，避免把用户归到错误审批楼栋。

### 6.3.5 后续扩展注意事项

- 不删除/合并 `sys_department` 已有节点；新增园区或楼栋一律按 `parent_id + sort_order` 追加
- 不要把 `procurement_approval_route`、`org_scope`、`procurement_approval_policy` 等审批路由主数据混入本表，部门字典只承担“部门/楼栋标准编码 + 父子关系”
- 前端部门下拉、审批楼栋绑定、供应商楼栋推荐、AI 助手楼栋识别等场景的字典读取应逐步切到 `sys_department`，避免继续依赖 `sys_user.dept_name` 字符串
- 回滚脚本只恢复 `dept_code / dept_name / region_id` 三个字段；如新增字段（例如 `sys_user.department_id`），回滚脚本需同步追加



## 7. 本地开发

## 7.1 环境要求

- Node.js `^20.19.0 || >=22.12.0`（来自当前 Vite 8 锁文件）
- npm 10+
- JDK 17
- Maven 3.9+
- MySQL 8

仓库没有 Maven Wrapper，需要本机安装 Maven。Redis、Qdrant、飞书、Dify 和其他外部集成不是普通本地启动的前置条件；只有开发对应功能时才按配置启用。

生产 Compose 没有把 MySQL、Redis、Qdrant 或后端端口发布到宿主机，因此当前不支持“Compose 起依赖、宿主机起后端”的开箱即用混合模式。

## 7.2 初始化数据库

先按[数据库说明](#6-数据库说明)准备独立开发库。后端默认读取以下环境变量：

```sql
CREATE DATABASE procurement_platform
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`
- `JWT_SECRET`

本地值只放当前终端、IDE 私有运行配置或未跟踪的本机环境文件，不要复制生产 `.env`。PowerShell 示例：

```powershell
$env:DB_URL = "jdbc:mysql://localhost:3306/procurement_platform?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
$env:DB_USERNAME = "<本地数据库用户>"
$env:DB_PASSWORD = "<本地数据库密码>"
$env:JWT_SECRET = "<至少 32 字节的本地随机值>"
```

## 7.3 启动后端

```powershell
cd backend-springboot
mvn.cmd spring-boot:run
```

macOS/Linux 使用同名的 `mvn` 命令。启动后可检查：

- `http://localhost:8080/health`：静态存活响应，只证明 Web 应用已启动，不检查 MySQL、Redis 或外部服务。
- `http://localhost:8080/swagger-ui/index.html`：开发环境 Swagger UI。
- `http://localhost:8080/v3/api-docs`：OpenAPI JSON。

生产 profile 默认关闭 OpenAPI；若临时开启，需注意当前 Swagger 路径在鉴权白名单中，不应暴露到不受信任网络。

## 7.4 启动前端

```powershell
cd frontend-vue
npm.cmd ci
npm.cmd run dev
```

首次检出或锁文件未变化时使用 `npm ci`；只有明确修改依赖时才使用 `npm install` 并提交同步变化的 `package-lock.json`。

Vite 固定监听 `http://localhost:5173`，Axios 以 `/api` 为基础路径，并由 Vite 代理到 `http://localhost:8080`。当前没有环境变量用于改写本地 API 目标。

## 7.5 登录账号与前端认证 mock

完整业务联调必须使用后端认可的本地测试账号。种子账号默认不可密码登录，应由维护者在独立开发环境中创建或启用账号，不要复用生产账号或生产凭据。

仅调试登录态和界面时，可在启动 Vite 的同一个 PowerShell 进程中设置：

```powershell
$env:VITE_AUTH_MOCK = "true"
$env:VITE_AUTH_MOCK_USERNAME = "<本地 mock 用户名>"
$env:VITE_AUTH_MOCK_PASSWORD = "<本地 mock 密码>"
npm.cmd run dev
```

当前 `vite.config.ts` 直接读取进程环境变量，没有调用 `loadEnv`，所以只写 `.env.local` 不能可靠启用此 mock。mock 只覆盖认证接口和少量历史接口；其他 `/api` 请求仍会转发到 `8080` 后端，不能把它当作完整业务后端。采购看板在开发环境接口失败时还可能回退本地样例数据，页面出现数据不代表后端接口已经联通。

## 7.6 本地构建检查

后端测试与完整打包：

```powershell
cd backend-springboot
mvn.cmd test
mvn.cmd clean package
```

前端类型、准入和生产构建检查：

```powershell
cd frontend-vue
npm.cmd run typecheck
npm.cmd run check:access
npm.cmd run build
```

仓库根目录再执行：

```powershell
git diff --check
```

说明：

- `npm run build` 已包含类型检查，但不包含 `check:access`。
- 新增构造注入或修改 Service 构造参数时，要同步更新手工实例化 Service 的单元测试，不能只跑 `mvn compile`。
- 修改前端依赖后执行与 CI 一致的 `npm audit --audit-level=moderate`。
- 当前后端测试以 JUnit/Mockito 单元测试为主，不代表数据库和 HTTP 全链路已回归；前端目前没有自动化测试和 lint 脚本，受影响页面仍需手工联调。

## 8. 飞书登录配置

飞书登录不是写死在代码里的，生产配置全部走环境变量。

关键变量：

- `FEISHU_AUTH_ENABLED`
- `FEISHU_APP_ID`
- `FEISHU_APP_SECRET`
- `FEISHU_REDIRECT_URI`

说明：

- 代码位置：`backend-springboot/src/main/resources/application-prod.yml`
- Compose 注入位置：`deploy/docker-compose.prod.yml`
- 生产实际值：`deploy/.env`

回调地址规则：

- 前端登录页回调地址固定构造为 `window.location.origin + /login`
- 因此飞书开放平台的重定向白名单必须包含线上登录页地址

当前代码里的飞书登录生成与回调处理：

- `AuthController` 提供 `/api/auth/feishu/login-url` 和 `/api/auth/feishu/callback`
- `LoginPage.vue` 中 `buildRedirectUri()` 返回 `/login`
- `AuthServiceImpl` 校验 `state` 并按邮箱 / 手机号 / 飞书身份字段匹配本地用户

## 8.1 飞书供应商外部会话

供应商飞书外部群或外部单聊只能作为“通知入口”和“材料入口”，不能另起一套采购流程。

当前实现：

- 供应商在机器人会话发送 `绑定 供应商编码`，写入 `supplier_feishu_binding`
- 绑定命令兼容 `绑定 SUP0001`、`供应商绑定 SUP0001`、`你好我要绑定 SUP0001` 和 `bind SUP0001`；如果消息只有“我要绑定”等绑定意图但没有供应商编码，机器人必须返回绑定引导，而不是进入内部员工账号匹配后提示“账号未绑定系统用户”
- 系统扫描 `purchase_request.status = QUOTING` 的现有采购申请，按原供应商报价可见范围筛选供应商
- 对已绑定供应商发送报价任务卡片，写入 `supplier_feishu_quote_notice` 防重复
- 如果后台开启 `feishu_admin_card_copy_enabled`，同一个供应商报价任务会按 `feishu_admin_card_copy_usernames` 抄送给管理员；管理员收到的是入口卡片，不代表替供应商报价。管理员抄送不需要供应商已经绑定飞书外部会话，避免供应商未绑定时管理员也看不到报价任务。
- `supplier_feishu_quote_notice.binding_id` 不是可空去重字段：真实供应商卡片写绑定 ID，管理员抄送写 `0`，供应商未绑定时的管理员兜底写 `-1`。同一申请、同一供应商、同一管理员邮箱在抄送和兜底两个场景下必须允许各生成一条日志和一张卡片。
- 报价任务通知记录在 `supplier_feishu_quote_notice`，后台“平台集成设置 / 供应商报价卡片日志”可查看接收目标、状态、失败原因并手动重发；审批卡片日志 `feishu_approval_card_notification` 只记录正式审批卡片，不要用它判断供应商报价卡片是否发送。
- 卡片只跳转到 `/procurement/quotes?reqNo=...`，实际报价、导入报价、比价、供应商确认中标仍走 `SupplierQuoteServiceImpl` 的原节点和校验

禁止事项：

- 不要为了飞书外部供应商另建独立采购申请、独立报价库或独立审批状态
- 不要绕过 `PurchaseRequest`、`SupplierQuote`、`SupplierQuoteItem` 和现有 `WorkflowService` 节点
- 如需把供应商上传的 Excel、发票或报价回复接入机器人，必须解析后写回原报价、订单或发票模块，并在设计前确认对应原始节点

## 9. 部署说明

## 9.1 生产部署方式

使用：

- `deploy/docker-compose.prod.yml`
- `deploy/.env`
- `deploy/deploy_current.py`

当前 Compose 只把前端 Nginx 的 `80` 端口发布到宿主机；浏览器 API 请求由 Nginx 转发给 Compose 内网的后端，MySQL、Redis、Qdrant 和后端端口均不直接对外发布。

`deploy_current.py` 的职责：

- 打包并上传项目文件
- 复用或补充远端 `deploy/.env`
- 执行 `docker compose up -d --build`
- 生产环境必须优先使用 Docker Compose v2；旧版 Python 版 `docker-compose` v1 已在服务器删除
- 远端保留 `/usr/local/bin/docker-compose` 兼容脚本，仅用于把历史命令转发到 `docker compose`
- 若遇到旧版 `docker-compose` 的 `ContainerConfig` 异常，说明仍有环境在走 v1 或历史容器元数据，先升级到 Compose v2，再 `down` 后重启，不得删除数据卷
- 容器启动后自动执行 `zz_*.sql`
- 最后做首页和登录接口检查

发布规则固定为：PR 自动进入 151 测试环境；合并 `main` 本身不发布生产；生产只能手工运行 `Deploy Production (Manual)` 并输入确认词。测试发布使用 `deploy/deploy_test_runner.sh`，保留现有测试数据和首次修改后的测试管理员密码，不会自动删除测试数据卷。

## 9.2 环境变量管理规则

- `.env.example` 只保留模板，不放真实密钥
- `.env` 用于真实部署
- 真实 `App Secret`、数据库密码、JWT Secret 不要写入代码
- 发布脚本和状态检查脚本只能从环境变量或 GitHub Secrets 读取 SSH/sudo 凭据，不能写死服务器地址、端口、账号或密码
- 一旦真实密钥进入 Git 历史，必须轮换对应密钥；删除文件只能修复当前树，不能让历史里的旧值重新变安全
- 修改 `.env` 后必须重新部署，不能只改本地文件
- 使用 `/api/integrations/shopping` 购物工具接口时必须配置 `SHOPPING_TOOL_TOKEN`；空值会被视为未授权，不能作为匿名接口使用

## 9.3 发布前检查

每次发布前至少确认：

- `frontend-vue` 构建通过，包含 `vue-tsc --noEmit` 类型检查
- `backend-springboot` 执行 `mvn test` 通过
- 前端依赖变更后执行与 CI 一致的 `npm audit --audit-level=moderate`
- `deploy/.env` 参数完整
- 如果启用了购物工具集成，`deploy/.env` 必须包含非空 `SHOPPING_TOOL_TOKEN`
- 如果涉及飞书登录，先验证新 `App ID/Secret` 能换取 `app_access_token`
- 如果涉及数据库结构，已新增对应 `zz_*.sql`

## 9.4 发布后回归

发布后至少验证：

- 首页访问正常
- `POST /api/auth/login` 正常
- `GET /api/auth/me` 正常
- 如果开启飞书登录，`GET /api/auth/feishu/login-url` 返回 `success=true`
- 受影响的业务页面能正常进入

## 10. 常改动点

后续开发最常碰到的入口如下：

- 路由和准入：`frontend-vue/src/router/index.ts`
- 登录态：`frontend-vue/src/stores/auth.ts`
- 用户管理：`frontend-vue/src/pages/system/UserManagement.vue`
- 供应商报价：`frontend-vue/src/pages/supplier/QuoteList.vue`
- 采购页面：`frontend-vue/src/pages/procurementVnet/*`
- 维保页面：`frontend-vue/src/pages/maintenance/*`
- 平台权限：`backend-springboot/src/main/java/com/company/procurement/modules/platform/*`
- 登录服务：`backend-springboot/src/main/java/com/company/procurement/modules/auth/*`
- SQL 增量：`backend-springboot/sql/zz_*.sql`
- 发布脚本：`deploy/deploy_current.py`

## 11. 文档维护约定

后续任何人修改以下内容时，必须同步更新本文档：

- 新增或删除业务模块
- 新增或修改主路由
- 新增或修改登录方式、鉴权方式、权限模型
- 新增环境变量、改动部署拓扑、改动发布脚本
- 新增数据库初始化脚本或增量脚本
- 重要上线结论导致开发方式发生变化

建议更新方式：

1. 先改代码
2. 再更新本文档对应章节
3. 如果影响使用或部署，再同步更新 `README.md` 或 `docs/DEPLOYMENT_MANUAL.md`

## 12. 待实施业务方案：税票、无税比价与楼栋推荐供应商

这部分会影响报价字段、比价口径和审批链路，后续实现时必须复用现有供应商报价、报价审核、中标确认和采购申请流程，不得新建旁路采购流程。

### 12.1 供应商报价税票字段

- 报价阶段需要在供应商报价页面、飞书询价单模板和报价导入逻辑中新增“发票类型”和“税率”。
- 发票类型建议固定为：`增值税专用发票`、`增值税普通发票`。税率按百分比录入，例如 `13`、`9`、`6`、`3`、`1`、`0`。
- 供应商录入的单价继续按“含税单价”处理，系统额外计算并落库：不含税单价、税额、可抵扣税额、比价成本价。
- 专票可以抵扣，推荐比价成本价为：`含税金额 / (1 + 税率)`。
- 普票不能抵扣，推荐比价成本价为：`含税金额`；同时可展示估算不含税价，但不用于抵扣成本比较。
- 发票类型或税率缺失时，供应商报价不得提交；税率小于 0 或格式非法也不得提交。

### 12.2 比价和展示口径

- 报价明细页同时展示含税金额、税率、发票类型、不含税金额和比价成本价。
- 自动比价按“比价成本价”排序，不直接按供应商填写的含税金额排序。
- 中标确认、供应商通知卡和管理端报价详情都要显示发票类型、税率和比价成本价，避免审批人只看到含税价而误判。
- 历史报价数据没有发票类型时，应按兼容策略显示“未维护”，不能自动默认为专票。

### 12.3 楼栋推荐供应商

- 楼栋/机房人员推荐供应商只能作为“候选报价来源”，不能直接进入中标或采购订单。
- 推荐供应商需先走前置审核：机房经理审核，再由区域经理审核；审核通过后才能参与对应采购明细或采购申请的报价邀请。
- 推荐通过的供应商应写入现有供应商资料或形成临时受控供应商记录，并绑定到现有 `supplier`/报价邀请体系，后续报价、比价、中标确认仍走原供应商报价流程。
- 如果楼栋推荐供应商的比价成本价低于集采供应商，可以在现有报价审核页面作为候选中标项展示，由报价审核人按原规则确认；不得绕过报价审核自动改中标。
- 审批和日志要保留推荐来源、推荐人、机房经理审核意见、区域经理审核意见，便于后续追溯。

## 13. 当前基线说明

截至 2026-07-20，当前开发基线是：

- 前端真实实现是 Vue 3 + TypeScript + Vite 8 + Element Plus，不是 Vue 2 + VNET UI。
- 平台统一承载工作台、维保、采购、平台管理和灾害预警等能力；测试环境额外启用 EHS 工作台预览，正式页面以当前路由、环境开关与应用准入配置为准。
- 本地热开发使用 `5173` 前端代理 `8080` 后端；MySQL 8 是基础依赖，其他集成按功能启用。
- 飞书、Dify、数据库和 JWT 等配置通过环境变量或后台受控配置提供，真实凭据不进入仓库。
- 生产发布依赖 `deploy/deploy_current.py` 和 Docker Compose 五服务栈，只有前端 Nginx 的 `80` 端口对外发布。
- 数据库增量由 `deploy_current.py` 执行 `zz_*.sql`；当前没有启用 Flyway，也没有经过验证的一键式全新本地数据库 bootstrap。
