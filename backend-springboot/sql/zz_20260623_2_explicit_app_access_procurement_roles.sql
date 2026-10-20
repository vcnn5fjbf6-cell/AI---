SET NAMES utf8mb4;

INSERT INTO platform_unit_app_scope (unit_id, app_code, status, remark, created_by, updated_by, is_deleted, version)
SELECT u.id, 'PROCUREMENT', 1, '补齐技术部采购系统申请范围', 0, 0, 0, 0
FROM platform_unit u
WHERE u.unit_code = 'ENG'
  AND u.is_deleted = 0
  AND NOT EXISTS (
    SELECT 1
    FROM platform_unit_app_scope existing_scope
    WHERE existing_scope.unit_id = u.id
      AND existing_scope.app_code = 'PROCUREMENT'
      AND existing_scope.is_deleted = 0
  );

UPDATE platform_unit_app_scope scope_row
JOIN platform_unit u ON u.id = scope_row.unit_id
SET scope_row.status = 1,
    scope_row.remark = '补齐技术部采购系统申请范围',
    scope_row.updated_at = NOW()
WHERE u.unit_code = 'ENG'
  AND u.is_deleted = 0
  AND scope_row.app_code = 'PROCUREMENT'
  AND scope_row.is_deleted = 0
  AND scope_row.status <> 1;

INSERT INTO platform_user_app_access (user_id, app_code, status, granted_by, granted_at, remark, created_by, updated_by, is_deleted, version)
SELECT u.id, 'PROCUREMENT', 1, 0, NOW(), '补齐采购流程角色准入', 0, 0, 0, 0
FROM sys_user u
WHERE u.is_deleted = 0
  AND UPPER(u.user_type) IN (
    'ADMIN',
    'SYS_ADMIN',
    'SUPER_ADMIN',
    'PURCHASER',
    'MANAGER',
    'REGION_MANAGER',
    'SENIOR_MANAGER',
    'ENGINEER',
    'FLOOR_LEADER',
    'SUPPLIER'
  )
  AND NOT EXISTS (
    SELECT 1
    FROM platform_user_app_access existing_access
    WHERE existing_access.user_id = u.id
      AND existing_access.app_code = 'PROCUREMENT'
      AND existing_access.is_deleted = 0
  );

UPDATE platform_user_app_access access_row
JOIN sys_user u ON u.id = access_row.user_id
SET access_row.status = 1,
    access_row.granted_by = 0,
    access_row.granted_at = NOW(),
    access_row.remark = '补齐采购流程角色准入',
    access_row.updated_at = NOW()
WHERE u.is_deleted = 0
  AND UPPER(u.user_type) IN (
    'ADMIN',
    'SYS_ADMIN',
    'SUPER_ADMIN',
    'PURCHASER',
    'MANAGER',
    'REGION_MANAGER',
    'SENIOR_MANAGER',
    'ENGINEER',
    'FLOOR_LEADER',
    'SUPPLIER'
  )
  AND access_row.app_code = 'PROCUREMENT'
  AND access_row.is_deleted = 0
  AND access_row.status <> 1;
