SET NAMES utf8mb4;

UPDATE sys_permission
SET perm_name = CASE perm_code
    WHEN 'sys_user:create' THEN '新增用户'
    WHEN 'sys_user:update' THEN '编辑用户'
    WHEN 'sys_user:delete' THEN '删除用户'
    WHEN 'sys_user:view' THEN '查看用户'
    WHEN 'sys_user:import' THEN '批量导入用户'
    WHEN 'sys_user:feishu_sync' THEN '同步飞书组织'
    WHEN 'sys_role:create' THEN '新增角色'
    WHEN 'sys_role:update' THEN '编辑角色'
    WHEN 'sys_role:delete' THEN '删除角色'
    WHEN 'sys_role:view' THEN '查看角色'
    WHEN 'supplier:create' THEN '新增供应商'
    WHEN 'supplier:update' THEN '编辑供应商'
    WHEN 'supplier:delete' THEN '删除供应商'
    WHEN 'supplier:view' THEN '查看供应商'
    WHEN 'purchase_request:create' THEN '新增采购申请'
    WHEN 'purchase_request:update' THEN '编辑采购申请'
    WHEN 'purchase_request:submit' THEN '提交采购申请'
    WHEN 'purchase_request:delete' THEN '删除采购申请'
    WHEN 'approval:action' THEN '审批操作'
    WHEN 'platform_permission:manage' THEN '平台权限管理'
    WHEN 'platform_permission:apply' THEN '平台权限申请'
    WHEN 'platform_error_log:manage' THEN '错误台账管理'
    WHEN 'procurement_route:manage' THEN '部门审批配置'
    WHEN 'procurement_settings:manage' THEN '采购管理设置'
    WHEN 'procurement:demand-pool-publish' THEN '需求池正式推送'
    WHEN 'procurement:demand-pool-publish-test' THEN '需求池测试推送'
    WHEN 'monthly_procurement:submit' THEN '飞书采购需求提交'
    WHEN 'monthly_procurement:view' THEN '查看月度采购池'
    WHEN 'monthly_procurement:aggregate' THEN '月度采购汇总'
    WHEN 'monthly_procurement:supplement' THEN '本月需求补充采购需求'
    WHEN 'procurement_ai:start' THEN 'AI采购助手发起'
    WHEN 'procurement_ai:view' THEN 'AI采购助手查看'
    WHEN 'procurement_ai:confirm' THEN 'AI采购助手确认'
    ELSE perm_name
  END,
  description = CASE perm_code
    WHEN 'sys_user:import' THEN '通过中文 Excel 模板批量新增或更新用户'
    WHEN 'sys_user:feishu_sync' THEN '从飞书通讯录同步部门和用户到本地用户管理'
    WHEN 'platform_permission:manage' THEN '维护账号准入、系统授权、单元范围和权限审批'
    WHEN 'platform_permission:apply' THEN '当前用户查看和申请平台权限'
    WHEN 'platform_error_log:manage' THEN '查看和处理系统错误台账'
    WHEN 'procurement_route:manage' THEN '维护部门、机房楼栋和 V1-V8 审批节点'
    WHEN 'procurement_settings:manage' THEN '维护采购管理基础规则'
    WHEN 'procurement:demand-pool-publish' THEN '正式把需求池按部门汇总推送给经理审批'
    WHEN 'procurement:demand-pool-publish-test' THEN '测试需求池推送，不受每月推送日拦截'
    WHEN 'monthly_procurement:submit' THEN '工程师在飞书窗口提交采购需求'
    WHEN 'monthly_procurement:view' THEN '查看飞书采购需求池'
    WHEN 'monthly_procurement:aggregate' THEN '采购员汇总月度采购需求'
    WHEN 'monthly_procurement:supplement' THEN '从本月需求池复制为普通采购补充需求'
    WHEN 'procurement_ai:start' THEN '创建采购 AI 推荐会话'
    WHEN 'procurement_ai:view' THEN '查看采购 AI 推荐会话'
    WHEN 'procurement_ai:confirm' THEN '确认采购 AI 候选项进入月度需求池'
    ELSE description
  END,
  updated_at = NOW(),
  version = version + 1
WHERE perm_code IN (
  'sys_user:create',
  'sys_user:update',
  'sys_user:delete',
  'sys_user:view',
  'sys_user:import',
  'sys_user:feishu_sync',
  'sys_role:create',
  'sys_role:update',
  'sys_role:delete',
  'sys_role:view',
  'supplier:create',
  'supplier:update',
  'supplier:delete',
  'supplier:view',
  'purchase_request:create',
  'purchase_request:update',
  'purchase_request:submit',
  'purchase_request:delete',
  'approval:action',
  'platform_permission:manage',
  'platform_permission:apply',
  'platform_error_log:manage',
  'procurement_route:manage',
  'procurement_settings:manage',
  'procurement:demand-pool-publish',
  'procurement:demand-pool-publish-test',
  'monthly_procurement:submit',
  'monthly_procurement:view',
  'monthly_procurement:aggregate',
  'monthly_procurement:supplement',
  'procurement_ai:start',
  'procurement_ai:view',
  'procurement_ai:confirm'
)
AND is_deleted = 0;
