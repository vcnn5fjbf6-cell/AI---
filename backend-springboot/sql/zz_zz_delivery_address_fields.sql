SET @db_name = DATABASE();

SET @sql = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE purchase_request_item ADD COLUMN receiving_city VARCHAR(128) NULL COMMENT ''Receiving city'' AFTER ecommerce_url',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'purchase_request_item' AND COLUMN_NAME = 'receiving_city'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE purchase_request_item ADD COLUMN delivery_address VARCHAR(500) NULL COMMENT ''Delivery address'' AFTER receiving_city',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'purchase_request_item' AND COLUMN_NAME = 'delivery_address'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE supplier_quote_item ADD COLUMN receiving_city VARCHAR(128) NULL COMMENT ''Receiving city'' AFTER quote_remark',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'supplier_quote_item' AND COLUMN_NAME = 'receiving_city'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE supplier_quote_item ADD COLUMN delivery_address VARCHAR(500) NULL COMMENT ''Delivery address'' AFTER receiving_city',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'supplier_quote_item' AND COLUMN_NAME = 'delivery_address'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE purchase_request_item ADD COLUMN source_request_id BIGINT NULL COMMENT ''Source purchase request id'' AFTER delivery_address',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'purchase_request_item' AND COLUMN_NAME = 'source_request_id'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE purchase_request_item ADD COLUMN source_request_item_id BIGINT NULL COMMENT ''Source purchase request item id'' AFTER source_request_id',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'purchase_request_item' AND COLUMN_NAME = 'source_request_item_id'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
