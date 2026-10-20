SET NAMES utf8mb4;

SET @column_exists := (
  SELECT COUNT(1)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'supplier_quote'
    AND COLUMN_NAME = 'quote_source'
);
SET @sql := IF(
  @column_exists = 0,
  'ALTER TABLE supplier_quote ADD COLUMN quote_source VARCHAR(32) NOT NULL DEFAULT ''NORMAL'' COMMENT ''报价来源：NORMAL 正式供应商，TEMP_RECOMMENDED 临时推荐供应商'' AFTER supplier_user_id',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
  SELECT COUNT(1)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'supplier_quote'
    AND COLUMN_NAME = 'recommendation_id'
);
SET @sql := IF(
  @column_exists = 0,
  'ALTER TABLE supplier_quote ADD COLUMN recommendation_id BIGINT NULL COMMENT ''临时推荐供应商审批记录ID'' AFTER quote_source',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists := (
  SELECT COUNT(1)
  FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'supplier_quote'
    AND INDEX_NAME = 'idx_quote_source_recommendation'
);
SET @sql := IF(
  @idx_exists = 0,
  'ALTER TABLE supplier_quote ADD KEY idx_quote_source_recommendation (quote_source, recommendation_id)',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS temporary_supplier_recommendation (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  recommendation_no VARCHAR(64) NOT NULL COMMENT '推荐编号',
  purchase_request_id BIGINT NOT NULL COMMENT '采购申请ID',
  request_no VARCHAR(64) NOT NULL COMMENT '采购申请单号',
  request_title VARCHAR(255) NULL COMMENT '采购申请标题',
  region_id BIGINT NULL COMMENT '区域ID',
  dept_code VARCHAR(128) NULL COMMENT '部门编码',
  dept_name VARCHAR(255) NULL COMMENT '部门名称',
  supplier_id BIGINT NOT NULL COMMENT '临时供应商ID',
  supplier_code VARCHAR(64) NOT NULL COMMENT '临时供应商编码',
  supplier_name VARCHAR(255) NOT NULL COMMENT '临时供应商名称',
  contact_name VARCHAR(64) NOT NULL COMMENT '联系人',
  contact_phone VARCHAR(32) NOT NULL COMMENT '联系电话',
  contact_email VARCHAR(128) NULL COMMENT '联系邮箱',
  recommended_by BIGINT NOT NULL COMMENT '推荐人ID',
  recommended_by_name VARCHAR(64) NULL COMMENT '推荐人名称',
  recommend_reason VARCHAR(1000) NOT NULL COMMENT '推荐原因',
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING_ROOM_MANAGER' COMMENT '状态：PENDING_ROOM_MANAGER/PENDING_REGION_MANAGER/APPROVED/REJECTED/CANCELLED',
  current_role_code VARCHAR(64) NULL COMMENT '当前审批角色',
  room_manager_user_id BIGINT NULL COMMENT '机房经理审批人ID',
  room_manager_name VARCHAR(64) NULL COMMENT '机房经理名称',
  room_manager_comment VARCHAR(500) NULL COMMENT '机房经理意见',
  room_manager_reviewed_at DATETIME NULL COMMENT '机房经理审批时间',
  region_manager_user_id BIGINT NULL COMMENT '区域经理审批人ID',
  region_manager_name VARCHAR(64) NULL COMMENT '区域经理名称',
  region_manager_comment VARCHAR(500) NULL COMMENT '区域经理意见',
  region_manager_reviewed_at DATETIME NULL COMMENT '区域经理审批时间',
  rejected_by BIGINT NULL COMMENT '驳回人ID',
  rejected_by_name VARCHAR(64) NULL COMMENT '驳回人名称',
  reject_reason VARCHAR(500) NULL COMMENT '驳回原因',
  rejected_at DATETIME NULL COMMENT '驳回时间',
  remark VARCHAR(1000) NULL COMMENT '备注',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  created_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人',
  updated_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人',
  is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记：0未删 1已删',
  version INT NOT NULL DEFAULT 0 COMMENT '版本号',
  UNIQUE KEY uk_temp_supplier_recommendation_no (recommendation_no),
  KEY idx_temp_supplier_request (purchase_request_id),
  KEY idx_temp_supplier_supplier (supplier_id),
  KEY idx_temp_supplier_status (status),
  KEY idx_temp_supplier_region (region_id),
  KEY idx_temp_supplier_deleted (is_deleted),
  CONSTRAINT fk_temp_supplier_recommendation_request FOREIGN KEY (purchase_request_id) REFERENCES purchase_request(id),
  CONSTRAINT fk_temp_supplier_recommendation_supplier FOREIGN KEY (supplier_id) REFERENCES supplier(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='临时推荐供应商审批表';

CREATE TABLE IF NOT EXISTS temporary_supplier_recommendation_item (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  recommendation_id BIGINT NOT NULL COMMENT '推荐供应商审批ID',
  purchase_request_id BIGINT NOT NULL COMMENT '采购申请ID',
  purchase_request_item_id BIGINT NOT NULL COMMENT '采购明细ID',
  line_no INT NOT NULL COMMENT '行号',
  item_code VARCHAR(64) NULL COMMENT '物料编码',
  req_type VARCHAR(64) NULL COMMENT '采购类型',
  item_name VARCHAR(255) NOT NULL COMMENT '商品名称',
  spec_model VARCHAR(255) NULL COMMENT '规格型号',
  unit VARCHAR(32) NULL COMMENT '单位',
  qty DECIMAL(18,2) NULL COMMENT '数量',
  purpose VARCHAR(500) NULL COMMENT '采购原因/用途',
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/INACTIVE',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  created_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人',
  updated_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人',
  is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记：0未删 1已删',
  version INT NOT NULL DEFAULT 0 COMMENT '版本号',
  UNIQUE KEY uk_temp_supplier_item_scope (recommendation_id, purchase_request_item_id, is_deleted),
  KEY idx_temp_supplier_item_request (purchase_request_id),
  KEY idx_temp_supplier_item_request_item (purchase_request_item_id),
  KEY idx_temp_supplier_item_deleted (is_deleted),
  CONSTRAINT fk_temp_supplier_item_recommendation FOREIGN KEY (recommendation_id) REFERENCES temporary_supplier_recommendation(id),
  CONSTRAINT fk_temp_supplier_item_request FOREIGN KEY (purchase_request_id) REFERENCES purchase_request(id),
  CONSTRAINT fk_temp_supplier_item_request_item FOREIGN KEY (purchase_request_item_id) REFERENCES purchase_request_item(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='临时推荐供应商可报价明细表';
