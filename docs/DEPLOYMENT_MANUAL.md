# 部署手册（明日上服务器可直接执行）

## 1. 部署方式

推荐使用 Docker Compose 生产部署。
当前生产编排包含 MySQL、Redis、Qdrant、后端 API 和前端 Nginx。Redis 与 Qdrant 仅在 Compose 内网暴露给后端，不需要对公网开放端口。

目录：

- 编排文件：`deploy/docker-compose.prod.yml`
- 环境变量模板：`deploy/.env.example`

## 2. 服务器前置条件

- Linux 服务器（推荐 Ubuntu 22.04+）
- Docker 24+
- Docker Compose v2（推荐使用 `docker compose` 子命令）
- 开放端口：
  - `80`（前端）
  - `8080`（后端，可选对外）
  - `3306`（数据库，建议仅内网）

## 3. 部署前准备

## 3.1 上传项目代码

将整个 `enterprise-procurement-platform` 上传到服务器，例如：

`/opt/procurement/enterprise-procurement-platform`

## 3.2 配置环境变量

```bash
cd /opt/procurement/enterprise-procurement-platform/deploy
cp .env.example .env
vim .env
```

至少修改：

- `MYSQL_ROOT_PASSWORD`
- `JWT_SECRET`（必须 32 字节以上）

按需修改：

- `REDIS_PASSWORD`：为空时 Redis 不启用密码；生产环境建议设置。
- `QDRANT_ENABLED`、`QDRANT_URL`、`QDRANT_COLLECTION_PREFIX`：默认连接 Compose 内的 `http://qdrant:6333`。
- `SHOPPING_TOOL_TOKEN`：启用 `/api/integrations/shopping` 购物工具接口时必须设置，空值会被后端拒绝。
- `PLATFORM_FEEDBACK_STORAGE_DIR`：意见反馈图片附件保存目录，Docker 生产默认 `/app/data/feedback`，由 `deploy/docker-compose.prod.yml` 挂载到宿主机项目目录的 `data/feedback`。
- 订单发票和送货单附件在 Compose 中固定保存到容器 `/app/data/order-invoices`，生产挂载到宿主机 `data/order-invoices`，测试使用独立数据卷。非 Compose 部署可用 `ORDER_INVOICE_STORAGE_DIR` 覆盖，但必须同时配置持久化、备份和恢复路径。

发票和送货单均允许每次上传 1～5 个文件，单文件上限 20MB，整次请求上限 110MB。若服务器前面还有 SLB 或额外 Nginx，需要同步确认该请求上限，避免上传在到达后端前被拒绝。

`deploy/.env` 只保存在服务器或 CI Secret 中，不提交到 Git。`.env.example` 只保留空模板，缺少真实值时部署应当失败，不能用示例值顶替生产密钥。

## 3.3 检查 SQL

已提供：

- `backend-springboot/sql/schema.sql`
- `backend-springboot/sql/seed.sql`

`docker-compose.prod.yml` 已自动挂载该目录到 MySQL 初始化目录，首次启动会自动导入。

## 4. 一键部署

```bash
cd /opt/procurement/enterprise-procurement-platform/deploy
docker compose -f docker-compose.prod.yml --env-file .env up -d --build
docker compose -f docker-compose.prod.yml ps
```

当前生产服务器已安装 `docker-compose-v2`，旧版 Python 版 `docker-compose` v1 已删除。服务器保留 `/usr/local/bin/docker-compose` 兼容脚本，仅把历史 `docker-compose ...` 命令转发到 `docker compose ...`，避免旧脚本因命令名变化失败；新脚本和人工排障统一使用 `docker compose`。

成功后访问：

- 前端：`http://<服务器IP>/`
- 后端健康：`http://<服务器IP>:8080/`

## 4.1 Gitee 测试与 GitHub 生产部署

仓库提供互相隔离的测试与生产发布链路：

