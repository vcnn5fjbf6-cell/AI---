INSERT INTO sys_permission
(perm_code, perm_name, module_code, perm_type, parent_id, path, http_method, sort_no, status, description, created_by, updated_by, is_deleted, version)
SELECT 'monthly_procurement:supplement', '本月需求补充采购需求', 'MONTHLY_PROCUREMENT', 'BUTTON', NULL, '/api/workflow/monthly-procurement/demands/*/supplement', 'POST', 123, 1, '从本月需求池复制为普通采购补充需求', 0, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'monthly_procurement:supplement' AND is_deleted = 0);

INSERT INTO sys_role_permission (role_id, permission_id, status, created_by, updated_by, is_deleted, version)
SELECT r.id, p.id, 1, 0, 0, 0, 0
FROM sys_role r
JOIN sys_permission p ON p.perm_code = 'monthly_procurement:supplement' AND p.is_deleted = 0
WHERE r.role_code IN ('ADMIN', 'SYS_ADMIN', 'SUPER_ADMIN', 'ENGINEER', 'PURCHASER', 'MANAGER', 'FLOOR_LEADER', 'REGION_MANAGER', 'SENIOR_MANAGER')
  AND r.is_deleted = 0
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permission rp
    WHERE rp.role_id = r.id AND rp.permission_id = p.id AND rp.is_deleted = 0
  );
