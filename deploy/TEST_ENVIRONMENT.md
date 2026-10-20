# 独立测试环境

测试环境使用 `docker-compose.test.yml`，与生产环境的容器名、网络、数据卷、数据库、Redis、JWT 和端口完全独立。

## 安全边界

- 只有前端发布宿主端口；MySQL、Redis 和后端不发布端口。
- 业务后端仅连接 Docker `internal` 网络，没有互联网出口。可选的 `feishu-egress` 侧车是唯一允许加入 `feishu_egress` 外联网络的容器，只把内部 `/open-apis/` 请求转发到固定的 `open.feishu.cn` 上游。
- 测试前端额外连接关闭 IP 伪装的 `test_ingress` 网络，只用于把宿主机 `18080` 转发到 Nginx；该网络不得开启 IP 伪装，前端仍不能主动访问公网。
- 上线控制面使用独立 Compose 项目 `/home/ceshi/procurement-release-control`，不属于 PR 测试栈；它只调用代码中固定的 GitHub 仓库与测试/生产 workflow，不挂载 Docker Socket、生产 SSH 密钥或生产环境文件。
- 仓库专属 GitHub App 私钥、HTTP Basic 密码和生产 HMAC 密钥只保存在上线控制面的 `.env`；同一 HMAC 仅额外写入 150 生产 Runner 的本地受限文件。它们均不进入 GitHub Environment、`deploy/.env.test`、数据库、前端包或仓库。
- 151 与 150 self-hosted Runner 均安装 job-start hook，按仓库、事件、`main` 引用、workflow 名称和 workflow 文件路径做白名单校验；PR、push、schedule 及其他 workflow 在任务开始前即被拒绝。
- 测试 Profile 默认关闭全部定时任务、飞书、邮件、Dify、M3、Qdrant 和购物接口。151 可按 [测试飞书通知说明](../docs/TEST_FEISHU_NOTIFICATIONS.md) 启用独立测试应用的手工测试卡片；审批心跳、长连接、机器人收消息和自动业务通知仍强制关闭。
- 候选部署要求 Docker Server 25.0.5 或更新版本，旧版本直接拒绝启动，避免旧版 `internal` 网络仍能通过宿主 DNS 转发外联。
- 前端保持只读根文件系统；`/var/cache/nginx` 使用 root 所有的临时盘，由受限的 Nginx 主进程创建并降权管理缓存目录，不得把该挂载改为非 root 所有。
- 数据库启动后再次执行 `999_safety.sql`，避免数据库设置覆盖环境变量。测试接收邮箱只注入后端的手工测试卡片配置，不改写任何系统用户邮箱；正式通知开关继续保持关闭。
- 数据库基线必须是纯结构导出；发现非测试用户或业务单据时，启动会拒绝继续。
- 前端启动时校验当前站点的 `X-Environment: test` 响应头；只有测试站显示右下角“测试环境”构建标识和“上线管理”入口，生产站默认隐藏。
- 生产参考配置只能由维护者执行单向只读同步，允许表限定为 `sys_region`、`sys_role`、`sys_permission`、`sys_role_permission`、`org_scope` 和 `procurement_approval_route`；同步前必须备份测试侧原表，并在同一事务中替换配置、恢复当前测试版本尚未进入生产的功能权限，再重新绑定测试管理员。
- 严禁同步 `sys_user`、密码哈希、会话、手机号邮箱、飞书身份、`platform_user_app_access`、供应商联系人、采购业务记录或外部集成设置。测试环境仍只保留独立测试管理员和测试产生的账号。

## 首次启动

1. 将 `deploy/.env.test.example` 复制为 `deploy/.env.test` 并生成独立测试数据库、Redis、JWT 和测试管理员密码。该文件不得包含任何生产发布凭据。
2. 将经过检查的纯结构基线放到 `deploy/db/test-init/runtime/001_baseline.sql`。
3. 检查编排：

   ```bash
   docker compose -f docker-compose.test.yml --env-file .env.test config --quiet
   ```

