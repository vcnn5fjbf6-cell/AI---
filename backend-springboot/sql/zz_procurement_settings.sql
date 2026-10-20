SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS procurement_setting (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'primary id',
  setting_key VARCHAR(128) NOT NULL COMMENT 'setting key',
  setting_value TEXT NULL COMMENT 'setting value',
  setting_group VARCHAR(32) NOT NULL COMMENT 'API/RULE',
  setting_label VARCHAR(128) NOT NULL COMMENT 'setting label',
  value_type VARCHAR(32) NOT NULL DEFAULT 'TEXT' COMMENT 'TEXT/PASSWORD/NUMBER/BOOLEAN',
  sort_no INT NOT NULL DEFAULT 0 COMMENT 'sort number',
  remark VARCHAR(500) NULL COMMENT 'remark',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated time',
  created_by BIGINT NOT NULL DEFAULT 0 COMMENT 'created by',
  updated_by BIGINT NOT NULL DEFAULT 0 COMMENT 'updated by',
  is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT 'logical delete flag',
  version INT NOT NULL DEFAULT 0 COMMENT 'version',
  UNIQUE KEY uk_procurement_setting_key (setting_key),
  KEY idx_procurement_setting_group (setting_group),
  KEY idx_procurement_setting_deleted (is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='procurement settings';

CREATE TABLE IF NOT EXISTS procurement_department_limit (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'primary id',
  dept_code VARCHAR(64) NULL COMMENT 'department code',
  dept_name VARCHAR(128) NOT NULL COMMENT 'department name',
  single_request_limit DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT 'single request limit',
  monthly_limit DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT 'monthly limit',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '1 active 0 inactive',
  remark VARCHAR(500) NULL COMMENT 'remark',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated time',
  created_by BIGINT NOT NULL DEFAULT 0 COMMENT 'created by',
  updated_by BIGINT NOT NULL DEFAULT 0 COMMENT 'updated by',
  is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT 'logical delete flag',
  version INT NOT NULL DEFAULT 0 COMMENT 'version',
  UNIQUE KEY uk_procurement_dept_limit_code (dept_code),
  KEY idx_procurement_dept_limit_name (dept_name),
  KEY idx_procurement_dept_limit_deleted (is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='procurement department limits';

INSERT INTO sys_permission
  (perm_code, perm_name, module_code, perm_type, parent_id, path, http_method, sort_no, status, description, created_by, updated_by, is_deleted, version)
SELECT 'procurement_settings:manage', '采购管理设置', 'PROCUREMENT_SETTINGS', 'BUTTON', NULL, '/api/admin/procurement/settings/**', '*', 98, 1, '维护采购 API 变量、采购日期和部门采购上限', 0, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'procurement_settings:manage' AND is_deleted = 0);

INSERT INTO sys_role_permission (role_id, permission_id, status, created_by, updated_by, is_deleted, version)
SELECT r.id, p.id, 1, 0, 0, 0, 0
FROM sys_role r
JOIN sys_permission p ON p.perm_code = 'procurement_settings:manage' AND p.is_deleted = 0
WHERE r.role_code IN ('ADMIN', 'SYS_ADMIN', 'SUPER_ADMIN')
  AND r.is_deleted = 0
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permission existing_perm
    WHERE existing_perm.role_id = r.id
      AND existing_perm.permission_id = p.id
      AND existing_perm.is_deleted = 0
  );
