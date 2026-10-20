SET NAMES utf8mb4;

SET @db_name = DATABASE();
SET @sql = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE sys_user ADD COLUMN password_changed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''密码最后修改时间'' AFTER must_change_password',
    'SELECT 1'
  )
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'password_changed_at'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE sys_user
SET password_changed_at = COALESCE(password_changed_at, updated_at, created_at, NOW())
WHERE password_changed_at IS NULL;

CREATE TABLE IF NOT EXISTS sys_user_password_history (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  user_id BIGINT NOT NULL COMMENT '用户ID',
  password_hash VARCHAR(255) NOT NULL COMMENT '历史密码哈希',
  changed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '修改时间',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  created_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人',
  updated_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人',
  is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记 0未删 1已删',
  version INT NOT NULL DEFAULT 0 COMMENT '版本号',
  KEY idx_user_password_history_user (user_id, changed_at),
  KEY idx_user_password_history_deleted (is_deleted),
  CONSTRAINT fk_password_history_user FOREIGN KEY (user_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户密码历史表';
