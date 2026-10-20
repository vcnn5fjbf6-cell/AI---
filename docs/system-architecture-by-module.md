# 酒仙桥区域运维平台架构图（按板块）

> 说明：本文按系统实际板块拆分架构，便于给管理人员、开发人员或外部系统对接方说明系统边界。图中“已上线”表示当前代码已有路由或后端服务；“扩展入口/预留”表示建议扩展点，不代表当前已经全部上线；“可选依赖”表示配置后可接入，不是主流程必需。

## 1. 系统总览

这个图说明整个平台从用户入口、前端、后端、外部集成到数据存储的整体关系。

```mermaid
flowchart TB
    subgraph U["用户与外部入口"]
        U1["内部员工 / 楼长 / 经理 / 管理员"]
        U2["供应商账号 / 外部供应商"]
        U3["飞书机器人 / 飞书卡片（已上线）"]
        U4["外部系统（扩展入口/预留：人员权限 API）"]
    end

    subgraph F["前端入口层"]
        F1["Web SPA Vue3（已上线）"]
        F2["飞书内嵌 Web / 机器人入口（已上线）"]
        F3["微信小程序（扩展入口/预留）"]
        F4["大屏看板（扩展入口/预留）"]
        F5["H5 移动端（扩展入口/预留）"]
    end

    subgraph PAGE["Web 已上线页面板块"]
        P1["工作台"]
        P2["平台管理"]
        P3["采购管理"]
        P4["维保管理"]
        P5["灾害预警"]
        P6["供应商报价"]
    end

    subgraph B["后端层 Spring Boot"]
        B1["认证与权限"]
        B2["工作台与统一审批"]
        B3["采购申请与流程引擎"]
        B4["月度需求 / 供应商报价 / 中标确认"]
        B5["飞书卡片 / 飞书机器人"]
        B6["Dify / AI 助手"]
        B7["灾害天气预警"]
        B8["维保与台账"]
        B9["系统管理 / 日志 / 数据库管理"]
    end

    subgraph E["外部服务"]
        E1["飞书开放平台"]
        E2["Dify / 大模型网关"]
        E3["和风天气 API"]
        E4["电商/商品搜索接口（可选）"]
    end

    subgraph D["数据与文件"]
        D1["MySQL"]
        D2["Redis"]
        D3["Qdrant（AI向量，可选）"]
        D4["本地附件目录"]
    end

    U1 --> F
    U3 --> F2
    U2 --> P6
    U4 -. "规划：签名鉴权调用" .-> B1
    F1 --> PAGE
    F2 --> B5
    PAGE --> B
    B5 <--> E1
    B6 <--> E2
    B7 <--> E3
    B4 -. "商品推荐/链接解析" .-> E4
    B --> D1
    B --> D2
    B6 -. "向量检索" .-> D3
    B9 --> D4
```

## 2. 前端功能板块

这个图说明用户在页面上看到的主要菜单和页面分组。

```mermaid
flowchart LR
    APP["AppLayout 主框架"]

    APP --> W["工作台（已上线）"]
    W --> W1["统一请求队列"]
    W --> W2["审批处理"]
    W --> W3["公告与关注"]

    APP --> P["平台管理（已上线）"]
    P --> P1["我的权限"]
    P --> P2["用户管理"]
    P --> P3["角色管理"]
    P --> P4["权限管理"]
    P --> P5["平台集成设置"]
    P --> P6["采购规则设置"]
    P --> P7["部门管理"]
    P --> P8["错误台账"]
    P --> P9["AI助手消息日志"]
    P --> P10["导入文件记录"]
    P --> P11["意见反馈"]
    P --> P12["数据库管理"]

    APP --> C["采购管理（已上线）"]
    C --> C1["采购看板"]
    C --> C2["本月需求"]
    C --> C3["采购申请"]
    C --> C4["个人劳保申领"]
    C --> C5["我的订单"]
    C --> C6["审批操作"]
    C --> C7["供应商管理"]
    C --> C8["推荐供应商审批"]
    C --> C9["供应商报价"]
    C --> C10["已售管理"]

    APP --> M["维保管理（已上线）"]
    M --> M1["维保总览"]
    M --> M2["维保来源"]
    M --> M3["合同/订单上传"]
    M --> M4["AI拆解确认"]
    M --> M5["设备维保台账"]
    M --> M6["续签/续采跟踪"]
    M --> M7["人员与机房配置"]

    APP --> DW["灾害预警（已上线）"]
    DW --> DW1["预警记录"]
    DW --> DW2["预警设置"]

    APP -.-> EXT["扩展入口/预留"]
    EXT -.-> EXT1["微信小程序"]
    EXT -.-> EXT2["H5 移动端"]
    EXT -.-> EXT3["大屏看板"]
```

