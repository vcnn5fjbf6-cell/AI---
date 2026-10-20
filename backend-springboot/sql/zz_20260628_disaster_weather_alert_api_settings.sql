INSERT INTO procurement_setting (setting_key, setting_value, setting_group, setting_label, value_type, sort_no, remark, created_by, updated_by, is_deleted, version)
VALUES
  ('disaster_weather_qweather_warning_api_mode', 'AUTO', 'WEATHER', '预警 API 模式', 'SELECT', 31, 'AUTO：配置新版 Token 时走 /weatheralert/v1/current，否则兼容旧 /v7/warning/now；WEATHER_ALERT：强制新版；V7_WARNING：强制旧版', 0, 0, 0, 0),
  ('disaster_weather_qweather_warning_host', 'https://devapi.qweather.com', 'WEATHER', '预警 API 地址', 'TEXT', 32, '实时天气预警接口域名，用于官方 /weatheralert/v1/current/{latitude}/{longitude}', 0, 0, 0, 0),
  ('disaster_weather_qweather_warning_token', '', 'WEATHER', '预警 API Token', 'PASSWORD', 33, '新版实时天气预警接口的 Bearer Token；旧版 /v7/warning/now 不使用该值', 0, 0, 0, 0)
ON DUPLICATE KEY UPDATE
  setting_group = VALUES(setting_group),
  setting_label = VALUES(setting_label),
  value_type = VALUES(value_type),
  sort_no = VALUES(sort_no),
  remark = VALUES(remark),
  updated_at = CURRENT_TIMESTAMP;

UPDATE procurement_setting
SET setting_label = '普通天气 API 地址',
    remark = '普通天气接口域名，用于 /v7/weather/now 和 /v7/weather/7d；不要填写接口路径或参数',
    updated_at = CURRENT_TIMESTAMP
WHERE setting_key = 'disaster_weather_qweather_host'
  AND is_deleted = 0;
