SET NAMES utf8mb4;

SET @db_name = DATABASE();
SET @sql = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE supplier_quote_item ADD COLUMN replacement_flag TINYINT NOT NULL DEFAULT 0 COMMENT ''是否替换商品 0否 1是'' AFTER quote_remark',
    'SELECT 1'
  )
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'supplier_quote_item' AND COLUMN_NAME = 'replacement_flag'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

