# 151 测试飞书通知

151 可以使用独立测试应用验证飞书卡片发送，但必须与生产应用、正式群和正式用户配置隔离。该能力只用于管理员手工点击“发送测试卡片”，不启用审批心跳、长连接、机器人收消息、供应商自动通知或生产业务数据。

## 安全边界

- App ID、App Secret 和测试接收邮箱只保存在 151 的 `/opt/procurement-test/deploy/.env.test`，文件权限保持 `0600:ceshi`。
- 一次性配置通过仓库 Secret `TEST_FEISHU_CONFIG_B64` 进入受信任工作流。工作流成功并验证登录后必须立即删除该 Secret。
- 测试接收邮箱由 151 的受限环境文件注入手工测试卡片配置，不写入系统用户表，不保存 App Secret 到数据库；正式通知开关继续保持关闭。
- 测试后端仍然只连接 `app_internal` 与 `data_internal`，不能直接访问互联网。
- `feishu-egress` 使用当前已经验收的前端镜像运行固定 Nginx 配置，只代理 `/open-apis/` 到 `https://open.feishu.cn`，不暴露宿主机端口。
- `feishu_egress` 外部网络只允许 `procurement-test-feishu-egress-1` 一个容器加入；部署和配置脚本都会复核。

## 飞书测试应用准备

1. 使用专门的测试应用，启用机器人能力，并把应用可用范围限制到测试人员。
2. 开通发送消息权限；如按邮箱解析接收人，还要开通用户 ID 查询所需的通讯录只读权限。
3. 创建并发布测试版本，确保目标测试人员可以使用该应用。
4. App Secret 一旦出现在截图、聊天或日志中，先在飞书开放平台轮换，再继续配置。
5. 本阶段不配置事件订阅和长连接；需要验证机器人收消息时另行评审外联域名和回调权限。

## 一次性配置

生成以下 JSON，并对 UTF-8 原文做单行 Base64 编码：

```json
{
  "enabled": true,
  "appId": "cli_test_app_id",
  "appSecret": "rotated-test-secret",
  "recipientEmail": "demo.user013@example.invalid"
}
```

把编码结果临时写入仓库 Secret `TEST_FEISHU_CONFIG_B64`，然后从 `main` 手工运行 `Deploy Trusted Release Control`，勾选 `configure_test_feishu_notifications`。工作流会：

1. 在 GitHub 托管 Runner 打包并校验精确的 `main` 源码，通过运行号与提交号绑定的 Cache 交给 151，避免 151 直连 GitHub 检出失败。
2. 获取与测试发布相同的宿主机部署锁，防止配置期间与 Gitee 或 release batch 测试部署并发。
3. 校验当前容器来自精确 GHCR digest，或来自与宿主机 Gitee 状态、镜像内 source/commit 标签和 Docker 镜像 ID 一致的 Gitee SHA 标签；旧环境缺少 `current-gitee-build-ref` 时，只有上述三项全部一致才会原子补回该审计标记。如果宿主机状态与运行镜像不一致，可在受信发布控制 workflow 中显式启用 `reconcile_test_gitee_deployment`，先完整重跑 Gitee 测试部署再继续配置。
4. 原子更新 151 的 `.env.test`，不输出凭据。
5. 启动飞书专用代理，并通过代理申请 tenant access token 验证测试应用凭据。
6. 重新执行测试数据库安全配置，并把测试接收邮箱仅注入手工测试卡片配置；不改写用户邮箱，正式通知开关保持关闭。
7. 使用原镜像重建测试后端，复核前后镜像 ID 不变、后端无法直连公网且只能连接飞书代理。

工作流成功后立即删除 `TEST_FEISHU_CONFIG_B64`。如需停用，使用相同结构提交 `enabled: false`，其余三个字符串留空。

## 验证

登录 151 测试站后进入“平台管理 / 平台集成设置”，点击“发送测试卡片”。测试人员收到标题为测试卡片的消息即表示 App 凭据、用户解析和消息发送链路已打通。该按钮不会创建或推进采购审批。

不要在测试环境开启“本地心跳”“长连接回调”“供应商报价通知”或“管理员接收全部卡片”。
