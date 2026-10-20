SET @db_name = DATABASE();

SET @sql = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE purchase_request ADD COLUMN quote_countdown_started_at DATETIME NULL COMMENT ''3家报价倒计时开始时间'' AFTER leader_confirmed_at',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'purchase_request' AND COLUMN_NAME = 'quote_countdown_started_at'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE purchase_request ADD COLUMN quote_countdown_due_at DATETIME NULL COMMENT ''3家报价倒计时截止时间'' AFTER quote_countdown_started_at',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'purchase_request' AND COLUMN_NAME = 'quote_countdown_due_at'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    COUNT(*) = 0,
    'CREATE INDEX idx_req_quote_countdown_due ON purchase_request (quote_countdown_due_at)',
    'SELECT 1'
  )
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'purchase_request' AND INDEX_NAME = 'idx_req_quote_countdown_due'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
