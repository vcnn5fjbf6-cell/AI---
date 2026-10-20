SET NAMES utf8mb4;

ALTER TABLE supplier_quote_item
    ADD COLUMN supplier_submitted_unit_price DECIMAL(18,2) NULL COMMENT '供应商原始提交单价' AFTER unit_price;

UPDATE supplier_quote_item
SET supplier_submitted_unit_price = unit_price
WHERE supplier_submitted_unit_price IS NULL;
