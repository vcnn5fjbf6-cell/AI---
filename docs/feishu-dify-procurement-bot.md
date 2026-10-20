# 飞书机器人 + Dify 采购助手

## 当前后端能力

- 飞书机器人事件入口：`POST /api/integrations/feishu-bot/events`
- 登录态调试入口：`POST /api/workflow/procurement-ai/sessions`
- 候选确认入口：`POST /api/workflow/procurement-ai/sessions/{id}/confirm`
- 后台消息日志：`GET /api/admin/procurement-ai/message-logs/page`，页面入口为“平台管理 / AI助手消息日志”，用于查看飞书用户原始消息、识别意图、系统回复、处理状态、错误信息和脱敏 payload。
- 确认后统一调用现有 `PurchaseRequestService.create` 和 `purchaseRequestService.submit` 创建正式采购申请单，不新建独立采购流程：
  - 普通采购：先进入原有楼长需求审核，楼长通过后由原流程进入月度需求池。
  - 紧急采购：按原紧急采购流程进入楼长审核，楼长通过后跳过月度需求池进入供应商报价。
- 任何 AI、Dify、飞书机器人能力只能作为候选采集和辅助建议入口，不得绕过原审批节点、状态、权限和审计记录。

## 配置方式

推荐在“平台管理 / 平台集成设置 / 飞书应用与审批卡片”中维护：

- `feishu_bot_enabled`：启用飞书采购AI助手。
- `feishu_app_id` / `feishu_app_secret`：机器人回复消息获取 tenant token 时优先使用。
- `feishu_approval_card_verification_token`：HTTP 事件入口校验 token；长连接接收消息时不需要公网回调。

以上配置留空时继续回退到服务器环境变量。

## 环境变量兜底

```env
FEISHU_BOT_ENABLED=true
FEISHU_BOT_VERIFICATION_TOKEN=<飞书事件订阅 Verification Token>
FEISHU_APPROVAL_CARD_LONG_CONNECTION_ENABLED=true

DIFY_PROCUREMENT_ENABLED=true
DIFY_PROCUREMENT_API_KEY=<Dify 工作流 API Key>
DIFY_PROCUREMENT_WORKFLOW_RUN_URL=https://api.dify.ai/v1/workflows/run
DIFY_PROCUREMENT_RESPONSE_MODE=blocking
```

飞书 App ID 和 App Secret 继续复用现有 `FEISHU_APP_ID`、`FEISHU_APP_SECRET`。

内网部署推荐使用飞书长连接：系统复用审批卡片的长连接客户端接收 `im.message.receive_v1` 机器人消息事件，然后交给同一套 AI 候选处理逻辑。HTTP 事件入口 `/api/integrations/feishu-bot/events` 继续保留给公网回调或调试使用。长连接接收消息时不需要公网请求地址，但仍需要在后台打开“启用飞书采购AI助手”，或通过 `FEISHU_BOT_ENABLED=true` 兜底启用，才会处理采购消息并回复用户。

机器人识别发送人时会先匹配系统用户表中的 `feishu_user_id`、`feishu_open_id`、`feishu_union_id`。如果这三个字段未维护，系统会先用系统用户已维护的邮箱/手机号调用飞书 `batch_get_id` 反查飞书 ID，再与消息事件里的发送人 ID 比对；仍未命中时，再调用飞书用户详情接口获取邮箱、手机号和工号，与系统用户匹配。匹配成功后自动回写飞书 ID，后续消息直接命中。

机器人收到非明确命令的飞书消息后，会先调用 Dify 的 `message_intent` 任务判断意图。只有 Dify 顶层返回 `messageIntent=CREATE_CANDIDATE` 且未要求补充信息时才进入采购候选创建；返回 `CHAT` 或 `HELP` 时，只把 `reply` 作为机器人回复，并在 AI 助手消息日志中记录为非采购消息。后端不会再把 `candidateAction.intent=ADD_ITEM`、`PROCUREMENT`、`PURCHASE` 等候选编辑字段当作创建依据；Dify 未配置、调用失败或未返回 `messageIntent` 时也不会创建候选。

如果 Dify 返回 `candidateAction.intent=CLARIFY` 或 `needClarification=true`，后端会把当前用户消息保存为 `procurement_ai_session.status=DRAFTING` 的待补充草稿，并把追问内容回复给飞书用户。用户下一条补充消息会与这份草稿合并后再次传给同一个 Dify 应用判断；补齐商品名称、数量/单位、用途、规格和大于 0 的参考单价/预算/链接等必要信息后，才会创建采购候选。草稿只用于采集输入，不会进入月度需求池，也不会绕过正式采购申请和审批流程。

