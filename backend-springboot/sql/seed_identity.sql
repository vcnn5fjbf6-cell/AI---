SET NAMES utf8mb4;

INSERT INTO sys_role
  (id, role_code, role_name, role_type, data_scope_type, approval_amount_min, approval_amount_max, status, description, created_by, updated_by, is_deleted, version)
SELECT 90, 'SYS_ADMIN', '系统超管', 'ADMIN', 'ALL', NULL, NULL, 1, '高于 ADMIN 的系统最高权限账号', 0, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_code = 'SYS_ADMIN');

INSERT INTO sys_role
  (id, role_code, role_name, role_type, data_scope_type, approval_amount_min, approval_amount_max, status, description, created_by, updated_by, is_deleted, version)
SELECT 91, 'SUPER_ADMIN', '平台超管', 'ADMIN', 'ALL', NULL, NULL, 1, '平台最高权限角色', 0, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_code = 'SUPER_ADMIN');

INSERT INTO sys_role_permission
  (role_id, permission_id, status, created_by, updated_by, is_deleted, version)
SELECT target_role.id, admin_perm.permission_id, 1, 0, 0, 0, 0
FROM sys_role target_role
JOIN sys_role admin_role ON admin_role.role_code = 'ADMIN' AND admin_role.is_deleted = 0
JOIN sys_role_permission admin_perm ON admin_perm.role_id = admin_role.id AND admin_perm.is_deleted = 0
WHERE target_role.role_code IN ('SYS_ADMIN', 'SUPER_ADMIN')
  AND target_role.is_deleted = 0
  AND NOT EXISTS (
    SELECT 1
    FROM sys_role_permission existing_perm
    WHERE existing_perm.role_id = target_role.id
      AND existing_perm.permission_id = admin_perm.permission_id
      AND existing_perm.is_deleted = 0
  );

INSERT INTO sys_user
  (id, username, password_hash, real_name, user_type, supplier_id, region_id, dept_code, dept_name, mobile, email, status, data_scope_type, must_change_password, remark, created_by, updated_by, is_deleted, version)
SELECT 90, 'sysadmin', CONCAT('DISABLED-', UUID()), '系统超管', 'SYS_ADMIN', NULL, 1, 'IT', '信息部', '13800000009', 'sysdemo.user001@example.invalid', 1, 'ALL', 0, '系统最高权限初始化账号', 0, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE username = 'sysadmin');

INSERT INTO sys_user_role
  (user_id, role_id, status, effective_from, effective_to, created_by, updated_by, is_deleted, version)
SELECT u.id, r.id, 1, NOW(), NULL, 0, 0, 0, 0
FROM sys_user u
JOIN sys_role r ON r.role_code = 'SYS_ADMIN' AND r.is_deleted = 0
WHERE u.username = 'sysadmin'
  AND u.is_deleted = 0
  AND NOT EXISTS (
    SELECT 1
    FROM sys_user_role ur
    WHERE ur.user_id = u.id
      AND ur.role_id = r.id
      AND ur.is_deleted = 0
  );
