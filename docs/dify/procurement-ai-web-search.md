# Dify 采购助手联网搜索接入

本文用于在现有 `docs/dify/procurement-ai-unified-chatflow.yml` 基础上给 Dify 增加联网搜索能力。平台后端仍只调用同一个 Dify Chatflow；联网搜索只负责补充商品参考信息，不直接创建月度需求池、不绕过采购申请和审批流程。

## 推荐方案

优先使用 Dify 的 Tool 节点或 HTTP Request 节点接公开搜索 API。Dify 官方文档说明：

- HTTP Request 节点用于从工作流调用外部 API，并支持请求头、请求体、变量替换、超时和鉴权配置。
- Tool 节点用于把 Dify 插件/工具作为独立节点接入工作流，可用于 Web Search 等实时数据查询。
- If-Else 节点用于按变量条件做分支，适合只让 `product_recommendation` 任务触发联网搜索。

参考链接：

- [Dify HTTP Request 节点](https://docs.dify.ai/en/use-dify/nodes/http-request)
- [Dify Tool 节点](https://docs.dify.ai/en/use-dify/nodes/tools)
- [Dify If-Else 节点](https://docs.dify.ai/en/use-dify/nodes/ifelse)
- [Tavily Search API](https://docs.tavily.com/documentation/api-reference/endpoint/search)

当前推荐两种方式：

1. Tavily Tool 节点：在 Dify 插件市场安装 `Tavily`，在 `Tools / Tavily / Authentication` 填 API Key，然后把 Tool 节点放到 `product_recommendation` 分支。
2. HTTP Request 节点：不依赖 Dify Tool 版本，直接请求 `https://api.tavily.com/search`，用 Dify 环境变量保存 `TAVILY_API_KEY`。

不建议让 Agent 自动决定是否调用工具。后端期望 Dify 每次返回唯一 JSON 对象；Agent 自动工具调用容易把“工具说明”“联网失败解释”直接回复给飞书用户，之前的“加个联网插件”就是这类问题。

## Dify 环境变量

在 Dify 应用的环境变量里新增：

```yaml
environment_variables:
- description: Tavily Search API Key，用于采购助手联网商品搜索
  id: env-tavily-api-key
  name: TAVILY_API_KEY
  selector:
  - env
  - TAVILY_API_KEY
  value: ''
  value_type: secret
```

导入 DSL 后需要在 Dify 页面里手动填密钥。不要把真实 Key 写进仓库。

## HTTP Request 节点配置

节点名称建议叫 `联网商品搜索`，只接到 `task_type=product_recommendation` 的分支，避免用户说“你好”也触发搜索。

```yaml
- data:
    authorization:
      config: null
      type: no-auth
    body:
      data: |
        {
          "query": "{{#start.raw_text#}}{{#start.text#}} 采购 商品 规格 价格 京东 1688 淘宝 抖音",
          "search_depth": "basic",
          "topic": "general",
          "max_results": 5,
          "include_answer": false,
          "include_images": true,
          "include_raw_content": false
        }
      type: json
    desc: 仅在 product_recommendation 分支调用。搜索结果只能作为采购候选参考，不作为最终报价。
    headers: |
      Content-Type:application/json
      Authorization:Bearer {{#env.TAVILY_API_KEY#}}
    method: post
    params: ''
    selected: false
    timeout:
      max_connect_timeout: 10
      max_read_timeout: 30
      max_write_timeout: 30
    title: 联网商品搜索
    type: http-request
    url: https://api.tavily.com/search
    variables: []
  height: 110
  id: web-search
  position:
    x: 420
    y: 150
  positionAbsolute:
    x: 420
    y: 150
  selected: false
  sourcePosition: right
  targetPosition: left
  type: custom
  width: 244
```

如果你不用 Tavily，而是用其他搜索 API，只要最后给 LLM 的结果里包含标题、摘要、链接、图片和价格线索即可。建议统一成下面格式再交给 LLM：

```json
{
  "mockData": false,
  "products": [
    {
      "itemName": "商品或页面标题",
      "specModel": "规格型号，无法判断留空",
      "unitPrice": 99.99,
      "ecommercePlatform": "京东/1688/淘宝/抖音/其他",
      "ecommerceUrl": "来源链接",
      "imageUrl": "图片链接",
      "evidenceSnippet": "搜索摘要或页面片段"
    }
  ],
  "sources": [
    {
      "title": "来源标题",
      "url": "来源链接",
      "content": "摘要"
    }
  ]
}
```

## 条件分支

在 `开始` 后面增加 `是否需要联网搜索` IF/ELSE：

```yaml
- data:
    cases:
    - case_id: 'true'
      conditions:
      - comparison_operator: is
        id: condition-product-recommendation
        value: product_recommendation
        varType: string
        variable_selector:
        - start
        - task_type
      id: 'true'
      logical_operator: and
    desc: 只有商品推荐任务才联网搜索。
    selected: false
    title: 是否需要联网搜索
    type: if-else
  height: 126
  id: if-web-search
  position:
    x: 340
    y: 300
  positionAbsolute:
    x: 340
    y: 300
  selected: false
  sourcePosition: right
  targetPosition: left
  type: custom
  width: 244
```

连线建议：

```text
开始 -> 是否需要联网搜索
是否需要联网搜索 true -> 联网商品搜索 -> 采购AI判断
是否需要联网搜索 false -> 采购AI判断
采购AI判断 -> 直接回复
```

如果当前 Dify 版本不支持多个分支汇入同一个 LLM 节点，就复制一个 `采购AI判断-联网版` LLM 节点，true 分支走联网版，false 分支走原节点。两个 LLM 节点的输出结构必须保持完全一致。

## LLM 提示词补充

在 `采购AI判断` 的用户提示词里，把联网结果一起传入：

```text
联网搜索结果JSON：{{#web-search.body#}}
```

在系统提示词的 `product_recommendation 规则` 增加：

```text
- 如果存在“联网搜索结果JSON”，优先从其中的 results/products/sources 数组提取商品候选；必须保留来源链接到 ecommerceUrl。
- 联网搜索结果只是公开网页参考，不等于公司最终采购报价；无法从结果中确认大于 0 的单价时不要创建候选，不要猜价格。
- 搜索结果不能证明商品和需求匹配时，candidates 输出空数组，并追问用户补充链接、图片、规格或用途。
- 不要把“安装插件、添加联网插件、配置 API Key”回复给飞书用户；配置问题只记录到 Dify/后台日志。
```

## 测试用例

发布 Dify 应用后，在平台后台保持：

```text
dify_app_type=chatflow
dify_api_base_url=https://你的 Dify 域名/v1
dify_api_key=当前 Chatflow 的应用 API Key
```

用飞书机器人测试：

```text
采购 2 个 A4 文件夹，用于机房资料归档
```

期望：

- 后台 AI 助手消息日志里能看到 Dify 请求、原始响应、解析结果。
- Dify 原始响应里 `candidates` 至少包含商品名、数量、用途、来源链接。
- 用户回复“可以提交了 / 确认提交”后，系统创建正式采购申请单，后续仍走楼长审核和原采购流程。