- `.workflow/gitee-test-deploy.yml`：Gitee `main` 更新后，由 `procurement-test-151` 主机组在 151 拉取精确提交、本机构建镜像并部署独立测试环境。该链路使用只读部署公钥，不保存 Gitee 私人令牌。
- `.github/workflows/deploy-test.yml`：保留 `workflow_dispatch` 给上线管理创建的发布批次使用。GitHub 托管构建节点先生成并推送精确 GHCR digest，再尽力把同一候选镜像打包并按“测试运行编号 + 重跑次数 + 候选 SHA”的精确 Actions Cache key 保存；保存与恢复固定使用相同的工作区相对路径，不能使用依赖 Runner 工作区深度的 `/tmp` 绝对路径。151 只拥有 `actions:read`，只接受同一精确 key 的命中，并通过统一校验器交叉核对候选提交、归档 SHA-256、镜像 digest，以及 Docker 归档中两个标签与构建 config ID 的映射；config 成员必须精确位于 legacy Docker 的 `<config>.json` 或 containerd 镜像存储的 `blobs/sha256/<config>`，预加载成功后仍由 Docker 登记和部署原始 digest。归档打包、缓存保存、恢复、校验或加载失败时会清理可能部分加载的候选标签并继续使用原 GHCR 路径。测试失败时，独立 GitHub 托管任务按缓存 ID 删除该批次缓存；测试成功时缓存保留给生产，不再额外上传 Actions Artifact。
- `.github/workflows/deploy.yml`：只接受上线管理生成的批次 ID、已测试 `main` SHA、测试运行编号、前后端镜像 digest 和 HMAC 签名；生产先核对测试运行确由受信工作流成功完成并解析准确重跑次数，再用同一精确 key 恢复候选 Cache。缓存通过与测试一致的统一校验器后才能加载，随后仍必须登记并复核原始 GHCR 不可变 digest。缓存不可用或被拒绝时自动回退 GHCR；生产失败保留缓存供同批次重试，生产成功后由 GitHub 托管任务按缓存 ID 删除。推送或合并 `main` 不会自动发布生产。

### 测试环境开发者中心

“开发者中心”及其机器可读资料属于测试环境能力。测试前端通过 `X-Environment: test` 识别环境并注册 `/developer-center` 菜单和路由；测试 Nginx 提供 `/llms.txt` 与 `/developer/*`，并代理 `/v3/api-docs` 和 `/swagger-ui/`。生产前端不注册开发者中心；生产 Nginx 对 `/developer-center`、`/llms.txt`、`/developer/*`、`/v3/api-docs`、`/swagger-ui/*` 以及开发者中心专用前端资源返回 404，生产 OpenAPI 保持禁用。

合并或同步测试部署前，在 `frontend-vue` 执行：

```powershell
npm.cmd run check:developer-center
```

专项检查必须确认六个 `/developer` 资源存在、JSON 可解析、`llms.txt` 索引没有失效链接、页面具备搜索及 TEST/OpenAPI/llms 入口，并且菜单和路由仍受测试环境开关控制。部署后再以当前测试站点为基准验证 `/developer-center`、`/llms.txt`、六个资源文件、`/v3/api-docs` 和 Swagger；不得把生产环境临时开启作为验收方式。

### 测试环境上线前门禁

测试环境用于验证已完成基本测试的候选版本，不得把“部署到测试环境”当作第一次测试。由于 Gitee 会自动部署 `main`，以下检查必须在合并 PR、同步 Gitee `main` 之前完成：

- 后端至少执行 `mvn.cmd -q -DskipTests compile`，前端执行 `npm.cmd run build`。
- 按变更范围运行相关测试：后端业务逻辑运行对应单元/集成测试，前端交互运行现有相关测试，部署脚本运行语法或专项测试。不能只用编译成功代替功能测试。
- 执行 `git diff --check` 和敏感信息扫描，并确认 PR 的 `Build pull request without deployment` 检查通过。
- 任一检查失败时禁止合并、禁止同步 Gitee、禁止部署。部署后的首页、OpenAPI、角色权限和改动功能验证属于第二阶段冒烟检查，不能替代上线前测试。

Gitee 与 GitHub 测试部署共用主机锁，避免同时修改 `/opt/procurement-test`。Gitee 本地镜像状态单独写入 `current-gitee-build-ref`；它会使旧的发布批次测试标记失效，不能作为生产镜像。生产设备、生产数据库、150 Runner 和生产 workflow 均不由 Gitee 流水线访问或修改。

Pull 镜像同步不保证产生 Gitee push 触发事件，因此测试主机同时安装 `deploy/install_gitee_test_deploy_cron.sh` 提供的每 5 分钟轮询任务。该任务直接使用只读部署公钥读取 Gitee `main`，不访问 GitHub；Gitee Go 的 push 流水线仍可用于镜像更新后的低延迟触发，两条 Gitee 入口由 bootstrap 锁互斥。只有 Gitee `main` SHA、当前 Gitee 状态或测试站健康发生变化时才构建部署，无变化时完成只读检查后立即退出。

在 151 上以 `ceshi` 执行以下命令可幂等安装或修复定时任务：

```bash
bash /opt/procurement-test/deploy/install_gitee_test_deploy_cron.sh
```

