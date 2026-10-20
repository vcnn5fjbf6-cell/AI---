SET NAMES utf8mb4;

SET @column_exists := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'org_scope'
    AND COLUMN_NAME = 'district_name'
);

SET @ddl := IF(
  @column_exists = 0,
  'ALTER TABLE org_scope ADD COLUMN district_name VARCHAR(128) NULL COMMENT ''区县'' AFTER city',
  'SELECT 1'
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'org_scope'
    AND COLUMN_NAME = 'town_name'
);

SET @ddl := IF(
  @column_exists = 0,
  'ALTER TABLE org_scope ADD COLUMN town_name VARCHAR(128) NULL COMMENT ''乡镇/街道'' AFTER district_name',
  'SELECT 1'
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE sys_permission
SET path = '/api/admin/procurement/departments/**',
    http_method = '*',
    updated_at = NOW(),
    version = version + 1
WHERE perm_code = 'procurement_route:manage'
  AND is_deleted = 0;

INSERT INTO sys_permission
  (perm_code, perm_name, module_code, perm_type, parent_id, path, http_method, sort_no, status, description, created_by, updated_by, is_deleted, version)
SELECT 'procurement_route:address', '部门地址维护', 'PROCUREMENT_ROUTE', 'BUTTON', NULL, '/api/admin/procurement/departments/**', '*', 100, 1, '维护部门省市区县镇和送达地址', 0, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'procurement_route:address' AND is_deleted = 0);

INSERT INTO sys_role_permission (role_id, permission_id, status, created_by, updated_by, is_deleted, version)
SELECT r.id, p.id, 1, 0, 0, 0, 0
FROM sys_role r
JOIN sys_permission p ON p.perm_code = 'procurement_route:address' AND p.is_deleted = 0
WHERE r.role_code IN ('ADMIN', 'SYS_ADMIN', 'SUPER_ADMIN')
  AND r.is_deleted = 0
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permission rp
    WHERE rp.role_id = r.id
      AND rp.permission_id = p.id
      AND rp.is_deleted = 0
  );
