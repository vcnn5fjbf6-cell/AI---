SET @approval_task_exists := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.TABLES
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'approval_task'
);
SET @approval_task_reminder_stage_exists := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'approval_task'
    AND COLUMN_NAME = 'reminder_stage'
);
SET @ddl := IF(
  @approval_task_exists > 0 AND @approval_task_reminder_stage_exists = 0,
  'ALTER TABLE approval_task ADD COLUMN reminder_stage INT NOT NULL DEFAULT 0 COMMENT ''超价复核提醒阶段 0未提醒/1剩余12小时/2剩余2小时/3已超时'' AFTER due_at',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE procurement_approval_route
SET over_price_rule = 'ITEM_ACTUAL_GT_REFERENCE_113'
WHERE over_price_rule = 'ITEM_ACTUAL_GT_REFERENCE_110';

SET @approval_policy_exists := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.TABLES
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'procurement_approval_policy'
);
SET @dml := IF(
  @approval_policy_exists > 0,
  'UPDATE procurement_approval_policy SET over_price_rule = ''ITEM_ACTUAL_GT_REFERENCE_113'' WHERE over_price_rule = ''ITEM_ACTUAL_GT_REFERENCE_110''',
  'SELECT 1'
);
PREPARE stmt FROM @dml;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
