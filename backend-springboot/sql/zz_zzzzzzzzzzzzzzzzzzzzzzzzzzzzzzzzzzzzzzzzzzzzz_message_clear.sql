SET @column_exists := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'procurement_message_read'
    AND COLUMN_NAME = 'action_type'
);

SET @ddl := IF(
  @column_exists = 0,
  'ALTER TABLE procurement_message_read ADD COLUMN action_type VARCHAR(32) NOT NULL DEFAULT ''READ'' COMMENT ''消息动作 READ已读 CLEARED已清除'' AFTER message_key',
  'SELECT 1'
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
