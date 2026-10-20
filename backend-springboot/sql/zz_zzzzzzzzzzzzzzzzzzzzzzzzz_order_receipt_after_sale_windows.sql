SET NAMES utf8mb4;

SET @db_name = DATABASE();

SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'supplier_quote_item' AND COLUMN_NAME = 'receipt_status') = 0,
  'ALTER TABLE supplier_quote_item ADD COLUMN receipt_status VARCHAR(32) NULL COMMENT ''收货状态 PENDING/RECEIVED'' AFTER awarded',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'supplier_quote_item' AND COLUMN_NAME = 'receipt_due_at') = 0,
  'ALTER TABLE supplier_quote_item ADD COLUMN receipt_due_at DATETIME NULL COMMENT ''自动收货截止时间'' AFTER receipt_status',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'supplier_quote_item' AND COLUMN_NAME = 'received_by') = 0,
  'ALTER TABLE supplier_quote_item ADD COLUMN received_by BIGINT NULL COMMENT ''收货确认人ID，0表示系统自动收货'' AFTER receipt_due_at',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'supplier_quote_item' AND COLUMN_NAME = 'received_at') = 0,
  'ALTER TABLE supplier_quote_item ADD COLUMN received_at DATETIME NULL COMMENT ''收货确认时间'' AFTER received_by',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'supplier_quote_item' AND COLUMN_NAME = 'receipt_remark') = 0,
  'ALTER TABLE supplier_quote_item ADD COLUMN receipt_remark VARCHAR(1000) NULL COMMENT ''收货备注'' AFTER received_at',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'supplier_quote_item' AND COLUMN_NAME = 'auto_received') = 0,
  'ALTER TABLE supplier_quote_item ADD COLUMN auto_received TINYINT NOT NULL DEFAULT 0 COMMENT ''是否系统自动收货 0否 1是'' AFTER receipt_remark',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'supplier_quote_item' AND INDEX_NAME = 'idx_quote_item_receipt') = 0,
  'ALTER TABLE supplier_quote_item ADD INDEX idx_quote_item_receipt (receipt_status, receipt_due_at)',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

INSERT INTO procurement_setting
  (setting_key, setting_value, setting_group, setting_label, value_type, sort_no, remark, created_by, updated_by, is_deleted, version)
VALUES
  ('order_auto_receipt_days', '7', 'RULE', '自动收货天数', 'NUMBER', 130, '供应商确认中标后，订单超过该天数未手动收货则系统自动收货', 0, 0, 0, 0),
  ('after_sale_no_reason_enabled', 'true', 'RULE', '启用7天无理由退货', 'BOOLEAN', 140, '开启后，收货后指定天数内可申请无理由退货', 0, 0, 0, 0),
  ('after_sale_no_reason_days', '7', 'RULE', '无理由退货天数', 'NUMBER', 150, '从确认收货时间起计算的无理由退货窗口天数', 0, 0, 0, 0),
  ('after_sale_return_exchange_enabled', 'true', 'RULE', '启用14天退换货', 'BOOLEAN', 160, '开启后，收货后指定天数内可申请退换货或商品更换', 0, 0, 0, 0),
  ('after_sale_return_exchange_days', '14', 'RULE', '退换货天数', 'NUMBER', 170, '从确认收货时间起计算的退换货窗口天数', 0, 0, 0, 0)
ON DUPLICATE KEY UPDATE
  setting_group = VALUES(setting_group),
  setting_label = VALUES(setting_label),
  value_type = VALUES(value_type),
  sort_no = VALUES(sort_no),
  remark = VALUES(remark),
  updated_at = CURRENT_TIMESTAMP;

UPDATE supplier_quote_item sqi
JOIN supplier_quote sq ON sq.id = sqi.quote_id
SET sqi.receipt_status = COALESCE(sqi.receipt_status, 'PENDING'),
    sqi.receipt_due_at = COALESCE(sqi.receipt_due_at, DATE_ADD(COALESCE(sq.supplier_confirmed_at, sq.updated_at, NOW()), INTERVAL 7 DAY)),
    sqi.auto_received = COALESCE(sqi.auto_received, 0)
WHERE sqi.is_deleted = 0
  AND sqi.awarded = 1
  AND sqi.status = 'ACTIVE'
  AND sq.supplier_confirm_status = 'CONFIRMED';
