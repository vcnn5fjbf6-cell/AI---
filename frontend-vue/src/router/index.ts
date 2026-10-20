import { createRouter, createWebHistory } from "vue-router";
import { isTestEnvironment } from "../config/runtime-environment";
import { useAuthStore } from "../stores/auth";
import { resolveAccessHomePath } from "../utils/access-home";

const maintenanceRoles: string[] = [];
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

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: "/login",
      name: "Login",
      component: () => import("../pages/login/LoginPage.vue")
    },
    {
      path: "/m",
      redirect: "/m/login"
    },
    {
      path: "/m/login",
      name: "MobileLogin",
      component: () => import("../pages/mobile/MobileLoginPage.vue"),
      meta: { title: "手机登录", public: true }
    },
    ...(testEnvironment
      ? [
          { path: "/ehs-public", redirect: "/ehs-public/apply" },
          {
            path: "/ehs-public/apply",
            name: "EhsPublicApply",
            component: () => import("../pages/ehs/EhsPublicPermitPage.vue"),
            meta: { title: "EHS 作业许可申请", public: true }
          },
          {
            path: "/ehs-public/permit",
            name: "EhsPublicPermit",
            component: () => import("../pages/ehs/EhsPublicPermitPage.vue"),
            meta: { title: "EHS 作业许可进度", public: true }
          },
          {
            path: "/ehs-public/review",
            name: "EhsPublicReview",
            component: () => import("../pages/ehs/EhsPublicPermitPage.vue"),
            meta: { title: "EHS 作业票审批", public: true }
          }
        ]
      : []),
    {
      path: "/feishu/procurement-intake",
      name: "FeishuProcurementIntake",
      component: () => import("../pages/feishu/FeishuProcurementIntake.vue"),
      meta: { title: "采购需求", roles: internalProcurementRoles, appCode: "PROCUREMENT" }
    },
    {
      path: "/",
      component: () => import("../layout/AppLayout.vue"),
      children: [
        {
          path: "/workbench",
          name: "ApprovalWorkbench",
          component: () => import("../pages/workbench/WorkbenchPage.vue"),
          meta: { title: "工作台", roles: maintenanceRoles, anyAppCodes: ["PROCUREMENT", "PLATFORM", "MAINTENANCE", "EHS", "OUTSOURCING"] }
        },
        {
          path: "/hr-management",
          redirect: "/hr/dashboard",
          meta: { hiddenForRoles: ["SUPPLIER"] }
        },
        {
          path: "/hr",
          redirect: "/hr/dashboard",
          meta: { hiddenForRoles: ["SUPPLIER"] }
        },
        {
          path: "/hr/dashboard",
          name: "HrDashboard",
          component: () => import("../pages/hr/HrDashboard.vue"),
          meta: { roles: hrAdminRoles, title: "人力资源数据看板", hiddenForRoles: ["SUPPLIER"] }
        },
        {
          path: "/hr/employees",
          name: "HrEmployees",
          component: () => import("../pages/hr/HrEmployees.vue"),
          meta: { roles: hrLeaderRoles, title: "人力资源", hiddenForRoles: ["SUPPLIER"] }
        },
        {
          path: "/hr/attendance",
          name: "HrAttendance",
          component: () => import("../pages/hr/HrAttendancePage.vue"),
          props: { tab: "attendance" },
          meta: { title: "考勤管理", hiddenForRoles: ["SUPPLIER"] }
        },
        { path: "/hr/leave", redirect: "/hr/attendance", meta: { hiddenForRoles: ["SUPPLIER"] } },
        { path: "/hr/overtime", redirect: "/hr/attendance", meta: { hiddenForRoles: ["SUPPLIER"] } },
        {
          path: "/hr/performance",
          name: "HrPerformance",
          component: () => import("../pages/hr/HrPerformance.vue"),
          meta: { roles: hrAdminRoles, title: "绩效管理", hiddenForRoles: ["SUPPLIER"] }
        },
        {
          path: "/hr/cost",
          name: "HrCost",
          component: () => import("../pages/hr/HrCost.vue"),
          meta: { roles: hrAdminRoles, title: "成本管理", hiddenForRoles: ["SUPPLIER"] }
        },
        { path: "/hr/org", redirect: "/hr/dashboard", meta: { hiddenForRoles: ["SUPPLIER"] } },
        {
          path: "/hr/ai",
          name: "HrAiAssistant",
          component: () => import("../pages/hr/HrAiAssistant.vue"),
          meta: { roles: hrAdminRoles, title: "AI 智能问答", hiddenForRoles: ["SUPPLIER"] }
        },
        {
          path: "/hr/permission",
          name: "HrPermission",
          component: () => import("../pages/hr/HrPermission.vue"),
          meta: { roles: hrAdminRoles, title: "人员权限分配", hiddenForRoles: ["SUPPLIER"] }
        },
        {
          path: "/hr/settings",
          name: "HrSettings",
          component: () => import("../pages/hr/HrSettings.vue"),
          meta: { roles: hrAdminRoles, title: "系统设置", hiddenForRoles: ["SUPPLIER"] }
        },
        ...(testEnvironment
          ? [
              {
                path: "/developer-center",
                name: "DeveloperCenter",
                component: () => import("../pages/platform/DeveloperCenterPage.vue"),
                meta: {
                  title: "开发者中心",
                  roles: maintenanceRoles,
                  anyAppCodes: ["PROCUREMENT", "PLATFORM", "MAINTENANCE", "EHS"],
                  hiddenForRoles: ["SUPPLIER"]
                }
              }
            ]
          : []),
        ...(testEnvironment
          ? [
              {
                path: "/ehs",
                redirect: "/ehs/dashboard"
              },
              {
                path: "/ehs/dashboard",
                name: "EhsDashboard",
                component: () => import("../pages/ehs/EhsDashboard.vue"),
                meta: { title: "EHS 总览", roles: maintenanceRoles, appCode: "EHS" }
              },
              {
                path: "/ehs/hazards",
                name: "EhsHazards",
                component: () => import("../pages/ehs/EhsRegistryPage.vue"),
                props: { moduleKey: "hazards" },
                meta: { title: "隐患管理", roles: maintenanceRoles, appCode: "EHS" }
              },
              {
                path: "/ehs/waste",
                name: "EhsWaste",
                component: () => import("../pages/ehs/EhsRegistryPage.vue"),
                props: { moduleKey: "waste" },
                meta: { title: "环境与危废", roles: maintenanceRoles, appCode: "EHS" }
              },
              {
                path: "/ehs/ppe",
                name: "EhsPpe",
                component: () => import("../pages/ehs/EhsRegistryPage.vue"),
                props: { moduleKey: "ppe" },
                meta: { title: "PPE 与应急物资", roles: maintenanceRoles, appCode: "EHS" }
              },
              {
                path: "/ehs/incidents",
                name: "EhsIncidents",
                component: () => import("../pages/ehs/EhsRegistryPage.vue"),
                props: { moduleKey: "incidents" },
                meta: { title: "事故事件", roles: maintenanceRoles, appCode: "EHS" }
              },
              {
                path: "/ehs/permits",
                name: "EhsPermits",
                component: () => import("../pages/ehs/EhsPermitPage.vue"),
                meta: { title: "作业许可", roles: maintenanceRoles, appCode: "EHS" }
              },
              {
                path: "/ehs/assistant",
                name: "EhsAssistant",
                component: () => import("../pages/ehs/EhsAssistantPage.vue"),
                meta: { title: "EHS 智能助手", roles: maintenanceRoles, appCode: "EHS" }
              },
              {
                path: "/ehs/settings",
                name: "EhsSettings",
                component: () => import("../pages/ehs/EhsSettingsPage.vue"),
                meta: { title: "EHS 运行设置", roles: adminRoles, appCode: "EHS" }
              }
            ]
          : []),
        ...(testEnvironment
          ? [
              {
                path: "/engineering-outsourcing",
                redirect: "/engineering-outsourcing/workbench"
              },
              {
                path: "/engineering-outsourcing/workbench",
                name: "EngineeringOutsourcingWorkbench",
                component: () => import("../pages/outsourcing/EngineeringOutsourcingWorkbench.vue"),
                meta: {
                  title: "IT工程外包看板",
                  roles: maintenanceRoles,
                  appCode: "OUTSOURCING",
                  outsourcingPage: "dashboard"
                }
              },
              {
                path: "/engineering-outsourcing/orders",
                name: "EngineeringOutsourcingOrders",
                component: () => import("../pages/outsourcing/EngineeringOutsourcingWorkbench.vue"),
                meta: { title: "工单管理", roles: maintenanceRoles, appCode: "OUTSOURCING", outsourcingPage: "orders" }
              },
              {
                path: "/engineering-outsourcing/entry",
                name: "EngineeringOutsourcingEntry",
                component: () => import("../pages/outsourcing/EngineeringOutsourcingWorkbench.vue"),
                meta: { title: "入场与人员", roles: maintenanceRoles, appCode: "OUTSOURCING", outsourcingPage: "entry" }
              },
              {
                path: "/engineering-outsourcing/acceptance",
                name: "EngineeringOutsourcingAcceptance",
                component: () => import("../pages/outsourcing/EngineeringOutsourcingWorkbench.vue"),
                meta: { title: "验收管理", roles: maintenanceRoles, hiddenForRoles: ["SUPPLIER"], appCode: "OUTSOURCING", outsourcingPage: "acceptance" }
              },
              {
                path: "/engineering-outsourcing/settlement",
                name: "EngineeringOutsourcingSettlement",
                component: () => import("../pages/outsourcing/EngineeringOutsourcingWorkbench.vue"),
                meta: { title: "结算与对账", roles: maintenanceRoles, appCode: "OUTSOURCING", outsourcingPage: "settlement" }
              },
              {
                path: "/engineering-outsourcing/providers",
                name: "EngineeringOutsourcingProviders",
                component: () => import("../pages/outsourcing/EngineeringOutsourcingWorkbench.vue"),
                meta: { title: "服务商管理", roles: maintenanceRoles, hiddenForRoles: ["SUPPLIER"], appCode: "OUTSOURCING", outsourcingPage: "providers" }
              },
              {
                path: "/engineering-outsourcing/catalog",
                name: "EngineeringOutsourcingCatalog",
                component: () => import("../pages/outsourcing/EngineeringOutsourcingWorkbench.vue"),
                meta: { title: "服务项与价格", roles: maintenanceRoles, hiddenForRoles: ["SUPPLIER"], appCode: "OUTSOURCING", outsourcingPage: "catalog" }
              },
              {
                path: "/engineering-outsourcing/config",
                name: "EngineeringOutsourcingConfig",
                component: () => import("../pages/outsourcing/EngineeringOutsourcingWorkbench.vue"),
                meta: { title: "机房外包配置", roles: maintenanceRoles, hiddenForRoles: ["SUPPLIER"], appCode: "OUTSOURCING", outsourcingPage: "config" }
              },
              {
                path: "/engineering-outsourcing/analytics",
                name: "EngineeringOutsourcingAnalytics",
                component: () => import("../pages/outsourcing/EngineeringOutsourcingWorkbench.vue"),
                meta: { title: "履约分析", roles: maintenanceRoles, hiddenForRoles: ["SUPPLIER"], appCode: "OUTSOURCING", outsourcingPage: "analytics" }
              },
              {
                path: "/engineering-outsourcing/settings",
                name: "EngineeringOutsourcingSettings",
                component: () => import("../pages/outsourcing/EngineeringOutsourcingWorkbench.vue"),
                meta: { title: "外包设置", roles: maintenanceRoles, hiddenForRoles: ["SUPPLIER"], appCode: "OUTSOURCING", outsourcingPage: "settings" }
              },
              {
                path: "/engineering-outsourcing/workload",
                name: "EngineeringOutsourcingSupplierWorkload",
                component: () => import("../pages/outsourcing/EngineeringOutsourcingWorkbench.vue"),
                meta: { title: "工作量填报", roles: supplierRoles, appCode: "OUTSOURCING", outsourcingPage: "supplier-workload" }
              },
              {
                path: "/engineering-outsourcing/acceptance-results",
                name: "EngineeringOutsourcingSupplierAcceptance",
                component: () => import("../pages/outsourcing/EngineeringOutsourcingWorkbench.vue"),
                meta: { title: "验收结果", roles: supplierRoles, appCode: "OUTSOURCING", outsourcingPage: "supplier-acceptance" }
              },
              {
                path: "/engineering-outsourcing/profile",
                name: "EngineeringOutsourcingSupplierProfile",
                component: () => import("../pages/outsourcing/EngineeringOutsourcingWorkbench.vue"),
                meta: { title: "服务能力档案", roles: supplierRoles, appCode: "OUTSOURCING", outsourcingPage: "supplier-profile" }
              }
            ]
          : []),
        {
          path: "/disaster-weather",
          redirect: "/disaster-weather/records"
        },
        {
          path: "/disaster-weather/records",
          name: "DisasterWeather",
          component: () => import("../pages/disaster/DisasterWeatherPage.vue"),
          meta: { title: "预警记录", roles: adminRoles }
        },
        {
          path: "/disaster-weather/settings",
          name: "DisasterWeatherSettings",
          component: () => import("../pages/disaster/DisasterWeatherSettingsPage.vue"),
          meta: { title: "预警设置", roles: adminRoles }
        },
        {
          path: "/dashboard",
          name: "DashboardRedirect",
          component: () => import("../pages/platform/PlatformAccess.vue"),
          meta: { title: "工作台", roles: maintenanceRoles }
        },
        {
          path: "/sources",
          redirect: "/maintenance/sources"
        },
        {
          path: "/upload",
          redirect: "/maintenance/upload"
        },
        {
          path: "/assets",
          redirect: "/maintenance/assets"
        },
        {
          path: "/renewal",
          redirect: "/maintenance/renewal"
        },
        {
          path: "/settings/people",
          redirect: "/maintenance/settings/people"
        },
        {
          path: "/settings/rooms",
          redirect: "/maintenance/settings/rooms"
        },
        {
          path: "/maintenance/dashboard",
          name: "MaintenanceDashboard",
          component: () => import("../pages/maintenance/MaintenanceDashboard.vue"),
          meta: { title: "维保总览", roles: maintenanceRoles, appCode: "MAINTENANCE" }
        },
        {
          path: "/maintenance/sources",
          name: "MaintenanceSources",
          component: () => import("../pages/maintenance/MaintenanceSources.vue"),
          meta: { title: "维保来源", roles: maintenanceRoles, appCode: "MAINTENANCE" }
        },
        {
          path: "/maintenance/upload",
          name: "MaintenanceUpload",
          component: () => import("../pages/maintenance/MaintenanceUpload.vue"),
          meta: { title: "合同 / 订单上传", roles: maintenanceRoles, appCode: "MAINTENANCE" }
        },
        {
          path: "/review/:parseId",
          redirect: (to) => `/maintenance/review/${to.params.parseId}`
        },
        {
          path: "/maintenance/review/:parseId",
          name: "MaintenanceReview",
          component: () => import("../pages/maintenance/MaintenanceReview.vue"),
          meta: { title: "AI 拆解确认", roles: maintenanceRoles, appCode: "MAINTENANCE" }
        },
        {
          path: "/maintenance/assets",
          name: "MaintenanceAssets",
          component: () => import("../pages/maintenance/MaintenanceAssets.vue"),
          meta: { title: "设备维保台账", roles: maintenanceRoles, appCode: "MAINTENANCE" }
        },
        {
          path: "/maintenance/renewal",
          name: "MaintenanceRenewal",
          component: () => import("../pages/maintenance/MaintenanceRenewal.vue"),
          meta: { title: "续签 / 续采跟踪", roles: maintenanceRoles, appCode: "MAINTENANCE" }
        },
        {
          path: "/maintenance/settings/people",
          name: "MaintenancePeople",
          component: () => import("../pages/maintenance/settings/MaintenancePeople.vue"),
          meta: { title: "权限设置 / 人员信息配置", roles: maintenanceRoles, appCode: "MAINTENANCE" }
        },
        {
          path: "/maintenance/settings/rooms",
          name: "MaintenanceRooms",
          component: () => import("../pages/maintenance/settings/MaintenanceRooms.vue"),
          meta: { title: "权限设置 / 机房分类配置", roles: maintenanceRoles, appCode: "MAINTENANCE" }
        },
        {
          path: "/platform/access",
          name: "PlatformAccess",
          component: () => import("../pages/platform/PlatformAccess.vue"),
          meta: { title: "我的权限", roles: maintenanceRoles }
        },
        {
          path: "/platform/users",
          name: "PlatformUsers",
          component: () => import("../pages/system/UserManagement.vue"),
          meta: { title: "用户管理", roles: maintenanceRoles, appCode: "PLATFORM" }
        },
        {
          path: "/platform/roles",
          name: "PlatformRoles",
          component: () => import("../pages/system/RoleManagement.vue"),
          meta: { title: "角色管理", roles: adminRoles, appCode: "PLATFORM" }
        },
        {
          path: "/platform/admin",
          name: "PlatformPermissionAdmin",
          component: () => import("../pages/platform/PlatformPermissionAdmin.vue"),
          meta: { title: "平台权限管理", roles: maintenanceRoles, appCode: "PLATFORM" }
        },
        {
          path: "/platform/procurement-settings",
          redirect: "/platform/integration-settings"
        },
        {
          path: "/platform/integration-settings",
          name: "PlatformIntegrationSettings",
          component: () => import("../pages/platform/ProcurementSettings.vue"),
          props: { viewMode: "integration" },
          meta: { title: "平台集成设置", roles: adminRoles, appCode: "PLATFORM" }
        },
        {
          path: "/platform/procurement-rule-settings",
          name: "PlatformProcurementRuleSettings",
          component: () => import("../pages/platform/ProcurementSettings.vue"),
          props: { viewMode: "rules" },
          meta: { title: "采购规则设置", roles: adminRoles, appCode: "PLATFORM" }
        },
        {
          path: "/platform/asset-catalog",
          name: "PlatformAssetCatalog",
          component: () => import("../pages/platform/AssetCatalogManagement.vue"),
          meta: { title: "资产目录管理", roles: adminRoles, appCode: "PLATFORM" }
        },
        {
          path: "/platform/ai-governance",
          name: "PlatformAiGovernance",
          component: () => import("../pages/platform/AiGovernancePage.vue"),
          meta: { title: "AI知识与技能", roles: adminRoles, permissions: ["platform_ai_governance:manage"], appCode: "PLATFORM" }
        },
        {
          path: "/platform/departments",
          name: "PlatformDepartments",
          component: () => import("../pages/platform/ProcurementApprovalRoutes.vue"),
          meta: { title: "部门管理", roles: adminRoles, permissions: ["procurement_route:manage", "procurement_route:address", "procurement_route:import"], appCode: "PLATFORM" }
        },
        {
          path: "/platform/procurement-approval-routes",
          redirect: "/platform/departments"
        },
        ...(testEnvironment
          ? [
              {
                path: "/platform/release-center",
                name: "TestReleaseCenter",
                component: () => import("../pages/platform/TestReleaseCenterPage.vue"),
                meta: { title: "上线管理", roles: ["SUPER_ADMIN"], appCode: "PLATFORM" }
              }
            ]
          : []),
        {
          path: "/platform/system-updates",
          name: "SystemUpdates",
          component: () => import("../pages/platform/SystemUpdatesPage.vue"),
          meta: { title: "系统更新", roles: adminRoles, appCode: "PLATFORM" }
        },
        {
          path: "/platform/error-logs",
          name: "SystemErrorLogs",
          component: () => import("../pages/platform/SystemErrorLogPage.vue"),
          meta: { title: "错误台账", roles: adminRoles, appCode: "PLATFORM" }
        },
        {
          path: "/platform/ai-message-logs",
          name: "ProcurementAiMessageLogs",
          component: () => import("../pages/platform/ProcurementAiMessageLogPage.vue"),
          meta: { title: "AI助手消息日志", roles: adminRoles, permissions: ["platform_ai_message_log:manage"], appCode: "PLATFORM" }
        },
        {
          path: "/platform/import-file-logs",
          name: "ImportFileLogs",
          component: () => import("../pages/platform/ImportFileLogPage.vue"),
          meta: { title: "导入文件记录", roles: adminRoles, permissions: ["platform_import_file_log:manage"], appCode: "PLATFORM" }
        },
        {
          path: "/platform/feedbacks",
          name: "PlatformFeedbacks",
          component: () => import("../pages/platform/PlatformFeedbackPage.vue"),
          meta: { title: "意见反馈", roles: adminRoles, permissions: ["platform_feedback:manage"], appCode: "PLATFORM" }
        },
        {
          path: "/platform/database",
          name: "DatabaseManagement",
          component: () => import("../pages/platform/DatabaseManagement.vue"),
          meta: { title: "数据库管理", roles: adminRoles, appCode: "PLATFORM" }
        },
        {
          path: "/procurement/dashboard",
          name: "ProcurementDashboard",
          component: () => import("../pages/procurementVnet/ProcurementDashboard.vue"),
          meta: { title: "采购看板", roles: internalProcurementRoles, appCode: "PROCUREMENT" }
        },
        {
          path: "/procurement/monthly-demands",
          name: "ProcurementMonthlyDemands",
          component: () => import("../pages/procurementVnet/MonthlyDemands.vue"),
          meta: { title: "本月需求", roles: internalProcurementRoles, appCode: "PROCUREMENT" }
        },
        {
          path: "/procurement/requests",
          name: "ProcurementRequests",
          component: () => import("../pages/procurementVnet/ProcurementRequests.vue"),
          meta: { title: "采购申请", roles: internalProcurementRoles, appCode: "PROCUREMENT" }
        },
        {
          path: "/procurement/requests/create",
          name: "ProcurementRequestCreate",
          component: () => import("../pages/procurementVnet/ProcurementRequestForm.vue"),
          meta: { title: "新建采购申请", roles: internalProcurementRoles, appCode: "PROCUREMENT" }
        },
        {
          path: "/procurement/personal-labor",
          name: "ProcurementPersonalLabor",
          component: () => import("../pages/procurementVnet/PersonalLaborRequests.vue"),
          meta: { title: "个人劳保申领", roles: internalProcurementRoles, appCode: "PROCUREMENT" }
        },
        {
          path: "/procurement/requests/:id/edit",
          name: "ProcurementRequestEdit",
          component: () => import("../pages/procurementVnet/ProcurementRequestForm.vue"),
          meta: { title: "编辑采购申请", roles: internalProcurementRoles, appCode: "PROCUREMENT" }
        },
        {
          path: "/procurement/orders",
          name: "ProcurementMyOrders",
          component: () => import("../pages/procurementVnet/MyOrders.vue"),
          meta: { title: "我的订单", roles: internalProcurementRoles, appCode: "PROCUREMENT" }
        },
        {
          path: "/procurement/sold-orders",
          name: "ProcurementSoldOrders",
          component: () => import("../pages/procurementVnet/MyOrders.vue"),
          meta: { title: "已售管理", roles: supplierRoles, appCode: "PROCUREMENT" }
        },
        {
          path: "/procurement/approval",
          name: "ProcurementApproval",
          component: () => import("../pages/procurementVnet/ProcurementApproval.vue"),
          meta: { title: "审批操作", roles: internalProcurementRoles, appCode: "PROCUREMENT" }
        },
        {
          path: "/procurement/suppliers",
          name: "ProcurementSuppliers",
          component: () => import("../pages/supplier/SupplierManagement.vue"),
          meta: { title: "供应商管理", roles: internalProcurementRoles, appCode: "PROCUREMENT" }
        },
        {
          path: "/procurement/temporary-supplier-recommendations",
          name: "TemporarySupplierRecommendations",
          component: () => import("../pages/supplier/TemporarySupplierRecommendations.vue"),
          meta: { title: "推荐供应商审批", roles: internalProcurementRoles, appCode: "PROCUREMENT" }
        },
        {
          path: "/procurement/quotes",
          name: "ProcurementQuotes",
          component: () => import("../pages/supplier/QuoteList.vue"),
          meta: { title: "供应商报价", roles: supplierQuoteRoles, appCode: "PROCUREMENT" }
        }
      ]
    },
    {
      path: "/:pathMatch(.*)*",
      redirect: "/dashboard"
    }
  ]
});

