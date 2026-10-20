SET NAMES utf8mb4;

SET @db_name = DATABASE();
SET @sql = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE sys_user ADD COLUMN session_secret VARCHAR(128) NULL COMMENT ''当前登录会话随机值'' AFTER data_scope_type',
    'SELECT 1'
  )
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'session_secret'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
