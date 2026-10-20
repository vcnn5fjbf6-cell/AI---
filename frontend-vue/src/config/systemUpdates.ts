export type SystemUpdateChangeType = "FIX" | "FEATURE" | "IMPROVEMENT";

export interface SystemUpdateChange {
  type: SystemUpdateChangeType;
  title: string;
  description: string;
}

export interface SystemUpdateRecord {
  version: string;
  releaseDate: string;
  title: string;
  summary: string;
  status: "RELEASED";
  changes: SystemUpdateChange[];
}

export const SYSTEM_UPDATE_RECORDS: SystemUpdateRecord[] = [
  {
    version: "v20261020.1",
    releaseDate: "2026-10-20",
    title: "考勤加班调休统计与人员状态规范",
    summary: "考勤页新增人员加班、请假扣减和可调休时长汇总；员工状态统一为在职或离职。",
    status: "RELEASED",
    changes: [
      {
        type: "FEATURE",
        title: "加班与调休时长自动汇总",
        description: "按员工累计已批准的加班小时数，并将已批准请假按每天 8 小时折算扣减，展示加班总时长、请假扣减和可调休时长。"
      },
      {
        type: "IMPROVEMENT",
        title: "员工状态统一为在职或离职",
        description: "编辑和导入时仅使用在职、离职两种状态；历史状态“离开”自动归一为“离职”。"
      }
    ]
  },
  {
    version: "v20260827.2",
    releaseDate: "2026-08-27",
    title: "替换商品原价比价修复",
    summary: "替换商品报价不再通过原商品占位低价取得最低价排序，并保留供应商原始提交价供审计。",
    status: "RELEASED",
    changes: [
      {
        type: "FIX",
        title: "调整替换报价的原商品有效价",
        description: "报价复核前按其他有效供应商原商品第二高单价加 1 元计算；仅一家可比报价时按该报价加 1 元，无可比报价时不调整。"
      },
      {
        type: "IMPROVEMENT",
        title: "保留供应商原始报价",
        description: "供应商填写的原商品单价独立留存，系统调整有效比价单价后同步重算行金额和报价总额，便于后续核对。"
      }
    ]
  },
  {
    version: "v20260827.1",
    releaseDate: "2026-08-27",
    title: "供应商单项放弃比价修复",
    summary: "供应商放弃个别采购明细时，其余有效报价可以继续完成比价。",
    status: "RELEASED",
    changes: [
      {
        type: "FIX",
        title: "单项放弃不再阻塞整单比价",
        description: "整单仍要求至少 3 家供应商提交；某项使用剩余有效报价比价，仅在该有效明细完全无人报价时阻止提交。"
      },
      {
        type: "FIX",
        title: "取消明细不参与中标计算",
        description: "已单项驳回的采购明细继续排除在报价校验和中标计算之外。"
      }
    ]
  },
  {
    version: "v20260826.4",
    releaseDate: "2026-08-26",
    title: "鑫方盛商品编号导入支持",
    summary: "鑫方盛替换商品报价可直接填写内部商品编号，系统按供应商身份转换为对应商品页。",
    status: "RELEASED",
    changes: [
      {
        type: "FIX",
        title: "支持鑫方盛内部商品编号",
        description: "手工报价和 Excel 批量导入均可填写鑫方盛纯数字商品编号，系统自动生成 vip.xfs.com 商品详情页。"
      },
      {
        type: "IMPROVEMENT",
        title: "按供应商隔离编号规则",
        description: "只有当前账号绑定的鑫方盛供应商可使用该编号规则，其他供应商的纯数字仍会被拦截。"
      },
      {
        type: "FIX",
        title: "可选成色字段按全新导入",
        description: "报价模板中的原商品和替换商品成色留空时默认按全新导入；填写二手时继续要求补充说明。"
      }
    ]
  },
  {
    version: "v20260825.1",
    releaseDate: "2026-08-25",
    title: "部门审批人选择校验修复",
    summary: "修复部门管理中已经选择审批人但仍提示未配置、无法保存的问题。",
    status: "RELEASED",
    changes: [
      {
        type: "FIX",
        title: "审批人选择立即生效",
        description: "V1-V8 任一节点选择或清空审批人后，待配置数量和保存校验立即使用页面最新结果。"
      }
    ]
  },
  {
    version: "v20260824.1",
    releaseDate: "2026-08-24",
    title: "订单付款流程展示补齐",
    summary: "订单详情补齐签收后的付款资料节点，并收紧订单资料上传权限、增加逐单批量上传。",
    status: "RELEASED",
    changes: [
      {
        type: "IMPROVEMENT",
        title: "补齐签收后续节点",
        description: "订单进度新增“发票/验收单提交”和“已提交付款”，并同步当前责任方、下一步与最新动态。"
      },
      {
        type: "IMPROVEMENT",
        title: "复用订单资料确认结果",
        description: "发票和验收单有效提交后进入资料确认，两类资料均确认后显示已提交付款，不新增平行处理入口。"
      },
      {
        type: "FEATURE",
        title: "发票与签收单批量上传",
        description: "供应商归档页可按订单批量选择并提交发票或签收单；上传仅限最高管理员、本订单供应商和跨供应商最高监督账号，其他账号不能上传。"
      }
    ]
  },
  {
    version: "v20260820.4",
    releaseDate: "2026-08-20",
    title: "供应商已售全量管理权限",
    summary: "供应商侧最高监督账号可跨供应商查看并处理已售订单，普通供应商仍限本公司数据。",
    status: "RELEASED",
    changes: [
      {
        type: "FIX",
        title: "修复已售数据范围",
        description: "独立全量权限同时生效于列表、详情、归档导出和售后列表，不再固定查询监督账号绑定的测试供应商。"
      },
      {
        type: "FEATURE",
        title: "跨供应商处理已售业务",
        description: "可导出订单、查看并上传发票和送货单、处理供应商侧售后；不扩大到采购审批、系统管理或原始报价修改。"
      }
    ]
  },
  {
    version: "v20260820.3",
    releaseDate: "2026-08-20",
    title: "退回申请支持主动关闭",
    summary: "申请人可将确认不再采购的已退回申请彻底关闭，避免长期停留在待补充列表。",
    status: "RELEASED",
    changes: [
      {
        type: "FEATURE",
        title: "关闭已退回采购申请",
        description: "采购申请列表新增关闭操作；关闭前二次确认，关闭后不可编辑或再次提交，审批历史继续保留。"
      },
      {
        type: "IMPROVEMENT",
        title: "关闭状态统一展示",
        description: "申请列表、详情、工作台和审批记录统一显示已关闭，并停止继续推送该申请的驳回明细提醒。"
      }
    ]
  },
  {
    version: "v20260820.2",
    releaseDate: "2026-08-20",
    title: "采购原因优化与预审统一标准",
    summary: "AI优化、填写框架和AI预审共用同一采购原因标准，避免优化后仍被另一套规则判定为待补充。",
    status: "RELEASED",
    changes: [
      {
        type: "FIX",
        title: "统一采购原因判定口径",
        description: "普通采购统一检查使用场景、现状及影响和采购必要性；仅在原文主动表明批量或紧急情形时检查对应依据。"
      },
      {
        type: "IMPROVEMENT",
        title: "缺失事实不再由套话代替",
        description: "AI优化遇到信息不足时追加与预审一致的待补框架，申请人补全前仍不能保存或提交。"
      }
    ]
  },
  {
    version: "v20260820.1",
    releaseDate: "2026-08-20",
    title: "采购原因 AI 优化与逐条采用修复",
    summary: "采购原因可调用受管 Skill 优化并追加到草稿；逐条采用填写框架不再影响其他明细。",
    status: "RELEASED",
    changes: [
      {
        type: "FEATURE",
        title: "采购原因 AI 优化",
        description: "明细编辑弹窗新增 AI优化；每次读取平台启用的采购原因优化 Skill，结果追加到当前草稿，不自动保存或提交。"
      },
      {
        type: "FIX",
        title: "逐条采用互不影响",
        description: "采用一条 AI 预审填写框架后，仅该行显示已采用，其余待补充明细仍可继续逐条采用。"
      }
    ]
  },
  {
    version: "v20260819.1",
    releaseDate: "2026-08-19",
    title: "采购申请逐条 AI 预审",
    summary: "AI 预审移入采购明细，并在每条采购原因后直接展示对应意见。",
    status: "RELEASED",
    changes: [
      {
        type: "IMPROVEMENT",
        title: "预审意见与明细同行展示",
        description: "移除右侧预审栏，在采购原因后增加 AI 预审意见；每条明细分别显示建议通过、建议补充或建议退回。"
      },
      {
        type: "IMPROVEMENT",
        title: "整单与逐条结论保持一致",
        description: "保留整单预审结论和重新预审入口；任一明细需要补充或退回时，整单不会显示预审通过。"
      }
    ]
  },
  {
    version: "v20260817.4",
    releaseDate: "2026-08-17",
    title: "采购申请 AI 预审交互补齐",
    summary: "AI 预审结果升级为分级卡片，支持逐条采用和一键采用；采用内容只回填当前草稿。",
    status: "RELEASED",
    changes: [
      {
        type: "IMPROVEMENT",
        title: "分级展示预审结论",
        description: "必须补充、建议优化和链接核验使用独立卡片展示，保留原有预审结论和重新预审能力。"
      },
      {
        type: "FEATURE",
        title: "逐条采用与一键采用",
        description: "采购原因过于笼统时可回填待完善框架；采用结果仅修改浏览器中的当前草稿，不自动保存或提交。"
      },
      {
        type: "FIX",
        title: "阻止未补全框架提交",
        description: "草稿中仍含待填写标记时不能保存明细或提交申请，避免填写框架被误当成正式采购原因。"
      }
    ]
  },
  {
    version: "v20260817.3",
    releaseDate: "2026-08-17",
    title: "售后申请填写职责修正",
    summary: "申请人提交退换货原因，具体更换商品及价格方案继续由中标供应商填写。",
    status: "RELEASED",
    changes: [
      {
        type: "FIX",
        title: "隐藏申请人端供应商方案字段",
        description: "申请人选择 14 天退换货时只填写售后类型、数量和原因，不再填写方案商品、规格、编码、单位及更换说明。"
      },
      {
        type: "IMPROVEMENT",
        title: "复用供应商售后方案节点",
        description: "更换商品、规格和价格仍由中标供应商在现有工作台待办中提交，再由需求方确认。"
      }
    ]
  },
  {
    version: "v20260817.2",
    releaseDate: "2026-08-17",
    title: "采购协同三页面升级",
    summary: "采购申请、订单详情和换品复核按已确认原型升级，继续使用现有数据、权限和流程。",
    status: "RELEASED",
    changes: [
      {
        type: "FEATURE",
        title: "采购申请 AI 预审",
        description: "新建或编辑申请时可对当前草稿执行合规预审；预审不保存、不提交，也不替代原审批结论。"
      },
      {
        type: "IMPROVEMENT",
        title: "订单协同详情",
        description: "订单详情集中展示当前责任方、关键节点、下一步、最新动态、订单资料、售后和来源申请。"
      },
      {
        type: "IMPROVEMENT",
        title: "换品证据对比",
        description: "换品待办并排展示原商品和替换商品，突出规格、单价差异、供应商、申请人、链接及处理时限。"
      }
    ]
  },
  {
    version: "v20260817.1",
    releaseDate: "2026-08-17",
    title: "供应商替换商品报价复核增强",
    summary: "替换商品报价补齐必填信息、原商品对比和提交确认，申请人复核时可直接查看完整方案。",
    status: "RELEASED",
    changes: [
      {
        type: "IMPROVEMENT",
        title: "替换方案提交前对比",
        description: "供应商开启替换商品后，页面同步展示原商品与替换商品的规格、报价差额和比例，超过 13% 的涨幅直接标红。"
      },
      {
        type: "FEATURE",
        title: "替换信息完整性校验",
        description: "替换商品名称、规格、商品链接、单价和替换原因统一必填；手工填写与批量导入使用同一校验，提交前再次确认替换方案。"
      },
      {
        type: "IMPROVEMENT",
        title: "复用原确认流程",
        description: "提交后继续由系统 AI 给出风险提示，中标后仍进入现有申请人替换商品确认节点，不新增旁路审批。"
      }
    ]
  },
  {
    version: "v20260813.2",
    releaseDate: "2026-08-13",
    title: "集采订单按机房和供应商归档",
    summary: "我的订单中的集采单按机房、供应商和订单资料逐级展开，收货、售后、发票、签收单和供应商归档导出集中处理。",
    status: "RELEASED",
    changes: [
      {
        type: "IMPROVEMENT",
        title: "集采单三级归档",
        description: "打开集采单后先查看机房，再查看该机房的中标供应商，最后进入供应商订单归档，减少跨机房订单混在同一张表中的情况。"
      },
      {
        type: "FEATURE",
        title: "供应商资料集中处理",
        description: "供应商归档内分别展示订单、发票和签收单；订单页保留确认收货和申请售后，资料页复用现有上传、查看、确认和退回权限。"
      },
      {
        type: "IMPROVEMENT",
        title: "供应商级 PDF 导出",
        description: "在供应商归档中导出的 PDF 仅包含当前集采单、当前机房和当前供应商的订单，继续包含物品、比价和审批信息。"
      }
    ]
  },
  {
    version: "v20260813.1",
    releaseDate: "2026-08-13",
    title: "部门助理订单操作权限",
    summary: "来源部门助理可在已授权部门范围内协助完成订单导出、收货和售后处理。",
    status: "RELEASED",
    changes: [
      {
        type: "FIX",
        title: "助理无法导出订单 PDF",
        description: "修复助理能查看本部门订单资料、但无法下载采购操作订单 PDF 的权限断点。"
      },
      {
        type: "FEATURE",
        title: "助理协助收货和售后",
        description: "部门助理可确认本部门订单收货、发起售后并确认供应商方案；楼长、供应商和无关部门人员不继承这些操作。"
      }
    ]
  },
  {
    version: "v20260812.3",
    releaseDate: "2026-08-12",
    title: "关闭订单自动收货",
    summary: "订单收货改为仅由申请人或采购员确认，系统不再按时间自动完成收货。",
    status: "RELEASED",
    changes: [
      {
        type: "FIX",
        title: "恢复历史订单待收货状态",
        description: "历史已收货订单统一恢复为待收货，并清除自动收货截止时间和系统自动收货标记。"
      },
      {
        type: "IMPROVEMENT",
        title: "收货仅保留人工确认",
        description: "供应商确认中标后订单进入待收货，只有申请人或采购员确认实际到货后才能完成收货。"
      }
    ]
  },
  {
    version: "v20260812.2",
    releaseDate: "2026-08-12",
    title: "订单发票与送货单归集",
    summary: "订单资料统一管理发票和送货单，供应商、来源部门助理及最高管理员可按订单分工补充资料。",
    status: "RELEASED",
    changes: [
      {
        type: "FEATURE",
        title: "发票与送货单独立归档",
        description: "订单资料窗口可切换发票和送货单，两类资料分别维护上传、重传、确认状态和历史版本。"
      },
      {
        type: "FEATURE",
        title: "支持内部补充配送资料",
        description: "来源部门助理和最高管理员可以补传手中持有的发票或送货单，历史记录标明上传人及身份。"
      },
      {
        type: "IMPROVEMENT",
        title: "供应商上传后通知助理",
        description: "供应商提交订单资料后，系统向来源部门助理发送飞书通知，便于及时核对或补充另一类资料。"
      }
    ]
  },
  {
    version: "v20260812.1",
    releaseDate: "2026-08-12",
    title: "资产目录管理与报价分类",
    summary: "资产目录改为后台可维护，报价按目录内固定资产、目录外低值易耗分类，并单独提示二手高价特殊审批。",
    status: "RELEASED",
    changes: [
      {
        type: "FEATURE",
        title: "报价单补充商品成色",
        description: "原商品和替换商品均需选择全新或二手；二手商品必须说明来源、使用年限及翻新或拆机情况。"
      },
      {
        type: "FEATURE",
        title: "资产目录独立管理",
        description: "最高管理员可在平台管理中新增、编辑、启停或批量导入资产目录；维护结果对后续报价实时生效，历史报价结论不回写。"
      },
      {
        type: "IMPROVEMENT",
        title: "分类与特殊审批分离",
        description: "命中目录统一标记固定资产，未命中统一标记低值易耗；二手商品单价达到 1000 元时另行标记需特殊审批。"
      }
    ]
  },
  {
    version: "v20260811.1",
    releaseDate: "2026-08-11",
    title: "飞书审批卡片标明需求提交人",
    summary: "采购审批卡片展示原始需求提交人，月度汇总涉及多人时逐项标明对应提交人。",
    status: "RELEASED",
    changes: [
      {
        type: "IMPROVEMENT",
        title: "审批责任来源更清晰",
        description: "普通采购显示申请人；月度汇总读取各商品的源申请人，避免将系统汇总触发人或管理员抄送人误认为需求提交人。"
      },
      {
        type: "IMPROVEMENT",
        title: "多人汇总逐项标注",
        description: "汇总卡片顶部显示提交人摘要，多人提交时每条采购明细和替换商品明细同步显示对应需求提交人。"
      }
    ]
  },
  {
    version: "v20260802.1",
    releaseDate: "2026-08-02",
    title: "楼长超价复核",
    summary: "系统选出的中标单价高于参考价13%时，由楼长在24小时内复核，支持单项驳回且不阻塞同单其他商品。",
    status: "RELEASED",
    changes: [
      {
        type: "FEATURE",
        title: "24小时楼长复核",
        description: "复核卡片展示提交者、来源工单、中标供应商、超出金额和比例，并在剩余12小时、2小时及逾期时提醒。"
      },
      {
        type: "IMPROVEMENT",
        title: "仅处理超价商品",
        description: "楼长只能通过或单项驳回；驳回商品停止采购，同一申请内其他商品继续流转。"
      },
      {
        type: "IMPROVEMENT",
        title: "包装数量审核提醒",
        description: "商品名称或规格明确包含每包装量时，AI审核提醒核对数量、单位和单价是否按包计算。"
      }
    ]
  },
  {
    version: "v20260730.2",
    releaseDate: "2026-07-30",
    title: "采购经营分析看板",
    summary: "采购看板升级为领导经营分析视图，集中呈现采购规模、节省成果、同比环比和区域机房贡献，并保留原自定义分析能力。",
    status: "RELEASED",
    changes: [
      {
        type: "FEATURE",
        title: "采购降本与趋势总览",
        description: "突出需求金额、中标金额、节省金额、节省率和采购数量，并按同一筛选周期展示同比、环比及月度趋势。"
      },
      {
        type: "FEATURE",
        title: "区域与机房穿透分析",
        description: "支持自定义日期、业务区域和机房群筛选，按华北一、华北二等真实区域口径展示区域贡献、机房排行和供应商降本贡献。"
      },
      {
        type: "IMPROVEMENT",
        title: "自定义分析能力保留",
        description: "原可拖拽采购 BI 看板调整为次级入口，领导视图与自定义分析可随时切换。"
      },
      {
        type: "IMPROVEMENT",
        title: "精简页面信息层级",
        description: "移除非必要的说明性副文案，仅保留指标、筛选条件、对比结果和异常提示。"
      }
    ]
  },
  {
    version: "v20260729.1",
    releaseDate: "2026-07-29",
    title: "供应商提交监督查看",
    summary: "新增独立的全量只读权限，让指定测试或监督账号在原供应商报价页面查看全部供应商提交，同时保持普通供应商数据隔离。",
    status: "RELEASED",
    changes: [
      {
        type: "FEATURE",
        title: "全部供应商提交只读查看",
        description: "持权账号可查看各供应商的报价、交期、替换商品和备注；普通供应商仍只查看本公司报价。"
      },
      {
        type: "IMPROVEMENT",
        title: "查看与操作权限分离",
        description: "新增权限不开放其他供应商报价修改、比价审核或报价审核表导出，测试账号通过独立角色单独授权。"
      }
    ]
  },
  {
    version: "v20260727.1",
    releaseDate: "2026-07-27",
    title: "测试环境开发者中心",
    summary: "测试环境新增模块接入、API 调试和 AI 机器资料入口，并补齐移动端文档导航与生产隔离门禁。",
    status: "RELEASED",
    changes: [
      {
        type: "FEATURE",
        title: "内部开发者中心",
        description: "提供模块清单、前后端接入、权限、数据库、测试和 PR 发布文档，支持全文搜索、章节深链和移动端阅读。"
      },
      {
        type: "FEATURE",
        title: "OpenAPI 与 AI 稳定资料",
        description: "集中提供 Swagger、OpenAPI、llms.txt、结构化开发上下文、模块清单 Schema 和下载示例。"
      },
      {
        type: "IMPROVEMENT",
        title: "测试与生产严格隔离",
        description: "开发者页面和机器资料仅在本地或测试环境启用；生产入口、接口文档路径和专用前端资源显式返回 404。"
      }
    ]
  },
  {
    version: "v20260723.1",
    releaseDate: "2026-07-23",
    title: "采购规则设置精简",
    summary: "移除已完成验收且不再使用的流程测试按钮，采购规则页面只保留正式配置能力。",
    status: "RELEASED",
    changes: [
      {
        type: "IMPROVEMENT",
        title: "移除流程测试按钮",
        description: "采购规则设置不再展示创建测试申请、模拟审批、生成报价和供应商确认等流程测试操作。"
      }
    ]
  },
  {
    version: "v20260722.2",
    releaseDate: "2026-07-22",
    title: "发票闭环与全量回归修复",
    summary: "补齐订单发票上传、审核和重传闭环，并修复售后分页、劳保提前领用、流程测试、审批时间及平台回收等回归问题。",
    status: "RELEASED",
    changes: [
      {
        type: "FEATURE",
        title: "订单发票收集闭环",
        description: "中标供应商可上传和版本化重传发票；订单采购人、来源部门助理和当前楼长可查看下载、确认收票或退回并填写原因。"
      },
      {
        type: "FIX",
        title: "售后与劳保状态一致性",
        description: "售后关键字在数据库分页前过滤；劳保仅允许申领人在采购审批、供应商确认和订单实际收货完成后确认领用。"
      },
      {
        type: "FIX",
        title: "流程与平台回归修复",
        description: "流程测试复用正式需求池和供应商确认服务，进行中审批会显式清空结束时间，删除用户同步撤销平台准入。"
      },
      {
        type: "IMPROVEMENT",
        title: "导入、路由和测试部署治理",
        description: "采购导入增加后端字段校验，缺参统一返回校验错误，修复 /assets 兼容入口，并为 Gitee 测试镜像增加保留和磁盘告警策略。"
      }
    ]
  },
  {
    version: "v20260722.1",
    releaseDate: "2026-07-22",
    title: "AI知识库与 Skill 治理",
    summary: "平台设置支持维护知识库和 Skill、查看修订历史并审核模型改进建议；采购合规审查作为首个受治理 Skill 接入正式提交。",
    status: "RELEASED",
    changes: [
      {
        type: "FEATURE",
        title: "知识库与 Skill 可视化管理",
        description: "管理员可以在设置中增删改查、启停知识库与 Skill，配置引用关系，并查看每次变更的完整修订快照。"
      },
      {
        type: "IMPROVEMENT",
        title: "模型建议必须审核",
        description: "模型发现规则缺口时只生成待审核改进建议；管理员对比当前正文和建议全文并通过后，新修订才会生效。"
      },
      {
        type: "IMPROVEMENT",
        title: "采购合规审查统一接入",
        description: "页面、批量导入和飞书 AI 创建的申请在正式提交时统一检查理由、用途、数量依据和紧急性；模型失败不影响原审批流转。"
      }
    ]
  },
  {
    version: "v20260721.1",
    releaseDate: "2026-07-21",
    title: "紧急采购成交价回写修复",
    summary: "中标后审批、订单和金额汇总统一使用供应商报价，申请时参考价继续保留用于核对。",
    status: "RELEASED",
    changes: [
      {
        type: "FIX",
        title: "审批金额仍显示申请参考价",
        description: "选定中标报价后，将供应商成交单价和总价回写为当前有效金额，避免审批继续沿用申请参考价。"
      },
      {
        type: "IMPROVEMENT",
        title: "保留原参考价审计信息",
        description: "审批页面同时标识供应商报价和原参考金额，历史数据修正不会丢失申请时价格。"
      }
    ]
  },
  {
    version: "v20260720.4",
    releaseDate: "2026-07-20",
    title: "EHS 部门审批与外部入口",
    summary: "作业许可直接使用部门管理审批链，并开放无需平台账号的外部申请、进度办理和审批链接。",
    status: "RELEASED",
    changes: [
      {
        type: "IMPROVEMENT",
        title: "沿用部门审批配置",
        description: "申请人选择作业所属部门后，系统按部门管理中的审批节点、候选人和会签规则生成统一待办，不再手工指定 EHS 审批人。"
      },
      {
        type: "FEATURE",
        title: "外部申请与进度办理",
        description: "外部人员无需平台账号即可申请、查看进度、修改退回票、重新提交、开工、采样和关单。"
      },
      {
        type: "FEATURE",
        title: "免登录审批链接",
        description: "审批通知可打开一次性审批链接，审批动作仍绑定内部审批人并进入原统一审批记录。"
      }
    ]
  },
  {
    version: "v20260720.3",
    releaseDate: "2026-07-20",
    title: "EHS 工作台全量业务接入",
    summary: "测试环境 EHS 工作台启用正式台账、作业许可、统一审批、通知记录、知识库和实时数据助手。",
    status: "RELEASED",
    changes: [
      {
        type: "FEATURE",
        title: "四类 EHS 正式台账",
        description: "隐患、环境与危废、PPE 与应急物资、事故事件均支持真实新增、编辑、删除、筛选、统计、Excel 导入和导出。"
      },
      {
        type: "FEATURE",
        title: "作业许可统一审批",
        description: "总作业票和六类专项票按顺序进入平台统一审批实例、待办和审批记录，专项票全部通过后才能开工，专项票全部关闭后才能关闭总票。"
      },
      {
        type: "IMPROVEMENT",
        title: "知识库与实时数据助手",
        description: "EHS 助手优先查询当前台账和许可统计，并可引用已录入的制度、标准和预案；助手不会代替审批人改变审批结果。"
      }
    ]
  },
  {
    version: "v20260720.2",
    releaseDate: "2026-07-20",
    title: "员工离职与审批交接",
    summary: "用户管理新增员工离职办理，冻结账号前逐项检查并交接长期审批配置和当前待办审批。",
    status: "RELEASED",
    changes: [
      {
        type: "FEATURE",
        title: "员工离职状态",
        description: "自有员工账号新增离职状态；办理完成后立即阻止登录并使已有会话失效，原角色和应用授权保留为冻结记录。"
      },
      {
        type: "FEATURE",
        title: "审批影响逐项交接",
        description: "离职前扫描部门 V1-V8 审批配置和当前待办，每个影响项必须单独指定启用中的替换员工。"
      },
      {
        type: "IMPROVEMENT",
        title: "在途审批连续处理",
        description: "待办改派后同步更新当前审批人并触发现有飞书审批通知，不改变原单据节点和审批状态。"
      }
    ]
  },
  {
    version: "v20260720.1",
    releaseDate: "2026-07-20",
    title: "EHS 工作台测试预览",
    summary: "测试环境先行新增 EHS 一级工作台和二级业务入口，验证平台准入、菜单隔离和统一审批表兼容性。",
    status: "RELEASED",
    changes: [
      {
        type: "FEATURE",
        title: "EHS 工作台与业务入口",
        description: "测试站新增隐患、环境与危废、PPE 与应急物资、事故事件、作业许可、智能助手和集成设置入口。"
      },
      {
        type: "FEATURE",
        title: "复用平台权限与统一审批",
        description: "EHS 使用现有应用准入；作业许可总览只统计统一审批表中真实存在的 EHS 实例，EHS-only 账号不会读取采购工作台数据。"
      },
      {
        type: "IMPROVEMENT",
        title: "先行空状态验证",
        description: "正式业务接入前仅展示真实空台账，不生成演示记录。"
      }
    ]
  },
  {
    version: "v20260715.5",
    releaseDate: "2026-07-15",
    title: "部门管理加载超时修复",
    summary: "修复测试环境部门管理偶发加载部门配置超时的问题，首屏只加载部门摘要，平台规则和完整审批链改为按需读取。",
    status: "RELEASED",
    changes: [
      {
        type: "FIX",
        title: "部门管理配置加载超时",
        description: "部门列表不再一次性拉取全部审批链和平台规则，减少测试链路下接口响应体过大导致的 20 秒超时。"
      },
      {
        type: "IMPROVEMENT",
        title: "配置详情按需加载",
        description: "点击“配置”时再读取该部门完整审批链；点击“查看平台规则”时再加载规则说明，页面首屏更快。"
      }
    ]
  },
  {
    version: "v20260715.4",
    releaseDate: "2026-07-15",
    title: "页面白屏自动恢复",
    summary: "修复浏览器偶发未能加载页面分包时停留在白屏的问题，增加一次自动恢复和明确的重新加载入口。",
    status: "RELEASED",
    changes: [
      {
        type: "FIX",
        title: "登录页偶发白屏",
        description: "页面分包首次加载失败时自动重新加载一次；连续失败时显示可操作的错误页，不再无提示白屏。"
      }
    ]
  },
  {
    version: "v20260715.3",
    releaseDate: "2026-07-15",
    title: "上线管理平台化显示",
    summary: "上线管理不再只跳到独立绿色控制台，改为在平台页面内承载安全上线工作台，并统一为平台后台视觉。",
    status: "RELEASED",
    changes: [
      {
        type: "IMPROVEMENT",
        title: "上线管理嵌入平台页面",
        description: "测试站的“平台管理 / 上线管理”直接展示安全上线工作台，同时保留新窗口打开作为兜底。"
      },
      {
        type: "IMPROVEMENT",
        title: "上线控制台视觉统一",
        description: "独立控制面改为浅色背景、蓝色主按钮、平台品牌和卡片表格布局，避免像另一个系统。"
      },
      {
        type: "FIX",
        title: "内嵌安全头配置",
        description: "上线控制面默认仍禁止外站嵌入，只允许配置的测试平台地址承载页面。"
      }
    ]
  },
  {
    version: "v20260715.2",
    releaseDate: "2026-07-15",
    title: "环境标识显示修正",
    summary: "测试标识和上线管理入口改为按当前站点识别，生产环境不再显示测试信息。",
    status: "RELEASED",
    changes: [
      {
        type: "FIX",
        title: "生产环境误显示测试标识",
        description: "右下角测试环境标识只在测试站显示，生产站默认隐藏。"
      },
      {
        type: "IMPROVEMENT",
        title: "上线管理入口按环境隔离",
        description: "上线管理菜单和页面只在测试站开放，生产站不生成对应路由。"
      }
    ]
  },
  {
    version: "v20260715.1",
    releaseDate: "2026-07-15",
    title: "上线管理正式启用",
    summary: "修复上线管理无法读取可发布更新的问题，补齐测试验收、生产确认和发布记录。",
    status: "RELEASED",
    changes: [
      {
        type: "FIX",
        title: "可上线更新无法显示",
        description: "重新识别已通过构建检查且与当前主线一致的更新，未通过项目会直接说明原因。"
      },
      {
        type: "FEATURE",
        title: "发布批次与人工确认",
        description: "管理员可组合一个或多个更新进入测试，验收通过后再确认上线，并保留批次、操作者和处理结果。"
      },
      {
        type: "IMPROVEMENT",
        title: "生产发布校验加强",
        description: "上线前增加独立数据库验证和运行版本核对，生产只使用测试阶段确认过的程序版本。"
      }
    ]
  },
  {
    version: "v20260714.1",
    releaseDate: "2026-07-14",
    title: "部门管理配置优化",
    summary: "修复送达地址层级无可选数据的问题，并按部门准入、地址、审批路径和协作权限重新组织配置页面。",
    status: "RELEASED",
    changes: [
      {
        type: "FIX",
        title: "送达地址无法维护",
        description: "移除依赖历史部门地址生成选项的空级联框，省、市、区县、街道和详细地址改为直接维护并实时预览。"
      },
      {
        type: "IMPROVEMENT",
        title: "部门配置状态更清晰",
        description: "部门列表集中展示业务范围、供应商送达地址、审批链和部门规则，并标记缺少地址或必要审批人的配置。"
      },
      {
        type: "IMPROVEMENT",
        title: "编辑弹窗按业务分区",
        description: "部门与机房、送达地址、审批路径、协作与备注分为四个页签，平台通用规则移入只读抽屉。"
      }
    ]
  },
  {
    version: "v20260710.2",
    releaseDate: "2026-07-10",
    title: "系统更新页面视觉改版",
    summary: "重新设计版本发布页，让当前版本、历史版本和每次上线内容更容易查找。",
    status: "RELEASED",
    changes: [
      {
        type: "IMPROVEMENT",
        title: "版本信息重新分层",
        description: "当前生产版本独立展示，历史版本改为目录切换，问题修复、功能更新和体验优化按类别分组查看。"
      },
      {
        type: "IMPROVEMENT",
        title: "移动端浏览优化",
        description: "窄屏下版本目录改为横向选择，发布详情自动切换为单列，避免版本标题和更新说明相互挤压。"
      }
    ]
  },
  {
    version: "v20260710.1",
    releaseDate: "2026-07-10",
    title: "采购日期校验与系统更新记录",
    summary: "修复采购明细日期格式导致的提交失败，并建立统一的版本更新查看入口。",
    status: "RELEASED",
    changes: [
      {
        type: "FIX",
        title: "采购申请日期兼容",
        description: "Excel 导入的 7/10/26、2026/7/10 等日期会自动转换为 2026-07-10，避免申请提交失败。"
      },
      {
        type: "FIX",
        title: "错误台账分类优化",
        description: "日期格式等可直接提示用户修正的表单问题不再进入错误台账，历史同类记录统一标记为已修复。"
      },
      {
        type: "FIX",
        title: "生产发布构建超时",
        description: "生产部署复用未变化的 Docker 基础依赖，避免重复下载系统字体导致发布超过 45 分钟。"
      },
      {
        type: "FEATURE",
        title: "新增系统更新页面",
        description: "管理员可以按版本查看已修复问题、功能更新和体验优化，左下角版本号与本页保持一致。"
      }
    ]
  },
  {
    version: "v20260709.1",
    releaseDate: "2026-07-09",
    title: "灾害预警来源调整",
    summary: "实时预警改用中央气象台数据，减少有效预警被提前判定解除的情况。",
    status: "RELEASED",
    changes: [
      {
        type: "FIX",
        title: "有效预警误解除",
        description: "平台按北京市朝阳区和北京市级官方预警判断状态，仅收到正式解除信息后才发送解除通知。"
      },
      {
        type: "IMPROVEMENT",
        title: "预警数据来源升级",
        description: "实时预警默认接入中央气象台当前预警列表，原天气接口继续负责天气实况和预报。"
      }
    ]
  },
  {
    version: "v20260707.7",
    releaseDate: "2026-07-07",
    title: "预警卡片增加图片",
    summary: "灾害预警卡片随消息发送当前预警图片，方便群内查看和转发。",
    status: "RELEASED",
    changes: [
      {
        type: "FEATURE",
        title: "预警图片随卡片发送",
        description: "每次推送自动生成当前预警图片；图片生成失败时仍继续发送文字卡片。"
      }
    ]
  },
  {
    version: "v20260707.6",
    releaseDate: "2026-07-07",
    title: "预警卡片增加检查表入口",
    summary: "值班人员可从预警卡片直接进入重保检查表。",
    status: "RELEASED",
    changes: [
      {
        type: "FEATURE",
        title: "重保检查表快捷入口",
        description: "预警卡片底部增加检查表按钮，减少收到预警后再查找表单的时间。"
      }
    ]
  },
  {
    version: "v20260707.5",
    releaseDate: "2026-07-07",
    title: "劳保申领周期调整",
    summary: "统一补齐现有在库人员劳保用品的下一次可领用日期。",
    status: "RELEASED",
    changes: [
      {
        type: "IMPROVEMENT",
        title: "劳保周期台账补齐",
        description: "现有在库人员下一次领用日期统一调整为 2027-04-01，后续新入职人员仍按正常周期计算。"
      }
    ]
  },
  {
    version: "v20260707.4",
    releaseDate: "2026-07-07",
    title: "采购售后方案确认",
    summary: "售后处理改为供应商给出方案后，由需求提交者或采购人确认。",
    status: "RELEASED",
    changes: [
      {
        type: "FEATURE",
        title: "供应商提交售后方案",
        description: "支持退换、维修、补发和更换商品方案；更换商品单价涨幅最多为 400 元。"
      },
      {
        type: "IMPROVEMENT",
        title: "售后方案由需求方确认",
        description: "需求方不同意时退回供应商重新给方案，暂不创建服务失单记录。"
      }
    ]
  },
  {
    version: "v20260704.1",
    releaseDate: "2026-07-04",
    title: "报价复审驳回项清理",
    summary: "修复报价复审驳回商品仍出现在供应商中标结果中的问题。",
    status: "RELEASED",
    changes: [
      {
        type: "FIX",
        title: "已驳回商品仍发送供应商",
        description: "复审驳回后同步取消该商品的中标标记，中标卡片和清单只展示剩余有效商品。"
      }
    ]
  },
  {
    version: "v20260703.4",
    releaseDate: "2026-07-03",
    title: "替换商品确认时限",
    summary: "申请人替换商品确认节点统一为两天，超时后按原商品继续处理。",
    status: "RELEASED",
    changes: [
      {
        type: "IMPROVEMENT",
        title: "替换商品确认规则统一",
        description: "待确认任务超过两天未处理时自动选择原商品报价，避免采购流程长期停留。"
      }
    ]
  }
];

export const CURRENT_SYSTEM_VERSION = SYSTEM_UPDATE_RECORDS[0].version;