飞书消息里的商品链接不一定只出现在 `content.text`，也可能在富文本 `href/url/title` 或链接预览字段里。后端会递归提取可读标题、URL 和预览文本，并追加为 `飞书链接/预览信息` 后传入 Dify；Dify DSL 需要把 `标题` 当作商品线索、把 `URL` 写入候选的 `ecommerceUrl`。如果飞书事件只给了 URL，没有商品名、数量或用途，仍应追问缺失信息。

飞书用户直接发送 `.xlsx/.xls` 文件时，后端会用消息 ID 和 `file_key` 下载飞书文件资源，解析首个 Sheet，并把表头和每一行明细转成 `飞书Excel附件内容` 文本后传入 Dify。标准采购明细模板会先由后端按表头确定性解析为采购候选，映射商品名称、规格型号、单位、数量、单价、用途和链接；Dify 正常返回时仍可提供 AI 审核意见，Dify 未产出可用明细时后端会用模板解析结果兜底。表格里商品名称、规格型号、数量、用途或大于 0 的单价/预算缺失时，系统会按行号追问更正，不生成 0 元候选，也不会退回到普通文本兜底。后台“AI助手消息日志”的用户消息会更新为解析后的文本，便于排查 Dify 实际收到的内容。飞书应用需要具备读取消息资源/下载消息文件所需权限，且机器人必须能收到 `im.message.receive_v1` 文件消息事件。

成功下载的飞书 Excel 附件会同步写入平台管理的“导入文件记录”。该记录只保存文件名、大小、SHA-256、来源用户、飞书消息 ID、AI 消息日志 ID、处理状态和有效数据行数，不保存原 Excel 文件内容。记录保留周期读取平台集成设置 `import_file_log_retention_days`，默认 90 天。

用户一次发送一批商品时，Dify 必须按每个实物商品输出一条 candidate。后端也会在 Dify 输出不稳定或未返回明细时，按括号、列表分隔符和明确数量单位做保守拆分与去重；“办公用品及设备采购”“一批物资”等概括词只能作为申请标题或摘要，不能作为采购明细的 `itemName`。

飞书开放平台需要在“事件与回调”中选择长连接，并订阅：

```text
接收消息 v2.0 / im.message.receive_v1
```

如订阅了消息已读事件，系统会静默忽略，避免日志出现未注册处理器警告。

可导入的 Dify Chatflow 模板见：

```text
docs/dify/procurement-ai-unified-chatflow.yml
```

后端只调用同一个 Dify 应用接口，并通过 `task_type=message_intent` 或 `task_type=product_recommendation` 交给 Dify 内部分流，同时传入 `raw_text/text`、`image_key`、`image_url`、`req_type`、`plan_month`、`need_date`、`urgency_level`、`emergency` 等字段。导入本项目 DSL 时，平台集成设置里的 `dify_api_base_url` 填 Dify API Base URL，例如 `https://wan.vnet.com/aigpt/v1`；`dify_api_key` 填 Dify 应用“访问 API”里的应用 API Key，不是模型供应商 Key；`dify_app_type` 选择 `chatflow`，后端会统一调用 `${baseUrl}/chat-messages`。如果确实使用 Dify Workflow 应用，才选择 `workflow`，后端会统一调用 `${baseUrl}/workflows/run`。

## Dify 联网插件说明

当前仓库提供的 `docs/dify/procurement-ai-unified-chatflow.yml` 默认不包含联网插件、浏览器插件或电商搜索工具节点。机器人不能主动访问淘宝、京东、抖音等外部页面，也不应该把“添加联网插件”作为回复发给飞书用户。

短期推荐做法是让用户在飞书里直接发送商品名称、数量、用途、链接或图片，Dify 只做意图判断、字段抽取和候选整理；候选确认后仍由后端创建正式采购申请单，后续走原采购审批流程。

如果后续确实要接入 Dify 官方联网/搜索插件，应在 Dify 编排器中新增工具节点，并把工具输出统一整理到 `shopping_result_json.products`，再交给现有 LLM 节点做字段映射和合规建议。插件密钥、搜索引擎配置和联网权限属于 Dify 工作区配置，不建议写进本仓库 DSL。

联网搜索的具体配置见：

```text
docs/dify/procurement-ai-web-search.md
```

推荐优先在 Dify 侧接 `Tavily` 等 Web Search Tool，或使用 HTTP Request 节点请求公开搜索 API。节点只允许放在 `task_type=product_recommendation` 分支，避免普通聊天和帮助消息也触发联网搜索。联网结果只能作为“候选参考”和“AI建议来源”，不能作为公司最终采购价格或供应商报价。

## Dify 输出建议

缺少必要信息时，Dify 不应返回 `CREATE_CANDIDATE`。示例：

