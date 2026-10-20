# 飞书接入配置说明（本机开发环境）

> 系统已内置飞书能力：飞书登录、采购 AI 机器人、审批卡片通知、组织/人员同步。
> 本说明用于把本机系统真正接到你的飞书企业自建应用。

## 1. 需要准备的东西

| 项目 | 从哪里获取 | 用途 |
|------|-----------|------|
| App ID（`cli_...`） | 飞书开放平台 → 开发者后台 → 你的自建应用 → 凭证与基础信息 | 登录 / 机器人 / 卡片 |
| App Secret | 同上 | 登录 / 机器人 / 卡片 |
| Verification Token | 飞书后台 → 事件与回调 → 加密策略 | 接收事件（机器人、卡片回传） |
| Encrypt Key（可选） | 同上 | 开启加密时必填 |
| 公网可达地址 | 自备域名/隧道 | 仅机器人、卡片回调需要 |

## 2. 飞书开放平台配置

1. 创建/进入**企业自建应用**，记录 App ID、App Secret。
2. 「安全设置 → 重定向 URL」新增：`http://localhost:5173/login`
   （本地登录回跳发生在你的浏览器里，localhost 可用）
3. 开通权限（至少）：
   - 登录：`contact:user.email:readonly`、`contact:user.id:readonly`、`contact:user.base:readonly`
   - 组织同步（可选）：通讯录读取相关权限
   - 机器人/卡片（可选）：消息与卡片相关权限
4. 「事件与回调」配置请求地址（仅机器人/卡片需要，必须是飞书能访问到的地址，例如 `https://<你的公网地址>/api/feishu/...`），并记录 Verification Token / Encrypt Key。
5. 发布应用版本并使企业内可用。

## 3. 本机配置

### 3.1 飞书登录（必需）

编辑项目根目录 `feishu.env`（已生成，且已加入 .gitignore）：

```
FEISHU_AUTH_ENABLED=true
FEISHU_APP_ID=cli_xxxxxxxxxxxx
FEISHU_APP_SECRET=xxxxxxxxxxxxxxxxxxxxxxxx
FEISHU_REDIRECT_URI=http://localhost:5173/login
```

启动脚本 `start-system.bat` 会在启动后端前自动加载该文件，因此**修改后重启服务即可生效**。

> 说明：飞书登录相关的开关与凭据由后端环境变量读取（`FEISHU_AUTH_ENABLED` / `FEISHU_APP_ID` / `FEISHU_APP_SECRET` / `FEISHU_REDIRECT_URI`）。

### 3.2 机器人 / 审批卡片（可选）

两种方式任选：

- **HR 模块内配置（推荐）**：人力资源管理 → 系统设置 → 「飞书接入配置」，可直接维护 App ID/Secret、机器人、审批卡片、Verification Token、Encrypt Key、系统访问基址等全部飞书参数，保存即时生效。
- **平台管理处配置**：平台管理 → 平台集成设置 → 「飞书应用与审批卡片」（同样是这套参数）。
- **环境变量**：在 `feishu.env` 中补充 `FEISHU_APPROVAL_CARD_VERIFICATION_TOKEN`、`FEISHU_APPROVAL_CARD_ENCRYPT_KEY`、`FEISHU_APPROVAL_CARD_APP_BASE_URL`。

> 机器人/卡片要求**飞书服务器能访问到本系统**。本机 `localhost` 飞书无法回调，需要公网地址或临时隧道（如 `cloudflared`），并把该地址填入「系统访问基址」/事件回调地址。

## 4. 启动与验证

1. 双击 `start-system.bat`（或运行后重启后端）。
2. 打开 http://localhost:5173/login → 点击「使用飞书登录」。
3. 应跳转到飞书授权页，授权后回跳 `/login` 并进入工作台。

接口自检：

```
GET http://localhost:8081/api/auth/feishu/login-url?redirectUri=http%3A%2F%2Flocalhost%3A5173%2Flogin
```

- 未配置：`{"code":"FEISHU_NOT_CONFIGURED","message":"飞书登录未启用"}` 或「飞书应用参数未配置」
- 已配置：返回 `data.url`（飞书授权地址）

## 5. 常见问题

| 现象 | 原因 / 处理 |
|------|-------------|
| 「飞书登录未启用」 | `FEISHU_AUTH_ENABLED` 未开启，或后端未重启 |
| 「飞书应用参数未配置」 | App ID / App Secret 为空 |
| 飞书报 redirect_uri 不合法 | 飞书后台「重定向 URL」未加入 `http://localhost:5173/login` |
| 机器人/卡片收不到回调 | 系统地址飞书不可达（需公网/隧道），或事件订阅、Verification Token 配置不对 |
| 授权后提示无权限/账号未绑定 | 该飞书账号邮箱/手机号未匹配系统用户，需在平台管理绑定或同步飞书用户 |