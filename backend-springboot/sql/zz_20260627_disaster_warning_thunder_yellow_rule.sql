SET NAMES utf8mb4;

ALTER TABLE procurement_setting
  MODIFY setting_value TEXT NULL COMMENT 'setting value';

UPDATE procurement_setting
SET setting_value = CAST(JSON_REMOVE(
    CAST(setting_value AS JSON),
    CONCAT('$[', JSON_LENGTH(CAST(setting_value AS JSON)) - 1, ']')
) AS CHAR CHARACTER SET utf8mb4),
updated_at = NOW()
WHERE setting_key = 'disaster_weather_warning_rules_json'
  AND is_deleted = 0
  AND JSON_VALID(setting_value)
  AND JSON_LENGTH(CAST(setting_value AS JSON)) > 1
  AND JSON_UNQUOTE(JSON_EXTRACT(
    CAST(setting_value AS JSON),
    CONCAT('$[', JSON_LENGTH(CAST(setting_value AS JSON)) - 1, '].type')
  )) = _utf8mb4 0xE99BB7E794B5
  AND JSON_UNQUOTE(JSON_EXTRACT(
    CAST(setting_value AS JSON),
    CONCAT('$[', JSON_LENGTH(CAST(setting_value AS JSON)) - 1, '].color')
  )) = _utf8mb4 0xE9BB84E889B2
  AND JSON_UNQUOTE(JSON_EXTRACT(
    CAST(setting_value AS JSON),
    CONCAT('$[', JSON_LENGTH(CAST(setting_value AS JSON)) - 2, '].type')
  )) = _utf8mb4 0xE99BB7E794B5
  AND JSON_UNQUOTE(JSON_EXTRACT(
    CAST(setting_value AS JSON),
    CONCAT('$[', JSON_LENGTH(CAST(setting_value AS JSON)) - 2, '].color')
  )) = _utf8mb4 0xE9BB84E889B2;

UPDATE procurement_setting
SET setting_value = CAST(JSON_ARRAY_APPEND(
    CAST(setting_value AS JSON),
    '$',
    CAST('{"type":"雷电","color":"黄色","guard":"三级戒备","card":"yellow","enabled":true}' AS JSON)
) AS CHAR CHARACTER SET utf8mb4),
updated_at = NOW()
WHERE setting_key = 'disaster_weather_warning_rules_json'
  AND is_deleted = 0
  AND JSON_VALID(setting_value)
  AND NOT EXISTS (
    SELECT 1
    FROM JSON_TABLE(CAST(setting_value AS JSON), '$[*]' COLUMNS(
      rule_type VARCHAR(64) PATH '$.type',
      rule_color VARCHAR(64) PATH '$.color'
    )) AS existing_rule
    WHERE existing_rule.rule_type = _utf8mb4 0xE99BB7E794B5
      AND existing_rule.rule_color = _utf8mb4 0xE9BB84E889B2
  );
