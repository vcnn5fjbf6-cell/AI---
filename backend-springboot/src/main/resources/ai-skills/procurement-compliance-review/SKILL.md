---
name: procurement-compliance-review
description: "按照采购原因统一标准审查每条采购原因，并输出结构化审批建议。"
---

# Procurement Compliance Review

版本：1.3.0

## 审查目标

仅根据系统提供的“采购原因”文字和系统附加的《采购原因统一标准》判断理由是否充分。审查结果只作为审批参考，不执行通过、退回、终止或任何数据库操作。

## 审查范围

- 允许读取：每条采购明细的采购原因。
- 禁止读取或要求补充：申请标题、采购类型、部门、金额、紧急程度、需求日期、商品名称、规格、数量、单价和商品链接。
- 上述禁止字段未提供时，不得判定为信息缺失。

## 审查步骤

1. 接受系统已经完成的字段、数量、价格和金额硬校验，不重复进行确定性校验。
2. 逐条按照《采购原因统一标准》判断核心项和条件项，并为每个输入行号单独给出一条意见。
3. 对“使用”“备用”“办公使用”“工作需要”“领导要求”“满足实际使用需求”“保障相关工作正常开展”等笼统表述，建议补充具体原因。
4. 只有采购原因本身明确显示个人消费、非业务用途、规避流程或其它高风险事实时，才建议退回。
5. 不根据采购原因之外的信息推测风险，也不追问审查范围之外的字段。

## 结论规则

- `APPROVE`：采购原因符合《采购原因统一标准》，没有实质性理由缺口。
- `SUPPLEMENT`：采购原因笼统，缺少统一标准要求的核心项或已触发的条件项。
- `RETURN`：采购原因本身明确显示非业务用途、规避流程或其它高风险事实。

## 输出格式

只输出 JSON 对象：

```json
{
  "decisionSuggestion": "APPROVE|SUPPLEMENT|RETURN",
  "riskLevel": "LOW|MEDIUM|HIGH",
  "summary": "面向审批人的简洁中文结论",
  "issues": ["最多三条审查关注点"],
  "requiredInformation": ["只列出采购原因需要补充的内容"],
  "itemReviews": [
    {
      "lineNo": 1,
      "decisionSuggestion": "APPROVE|SUPPLEMENT|RETURN",
      "opinion": "仅针对本行采购原因的简洁中文意见",
      "missingFields": ["USE_SCENE|CURRENT_IMPACT|NECESSITY|QUANTITY_BASIS|URGENCY_BASIS"]
    }
  ],
  "confidence": 0.0,
  "improvementSuggestions": [
    {
      "targetType": "SKILL|KNOWLEDGE_BASE",
      "targetCode": "当前上下文中明确存在的编码",
      "proposedContent": "可完整替换当前正文的全文",
      "rationale": "发现的具体、可复用规则缺口"
    }
  ]
}
```

`itemReviews` 必须覆盖系统提供的每个采购原因行号，每行只能出现一次，不能把多条原因合并成一条意见。`missingFields` 只列出《采购原因统一标准》中的实际缺项；建议通过或退回时为空数组。普通理由不得出现 `QUANTITY_BASIS` 或 `URGENCY_BASIS`，除非原文主动触发对应条件。

没有具体且可复用的内容缺口时，`improvementSuggestions` 必须为空数组。改进建议只能进入管理员审核队列，不能自行生效。不要输出审批动作、数据库字段、技术错误或模型思考过程。不要因为没有知识库命中就编造公司制度。

审查时必须以系统附加的 [采购原因统一标准](../procurement-purpose-standard.md) 为唯一的理由充分性标准。
