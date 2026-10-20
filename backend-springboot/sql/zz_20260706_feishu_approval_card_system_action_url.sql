SET NAMES utf8mb4;

INSERT INTO procurement_setting
  (setting_key, setting_value, setting_group, setting_label, value_type, sort_no, remark, created_by, updated_by, is_deleted, version)
VALUES
  ('feishu_approval_card_system_action_url', 'https://mercy-sheep-essentials-genius.trycloudflare.com/login?redirect=/workbench', 'FEISHU', '系统处理入口地址', 'TEXT', 85, '审批卡片中“系统处理”按钮打开的完整地址，可填写登录后跳转工作台的链接', 0, 0, 0, 0)
ON DUPLICATE KEY UPDATE
  setting_group = VALUES(setting_group),
  setting_label = VALUES(setting_label),
  value_type = VALUES(value_type),
  sort_no = VALUES(sort_no),
  remark = VALUES(remark),
  updated_at = CURRENT_TIMESTAMP;

UPDATE procurement_setting
SET setting_label = '系统访问基址',
    remark = '系统可访问基址，例如 http://100.125.220.73:10086；用于图片中转和未配置系统处理入口地址时的卡片兜底',
    updated_at = CURRENT_TIMESTAMP
WHERE setting_key = 'feishu_approval_card_app_base_url';
