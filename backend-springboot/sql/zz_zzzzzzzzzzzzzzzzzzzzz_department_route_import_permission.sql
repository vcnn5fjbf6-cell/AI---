SET NAMES utf8mb4;

INSERT INTO sys_permission
  (perm_code, perm_name, module_code, perm_type, parent_id, path, http_method, sort_no, status, description, created_by, updated_by, is_deleted, version)
SELECT 'procurement_route:import', '部门批量导入修改', 'PROCUREMENT_ROUTE', 'BUTTON', NULL, '/api/admin/procurement/**/import*', '*', 101, 1, '导出部门审批配置模板并批量导入修改；导入时任一行校验失败则整批回滚', 0, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'procurement_route:import' AND is_deleted = 0);

UPDATE sys_permission
SET perm_name = '部门批量导入修改',
    module_code = 'PROCUREMENT_ROUTE',
    perm_type = 'BUTTON',
    path = '/api/admin/procurement/**/import*',
    http_method = '*',
    sort_no = 101,
    status = 1,
    description = '导出部门审批配置模板并批量导入修改；导入时任一行校验失败则整批回滚',
    updated_at = NOW(),
    version = version + 1
WHERE perm_code = 'procurement_route:import'
  AND is_deleted = 0;

INSERT INTO sys_role_permission (role_id, permission_id, status, created_by, updated_by, is_deleted, version)
SELECT r.id, p.id, 1, 0, 0, 0, 0
FROM sys_role r
JOIN sys_permission p ON p.perm_code = 'procurement_route:import' AND p.is_deleted = 0
WHERE r.role_code IN ('ADMIN', 'SYS_ADMIN', 'SUPER_ADMIN')
  AND r.is_deleted = 0
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permission rp
    WHERE rp.role_id = r.id
      AND rp.permission_id = p.id
      AND rp.is_deleted = 0
  );