定时任务日志写入 `~/.local/state/procurement-test-gitee/scheduler.log`，当前成功部署的 Gitee SHA 写入 `~/.local/state/procurement-test-gitee/current-sha`。GitHub 的 `.github/workflows/deploy-test.yml` 只有手工 `workflow_dispatch`，不会因 `main` 更新自动部署测试环境。

当前私有仓库套餐不提供 Environment 审批能力，因此发布凭据不存入 GitHub Environment。151 控制面使用只安装到本仓库的 GitHub App 并动态生成短期 token；测试和生产 Runner 通过 job-start hook 只接收固定仓库、固定 `main` workflow。生产 HMAC 仅在 151 控制面与 150 本地 `0600` 文件各保存一份。

旧“系统版本升级”GitHub Token 配置已停用并由增量 SQL 清理；“平台管理 / 系统更新”只展示版本记录。生产发布统一从“平台管理 / 上线管理”创建批次，测试通过后由上线管理员人工确认，不再允许业务后端持有个人 GitHub Token 直接触发生产部署。

151 的上线控制面由 `.github/workflows/deploy-release-control.yml` 独立部署。GitHub 托管 Runner 负责检出精确的 `main` SHA 并生成带 SHA-256 清单的源码包，151 通过绑定当前运行号和提交号的 Actions Cache 恢复、复核并解包，不要求服务器能够直连 `github.com` 完成 Git 检出。测试站使用 HTTPS 时，页面默认访问 `https://procurement-test-151.tailb89388.ts.net:18081/`；Tailscale Serve 仅在 18081 终止 TLS，再反向代理到 `http://192.168.132.151:18081`。部署 workflow 会在修改前后读取 Serve JSON 配置，除 18081 外必须完全一致，因此既有 443 Funnel 和 18080 测试站转发不得被覆盖；18081 也不得开启 Funnel。release-control 的 CSP 只允许列入 `RELEASE_CONTROL_FRAME_ANCESTORS` 的测试站 origin 内嵌，默认包含上述 HTTPS 测试域名。

生产部署在 150 内网 `self-hosted` runner 本机执行，复用 `deploy/deploy_local_runner.sh`，流程包括：

- job-start hook 在任务执行前拒绝 PR、push、schedule、非 `main` 或非固定 `deploy.yml` 的任务
- 从 150 本地受限文件读取 HMAC，核验批次 ID、源码 SHA、测试运行编号和两个已测试镜像 digest 的签名
- 要求 `deploy/releases/current.json` 同时保存当前生产 `releaseSha`、`backendImage` 和 `frontendImage`；工作流在下载或拉取候选镜像之前先只读校验记录、可信镜像引用和目标提交祖先关系，正式发布脚本再次复用同一校验，并把镜像引用与实际运行容器的 `Config.Image` 和本地镜像 ID 交叉核验。正常基线使用精确 GHCR digest，首次迁移仅额外兼容与 `releaseSha` 完全一致的前后端 `hotfix-<SHA>` 紧急修复标签，受控发布成功后自动写回精确 digest；旧基线只有 SHA 或使用其他可变标签时会在镜像传输及停应用前拒绝发布，禁止猜测
- 检出与当前 `main` 完全一致的发布 SHA；`test_run_id` 用于绑定已成功的测试运行、发布审计、缓存键和 HMAC 签名。生产优先恢复该测试运行保存的唯一候选镜像 Cache，统一校验候选提交、归档哈希、两个精确 digest 和 Docker config ID 后预加载；预加载只是传输加速，生产仍通过 GHCR 登记并复核两个已测试 digest，不能将缓存标签直接作为发布真源。生产机不重新构建候选镜像。缓存未命中或校验失败时继续使用 GHCR；精确镜像自然拉取失败或进程被终止时会在同一任务内最多重试三次，并复用生产机已完成的 Docker 层，未完成的单层不承诺字节级断点续传。后端镜像按 Spring Boot 依赖、加载器和业务代码分层，依赖再拆为六个且每层构建上限为 32 MiB，当前最大分组约 24 MiB；构建输出时间固定，依赖不变时层 digest 可复用。生产任务摘要记录缓存是否成功加载及精确 digest 登记耗时，禁止只凭 Cache restore 成功就宣称加速生效
- 在维护窗口停止 `frontend/backend` 写入端，在同一停写窗口内创建 MySQL 全量逻辑备份和 `data/order-invoices` 附件归档；数据库、附件逐文件清单和发布基线摘要全部校验通过后才允许继续
- 恢复点原子保存到 `backups/releases/<批次ID-UTC时间>/`；候选后端启动前发生增量 SQL、源码同步或预检失败时，自动恢复旧源码、数据库、附件和旧镜像，且不推进 `deploy/releases/current.json`
- 候选后端启动是数据自动回滚截止点：它可能执行定时任务或发送外部通知，之后若镜像、网络、无端口候选前端联调或生产前端切换失败，脚本只停止候选应用并保留迁移后数据与恢复点，禁止自动还原快照；维护者需核对外部副作用后人工决定继续修复或恢复
- 候选前端只加入生产 Compose 网络，不发布宿主机端口；它验证首页与 `/api/auth/me` 通过后才启动对外生产前端。脚本继续核验生产前端运行状态、Compose 身份、精确镜像以及宿主机首页/API，全部通过后最后原子推进 `current.json`
- 生产 job 总预算为 360 分钟，准备阶段超过 90 分钟会在进入维护窗口前拒绝发布；工作流使用 `setsid` 启动独立发布进程组，脚本内部看门狗提供 120 分钟前向阶段预算。正常进入自动恢复或安全停机前会先取消看门狗，避免第 120 分钟误杀恢复任务，并至少保留约 150 分钟 job 窗口；普通失败与看门狗到期恰好并发时保持应用停机并转人工恢复。自动恢复或候选停机自身失败时保持 fail-closed 并保留恢复材料，本机恢复点不能替代异机灾备
- 打包该精确提交并在 runner 本机解包到生产目录
- 同步发布源码时保留生产目录中的 `backups/`，不删除历史备份
- `deploy/deploy_current.py` 只打包 `git ls-files` 中的版本库文件，未跟踪的本地 demo、截图或临时文件不会进入生产包
- 只复用服务器已有的 `deploy/.env`，workflow 不读取或覆盖生产环境文件
- 根据 `deploy/releases/current.json` 从当前生产 SHA 到目标 SHA 执行新增或修改的 `backend-springboot/sql/zz_*.sql`
- Docker Compose 使用精确 digest 重建容器，并检查镜像 ID、首页和登录接口

