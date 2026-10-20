SET NAMES utf8mb4;

ALTER TABLE procurement_setting
  MODIFY setting_value TEXT NULL COMMENT 'setting value';

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
VALUES
  (
    'disaster_weather_new_delivery_promotion_enabled',
    'false',
    'WEATHER',
    '新交付楼栋提级',
    'BOOLEAN',
    126,
    '开启后按重保制度对未真实经历过极端天气的新交付楼栋执行预警提级',
    0,
    0,
    0,
    0
  ),
  (
    'disaster_weather_rainy_season_enabled',
    'true',
    'WEATHER',
    '雨季专项提醒',
    'BOOLEAN',
    127,
    '开启后在配置月份内追加雨季专项防水、排水、应急物资检查提醒',
    0,
    0,
    0,
    0
  ),
  (
    'disaster_weather_rainy_season_months',
    '6,7,8,9',
    'WEATHER',
    '雨季月份',
    'TEXT',
    128,
    '逗号分隔月份；各机房应按当地历史情况并经区域负责人确认后维护',
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

DROP TEMPORARY TABLE IF EXISTS tmp_disaster_weather_guard_policy_v33;

CREATE TEMPORARY TABLE tmp_disaster_weather_guard_policy_v33 (
  rule_type VARCHAR(64) NOT NULL,
  rule_color VARCHAR(64) NOT NULL,
  rule_guard VARCHAR(64) NOT NULL,
  rule_card VARCHAR(32) NOT NULL,
  rule_enabled TINYINT NOT NULL DEFAULT 1,
  PRIMARY KEY (rule_type, rule_color)
) ENGINE=Memory DEFAULT CHARSET=utf8mb4;

INSERT INTO tmp_disaster_weather_guard_policy_v33 (rule_type, rule_color, rule_guard, rule_card, rule_enabled)
VALUES
  ('暴雨', '黄色', '三级戒备', 'yellow', 1),
  ('暴雪', '黄色', '三级戒备', 'yellow', 1),
  ('台风', '黄色', '三级戒备', 'yellow', 1),
  ('洪水', '黄色', '三级戒备', 'yellow', 1),
  ('雷电', '橙色', '三级戒备', 'orange', 1),
  ('冰雹', '橙色', '三级戒备', 'orange', 1),
  ('寒潮', '橙色', '三级戒备', 'orange', 1),
  ('霜冻', '橙色', '三级戒备', 'orange', 1),
  ('高温', '橙色', '三级戒备', 'orange', 1),
  ('雷电', '黄色', '无匹配标签', 'yellow', 1),
  ('冰雹', '黄色', '无匹配标签', 'yellow', 1),
  ('寒潮', '黄色', '无匹配标签', 'yellow', 1),
  ('霜冻', '黄色', '无匹配标签', 'yellow', 1),
  ('高温', '黄色', '无匹配标签', 'yellow', 1),
  ('雷电', '蓝色', '无匹配标签', 'blue', 1),
  ('冰雹', '蓝色', '无匹配标签', 'blue', 1),
  ('寒潮', '蓝色', '无匹配标签', 'blue', 1),
  ('霜冻', '蓝色', '无匹配标签', 'blue', 1),
  ('高温', '蓝色', '无匹配标签', 'blue', 1);

UPDATE procurement_setting AS s
JOIN JSON_TABLE(CAST(s.setting_value AS JSON), '$[*]' COLUMNS(
  rule_ordinal FOR ORDINALITY,
  rule_type VARCHAR(64) PATH '$.type',
  rule_color VARCHAR(64) PATH '$.color'
)) AS existing_rule
JOIN tmp_disaster_weather_guard_policy_v33 AS policy_rule
  ON policy_rule.rule_type = existing_rule.rule_type
  AND policy_rule.rule_color = existing_rule.rule_color
SET s.setting_value = CAST(JSON_SET(
    CAST(s.setting_value AS JSON),
    CONCAT('$[', existing_rule.rule_ordinal - 1, '].guard'),
    policy_rule.rule_guard,
    CONCAT('$[', existing_rule.rule_ordinal - 1, '].card'),
    policy_rule.rule_card,
    CONCAT('$[', existing_rule.rule_ordinal - 1, '].enabled'),
    TRUE
  ) AS CHAR CHARACTER SET utf8mb4),
  s.updated_at = NOW()
WHERE s.setting_key = 'disaster_weather_warning_rules_json'
  AND s.is_deleted = 0
  AND JSON_VALID(s.setting_value);

UPDATE procurement_setting AS s
JOIN tmp_disaster_weather_guard_policy_v33 AS policy_rule
SET s.setting_value = CAST(JSON_ARRAY_APPEND(
    CAST(s.setting_value AS JSON),
    '$',
    JSON_OBJECT(
      'type', policy_rule.rule_type,
      'color', policy_rule.rule_color,
      'guard', policy_rule.rule_guard,
      'card', policy_rule.rule_card,
      'enabled', TRUE
    )
  ) AS CHAR CHARACTER SET utf8mb4),
  s.updated_at = NOW()
WHERE s.setting_key = 'disaster_weather_warning_rules_json'
  AND s.is_deleted = 0
  AND JSON_VALID(s.setting_value)
  AND NOT EXISTS (
    SELECT 1
    FROM JSON_TABLE(CAST(s.setting_value AS JSON), '$[*]' COLUMNS(
      rule_type VARCHAR(64) PATH '$.type',
      rule_color VARCHAR(64) PATH '$.color'
    )) AS existing_rule
    WHERE existing_rule.rule_type = policy_rule.rule_type
      AND existing_rule.rule_color = policy_rule.rule_color
  );

DROP TEMPORARY TABLE IF EXISTS tmp_disaster_weather_guard_policy_v33;
