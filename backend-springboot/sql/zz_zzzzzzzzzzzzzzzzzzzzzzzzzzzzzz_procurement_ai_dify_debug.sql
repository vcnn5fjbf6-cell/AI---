SET @column_exists := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'procurement_ai_message_log'
    AND COLUMN_NAME = 'purchase_request_id'
);
SET @ddl := IF(@column_exists = 0,
  'ALTER TABLE procurement_ai_message_log ADD COLUMN purchase_request_id BIGINT NULL COMMENT ''采购申请ID'' AFTER monthly_demand_no',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'procurement_ai_message_log'
    AND COLUMN_NAME = 'purchase_request_no'
);
SET @ddl := IF(@column_exists = 0,
  'ALTER TABLE procurement_ai_message_log ADD COLUMN purchase_request_no VARCHAR(64) NULL COMMENT ''采购申请单号'' AFTER purchase_request_id',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'procurement_ai_message_log'
    AND INDEX_NAME = 'idx_ai_msg_source_message'
);
SET @ddl := IF(@index_exists = 0,
  'CREATE INDEX idx_ai_msg_source_message ON procurement_ai_message_log (source_channel, message_id)',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'procurement_ai_message_log'
    AND COLUMN_NAME = 'dify_request_json'
);
SET @ddl := IF(@column_exists = 0,
  'ALTER TABLE procurement_ai_message_log ADD COLUMN dify_request_json MEDIUMTEXT NULL COMMENT ''Dify请求体，已脱敏'' AFTER raw_payload',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'procurement_ai_message_log'
    AND COLUMN_NAME = 'dify_response_text'
);
SET @ddl := IF(@column_exists = 0,
  'ALTER TABLE procurement_ai_message_log ADD COLUMN dify_response_text MEDIUMTEXT NULL COMMENT ''Dify原始响应'' AFTER dify_request_json',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'procurement_ai_message_log'
    AND COLUMN_NAME = 'dify_parsed_json'
);
SET @ddl := IF(@column_exists = 0,
  'ALTER TABLE procurement_ai_message_log ADD COLUMN dify_parsed_json MEDIUMTEXT NULL COMMENT ''Dify解析结果'' AFTER dify_response_text',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'procurement_ai_session'
    AND COLUMN_NAME = 'purchase_request_id'
);
SET @ddl := IF(@column_exists = 0,
  'ALTER TABLE procurement_ai_session ADD COLUMN purchase_request_id BIGINT NULL COMMENT ''采购申请ID'' AFTER monthly_demand_id',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'procurement_ai_session'
    AND COLUMN_NAME = 'dify_request_json'
);
SET @ddl := IF(@column_exists = 0,
  'ALTER TABLE procurement_ai_session ADD COLUMN dify_request_json MEDIUMTEXT NULL COMMENT ''Dify候选请求体，已脱敏'' AFTER ai_error',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'procurement_ai_session'
    AND COLUMN_NAME = 'dify_response_text'
);
SET @ddl := IF(@column_exists = 0,
  'ALTER TABLE procurement_ai_session ADD COLUMN dify_response_text MEDIUMTEXT NULL COMMENT ''Dify候选原始响应'' AFTER dify_request_json',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'procurement_ai_session'
    AND COLUMN_NAME = 'dify_parsed_json'
);
SET @ddl := IF(@column_exists = 0,
  'ALTER TABLE procurement_ai_session ADD COLUMN dify_parsed_json MEDIUMTEXT NULL COMMENT ''Dify候选解析结果'' AFTER dify_response_text',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;