INSERT INTO sys_permission
  (perm_code, perm_name, module_code, perm_type, parent_id, path, http_method, sort_no, status,
   description, created_by, updated_by, is_deleted, version)
VALUES
  ('sys_user:offboard', '办理员工离职', 'SYSTEM_USER', 'BUTTON', NULL,
   '/api/admin/users/*/offboarding', 'POST', 3, 1,
   '冻结离职员工账号并逐项交接审批配置和待办任务', 0, 0, 0, 0)
ON DUPLICATE KEY UPDATE
  perm_name = VALUES(perm_name),
  module_code = VALUES(module_code),
  perm_type = VALUES(perm_type),
  path = VALUES(path),
  http_method = VALUES(http_method),
  status = 1,
  description = VALUES(description),
  updated_by = 0,
  is_deleted = 0;

INSERT INTO sys_role_permission
  (role_id, permission_id, status, created_by, updated_by, is_deleted, version)
SELECT DISTINCT source_role.role_id, offboard_perm.id, 1, 0, 0, 0, 0
FROM (
  SELECT role_permission.role_id
  FROM sys_role_permission role_permission
  JOIN sys_permission update_perm
    ON update_perm.id = role_permission.permission_id
   AND update_perm.perm_code = 'sys_user:update'
   AND update_perm.status = 1
   AND update_perm.is_deleted = 0
  WHERE role_permission.status = 1
    AND role_permission.is_deleted = 0
) source_role
JOIN sys_permission offboard_perm
  ON offboard_perm.perm_code = 'sys_user:offboard'
 AND offboard_perm.is_deleted = 0
ON DUPLICATE KEY UPDATE
  status = 1,
  updated_by = 0,
  is_deleted = 0;
