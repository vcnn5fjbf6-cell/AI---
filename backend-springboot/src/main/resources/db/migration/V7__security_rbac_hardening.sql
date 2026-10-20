SET NAMES utf8mb4;

INSERT INTO sys_permission
  (perm_code, perm_name, module_code, perm_type, parent_id, path, http_method, sort_no, status, description, created_by, updated_by, is_deleted, version)
SELECT 'supplier:ban', 'Ban supplier', 'SUPPLIER', 'BUTTON', NULL, '/api/admin/suppliers/*/ban', 'POST', 180, 1, 'Ban a supplier from quoting and award confirmation', 0, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'supplier:ban' AND is_deleted = 0);

INSERT INTO sys_permission
  (perm_code, perm_name, module_code, perm_type, parent_id, path, http_method, sort_no, status, description, created_by, updated_by, is_deleted, version)
SELECT 'supplier:unban', 'Unban supplier', 'SUPPLIER', 'BUTTON', NULL, '/api/admin/suppliers/*/unban', 'POST', 181, 1, 'Restore a banned supplier', 0, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'supplier:unban' AND is_deleted = 0);

INSERT INTO sys_permission
  (perm_code, perm_name, module_code, perm_type, parent_id, path, http_method, sort_no, status, description, created_by, updated_by, is_deleted, version)
SELECT 'supplier:bidding-suspension-release', 'Release supplier suspension', 'SUPPLIER', 'BUTTON', NULL, '/api/admin/suppliers/*/bidding-suspension/release', 'POST', 182, 1, 'Manually release automatic supplier bidding suspension', 0, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'supplier:bidding-suspension-release' AND is_deleted = 0);

INSERT INTO sys_permission
  (perm_code, perm_name, module_code, perm_type, parent_id, path, http_method, sort_no, status, description, created_by, updated_by, is_deleted, version)
SELECT 'supplier_quote:read', 'Read supplier quotes', 'SUPPLIER_QUOTE', 'BUTTON', NULL, '/api/supplier/quote-requests/**', 'GET', 183, 1, 'Allow internal procurement actors to read supplier quote request summaries', 0, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'supplier_quote:read' AND is_deleted = 0);

INSERT INTO sys_permission
  (perm_code, perm_name, module_code, perm_type, parent_id, path, http_method, sort_no, status, description, created_by, updated_by, is_deleted, version)
SELECT 'procurement_department:options', 'Read procurement departments', 'PROCUREMENT_ROUTE', 'BUTTON', NULL, '/api/workflow/procurement-departments/**', 'GET', 184, 1, 'Read sanitized procurement department options', 0, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'procurement_department:options' AND is_deleted = 0);

INSERT INTO sys_role_permission (role_id, permission_id, status, created_by, updated_by, is_deleted, version)
SELECT r.id, p.id, 1, 0, 0, 0, 0
FROM sys_role r
JOIN sys_permission p ON p.perm_code IN (
    'supplier:ban',
    'supplier:unban',
    'supplier:bidding-suspension-release'
  ) AND p.is_deleted = 0
WHERE r.role_code IN ('ADMIN', 'SYS_ADMIN', 'SUPER_ADMIN')
  AND r.is_deleted = 0
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permission rp
    WHERE rp.role_id = r.id
      AND rp.permission_id = p.id
      AND rp.is_deleted = 0
  );

INSERT INTO sys_role_permission (role_id, permission_id, status, created_by, updated_by, is_deleted, version)
SELECT r.id, p.id, 1, 0, 0, 0, 0
FROM sys_role r
JOIN sys_permission p ON p.perm_code IN (
    'supplier_quote:read',
    'procurement_department:options'
  ) AND p.is_deleted = 0
WHERE r.role_code IN ('ADMIN', 'SYS_ADMIN', 'SUPER_ADMIN', 'ENGINEER', 'PURCHASER', 'MANAGER', 'FLOOR_LEADER', 'REGION_MANAGER', 'SENIOR_MANAGER')
  AND r.is_deleted = 0
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permission rp
    WHERE rp.role_id = r.id
      AND rp.permission_id = p.id
      AND rp.is_deleted = 0
  );