2026-06-02 已确认 `192.168.132.150:10086` 上的 `procurement-150` runner 服务处于运行状态，最近一次 `Deploy to server` 在 20:54:40 CST 成功完成；生产目录为 `/home/caigou/procurement`。

本次报价倒计时功能依赖增量脚本：

- `backend-springboot/sql/zz_zzzzzzzzzzzzz_quote_fast_countdown.sql`

脚本会为 `purchase_request` 补齐 `quote_countdown_started_at`、`quote_countdown_due_at` 和倒计时索引；当它属于发布基线到目标提交之间的新增或修改脚本时，发布流程会在应用容器启动前执行。

2026-05-29 的驳回消息提醒和报价截止延期功能不新增数据库表或增量 SQL，复用现有 `purchase_request`、审批记录和 `quote_open_days` 采购规则。上线后需要重点验证：

- 右上角“消息”入口能拉取当前发起人的已驳回采购申请，并可跳转重新提交。
- “采购管理 / 供应商报价”列表、详情和逐项报价弹窗展示报价截止提醒。
- 管理员可调用 `POST /api/workflow/purchase-requests/{id}/quote-deadline-extension` 或页面“延期”按钮延长报价截止时间。
- 报价截止后，供应商账号提交报价会被拒绝，未报价供应商按放弃报价处理。

2026-06-22 的全员意见反馈功能依赖增量脚本：

- `backend-springboot/sql/zz_zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz_platform_feedback.sql`

脚本会创建 `platform_feedback` 和 `platform_feedback_attachment`，并初始化 `platform_feedback:manage` 管理权限。生产 Compose 已把反馈图片目录挂载到宿主机 `data/feedback`，上线后需要重点验证：

- 任意已登录账号左上角可以打开“意见反馈”，提交文字、链接和图片。
- 管理员可在“平台管理 / 意见反馈”查看完整反馈内容、提取到的链接、图片附件和处理状态。
- 重建后端容器不会丢失已上传反馈图片。

2026-06-24 的灾害预警功能依赖增量脚本：

- `backend-springboot/sql/zz_zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz_disaster_weather_warning.sql`
- `backend-springboot/sql/zz_20260628_disaster_weather_alert_api_settings.sql`
- `backend-springboot/sql/zz_20260629_disaster_weather_warning_resolve_push.sql`
- `backend-springboot/sql/zz_20260629_disaster_weather_guard_policy_v33.sql`
- `backend-springboot/sql/zz_20260709_disaster_warning_nmc_api.sql`

