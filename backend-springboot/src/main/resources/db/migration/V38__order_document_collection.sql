SET NAMES utf8mb4;

ALTER TABLE order_invoice_submission
  ADD COLUMN document_type VARCHAR(32) NOT NULL DEFAULT 'INVOICE' COMMENT '资料类型 INVOICE/DELIVERY_NOTE' AFTER supplier_id,
  ADD COLUMN submitted_by_role VARCHAR(32) NOT NULL DEFAULT 'SUPPLIER' COMMENT '上传身份 SUPPLIER/ASSISTANT/ADMIN' AFTER submitted_by_name,
  DROP INDEX uk_order_invoice_quote_version,
  ADD UNIQUE KEY uk_order_document_quote_type_version (quote_item_id, document_type, version_no),
  ADD KEY idx_order_document_type_status (quote_item_id, document_type, status, version_no);

UPDATE order_invoice_submission
SET document_type = 'INVOICE'
WHERE document_type IS NULL OR document_type = '';

UPDATE order_invoice_submission
SET submitted_by_role = 'SUPPLIER'
WHERE submitted_by_role IS NULL OR submitted_by_role = '';

UPDATE sys_permission
SET perm_name = CASE perm_code
      WHEN 'procurement_order:invoice-read' THEN '订单资料查看下载'
      WHEN 'procurement_order:invoice-upload' THEN '订单资料上传重传'
      WHEN 'procurement_order:invoice-review' THEN '订单资料确认退回'
    END,
    description = CASE perm_code
      WHEN 'procurement_order:invoice-read' THEN '按订单关系查看和下载发票、送货单，服务层校验具体订单范围'
      WHEN 'procurement_order:invoice-upload' THEN '中标供应商、来源部门助理或最高管理员按订单上传发票和送货单，服务层校验具体订单范围'
      WHEN 'procurement_order:invoice-review' THEN '采购人、来源部门助理、当前V3楼长或最高管理员确认或退回订单资料'
    END,
    updated_by = 0,
    is_deleted = 0
WHERE perm_code IN (
  'procurement_order:invoice-read',
  'procurement_order:invoice-upload',
  'procurement_order:invoice-review'
);

INSERT INTO sys_role_permission (role_id, permission_id, status, created_by, updated_by, is_deleted, version)
SELECT r.id, p.id, 1, 0, 0, 0, 0
FROM sys_role r
JOIN sys_permission p ON p.perm_code = 'procurement_order:invoice-upload' AND p.is_deleted = 0
WHERE r.role_code IN ('ENGINEER', 'PURCHASER', 'FLOOR_LEADER', 'MANAGER', 'REGION_MANAGER', 'SENIOR_MANAGER')
  AND r.is_deleted = 0
ON DUPLICATE KEY UPDATE status = 1, updated_by = 0, is_deleted = 0;
