# 代码遗漏检查清单（部署前）

本文档用于记录上线前检查项、历史坑点和已完成的修复。涉及流程、数据库结构、外部系统对接和编码问题时，必须记录位置、现象、处理方式和验证结果。

## P0（必须优先处理）

1. **审批流程必须复用原项目节点**
   - 现象：AI 助手、飞书卡片或新入口容易绕过原 `WorkflowService` / 采购审批节点，导致普通采购、紧急采购、月度需求池流转不一致。
   - 要求：任何“创建采购申请、提交审批、单项驳回、整单退回、楼长审核、经理审核”等动作，默认走原始项目节点和已有服务；确实需要新流程时必须先确认。
   - 记录：2026-06-12 已明确 AI 创建的单子进入正式采购申请，后续按现有普通采购/紧急采购流程流转，普通采购楼长通过后再进入月度需求池。
   - 记录：2026-06-23 个人劳保申领只做采购申请入口和领用周期台账，提交时必须调用原 `WorkflowService.createRequest/submitRequest`，不能另建劳保审批流或直接写需求池。

2. **同一任务不得由两个线程并行互改**
   - 现象：多个页面或多个 Codex 线程同时修同一功能，容易出现互相覆盖、误删、分支错乱。
   - 要求：开始修改前先看 `git status`；发现同一文件已有未说明改动时，先确认来源并合并思路，不得直接覆盖。
   - 记录：2026-06-12 AI 助手流程修复中出现过并行修复冲突，后续以当前主线程为准。

3. **数据库和源码乱码必须同步处理**
   - 现象：表结构注释、种子数据或文档出现 UTF-8/GBK/Latin1 误解码，页面和数据库管理后台会显示乱码。
   - 要求：发现乱码时必须记录表名/字段/文件位置；修复生产库后，还要修源 SQL 或迁移脚本；最后用二进制特征扫描复查。
   - 2026-06-16 记录：
     - 生产库字段注释乱码：`monthly_procurement_demand` 全表字段注释、`platform_unit`、`platform_unit_app_scope`、`platform_user_app_access`、`platform_permission_request`、`purchase_request.selected_quote_id/leader_confirm_*`、`supplier_quote.supplier_confirm_*`、`workbench_follow.user_id/source_type/source_id`。
     - 生产库表注释乱码：`monthly_procurement_demand`、`platform_unit`、`platform_unit_app_scope`、`platform_user_app_access`、`platform_permission_request`、`workbench_follow`。
     - 生产库数据乱码：`platform_unit_app_scope.remark` 的“初始化允许申请范围”、`platform_user_app_access.remark` 的“初始化系统准入”。
     - 源文件复查：`backend-springboot/sql/zz_monthly_procurement.sql`、`backend-springboot/sql/zz_platform_permission.sql`、`backend-springboot/sql/zz_workbench_follow.sql`、`backend-springboot/src/main/resources/db/migration/V5__workbench_follow.sql` 用 Python 按 UTF-8 读取为正常中文，PowerShell `Get-Content` 的乱码显示属于终端编码假象；本文件曾为实际乱码，已重写为 UTF-8 中文。
     - 修复方式：生产库注释按原始 UTF-8 字节可逆还原后 `ALTER TABLE ... MODIFY COLUMN ... COMMENT` 回写；种子备注按确定乱码值更新；`backend-springboot/sql/zz_quote_review_workflow.sql` 中报价确认备注统一为“需求人替换物品审核或报价审核备注”。
     - 验证结果：生产库表注释、字段注释和平台授权备注已重扫，未再命中乱码特征。

4. **默认账号和密钥不得写入代码**
   - 现象：历史种子数据和部署说明可能包含固定口令、服务器地址或 token。
   - 要求：代码、文档和技能中只保留变量名、占位符和配置步骤，不写真实密钥；需要部署时走环境变量或后台设置。

## P1（应尽快处理）

1. **权限码规范需要与数据库统一**
   - 前端已使用 `v-perm`，但要持续确认所有按钮权限码在 `sys_permission.perm_code` 中完整维护。

2. **后端自动化测试仍需补足**
   - 当前以编译、打包和核心链路手工验证为主，后续应补充登录鉴权、数据权限、采购申请状态流转、审批动作、飞书卡片回调和 AI 助手候选提交的集成测试。

3. **生产日志策略可继续完善**
   - 建议生产环境关闭 SQL stdout 日志，增加日志滚动和集中采集，避免排障时只能依赖容器即时日志。

## P2（优化项）

1. **历史原型页面需要清理**
   - 如 `src/pages/admin/UserRole.vue` 等未被路由使用的旧页面，后续应降低维护噪音。

2. **前端打包体积较大**
   - `vite build` 仍可能有 chunk size warning，后续可做 `manualChunks` 和按需加载优化。

3. **部署安全增强**
   - 数据库和后端应位于内网，仅前端/网关按需开放；公网入口建议增加限流、白名单和审计。

## 已补齐项

- 已补充 `schema.sql` 与 `seed.sql`。
- 已补充 Flyway `V1/V2` 基础脚本。
- 已补充 Docker 化部署配置。
- 已补充使用手册与部署手册。
- 已将后端主要配置改为环境变量或后台设置。
