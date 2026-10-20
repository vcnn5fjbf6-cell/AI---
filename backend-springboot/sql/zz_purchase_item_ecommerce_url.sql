SET @column_exists := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'purchase_request_item'
    AND COLUMN_NAME = 'ecommerce_url'
);

SET @ddl := IF(
  @column_exists = 0,
  'ALTER TABLE purchase_request_item ADD COLUMN ecommerce_url VARCHAR(500) NULL COMMENT ''电商链接'' AFTER purpose',
  'SELECT 1'
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
