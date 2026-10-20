SET @db_name = DATABASE();

SET @sql = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE purchase_request ADD COLUMN award_strategy VARCHAR(32) NOT NULL DEFAULT ''TOTAL_LOWEST'' COMMENT ''Award strategy: TOTAL_LOWEST/ITEM_LOWEST'' AFTER req_reason',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'purchase_request' AND COLUMN_NAME = 'award_strategy'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE supplier_quote_item ADD COLUMN awarded TINYINT NOT NULL DEFAULT 0 COMMENT ''Awarded item flag: 0/1'' AFTER quote_remark',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'supplier_quote_item' AND COLUMN_NAME = 'awarded'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

INSERT INTO procurement_setting (
  setting_key,
  setting_value,
  setting_group,
  setting_label,
  value_type,
  sort_no,
  remark
)
SELECT
  'monthly_aggregate_day',
  '25',
  'RULE',
  '集采自动发起日',
  'NUMBER',
  60,
  '每月几号自动汇总集采需求并生成采购申请'
WHERE NOT EXISTS (
  SELECT 1 FROM procurement_setting WHERE setting_key = 'monthly_aggregate_day'
);

INSERT INTO procurement_setting (
  setting_key,
  setting_value,
  setting_group,
  setting_label,
  value_type,
  sort_no,
  remark
)
SELECT
  'default_award_strategy',
  'TOTAL_LOWEST',
  'RULE',
  '默认中标策略',
  'TEXT',
  70,
  '采购需求默认中标策略：TOTAL_LOWEST 总价最低，ITEM_LOWEST 单项最低'
WHERE NOT EXISTS (
  SELECT 1 FROM procurement_setting WHERE setting_key = 'default_award_strategy'
);
