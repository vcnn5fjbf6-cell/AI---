UPDATE supplier_feishu_quote_notice
SET binding_id = 0
WHERE binding_id IS NULL;

SET @idx_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'supplier_feishu_quote_notice'
      AND INDEX_NAME = 'uk_supplier_quote_notice_target'
);
SET @ddl := IF(
    @idx_exists > 0,
    'ALTER TABLE supplier_feishu_quote_notice DROP INDEX uk_supplier_quote_notice_target',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE supplier_feishu_quote_notice
    MODIFY binding_id bigint NOT NULL DEFAULT 0 COMMENT '供应商飞书绑定ID；0=管理员抄送，-1=管理员兜底';

SET @idx_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'supplier_feishu_quote_notice'
      AND INDEX_NAME = 'uk_supplier_quote_notice_target'
);
SET @ddl := IF(
    @idx_exists = 0,
    'ALTER TABLE supplier_feishu_quote_notice ADD UNIQUE KEY uk_supplier_quote_notice_target (purchase_request_id, supplier_id, notice_type, receive_id_type, receive_id, binding_id, is_deleted)',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
