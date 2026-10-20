UPDATE procurement_approval_route
SET over_price_rule = 'ITEM_ACTUAL_GT_REFERENCE_113'
WHERE over_price_rule IN (
  'ITEM_ACTUAL_GT_REFERENCE_110',
  '单项实际价 > 参考价 * 110%'
);

SET @approval_policy_exists := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.TABLES
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'procurement_approval_policy'
);
SET @dml := IF(
  @approval_policy_exists > 0,
  'UPDATE procurement_approval_policy SET over_price_rule = ''ITEM_ACTUAL_GT_REFERENCE_113'' WHERE over_price_rule IN (''ITEM_ACTUAL_GT_REFERENCE_110'', ''单项实际价 > 参考价 * 110%'')',
  'SELECT 1'
);
PREPARE stmt FROM @dml;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
