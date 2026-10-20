SET @column_type := (
  SELECT DATA_TYPE
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'purchase_request_item'
    AND COLUMN_NAME = 'ecommerce_url'
  LIMIT 1
);

SET @ddl := IF(
  @column_type IS NOT NULL AND @column_type NOT IN ('text', 'mediumtext', 'longtext'),
  'ALTER TABLE purchase_request_item MODIFY COLUMN ecommerce_url TEXT NULL COMMENT ''电商链接''',
  'SELECT 1'
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
