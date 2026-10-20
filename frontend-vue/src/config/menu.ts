import { isTestEnvironment } from "./runtime-environment";

export interface AppMenuItem {
  path: string;
  name: string;
  roles: string[];
  permissions?: string[];
  appCode?: string;
  anyAppCodes?: string[];
  children?: AppMenuItem[];
  hidden?: boolean;
  hiddenForRoles?: string[];
  alwaysVisible?: boolean;
}

const appRoles: string[] = [];
const adminRoles = ["SUPER_ADMIN"];
const hrAdminRoles = [...adminRoles, "ADMIN", "SYS_ADMIN"];
const hrLeaderRoles = [...hrAdminRoles, "FLOOR_LEADER"];
const testEnvironment = isTestEnvironment();
const internalProcurementRoles = [
  ...adminRoles,
  "ENGINEER",
  "PURCHASER",
  "MANAGER",
  "FLOOR_LEADER",
  "REGION_MANAGER",
  "SENIOR_MANAGER"
];
const supplierQuoteRoles = [...internalProcurementRoles, "SUPPLIER"];
const supplierRoles = ["SUPPLIER"];

export const menuTree: AppMenuItem[] = [
  {
    path: "/workbench",
    name: "工作台",
    roles: appRoles,
    anyAppCodes: ["PROCUREMENT", "PLATFORM", "MAINTENANCE", "EHS", "OUTSOURCING"]
  },
  {
    path: "/hr",
    name: "人力资源管理",
    roles: appRoles,
    hiddenForRoles: ["SUPPLIER"],
    alwaysVisible: true,
    children: [
      { path: "/hr/dashboard", name: "数据看板", roles: hrAdminRoles, alwaysVisible: true },
      { path: "/hr/employees", name: "人力资源", roles: hrLeaderRoles, alwaysVisible: true },
      { path: "/hr/attendance", name: "考勤管理", roles: appRoles, alwaysVisible: true },
      { path: "/hr/performance", name: "绩效管理", roles: hrAdminRoles, alwaysVisible: true },
      { path: "/hr/cost", name: "成本管理", roles: hrAdminRoles, alwaysVisible: true },
      { path: "/hr/permission", name: "权限管理", roles: hrAdminRoles, alwaysVisible: true },
      { path: "/hr/settings", name: "系统设置", roles: hrAdminRoles, alwaysVisible: true }
    ]
  },
  ...(testEnvironment
    ? [{
        path: "/developer-center",
        name: "开发者中心",
        roles: appRoles,
        anyAppCodes: ["PROCUREMENT", "PLATFORM", "MAINTENANCE", "EHS"],
        hiddenForRoles: ["SUPPLIER"]
      }]
    : []),
  ...(testEnvironment
    ? [{
        path: "/ehs",
        name: "EHS工作台",
        roles: appRoles,
        appCode: "EHS",
        hiddenForRoles: ["SUPPLIER"],
        children: [
          { path: "/ehs/dashboard", name: "总览", roles: appRoles, appCode: "EHS" },
          { path: "/ehs/hazards", name: "隐患管理", roles: appRoles, appCode: "EHS" },
          { path: "/ehs/waste", name: "环境与危废", roles: appRoles, appCode: "EHS" },
          { path: "/ehs/ppe", name: "PPE与应急物资", roles: appRoles, appCode: "EHS" },
          { path: "/ehs/incidents", name: "事故事件", roles: appRoles, appCode: "EHS" },
          { path: "/ehs/permits", name: "作业许可", roles: appRoles, appCode: "EHS" },
          { path: "/ehs/assistant", name: "智能助手", roles: appRoles, appCode: "EHS" },
          { path: "/ehs/settings", name: "运行设置", roles: adminRoles, appCode: "EHS" }
        ]
      }]
    : []),
  ...(testEnvironment
    ? [{
        path: "/engineering-outsourcing",
        name: "工程外包管理",
        roles: appRoles,
        appCode: "OUTSOURCING",
        children: [
          { path: "/engineering-outsourcing/workbench", name: "IT工程外包看板", roles: appRoles, appCode: "OUTSOURCING", hiddenForRoles: ["SUPPLIER"] },
          { path: "/engineering-outsourcing/orders", name: "工单管理", roles: appRoles, appCode: "OUTSOURCING", hiddenForRoles: ["SUPPLIER"] },
          { path: "/engineering-outsourcing/entry", name: "入场与人员", roles: appRoles, appCode: "OUTSOURCING", hiddenForRoles: ["SUPPLIER"] },
          { path: "/engineering-outsourcing/acceptance", name: "验收管理", roles: appRoles, appCode: "OUTSOURCING", hiddenForRoles: ["SUPPLIER"] },
          { path: "/engineering-outsourcing/settlement", name: "结算与对账", roles: appRoles, appCode: "OUTSOURCING", hiddenForRoles: ["SUPPLIER"] },
          { path: "/engineering-outsourcing/providers", name: "服务商管理", roles: appRoles, appCode: "OUTSOURCING", hiddenForRoles: ["SUPPLIER"] },
          { path: "/engineering-outsourcing/catalog", name: "服务项与价格", roles: appRoles, appCode: "OUTSOURCING", hiddenForRoles: ["SUPPLIER"] },
          { path: "/engineering-outsourcing/config", name: "机房外包配置", roles: appRoles, appCode: "OUTSOURCING", hiddenForRoles: ["SUPPLIER"] },
          { path: "/engineering-outsourcing/analytics", name: "履约分析", roles: appRoles, appCode: "OUTSOURCING", hiddenForRoles: ["SUPPLIER"] },
          { path: "/engineering-outsourcing/settings", name: "外包设置", roles: appRoles, appCode: "OUTSOURCING", hiddenForRoles: ["SUPPLIER"] },
          { path: "/engineering-outsourcing/workbench", name: "协同工作台", roles: supplierRoles, appCode: "OUTSOURCING" },
          { path: "/engineering-outsourcing/orders", name: "我的工单", roles: supplierRoles, appCode: "OUTSOURCING" },
          { path: "/engineering-outsourcing/entry", name: "入场预约", roles: supplierRoles, appCode: "OUTSOURCING" },
          { path: "/engineering-outsourcing/workload", name: "工作量填报", roles: supplierRoles, appCode: "OUTSOURCING" },
          { path: "/engineering-outsourcing/acceptance-results", name: "验收结果", roles: supplierRoles, appCode: "OUTSOURCING" },
          { path: "/engineering-outsourcing/settlement", name: "账单对账", roles: supplierRoles, appCode: "OUTSOURCING" },
          { path: "/engineering-outsourcing/profile", name: "服务能力档案", roles: supplierRoles, appCode: "OUTSOURCING" }
        ]
      }]
    : []),
  {
    path: "/disaster-weather",
    name: "灾害预警",
    roles: adminRoles,
    hiddenForRoles: ["SUPPLIER"],
    alwaysVisible: true,
    children: [
      { path: "/disaster-weather/records", name: "预警记录", roles: adminRoles, alwaysVisible: true },
      { path: "/disaster-weather/settings", name: "预警设置", roles: adminRoles, alwaysVisible: true }
    ]
  },
  {
    path: "/platform",
    name: "平台管理",
    roles: appRoles,
    hiddenForRoles: ["SUPPLIER"],
    alwaysVisible: true,
    children: [
      { path: "/platform/access", name: "我的权限", roles: appRoles, alwaysVisible: true },
      { path: "/platform/users", name: "用户管理", roles: appRoles, appCode: "PLATFORM" },
      { path: "/platform/roles", name: "角色管理", roles: adminRoles, appCode: "PLATFORM" },
      { path: "/platform/admin", name: "权限管理", roles: appRoles, appCode: "PLATFORM" },
      { path: "/platform/integration-settings", name: "平台集成设置", roles: adminRoles, appCode: "PLATFORM" },
      { path: "/platform/procurement-rule-settings", name: "采购规则设置", roles: adminRoles, appCode: "PLATFORM" },
      { path: "/platform/asset-catalog", name: "资产目录管理", roles: adminRoles, appCode: "PLATFORM" },
      { path: "/platform/ai-governance", name: "AI知识与技能", roles: adminRoles, permissions: ["platform_ai_governance:manage"], appCode: "PLATFORM" },
      { path: "/platform/departments", name: "部门管理", roles: adminRoles, permissions: ["procurement_route:manage", "procurement_route:address", "procurement_route:import"], appCode: "PLATFORM" },
      ...(testEnvironment
        ? [{ path: "/platform/release-center", name: "上线管理", roles: ["SUPER_ADMIN"], appCode: "PLATFORM" }]
        : []),
      { path: "/platform/system-updates", name: "系统更新", roles: adminRoles, appCode: "PLATFORM" },
      { path: "/platform/error-logs", name: "错误台账", roles: adminRoles, appCode: "PLATFORM" },
      { path: "/platform/ai-message-logs", name: "AI助手消息日志", roles: adminRoles, permissions: ["platform_ai_message_log:manage"], appCode: "PLATFORM" },
      { path: "/platform/import-file-logs", name: "导入文件记录", roles: adminRoles, permissions: ["platform_import_file_log:manage"], appCode: "PLATFORM" },
      { path: "/platform/feedbacks", name: "意见反馈", roles: adminRoles, permissions: ["platform_feedback:manage"], appCode: "PLATFORM" },
      { path: "/platform/database", name: "数据库管理", roles: adminRoles, appCode: "PLATFORM" }
    ]
  },
  {
    path: "/procurement",
    name: "采购管理",
    roles: appRoles,
    appCode: "PROCUREMENT",
    children: [
      { path: "/procurement/dashboard", name: "采购看板", roles: internalProcurementRoles, appCode: "PROCUREMENT" },
      { path: "/procurement/monthly-demands", name: "本月需求", roles: internalProcurementRoles, appCode: "PROCUREMENT" },
      { path: "/procurement/requests", name: "采购申请", roles: internalProcurementRoles, appCode: "PROCUREMENT" },
      { path: "/procurement/personal-labor", name: "个人劳保申领", roles: internalProcurementRoles, appCode: "PROCUREMENT" },
      { path: "/procurement/orders", name: "我的订单", roles: internalProcurementRoles, appCode: "PROCUREMENT" },
      { path: "/procurement/approval", name: "审批操作", roles: internalProcurementRoles, appCode: "PROCUREMENT" },
      { path: "/procurement/suppliers", name: "供应商管理", roles: adminRoles, appCode: "PROCUREMENT" },
      { path: "/procurement/temporary-supplier-recommendations", name: "推荐供应商审批", roles: internalProcurementRoles, appCode: "PROCUREMENT" },
      { path: "/procurement/sold-orders", name: "已售管理", roles: supplierRoles, appCode: "PROCUREMENT" },
      { path: "/procurement/quotes", name: "供应商报价", roles: supplierQuoteRoles, appCode: "PROCUREMENT" }
    ]
  }
];

