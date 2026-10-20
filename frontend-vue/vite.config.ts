import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";

export default defineConfig({
  plugins: [
    vue(),
    {
      name: "dev-auth-mock",
      configureServer(server) {
        const authMockEnabled = ["1", "true", "TRUE"].includes(process.env.VITE_AUTH_MOCK ?? "");
        if (!authMockEnabled) {
          return;
        }

        const mockUsername = process.env.VITE_AUTH_MOCK_USERNAME || "dev-admin";
        const mockPassword = process.env.VITE_AUTH_MOCK_PASSWORD;

        const mockUser = {
          userId: 1,
          username: mockUsername,
          realName: "系统管理员",
          userType: "SUPER_ADMIN",
          regionId: 1,
          supplierId: null,
          dataScopeType: "ALL",
          roleCodes: ["SUPER_ADMIN"],
          permissionCodes: ["*:*:*"],
          appCodes: ["MAINTENANCE", "PROCUREMENT", "PLATFORM", "EHS", "OUTSOURCING"]
        };

        const sendJson = (res: any, payload: unknown, statusCode = 200) => {
          res.statusCode = statusCode;
          res.setHeader("Content-Type", "application/json; charset=utf-8");
          res.end(JSON.stringify(payload));
        };

        const readBody = (req: any): Promise<Record<string, unknown>> =>
          new Promise((resolve) => {
            let body = "";
            req.on("data", (chunk: Buffer) => {
              body += chunk.toString("utf-8");
            });
            req.on("end", () => {
              try {
                resolve(body ? JSON.parse(body) : {});
              } catch {
                resolve({});
              }
            });
          });

        server.middlewares.use(async (req, res, next) => {
          if (req.method === "GET" && req.url?.startsWith("/api/admin/procurement/approval-routes/config")) {
            sendJson(res, {
              success: true,
              code: "OK",
              message: "success",
              data: {
                routes: [
                  {
                    id: 1,
                    enabled: 1,
                    routeKey: "酒仙桥|M6|N420N00077",
                    roomNature: "自建",
                    roomName: "酒仙桥",
                    buildingName: "M6",
                    costCenterCode: "N420N00077",
                    deptCode: "224",
                    deptName: "[N420N00077]世纪互联-业务共享中台-DC运维事业部-华北一部-酒仙桥-M6",
                    floorLeaderNames: "赵新,马金礼",
                    floorLeaderRule: "任意一人通过",
                    managerNames: "张强,刘燚",
                    managerRule: "任意一人通过",
                    regionName: "华北一",
                    regionManagerNames: "王郑建",
                    regionManagerRule: "默认全部通过/可改任意一人",
                    directorNames: "杨磊",
                    directorRule: "默认全部通过/可改任意一人",
                    poolPushDay: 20,
                    regionThreshold: 5000,
                    directorThreshold: 100000,
                    overPriceRule: "单项实际价 > 参考价 * 113%",
                    replacementConfirmNames: "赵新,马金礼",
                    supplierQuoteScope: "仅可见推送给自己的商品明细",
                    remark: "M6示例：楼长赵新/马金礼，经理张强/刘燚，区域经理王郑建，总监杨磊",
                    updatedAt: new Date().toISOString()
                  }
                ],
                workflowPaths: [
                  {
                    code: "STANDARD_MONTHLY",
                    name: "常规月度采购",
                    source: "需求人提交",
                    scope: "楼长任一人审批通过后进入需求池",
                    permissions: "每月20日推送经理；经理/区域经理/总监按未拒绝物品合计金额逐级审批",
                    rule: "通过后推送供应商报价；中标单价高于参考价13%由楼长24小时内复核；供应商换商品回楼长确认",
                    remark: ""
                  },
                  {
                    code: "EMERGENCY_DIRECT",
                    name: "紧急采购",
                    source: "需求人标记紧急提交",
                    scope: "跳过月度需求池",
                    permissions: "楼长任一人审批通过后直接推送供应商报价",
                    rule: "后续仍执行高于参考价13%楼长复核、供应商换商品楼长确认、供应商备货",
                    remark: ""
                  }
                ],
                rolePermissions: [
                  { code: "REQUESTER", name: "需求人", source: "系统用户/部门成员", scope: "本人提交的数据", permissions: "提交需求、查看状态、查看单项拒绝原因", rule: "申请提交后按机房/楼栋匹配审批路由", remark: "" },
                  { code: "FLOOR_LEADER", name: "楼长", source: "机房主数据：楼长", scope: "负责机房/楼栋", permissions: "需求初审、供应商换商品确认", rule: "多人候选时任意一人通过即可", remark: "" },
                  { code: "MANAGER", name: "经理", source: "机房主数据：机房负责人", scope: "负责机房/楼栋", permissions: "需求池审核、拒绝单项、超价复审", rule: "多人候选时任意一人通过即可", remark: "" },
                  { code: "REGION_MANAGER", name: "区域经理", source: "机房主数据：区域负责人", scope: "所属区域", permissions: "大于5000元有效金额审批、拒绝单项", rule: "按区域负责人配置", remark: "" },
                  { code: "SENIOR_MANAGER", name: "总监", source: "后台角色配置", scope: "后台授权范围", permissions: "大于10万元有效金额审批、拒绝单项", rule: "原机房表没有字段，需要后台维护", remark: "" },
                  { code: "SUPPLIER", name: "供应商", source: "供应商账号/后台绑定", scope: "仅推送给自己的商品", permissions: "报价、提交换商品说明、确认备货", rule: "不能查看内部审批意见和其他供应商报价", remark: "" }
                ],
                approvalRules: [
                  { code: "POOL_PUSH_DAY", name: "需求池经理推送日", source: "采购规则", scope: "默认每月20日", permissions: "楼长通过后进入需求池，到期推送经理待审", rule: "可在后台修改", remark: "" },
                  { code: "ITEM_REJECT", name: "单项物品拒绝", source: "审批动作", scope: "经理/区域经理/总监", permissions: "拒绝单项并填写原因，被拒物品特殊显示", rule: "被拒物品不计入后续有效合计金额", remark: "" },
                  { code: "REGION_THRESHOLD", name: "区域经理门槛", source: "金额规则", scope: "未拒绝物品合计 > 5000", permissions: "进入区域经理审核", rule: "小于等于5000跳过", remark: "" },
                  { code: "DIRECTOR_THRESHOLD", name: "总监门槛", source: "金额规则", scope: "未拒绝物品合计 > 100000", permissions: "进入总监审核", rule: "小于等于10万元跳过", remark: "" },
                  { code: "SUPPLIER_REPLACE", name: "供应商更换商品", source: "报价阶段", scope: "供应商变更品牌/型号/规格", permissions: "回到楼长确认", rule: "不同意则退回供应商重新调整", remark: "" },
                  { code: "OVER_13_REVIEW", name: "楼长超价复核", source: "报价阶段", scope: "中标单价 > 参考价 * 113%", permissions: "楼长24小时内通过或单项驳回", rule: "低于或等于13%不触发", remark: "" }
                ],
                supplierPermissions: [
                  { code: "SUPPLIER_QUOTE_VIEW", name: "报价可见数据", source: "供应商报价页", scope: "被推送商品明细", permissions: "查看商品、规格、数量、参考金额、报价截止时间", rule: "不展示内部审批链", remark: "" },
                  { code: "SUPPLIER_QUOTE_SUBMIT", name: "报价填写", source: "供应商报价页", scope: "被推送商品明细", permissions: "填写实际价、品牌/型号/规格、交期和说明", rule: "中标单价用于13%超价判断", remark: "" },
                  { code: "SUPPLIER_REPLACEMENT", name: "换商品申请", source: "供应商报价页", scope: "被推送商品明细", permissions: "提交替代商品和原因", rule: "触发楼长确认", remark: "" },
                  { code: "SUPPLIER_PREPARE", name: "备货确认", source: "供应商备货页", scope: "中标/通过商品", permissions: "填写预计到货时间、备货状态、交付说明", rule: "报价通过后开放", remark: "" }
                ],
                fieldMappings: [
                  { code: "room_name", name: "机房", source: "来源表：机房", scope: "酒仙桥", permissions: "申请归属和路由匹配", rule: "必填", remark: "" },
                  { code: "building_name", name: "楼栋", source: "来源表：楼栋", scope: "M6", permissions: "申请归属和路由匹配", rule: "必填", remark: "" },
                  { code: "floor_leader_names", name: "楼长候选人", source: "来源表：楼长", scope: "赵新,马金礼", permissions: "楼长审核、换商品确认", rule: "必填", remark: "" },
                  { code: "manager_names", name: "经理候选人", source: "来源表：机房负责人", scope: "张强,刘燚", permissions: "经理审核、超价复审", rule: "必填", remark: "" },
                  { code: "region_manager_names", name: "区域经理候选人", source: "来源表：区域负责人", scope: "王郑建", permissions: "区域经理审核", rule: "必填", remark: "" },
                  { code: "director_names", name: "总监候选人", source: "后台配置", scope: "杨磊", permissions: "总监审核", rule: "必填", remark: "" }
                ]
              }
            });
            return;
          }

          const mockEmptyPage = (search: URLSearchParams) => ({
            pageNo: Number(search.get("pageNo")) || 1,
            pageSize: Number(search.get("pageSize")) || 20,
            total: 0,
            records: []
          });

          if (req.method === "GET" && req.url?.startsWith("/api/workbench/requests/page")) {
            const search = new URL(req.url, "http://localhost:5173").searchParams;
            sendJson(res, {
              success: true,
              code: "OK",
              message: "success",
              data: { page: mockEmptyPage(search), metrics: [] }
            });
            return;
          }

          if (req.method === "GET" && req.url?.startsWith("/api/workbench/users/options")) {
            sendJson(res, { success: true, code: "OK", message: "success", data: [] });
            return;
          }

          if (req.method === "GET" && req.url?.startsWith("/api/workbench/notices/manage")) {
            sendJson(res, { success: true, code: "OK", message: "success", data: [] });
            return;
          }

          if (req.method === "GET" && req.url?.startsWith("/api/workbench/notices")) {
            sendJson(res, { success: true, code: "OK", message: "success", data: [] });
            return;
          }

          if (req.method === "GET" && req.url?.startsWith("/api/admin/users/page")) {
            const search = new URL(req.url, "http://localhost:5173").searchParams;
            sendJson(res, { success: true, code: "OK", message: "success", data: mockEmptyPage(search) });
            return;
          }

          if (!req.url?.startsWith("/api/auth/")) {
            next();
            return;
          }

          if (req.method === "POST" && req.url === "/api/auth/login") {
            const body = await readBody(req);
            if (mockPassword && body.username === mockUsername && body.password === mockPassword) {
              sendJson(res, {
                success: true,
                code: "OK",
                message: "success",
                data: {
                  token: "dev-maintenance-token",
                  tokenType: "Bearer",
                  expiresIn: 7200,
                  user: mockUser
                }
              });
              return;
            }
            sendJson(
              res,
              {
                success: false,
                code: "INVALID_CREDENTIALS",
                message: "开发环境 mock 登录需配置 VITE_AUTH_MOCK_USERNAME / VITE_AUTH_MOCK_PASSWORD",
                data: null
              },
              200
            );
            return;
          }

          if (req.method === "GET" && req.url?.startsWith("/api/auth/feishu/login-url")) {
            const loginUrl = new URL(req.url, "http://localhost:5173");
            const redirectUri = loginUrl.searchParams.get("redirectUri") || "http://localhost:5173/login";
            const callbackUrl = new URL(redirectUri);
            callbackUrl.searchParams.set("code", "dev-feishu-code");
            callbackUrl.searchParams.set("state", "dev-feishu-state");
            sendJson(res, {
              success: true,
              code: "OK",
              message: "success",
              data: {
                url: callbackUrl.toString()
              }
            });
            return;
          }

          if (req.method === "POST" && req.url === "/api/auth/feishu/callback") {
            sendJson(res, {
              success: true,
              code: "OK",
              message: "success",
              data: {
                token: "dev-feishu-token",
                tokenType: "Bearer",
                expiresIn: 7200,
                user: mockUser
              }
            });
            return;
          }

          if (req.method === "GET" && req.url === "/api/auth/me") {
            sendJson(res, {
              success: true,
              code: "OK",
              message: "success",
              data: mockUser
            });
            return;
          }

          if (req.method === "POST" && req.url === "/api/auth/logout") {
            sendJson(res, {
              success: true,
              code: "OK",
              message: "success",
              data: null
            });
            return;
          }

          next();
        });
      }
    }
  ],
  server: {
    port: 5173,
    proxy: {
      "/api": {
        target: process.env.VITE_API_TARGET || "http://localhost:8080",
        changeOrigin: true
      }
    }
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (id.includes("/node_modules/echarts/") || id.includes("/node_modules/zrender/")) {
            return "echarts";
          }
          if (id.includes("/node_modules/element-plus/") || id.includes("/node_modules/@element-plus/")) {
            return "element-plus";
          }
          return undefined;
        }
      }
    }
  }
});
