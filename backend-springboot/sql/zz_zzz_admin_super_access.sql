SET NAMES utf8mb4;

INSERT INTO sys_role
  (role_code, role_name, role_type, data_scope_type, approval_amount_min, approval_amount_max, status, description, created_by, updated_by, is_deleted, version)
VALUES
  ('ADMIN', 'System Administrator', 'ADMIN', 'ALL', NULL, NULL, 1, 'System administrator with full platform access', 0, 0, 0, 0),
  ('SYS_ADMIN', 'System Super Administrator', 'ADMIN', 'ALL', NULL, NULL, 1, 'System-level super administrator', 0, 0, 0, 0),
  ('SUPER_ADMIN', 'Platform Super Administrator', 'ADMIN', 'ALL', NULL, NULL, 1, 'Highest platform administrator', 0, 0, 0, 0)
ON DUPLICATE KEY UPDATE
  role_type = 'ADMIN',
  data_scope_type = 'ALL',
  status = 1,
  is_deleted = 0,
  updated_by = 0;

UPDATE sys_user
SET user_type = 'ADMIN',
    supplier_id = NULL,
    data_scope_type = 'ALL',
    status = 1,
    updated_by = 0
WHERE username = 'admin'
  AND is_deleted = 0;

INSERT INTO sys_user_role
  (user_id, role_id, status, effective_from, effective_to, created_by, updated_by, is_deleted, version)
SELECT u.id, r.id, 1, NOW(), NULL, 0, 0, 0, 0
FROM sys_user u
JOIN sys_role r ON r.role_code IN ('ADMIN', 'SUPER_ADMIN') AND r.is_deleted = 0
WHERE u.username = 'admin'
  AND u.is_deleted = 0
ON DUPLICATE KEY UPDATE
  status = 1,
  effective_to = NULL,
  is_deleted = 0,
  updated_by = 0;

INSERT INTO sys_role_permission
  (role_id, permission_id, status, created_by, updated_by, is_deleted, version)
SELECT r.id, p.id, 1, 0, 0, 0, 0
FROM sys_role r
JOIN sys_permission p ON p.status = 1 AND p.is_deleted = 0
WHERE r.role_code IN ('ADMIN', 'SYS_ADMIN', 'SUPER_ADMIN')
  AND r.is_deleted = 0
ON DUPLICATE KEY UPDATE
  status = 1,
  is_deleted = 0,
  updated_by = 0;

INSERT INTO platform_user_app_access
  (user_id, app_code, status, granted_by, granted_at, remark, created_by, updated_by, is_deleted, version)
SELECT u.id, app.app_code, 1, 0, NOW(), 'Admin super access initialization', 0, 0, 0, 0
FROM sys_user u
JOIN (
  SELECT 'PLATFORM' AS app_code UNION ALL
  SELECT 'PROCUREMENT'
) app
WHERE u.username = 'admin'
  AND u.is_deleted = 0
ON DUPLICATE KEY UPDATE
  status = 1,
  granted_by = 0,
  granted_at = NOW(),
  remark = 'Admin super access initialization',
  is_deleted = 0,
  updated_by = 0;
