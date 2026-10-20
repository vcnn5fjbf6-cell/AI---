# 部署目录说明

- `docker-compose.prod.yml`：生产编排文件（MySQL + Redis + Qdrant + Backend + Frontend）
- `.env.example`：环境变量模板
- `deploy_local_runner.sh`：受控生产 workflow 的本机发布脚本，只接受测试通过的精确 GHCR digest；停写后为 MySQL 与订单发票附件创建同一恢复点，在候选应用启动前失败时自动恢复，启动后失败则停机并保留现场供人工决策
- `pull_immutable_images.sh`：在生产维护窗口开始前拉取两个已测试的精确 GHCR digest；单个镜像失败时最多重试三次并复用生产机已完成的 Docker 层
- `verify_production_baseline.sh`：只读核验生产基线记录、可信镜像引用和目标提交祖先关系；生产 workflow 会在传输候选镜像前执行，正式发布脚本会再次复用
- `deploy_test_runner.sh`：测试环境共用发布脚本，串行执行数据库安全检查、隔离部署和健康检查
- `deploy_test_gitee.sh`：只在 151 上构建并部署 Gitee `main` 的精确提交
- `gitee_test_deploy_bootstrap.sh`：使用只读部署公钥和固定主机指纹抓取 Gitee `main` 的主机引导脚本
- `deploy_current.py`：旧远程构建发布脚本，保留用于历史排障，不再作为正式发布入口
- `check_status.py`：当前状态检查脚本，只从环境变量读取服务器连接信息
- `scripts/deploy.sh`：Linux 一键部署脚本
- `scripts/deploy.ps1`：Windows 一键部署脚本
- `scripts/stop.sh`：停止脚本

使用前请先：

1. 复制 `.env.example` 为 `.env`
2. 修改数据库密码与 JWT 密钥
3. 按需配置 `REDIS_PASSWORD`、`QDRANT_ENABLED`、`QDRANT_URL`、`QDRANT_COLLECTION_PREFIX`
4. 使用 GitHub Actions 中受控的 `Deploy Production (Manual)` 发布；生产 Compose 不再支持本机 `up --build`

首次切换到受控发布前，维护者必须人工核验生产当前实际运行的完整 Git commit、后端镜像引用和前端镜像引用，并在 `/home/caigou/procurement/deploy/releases/current.json` 写入 `releaseSha`、`backendImage`、`frontendImage` 三个字段作为迁移基线。正常基线必须使用精确 GHCR digest；为兼容已部署的紧急修复版本，首次迁移也接受与 `releaseSha` 完全一致的 `procurement-backend:hotfix-<SHA>` 和 `procurement-frontend:hotfix-<SHA>`，脚本仍会把引用与当前容器的 `Config.Image` 和本地镜像 ID 交叉核验并按镜像 ID 建立回滚标签。旧基线若只有 `releaseSha`、使用其他可变标签，或任一值与实际容器不一致，发布会在同步源码、停止应用和修改数据库之前 fail-closed；只能在人工核验实际运行值后补齐，不能猜测，也不能用 `main` 当前值代替生产实际版本。首次受控发布成功后，新基线会自动改为本批次已测试的精确 GHCR digest。

生产 workflow 在候选镜像 Cache 恢复和 GHCR 拉取之前先执行上述只读基线校验，因此明显错误会在大文件传输前失败；生产只恢复与已授权测试运行编号、重跑次数和发布 SHA 精确绑定的 Cache，并通过 `verify_candidate_image_archive.py` 校验归档哈希、候选提交、镜像 digest 和 Docker config ID。`deploy_local_runner.sh` 进入正式发布时会再次校验，并继续把记录中的镜像引用与实际运行容器和本地镜像 ID 交叉核对。提前失败和缓存预加载只节省等待时间，不会放宽发布授权或完整门禁。