4. 未合入的 PR 候选代码不允许在 151 本机构建。Gitee 流水线只允许从固定仓库的 `main` 拉取精确提交后本机构建；发布批次仍使用 GitHub workflow 生成的精确 GHCR digest。人工排障使用发布制品时，必须先提供已经推送并核验过的两个 digest：

   ```bash
   export TEST_BACKEND_IMAGE='ghcr.io/dongfanfan94-droid/enterprise-procurement-platform-backend@sha256:<64位摘要>'
   export TEST_FRONTEND_IMAGE='ghcr.io/dongfanfan94-droid/enterprise-procurement-platform-frontend@sha256:<64位摘要>'
   docker compose -f docker-compose.test.yml --env-file .env.test up -d --no-build
   ```

5. 验证容器、首页、OpenAPI 和外联隔离：

   ```bash
   docker compose -f docker-compose.test.yml --env-file .env.test ps
   curl -fsS http://127.0.0.1:18080/ >/dev/null
   curl -fsS http://127.0.0.1:18080/v3/api-docs >/dev/null
   docker compose -f docker-compose.test.yml --env-file .env.test exec backend \
     bash -lc 'if timeout 5 bash -c "</dev/tcp/open.feishu.cn/443"; then exit 1; fi'
   ```

删除测试卷会清空全部测试数据，因此不放入任何自动部署脚本，只允许维护者人工执行。

## 生产参考配置单向同步

当测试库的角色、权限、组织范围或部门路线与生产不一致时，维护者可以从生产 MySQL 只读导出安全边界中列出的六张配置表，并覆盖到测试库。操作必须满足：

1. 同步前比较六张表的列结构哈希；结构不一致时停止，先通过正常发布补齐结构。
2. 在 151 为六张测试配置表生成带时间戳的压缩 SQL 备份，不覆盖历史备份。
3. 生产侧只执行一致性查询和无建表语句的数据导出，不得执行 `INSERT`、`UPDATE`、`DELETE` 或 DDL。
4. 测试侧在同一事务中清空六张配置表、导入生产快照；随后执行当前测试版本中用于恢复“尚未进入生产”的权限修复脚本，并把 `test-admin` 重新绑定生产的 `SUPER_ADMIN` 角色和首个启用区域。订单发票功能至少要执行 `backend-springboot/sql/zz_zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz_20260723_order_invoice_permission_repair.sql`。
5. 同步后比较生产参考行的数量和内容校验值，确认测试配置与生产一致；测试专有权限必须单独列出，不得伪装成生产快照内容。订单发票还要确认 3 条 `procurement_order:invoice-*` 权限及对应角色绑定存在，同时确认测试管理员仍可登录、外部集成仍关闭、生产表校验值未变化。

`test_schema_migration_history` 只证明某个脚本版本曾执行过，不证明其写入的权限配置后来没有被参考数据同步覆盖。同步维护不得据此跳过上述数据一致性校验；每增加一个尚未上线生产、但依赖 `sys_permission` 或 `sys_role_permission` 的测试功能，都必须同时补充幂等修复脚本和同步后断言。

该同步是维护操作，不属于 Gitee 自动部署。自动部署不得读取生产数据库，也不得自动刷新测试配置。

## GitHub PR 检查与发布批次部署

面向 `main` 的非草稿 PR 只触发 `.github/workflows/verify-pr.yml`，在 GitHub 托管的临时 Runner 中以只读权限检出合并提交并构建前后端 Docker 镜像。PR 检查不推送镜像、不使用私有凭据，也不连接或部署 151。

上线管理页面位于测试站“平台管理 / 上线管理”。页面内嵌 151 独立 release-control 工作台；HTTPS 测试站默认访问 `https://procurement-test-151.tailb89388.ts.net:18081/`，HTTP 测试站仍按当前主机使用 HTTP。release-control 只允许配置的测试平台地址作为 iframe 来源，默认 CSP 包含 HTTPS 测试域名并保留原有 HTTP 测试地址；首次进入仍使用独立上线管理员 Basic 账号，发布凭据不进入业务前端、业务后端或测试数据库。

上线管理创建发布批次时，仍通过 `.github/workflows/deploy-test.yml` 的 `workflow_dispatch` 执行完整发布验收：

