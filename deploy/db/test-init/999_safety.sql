SET NAMES utf8mb4;

INSERT INTO procurement_setting
  (setting_key, setting_value, setting_group, setting_label, value_type, sort_no, remark, created_by, updated_by, is_deleted, version)
VALUES
  ('feishu_bot_enabled', 'false', 'FEISHU', '启用飞书采购 AI 助手', 'BOOLEAN', 28, '测试环境强制关闭', 0, 0, 0, 0),
  ('feishu_approval_card_enabled', 'false', 'FEISHU', '启用审批卡片', 'BOOLEAN', 30, '测试环境强制关闭', 0, 0, 0, 0),
  ('feishu_approval_card_heartbeat_enabled', 'false', 'FEISHU', '启用审批卡片心跳', 'BOOLEAN', 40, '测试环境强制关闭', 0, 0, 0, 0),
  ('feishu_approval_card_long_connection_enabled', 'false', 'FEISHU', '启用审批卡片长连接', 'BOOLEAN', 50, '测试环境强制关闭', 0, 0, 0, 0),
  ('feishu_supplier_quote_notice_enabled', 'false', 'FEISHU', '启用供应商报价通知', 'BOOLEAN', 110, '测试环境强制关闭', 0, 0, 0, 0),
  ('feishu_supplier_award_notice_enabled', 'false', 'FEISHU', '启用供应商中标通知', 'BOOLEAN', 125, '测试环境强制关闭', 0, 0, 0, 0),
  ('feishu_admin_card_copy_enabled', 'false', 'FEISHU', '管理员接收全部卡片', 'BOOLEAN', 130, '测试环境强制关闭', 0, 0, 0, 0),
  ('mail_enabled', 'false', 'MAIL', '启用邮件', 'BOOLEAN', 10, '测试环境强制关闭', 0, 0, 0, 0),
  ('m3_vision_enabled', 'false', 'API', '启用 M3 视觉识别', 'BOOLEAN', 110, '测试环境强制关闭', 0, 0, 0, 0),
  ('disaster_weather_enabled', 'false', 'DISASTER', '启用灾害天气', 'BOOLEAN', 10, '测试环境强制关闭', 0, 0, 0, 0),
  ('disaster_weather_daily_push_enabled', 'false', 'DISASTER', '启用天气日推', 'BOOLEAN', 20, '测试环境强制关闭', 0, 0, 0, 0),
  ('disaster_weather_weekly_push_enabled', 'false', 'DISASTER', '启用天气周推', 'BOOLEAN', 30, '测试环境强制关闭', 0, 0, 0, 0),
  ('disaster_weather_warning_push_enabled', 'false', 'DISASTER', '启用天气预警推送', 'BOOLEAN', 40, '测试环境强制关闭', 0, 0, 0, 0),
  ('disaster_weather_warning_resolve_push_enabled', 'false', 'DISASTER', '启用预警解除推送', 'BOOLEAN', 50, '测试环境强制关闭', 0, 0, 0, 0),
  ('disaster_weather_new_delivery_promotion_enabled', 'false', 'DISASTER', '启用新交付宣传', 'BOOLEAN', 60, '测试环境强制关闭', 0, 0, 0, 0),
  ('disaster_weather_rainy_season_enabled', 'false', 'DISASTER', '启用汛期策略', 'BOOLEAN', 70, '测试环境强制关闭', 0, 0, 0, 0)
ON DUPLICATE KEY UPDATE
  setting_value = VALUES(setting_value),
  setting_group = VALUES(setting_group),
  setting_label = VALUES(setting_label),
  value_type = VALUES(value_type),
  remark = VALUES(remark),
  updated_by = 0,
  is_deleted = 0;

UPDATE procurement_setting
SET setting_value = '', updated_by = 0, is_deleted = 0
WHERE setting_key IN (
  'dify_api_base_url',
  'dify_api_key',
  'jd_enterprise_api_url',
  'jd_enterprise_app_key',
  'jd_enterprise_app_secret',
  'alibaba_1688_api_url',
  'alibaba_1688_app_key',
  'alibaba_1688_app_secret',
  'shopping_tool_token',
  'm3_base_url',
  'm3_api_key',
  'procurement_ai_image_public_base_url',
  'feishu_app_id',
  'feishu_app_secret',
  'feishu_approval_card_verification_token',
  'feishu_approval_card_encrypt_key',
  'feishu_approval_card_app_base_url',
  'feishu_approval_card_system_action_url',
  'feishu_admin_card_copy_usernames',
  'mail_smtp_host',
  'mail_smtp_username',
  'mail_smtp_password',
  'mail_smtp_from',
  'disaster_weather_duty_chat_id',
  'disaster_weather_push_group',
  'disaster_weather_qweather_host',
  'disaster_weather_qweather_key',
  'disaster_weather_qweather_warning_host',
  'disaster_weather_qweather_warning_token'
);

UPDATE procurement_setting
SET setting_value = '', updated_by = 0, is_deleted = 1
WHERE setting_key LIKE 'version_upgrade_github_%';

UPDATE sys_user
SET mobile = NULL,
    email = 'demo.user010@example.invalid',
    feishu_user_id = NULL,
    feishu_open_id = NULL,
    feishu_union_id = NULL,
    session_secret = NULL
WHERE username = 'test-admin' AND is_deleted = 0;
