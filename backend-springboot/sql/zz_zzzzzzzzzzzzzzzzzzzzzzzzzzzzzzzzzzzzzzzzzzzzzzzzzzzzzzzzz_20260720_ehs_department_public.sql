SET NAMES utf8mb4;

SET @db_name := DATABASE();

ALTER TABLE ehs_permit
  MODIFY COLUMN applicant_user_id BIGINT NULL COMMENT '内部申请人用户ID，外部申请为空';

SET @ddl := IF(
  EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db_name AND TABLE_NAME='ehs_permit' AND COLUMN_NAME='applicant_phone'),
  'SELECT 1',
  'ALTER TABLE ehs_permit ADD COLUMN applicant_phone VARCHAR(32) NULL COMMENT ''外部申请人联系电话'' AFTER applicant_name'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := IF(
  EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db_name AND TABLE_NAME='ehs_permit' AND COLUMN_NAME='applicant_company'),
  'SELECT 1',
  'ALTER TABLE ehs_permit ADD COLUMN applicant_company VARCHAR(255) NULL COMMENT ''外部申请单位'' AFTER applicant_phone'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := IF(
  EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db_name AND TABLE_NAME='ehs_permit' AND COLUMN_NAME='source_type'),
  'SELECT 1',
  'ALTER TABLE ehs_permit ADD COLUMN source_type VARCHAR(16) NOT NULL DEFAULT ''INTERNAL'' COMMENT ''INTERNAL/PUBLIC'' AFTER applicant_company'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := IF(
  EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db_name AND TABLE_NAME='ehs_permit' AND COLUMN_NAME='approval_route_id'),
  'SELECT 1',
  'ALTER TABLE ehs_permit ADD COLUMN approval_route_id BIGINT NULL COMMENT ''提交时选择的部门审批配置'' AFTER source_type'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := IF(
  EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db_name AND TABLE_NAME='ehs_permit' AND COLUMN_NAME='dept_code'),
  'SELECT 1',
  'ALTER TABLE ehs_permit ADD COLUMN dept_code VARCHAR(128) NULL COMMENT ''部门编码快照'' AFTER approval_route_id'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := IF(
  EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db_name AND TABLE_NAME='ehs_permit' AND COLUMN_NAME='dept_name'),
  'SELECT 1',
  'ALTER TABLE ehs_permit ADD COLUMN dept_name VARCHAR(255) NULL COMMENT ''部门名称快照'' AFTER dept_code'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := IF(
  EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db_name AND TABLE_NAME='ehs_permit' AND COLUMN_NAME='approval_snapshot_json'),
  'SELECT 1',
  'ALTER TABLE ehs_permit ADD COLUMN approval_snapshot_json JSON NULL COMMENT ''部门审批节点、候选人及通过规则快照'' AFTER special_approver_user_ids_json'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := IF(
  EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=@db_name AND TABLE_NAME='ehs_permit' AND INDEX_NAME='idx_ehs_permit_route'),
  'SELECT 1',
  'ALTER TABLE ehs_permit ADD KEY idx_ehs_permit_route (approval_route_id, source_type, is_deleted)'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS ehs_public_access_token (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  permit_id BIGINT NOT NULL COMMENT '令牌授权的作业票ID',
  token_hash CHAR(64) NOT NULL COMMENT '随机令牌SHA-256摘要',
  token_type VARCHAR(16) NOT NULL COMMENT 'APPLICANT/APPROVER',
  user_id BIGINT NULL COMMENT '审批令牌绑定的内部用户ID',
  expires_at DATETIME NOT NULL,
  used_at DATETIME NULL,
  revoked_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  created_by BIGINT NOT NULL DEFAULT 0,
  updated_by BIGINT NOT NULL DEFAULT 0,
  is_deleted TINYINT NOT NULL DEFAULT 0,
  version INT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_ehs_public_token_hash (token_hash),
  KEY idx_ehs_public_token_permit (permit_id, token_type, revoked_at, expires_at),
  KEY idx_ehs_public_token_user (user_id, token_type, revoked_at, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='EHS外部申请与免登录审批访问令牌';

UPDATE sys_role
SET is_deleted = 1,
    updated_at = CURRENT_TIMESTAMP
WHERE role_code = 'EHS_APPROVER'
  AND is_deleted = 0;
