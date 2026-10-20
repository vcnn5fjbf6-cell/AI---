SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS procurement_asset_catalog_item (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  category_code VARCHAR(32) NOT NULL COMMENT '资产大类编码',
  category_name VARCHAR(255) NOT NULL COMMENT '资产大类名称',
  asset_name_code VARCHAR(32) NOT NULL COMMENT '资产名称编码',
  asset_name VARCHAR(255) NOT NULL COMMENT '资产名称',
  depreciation_years VARCHAR(64) NULL COMMENT '折旧年限',
  depreciation_months VARCHAR(64) NULL COMMENT '折旧月数',
  unit VARCHAR(64) NULL COMMENT '计量单位',
  inventory_method VARCHAR(128) NULL COMMENT '盘点方式',
  physical_number VARCHAR(64) NULL COMMENT '实物编号要求',
  physical_label VARCHAR(128) NULL COMMENT '实物标签要求',
  remark VARCHAR(1000) NULL COMMENT '备注',
  change_mark VARCHAR(255) NULL COMMENT '目录变更标识',
  enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用 1启用 0停用',
  source_version VARCHAR(128) NULL COMMENT '目录来源版本',
  source_row INT NULL COMMENT '来源文件行号',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  created_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人',
  updated_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人',
  is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记 0未删 1已删',
  version INT NOT NULL DEFAULT 0 COMMENT '版本号',
  UNIQUE KEY uk_asset_catalog_code (category_code, asset_name_code),
  KEY idx_asset_catalog_enabled (enabled, category_code, asset_name_code),
  KEY idx_asset_catalog_name (asset_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采购资产目录';

DROP PROCEDURE IF EXISTS add_supplier_quote_asset_column;
DELIMITER $$
CREATE PROCEDURE add_supplier_quote_asset_column(
  IN column_name_value VARCHAR(64),
  IN column_definition_value TEXT
)
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'supplier_quote_item'
      AND COLUMN_NAME = column_name_value
  ) THEN
    SET @column_sql = CONCAT('ALTER TABLE supplier_quote_item ADD COLUMN ', column_definition_value);
    PREPARE column_stmt FROM @column_sql;
    EXECUTE column_stmt;
    DEALLOCATE PREPARE column_stmt;
  END IF;
END$$
DELIMITER ;

CALL add_supplier_quote_asset_column('original_condition',
  'original_condition VARCHAR(16) NULL COMMENT ''原商品成色 NEW/USED'' AFTER lead_time_days');
CALL add_supplier_quote_asset_column('original_second_hand_remark',
  'original_second_hand_remark VARCHAR(500) NULL COMMENT ''原商品二手来源和状态说明'' AFTER original_condition');
CALL add_supplier_quote_asset_column('replacement_condition',
  'replacement_condition VARCHAR(16) NULL COMMENT ''替换商品成色 NEW/USED'' AFTER replacement_reason');
CALL add_supplier_quote_asset_column('replacement_second_hand_remark',
  'replacement_second_hand_remark VARCHAR(500) NULL COMMENT ''替换商品二手来源和状态说明'' AFTER replacement_condition');
CALL add_supplier_quote_asset_column('asset_review_status',
  'asset_review_status VARCHAR(32) NULL COMMENT ''资产复核状态 PENDING/COMPLETED/PARTIAL/FAILED'' AFTER replacement_second_hand_remark');
CALL add_supplier_quote_asset_column('asset_review_result_json',
  'asset_review_result_json LONGTEXT NULL COMMENT ''M3资产复核原始结果'' AFTER asset_review_status');
CALL add_supplier_quote_asset_column('asset_review_message',
  'asset_review_message VARCHAR(1000) NULL COMMENT ''资产复核摘要'' AFTER asset_review_result_json');
CALL add_supplier_quote_asset_column('original_asset_category_code',
  'original_asset_category_code VARCHAR(32) NULL COMMENT ''原商品资产大类代码'' AFTER asset_review_message');
CALL add_supplier_quote_asset_column('original_asset_name_code',
  'original_asset_name_code VARCHAR(32) NULL COMMENT ''原商品资产名称代码'' AFTER original_asset_category_code');
CALL add_supplier_quote_asset_column('original_asset_catalog_name',
  'original_asset_catalog_name VARCHAR(255) NULL COMMENT ''原商品命中资产目录名称'' AFTER original_asset_name_code');
CALL add_supplier_quote_asset_column('original_asset_classification',
  'original_asset_classification VARCHAR(32) NULL COMMENT ''原商品资产分类'' AFTER original_asset_catalog_name');
CALL add_supplier_quote_asset_column('original_special_approval_required',
  'original_special_approval_required TINYINT NOT NULL DEFAULT 0 COMMENT ''原商品是否需要二手高价特殊审批'' AFTER original_asset_classification');
CALL add_supplier_quote_asset_column('replacement_asset_category_code',
  'replacement_asset_category_code VARCHAR(32) NULL COMMENT ''替换商品资产大类代码'' AFTER original_special_approval_required');
CALL add_supplier_quote_asset_column('replacement_asset_name_code',
  'replacement_asset_name_code VARCHAR(32) NULL COMMENT ''替换商品资产名称代码'' AFTER replacement_asset_category_code');
CALL add_supplier_quote_asset_column('replacement_asset_catalog_name',
  'replacement_asset_catalog_name VARCHAR(255) NULL COMMENT ''替换商品命中资产目录名称'' AFTER replacement_asset_name_code');
CALL add_supplier_quote_asset_column('replacement_asset_classification',
  'replacement_asset_classification VARCHAR(32) NULL COMMENT ''替换商品资产分类'' AFTER replacement_asset_catalog_name');
CALL add_supplier_quote_asset_column('replacement_special_approval_required',
  'replacement_special_approval_required TINYINT NOT NULL DEFAULT 0 COMMENT ''替换商品是否需要二手高价特殊审批'' AFTER replacement_asset_classification');
CALL add_supplier_quote_asset_column('condition_mismatch',
  'condition_mismatch TINYINT NOT NULL DEFAULT 0 COMMENT ''供应商声明成色与M3复核是否冲突'' AFTER replacement_special_approval_required');
CALL add_supplier_quote_asset_column('asset_reviewed_at',
  'asset_reviewed_at DATETIME NULL COMMENT ''M3资产复核完成时间'' AFTER condition_mismatch');

DROP PROCEDURE IF EXISTS add_supplier_quote_asset_column;

SET @asset_review_index_sql = IF(
  EXISTS (
    SELECT 1
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'supplier_quote_item'
      AND INDEX_NAME = 'idx_quote_item_asset_review'
  ),
  'SELECT 1',
  'ALTER TABLE supplier_quote_item ADD KEY idx_quote_item_asset_review (asset_review_status, status, id)'
);
PREPARE asset_review_index_stmt FROM @asset_review_index_sql;
EXECUTE asset_review_index_stmt;
DEALLOCATE PREPARE asset_review_index_stmt;
