# 独立上线控制面

该服务部署在受信任的测试主机上，与 PR 测试编排分离。它提供自带的中文管理页面，只调用固定 GitHub 仓库和固定 workflow，不执行本地命令，也不接受客户端传入 URL、分支名或 workflow 名称。

## 安全边界

- `GET /health` 公开供容器健康检查使用；页面、静态资源及全部 `/api` 均要求 HTTP Basic 认证。
- 管理员账号和高熵密码只从宿主机环境变量读取，不写入镜像、状态文件或页面。
- 仓库专属 GitHub App 可暂时留空；此时页面可访问并显示“未配置”，所有写操作返回 503。App 私钥只保存在 151 控制面宿主机，服务按需生成短期 installation token。
- 测试与生产 self-hosted Runner 使用 job-start hook，只接受固定仓库、`main`、`workflow_dispatch` 和固定 workflow 文件。未安装并核对该门禁前，`RELEASE_RUNNERS_ISOLATED` 必须保持 `false`。
- `release/batch-*` 组合测试先从可信生产结构基线创建一次性数据库和数据卷，应用候选增量 SQL 并验证精确镜像；通过后才允许更新共享测试站。未验证前 `RELEASE_CLEAN_BATCH_DATABASE_READY` 必须保持 `false`。
- 服务通过 GitHub Git Ref API 以 `force=false` 将 `main` 快进到已经通过组合测试的精确 `releaseSha`，不会用合并 PR 产生新的生产提交。
- 生产 workflow 的 `release_signature` 是 `HMAC-SHA256(batchId:productionSha:backendImage:frontendImage)`；源码 SHA 和测试通过的两个 GHCR 镜像摘要都不能在签名后替换。HMAC 只保存在 151 `.env` 和 150 本地受限文件，不写入状态卷或通过 API 返回。
- 测试 workflow 成功后及点击上线前，服务都会读取只读 JSON 标记 `/test-status/current-build-ref`，并要求其中的 `buildRef`、`backendImage` 和 `frontendImage` 精确等于当前批次保存的值。
- 测试 workflow 始终从受信任的 `main` 调度，候选分支和精确 SHA 只作为固定 inputs 传入；代理不会在候选分支上加载 workflow 文件。
- 普通 PR 只有在 head commit 上存在由 `github-actions[bot]` 创建的精确状态 `Verify pull request build @ <当前 main 完整 SHA>` 且状态为 success 时才可入选，main 变化会自动淘汰旧构建结果。
- 没有 Docker Socket、SSH 凭据或生产环境文件挂载。浏览器安全策略默认禁止被 iframe 嵌入；仅配置在 `RELEASE_CONTROL_FRAME_ANCESTORS` 中的测试站 origin 可以内嵌，不加载站外脚本或样式，也不开放 CORS。

## 环境变量

