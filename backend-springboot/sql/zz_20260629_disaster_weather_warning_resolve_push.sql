INSERT INTO procurement_setting (
  setting_key,
  setting_value,
  setting_group,
  setting_label,
  value_type,
  sort_no,
  remark,
  created_by,
  updated_by,
  is_deleted,
  version
)
VALUES (
  'disaster_weather_warning_resolve_push_enabled',
  'true',
  'WEATHER',
  '天气预警解除推送',
  'BOOLEAN',
  125,
  '开启后收到官方解除预警通知时，向值班群推送一次解除通知',
  0,
  0,
  0,
  0
)
ON DUPLICATE KEY UPDATE
  setting_group = VALUES(setting_group),
  setting_label = VALUES(setting_label),
  value_type = VALUES(value_type),
  sort_no = VALUES(sort_no),
  remark = VALUES(remark),
  updated_at = CURRENT_TIMESTAMP;