1. 在 GitHub 临时 Runner 校验候选不包含发布基础设施变更，随后构建批次并推送以完整 commit SHA 标记的不可变 GHCR 镜像。
2. 151 Runner 的 job-start hook 只接受从 `main` 手工调度的固定 `deploy-test.yml`，任务再核对宿主机门禁脚本与仓库受信任版本一致。
3. 151 只检出受信任的 `main` 部署脚本、精确批次源码并拉取上述镜像；仓库包含 Git symlink 时直接拒绝。
4. `deploy/verify_release_batch_clean_db.sh` 从可信生产结构基线创建一次性 Compose 项目和全新数据卷，执行基线到候选之间的 `backend-springboot/sql/zz_*.sql`，再使用精确镜像检查首页和 OpenAPI；无论成功失败都销毁一次性容器和数据卷。
5. 干净数据库验收通过后，`deploy/deploy_test_runner.sh` 才更新共享测试站；同步代码时保留 `deploy/.env.test` 和 `deploy/db/test-init/runtime/`，增量 SQL 必须可重复执行。
6. Docker Compose 在 `internal` 网络重建测试前后端，保留共享测试 MySQL、Redis 和附件数据卷；候选运行容器不能主动访问 151 宿主、互联网或生产网段。
7. 检查镜像 ID、首页、OpenAPI、容器健康和业务后端外联隔离；全部成功后，将完整 commit SHA 与前后端两个不可变 GHCR digest 作为 JSON 原子写入 `deploy/test-status/current-build-ref`。

## Gitee Go 迁移验证

测试主机组标识为 `procurement-test-151`，主机 `m6-procurement-151` 运行 Gitee Go 自有 Agent。`.workflow/gitee-test-agent-smoke.yml` 只允许手工触发，并仅检查主机身份、Docker、测试首页和 OpenAPI，不构建或部署候选代码。

`.workflow/gitee-test-deploy.yml` 在 Gitee `main` 更新后调用 151 上固定的只读引导脚本。引导脚本使用专用部署公钥、固定 Gitee SSH 主机指纹和固定仓库地址，只抓取 `main`，再以 detached worktree 运行该提交中的 `deploy/deploy_test_gitee.sh`。部署脚本执行以下步骤：

1. 校验执行主机必须是 `m6-procurement-151`、账号必须是 `ceshi`，目标必须是完整 40 位提交 SHA。
2. 在 151 本地从已合并的可信 `main` 构建前后端镜像；镜像标签和镜像元数据均绑定完整提交 SHA，不执行未合并 PR 中的代码。
3. 复用 `deploy/deploy_test_runner.sh` 的测试数据库标记、增量 SQL、隔离网络、镜像 ID、页面、OpenAPI、容器健康和后端外联检查。
4. Gitee 与 GitHub 部署共用 `flock` 锁，迁移期间即使两个任务接近触发，也只会串行修改 `/opt/procurement-test`。
5. Gitee 状态写入 `deploy/test-status/current-gitee-build-ref`，并在替换测试环境时删除发布批次使用的 `current-build-ref`，避免把本地测试镜像误当成可上线的 GHCR 制品。
6. 健康检查和 Gitee 构建状态写入成功后，前后端各保留当前运行版本及最近版本、合计最多 3 个提交标签，并仅清理带 `com.company.procurement.source=gitee` 标签且超过 7 天的 dangling 镜像；不会执行 `docker system prune`。清理与磁盘读取失败只输出告警，随后原子写入宿主机 `current-sha`。项目目录所在文件系统达到 70%/80% 时输出分级告警，可用 `TEST_IMAGE_RETENTION_COUNT` 和 `TEST_DANGLING_IMAGE_MAX_AGE` 调整策略。

GitHub→Gitee 的 Pull 镜像更新不会稳定产生普通 Gitee push 触发事件，因此还需在 Gitee Go 流水线“高级设置”中配置每 5 分钟定时触发。引导脚本每次先抓取 Gitee `main`；当完整 SHA、Gitee 状态标记、当前容器镜像、容器健康和页面检查全部一致时立即退出，不重复构建。发布批次部署会反向删除 `current-gitee-build-ref`，所以下一次定时检查会恢复 Gitee `main` 测试环境，而不会被旧状态误判为无需部署。

