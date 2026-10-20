SET NAMES utf8mb4;

SET @db_name = DATABASE();

SET @sql = IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'purchase_request' AND COLUMN_NAME = 'direct_award_applied') = 0,
  'ALTER TABLE purchase_request ADD COLUMN direct_award_applied TINYINT NOT NULL DEFAULT 0 COMMENT ''低额紧急采购是否定向报价'' AFTER quote_countdown_due_at',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'purchase_request' AND COLUMN_NAME = 'direct_award_supplier_id') = 0,
  'ALTER TABLE purchase_request ADD COLUMN direct_award_supplier_id BIGINT NULL COMMENT ''低额紧急采购定向供应商ID'' AFTER direct_award_applied',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'purchase_request' AND COLUMN_NAME = 'direct_award_supplier_name') = 0,
  'ALTER TABLE purchase_request ADD COLUMN direct_award_supplier_name VARCHAR(128) NULL COMMENT ''低额紧急采购定向供应商名称'' AFTER direct_award_supplier_id',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'purchase_request' AND COLUMN_NAME = 'direct_award_threshold_amount') = 0,
  'ALTER TABLE purchase_request ADD COLUMN direct_award_threshold_amount DECIMAL(18,2) NULL COMMENT ''低额紧急采购定向报价阈值'' AFTER direct_award_supplier_name',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'purchase_request' AND COLUMN_NAME = 'direct_award_reason') = 0,
  'ALTER TABLE purchase_request ADD COLUMN direct_award_reason VARCHAR(500) NULL COMMENT ''低额紧急采购定向报价说明'' AFTER direct_award_threshold_amount',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
   WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'purchase_request' AND INDEX_NAME = 'idx_req_direct_award_supplier') = 0,
  'CREATE INDEX idx_req_direct_award_supplier ON purchase_request (direct_award_supplier_id)',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

INSERT INTO procurement_setting
  (setting_key, setting_value, setting_group, setting_label, value_type, sort_no, remark, created_by, updated_by, is_deleted, version)
VALUES
  ('urgent_direct_award_enabled', 'true', 'RULE', '低额紧急采购定向报价', 'BOOLEAN', 80, '开启后，低于阈值的紧急采购优先定向推送历史单项中标次数最多的供应商；定向供应商报价不超过阈值130%时免三方比价，超过则转入三方比价', 0, 0, 0, 0),
  ('urgent_direct_award_threshold', '1000', 'RULE', '低额紧急采购阈值', 'NUMBER', 90, '紧急采购有效金额低于该值时启用定向报价；默认1000元时定向报价上限为1300元', 0, 0, 0, 0)
ON DUPLICATE KEY UPDATE
  setting_group = VALUES(setting_group),
  setting_label = VALUES(setting_label),
  value_type = VALUES(value_type),
  sort_no = VALUES(sort_no),
  remark = VALUES(remark);
