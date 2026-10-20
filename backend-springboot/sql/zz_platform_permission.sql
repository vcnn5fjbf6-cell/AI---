SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS platform_unit (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  unit_code VARCHAR(64) NOT NULL COMMENT '单元编码',
  unit_name VARCHAR(128) NOT NULL COMMENT '单元名称',
  parent_id BIGINT NULL COMMENT '上级单元ID',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '状态 1启用 0停用',
  remark VARCHAR(500) NULL COMMENT '备注',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  created_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人',
  updated_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人',
  is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  UNIQUE KEY uk_platform_unit_code (unit_code),
  KEY idx_platform_unit_deleted (is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台组织单元表';

CREATE TABLE IF NOT EXISTS platform_unit_app_scope (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  unit_id BIGINT NOT NULL COMMENT '单元ID',
  app_code VARCHAR(32) NOT NULL COMMENT '系统编码 MAINTENANCE/PROCUREMENT/PLATFORM',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '状态 1允许申请 0停用',
  remark VARCHAR(500) NULL COMMENT '备注',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  created_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人',
  updated_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人',
  is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  UNIQUE KEY uk_platform_unit_app (unit_id, app_code),
  KEY idx_platform_unit_app_deleted (is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台单元系统申请范围表';

CREATE TABLE IF NOT EXISTS platform_user_app_access (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  user_id BIGINT NOT NULL COMMENT '用户ID',
  app_code VARCHAR(32) NOT NULL COMMENT '系统编码 MAINTENANCE/PROCUREMENT/PLATFORM',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '状态 1启用 0停用',
  granted_by BIGINT NULL COMMENT '授权人',
  granted_at DATETIME NULL COMMENT '授权时间',
  remark VARCHAR(500) NULL COMMENT '备注',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  created_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人',
  updated_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人',
  is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  UNIQUE KEY uk_platform_user_app (user_id, app_code),
  KEY idx_platform_user_app_deleted (is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台用户系统准入表';

CREATE TABLE IF NOT EXISTS platform_permission_request (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  applicant_user_id BIGINT NOT NULL COMMENT '申请人用户ID',
  applicant_name VARCHAR(64) NOT NULL COMMENT '申请人姓名',
  applicant_email VARCHAR(128) NULL COMMENT '申请人邮箱',
  applicant_mobile VARCHAR(32) NULL COMMENT '申请人手机号',
  unit_id BIGINT NULL COMMENT '申请单元ID',
  app_code VARCHAR(32) NOT NULL COMMENT '申请系统编码',
  role_code VARCHAR(64) NULL COMMENT '申请角色编码',
  reason VARCHAR(500) NULL COMMENT '申请原因',
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '状态 PENDING/APPROVED/REJECTED',
  reviewer_user_id BIGINT NULL COMMENT '审批人ID',
  review_comment VARCHAR(500) NULL COMMENT '审批意见',
  reviewed_at DATETIME NULL COMMENT '审批时间',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  created_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人',
  updated_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人',
  is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  KEY idx_platform_request_user (applicant_user_id),
  KEY idx_platform_request_status (status),
  KEY idx_platform_request_deleted (is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台权限申请表';

INSERT INTO sys_permission
  (perm_code, perm_name, module_code, perm_type, parent_id, path, http_method, sort_no, status, description, created_by, updated_by, is_deleted, version)
SELECT 'platform_permission:manage', '平台权限管理', 'PLATFORM_PERMISSION', 'BUTTON', NULL, '/api/admin/platform/**', '*', 90, 1, '维护账号准入、系统授权、单元范围和权限审批', 0, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'platform_permission:manage' AND is_deleted = 0);

INSERT INTO sys_permission
  (perm_code, perm_name, module_code, perm_type, parent_id, path, http_method, sort_no, status, description, created_by, updated_by, is_deleted, version)
SELECT 'platform_permission:apply', '平台权限申请', 'PLATFORM_PERMISSION', 'BUTTON', NULL, '/api/platform/**', '*', 91, 1, '当前用户查看和申请平台权限', 0, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'platform_permission:apply' AND is_deleted = 0);

INSERT INTO sys_permission
  (perm_code, perm_name, module_code, perm_type, parent_id, path, http_method, sort_no, status, description, created_by, updated_by, is_deleted, version)
SELECT 'sys_user:view', '查看用户', 'SYSTEM_USER', 'BUTTON', NULL, '/api/admin/users/**', 'GET', 92, 1, '查看用户列表和详情', 0, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'sys_user:view' AND is_deleted = 0);

INSERT INTO sys_permission
  (perm_code, perm_name, module_code, perm_type, parent_id, path, http_method, sort_no, status, description, created_by, updated_by, is_deleted, version)
SELECT 'sys_role:view', '查看角色', 'SYSTEM_ROLE', 'BUTTON', NULL, '/api/admin/roles/**', 'GET', 93, 1, '查看角色和权限列表', 0, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'sys_role:view' AND is_deleted = 0);

INSERT INTO sys_permission
  (perm_code, perm_name, module_code, perm_type, parent_id, path, http_method, sort_no, status, description, created_by, updated_by, is_deleted, version)
SELECT 'supplier:view', '查看供应商', 'SUPPLIER', 'BUTTON', NULL, '/api/admin/suppliers/**', 'GET', 94, 1, '查看供应商列表和详情', 0, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'supplier:view' AND is_deleted = 0);

INSERT INTO sys_role_permission (role_id, permission_id, status, created_by, updated_by, is_deleted, version)
SELECT r.id, p.id, 1, 0, 0, 0, 0
FROM sys_role r
JOIN sys_permission p ON p.perm_code IN (
    'platform_permission:manage',
    'sys_user:view',
    'sys_role:view',
    'supplier:view'
  ) AND p.is_deleted = 0
WHERE r.role_code IN ('ADMIN', 'SYS_ADMIN', 'SUPER_ADMIN')
  AND r.is_deleted = 0
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permission existing_perm
    WHERE existing_perm.role_id = r.id
      AND existing_perm.permission_id = p.id
      AND existing_perm.is_deleted = 0
  );

INSERT INTO sys_role_permission (role_id, permission_id, status, created_by, updated_by, is_deleted, version)
SELECT r.id, p.id, 1, 0, 0, 0, 0
FROM sys_role r
JOIN sys_permission p ON p.perm_code = 'platform_permission:apply' AND p.is_deleted = 0
WHERE r.is_deleted = 0
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permission existing_perm
    WHERE existing_perm.role_id = r.id
      AND existing_perm.permission_id = p.id
      AND existing_perm.is_deleted = 0
  );

INSERT INTO platform_unit (unit_code, unit_name, status, remark, created_by, updated_by, is_deleted, version)
SELECT 'IT', '信息部', 1, '默认平台管理单元', 0, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM platform_unit WHERE unit_code = 'IT' AND is_deleted = 0);

