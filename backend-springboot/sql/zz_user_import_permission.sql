INSERT INTO sys_permission
  (perm_code, perm_name, module_code, perm_type, parent_id, path, http_method, sort_no, status, description, created_by, updated_by, is_deleted, version)
SELECT 'sys_user:import', '批量导入用户', 'SYSTEM_USER', 'BUTTON', NULL, '/api/admin/users/import', 'POST', 96, 1, '通过中文 Excel 模板批量新增或更新用户', 0, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'sys_user:import' AND is_deleted = 0);

UPDATE sys_permission
SET description = '通过中文 Excel 模板批量新增或更新用户',
    updated_at = NOW()
WHERE perm_code = 'sys_user:import'
  AND is_deleted = 0;

INSERT INTO sys_role_permission
  (role_id, permission_id, status, created_by, updated_by, is_deleted, version)
SELECT r.id, p.id, 1, 0, 0, 0, 0
FROM sys_role r
JOIN sys_permission p ON p.perm_code = 'sys_user:import' AND p.is_deleted = 0
WHERE r.role_code IN ('ADMIN', 'SYS_ADMIN', 'SUPER_ADMIN')
  AND r.is_deleted = 0
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permission existing_perm
    WHERE existing_perm.role_id = r.id
      AND existing_perm.permission_id = p.id
      AND existing_perm.is_deleted = 0
  );
