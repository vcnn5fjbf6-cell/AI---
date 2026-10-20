SET NAMES utf8mb4;

SET @db_name = DATABASE();

SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'supplier' AND COLUMN_NAME = 'bid_suspended_until') = 0,
  'ALTER TABLE supplier ADD COLUMN bid_suspended_until DATETIME NULL COMMENT ''暂停投标截止时间'' AFTER blacklist_reason',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'supplier' AND COLUMN_NAME = 'bid_suspended_reason') = 0,
  'ALTER TABLE supplier ADD COLUMN bid_suspended_reason VARCHAR(500) NULL COMMENT ''暂停投标原因'' AFTER bid_suspended_until',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'supplier' AND INDEX_NAME = 'idx_supplier_bid_suspend') = 0,
  'ALTER TABLE supplier ADD INDEX idx_supplier_bid_suspend (bid_suspended_until)',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
