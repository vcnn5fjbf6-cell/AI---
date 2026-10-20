-- 2026-07-09 灾害预警实时预警接口切换：
-- 1. 实时预警默认使用中央气象台当前预警列表。
-- 2. 解除卡片仅在接口返回官方解除通知时推送，不再按“本轮未命中重保规则”兜底推送。

UPDATE procurement_setting
SET setting_value = 'NMC',
    remark = 'NMC：中央气象台当前预警列表；AUTO：配置新版 Token 时走 /weatheralert/v1/current，否则兼容旧 /v7/warning/now；WEATHER_ALERT：强制新版；V7_WARNING：强制旧版',
    updated_at = NOW()
WHERE setting_key = 'disaster_weather_qweather_warning_api_mode';

UPDATE procurement_setting
SET setting_value = 'https://www.nmc.cn',
    remark = 'NMC 模式填写中央气象台域名；和风模式填写实时天气预警接口域名',
    updated_at = NOW()
WHERE setting_key = 'disaster_weather_qweather_warning_host'
  AND (setting_value IS NULL OR setting_value = '' OR setting_value LIKE '%qweather.com%');

UPDATE procurement_setting
SET remark = '开启后收到官方解除预警通知时，向值班群推送一次解除通知',
    updated_at = NOW()
WHERE setting_key = 'disaster_weather_warning_resolve_push_enabled';