## 3. 后端模块板块

这个图说明 Spring Boot 后端按业务域拆分后的模块边界。

```mermaid
flowchart TB
    API["HTTP API / Controller"]

    API --> AUTH["auth 认证模块"]
    AUTH --> AUTH1["账号密码登录"]
    AUTH --> AUTH2["飞书 OAuth 登录"]
    AUTH --> AUTH3["JWT / 当前用户信息"]

    API --> SYS["system 系统用户模块"]
    SYS --> SYS1["用户管理"]
    SYS --> SYS2["角色管理"]
    SYS --> SYS3["权限分配"]
    SYS --> SYS4["飞书组织同步"]

    API --> PLATFORM["platform 平台管理模块"]
    PLATFORM --> PF1["平台入口权限"]
    PLATFORM --> PF2["错误台账"]
    PLATFORM --> PF3["AI助手消息日志"]
    PLATFORM --> PF4["导入文件记录"]
    PLATFORM --> PF5["意见反馈"]
    PLATFORM --> PF6["数据库管理"]
    PLATFORM --> PF7["灾害天气预警"]

    API --> WORKBENCH["workbench 工作台模块"]
    WORKBENCH --> WB1["统一请求队列"]
    WORKBENCH --> WB2["审批详情"]
    WORKBENCH --> WB3["审批动作入口"]

    API --> PROCUREMENT["procurement 采购模块"]
    PROCUREMENT --> PR1["采购申请"]
    PROCUREMENT --> PR2["审批流程"]
    PROCUREMENT --> PR3["月度需求"]
    PROCUREMENT --> PR4["供应商报价"]
    PROCUREMENT --> PR5["中标确认"]
    PROCUREMENT --> PR6["订单/收货/售后"]
    PROCUREMENT --> PR7["个人劳保"]
    PROCUREMENT --> PR8["采购看板 BI"]

    API --> SUPPLIER["supplier 供应商模块"]
    SUPPLIER --> SP1["供应商档案"]
    SUPPLIER --> SP2["临时供应商推荐"]
    SUPPLIER --> SP3["供应商账号绑定"]

    API --> INTEGRATION["integration 集成模块"]
    INTEGRATION --> I1["飞书审批卡片"]
    INTEGRATION --> I2["飞书长连接回调"]
    INTEGRATION --> I3["飞书消息推送"]

    API --> MAINT["maintenance 维保模块"]
    MAINT --> MA1["维保台账"]
    MAINT --> MA2["合同/订单解析"]
    MAINT --> MA3["续签跟踪"]
```

## 4. 采购核心流程板块

这个图说明普通采购、紧急采购、AI 创建采购申请、月度汇总、供应商报价和中标确认如何走同一条主流程。

```mermaid
flowchart TB
    A["采购需求来源"]
    A --> A1["人工新建采购申请"]
    A --> A2["飞书 AI 助手生成候选"]
    A --> A3["批量导入 Excel"]
    A --> A4["个人劳保申领"]

    A1 --> B["PurchaseRequestService 创建申请"]
    A2 --> B
    A3 --> B
    A4 --> B

    B --> C{"普通采购 / 紧急采购"}
    C -->|普通采购| D["楼长/经理等普通审批节点"]
    C -->|紧急采购| E["紧急采购审批节点"]

    D --> F["审批通过"]
    E --> F
    F --> G["进入月度需求池 / 汇总"]
    G --> H["供应商报价"]
    H --> I["报价审核 / 比价"]
    I --> J["确认中标"]
    J --> K["供应商确认"]
    K --> L["生成订单 / 收货 / 发票 / 售后"]

    D --> R["整单退回或单项驳回"]
    E --> R
    R --> B
```

### 说明