脚本会创建 `disaster_weather_record` 并初始化 `WEATHER` 分组配置。上线后需要管理员进入“灾害预警 / 预警设置”填写和风天气 Key、普通天气 API 地址、预警 API 模式、预警 API 地址、预警 API Token、正式值班群 `chat_id` 和测试群 `chat_id`；普通天气 API 地址只填写 Host，例如 `https://devapi.qweather.com` 或专属域名，不要填写 `/v7/weather/now`、`location`、`key` 等路径参数。预警 API 默认 `NMC`：调用中央气象台当前预警列表 `/rest/findAlarm`，按当前配置区域筛选北京市朝阳区和北京市级预警；如切回和风新版模式，则调用 `/weatheralert/v1/current/{latitude}/{longitude}` 并使用 `Authorization: Bearer Token`，旧版模式兼容 `/v7/warning/now`。默认区域为北京市朝阳区酒仙桥东路1号M6楼，城市 Code 为 `101010300`，中心经纬度为 `116.50,39.98`，后台保存格式仍是“经度,纬度”，调用和风新版预警 API 时由后端转换成官方路径要求的“纬度/经度”。定时任务为每天 09:00 今日预报、每周一 09:05 本周预报、每 1 分钟预警检查、每小时第 10 分钟清理预警记录；定时任务只推送正式值班群，设置页测试按钮、颜色测试按钮和预警记录页手动发送只推送测试群，测试群未配置时不会回退到正式值班群。预警只在命中“重保天气预警规则”中一级、二级或三级戒备时推送，卡片颜色按预警红/橙/黄/蓝变化；常规观察/无预警测试卡片统一使用绿色。当前制度版默认规则为：一类极端天气（暴雨、暴雪、台风、洪水）红/橙/黄蓝分别对应一级/二级/三级戒备；二类极端天气（雷电、冰雹、寒潮、霜冻、高温）红/橙分别对应二级/三级戒备，二类黄蓝默认不触发重保推送。真实飞书 `WARNING` 卡片以预警状态、重保等级、预警处置和预警接口状态为主，今日天气和湿球温度仅作为辅助字段，不展示未来天气列表；今日/本周天气预报卡片才展示未来天气。湿球温度处置建议由“湿球温度建议规则”和“湿球温度说明”配置生成，默认覆盖 `≤6.5℃`、`6.5℃<T≤12.5℃`、`T>12.5℃`、`18℃-24℃`、`>24℃` 等运行建议，后续可直接在设置页调整。设置页的“测试预警推送”按钮会复用手动触发接口按预警模式拉取数据并尝试推送测试群，返回记录编号和发送状态；颜色测试按钮会用模拟数据发送红/橙/黄/蓝/常规卡片，方便不等真实预警也能验证样式。如测试群未配置，记录会保存为发送失败并写入错误原因。预警清理策略为：无预警记录超过 1 天自动删除；连续重复的有效预警只保留开始和结束/最新两条记录，减少每分钟轮询造成的日志堆积。

`backend-springboot/sql/zz_20260629_disaster_weather_warning_resolve_push.sql` 会新增“预警解除推送”开关，默认开启。开启后，轮询收到“解除雷电黄色预警”等官方解除通知时，会先确认前序存在同城市、同类型、同颜色的已处理重保预警，再向正式值班群推送一次绿色“预警解除”卡片；没有官方解除通知时，不再因为上一轮有效预警未命中而兜底发送解除通知，避免接口未返回区县预警、规则未匹配或仍存在低等级/其他类型预警时误通知值班群已解除。已经成功发送过解除卡片后，后续无预警或重复解除通知不会重复推送。

`backend-springboot/sql/zz_20260629_disaster_weather_guard_policy_v33.sql` 会按《21V-DCO-MO-224 重保戒备管理制度 V3.3》更新生产库现有规则，新增“新交付楼栋提级”“雨季专项提醒”“雨季月份”配置项，并把 `procurement_setting.setting_value` 保持为 `TEXT`。新交付提级默认关闭，需要现场确认楼栋仍未真实经历极端天气后再开启；雨季月份默认 `6,7,8,9`，各机房应按当地历史雨季并经区域负责人确认后维护。

连续预警去重不能只统计固定时间窗口内的 `SENT` 记录。每小时清理会删除连续预警段的中间状态，只保留开始和最新记录，如果旧逻辑只看 `SENT`，清理后可能丢失最近一次发送记录并在下一分钟重复推送。当前部署标准是按连续预警段去重：同城市、同预警、同戒备等级的上一条有效记录只要已经 `SENT` 或 `SKIPPED`，后续轮询都只记录为跳过；无预警后重新出现、预警段变化或上一条失败时才重新推送。

