SET NAMES utf8mb4;

SET @personal_labor_target_due_date = DATE('2027-04-01');
SET @personal_labor_baseline_already_applied = (
  SELECT COUNT(1)
  FROM procurement_setting
  WHERE setting_key = 'personal_labor_next_due_20270401_applied_at'
    AND is_deleted = 0
    AND setting_value IS NOT NULL
    AND TRIM(setting_value) <> ''
);

INSERT INTO procurement_setting
  (setting_key, setting_value, setting_group, setting_label, value_type, sort_no, remark, created_by, updated_by, is_deleted, version)
VALUES
  ('personal_labor_baseline_cutoff_at', DATE_FORMAT(NOW(), '%Y-%m-%d %H:%i:%s'), 'LABOR', '个人劳保在库人员基线时间', 'TEXT', 9, '仅用于一次性锁定本次个人劳保下次领用日期调整的在库人员范围；该时间之后新入职人员不受限制', 0, 0, 0, 0)
ON DUPLICATE KEY UPDATE
  setting_value = CASE
    WHEN setting_value IS NULL OR TRIM(setting_value) = '' THEN VALUES(setting_value)
    ELSE setting_value
  END,
  setting_group = VALUES(setting_group),
  setting_label = VALUES(setting_label),
  value_type = VALUES(value_type),
  sort_no = VALUES(sort_no),
  remark = VALUES(remark),
  is_deleted = 0,
  updated_at = CURRENT_TIMESTAMP;

DROP TEMPORARY TABLE IF EXISTS tmp_personal_labor_rules_20270401;
CREATE TEMPORARY TABLE tmp_personal_labor_rules_20270401 (
  item_code VARCHAR(64) NOT NULL,
  item_name VARCHAR(128) NOT NULL,
  unit VARCHAR(32) NOT NULL,
  quantity_limit DECIMAL(18,4) NOT NULL,
  cycle_days INT NOT NULL,
  PRIMARY KEY (item_code)
) ENGINE=MEMORY DEFAULT CHARSET=utf8mb4;

INSERT INTO tmp_personal_labor_rules_20270401 (item_code, item_name, unit, quantity_limit, cycle_days)
SELECT *
FROM (
  SELECT 'LABOR_SHOES' AS item_code, '劳保鞋' AS item_name, '双' AS unit, 1 AS quantity_limit, 365 AS cycle_days UNION ALL
  SELECT 'LABOR_GLOVES', '劳保手套', '副', 2, 365 UNION ALL
  SELECT 'INSULATED_GLOVES', '绝缘手套', '副', 1, 180 UNION ALL
  SELECT 'FLASHLIGHT', '手电筒', '个', 1, 365 UNION ALL
  SELECT 'TEST_PEN', '电笔', '支', 1, 365 UNION ALL
  SELECT 'ANTI_STATIC_GLOVES', '防静电手套', '副', 2, 365 UNION ALL
  SELECT 'ELECTRICAL_INSULATING_SHOES', '电绝缘鞋', '双', 1, 365
) defaults_rule
WHERE NOT EXISTS (
  SELECT 1
  FROM procurement_setting s
  WHERE s.setting_key = 'personal_labor_item_rules_json'
    AND s.is_deleted = 0
    AND JSON_VALID(s.setting_value)
);

INSERT INTO tmp_personal_labor_rules_20270401 (item_code, item_name, unit, quantity_limit, cycle_days)
SELECT
  UPPER(TRIM(rule_json.item_code)) AS item_code,
  TRIM(rule_json.item_name) AS item_name,
  COALESCE(NULLIF(TRIM(rule_json.unit), ''), '件') AS unit,
  GREATEST(COALESCE(rule_json.quantity_limit, 1), 1) AS quantity_limit,
  GREATEST(COALESCE(rule_json.cycle_days, 365), 1) AS cycle_days
FROM (
  SELECT CAST(s.setting_value AS JSON) AS setting_json
  FROM procurement_setting s
  WHERE s.setting_key = 'personal_labor_item_rules_json'
    AND s.is_deleted = 0
    AND JSON_VALID(s.setting_value)
  LIMIT 1
) valid_setting
JOIN JSON_TABLE(valid_setting.setting_json, '$[*]' COLUMNS(
  item_code VARCHAR(64) PATH '$.itemCode',
  item_name VARCHAR(128) PATH '$.itemName',
  unit VARCHAR(32) PATH '$.unit',
  quantity_limit DECIMAL(18,4) PATH '$.quantityLimit' DEFAULT '1' ON EMPTY DEFAULT '1' ON ERROR,
  cycle_days INT PATH '$.cycleDays' DEFAULT '365' ON EMPTY DEFAULT '365' ON ERROR
)) AS rule_json
WHERE TRIM(COALESCE(rule_json.item_code, '')) <> ''
  AND TRIM(COALESCE(rule_json.item_name, '')) <> ''