后端候选镜像先按 Spring Boot 的依赖、加载器和业务代码分层，再把依赖按稳定文件名拆成 `a-l`、`m-n`、`o`、`p-r`、`s`、`t-z` 六层；当前最大分组约 24 MiB，构建会拒绝任何超过 32 MiB 的依赖层。构建输出时间固定，依赖不变时层 digest 可复用；Maven 依赖预取使用普通 Docker 中间层缓存，兼容 151 的 legacy builder，不要求额外安装 buildx。测试成功后保留唯一候选 Cache 给生产预热，生产成功后按缓存 ID 删除，失败则保留供同批次重试；测试失败的缓存直接清理，未进入生产的成功批次由 GitHub 正常缓存淘汰兜底。前端只在依赖清单变化时重跑 `npm ci`。生产始终拉取测试通过的精确 digest，不会因缓存优化改为现场构建。若缓存未命中、被拒绝或 GHCR 拉取自然失败，工作流会回退到精确 digest 并最多重试三次；Docker 已完成的层可继续复用，正在传输但未完成的单层不承诺字节级断点续传。

每次受控生产发布会在维护窗口先停止 `frontend/backend` 写入端，再在 `/home/caigou/procurement/backups/releases/<批次ID-UTC时间>/` 原子生成 MySQL 全量逻辑备份、`data/order-invoices` 附件归档、逐文件大小/摘要清单以及发布基线副本。备份完整性检查通过后才同步源码和执行增量 SQL。候选后端启动前的备份、同步或迁移失败会自动恢复旧源码、数据库、附件和旧镜像，并保持 `deploy/releases/current.json` 指向原版本。

候选后端启动可能触发定时任务、飞书通知或其他外部副作用，因此它是数据自动回滚的截止点：脚本在启动候选后端前准备好新版本标记，随后用不发布宿主机端口的临时候选前端验证前后端链路；从候选后端启动开始，任何镜像、网络、健康检查或生产前端切换失败都只会停止候选应用并保留迁移后数据与恢复点，不会自动把 MySQL/附件倒退到快照。维护者必须结合外部消息与数据库状态决定继续修复还是按恢复点人工回退，避免已发送通知对应的业务记录被回滚后重复发送。候选链路通过后才启动生产前端，核验运行状态、Compose 身份、精确镜像以及宿主机首页/API；全部通过后最后原子推进 `current.json`。

恢复点位于生产机本地，只用于发布失败快速回滚，不能替代异机或离线灾备。发布前需确认维护窗口和本地磁盘空间；在建立正式保留策略前不要自动清理恢复点。生产 job 总预算为 360 分钟，准备阶段超过 90 分钟会在进入维护窗口前拒绝发布；工作流通过 `setsid` 为发布脚本建立独立进程组，脚本内部的 120 分钟看门狗只限制前向发布。脚本正常进入自动恢复或安全停机前会先取消看门狗，因此恢复过程不会在第 120 分钟被误中断，并至少保留约 150 分钟的 job 窗口；若普通失败与看门狗到期恰好并发，则保持应用停机并要求人工使用恢复点处理。专项回归可在非生产环境运行 `bash deploy/tests/test_deploy_local_runner_recovery.sh`，覆盖基线预检、备份失败、附件内容损坏、迁移失败、跨前向截止时间完成回滚、候选健康检查失败后的禁止数据回滚、恢复失败、信号中断和成功路径。

生产环境统一使用 Docker Compose v2。旧版 Python 版 `docker-compose` v1 会触发 `ContainerConfig` 等兼容性问题，不再作为部署工具；如历史脚本仍调用 `docker-compose`，服务器可通过 `/usr/local/bin/docker-compose` 兼容脚本转发到 `docker compose`。

使用 `--force-recreate` 发布时，如果 Docker Compose 返回 `container name ... already in use` 这类临时容器名冲突，优先执行 `docker compose down` 后重试 `up -d --force-recreate`，不要删除数据卷。受控发布脚本已内置兼容重试逻辑。

Redis 用于后续问答缓存和会话加速；Qdrant 用于后续向量检索。智能分析底座的 MySQL 元数据由 `backend-springboot/sql/zz_zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz_ai_bi_agent_foundation.sql` 在发布脚本中自动执行。

真实 `.env`、SSH 密码、sudo 密码、飞书 App Secret、JWT Secret 不允许提交到仓库。临时排障脚本也不能写死服务器地址或密码，必须通过环境变量传入。
