SET NAMES utf8mb4;

SET @db_name = DATABASE();

SET @sql = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE purchase_request ADD COLUMN reference_total_amount DECIMAL(18,2) NULL COMMENT ''申请时参考总金额'' AFTER total_amount',
    'SELECT 1'
  )
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'purchase_request' AND COLUMN_NAME = 'reference_total_amount'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE purchase_request ADD COLUMN price_source VARCHAR(32) NOT NULL DEFAULT ''REFERENCE'' COMMENT ''当前价格来源 REFERENCE/SUPPLIER_QUOTE'' AFTER reference_total_amount',
    'SELECT 1'
  )
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'purchase_request' AND COLUMN_NAME = 'price_source'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE purchase_request_item ADD COLUMN reference_unit_price DECIMAL(18,2) NULL COMMENT ''申请时参考单价'' AFTER unit_price',
    'SELECT 1'
  )
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'purchase_request_item' AND COLUMN_NAME = 'reference_unit_price'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE purchase_request_item ADD COLUMN reference_line_amount DECIMAL(18,2) NULL COMMENT ''申请时参考行金额'' AFTER line_amount',
    'SELECT 1'
  )
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'purchase_request_item' AND COLUMN_NAME = 'reference_line_amount'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE purchase_request
SET reference_total_amount = total_amount
WHERE reference_total_amount IS NULL;

UPDATE purchase_request
SET price_source = 'REFERENCE'
WHERE price_source IS NULL OR TRIM(price_source) = '';

UPDATE purchase_request_item
SET reference_unit_price = unit_price
WHERE reference_unit_price IS NULL;

UPDATE purchase_request_item
SET reference_line_amount = line_amount
WHERE reference_line_amount IS NULL;

DROP TEMPORARY TABLE IF EXISTS tmp_awarded_price_item_20260721;
CREATE TEMPORARY TABLE tmp_awarded_price_item_20260721 AS
SELECT
  sqi.purchase_request_id,
  sqi.purchase_request_item_id,
  MAX(CASE
    WHEN sqi.replacement_review_status = 'APPROVED' AND sqi.replacement_unit_price IS NOT NULL
      THEN sqi.replacement_unit_price
    ELSE sqi.unit_price
  END) AS effective_unit_price,
  MAX(CASE
    WHEN sqi.replacement_review_status = 'APPROVED' AND sqi.replacement_line_amount IS NOT NULL
      THEN sqi.replacement_line_amount
    ELSE sqi.line_amount
  END) AS effective_line_amount
FROM supplier_quote_item sqi
INNER JOIN supplier_quote sq ON sq.id = sqi.quote_id AND sq.is_deleted = 0
WHERE sqi.is_deleted = 0
  AND sqi.status = 'ACTIVE'
  AND sqi.awarded = 1
  AND sq.status = 'SUBMITTED'
GROUP BY sqi.purchase_request_id, sqi.purchase_request_item_id
HAVING COUNT(*) = 1;

DROP TEMPORARY TABLE IF EXISTS tmp_complete_awarded_request_20260721;
CREATE TEMPORARY TABLE tmp_complete_awarded_request_20260721 AS
SELECT pr.id AS purchase_request_id
FROM purchase_request pr
INNER JOIN purchase_request_item pri
  ON pri.purchase_request_id = pr.id
  AND pri.is_deleted = 0
  AND (pri.status IS NULL OR pri.status <> 'REJECTED')
LEFT JOIN tmp_awarded_price_item_20260721 awarded
  ON awarded.purchase_request_id = pr.id
  AND awarded.purchase_request_item_id = pri.id
WHERE pr.is_deleted = 0
  AND pr.status IN ('PENDING_APPROVAL', 'APPROVED')
GROUP BY pr.id
HAVING COUNT(pri.id) > 0
  AND COUNT(pri.id) = COUNT(awarded.purchase_request_item_id)
  AND MIN(awarded.effective_unit_price) > 0
  AND MIN(awarded.effective_line_amount) > 0;

UPDATE purchase_request_item pri
INNER JOIN tmp_awarded_price_item_20260721 awarded
  ON awarded.purchase_request_item_id = pri.id
INNER JOIN tmp_complete_awarded_request_20260721 complete
  ON complete.purchase_request_id = pri.purchase_request_id
SET pri.unit_price = awarded.effective_unit_price,
    pri.line_amount = awarded.effective_line_amount
WHERE pri.is_deleted = 0
  AND (pri.status IS NULL OR pri.status <> 'REJECTED');

UPDATE purchase_request pr
INNER JOIN (
  SELECT awarded.purchase_request_id, ROUND(SUM(awarded.effective_line_amount), 2) AS effective_total_amount
  FROM tmp_awarded_price_item_20260721 awarded
  INNER JOIN tmp_complete_awarded_request_20260721 complete
    ON complete.purchase_request_id = awarded.purchase_request_id
  INNER JOIN purchase_request_item pri
    ON pri.id = awarded.purchase_request_item_id
    AND pri.is_deleted = 0
    AND (pri.status IS NULL OR pri.status <> 'REJECTED')
  GROUP BY awarded.purchase_request_id
) totals ON totals.purchase_request_id = pr.id
SET pr.total_amount = totals.effective_total_amount,
    pr.price_source = 'SUPPLIER_QUOTE';

DROP TEMPORARY TABLE IF EXISTS tmp_complete_awarded_request_20260721;
DROP TEMPORARY TABLE IF EXISTS tmp_awarded_price_item_20260721;
