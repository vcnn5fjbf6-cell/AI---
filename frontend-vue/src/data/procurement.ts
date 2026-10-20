export type ProcurementStatus = "draft" | "approving" | "quoting" | "compared" | "approved" | "rejected";
export type SupplierStatus = "active" | "pending" | "blacklist";

export interface ProcurementMetric {
  label: string;
  value: string;
  note: string;
  tone?: "primary" | "success" | "warning" | "danger";
}

export interface ProcurementRequest {
  id: number;
  reqNo: string;
  reqTitle: string;
  reqType: string;
  deptName: string;
  region: string;
  applicant: string;
  purchaser: string;
  amount: number;
  needDate: string;
  approvalStatus: ProcurementStatus;
  currentNode: string;
}

export interface ProcurementItem {
  lineNo: number;
  itemCode: string;
  itemName: string;
  specModel: string;
  unit: string;
  qty: number;
  unitPrice: number;
  needDate: string;
  purpose: string;
  ecommerceUrl: string;
}

export interface SupplierDirectory {
  id: number;
  supplierCode: string;
  supplierName: string;
  supplierType: string;
  contactName: string;
  contactPhone: string;
  ratingLevel: string;
  onboardingStatus: string;
  status: SupplierStatus;
}

export interface QuoteRow {
  id: number;
  reqNo: string;
  reqTitle: string;
  supplierName: string;
  quoteAmount: number;
  leadTimeDays: number;
  status: "待报价" | "已报价" | "已比价";
}

export const procurementMetrics: ProcurementMetric[] = [
  { label: "采购申请", value: "68", note: "本月新增申请单", tone: "primary" },
  { label: "待审批", value: "12", note: "停留在审批节点", tone: "warning" },
  { label: "询价中", value: "9", note: "等待供应商报价", tone: "success" },
  { label: "异常退回", value: "3", note: "需申请人补充材料", tone: "danger" }
];

export const requestRows: ProcurementRequest[] = [
  {
    id: 1,
    reqNo: "PR-2026-0412",
    reqTitle: "亦庄A1机房精密空调备件采购",
    reqType: "服务/费用/低值易耗等",
    deptName: "基础设施运维部",
    region: "北京",
    applicant: "张维",
    purchaser: "赵越",
    amount: 186000,
    needDate: "2026-05-20",
    approvalStatus: "approving",
    currentNode: "部门经理审批"
  },
  {
    id: 2,
    reqNo: "PR-2026-0418",
    reqTitle: "廊坊B1弱电门禁控制器更换",
    reqType: "实物资产/无形资产/长摊等",
    deptName: "数据中心运营部",
    region: "河北",
    applicant: "王珊",
    purchaser: "李宁",
    amount: 74200,
    needDate: "2026-05-12",
    approvalStatus: "quoting",
    currentNode: "供应商报价"
  },
  {
    id: 3,
    reqNo: "PR-2026-0421",
    reqTitle: "通州C3消防系统巡检服务",
    reqType: "服务/费用/低值易耗等",
    deptName: "安全与合规部",
    region: "北京",
    applicant: "陈启",
    purchaser: "赵越",
    amount: 128500,
    needDate: "2026-06-01",
    approvalStatus: "compared",
    currentNode: "最低价比对"
  },
  {
    id: 4,
    reqNo: "PR-2026-0425",
    reqTitle: "昌平D2机房UPS电池采购",
    reqType: "实物资产/无形资产/长摊等",
    deptName: "基础设施运维部",
    region: "北京",
    applicant: "孙航",
    purchaser: "李宁",
    amount: 326000,
    needDate: "2026-06-15",
    approvalStatus: "draft",
    currentNode: "申请草稿"
  }
];

export const itemRows: ProcurementItem[] = [
  {
    lineNo: 1,
    itemCode: "MAT-HVAC-001",
    itemName: "压缩机维修包",
    specModel: "CRAC-30kW",
    unit: "套",
    qty: 4,
    unitPrice: 18500,
    needDate: "2026-05-20",
    purpose: "精密空调维保备件更换",
    ecommerceUrl: "https://example.com/hvac-kit"
  },
  {
    lineNo: 2,
    itemCode: "MAT-ELV-012",
    itemName: "门禁控制器",
    specModel: "TCP/IP 四门",
    unit: "台",
    qty: 8,
    unitPrice: 4200,
    needDate: "2026-05-12",
    purpose: "弱电门禁更换",
    ecommerceUrl: "https://example.com/access-controller"
  }
];

export const supplierRows: SupplierDirectory[] = [
  {
    id: 1,
    supplierCode: "SUP-001",
    supplierName: "北京智维机电服务有限公司",
    supplierType: "维保服务",
    contactName: "周明",
    contactPhone: "13800000002",
    ratingLevel: "A",
    onboardingStatus: "已准入",
    status: "active"
  },
  {
    id: 2,
    supplierCode: "SUP-015",
    supplierName: "廊坊数能工程技术有限公司",
    supplierType: "工程维修",
    contactName: "刘洋",
    contactPhone: "13800000004",
    ratingLevel: "B",
    onboardingStatus: "审核中",
    status: "pending"
  },
  {
    id: 3,
    supplierCode: "SUP-022",
    supplierName: "华北动力设备有限公司",
    supplierType: "设备供应",
    contactName: "马越",
    contactPhone: "13800000004",
    ratingLevel: "A",
    onboardingStatus: "已准入",
    status: "active"
  }
];

export const quoteRows: QuoteRow[] = [
  {
    id: 1,
    reqNo: "PR-2026-0418",
    reqTitle: "廊坊B1弱电门禁控制器更换",
    supplierName: "廊坊数能工程技术有限公司",
    quoteAmount: 69800,
    leadTimeDays: 7,
    status: "已报价"
  },
  {
    id: 2,
    reqNo: "PR-2026-0418",
    reqTitle: "廊坊B1弱电门禁控制器更换",
    supplierName: "北京智维机电服务有限公司",
    quoteAmount: 72400,
    leadTimeDays: 5,
    status: "已报价"
  },
  {
    id: 3,
    reqNo: "PR-2026-0421",
    reqTitle: "通州C3消防系统巡检服务",
    supplierName: "华北动力设备有限公司",
    quoteAmount: 121000,
    leadTimeDays: 12,
    status: "已比价"
  }
];

export function procurementStatusText(status: ProcurementStatus): string {
  const map: Record<ProcurementStatus, string> = {
    draft: "草稿",
    approving: "审批中",
    quoting: "询价中",
    compared: "已比价",
    approved: "已通过",
    rejected: "已退回"
  };
  return map[status];
}

export function procurementStatusTag(status: ProcurementStatus): "primary" | "success" | "warning" | "danger" | "info" {
  if (status === "draft") return "info";
  if (status === "approving" || status === "quoting") return "warning";
  if (status === "compared") return "primary";
  if (status === "approved") return "success";
  return "danger";
}

export function supplierStatusText(status: SupplierStatus): string {
  const map: Record<SupplierStatus, string> = {
    active: "启用",
    pending: "待准入",
    blacklist: "黑名单"
  };
  return map[status];
}
