SET @db_name := DATABASE();

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'approval_task' AND COLUMN_NAME = 'due_at') = 0,
  'ALTER TABLE approval_task ADD COLUMN due_at DATETIME NULL COMMENT ''审批任务截止时间'' AFTER closed_reason',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'approval_task' AND INDEX_NAME = 'idx_approval_task_due') = 0,
  'ALTER TABLE approval_task ADD INDEX idx_approval_task_due (status, due_at)',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE approval_task
SET due_at = DATE_ADD(
    COALESCE(created_at, updated_at, NOW()),
    INTERVAL CASE DAYOFWEEK(COALESCE(created_at, updated_at, NOW()))
        WHEN 1 THEN 4
        WHEN 2 THEN 4
        WHEN 3 THEN 6
        WHEN 4 THEN 6
        WHEN 5 THEN 6
        WHEN 6 THEN 6
        WHEN 7 THEN 5
        ELSE 4
    END DAY
)
WHERE status = 'PENDING'
  AND due_at IS NULL;