- AI 助手只是输入采集层，不能绕过 `PurchaseRequestService` 和原审批流程。
- 普通采购和紧急采购仍走原有流程节点，不能新建一条旁路。
- 单项驳回本质是“驳回部分明细，其他明细继续流转”，不能当成独立卡片状态处理。

## 5. 飞书与 AI 板块

这个图说明飞书机器人、Dify、AI 候选、审批卡片、供应商卡片之间的关系。

```mermaid
flowchart TB
    FS["飞书开放平台"]

    subgraph BOT["飞书机器人消息"]
        B1["用户发送文字/图片/链接/Excel"]
        B2["FeishuBotController 接收事件"]
        B3["下载文件 / 解析链接 / 提取文本"]
        B4["DifyProcurementClient 意图判断"]
        B5{"是否采购需求"}
        B6["ProcurementAiSession 草稿/会话"]
        B7["ProcurementAiCandidate 候选明细"]
        B8["候选卡片：确认/撤回/刷新"]
    end

    subgraph CARD["飞书审批卡片"]
        C1["本地心跳扫描待审批任务"]
        C2["生成审批卡片"]
        C3["楼长/经理点击同意/驳回"]
        C4["长连接或回调接收按钮动作"]
        C5["调用 WorkflowService 写审批结果"]
    end

    subgraph SUP["供应商卡片"]
        S1["报价任务卡片"]
        S2["下载询价单"]
        S3["上传报价 Excel"]
        S4["中标确认卡片"]
        S5["供应商确认/放弃/售后"]
    end

    FS --> B1 --> B2 --> B3 --> B4 --> B5
    B5 -->|不是采购| B6
    B5 -->|是采购| B7 --> B8
    B8 -->|"确认全部提交"| PR["正式采购申请"]

    PR --> C1 --> C2 --> FS
    FS --> C3 --> C4 --> C5

    PR --> S1 --> FS
    FS --> S2 --> S3 --> S4 --> S5
```

### 说明

- Dify 负责意图判断、候选生成、AI审核意见建议。
- 后端必须保存 Dify 请求、响应、解析结果，方便在“AI助手消息日志”里排障。
- 飞书卡片只是交互入口，最终审批动作必须回到系统工作流服务。

## 6. 灾害天气预警板块

这个图说明灾害预警从配置、定时任务、天气接口、规则判断到飞书值班群推送的路径。

```mermaid
flowchart TB
    SET["灾害预警设置"]
    SET --> S1["区域/中心点/城市 Code"]
    SET --> S2["和风天气 Key"]
    SET --> S3["值班群 chat_id"]
    SET --> S4["重保天气规则"]
    SET --> S5["湿球温度运行建议规则"]

    JOB["定时任务"]
    JOB --> J1["每日 09:00 气象预报"]
    JOB --> J2["每周一 09:05 本周预报"]
    JOB --> J3["预警检查任务"]

    J1 --> Q["和风天气 API"]
    J2 --> Q
    J3 --> Q

    Q --> R["规则引擎"]
    R --> R1["预警等级判断"]
    R --> R2["三级以上重保通报"]
    R --> R3["湿球温度建议"]
    R --> R4["卡片颜色随预警颜色变化"]

    R --> LOG["天气/预警记录"]
    R --> CARD["飞书天气预警卡片"]
    CARD --> GROUP["值班群"]
```

### 说明

- 天气地点不应写死在代码里，应从后台设置读取。
- 重保规则、预警颜色、湿球温度建议都应可在后台维护。
- 测试按钮用于手动触发拉取和推送，方便上线前验证。

## 7. 数据存储板块

这个图说明当前系统主要数据落点。

```mermaid
flowchart LR
    subgraph DB["MySQL 主业务库"]
        D1["用户/角色/权限"]
        D2["平台入口权限"]
        D3["采购申请/明细/审批记录"]
        D4["月度需求/供应商报价/中标"]
        D5["AI 会话/候选/消息日志"]
        D6["飞书卡片日志"]
        D7["劳保申领/领用记录"]
        D8["灾害天气设置/预警记录"]
        D9["反馈/导入/错误台账"]
    end

    subgraph CACHE["Redis"]
        R1["登录态辅助"]
        R2["短期缓存"]
        R3["定时任务临时状态"]
    end

    subgraph VECTOR["Qdrant（可选）"]
        Q1["AI 语义检索"]
        Q2["采购知识/历史候选向量"]
    end

    subgraph FILE["文件存储"]
        F1["意见反馈附件"]
        F2["导入 Excel 临时/留存文件"]
        F3["报价/询价导出文件"]
    end
```

