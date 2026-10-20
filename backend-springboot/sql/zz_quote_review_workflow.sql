SET @db_name = DATABASE();

SET @sql = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE purchase_request ADD COLUMN selected_quote_id BIGINT NULL COMMENT ''选定报价ID'' AFTER approved_at',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'purchase_request' AND COLUMN_NAME = 'selected_quote_id'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE purchase_request ADD COLUMN leader_confirm_status VARCHAR(32) NOT NULL DEFAULT ''PENDING'' COMMENT ''报价审核状态 PENDING/CONFIRMED'' AFTER selected_quote_id',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'purchase_request' AND COLUMN_NAME = 'leader_confirm_status'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE purchase_request ADD COLUMN leader_confirm_remark VARCHAR(1000) NULL COMMENT ''需求人替换物品审核或报价审核备注'' AFTER leader_confirm_status',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'purchase_request' AND COLUMN_NAME = 'leader_confirm_remark'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE purchase_request ADD COLUMN leader_confirmed_by BIGINT NULL COMMENT ''最近确认人ID'' AFTER leader_confirm_remark',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'purchase_request' AND COLUMN_NAME = 'leader_confirmed_by'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE purchase_request ADD COLUMN leader_confirmed_at DATETIME NULL COMMENT ''最近确认时间'' AFTER leader_confirmed_by',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'purchase_request' AND COLUMN_NAME = 'leader_confirmed_at'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE supplier_quote ADD COLUMN supplier_confirm_status VARCHAR(32) NOT NULL DEFAULT ''UNSELECTED'' COMMENT ''供应商中标确认状态 UNSELECTED/PENDING/CONFIRMED'' AFTER status',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'supplier_quote' AND COLUMN_NAME = 'supplier_confirm_status'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE supplier_quote ADD COLUMN supplier_confirm_remark VARCHAR(1000) NULL COMMENT ''供应商确认备注'' AFTER supplier_confirm_status',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'supplier_quote' AND COLUMN_NAME = 'supplier_confirm_remark'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE supplier_quote ADD COLUMN supplier_confirmed_by BIGINT NULL COMMENT ''供应商确认人ID'' AFTER supplier_confirm_remark',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'supplier_quote' AND COLUMN_NAME = 'supplier_confirmed_by'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE supplier_quote ADD COLUMN supplier_confirmed_at DATETIME NULL COMMENT ''供应商确认时间'' AFTER supplier_confirmed_by',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'supplier_quote' AND COLUMN_NAME = 'supplier_confirmed_at'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
