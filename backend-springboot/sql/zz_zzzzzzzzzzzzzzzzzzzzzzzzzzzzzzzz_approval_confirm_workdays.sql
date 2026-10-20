-- Make approval confirmation timeout configurable and move the default from
-- 2 business days to 4 business days. The pending-task due date extension is
-- guarded by the previous setting value so repeated deploys do not keep moving
-- deadlines after an administrator customizes the value.
SET @approval_workdays_setting_exists := (
    SELECT COUNT(*)
    FROM procurement_setting
    WHERE setting_key = 'approval_confirm_workdays'
      AND is_deleted = 0
);

SET @approval_workdays_old_value := (
    SELECT setting_value
    FROM procurement_setting
    WHERE setting_key = 'approval_confirm_workdays'
      AND is_deleted = 0
    ORDER BY id
    LIMIT 1
);

SET @should_extend_pending_due := (
    @approval_workdays_setting_exists = 0
    OR @approval_workdays_old_value IS NULL
    OR TRIM(@approval_workdays_old_value) = ''
    OR TRIM(@approval_workdays_old_value) = '2'
);

INSERT INTO procurement_setting
  (setting_key, setting_value, setting_group, setting_label, value_type, sort_no, remark,
   created_by, updated_by, is_deleted, version)
VALUES
  ('approval_confirm_workdays', '4', 'RULE', '审批确认工作日', 'NUMBER', 95,
   '审批人最长确认工作日数；超期确认仍可提交，但普通采购顺延到下月执行',
   0, 0, 0, 0)
ON DUPLICATE KEY UPDATE
  setting_value = CASE
    WHEN @should_extend_pending_due THEN VALUES(setting_value)
    ELSE setting_value
  END,
  setting_group = VALUES(setting_group),
  setting_label = VALUES(setting_label),
  value_type = VALUES(value_type),
  sort_no = VALUES(sort_no),
  remark = VALUES(remark),
  updated_at = NOW(),
  is_deleted = 0;

UPDATE approval_task
SET due_at = DATE_ADD(
    COALESCE(created_at, updated_at, NOW()),
    INTERVAL CASE DAYOFWEEK(COALESCE(created_at, updated_at, NOW()))
        WHEN 1 THEN 4  -- Sunday -> Thursday
        WHEN 2 THEN 4  -- Monday -> Friday
        WHEN 3 THEN 6  -- Tuesday -> next Monday
        WHEN 4 THEN 6  -- Wednesday -> next Tuesday
        WHEN 5 THEN 6  -- Thursday -> next Wednesday
        WHEN 6 THEN 6  -- Friday -> next Thursday
        WHEN 7 THEN 5  -- Saturday -> next Thursday
        ELSE 4
    END DAY
)
WHERE @should_extend_pending_due
  AND status = 'PENDING';

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
