-- Reconcile invoice permissions after replacing test reference authorization data.
-- The statements are idempotent so the repair is also safe on a clean database.

INSERT INTO sys_permission
  (perm_code, perm_name, module_code, perm_type, parent_id, path, http_method, sort_no, status, description, created_by, updated_by, is_deleted, version)
VALUES
  ('procurement_order:invoice-read', '订单发票查看下载', 'PROCUREMENT', 'BUTTON', NULL, '/api/procurement/orders/my/*/invoices/**', 'GET', 193, 1, '按订单关系查看和下载发票，服务层校验供应商、采购人、助理及楼长范围', 0, 0, 0, 0),
  ('procurement_order:invoice-upload', '订单发票上传重传', 'PROCUREMENT', 'BUTTON', NULL, '/api/procurement/orders/my/*/invoices', 'POST', 194, 1, '供应商为本公司已确认中标订单上传或重新上传发票', 0, 0, 0, 0),
  ('procurement_order:invoice-review', '订单发票确认退回', 'PROCUREMENT', 'BUTTON', NULL, '/api/procurement/orders/my/*/invoices/*/**', 'POST', 195, 1, '采购人、来源部门助理、当前V3楼长或最高管理员确认收票或退回重传', 0, 0, 0, 0)
ON DUPLICATE KEY UPDATE
  perm_name = VALUES(perm_name),
  module_code = VALUES(module_code),
  perm_type = VALUES(perm_type),
  path = VALUES(path),
  http_method = VALUES(http_method),
  sort_no = VALUES(sort_no),
  status = 1,
  description = VALUES(description),
  updated_by = 0,
  is_deleted = 0;

INSERT INTO sys_role_permission (role_id, permission_id, status, created_by, updated_by, is_deleted, version)
SELECT r.id, p.id, 1, 0, 0, 0, 0
FROM sys_role r
JOIN sys_permission p ON p.perm_code = 'procurement_order:invoice-upload' AND p.is_deleted = 0
WHERE r.role_code = 'SUPPLIER' AND r.is_deleted = 0
ON DUPLICATE KEY UPDATE status = 1, updated_by = 0, is_deleted = 0;

INSERT INTO sys_role_permission (role_id, permission_id, status, created_by, updated_by, is_deleted, version)
SELECT r.id, p.id, 1, 0, 0, 0, 0
FROM sys_role r
JOIN sys_permission p ON p.perm_code = 'procurement_order:invoice-read' AND p.is_deleted = 0
WHERE r.role_code IN ('SUPPLIER', 'ENGINEER', 'PURCHASER', 'FLOOR_LEADER', 'MANAGER', 'REGION_MANAGER', 'SENIOR_MANAGER')
  AND r.is_deleted = 0
ON DUPLICATE KEY UPDATE status = 1, updated_by = 0, is_deleted = 0;

INSERT INTO sys_role_permission (role_id, permission_id, status, created_by, updated_by, is_deleted, version)
SELECT r.id, p.id, 1, 0, 0, 0, 0
FROM sys_role r
JOIN sys_permission p ON p.perm_code = 'procurement_order:invoice-review' AND p.is_deleted = 0
WHERE r.role_code IN ('ENGINEER', 'PURCHASER', 'FLOOR_LEADER', 'MANAGER', 'REGION_MANAGER', 'SENIOR_MANAGER')
  AND r.is_deleted = 0
ON DUPLICATE KEY UPDATE status = 1, updated_by = 0, is_deleted = 0;
