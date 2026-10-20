# VNET 组件化前端落地说明

## 技术现状

- 当前工程为 Vue 3 + Vite + Element Plus。
- 规划文档建议为 Vue 2 + VNET UI / VNET UI Business；本次先在现有工程内用 Element Plus 组件对齐 VNET 组件语义。
- 后续接入公司私有组件库时，重点替换选择类和流程类组件，不需要重排页面结构。

## 双系统路由

### 维保管理

| 路由 | 页面 | 组件重点 |
|---|---|---|
| `/maintenance/dashboard` | 维保总览 | 指标卡、风险表、覆盖热力、快捷筛选 |
| `/maintenance/sources` | 维保来源 | 来源表、状态标签、详情弹窗 |
| `/maintenance/upload` | 合同 / 订单上传 | 上传、类型选择、解析步骤、结果弹窗 |
| `/maintenance/review/:parseId` | AI 拆解确认 | 原文证据、字段表单、设备清单、确认入库 |
| `/maintenance/assets` | 设备维保台账 | 组合筛选、台账表格、分页 |
| `/maintenance/renewal` | 续签 / 续采跟踪 | 时间线、任务表、提醒弹窗 |
| `/maintenance/settings/people` | 人员信息配置 | 人员选择、总部权限、机房多选、专业多选 |
| `/maintenance/settings/rooms` | 机房分类配置 | 机房分类表、新增编辑弹窗、区域筛选 |

### 采购管理

| 路由 | 页面 | 组件重点 |
|---|---|---|
| `/procurement/dashboard` | 采购总览 | 指标卡、最新申请、流程步骤 |
| `/procurement/requests` | 采购申请 | 组合筛选、申请表格、详情弹窗 |
| `/procurement/requests/create` | 新建采购申请 | 头信息表单、明细表格 |
| `/procurement/approval` | 审批操作 | 待办表格、审批弹窗、审批记录时间线 |
| `/procurement/suppliers` | 供应商管理 | 供应商筛选、目录表格、状态标签 |
| `/procurement/quotes` | 供应商报价 | 报价表格、比价摘要 |

## 本地组件映射

| VNET 规划组件 | 当前实现 |
|---|---|
| `ElCardWidget` / `ElCard` | `VnetMetricCard` + `VnetPanel` |
| `ElSearch` | `ElForm` inline 组合筛选 |
| `ElSelectMaDataCenter` | `ElSelect` + `dataCenterOptions` |
| `ElSelectMaPerson` | `ElSelect` + `personOptions` |
| `ElFlowTask` / `ElFlowTaskChart` | `ElTimeline` + 任务表 |
| `ElUploadDrag` | `ElUpload` drag 模式 |

## 视觉原则

- 企业后台优先：紧凑信息密度、清晰表格、稳定筛选区、明确操作按钮。
- 控件半径控制在 6-8px，避免营销页式大圆角和装饰化背景。
- 状态色固定为蓝、绿、橙、红，分别表达主操作、正常、临期、风险。
- 页面结构优先服务表单、表格、筛选、弹窗和页签，避免无业务含义的展示卡片。

## 后续接入点

1. 将 `src/data/maintenance.ts` 替换为真实接口层。
2. 将 `src/data/procurement.ts` 替换为已有采购接口层。
3. 将机房、人员、订单、流程选择控件替换为 VNET UI Business 组件。
4. 接入合同上传与 AI 拆解后端，保留 `/maintenance/review/:parseId` 作为人工确认入口。
5. 接入飞书提醒或任务流系统，替换续签页面的模拟提醒动作。