2026-06-08 的数据库管理页面新增权限初始化增量 SQL：

- `backend-springboot/sql/zz_zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz_database_admin_permissions.sql`
- `backend-springboot/sql/zz_zzzz_feishu_approval_card_notification.sql`
- `backend-springboot/sql/zz_zzzzzz_feishu_approval_card_settings.sql`
- `backend-springboot/sql/zz_zzzzzz_feishu_department_id_type_setting.sql`
- `backend-springboot/sql/zz_zzzzzz_feishu_receive_email_setting.sql`
- `backend-springboot/sql/zz_zzzzzz_feishu_root_department_multi_setting.sql`
- `backend-springboot/sql/zz_zzzzzz_feishu_root_department_setting.sql`
- `backend-springboot/sql/zz_zzzzzz_feishu_user_bindings.sql`

脚本会初始化 `database_admin:view/create/update/delete` 权限并授权给 `SUPER_ADMIN`，同时新增飞书审批卡片通知表、后台配置项和用户飞书绑定字段。上线后需要重点验证：

- `SUPER_ADMIN` 账号可在“平台管理 / 数据库管理”打开 `/platform/database`。
- 未登录或非超级管理员访问 `/api/admin/database/**` 被拒绝。
- 页面能查看当前业务库表结构和分页数据；有主键的数据表才开放编辑和删除。
- `SUPER_ADMIN` 账号可在“平台管理 / 平台集成设置 / 飞书应用与审批卡片”维护飞书审批卡片配置、飞书同步根部门 ID 列表和飞书部门 ID 类型，根部门可搜索部门并多选，保存后长连接约 30 秒内按最新配置启动或重启，组织同步会立即使用最新后台配置。
- “消息接收 ID 类型”默认 `email`，审批卡片会使用系统用户邮箱发送；邮箱必须和飞书账号邮箱一致。只有改为 `open_id/user_id/union_id` 时才需要维护飞书 ID 绑定。
- `SUPER_ADMIN` 账号可在“平台管理 / 用户管理 / 查飞书部门ID”按部门名称搜索应用可见部门，复制 `department_id` 或 `open_department_id` 后回填到平台集成设置。
- `SUPER_ADMIN` 账号可通过“发送测试卡片”验证当前账号收件配置；默认邮箱模式不需要额外绑定，改用 `open_id/user_id/union_id` 时可在“平台管理 / 用户管理 / 编辑账号 / 飞书绑定”维护对应 ID。
- 飞书登录会先按飞书返回的邮箱/手机号匹配系统用户；如果 OAuth 未返回邮箱但返回了飞书 ID，系统会使用本地用户表中已维护的邮箱调用飞书“通过邮箱获取用户 ID”接口做兜底匹配，并在成功后回填用户的飞书 ID。

2026-05-29 的智能分析底座发布新增 Redis、Qdrant 和增量 SQL：

- `backend-springboot/sql/zz_zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz_ai_bi_agent_foundation.sql`

脚本会创建 `bi_*` 和 `ai_*` 元数据表，初始化采购核心数据集的中文字段字典、采购看板默认组件、常用指标、AI 提示词模板和智能体工具注册。上线后需要重点验证：

- `procurement-redis`、`procurement-qdrant`、`procurement-backend`、`procurement-frontend` 和 `procurement-mysql` 均处于运行状态。
- Redis 在容器内执行 `redis-cli ping` 返回 `PONG`。
- Qdrant 在容器内访问 `http://127.0.0.1:6333/readyz` 正常。
- MySQL 中可以查询到 `bi_dataset`、`bi_dataset_field`、`ai_question_answer`、`ai_vector_document` 和 `ai_answer_cache_meta` 等新表。

本次订单、售后和密码策略功能还依赖以下增量脚本：

