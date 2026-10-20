SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS system_error_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  module_code VARCHAR(64) NOT NULL DEFAULT 'UNKNOWN' COMMENT '模块编码',
  function_name VARCHAR(160) NULL COMMENT '功能/接口',
  error_code VARCHAR(128) NULL COMMENT '错误编码',
  error_message TEXT NOT NULL COMMENT '报错内容',
  exception_class VARCHAR(255) NULL COMMENT '异常类',
  stack_trace MEDIUMTEXT NULL COMMENT '异常堆栈',
  request_method VARCHAR(16) NULL COMMENT '请求方法',
  request_uri VARCHAR(512) NULL COMMENT '请求路径',
  user_id BIGINT NULL COMMENT '触发用户ID',
  username VARCHAR(64) NULL COMMENT '触发账号',
  client_ip VARCHAR(64) NULL COMMENT '客户端IP',
  status VARCHAR(32) NOT NULL DEFAULT 'OPEN' COMMENT '处理状态 OPEN/FIXED/IGNORED',
  fix_remark VARCHAR(1000) NULL COMMENT '修复说明',
  fixed_by BIGINT NULL COMMENT '处理人ID',
  fixed_at DATETIME NULL COMMENT '处理时间',
  occurred_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  created_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人',
  updated_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人',
  is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  version INT NOT NULL DEFAULT 0 COMMENT '版本号',
  KEY idx_error_log_status (status),
  KEY idx_error_log_module (module_code),
  KEY idx_error_log_occurred (occurred_at),
  KEY idx_error_log_deleted (is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统错误台账';

INSERT INTO sys_permission
  (perm_code, perm_name, module_code, perm_type, parent_id, path, http_method, sort_no, status, description, created_by, updated_by, is_deleted, version)
SELECT 'platform_error_log:manage', '错误台账管理', 'PLATFORM', 'BUTTON', NULL, '/api/admin/error-logs/**', '*', 99, 1, '查询并维护系统错误台账', 0, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'platform_error_log:manage' AND is_deleted = 0);

INSERT INTO sys_role_permission (role_id, permission_id, status, created_by, updated_by, is_deleted, version)
SELECT r.id, p.id, 1, 0, 0, 0, 0
FROM sys_role r
JOIN sys_permission p ON p.perm_code = 'platform_error_log:manage' AND p.is_deleted = 0
WHERE r.role_code IN ('ADMIN', 'SYS_ADMIN', 'SUPER_ADMIN')
  AND r.is_deleted = 0
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permission existing_perm
    WHERE existing_perm.role_id = r.id
      AND existing_perm.permission_id = p.id
      AND existing_perm.is_deleted = 0
  );
