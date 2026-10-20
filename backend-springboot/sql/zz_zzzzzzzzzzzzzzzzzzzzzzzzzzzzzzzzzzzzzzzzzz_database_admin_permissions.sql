SET NAMES utf8mb4;

INSERT INTO sys_permission
  (perm_code, perm_name, module_code, perm_type, parent_id, path, http_method, sort_no, status, description, created_by, updated_by, is_deleted, version)
VALUES
  ('database_admin:view', '数据库管理-查看', 'PLATFORM', 'BUTTON', NULL, '/api/admin/database/**', 'GET', 120, 1, '查看数据库表、字段结构和分页数据', 0, 0, 0, 0),
  ('database_admin:create', '数据库管理-新增', 'PLATFORM', 'BUTTON', NULL, '/api/admin/database/tables/*/rows', 'POST', 121, 1, '新增数据库表记录', 0, 0, 0, 0),
  ('database_admin:update', '数据库管理-编辑', 'PLATFORM', 'BUTTON', NULL, '/api/admin/database/tables/*/rows', 'PUT', 122, 1, '编辑数据库表记录', 0, 0, 0, 0),
  ('database_admin:delete', '数据库管理-删除', 'PLATFORM', 'BUTTON', NULL, '/api/admin/database/tables/*/rows', 'DELETE', 123, 1, '删除数据库表记录', 0, 0, 0, 0)
ON DUPLICATE KEY UPDATE
  perm_name = VALUES(perm_name),
  module_code = VALUES(module_code),
  perm_type = VALUES(perm_type),
  path = VALUES(path),
  http_method = VALUES(http_method),
  sort_no = VALUES(sort_no),
  status = VALUES(status),
  description = VALUES(description),
  is_deleted = 0,
  updated_by = 0;

INSERT INTO sys_role_permission (role_id, permission_id, status, created_by, updated_by, is_deleted, version)
SELECT r.id, p.id, 1, 0, 0, 0, 0
FROM sys_role r
JOIN sys_permission p ON p.perm_code IN (
  'database_admin:view',
  'database_admin:create',
  'database_admin:update',
  'database_admin:delete'
) AND p.is_deleted = 0
WHERE r.role_code = 'SUPER_ADMIN'
  AND r.is_deleted = 0
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permission existing_perm
    WHERE existing_perm.role_id = r.id
      AND existing_perm.permission_id = p.id
      AND existing_perm.is_deleted = 0
  );
