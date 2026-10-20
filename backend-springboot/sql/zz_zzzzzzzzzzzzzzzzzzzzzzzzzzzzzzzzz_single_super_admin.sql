-- 统一最高管理员：系统只将 SUPER_ADMIN 作为全局最大权限角色。

INSERT INTO sys_role (
  role_code, role_name, role_type, data_scope_type, approval_amount_min, approval_amount_max,
  status, description, created_by, updated_by, is_deleted, version
)
SELECT 'SUPER_ADMIN', '最高管理员', 'ADMIN', 'ALL', NULL, NULL,
       1, '系统唯一最高权限角色', 0, 0, 0, 0
WHERE NOT EXISTS (
  SELECT 1 FROM sys_role WHERE role_code = 'SUPER_ADMIN'
);

UPDATE sys_role
SET role_name = '最高管理员',
    role_type = 'ADMIN',
    data_scope_type = 'ALL',
    status = 1,
    is_deleted = 0,
    description = '系统唯一最高权限角色'
WHERE role_code = 'SUPER_ADMIN';

INSERT INTO sys_role_permission (
  role_id, permission_id, status, created_by, updated_by, is_deleted, version
)
SELECT sr.id, p.id, 1, 0, 0, 0, 0
FROM sys_role sr
JOIN sys_permission p ON p.status = 1 AND p.is_deleted = 0
WHERE sr.role_code = 'SUPER_ADMIN'
ON DUPLICATE KEY UPDATE
  status = 1,
  is_deleted = 0,
  updated_at = CURRENT_TIMESTAMP,
  updated_by = 0,
  version = sys_role_permission.version + 1;

INSERT INTO sys_user_role (
  user_id, role_id, status, effective_from, effective_to,
  created_by, updated_by, is_deleted, version
)
SELECT DISTINCT u.id, super_role.id, 1, NULL, NULL, 0, 0, 0, 0
FROM sys_user u
JOIN sys_role super_role ON super_role.role_code = 'SUPER_ADMIN'
LEFT JOIN sys_user_role old_ur
  ON old_ur.user_id = u.id
  AND old_ur.status = 1
  AND old_ur.is_deleted = 0
LEFT JOIN sys_role old_role
  ON old_role.id = old_ur.role_id
  AND old_role.role_code IN ('ADMIN', 'SYS_ADMIN', 'SUPER_ADMIN')
  AND old_role.status = 1
  AND old_role.is_deleted = 0
WHERE u.is_deleted = 0
  AND (
    u.user_type IN ('ADMIN', 'SYS_ADMIN', 'SUPER_ADMIN')
    OR old_role.id IS NOT NULL
  )
ON DUPLICATE KEY UPDATE
  status = 1,
  effective_from = NULL,
  effective_to = NULL,
  is_deleted = 0,
  updated_at = CURRENT_TIMESTAMP,
  updated_by = 0,
  version = sys_user_role.version + 1;

UPDATE sys_user u
SET u.user_type = 'SUPER_ADMIN',
    u.data_scope_type = 'ALL',
    u.updated_at = CURRENT_TIMESTAMP,
    u.updated_by = 0,
    u.version = u.version + 1
WHERE u.is_deleted = 0
  AND (
    u.user_type IN ('ADMIN', 'SYS_ADMIN', 'SUPER_ADMIN')
    OR EXISTS (
      SELECT 1
      FROM sys_user_role ur
      JOIN sys_role r ON r.id = ur.role_id
      WHERE ur.user_id = u.id
        AND ur.status = 1
        AND ur.is_deleted = 0
        AND r.role_code IN ('ADMIN', 'SYS_ADMIN', 'SUPER_ADMIN')
        AND r.status = 1
        AND r.is_deleted = 0
    )
  );

UPDATE approval_record
SET approver_role_code = 'SUPER_ADMIN',
    updated_at = CURRENT_TIMESTAMP
WHERE approver_role_code IN ('ADMIN', 'SYS_ADMIN');

UPDATE approval_task
SET role_code = 'SUPER_ADMIN',
    updated_at = CURRENT_TIMESTAMP
WHERE role_code IN ('ADMIN', 'SYS_ADMIN');

UPDATE platform_permission_request
SET role_code = NULL,
    updated_at = CURRENT_TIMESTAMP
WHERE role_code IN ('ADMIN', 'SYS_ADMIN');

UPDATE sys_role_permission rp
JOIN sys_role r ON r.id = rp.role_id
SET rp.status = 0,
    rp.is_deleted = 1,
    rp.updated_at = CURRENT_TIMESTAMP,
    rp.updated_by = 0,
    rp.version = rp.version + 1
WHERE r.role_code IN ('ADMIN', 'SYS_ADMIN');

UPDATE sys_user_role ur
JOIN sys_role r ON r.id = ur.role_id
SET ur.status = 0,
    ur.is_deleted = 1,
    ur.updated_at = CURRENT_TIMESTAMP,
    ur.updated_by = 0,
    ur.version = ur.version + 1
WHERE r.role_code IN ('ADMIN', 'SYS_ADMIN');

UPDATE sys_role
SET status = 0,
    is_deleted = 1,
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 0,
    version = version + 1,
    description = 'Legacy highest-admin role disabled; use SUPER_ADMIN only'
WHERE role_code IN ('ADMIN', 'SYS_ADMIN');
