SET NAMES utf8mb4;

INSERT INTO procurement_setting
  (setting_key, setting_value, setting_group, setting_label, value_type, sort_no, remark, created_by, updated_by, is_deleted, version)
VALUES
  ('feishu_bot_enabled', '', 'FEISHU', '启用飞书采购AI助手', 'BOOLEAN', 28, '开启后，飞书用户给机器人发送采购需求时，系统会转入 AI 候选流程并回复用户；留空时使用环境变量 FEISHU_BOT_ENABLED', 0, 0, 0, 0)
ON DUPLICATE KEY UPDATE
  setting_group = VALUES(setting_group),
  setting_label = VALUES(setting_label),
  value_type = VALUES(value_type),
  sort_no = VALUES(sort_no),
  remark = VALUES(remark),
  updated_at = CURRENT_TIMESTAMP;
