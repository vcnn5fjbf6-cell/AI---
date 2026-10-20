SET NAMES utf8mb4;

-- Baseline accounts for the M6 approval-route example. If a real account with
-- the same real_name already exists, it is reused and only the role binding is
-- repaired.
INSERT INTO sys_user
  (username, password_hash, real_name, user_type, supplier_id, region_id, dept_code, dept_name,
   mobile, email, status, data_scope_type, must_change_password, remark,
   created_by, updated_by, is_deleted, version)
SELECT 'm6_majinli', CONCAT('DISABLED-', UUID()), '马金礼',
       'FLOOR_LEADER', NULL, 1, '224',
       '[N420N00077]世纪互联-业务共享中台-DC运维事业部-华北一部-酒仙桥-M6',
       NULL, NULL, 1, 'REGION', 0, 'M6审批路由候选人', 0, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE real_name = '马金礼' AND is_deleted = 0)
  AND NOT EXISTS (SELECT 1 FROM sys_user WHERE username = 'm6_majinli');

INSERT INTO sys_user
  (username, password_hash, real_name, user_type, supplier_id, region_id, dept_code, dept_name,
   mobile, email, status, data_scope_type, must_change_password, remark,
   created_by, updated_by, is_deleted, version)
SELECT 'm6_zhangqiang', CONCAT('DISABLED-', UUID()), '张强',
       'MANAGER', NULL, 1, '224',
       '[N420N00077]世纪互联-业务共享中台-DC运维事业部-华北一部-酒仙桥-M6',
       NULL, NULL, 1, 'REGION', 0, 'M6审批路由候选人', 0, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE real_name = '张强' AND is_deleted = 0)
  AND NOT EXISTS (SELECT 1 FROM sys_user WHERE username = 'm6_zhangqiang');

INSERT INTO sys_user
  (username, password_hash, real_name, user_type, supplier_id, region_id, dept_code, dept_name,
   mobile, email, status, data_scope_type, must_change_password, remark,
   created_by, updated_by, is_deleted, version)
SELECT 'm6_liuyi', CONCAT('DISABLED-', UUID()), '刘燚',
       'MANAGER', NULL, 1, '224',
       '[N420N00077]世纪互联-业务共享中台-DC运维事业部-华北一部-酒仙桥-M6',
       NULL, NULL, 1, 'REGION', 0, 'M6审批路由候选人', 0, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE real_name = '刘燚' AND is_deleted = 0)
  AND NOT EXISTS (SELECT 1 FROM sys_user WHERE username = 'm6_liuyi');

INSERT INTO sys_user
  (username, password_hash, real_name, user_type, supplier_id, region_id, dept_code, dept_name,
   mobile, email, status, data_scope_type, must_change_password, remark,
   created_by, updated_by, is_deleted, version)
SELECT 'm6_wangzhengjian', CONCAT('DISABLED-', UUID()), '王郑建',
       'REGION_MANAGER', NULL, 1, '224',
       '[N420N00077]世纪互联-业务共享中台-DC运维事业部-华北一部-酒仙桥-M6',
       NULL, NULL, 1, 'REGION', 0, 'M6审批路由候选人', 0, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE real_name = '王郑建' AND is_deleted = 0)
  AND NOT EXISTS (SELECT 1 FROM sys_user WHERE username = 'm6_wangzhengjian');

INSERT INTO sys_user
  (username, password_hash, real_name, user_type, supplier_id, region_id, dept_code, dept_name,
   mobile, email, status, data_scope_type, must_change_password, remark,
   created_by, updated_by, is_deleted, version)
SELECT 'm6_yanglei', CONCAT('DISABLED-', UUID()), '杨磊',
       'SENIOR_MANAGER', NULL, 1, '224',
       '[N420N00077]世纪互联-业务共享中台-DC运维事业部-华北一部-酒仙桥-M6',
       NULL, NULL, 1, 'REGION', 0, 'M6审批路由候选人', 0, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE real_name = '杨磊' AND is_deleted = 0)
  AND NOT EXISTS (SELECT 1 FROM sys_user WHERE username = 'm6_yanglei');

UPDATE sys_user
SET status = 1,
    is_deleted = 0,
    region_id = 1,
    dept_code = COALESCE(NULLIF(dept_code, ''), '224'),
    dept_name = COALESCE(NULLIF(dept_name, ''), '[N420N00077]世纪互联-业务共享中台-DC运维事业部-华北一部-酒仙桥-M6'),
    updated_at = NOW(),
    updated_by = 0,
    version = version + 1
WHERE real_name IN ('赵新', '马金礼', '张强', '刘燚', '王郑建', '杨磊')
  AND is_deleted = 0;

INSERT INTO sys_user_role
  (user_id, role_id, status, effective_from, effective_to, created_by, updated_by, is_deleted, version)
SELECT u.id, r.id, 1, NULL, NULL, 0, 0, 0, 0
FROM sys_user u
JOIN sys_role r ON (u.real_name IN ('赵新', '马金礼') AND r.role_code = 'FLOOR_LEADER')
    OR (u.real_name IN ('张强', '刘燚') AND r.role_code = 'MANAGER')
    OR (u.real_name = '王郑建' AND r.role_code = 'REGION_MANAGER')
    OR (u.real_name = '杨磊' AND r.role_code = 'SENIOR_MANAGER')
WHERE u.is_deleted = 0
  AND r.is_deleted = 0
ON DUPLICATE KEY UPDATE
  status = 1,
  effective_from = NULL,
  effective_to = NULL,
  is_deleted = 0,
  updated_at = NOW(),
  updated_by = 0,
  version = sys_user_role.version + 1;

INSERT INTO sys_role_permission
  (role_id, permission_id, status, created_by, updated_by, is_deleted, version)
SELECT r.id, p.id, 1, 0, 0, 0, 0
FROM sys_role r
JOIN sys_permission p ON p.perm_code = 'approval:action' AND p.is_deleted = 0
WHERE r.role_code IN ('FLOOR_LEADER', 'MANAGER', 'REGION_MANAGER', 'SENIOR_MANAGER')
  AND r.is_deleted = 0
ON DUPLICATE KEY UPDATE
  status = 1,
  is_deleted = 0,
  updated_at = NOW(),
  updated_by = 0,
  version = sys_role_permission.version + 1;