INSERT INTO platform_unit (unit_code, unit_name, status, remark, created_by, updated_by, is_deleted, version)
SELECT 'PUR', '采购部', 1, '默认采购单元', 0, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM platform_unit WHERE unit_code = 'PUR' AND is_deleted = 0);

INSERT INTO platform_unit (unit_code, unit_name, status, remark, created_by, updated_by, is_deleted, version)
SELECT 'ENG', '技术部', 1, '默认维保技术单元', 0, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM platform_unit WHERE unit_code = 'ENG' AND is_deleted = 0);

INSERT INTO platform_unit_app_scope (unit_id, app_code, status, remark, created_by, updated_by, is_deleted, version)
SELECT u.id, app.app_code, 1, '初始化允许申请范围', 0, 0, 0, 0
FROM platform_unit u
JOIN (
  SELECT 'IT' unit_code, 'MAINTENANCE' app_code UNION ALL
  SELECT 'IT', 'PROCUREMENT' UNION ALL
  SELECT 'IT', 'PLATFORM' UNION ALL
  SELECT 'PUR', 'PROCUREMENT' UNION ALL
  SELECT 'ENG', 'MAINTENANCE' UNION ALL
  SELECT 'ENG', 'PROCUREMENT'
) app ON app.unit_code = u.unit_code
WHERE NOT EXISTS (
  SELECT 1 FROM platform_unit_app_scope existing_scope
  WHERE existing_scope.unit_id = u.id
    AND existing_scope.app_code = app.app_code
    AND existing_scope.is_deleted = 0
);

INSERT INTO platform_user_app_access (user_id, app_code, status, granted_by, granted_at, remark, created_by, updated_by, is_deleted, version)
SELECT u.id, app.app_code, 1, 0, NOW(), '初始化系统准入', 0, 0, 0, 0
FROM sys_user u
JOIN (
  SELECT 'ADMIN' user_type, 'PROCUREMENT' app_code UNION ALL
  SELECT 'ADMIN', 'PLATFORM' UNION ALL
  SELECT 'SYS_ADMIN', 'PROCUREMENT' UNION ALL
  SELECT 'SYS_ADMIN', 'PLATFORM' UNION ALL
  SELECT 'SUPER_ADMIN', 'PROCUREMENT' UNION ALL
  SELECT 'SUPER_ADMIN', 'PLATFORM' UNION ALL
  SELECT 'PURCHASER', 'PROCUREMENT' UNION ALL
  SELECT 'MANAGER', 'PROCUREMENT' UNION ALL
  SELECT 'REGION_MANAGER', 'PROCUREMENT' UNION ALL
  SELECT 'SUPPLIER', 'PROCUREMENT' UNION ALL
  SELECT 'ENGINEER', 'PROCUREMENT' UNION ALL
  SELECT 'FLOOR_LEADER', 'PROCUREMENT' UNION ALL
  SELECT 'SENIOR_MANAGER', 'PROCUREMENT'
) app ON app.user_type = u.user_type
WHERE u.is_deleted = 0
  AND NOT EXISTS (
    SELECT 1 FROM platform_user_app_access existing_access
    WHERE existing_access.user_id = u.id
      AND existing_access.app_code = app.app_code
      AND existing_access.is_deleted = 0
  );
