SET NAMES utf8mb4;

SET @db_name = DATABASE();

SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = @db_name
     AND TABLE_NAME = 'purchase_request'
     AND COLUMN_NAME = 'acting_user_id') = 0,
  'ALTER TABLE purchase_request ADD COLUMN acting_user_id BIGINT NULL COMMENT ''管理员代选/代提交用户ID'' AFTER budget_code',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = @db_name
     AND TABLE_NAME = 'purchase_request'
     AND COLUMN_NAME = 'acting_user_name') = 0,
  'ALTER TABLE purchase_request ADD COLUMN acting_user_name VARCHAR(128) NULL COMMENT ''管理员代选/代提交用户名称快照'' AFTER acting_user_id',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = @db_name
     AND TABLE_NAME = 'purchase_request'
     AND COLUMN_NAME = 'acting_reason') = 0,
  'ALTER TABLE purchase_request ADD COLUMN acting_reason VARCHAR(500) NULL COMMENT ''管理员代选/代提交原因'' AFTER acting_user_name',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.STATISTICS
   WHERE TABLE_SCHEMA = @db_name
     AND TABLE_NAME = 'purchase_request'
     AND INDEX_NAME = 'idx_req_acting_user') = 0,
  'ALTER TABLE purchase_request ADD INDEX idx_req_acting_user (acting_user_id)',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
