export type CoverageStatus = "normal" | "warning" | "expired" | "gap";
export type SourceType = "framework" | "order" | "local" | "gap";
export type RenewalPriority = "high" | "medium" | "low";

export interface MetricItem {
  label: string;
  value: string;
  note: string;
  tone?: "primary" | "success" | "warning" | "danger";
}

export interface MaintenanceSource {
  id: number;
  sourceNo: string;
  sourceName: string;
  sourceType: SourceType;
  dataCenter: string;
  area: string;
  owner: string;
  startDate: string;
  endDate: string;
  status: CoverageStatus;
  amount: string;
}

export interface AssetCoverage {
  id: number;
  assetCode: string;
  assetName: string;
  dataCenter: string;
  building: string;
  profession: string;
  sourceType: SourceType;
  sourceNo: string;
  owner: string;
  endDate: string;
  status: CoverageStatus;
}

export interface RoomCategory {
  id: number;
  area: string;
  dataCenter: string;
  building: string;
  orderRoomName: string;
  roomNature: "自建" | "代维";
  region: string;
  roomType: "园区型" | "单体型";
  regionOwner: string;
  roomOwner: string;
  floorLeader: string;
  businessType: string;
  professions: string[];
}

export interface PermissionPerson {
  id: number;
  personName: string;
  role: string;
  headquartersAccess: boolean;
  rooms: string[];
  professions: string[];
}

export interface RenewalTask {
  id: number;
  taskNo: string;
  targetName: string;
  sourceType: SourceType;
  owner: string;
  dueDate: string;
  reminderRule: string;
  priority: RenewalPriority;
  status: "待启动" | "跟进中" | "已升级";
}

export const professionOptions = ["暖通", "弱电", "强电", "交付", "消防"];

export const dataCenterOptions = ["亦庄A1机房", "亦庄A2机房", "廊坊B1机房", "通州C3机房", "昌平D2机房"];

export const personOptions = ["张维", "李宁", "王珊", "陈启", "赵越", "孙航"];

export const dashboardMetrics: MetricItem[] = [
  { label: "维保覆盖设备", value: "1,286", note: "已关联框架订单或自采依据", tone: "primary" },
  { label: "临期风险", value: "42", note: "180 / 90 / 60 / 30 天提醒队列", tone: "warning" },
  { label: "自采项目", value: "18", note: "机房侧自采合同与维修项目", tone: "success" },
  { label: "无维保缺口", value: "23", note: "既无框架订单，也无自采依据", tone: "danger" }
];

export const sourceRows: MaintenanceSource[] = [
  {
    id: 1,
    sourceNo: "FW-2026-001",
    sourceName: "总部暖通维保框架协议",
    sourceType: "framework",
    dataCenter: "亦庄A1机房",
    area: "华北一区",
    owner: "张维",
    startDate: "2026-01-01",
    endDate: "2026-12-31",
    status: "normal",
    amount: "860万"
  },
  {
    id: 2,
    sourceNo: "PO-2026-088",
    sourceName: "A2弱电维保框架采购订单",
    sourceType: "order",
    dataCenter: "亦庄A2机房",
    area: "华北一区",
    owner: "李宁",
    startDate: "2026-02-01",
    endDate: "2026-06-30",
    status: "warning",
    amount: "126万"
  },
  {
    id: 3,
    sourceNo: "LC-2026-017",
    sourceName: "廊坊B1消防泵维修自采项目",
    sourceType: "local",
    dataCenter: "廊坊B1机房",
    area: "华北二区",
    owner: "王珊",
    startDate: "2026-03-15",
    endDate: "2026-05-15",
    status: "warning",
    amount: "38万"
  },
  {
    id: 4,
    sourceNo: "GAP-2026-011",
    sourceName: "通州C3强电巡检缺口",
    sourceType: "gap",
    dataCenter: "通州C3机房",
    area: "华北三区",
    owner: "陈启",
    startDate: "-",
    endDate: "-",
    status: "gap",
    amount: "-"
  }
];

