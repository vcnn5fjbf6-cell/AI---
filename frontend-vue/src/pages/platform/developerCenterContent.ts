export type DeveloperAlertType = "success" | "warning" | "info";
export type EndpointRisk = "只读" | "修改测试数据";

export interface DeveloperStep {
  title: string;
  description: string;
  meta?: string;
}

export interface DeveloperCodeBlock {
  label: string;
  language: string;
  value: string;
}

export interface DeveloperEndpoint {
  method: "GET" | "POST" | "PUT" | "DELETE" | "UI";
  path: string;
  summary: string;
  permission: string;
  risk: EndpointRisk;
}

export interface DeveloperResourceLink {
  label: string;
  path: string;
  description: string;
  format: string;
  downloadable?: boolean;
}

export interface DeveloperSection {
  id: string;
  title: string;
  paragraphs?: string[];
  bullets?: string[];
  steps?: DeveloperStep[];
  code?: DeveloperCodeBlock;
  endpoints?: DeveloperEndpoint[];
  links?: DeveloperResourceLink[];
  alert?: {
    type: DeveloperAlertType;
    title: string;
    description: string;
  };
}

export interface DeveloperDocument {
  key: string;
  title: string;
  summary: string;
  group: string;
  keywords: string[];
  updatedAt: string;
  sections: DeveloperSection[];
}

export interface DeveloperDocumentGroup {
  label: string;
  documents: string[];
}

const moduleManifest = `Invoke-WebRequest \`
  -Uri "https://<test-host>/developer/module-manifest.example.json" \`
  -OutFile "module-manifest.json"

# 使用同目录的 module-manifest.schema.json 完成编辑器或 CI 校验。`;

const frontendRouteExample = `{
  path: "/asset-inspection",
  name: "AssetInspection",
  component: () => import("../pages/assetInspection/AssetInspectionPage.vue"),
  meta: {
    title: "资产巡检",
    anyAppCodes: ["ASSET_INSPECTION"],
    roles: ["SUPER_ADMIN"],
    permissions: ["asset_inspection:read"]
  }
}`;

const backendExample = `@RestController
@RequestMapping("/api/asset-inspection")
@RequiredArgsConstructor
public class AssetInspectionController {

    private final AssetInspectionService service;

    @GetMapping("/records")
    public Result<List<InspectionRecordVO>> list() {
        return Result.ok(service.listVisibleRecords());
    }
}`;

const accessChecklist = `菜单入口
  → route.meta.appCode / anyAppCodes
  → 后端角色或权限码校验
  → 服务层数据范围过滤
  → 审计记录`;

const migrationExample = `-- backend-springboot/sql/zz_20260727_asset_inspection.sql
CREATE TABLE IF NOT EXISTS asset_inspection_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  region_id BIGINT NOT NULL,
  status VARCHAR(32) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_asset_inspection_region (region_id)
);
`;

const curlExample = `curl -i "https://<test-host>/api/auth/me" \\
  -H "Authorization: Bearer <test-token>"`;

const validationCommands = `# frontend-vue
npm.cmd run check:developer-center
npm.cmd run check:access
npm.cmd run build

# backend-springboot
mvn.cmd -q -DskipTests compile

# repository root
git diff --check`;

export const developerDocumentGroups: DeveloperDocumentGroup[] = [
  {
    label: "开始",
    documents: ["overview", "quickstart"]
  },
  {
    label: "模块开发",
    documents: ["manifest", "frontend", "backend"]
  },
  {
    label: "权限与数据",
    documents: ["access", "migration"]
  },
  {
    label: "API 参考",
    documents: ["api-overview", "api-testing"]
  },
  {
    label: "测试与发布",
    documents: ["testing", "release"]
  },
  {
    label: "AI 开发资料",
    documents: ["ai-context"]
  }
];

