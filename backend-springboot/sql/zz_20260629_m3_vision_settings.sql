SET NAMES utf8mb4;

INSERT INTO procurement_setting
  (setting_key, setting_value, setting_group, setting_label, value_type, sort_no, remark, created_by, updated_by, is_deleted, version)
SELECT 'm3_vision_enabled', 'false', 'API', '启用 M3 视觉识别', 'BOOLEAN', 110,
       '开启后，飞书图片采购需求优先调用 M3 视觉模型；识别失败时回退 Dify',
       0, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM procurement_setting WHERE setting_key = 'm3_vision_enabled' AND is_deleted = 0);

INSERT INTO procurement_setting
  (setting_key, setting_value, setting_group, setting_label, value_type, sort_no, remark, created_by, updated_by, is_deleted, version)
SELECT 'm3_base_url', '', 'API', 'M3 Base URL', 'TEXT', 120,
       'M3 OpenAI-compatible Base URL，例如 http://127.0.0.1:8082/v1',
       0, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM procurement_setting WHERE setting_key = 'm3_base_url' AND is_deleted = 0);

INSERT INTO procurement_setting
  (setting_key, setting_value, setting_group, setting_label, value_type, sort_no, remark, created_by, updated_by, is_deleted, version)
SELECT 'm3_api_key', '', 'API', 'M3 API Key', 'PASSWORD', 130,
       'M3 OpenAI-compatible API Key，可按本地服务要求填写',
       0, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM procurement_setting WHERE setting_key = 'm3_api_key' AND is_deleted = 0);

INSERT INTO procurement_setting
  (setting_key, setting_value, setting_group, setting_label, value_type, sort_no, remark, created_by, updated_by, is_deleted, version)
SELECT 'm3_vision_model', 'm3-vision', 'API', 'M3 视觉模型', 'TEXT', 140,
       '用于图片商品识别的模型名，例如 WanWu/MiniMax-M3、m3-vision 或 vision-pro',
       0, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM procurement_setting WHERE setting_key = 'm3_vision_model' AND is_deleted = 0);

INSERT INTO procurement_setting
  (setting_key, setting_value, setting_group, setting_label, value_type, sort_no, remark, created_by, updated_by, is_deleted, version)
SELECT 'm3_endpoint', '/chat/completions', 'API', 'M3 Chat Endpoint', 'TEXT', 150,
       '默认 /chat/completions；如果 Base URL 已包含完整路径，可保持默认',
       0, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM procurement_setting WHERE setting_key = 'm3_endpoint' AND is_deleted = 0);

INSERT INTO procurement_setting
  (setting_key, setting_value, setting_group, setting_label, value_type, sort_no, remark, created_by, updated_by, is_deleted, version)
SELECT 'procurement_ai_image_public_base_url', '', 'API', 'AI 图片访问基址', 'TEXT', 160,
       '飞书图片中转后给 Dify 或仅支持远程 URL 的视觉模型访问；WanWu/MiniMax-M3 会优先直传图片内容，可留空',
       0, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM procurement_setting WHERE setting_key = 'procurement_ai_image_public_base_url' AND is_deleted = 0);