export const assetRows: AssetCoverage[] = [
  {
    id: 1,
    assetCode: "HVAC-A1-022",
    assetName: "冷冻水泵 2#",
    dataCenter: "亦庄A1机房",
    building: "A1楼",
    profession: "暖通",
    sourceType: "framework",
    sourceNo: "FW-2026-001",
    owner: "张维",
    endDate: "2026-12-31",
    status: "normal"
  },
  {
    id: 2,
    assetCode: "ELV-A2-117",
    assetName: "门禁控制器 17#",
    dataCenter: "亦庄A2机房",
    building: "A2楼",
    profession: "弱电",
    sourceType: "order",
    sourceNo: "PO-2026-088",
    owner: "李宁",
    endDate: "2026-06-30",
    status: "warning"
  },
  {
    id: 3,
    assetCode: "FIR-B1-034",
    assetName: "消防泵控制柜",
    dataCenter: "廊坊B1机房",
    building: "B1楼",
    profession: "消防",
    sourceType: "local",
    sourceNo: "LC-2026-017",
    owner: "王珊",
    endDate: "2026-05-15",
    status: "warning"
  },
  {
    id: 4,
    assetCode: "PWR-C3-051",
    assetName: "低压配电柜 5#",
    dataCenter: "通州C3机房",
    building: "C3楼",
    profession: "强电",
    sourceType: "gap",
    sourceNo: "-",
    owner: "陈启",
    endDate: "-",
    status: "gap"
  }
];

export const roomRows: RoomCategory[] = [
  {
    id: 1,
    area: "华北一区",
    dataCenter: "亦庄A1机房",
    building: "A1楼",
    orderRoomName: "北京亦庄A1数据中心",
    roomNature: "自建",
    region: "北京",
    roomType: "园区型",
    regionOwner: "赵越",
    roomOwner: "张维",
    floorLeader: "孙航",
    businessType: "核心生产",
    professions: ["暖通", "强电", "消防"]
  },
  {
    id: 2,
    area: "华北一区",
    dataCenter: "亦庄A2机房",
    building: "A2楼",
    orderRoomName: "北京亦庄A2数据中心",
    roomNature: "自建",
    region: "北京",
    roomType: "园区型",
    regionOwner: "赵越",
    roomOwner: "李宁",
    floorLeader: "孙航",
    businessType: "核心生产",
    professions: ["弱电", "交付"]
  },
  {
    id: 3,
    area: "华北二区",
    dataCenter: "廊坊B1机房",
    building: "B1楼",
    orderRoomName: "廊坊B1托管机房",
    roomNature: "代维",
    region: "河北",
    roomType: "单体型",
    regionOwner: "陈启",
    roomOwner: "王珊",
    floorLeader: "赵越",
    businessType: "边缘节点",
    professions: ["暖通", "消防"]
  }
];

export const permissionRows: PermissionPerson[] = [
  {
    id: 1,
    personName: "张维",
    role: "机房负责人",
    headquartersAccess: false,
    rooms: ["亦庄A1机房"],
    professions: ["暖通", "强电"]
  },
  {
    id: 2,
    personName: "赵越",
    role: "区域负责人",
    headquartersAccess: true,
    rooms: ["亦庄A1机房", "亦庄A2机房", "廊坊B1机房"],
    professions: ["暖通", "弱电", "强电", "消防"]
  },
  {
    id: 3,
    personName: "王珊",
    role: "运维人员",
    headquartersAccess: false,
    rooms: ["廊坊B1机房"],
    professions: ["消防"]
  }
];

export const renewalRows: RenewalTask[] = [
  {
    id: 1,
    taskNo: "RN-2026-001",
    targetName: "总部暖通维保框架协议",
    sourceType: "framework",
    owner: "张维",
    dueDate: "2026-12-31",
    reminderRule: "180天 / 90天",
    priority: "low",
    status: "待启动"
  },
  {
    id: 2,
    taskNo: "RN-2026-014",
    targetName: "A2弱电维保框架采购订单",
    sourceType: "order",
    owner: "李宁",
    dueDate: "2026-06-30",
    reminderRule: "60天 / 30天 / 7天",
    priority: "medium",
    status: "跟进中"
  },
  {
    id: 3,
    taskNo: "RN-2026-020",
    targetName: "廊坊B1消防泵维修自采项目",
    sourceType: "local",
    owner: "王珊",
    dueDate: "2026-05-15",
    reminderRule: "30天 / 15天 / 7天",
    priority: "high",
    status: "已升级"
  }
];

export function sourceTypeText(type: SourceType): string {
  const map: Record<SourceType, string> = {
    framework: "总部框架",
    order: "框架订单",
    local: "机房自采",
    gap: "无维保"
  };
  return map[type];
}

export function coverageStatusText(status: CoverageStatus): string {
  const map: Record<CoverageStatus, string> = {
    normal: "正常",
    warning: "临期",
    expired: "已过期",
    gap: "缺口"
  };
  return map[status];
}