| 变量 | 说明 |
| --- | --- |
| `RELEASE_ADMIN_USERNAME` | 固定 Basic Auth 用户名 |
| `RELEASE_ADMIN_PASSWORD` | Basic Auth 高熵密码，至少 32 个字符 |
| `RELEASE_GITHUB_OWNER` | 固定仓库 owner |
| `RELEASE_GITHUB_REPO` | 固定仓库名称 |
| `RELEASE_GITHUB_MAIN_BRANCH` | 固定主分支，当前为 `main` |
| `RELEASE_GITHUB_TEST_WORKFLOW` | 固定测试 workflow 文件名 |
| `RELEASE_GITHUB_PRODUCTION_WORKFLOW` | 固定生产 workflow 文件名 |
| `RELEASE_GITHUB_APP_ID` | 仓库专属 GitHub App ID；三项 App 配置缺失时服务以未配置模式启动 |
| `RELEASE_GITHUB_APP_INSTALLATION_ID` | GitHub App 在目标仓库的 installation ID |
| `RELEASE_GITHUB_APP_PRIVATE_KEY_B64` | GitHub App PEM 私钥的单行 Base64，只保存在 151 宿主机 |
| `RELEASE_RUNNERS_ISOLATED` | Runner 门禁安装并核验后设为 `true` |
| `RELEASE_CLEAN_BATCH_DATABASE_READY` | 一次性干净数据库验收链路准备完成后设为 `true` |
| `RELEASE_PRODUCTION_HMAC_SECRET` | 生产 dispatch 签名密钥，至少 32 个字符；同值只写入 150 本地受限文件 |
| `RELEASE_TEST_STATUS_FILE` | 可信测试部署标记文件，默认 `/test-status/current-build-ref` |
| `RELEASE_AGENT_PORT` | 监听端口，默认 `8080` |
| `RELEASE_AGENT_STATE_FILE` | 原子状态文件绝对路径，默认 `/data/state.json` |
| `RELEASE_AGENT_POLL_INTERVAL_MS` | workflow 轮询间隔，默认 `10000` |
| `RELEASE_WORKFLOW_RUN_TIMEOUT_MS` | dispatch 后没有找到运行记录的超时，默认 `300000` |
| `RELEASE_CONTROL_FRAME_ANCESTORS` | CSP `frame-ancestors` 白名单；未配置时为 `'none'`，测试部署只加入明确的测试站 HTTP/HTTPS origin |

GitHub App 仅安装到目标仓库，并只授予 Contents 读写、Pull requests 读写、Actions 读写以及 commit status 读取权限。HMAC 密钥与 Basic 密码分别生成，不能复用 App 私钥或其他系统密码。

## 页面与 API

打开 `/` 后可：

1. 查看已通过指定测试检查的开放 PR，并勾选单个或多个更新。
   页面同时列出 PR 的实际变更文件；涉及 `.github/`、控制服务、可信测试脚本或生产 Compose 的 PR 会被标记为仅可人工审核合并。
2. 创建组合批次并查看测试进度。
3. 在测试环境标记仍指向该批次时确认上线。
4. 对安全范围内的生产失败进行重试。

页面调用以下同源 API；审计用户由服务端根据 Basic 用户名生成，客户端不能伪造：

- `GET /api/context`
- `GET /api/batches`
- `POST /api/batches`
- `POST /api/batches/{uuid}/promote`
- `POST /api/batches/{uuid}/retry`

创建批次：

```json
{
  "prNumbers": [12, 15],
  "idempotencyKey": "create:20260714:001"
}
```

提升或重试：

```json
{
  "confirmation": "DEPLOY_PRODUCTION",
  "idempotencyKey": "promote:20260714:001"
}
```

## 状态与重试

```text
PREPARING -> TESTING -> READY -> PROMOTING -> RELEASED
                       |  |           |
                  TEST_FAILED STALE PROD_FAILED
```

`PROD_FAILED` 会继续占用唯一活动批次，避免失败批次被新的发布覆盖。只有当 `main` 仍等于批次 `baseSha` 或精确 `productionSha` 时才允许重试。若 dispatch 后五分钟内找不到对应的精确 SHA 运行记录，批次会自动失败，不会永久停留在 `TESTING` 或 `PROMOTING`。

组合测试 dispatch 固定使用 `main` 作为 workflow ref，并传入：

```json
{
  "candidate_ref": "release/batch-<uuid>",
  "candidate_sha": "<releaseSha>",
  "release_batch_id": "<uuid>"
}
```

受信任 workflow 的 `run-name` 必须精确为 `Release test <release_batch_id> <candidate_sha>`。代理只接受 `head_branch=main` 且 `display_title` 完全匹配该字符串的运行记录。

审计状态以原子替换方式写入 `/data/state.json`，不保存 Basic 密码、GitHub App 私钥、短期 installation token、HMAC 密钥或签名。

## 运行与验证

使用独立编排启动，不要把该服务放进 PR 测试的 Compose 项目：

```bash
cp .env.release-control.example .env.release-control
docker compose --env-file .env.release-control -f docker-compose.release-control.yml up -d --build
```

本地自动化验证：

```bash
cd release-agent
npm test
docker build -t procurement-release-control .
```
