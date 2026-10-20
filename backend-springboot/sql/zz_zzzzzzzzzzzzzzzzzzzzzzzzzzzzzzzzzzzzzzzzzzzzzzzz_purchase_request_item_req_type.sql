SET NAMES utf8mb4;

SET @column_exists := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'purchase_request_item'
    AND COLUMN_NAME = 'req_type'
);

SET @ddl := IF(
  @column_exists = 0,
  'ALTER TABLE purchase_request_item ADD COLUMN req_type VARCHAR(64) NULL COMMENT ''采购类型'' AFTER item_code',
  'SELECT 1'
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE purchase_request
  MODIFY COLUMN req_type VARCHAR(32) NOT NULL COMMENT '汇总类型 工程项目/实物资产/无形资产/长摊等/服务/费用/低值易耗等/混合采购';

UPDATE purchase_request_item pri
JOIN purchase_request pr ON pr.id = pri.purchase_request_id
SET pri.req_type = CASE
  WHEN pr.req_type = '工程项目' THEN '工程项目'
  WHEN pr.req_type IN ('资产', '实物资产', '无形资产', '长摊', '长期待摊', '实物资产/无形资产/长摊等') THEN '实物资产/无形资产/长摊等'
  WHEN pr.req_type IN ('低值易耗', '服务', '费用', '其他', '服务/费用/低值易耗等') THEN '服务/费用/低值易耗等'
  WHEN pr.req_type = '混合采购' THEN '服务/费用/低值易耗等'
  ELSE '服务/费用/低值易耗等'
END
WHERE pri.is_deleted = 0
  AND (pri.req_type IS NULL OR pri.req_type = '');