router.beforeEach(async (to) => {
  const authStore = useAuthStore();
  const isFeishuCallback =
    to.path === "/login" && typeof to.query.code === "string" && typeof to.query.state === "string";

  if (to.meta.public === true) {
    return true;
  }

  if (!authStore.isLoggedIn && to.path !== "/login") {
    return { path: "/login", query: { redirect: to.fullPath } };
  }

  if (isFeishuCallback) {
    return true;
  }

  if (authStore.isLoggedIn && !authStore.profileLoaded) {
    try {
      await authStore.fetchProfile();
    } catch {
      authStore.logoutLocal();
      return { path: "/login", query: { redirect: to.fullPath } };
    }
  }

  if (to.path === "/login" && authStore.isLoggedIn) {
    return { path: resolveAccessHomePath(authStore.appCodes, authStore.effectiveRoleCodes) };
  }

  if (authStore.isLoggedIn && (to.path === "/" || to.path === "/dashboard")) {
    return { path: resolveAccessHomePath(authStore.appCodes, authStore.effectiveRoleCodes) };
  }

  const routeHiddenForRoles = to.meta.hiddenForRoles as string[] | undefined;
  if (routeHiddenForRoles?.length && authStore.hasAnyRole(routeHiddenForRoles)) {
    return { path: resolveAccessHomePath(authStore.appCodes, authStore.effectiveRoleCodes) };
  }

  const routeRoles = to.meta.roles as string[] | undefined;
  const routePermissions = to.meta.permissions as string[] | undefined;
  const hasRoleAccess = !routeRoles || routeRoles.length === 0 || authStore.hasAnyRole(routeRoles);
  const hasPermissionAccess = !!routePermissions && routePermissions.length > 0 && authStore.hasAnyPermission(routePermissions);
  if (!hasRoleAccess && !hasPermissionAccess) {
    return { path: resolveAccessHomePath(authStore.appCodes, authStore.effectiveRoleCodes) };
  }

  const routeAppCode = to.meta.appCode as string | undefined;
  if (routeAppCode && !authStore.hasApp(routeAppCode)) {
    return { path: "/platform/access" };
  }

  const routeAnyAppCodes = to.meta.anyAppCodes as string[] | undefined;
  if (routeAnyAppCodes && routeAnyAppCodes.length > 0 && !routeAnyAppCodes.some((code) => authStore.hasApp(code))) {
    return { path: "/platform/access" };
  }

  return true;
});

export default router;
