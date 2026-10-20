SET NAMES utf8mb4;

SET @column_exists := (
  SELECT COUNT(1)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'supplier_feishu_quote_notice'
    AND COLUMN_NAME = 'notice_type'
);
SET @sql := IF(
  @column_exists = 0,
  'ALTER TABLE supplier_feishu_quote_notice ADD COLUMN notice_type VARCHAR(32) NOT NULL DEFAULT ''QUOTE_TASK'' COMMENT ''通知类型 QUOTE_TASK/AWARD_CONFIRM'' AFTER supplier_id',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE supplier_feishu_quote_notice
SET notice_type = 'QUOTE_TASK'
WHERE notice_type IS NULL OR notice_type = '';

SET @idx_exists := (
  SELECT COUNT(1)
  FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'supplier_feishu_quote_notice'
    AND INDEX_NAME = 'uk_supplier_quote_notice_target'
);
SET @sql := IF(
  @idx_exists > 0,
  'ALTER TABLE supplier_feishu_quote_notice DROP INDEX uk_supplier_quote_notice_target',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE supplier_feishu_quote_notice
  ADD UNIQUE KEY uk_supplier_quote_notice_target
    (purchase_request_id, supplier_id, notice_type, receive_id_type, receive_id, binding_id, is_deleted);

INSERT INTO procurement_setting
(setting_key, setting_value, setting_group, setting_label, value_type, sort_no, remark, created_by, updated_by, is_deleted, version)
SELECT 'feishu_supplier_award_notice_enabled', 'true', 'FEISHU', '启用供应商中标确认通知', 'BOOLEAN', 125,
       '开启后，系统会扫描待供应商确认的中标记录，并向已绑定供应商推送中标确认卡片',
       0, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM procurement_setting WHERE setting_key = 'feishu_supplier_award_notice_enabled' AND is_deleted = 0);