## 8. 权限与外部系统开放 API（规划板块）

这个图说明后续如果给外部系统调用人员权限，建议放在独立开放 API 层，避免直接暴露后台管理接口。

```mermaid
flowchart TB
    EXT["外部系统"]
    EXT --> GW["Nginx / API 网关"]
    GW --> AUTH["OpenAPI 鉴权拦截器"]
    AUTH --> A1["app_key"]
    AUTH --> A2["HMAC 签名"]
    AUTH --> A3["IP 白名单"]
    AUTH --> A4["接口范围 scope"]

    AUTH --> API["/openapi/v1/permission/check"]
    API --> USER["系统用户服务"]
    API --> ROLE["角色权限服务"]
    API --> LOG["OpenAPI 调用审计日志"]

    USER --> DB["MySQL"]
    ROLE --> DB
    LOG --> DB
```

### 说明

- 外部系统只建议查“某人是否有某权限”，不要直接开放改角色、删用户、改权限。
- 每个外部系统一个 `app_key/app_secret`，并记录调用日志。
- 返回数据要最小化，不返回飞书 open_id、手机号、密码、token 等敏感字段。

## 9. 板块职责速查

| 板块 | 主要页面/入口 | 主要后端模块 | 当前状态 | 作用 |
|---|---|---|---|---|
| 工作台 | `/workbench` | `workbench` | 已上线 | 统一查看待办、已办、我发起、审批处理。 |
| 平台管理 | `/platform/*` | `platform`, `system` | 已上线 | 管用户、角色、入口权限、设置、日志、反馈、数据库。 |
| 采购管理 | `/procurement/*` | `procurement`, `supplier` | 已上线 | 采购申请、审批、月度汇总、报价、中标、订单、劳保。 |
| 供应商 | `/procurement/quotes`, `/procurement/sold-orders` | `supplier`, `procurement/order` | 已上线 | 报价、确认中标、发货、售后、发票相关协作。 |
| 飞书集成 | 飞书机器人/卡片 | `integration/feishu`, `procurement/assistant` | 已上线 | 飞书登录、机器人消息、审批卡片、供应商卡片。 |
| AI 助手 | 飞书机器人、AI日志 | `procurement/assistant`, `platform/aimessagelog` | 已上线 | 意图识别、候选生成、AI审核意见、日志追踪。 |
| 灾害预警 | `/disaster-weather/*` | `platform/disaster` | 已上线 | 天气预报、重保预警、湿球温度建议、飞书值班群推送。 |
| 维保管理 | `/maintenance/*` | `maintenance` | 已上线 | 维保来源、合同上传、AI拆解、设备台账、续签。 |
| 数据与审计 | 数据库管理、日志页面 | `platform/database`, `platform/errorlog` | 已上线 | 管理数据、查看错误、追踪导入和 AI 消息。 |
| 飞书工作台入口 | 飞书内嵌 Web / 机器人 / 卡片 | `auth`, `procurement/assistant`, `workflow` | 已上线 | 通过飞书登录、机器人消息和卡片按钮进入系统能力。 |
| 微信小程序 | 暂无前端路由 | 暂无独立模块 | 扩展入口/预留 | 未来可做轻量移动端入口，目前不作为已上线能力展示。 |
| H5 移动端 | 暂无独立 H5 路由 | 复用现有 API | 扩展入口/预留 | 未来可做移动审批、申领、收货等轻量页面。 |
| 大屏看板 | 暂无独立大屏路由 | 可复用 `dashboard/BI` | 扩展入口/预留 | 未来可面向值班室或管理层展示统计数据。 |
| 人员权限开放 API | `/openapi/v1/permission/check`（规划） | `openapi`, `system`, `platform` | 扩展入口/预留 | 未来给外部系统查询人员权限，需单独签名鉴权和审计。 |
| 商品搜索 / Qdrant | 配置型外部依赖 | `integration`, `procurement/assistant` | 可选依赖 | 用于商品推荐、语义检索等增强能力，不是采购主流程必需。 |
