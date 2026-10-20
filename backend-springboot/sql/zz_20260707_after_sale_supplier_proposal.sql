SET @schema_name = DATABASE();

SET @sql = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE procurement_after_sale ADD COLUMN supplier_proposal_type VARCHAR(32) NULL COMMENT ''供应商售后方案类型'' AFTER replacement_remark',
    'SELECT 1')
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'procurement_after_sale' AND COLUMN_NAME = 'supplier_proposal_type'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE procurement_after_sale ADD COLUMN supplier_proposal_remark VARCHAR(1000) NULL COMMENT ''供应商售后方案说明'' AFTER supplier_proposal_type',
    'SELECT 1')
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'procurement_after_sale' AND COLUMN_NAME = 'supplier_proposal_remark'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE procurement_after_sale ADD COLUMN supplier_replacement_item_code VARCHAR(64) NULL COMMENT ''供应商方案更换商品编码'' AFTER supplier_proposal_remark',
    'SELECT 1')
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'procurement_after_sale' AND COLUMN_NAME = 'supplier_replacement_item_code'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE procurement_after_sale ADD COLUMN supplier_replacement_item_name VARCHAR(255) NULL COMMENT ''供应商方案更换商品名称'' AFTER supplier_replacement_item_code',
    'SELECT 1')
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'procurement_after_sale' AND COLUMN_NAME = 'supplier_replacement_item_name'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE procurement_after_sale ADD COLUMN supplier_replacement_spec_model VARCHAR(255) NULL COMMENT ''供应商方案更换规格型号'' AFTER supplier_replacement_item_name',
    'SELECT 1')
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'procurement_after_sale' AND COLUMN_NAME = 'supplier_replacement_spec_model'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE procurement_after_sale ADD COLUMN supplier_replacement_unit VARCHAR(32) NULL COMMENT ''供应商方案更换单位'' AFTER supplier_replacement_spec_model',
    'SELECT 1')
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'procurement_after_sale' AND COLUMN_NAME = 'supplier_replacement_unit'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE procurement_after_sale ADD COLUMN supplier_replacement_unit_price DECIMAL(18,2) NULL COMMENT ''供应商方案更换单价'' AFTER supplier_replacement_unit',
    'SELECT 1')
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'procurement_after_sale' AND COLUMN_NAME = 'supplier_replacement_unit_price'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE procurement_after_sale ADD COLUMN supplier_replacement_line_amount DECIMAL(18,2) NULL COMMENT ''供应商方案更换金额'' AFTER supplier_replacement_unit_price',
    'SELECT 1')
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'procurement_after_sale' AND COLUMN_NAME = 'supplier_replacement_line_amount'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE procurement_after_sale ADD COLUMN supplier_proposed_at DATETIME NULL COMMENT ''供应商给出方案时间'' AFTER supplier_replacement_line_amount',
    'SELECT 1')
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'procurement_after_sale' AND COLUMN_NAME = 'supplier_proposed_at'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE procurement_after_sale ADD COLUMN requester_confirm_user_id BIGINT NULL COMMENT ''需求方方案确认人'' AFTER supplier_proposed_at',
    'SELECT 1')
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'procurement_after_sale' AND COLUMN_NAME = 'requester_confirm_user_id'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE procurement_after_sale ADD COLUMN requester_confirmed_at DATETIME NULL COMMENT ''需求方方案确认时间'' AFTER requester_confirm_user_id',
    'SELECT 1')
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'procurement_after_sale' AND COLUMN_NAME = 'requester_confirmed_at'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE procurement_after_sale ADD COLUMN requester_confirm_remark VARCHAR(1000) NULL COMMENT ''需求方方案确认意见'' AFTER requester_confirmed_at',
    'SELECT 1')
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'procurement_after_sale' AND COLUMN_NAME = 'requester_confirm_remark'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

ALTER TABLE procurement_after_sale
  MODIFY COLUMN status VARCHAR(32) NOT NULL DEFAULT 'PENDING'
  COMMENT '状态 PENDING待供应商方案/PROPOSAL_PENDING方案待确认/APPROVED已通过/REJECTED已驳回';