- `backend-springboot/sql/zz_zzzzzzzzzzzzzzzzzzzzzzz_procurement_after_sale.sql`：新增 `procurement_after_sale` 售后申请表。
- `backend-springboot/sql/zz_zzzzzzzzzzzzzzzzzzzzzzzzzzzz_return_after_sale_item_status.sql`：将已通过退货类售后回写到采购申请明细，并重算申请总金额。
- `backend-springboot/sql/zz_zzzzzzzzzzzzzzzzzzzzzzzz_user_password_policy.sql`：为 `sys_user` 补齐 `password_changed_at`，并新增 `sys_user_password_history` 历史密码表。
- `backend-springboot/sql/zz_zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz_single_super_admin.sql`：将最高管理员权限统一归一到 `SUPER_ADMIN`，为历史 `ADMIN`、`SYS_ADMIN` 账号补授最高管理员角色，并停用旧最高权限分组。
- `backend-springboot/sql/zz_zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz_order_invoice_collection.sql`：新增订单发票版本、附件表和查看、上传、审核权限；新库顺序迁移对应 `V32__order_invoice_collection.sql`。
- `backend-springboot/sql/zz_zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz_20260723_order_invoice_permission_repair.sql`：在生产参考权限覆盖测试配置后，幂等恢复订单发票 3 条权限及角色绑定；新库顺序迁移对应 `V33__order_invoice_permission_repair.sql`。
- `backend-springboot/sql/zz_zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz_supplier_quote_view_all_permission.sql`：新增 `supplier_quote:view-all` 只读权限及“供应商提交全量查看”独立角色，不自动绑定任何账号；新库顺序迁移对应 `V34__supplier_quote_view_all_permission.sql`。

发布脚本会在启动应用容器前，只执行发布基线 SHA 到目标 SHA 之间新增或修改的 `backend-springboot/sql/zz_*.sql`；同一路径和 Git blob 已记入迁移账本时不会因重启而重放。生产参考配置覆盖测试权限后，必须按《独立测试环境》的同步步骤显式执行修复脚本和数据断言。上线后如遇账号被提示密码到期，用户应通过右上角“账号 / 修改密码”或“我的权限”页完成本人改密后重新登录。

150 内网 runner 已直接部署在生产服务器上，自动部署不再强制要求 SSH 连接类 Secrets。

可选配置：

- `DEPLOY_REMOTE_DIR`：服务器项目目录，默认 `/home/caigou/procurement`
- `DEPLOY_ENV_FILE`：完整的生产 `deploy/.env` 内容；不配置时不会覆盖服务器现有 `.env`

第一次启用自动部署前，建议先确认服务器已安装 Docker / Docker Compose v2，并且目标目录下已经有正确的 `deploy/.env`，或者在 GitHub Secrets 中配置 `DEPLOY_ENV_FILE`。

如果服务器只允许内网访问，GitHub 托管 Runner 可能无法连上服务器，需要改用能访问服务器的 self-hosted runner 或先打通网络访问策略。当前生产部署 workflow 已改为 `runs-on: [self-hosted, procurement-prod]`。

当前内网 runner 直接使用服务器本机工具链，避免在每次部署时通过 `actions/setup-*` 下载运行时。runner 所在机器需预装 OpenJDK 17、Maven、Node.js、npm、Python 3、pip、Docker Compose v2，并保持 `procurement-prod` label 在线；runner 用户需具备 Docker 执行权限。

如遇到 `ContainerConfig` 报错，优先确认是否仍在使用旧版 `docker-compose` v1。该错误是 v1 与新版 Docker Engine 镜像元数据兼容性问题，推荐处理方式是升级到 Compose v2，并在必要时执行 `docker compose down` 后重新 `up -d --force-recreate`；不要删除 MySQL volume。

150 内网 runner 默认部署到 `/home/caigou/procurement`。如需覆盖部署目录或生产环境变量，再额外配置 `DEPLOY_REMOTE_DIR`、`DEPLOY_ENV_FILE`。

## 5. 启停与日志

## 5.1 查看日志

```bash
docker compose -f deploy/docker-compose.prod.yml logs -f backend
docker compose -f deploy/docker-compose.prod.yml logs -f frontend
docker compose -f deploy/docker-compose.prod.yml logs -f mysql
docker compose -f deploy/docker-compose.prod.yml logs -f redis
docker compose -f deploy/docker-compose.prod.yml logs -f qdrant
```

## 5.2 停止服务

```bash
docker compose -f deploy/docker-compose.prod.yml down
```

## 5.3 保留数据停止

`down` 不会删除 `mysql_data` volume，数据库仍保留。

## 6. 本地开发联调启动（非 Docker）

## 6.1 数据库初始化

先执行：

1. `backend-springboot/sql/schema.sql`
2. `backend-springboot/sql/seed.sql`

## 6.1.1 生产采购业务初始化

当需要保留人员、部门、角色权限、供应商和审批路线，只清空采购填报/审批/报价/售后流水时，使用手动脚本：

```powershell
$env:DEPLOY_HOST='<服务器 IP>'
$env:DEPLOY_PORT='<SSH 端口>'
$env:DEPLOY_USER='<部署用户>'
$env:DEPLOY_PASSWORD='<部署密码>'
python deploy\scripts\reset_business_data.py
```