export function filterMenusByRoles(menus: AppMenuItem[], roleCodes: string[], appCodes: string[] = [], permissionCodes: string[] = []): AppMenuItem[] {
  const effectiveRoleCodes = roleCodes.map((role) => role.trim().toUpperCase());
  const effectiveAppCodes = appCodes.map((code) => code.trim().toUpperCase());
  const hasRole = (roles: string[]): boolean =>
    roles.length === 0 || roles.some((role) => effectiveRoleCodes.includes(role.trim().toUpperCase()));
  const hasPermission = (permissions?: string[]): boolean => {
    if (!permissions || permissions.length === 0) {
      return false;
    }
    return permissionCodes.includes("*") || permissionCodes.includes("*:*:*") || permissions.some((permission) => permissionCodes.includes(permission));
  };
  const hiddenForRole = (menu: AppMenuItem): boolean =>
    Boolean(menu.hiddenForRoles?.some((role) => effectiveRoleCodes.includes(role.trim().toUpperCase())));
  const hasApp = (menu: AppMenuItem): boolean => {
    if (menu.alwaysVisible) {
      return true;
    }
    if (menu.appCode) {
      return effectiveAppCodes.includes(menu.appCode.trim().toUpperCase());
    }
    if (menu.anyAppCodes && menu.anyAppCodes.length > 0) {
      return menu.anyAppCodes.some((code) => effectiveAppCodes.includes(code.trim().toUpperCase()));
    }
    return true;
  };

  return menus
    .filter((menu) => (hasRole(menu.roles) || hasPermission(menu.permissions)) && !hiddenForRole(menu))
    .map((menu) => {
      const children = menu.children ? filterMenusByRoles(menu.children, roleCodes, appCodes, permissionCodes) : undefined;
      return {
        ...menu,
        children: children?.filter((child) => !child.hidden)
      };
    })
    .filter((menu) => !menu.hidden && (hasApp(menu) || Boolean(menu.children?.length)));
}