```json
{
  "taskType": "message_intent",
  "messageIntent": "CHAT",
  "reply": "你说的“本”是纸质笔记本还是笔记本电脑？请补充数量、用途、规格和大于 0 的参考单价或预算。",
  "summary": "采购信息待补充",
  "candidateAction": {
    "intent": "CLARIFY",
    "needClarification": true,
    "clarificationQuestion": "你说的“本”是纸质笔记本还是笔记本电脑？请补充数量、用途、规格和大于 0 的参考单价或预算。"
  },
  "candidates": [],
  "approvalReview": {
    "riskLevel": "LOW",
    "decisionSuggestion": "SUPPLEMENT",
    "score": 0,
    "summary": "建议补充：信息不足，需补充数量、用途、规格和大于 0 的参考单价或预算。",
    "approvalCommentSuggestion": "建议补充：信息不足，需补充数量、用途、规格和大于 0 的参考单价或预算。",
    "reviewPoints": [],
    "itemOpinions": []
  }
}
```

工作流输出优先使用 `candidates` 数组：

```json
{
  "reqTitle": "机房运维耗材采购",
  "reqType": "低值易耗",
  "planMonth": "2026-04",
  "urgencyLevel": 1,
  "summary": "根据需求推荐以下商品",
  "candidates": [
    {
      "itemName": "万用表表笔",
      "specModel": "CAT III 1000V",
      "unit": "套",
      "qty": 1,
      "unitPrice": 39.9,
      "taxRate": 0,
      "reqType": "低值易耗",
      "purchaseReason": "现场仪表维护需要更换表笔",
      "platform": "京东",
      "ecommerceUrl": "https://example.com/product",
      "imageUrl": "https://example.com/product.jpg",
      "score": 0.92
    }
  ]
}
```

字段别名也兼容：`items`、`products`、`name`、`productName`、`price`、`url`、`link`、`vendor`、`merchant`。`unitPrice/price/budget` 必须是大于 0 的数字；缺失或小于等于 0 时后端不会创建采购候选，飞书侧会要求用户补充或更正价格。

## 飞书交互

用户发送采购需求、图片或链接后，机器人会返回候选商品列表。可以继续发送多条需求累积候选。

候选卡片会在每条采购明细右侧展示“撤回”按钮。点击后先只在当前卡片内临时标记该明细，明细保留并显示删除线，按钮变为“恢复”；只有点击“确认全部提交”时，后端才会提交未标记明细，并把已标记明细落库为取消。飞书卡片不支持在 Markdown 文本中嵌入真正的行内按钮，当前使用 JSON 2.0 `column_set` 做成左侧明细、右侧按钮的行式布局。

候选卡片中的 `AI审核意见` 优先读取 Dify 输出的 `approvalReview.approvalCommentSuggestion`、`approvalReview.summary` 和 `approvalReview.reviewPoints`。该意见必须以“建议通过：”“建议补充：”或“建议退回：”开头，方便审批人先判断处理方向。如果 Dify 只返回普通摘要，后端会按采购类型、明细数量、参考金额、规格完整性和报价状态生成兜底审核意见；调试时可在“AI助手消息日志”详情里查看 `Dify parsed` 是否包含 `approvalReview`。候选确认成正式采购申请后，后端会把审核意见写入 `purchase_request.ai_review_opinion` 独立字段，正式飞书审批卡片从该字段展示，不占用申请备注。

查看当前候选：

```text
可以提交了
```

修改用途：

```text
把第2项用途改成机房巡检备用
```

删除候选：

```text
删除第2项
```

确认全部候选：

```text
确认提交
```

普通候选会生成普通采购申请并进入楼长需求审核；消息里明确识别为紧急、急用、特急等的候选会生成紧急采购申请并按现有紧急采购流程提交。

仍保留单条确认格式：

```text
确认 PA20260429ABC123 1
```

如果不写会话号，会默认确认该用户最近一个待确认会话：

```text
确认 1
```

图片需求会下载飞书图片并优先调用后台配置的 M3 视觉模型识别商品名称、规格和参考单价。用户只发图片且没有文字需求时，后端只保存 `DRAFTING` 草稿并追问缺失信息；如果图片已经识别出名称、规格和大于 0 的参考单价，只追问采购数量和用途。用户随后补充“买200个用于更换使用”这类数量/用途文本时，后端会把图片草稿和本次补充本地结构化成采购候选，再复用原 `ProcurementAiService.startSession`、候选确认和正式采购审批链路；这条补全路径不再等待 Dify，避免外部模型慢调用阻塞飞书回复。Dify 和 M3 客户端都设置了 5 秒连接、25 秒读取超时，超时后走已有本地降级。

候选确认生成正式采购申请时，申请标题由后端按“机房/楼栋-采购类型采购申请”统一生成，例如 `百度M1-低值易耗采购申请`。Dify、M3/MiniMax 或用户后续自然语言只负责补充候选明细、数量、用途和类型，不允许把聊天原文当成正式申请标题。

Excel 文件会由后端下载并解析首个 Sheet 后再传给 Dify。标准采购明细模板会先由后端按表头兜底解析，Dify 只负责补充意图判断和 AI 审核意见。