export const developerDocuments: DeveloperDocument[] = [
  {
    key: "overview",
    title: "平台接入总览",
    summary: "了解平台边界、技术栈、环境和模块交付方式。",
    group: "开始",
    keywords: ["架构", "技术栈", "Vue", "Spring Boot", "测试环境", "生产环境"],
    updatedAt: "2026-07-27",
    sections: [
      {
        id: "purpose",
        title: "这个开发者中心解决什么问题",
        paragraphs: [
          "它是内部模块开发的统一入口。开发者或 AI 可以在这里找到平台约束、最小示例、实时接口契约、测试方法和 PR 发布规则。",
          "开发者中心只在测试环境开放。生产环境不提供在线文档和接口调试入口，测试通过也不等于获得生产发布授权。"
        ]
      },
      {
        id: "stack",
        title: "现有技术底座",
        bullets: [
          "前端：Vue 3、TypeScript、Vite、Element Plus，统一运行在 AppLayout 中。",
          "后端：Spring Boot 3、MyBatis Plus、MySQL 8，接口使用统一响应与异常处理。",
          "访问控制：菜单、路由、后端权限和数据范围四层闭环。",
          "交付方式：开发分支提交 PR，检查通过后合并，先部署测试环境完成业务验收。"
        ]
      },
      {
        id: "boundaries",
        title: "必须遵守的边界",
        alert: {
          type: "warning",
          title: "不要建立平行流程",
          description: "采购、审批、报价、飞书通知和 AI 判断必须复用现有服务、状态和审计记录。需要新增流程分支时，先完成业务确认。"
        },
        bullets: [
          "前端隐藏不是安全边界，后端必须再次校验权限与数据范围。",
          "数据库只使用幂等增量 SQL；初始化 schema.sql 仅用于可丢弃的本地数据库。",
          "静态接口快照只用于检索，测试环境 /v3/api-docs 才是实时接口契约。"
        ]
      }
    ]
  },
  {
    key: "quickstart",
    title: "10 分钟创建第一个模块",
    summary: "从模块声明到本地构建，完成一个可提交 PR 的最小模块骨架。",
    group: "开始",
    keywords: ["quickstart", "快速开始", "第一个模块", "分支", "PR"],
    updatedAt: "2026-07-27",
    sections: [
      {
        id: "before-start",
        title: "开始前",
        paragraphs: [
          "先确认模块解决的业务问题、负责人和稳定 appCode。appCode 一经使用，不要因中文名称变化而修改。"
        ],
        bullets: [
          "从最新 main 创建 codex/中文功能名称 分支。",
          "阅读 /developer/ai-context.md 和模块开发指南。",
          "确认新能力能否复用现有页面、服务和工作流。"
        ]
      },
      {
        id: "five-steps",
        title: "最小接入步骤",
        steps: [
          {
            title: "声明模块",
            description: "按 Schema 填写 application.appCode、前后端入口、权限、数据库和发布边界，并在 PR 中标明负责人。",
            meta: "module-manifest.json"
          },
          {
            title: "接入前端",
            description: "在 menu.ts 与 router/index.ts 使用一致的 appCode，并复用 AppLayout 和现有页面组件。",
            meta: "frontend-vue/src"
          },
          {
            title: "接入后端",
            description: "Controller 只负责协议，权限与数据范围放在服务层或现有鉴权链路。",
            meta: "backend-springboot/src"
          },
          {
            title: "补充迁移与测试",
            description: "新增幂等 zz_*.sql，编写直接覆盖新行为的专项检查。",
            meta: "backend-springboot/sql"
          },
          {
            title: "提交 PR",
            description: "说明影响范围、验证证据、数据变化和回滚方式，先通过检查再合并测试。",
            meta: "/developer/pr-checklist.md"
          }
        ]
      },
      {
        id: "manifest-example",
        title: "模块声明示例",
        paragraphs: [
          "不要复制页面里的缩略字段自行拼装清单。下载完整示例后按 JSON Schema 修改，避免页面说明与机器校验规则漂移。"
        ],
        code: {
          label: "下载完整 module-manifest.example.json",
          language: "PowerShell",
          value: moduleManifest
        }
      }
    ]
  },
  {
    key: "manifest",
    title: "模块清单",
    summary: "用一个结构化文件声明模块入口、接口、权限和数据变更。",
    group: "模块开发",
    keywords: ["manifest", "Schema", "appCode", "moduleCode", "模块声明"],
    updatedAt: "2026-07-27",
    sections: [
      {
        id: "why-manifest",
        title: "为什么需要模块清单",
        paragraphs: [
          "清单让评审者和 AI 在读代码前就能判断模块的边界与影响范围，也能避免菜单、路由、后端权限和数据迁移出现遗漏。"
        ]
      },
      {
        id: "required-fields",
        title: "必填信息",
        bullets: [
          "schemaVersion、moduleCode、displayName、summary、status：描述清单版本、稳定模块编码和当前状态。",
          "application：声明接入模式、稳定 appCode、审批状态和工作台来源。",
          "frontend、backend：列出页面、路由、菜单、API 前缀、权限码和对象范围检查。",
          "database：始终填写对象；无数据库变更时 required=false 且 migrationFiles=[]，不能填写 null。",
          "workflow、verification、documentation、security、release：说明流程复用、验证证据、文档、安全边界和测试发布目标。"
        ]
      },
      {
        id: "schema",
        title: "下载与校验",
        links: [
          {
            label: "模块清单 JSON Schema",
            path: "/developer/module-manifest.schema.json",
            description: "用于编辑器提示和自动校验。",
            format: "JSON Schema"
          },
          {
            label: "模块清单示例",
            path: "/developer/module-manifest.example.json",
            description: "可直接复制后修改。",
            format: "JSON"
          }
        ]
      }
    ]
  },
  {
    key: "frontend",
    title: "前端模块接入",
    summary: "接入菜单、路由、页面组件和访问控制元数据。",
    group: "模块开发",
    keywords: ["menu.ts", "router", "Vue", "Element Plus", "AppLayout", "anyAppCodes"],
    updatedAt: "2026-07-27",
    sections: [
      {
        id: "frontend-structure",
        title: "目录与组件",
        bullets: [
          "页面放在 frontend-vue/src/pages/<module>/，复用 components/vnet 与 Element Plus。",
          "全局视觉变量来自 frontend-vue/src/styles.css，不为单个模块创建新的品牌体系。",
          "路由和菜单使用相同路径、appCode 与角色规则。"
        ]
      },
      {
        id: "route-example",
        title: "路由示例",
        code: {
          label: "frontend-vue/src/router/index.ts",
          language: "TypeScript",
          value: frontendRouteExample
        }
      },
      {
        id: "frontend-check",
        title: "前端门禁",
        bullets: [
          "npm.cmd run check:access",
          "npm.cmd run typecheck",
          "npm.cmd run build",
          "使用真实角色检查入口是否可见、直达路由是否被正确拦截。"
        ]
      }
    ]
  },
  {
    key: "backend",
    title: "后端模块接入",
    summary: "沿用统一接口、服务边界、权限校验、审计与异常处理。",
    group: "模块开发",
    keywords: ["Spring Boot", "Controller", "Service", "ApiResponse", "BizException"],
    updatedAt: "2026-07-27",
    sections: [
      {
        id: "backend-layers",
        title: "分层原则",
        bullets: [
          "Controller 负责参数和响应协议，不在控制器中复制业务状态流转。",
          "Service 复用现有事务与工作流服务，写操作保留审计依据。",
          "Mapper 只负责数据访问；所有列表查询必须应用用户的数据范围。"
        ]
      },
      {
        id: "controller-example",
        title: "最小接口示例",
        code: {
          label: "AssetInspectionController.java",
          language: "Java",
          value: backendExample
        }
      },
      {
        id: "backend-check",
        title: "后端门禁",
        alert: {
          type: "info",
          title: "测试接口不绕过权限",
          description: "Swagger 只是接口调用入口，服务端仍按 Authorize 中测试 JWT 对应的身份执行角色、权限码和数据范围校验。"
        },
        bullets: [
          "mvn.cmd -q -DskipTests compile",
          "运行模块专项单元或集成测试。",
          "验证无权限、无数据范围和非法状态三类失败路径。"
        ]
      }
    ]
  },
  {
    key: "access",
    title: "权限与数据范围",
    summary: "建立菜单、路由、接口权限和数据范围的完整闭环。",
    group: "权限与数据",
    keywords: ["权限", "角色", "appCode", "permission", "dataScope", "403"],
    updatedAt: "2026-07-27",
    sections: [
      {
        id: "access-chain",
        title: "四层访问控制",
        code: {
          label: "访问链路",
          language: "Text",
          value: accessChecklist
        }
      },
      {
        id: "access-rules",
        title: "实施规则",
        bullets: [
          "PLATFORM 不是全局通配符；用户必须拥有目标模块 appCode。",
          "菜单和路由的 roles 与 permissions 是“任一满足即可”；roles 为空会直接通过角色门槛，不能再用 permissions 收窄入口。权限优先入口可使用 roles=[SUPER_ADMIN] 并配置 permissions。",
          "菜单隐藏只减少误操作，不能替代接口鉴权。",
          "列表、详情、导出和下载使用一致的数据范围。",
          "供应商账号不得进入内部开发者中心。"
        ]
      },
      {
        id: "access-test",
        title: "至少验证三种身份",
        steps: [
          {
            title: "模块管理员",
            description: "可以进入模块并访问授权范围内的全部管理能力。"
          },
          {
            title: "普通内部用户",
            description: "只能访问已开通模块和本人数据范围。"
          },
          {
            title: "无权限或供应商用户",
            description: "菜单不可见，直达路由和接口调用均被拒绝。"
          }
        ]
      }
    ]
  },
  {
    key: "migration",
    title: "数据库迁移",
    summary: "使用可重复执行的增量 SQL 演进测试和生产数据库。",
    group: "权限与数据",
    keywords: ["SQL", "zz_", "幂等", "迁移", "MySQL", "schema.sql"],
    updatedAt: "2026-07-27",
    sections: [
      {
        id: "migration-rules",
        title: "增量迁移规则",
        alert: {
          type: "warning",
          title: "不要在现有环境运行 schema.sql",
          description: "schema.sql 可能包含初始化或破坏性语句，仅适用于可丢弃的本地数据库。测试和生产使用受控 zz_*.sql。"
        },
        bullets: [
          "脚本命名使用 backend-springboot/sql/zz_<date>_<feature>.sql。",
          "新增表、列、索引和数据修复都必须可重复执行。",
          "脚本不得覆盖用户已配置的值；数据修复需给出明确筛选条件和结果统计。",
          "在 PR 中说明执行顺序、影响范围和恢复依据。"
        ]
      },
      {
        id: "migration-example",
        title: "幂等脚本示例",
        code: {
          label: "zz_20260727_asset_inspection.sql",
          language: "SQL",
          value: migrationExample
        }
      }
    ]
  },
  {
    key: "api-overview",
    title: "API 总览",
    summary: "以测试环境实时 OpenAPI 为契约，按风险识别接口用途。",
    group: "API 参考",
    keywords: ["/v3/api-docs", "Swagger", "OpenAPI", "接口", "GET", "POST"],
    updatedAt: "2026-07-27",
    sections: [
      {
        id: "live-contract",
        title: "实时接口契约",
        paragraphs: [
          "测试环境 /v3/api-docs 是当前部署版本的实时契约；Swagger UI 用于人工查看参数和执行请求。静态 CSV 只作为离线索引，不代表当前运行版本。"
        ],
        links: [
          {
            label: "打开 Swagger UI",
            path: "/swagger-ui/index.html",
            description: "查看参数并在测试环境执行请求。",
            format: "Interactive UI",
            downloadable: false
          },
          {
            label: "获取 OpenAPI JSON",
            path: "/v3/api-docs",
            description: "用于生成客户端、差异分析或交给 AI。",
            format: "JSON"
          }
        ]
      },
      {
        id: "common-endpoints",
        title: "开发者常用入口",
        endpoints: [
          {
            method: "UI",
            path: "/swagger-ui/index.html",
            summary: "交互式接口文档",
            permission: "内部登录",
            risk: "只读"
          },
          {
            method: "GET",
            path: "/v3/api-docs",
            summary: "实时 OpenAPI JSON",
            permission: "测试环境",
            risk: "只读"
          },
          {
            method: "GET",
            path: "/api/auth/me",
            summary: "验证当前登录身份与权限",
            permission: "已登录",
            risk: "只读"
          },
          {
            method: "POST",
            path: "/api/<module>/...",
            summary: "模块写操作示例",
            permission: "按接口权限",
            risk: "修改测试数据"
          }
        ]
      },
      {
        id: "api-risk",
        title: "调用风险",
        alert: {
          type: "warning",
          title: "Swagger 会真实调用测试环境",
          description: "POST、PUT、DELETE 会修改测试数据。先使用 GET 验证身份和参数，再执行写操作；开发者中心不提供生产接口调试。"
        }
      }
    ]
  },
  {
    key: "api-testing",
    title: "完成第一次接口调试",
    summary: "登录测试环境，通过只读接口和统一响应体验证鉴权、响应及错误处理。",
    group: "API 参考",
    keywords: ["调试", "Authorize", "UNAUTHORIZED", "FORBIDDEN", "APP_ACCESS_DENIED", "Execute", "curl"],
    updatedAt: "2026-07-27",
    sections: [
      {
        id: "api-test-steps",
        title: "推荐调试顺序",
        steps: [
          {
            title: "登录测试环境",
            description: "先完成系统登录，确认当前账号已开通目标模块，并只使用当前会话的短期测试 JWT。"
          },
          {
            title: "在 Swagger 中授权",
            description: "打开 Swagger UI，点击 Authorize，粘贴测试 JWT；Swagger 不会自动读取平台 localStorage 中的登录凭据。"
          },
          {
            title: "检查统一响应体",
            description: "不要只看 HTTP 状态。本项目部分鉴权和业务失败仍返回 HTTP 200；以 success 与 code 为准：success=true 且 code=0 才表示调用成功。"
          },
          {
            title: "再测试模块接口",
            description: "先读后写。执行 POST、PUT、DELETE 前准备可删除的测试数据并记录回收方式。"
          }
        ]
      },
      {
        id: "curl",
        title: "命令行示例",
        code: {
          label: "只读身份检查",
          language: "cURL",
          value: curlExample
        }
      },
      {
        id: "common-errors",
        title: "常见响应",
        bullets: [
          "UNAUTHORIZED：测试 JWT 缺失或失效，重新登录并在 Authorize 中更新。",
          "FORBIDDEN / APP_ACCESS_DENIED：检查 appCode、角色、权限码及记录所属数据范围。",
          "NOT_FOUND：确认接口是否存在于当前 /v3/api-docs，避免使用过期静态路径。",
          "VALIDATION_ERROR / BIND_ERROR：按 message 修正请求体或参数。",
          "SYSTEM_ERROR：停止写操作并联系维护者查看测试环境错误台账。"
        ]
      }
    ]
  },
  {
    key: "testing",
    title: "测试与检查门禁",
    summary: "本地检查通过后才能合并并触发测试环境部署。",
    group: "测试与发布",
    keywords: ["build", "compile", "check:access", "diff", "测试报告"],
    updatedAt: "2026-07-27",
    sections: [
      {
        id: "required-checks",
        title: "必跑检查",
        code: {
          label: "发布前门禁",
          language: "PowerShell",
          value: validationCommands
        }
      },
      {
        id: "test-evidence",
        title: "PR 中需要的证据",
        bullets: [
          "变更页面或接口的正常路径。",
          "直接相关角色、权限和失败路径。",
          "数据库增量脚本的重复执行结果。",
          "构建、专项测试、PR Build Check 的通过记录。",
          "回滚方式与仍未覆盖的验证限制。"
        ]
      },
      {
        id: "test-tier",
        title: "选择验证范围",
        paragraphs: [
          "单页或单接口使用烟雾测试；涉及共享权限、工作流、SQL 或回调时使用聚焦回归；只有用户明确要求或共享基础设施发生变化时才执行全量回归。"
        ]
      }
    ]
  },
  {
    key: "release",
    title: "PR 与测试发布",
    summary: "通过 PR 评审后合并，使用 Gitee main 自动部署 151 测试环境。",
    group: "测试与发布",
    keywords: ["PR", "Gitee", "GitHub", "测试部署", "生产发布", "回滚"],
    updatedAt: "2026-07-27",
    sections: [
      {
        id: "release-flow",
        title: "交付流程",
        steps: [
          {
            title: "提交开发分支",
            description: "只包含当前模块相关改动，并在 PR 中填写检查证据。"
          },
          {
            title: "通过 PR 检查与评审",
            description: "Build Check、专项测试和人工评审全部通过后才能合并。"
          },
          {
            title: "同步 Gitee main",
            description: "确认 GitHub 与 Gitee main 指向同一提交。"
          },
          {
            title: "部署测试环境",
            description: "151 主机轮询 Gitee main，构建完成后验证页面、OpenAPI 与变更功能。"
          },
          {
            title: "业务验收",
            description: "测试通过后才能创建独立的生产上线批次；开发者中心本身不执行生产发布。"
          }
        ]
      },
      {
        id: "release-boundary",
        title: "环境边界",
        alert: {
          type: "warning",
          title: "测试与生产严格分离",
          description: "测试环境部署不访问生产主机、生产数据库或生产工作流。生产发布必须获得明确授权并走现有 GitHub 手动流程。"
        }
      },
      {
        id: "pr-template",
        title: "提交前检查",
        links: [
          {
            label: "PR 检查清单",
            path: "/developer/pr-checklist.md",
            description: "提交评审前逐项确认范围、测试、数据和回滚。",
            format: "Markdown"
          }
        ]
      }
    ]
  },
  {
    key: "ai-context",
    title: "交给 AI 的开发资料",
    summary: "通过稳定、脱敏、机器可读的地址向其他 AI 提供完整上下文。",
    group: "AI 开发资料",
    keywords: ["llms.txt", "AI", "Markdown", "JSON", "Schema", "下载"],
    updatedAt: "2026-07-27",
    sections: [
      {
        id: "ai-first",
        title: "AI 首选入口",
        paragraphs: [
          "让其他 AI 从 /llms.txt 开始。它列出可读取的稳定资料地址；需要完整平台约束时，再读取 ai-context.md 或结构化 JSON。"
        ],
        links: [
          {
            label: "llms.txt",
            path: "/llms.txt",
            description: "最短入口，告诉 AI 下一步读取哪些资料。",
            format: "Text"
          },
          {
            label: "AI 开发上下文",
            path: "/developer/ai-context.md",
            description: "技术栈、业务边界、权限、数据和发布规则。",
            format: "Markdown"
          },
          {
            label: "结构化上下文",
            path: "/developer/ai-context.json",
            description: "适合工具自动读取的平台结构。",
            format: "JSON"
          },
          {
            label: "模块开发指南",
            path: "/developer/module-development-guide.md",
            description: "面向开发者与 AI 的完整模块接入说明。",
            format: "Markdown"
          }
        ]
      },
      {
        id: "ai-prompt",
        title: "推荐交给 AI 的指令",
        code: {
          label: "任务开场指令",
          language: "Text",
          value: `先读取 <测试环境地址>/llms.txt 和其中列出的开发上下文。
在修改代码前说明：
1. 计划复用的现有模块或服务；
2. 菜单、路由、接口权限和数据范围；
3. 数据库迁移与测试方案；
4. PR 影响范围和回滚方式。`
        }
      },
      {
        id: "ai-boundary",
        title: "资料边界",
        alert: {
          type: "info",
          title: "机器资料必须保持脱敏",
          description: "不要把账号、密码、令牌、真实内网地址、用户数据或生产配置写入公开下载文件。AI 资料只提供开发规则和稳定结构。"
        }
      }
    ]
  }
];

export const developerDocumentsByKey = new Map(
  developerDocuments.map((document) => [document.key, document])
);