Gitee 普通测试部署只负责合入后的 `main` 验证，不替代上线管理创建的 GitHub 发布批次。发布批次仍通过 `.github/workflows/deploy-test.yml` 的 `workflow_dispatch` 构建并验证精确 GHCR digest；生产由上线管理签名后交给固定 `deploy.yml` 和 150 Runner 发布，不纳入 Gitee 迁移。

## 上线管理

`SUPER_ADMIN` 在测试站“平台管理 / 上线管理”中打开 151 的独立控制台（Tailscale HTTPS `:18081`），使用单独的上线管理员 Basic 账号查看已通过 PR 测试的更新：

1. 单选或多选 PR 后创建上线批次。
2. 控制面从 `main` 创建 `release/batch-*` 临时分支，只合入所选 PR，并始终从受信任的 `main` workflow 再次触发 151 整体测试。
3. 只有同一批次测试成功且 `main` 未发生变化时，页面才允许确认上线生产。
4. 确认后控制面以非强制 fast-forward 把 `main` 精确移动到已测试的 `releaseSha`，再使用 HMAC 同时授权该 SHA 与测试通过的两个镜像 digest。生产只拉取这两个 digest，不在生产机重新构建候选代码。生产部署期间的新请求排队，不会取消正在执行的发布。

生产自动发布还要求宿主机已有经人工核验的 `deploy/releases/current.json`，其 `releaseSha` 必须是生产当前实际运行版本。发布脚本只执行该 SHA 到新 SHA 之间新增或修改的 `zz_*.sql`，先停止应用、完成迁移，再启动测试通过的镜像；缺少基线标记时在任何生产变更前失败，避免首次建迁移账本时重放全部历史脚本。

GitHub App 缺失、PR 草稿、构建失败、合并冲突、批次过期或存在活动批次时，页面必须禁止上线并显示原因。修改 `.github/`、`.workflow/`、发布控制服务、可信测试脚本或生产 Compose 的 PR 禁止从按钮自动上线，必须单独人工审核合并。状态卷 `release_control_state` 保存批次、操作者和 Actions 链接；删除该卷会丢失测试侧发布审计记录。

控制面由 `.github/workflows/deploy-release-control.yml` 从 `main` 部署，但 workflow 只更新代码、镜像标签和固定非敏感配置，不读取或写入发布密钥。GitHub 托管 Runner 先检出精确的 `main` SHA，拒绝符号链接，使用 `git archive` 生成源码包和 SHA-256 清单，再通过绑定运行号与提交号的 Actions Cache 交给 151；151 不再直接连接 `github.com` 执行 Git 检出。自托管 Runner 只接受精确 Cache 命中，复核清单、归档哈希、路径边界、文件类型和解包体积后才执行仓库脚本，任务结束清理源码包和工作目录。部署完成后仅把 Tailscale 18081 从 TCP 转发更新为 HTTPS 反向代理；更新前后的 Serve JSON 配置除 18081 外必须完全一致，避免覆盖既有 443 Funnel 和 18080 测试站转发，同时断言 18081 保持 tailnet-only。仓库专属 GitHub App 私钥、Basic 密码和 HMAC 由 151 宿主机独立维护；App 只安装到本仓库并按需签发短期 installation token。生产 HMAC 的同值只保存在 150 的 `/home/caigou/.config/procurement-release/hmac-secret`，权限为 `0600`。Runner 门禁和一次性干净数据库验收均通过后，才可把 `RELEASE_RUNNERS_ISOLATED`、`RELEASE_CLEAN_BATCH_DATABASE_READY` 设为 `true`。禁止使用个人访问 Token 代替该 App。

首次数据库初始化会拒绝包含用户或采购业务数据的基线，并写入 `test_environment_marker=procurement-test`。后续 PR 发布只验证该安全标识并再次清空外部凭据，不会重置开发者已经产生的测试业务数据，也不会把测试管理员密码恢复成初始密码。
