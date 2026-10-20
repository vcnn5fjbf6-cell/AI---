SET NAMES utf8mb4;

SET @db_name = DATABASE();

SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'supplier_quote' AND COLUMN_NAME = 'supplier_confirm_due_at') = 0,
  'ALTER TABLE supplier_quote ADD COLUMN supplier_confirm_due_at DATETIME NULL COMMENT ''供应商中标确认截止时间'' AFTER supplier_confirmed_at',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'supplier_quote' AND COLUMN_NAME = 'supplier_confirm_notified_at') = 0,
  'ALTER TABLE supplier_quote ADD COLUMN supplier_confirm_notified_at DATETIME NULL COMMENT ''供应商中标确认邮件发送时间'' AFTER supplier_confirm_due_at',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'supplier_quote' AND INDEX_NAME = 'idx_quote_confirm_due') = 0,
  'ALTER TABLE supplier_quote ADD INDEX idx_quote_confirm_due (supplier_confirm_status, supplier_confirm_due_at)',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

INSERT INTO procurement_setting
  (setting_key, setting_value, setting_group, setting_label, value_type, sort_no, remark, created_by, updated_by, is_deleted, version)
VALUES
  ('mail_enabled', 'false', 'MAIL', '启用中标邮件', 'BOOLEAN', 10, '开启后，供应商中标会发送确认邮件', 0, 0, 0, 0),
  ('mail_smtp_host', '', 'MAIL', 'SMTP 服务器', 'TEXT', 20, '企业邮箱 SMTP 服务器地址', 0, 0, 0, 0),
  ('mail_smtp_port', '25', 'MAIL', 'SMTP 端口', 'NUMBER', 30, '企业邮箱 SMTP 端口，常见为 25/465/587', 0, 0, 0, 0),
  ('mail_smtp_username', '', 'MAIL', 'SMTP 用户名', 'TEXT', 40, '企业邮箱发信账号', 0, 0, 0, 0),
  ('mail_smtp_password', '', 'MAIL', 'SMTP 密码', 'PASSWORD', 50, '企业邮箱发信密码或授权码', 0, 0, 0, 0),
  ('mail_smtp_from', '', 'MAIL', '发件邮箱', 'TEXT', 60, '中标确认邮件发件地址', 0, 0, 0, 0),
  ('mail_smtp_ssl', 'false', 'MAIL', '启用 SSL', 'BOOLEAN', 70, 'SMTP 是否使用 SSL', 0, 0, 0, 0),
  ('mail_smtp_starttls', 'false', 'MAIL', '启用 STARTTLS', 'BOOLEAN', 80, 'SMTP 是否使用 STARTTLS', 0, 0, 0, 0),
  ('supplier_award_confirm_workdays', '4', 'RULE', '供应商中标确认工作日', 'NUMBER', 80, '中标后供应商最长确认工作日数', 0, 0, 0, 0),
  ('supplier_confirm_holidays', '', 'RULE', '中标确认节假日', 'TEXT', 90, '逗号分隔日期，如 2026-05-01,2026-05-04，不计入确认期限', 0, 0, 0, 0),
  ('supplier_confirm_extra_workdays', '', 'RULE', '中标确认调休工作日', 'TEXT', 100, '逗号分隔日期，周末调休上班日会计入确认期限', 0, 0, 0, 0)
ON DUPLICATE KEY UPDATE
  setting_group = VALUES(setting_group),
  setting_label = VALUES(setting_label),
  value_type = VALUES(value_type),
  sort_no = VALUES(sort_no),
  remark = VALUES(remark),
  updated_at = CURRENT_TIMESTAMP;
