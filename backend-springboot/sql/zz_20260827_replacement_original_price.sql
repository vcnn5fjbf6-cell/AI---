SET NAMES utf8mb4;

SET @column_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'supplier_quote_item'
      AND COLUMN_NAME = 'supplier_submitted_unit_price'
);
SET @column_sql = IF(
    @column_exists = 0,
    'ALTER TABLE supplier_quote_item ADD COLUMN supplier_submitted_unit_price DECIMAL(18,2) NULL COMMENT ''供应商原始提交单价'' AFTER unit_price',
    'SELECT 1'
);
PREPARE column_statement FROM @column_sql;
EXECUTE column_statement;
DEALLOCATE PREPARE column_statement;

UPDATE supplier_quote_item
SET supplier_submitted_unit_price = unit_price
WHERE supplier_submitted_unit_price IS NULL;