ON DUPLICATE KEY UPDATE
  item_name = VALUES(item_name),
  unit = VALUES(unit),
  quantity_limit = VALUES(quantity_limit),
  cycle_days = VALUES(cycle_days);

UPDATE personal_labor_item_record record
JOIN sys_user u
  ON u.id = record.user_id
  AND u.status = 1
  AND u.is_deleted = 0
  AND UPPER(COALESCE(u.user_type, '')) <> 'SUPPLIER'
JOIN procurement_setting cutoff
  ON cutoff.setting_key = 'personal_labor_baseline_cutoff_at'
  AND cutoff.is_deleted = 0
  AND u.created_at <= STR_TO_DATE(cutoff.setting_value, '%Y-%m-%d %H:%i:%s')
JOIN tmp_personal_labor_rules_20270401 rule_row
  ON rule_row.item_code = record.item_code
LEFT JOIN personal_labor_item_record pending
  ON pending.user_id = record.user_id
  AND pending.item_code = record.item_code
  AND pending.is_deleted = 0
  AND UPPER(COALESCE(pending.status, '')) = 'REQUESTED'
SET record.status = 'RECEIVED',
    record.last_received_date = COALESCE(record.last_received_date, CURDATE()),
    record.next_due_date = @personal_labor_target_due_date,
    record.remark = CASE
      WHEN record.remark IS NULL OR TRIM(record.remark) = '' THEN '系统统一调整：下次劳保领用日期调整为 2027-04-01'
      WHEN record.remark LIKE '%下次劳保领用日期调整为 2027-04-01%' THEN record.remark
      ELSE CONCAT(record.remark, '；系统统一调整：下次劳保领用日期调整为 2027-04-01')
    END,
    record.updated_by = 0,
    record.updated_at = CURRENT_TIMESTAMP
WHERE record.is_deleted = 0
  AND @personal_labor_baseline_already_applied = 0
  AND UPPER(COALESCE(record.status, '')) <> 'REQUESTED'
  AND pending.id IS NULL;

INSERT INTO personal_labor_item_record
  (user_id, username, real_name, dept_code, dept_name, item_code, item_name, spec_model, unit, quantity,
   last_received_date, next_due_date, status, purchase_request_id, purchase_request_no, purchase_request_item_id,
   emergency_flag, remark, created_at, updated_at, created_by, updated_by, is_deleted, version)
SELECT
  u.id,
  u.username,
  u.real_name,
  u.dept_code,
  u.dept_name,
  rule_row.item_code,
  rule_row.item_name,
  NULL,
  rule_row.unit,
  rule_row.quantity_limit,
  CURDATE(),
  @personal_labor_target_due_date,
  'RECEIVED',
  NULL,
  NULL,
  NULL,
  0,
  '系统基线记录：仅用于限制当前在库人员下次劳保领用日期为 2027-04-01，不代表实际领取',
  CURRENT_TIMESTAMP,
  CURRENT_TIMESTAMP,
  0,
  0,
  0,
  0
FROM sys_user u
JOIN procurement_setting cutoff
  ON cutoff.setting_key = 'personal_labor_baseline_cutoff_at'
  AND cutoff.is_deleted = 0
  AND u.created_at <= STR_TO_DATE(cutoff.setting_value, '%Y-%m-%d %H:%i:%s')
CROSS JOIN tmp_personal_labor_rules_20270401 rule_row
WHERE u.status = 1
  AND @personal_labor_baseline_already_applied = 0
  AND u.is_deleted = 0
  AND UPPER(COALESCE(u.user_type, '')) <> 'SUPPLIER'
  AND NOT EXISTS (
    SELECT 1
    FROM personal_labor_item_record existing_record
    WHERE existing_record.user_id = u.id
      AND existing_record.item_code = rule_row.item_code
      AND existing_record.is_deleted = 0
  );

INSERT INTO procurement_setting
  (setting_key, setting_value, setting_group, setting_label, value_type, sort_no, remark, created_by, updated_by, is_deleted, version)
SELECT
  'personal_labor_next_due_20270401_applied_at',
  DATE_FORMAT(NOW(), '%Y-%m-%d %H:%i:%s'),
  'LABOR',
  '个人劳保2027-04-01基线已执行时间',
  'TEXT',
  9,
  '防止生产幂等脚本在后续部署中重复把现有人员劳保下次日期写回 2027-04-01',
  0,
  0,
  0,
  0
WHERE @personal_labor_baseline_already_applied = 0
ON DUPLICATE KEY UPDATE
  setting_value = CASE
    WHEN setting_value IS NULL OR TRIM(setting_value) = '' THEN VALUES(setting_value)
    ELSE setting_value
  END,
  setting_group = VALUES(setting_group),
  setting_label = VALUES(setting_label),
  value_type = VALUES(value_type),
  sort_no = VALUES(sort_no),
  remark = VALUES(remark),
  is_deleted = 0,
  updated_at = CURRENT_TIMESTAMP;

DROP TEMPORARY TABLE IF EXISTS tmp_personal_labor_rules_20270401;