执行前必须先备份生产 MySQL 和宿主机 `data/order-invoices`，并保证两份备份属于同一恢复点。脚本会上传并执行 `deploy/db/manual/reset_business_data.sql`，数据库重置成功后再清理后端容器 `/app/data/order-invoices` 内的附件；任一阶段失败都会明确报错。清理范围包括：

- 采购申请与明细：`purchase_request`、`purchase_request_item`
- 审批流水：`approval_instance`、`approval_task`、`approval_record`
- 供应商报价：`supplier_quote`、`supplier_quote_item`
- 订单发票：`order_invoice_attachment`、`order_invoice_submission` 及对应持久化附件
- 售后、月度需求池、AI 补充和采购消息状态：`procurement_after_sale`、`monthly_procurement_demand`、`procurement_ai_session`、`procurement_ai_candidate`、`procurement_message_read`
- 工作台中采购来源的关注/抄送记录：`workbench_follow`、`workbench_copy` 仅删除 `PURCHASE/MONTHLY/SUPPLIER/AFTER_SALE`
- 单号序列：`PTCG/JJCG` 重置为 `100000`，`HZCG` 重置为 `0`

该脚本不清理 `sys_*`、`org_*`、`platform_unit*`、`platform_user_app_access`、`supplier`、`supplier_qualification`、`procurement_approval_route`、`procurement_approval_policy/level/relation`、`platform_permission_request` 和 `system_error_log`。

## 6.2 启动后端

```bash
cd backend-springboot
mvn spring-boot:run
```

Windows 可使用：

```powershell
mvn.cmd spring-boot:run
```

本机需安装 JDK 17 或更高兼容版本及 Maven，并将 `mvn.cmd` 加入 `PATH`。仓库不再内置 JDK、Maven 或其他开发工具二进制。

## 6.3 启动前端

```bash
cd frontend-vue
npm install
npm run dev
```

默认前端会将 `/api` 代理到 `http://localhost:8080`。

## 7. 需要改的配置项清单

## 后端

文件：`backend-springboot/src/main/resources/application.yml`

- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`
- `JWT_SECRET`
- `SERVER_PORT`（可选）
- `OPENAPI_ENABLED`（生产建议 false）
- `SHOPPING_TOOL_TOKEN`（启用购物工具接口时必填）
- `ORDER_INVOICE_STORAGE_DIR`（非 Compose 部署按需覆盖；Compose 固定为 `/app/data/order-invoices`）

## 前端

文件：`frontend-vue/vite.config.ts`

- 开发代理目标：`server.proxy["/api"].target`

## 8. 上线建议

- 开启 HTTPS（建议前置 Nginx/SLB）
- MySQL 强密码并限制公网访问
- 更换默认账号密码
- 关闭生产 Swagger（已支持 `OPENAPI_ENABLED=false`）
- 受控发布已把数据库和宿主机 `data/order-invoices` 作为同一恢复点备份与回滚；仍需配置异机/离线备份、磁盘容量监控和经审批的恢复点保留策略，不能只依赖生产机本地副本。
- 启用账号密码轮换：用户密码默认 90 天到期，最近 6 次历史密码不可重复；历史环境需要确认 `zz_zzzzzzzzzzzzzzzzzzzzzzzz_user_password_policy.sql` 已执行。
- 验证退出登录会让旧 token 失效，通用异常响应不会向前端暴露内部异常细节。
- 前置 Nginx/SLB 应覆盖或清理客户端传入的 `X-Forwarded-For`，不要把客户端自带的来源 IP 链直接透传给后端。
- 如启用购物工具集成，确认生产 `deploy/.env` 已配置非空 `SHOPPING_TOOL_TOKEN` 后再发布。

## 9. 密钥泄露处置

如果生产 `.env`、SSH/sudo 密码、飞书 App Secret、数据库密码或 JWT Secret 曾经进入 Git 历史，必须按已泄露处理：

- 立即从当前分支移除真实密钥和硬编码部署凭据，只保留 `.env.example` 模板。
- 轮换 `JWT_SECRET`，重启后端，使历史签发或被伪造的 token 全部失效。
- 轮换 MySQL root 密码，并同步更新服务器 `deploy/.env` 或 `DEPLOY_ENV_FILE` Secret。
- 到飞书开放平台重新生成 App Secret，并同步更新生产环境变量。
- 修改 SSH/sudo 密码或替换为受控部署密钥，并同步更新 GitHub Actions Secrets。
- 即使当前 HEAD 已删除密钥，Git 历史里出现过的值也不能继续使用；需要时再做历史清理或重新建仓。
