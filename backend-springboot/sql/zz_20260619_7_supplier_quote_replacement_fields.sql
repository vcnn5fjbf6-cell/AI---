SET NAMES utf8mb4;

SET @db_name = DATABASE();

SET @sql = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE supplier_quote_item ADD COLUMN replacement_item_name VARCHAR(255) NULL COMMENT ''替换商品名称'' AFTER replacement_flag',
    'SELECT 1'
  )
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'supplier_quote_item' AND COLUMN_NAME = 'replacement_item_name'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE supplier_quote_item ADD COLUMN replacement_spec_model VARCHAR(255) NULL COMMENT ''替换规格型号'' AFTER replacement_item_name',
    'SELECT 1'
  )
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'supplier_quote_item' AND COLUMN_NAME = 'replacement_spec_model'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE supplier_quote_item ADD COLUMN replacement_ecommerce_url VARCHAR(1000) NULL COMMENT ''替换商品链接'' AFTER replacement_spec_model',
    'SELECT 1'
  )
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'supplier_quote_item' AND COLUMN_NAME = 'replacement_ecommerce_url'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE supplier_quote_item ADD COLUMN replacement_unit_price DECIMAL(18,2) NULL COMMENT ''替换商品报价单价'' AFTER replacement_ecommerce_url',
    'SELECT 1'
  )
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'supplier_quote_item' AND COLUMN_NAME = 'replacement_unit_price'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE supplier_quote_item ADD COLUMN replacement_line_amount DECIMAL(18,2) NULL COMMENT ''替换商品报价金额'' AFTER replacement_unit_price',
    'SELECT 1'
  )
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'supplier_quote_item' AND COLUMN_NAME = 'replacement_line_amount'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE supplier_quote_item ADD COLUMN replacement_reason VARCHAR(500) NULL COMMENT ''替换原因'' AFTER replacement_line_amount',
    'SELECT 1'
  )
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'supplier_quote_item' AND COLUMN_NAME = 'replacement_reason'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE supplier_quote_item ADD COLUMN replacement_review_status VARCHAR(32) NULL COMMENT ''替换确认状态 PENDING/APPROVED/REJECTED/AUTO_REJECTED'' AFTER replacement_reason',
    'SELECT 1'
  )
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'supplier_quote_item' AND COLUMN_NAME = 'replacement_review_status'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE supplier_quote_item ADD COLUMN replacement_reviewed_by BIGINT NULL COMMENT ''替换确认人ID'' AFTER replacement_review_status',
    'SELECT 1'
  )
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'supplier_quote_item' AND COLUMN_NAME = 'replacement_reviewed_by'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE supplier_quote_item ADD COLUMN replacement_reviewed_at DATETIME NULL COMMENT ''替换确认时间'' AFTER replacement_reviewed_by',
    'SELECT 1'
  )
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'supplier_quote_item' AND COLUMN_NAME = 'replacement_reviewed_at'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE supplier_quote_item ADD COLUMN replacement_review_remark VARCHAR(500) NULL COMMENT ''替换确认备注'' AFTER replacement_reviewed_at',
    'SELECT 1'
  )
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'supplier_quote_item' AND COLUMN_NAME = 'replacement_review_remark'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

