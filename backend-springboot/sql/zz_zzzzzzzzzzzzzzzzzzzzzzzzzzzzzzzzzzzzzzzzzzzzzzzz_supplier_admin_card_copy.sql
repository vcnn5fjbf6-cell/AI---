SET NAMES utf8mb4;

SET @column_exists := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'feishu_approval_card_notification'
    AND COLUMN_NAME = 'recipient_user_id'
);
SET @ddl := IF(@column_exists = 0,
  'ALTER TABLE feishu_approval_card_notification ADD COLUMN recipient_user_id BIGINT NULL COMMENT ''卡片接收人用户ID'' AFTER approver_user_id',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE feishu_approval_card_notification
SET recipient_user_id = approver_user_id
WHERE recipient_user_id IS NULL;

SET @old_unique_exists := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'feishu_approval_card_notification'
    AND INDEX_NAME = 'uk_feishu_approval_card_task'
);
SET @ddl := IF(@old_unique_exists > 0,
  'ALTER TABLE feishu_approval_card_notification DROP INDEX uk_feishu_approval_card_task',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @new_unique_exists := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'feishu_approval_card_notification'
    AND INDEX_NAME = 'uk_feishu_approval_card_task_recipient'
);
SET @ddl := IF(@new_unique_exists = 0,
  'ALTER TABLE feishu_approval_card_notification ADD UNIQUE KEY uk_feishu_approval_card_task_recipient (approval_task_id, recipient_user_id, is_deleted)',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @recipient_index_exists := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'feishu_approval_card_notification'
    AND INDEX_NAME = 'idx_feishu_approval_card_recipient_status'
);
SET @ddl := IF(@recipient_index_exists = 0,
  'ALTER TABLE feishu_approval_card_notification ADD KEY idx_feishu_approval_card_recipient_status (recipient_user_id, status)',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

INSERT INTO procurement_setting
(setting_key, setting_value, setting_group, setting_label, value_type, sort_no, remark, created_by, updated_by, is_deleted, version)
SELECT 'feishu_admin_card_copy_enabled', 'true', 'FEISHU', '管理员接收全部卡片', 'BOOLEAN', 130,
       '开启后，系统会把正式审批卡片和供应商报价任务卡片同步抄送给配置的系统管理员账号；管理员点击审批卡片时按系统管理员权限代处理原审批任务',
       0, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM procurement_setting WHERE setting_key = 'feishu_admin_card_copy_enabled' AND is_deleted = 0);

INSERT INTO procurement_setting
(setting_key, setting_value, setting_group, setting_label, value_type, sort_no, remark, created_by, updated_by, is_deleted, version)
SELECT 'feishu_admin_card_copy_usernames', 'admin', 'FEISHU', '管理员抄送账号列表', 'TEXT', 140,
       '逗号分隔，可填用户名、工号、邮箱或用户ID；留空时自动选择所有启用的 SUPER_ADMIN/SYS_ADMIN/ADMIN 系统管理员账号',
       0, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM procurement_setting WHERE setting_key = 'feishu_admin_card_copy_usernames' AND is_deleted = 0);
