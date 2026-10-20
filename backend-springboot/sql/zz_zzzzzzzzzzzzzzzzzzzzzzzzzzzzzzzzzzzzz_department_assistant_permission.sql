SET @has_assistant_user_ids := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'procurement_approval_route'
    AND COLUMN_NAME = 'assistant_user_ids'
);
SET @ddl_assistant_user_ids := IF(
  @has_assistant_user_ids = 0,
  'ALTER TABLE procurement_approval_route ADD COLUMN assistant_user_ids VARCHAR(500) NULL COMMENT ''助理用户ID，逗号分隔'' AFTER manager_rule',
  'SELECT 1'
);
PREPARE stmt FROM @ddl_assistant_user_ids;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_assistant_names := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'procurement_approval_route'
    AND COLUMN_NAME = 'assistant_names'
);
SET @ddl_assistant_names := IF(
  @has_assistant_names = 0,
  'ALTER TABLE procurement_approval_route ADD COLUMN assistant_names VARCHAR(500) NULL COMMENT ''助理姓名，逗号分隔'' AFTER assistant_user_ids',
  'SELECT 1'
);
PREPARE stmt FROM @ddl_assistant_names;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
