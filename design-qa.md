# 三套采购原型 Design QA

## 对照范围

- 源设计：
  - `C:/Users/10301/.codex/visualizations/2026/07/29/019faef9-ef30-77d1-9c95-5ab7214beeb2/procurement-meeting-20260812/procurement-ux-prototype/reference/direction-1.png`
  - `C:/Users/10301/.codex/visualizations/2026/07/29/019faef9-ef30-77d1-9c95-5ab7214beeb2/procurement-meeting-20260812/procurement-ux-prototype/reference/direction-2.png`
  - `C:/Users/10301/.codex/visualizations/2026/07/29/019faef9-ef30-77d1-9c95-5ab7214beeb2/procurement-meeting-20260812/procurement-ux-prototype/reference/direction-3.png`
- 实现页面：
  - `frontend-vue/src/pages/procurementVnet/MyOrders.vue`
  - `frontend-vue/src/pages/procurementVnet/ProcurementRequestForm.vue`
  - `frontend-vue/src/pages/workbench/WorkbenchPage.vue`
- 源设计画布：1487 × 1058，1x。
- 浏览器实测：909 × 646、789 × 444；窄屏实测 432 × 555，1x。
- 状态：订单待收货、采购申请 AI 预审完成、换品待确认且已选择替换商品。
- 全局视图：`tmp/qa-compare-orders.png`、`tmp/qa-compare-request.png`、`tmp/qa-compare-replacement.png`。
- 聚焦视图：`tmp/qa-mobile-check.png`、`tmp/qa-mobile-replacement.png`。

## Mandatory comparison pass

- 字体与排版：沿用系统既有中文字体栈和标题/正文层级；订单号、金额、状态和 SLA 保持高对比，无截断关键信息。
- 间距与布局：三套页面均保留原型的信息顺序和主次分组。桌面使用两栏或左右对比，窄屏改为纵向排列；卡片圆角、边框和间距与现有 VNet 视觉令牌一致。
- 视口韧性：789px 下订单弹窗不再溢出，432px 下进度和页签可横向滚动、换品证据卡纵向排列，未发现页面级水平溢出或控件重叠。
- 颜色与状态：蓝色用于主流程/选择，绿色用于已选择，橙色用于 SLA/待补充，红色用于重要差异；禁用和次级信息对比度符合现有系统令牌。
- 图片与资产：订单接口没有可信商品图片字段，因此未伪造占位商品图；所有可见操作图标均使用 Element Plus 图标或既有组件。
- 文案：保留业务数据和行动项，移除“温馨提示”等泛化前缀；AI 预审明确为建议，不改变保存、提交或审批状态。
- 交互状态：实测订单页签切换、采购申请新增明细与 AI 预审、换品原/替换卡选择；选中、加载、空态和禁用态沿用既有组件。
- 无障碍：交互卡片使用 `button` 和 pressed 状态，页签有导航标签，表单沿用现有可访问标签和焦点样式；窄屏触控区域未缩小。
- AI 快捷产物检查：未增加装饰性图形、伪造头像、自绘 SVG 或假商品图。

## 修复记录

1. P1 / 响应式：订单与审批弹窗使用固定 1120/1180px，789px 视口出现水平溢出。改为 `min(目标宽度, 100vw - 32px)`，并复测通过。
2. P2 / 布局：订单商品表的最小列宽会把弹窗内容撑宽。为 cockpit、tab panel 和 table 补充可收缩宽度，并在 900px 以下将命令栏与洞察区改为单列，复测通过。
3. P2 / 内容：换品页存在泛化操作说明和“温馨提示”前缀。改为“每项选择后提交”和直接截止规则，复测通过。

## 结论

最终结果：passed
