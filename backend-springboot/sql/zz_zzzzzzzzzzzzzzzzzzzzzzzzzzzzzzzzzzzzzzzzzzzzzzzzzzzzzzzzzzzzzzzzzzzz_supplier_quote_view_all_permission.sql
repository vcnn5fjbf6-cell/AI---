SET NAMES utf8mb4;

INSERT INTO sys_permission
  (perm_code, perm_name, module_code, perm_type, parent_id, path, http_method, sort_no, status, description, created_by, updated_by, is_deleted, version)
VALUES
  ('supplier_quote:view-all', '查看全部供应商提交', 'SUPPLIER', 'BUTTON', NULL, '/api/workflow/purchase-requests/*/quotes', 'GET', 196, 1,
   '只读查看全部供应商提交的报价、交期、替换商品和备注，不授予报价修改、比价审核或导出权限', 0, 0, 0, 0)
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

INSERT INTO sys_role
  (role_code, role_name, role_type, data_scope_type, approval_amount_min, approval_amount_max, status, description, created_by, updated_by, is_deleted, version)
VALUES
  ('SUPPLIER_QUOTE_VIEWER', '供应商提交全量查看', 'SUPPLIER', 'SELF', NULL, NULL, 1,
   '用于测试或监督账号只读查看全部供应商报价提交，不自动授予任何账号', 0, 0, 0, 0)
ON DUPLICATE KEY UPDATE
  role_name = VALUES(role_name),
  role_type = VALUES(role_type),
  data_scope_type = VALUES(data_scope_type),
  status = 1,
  description = VALUES(description),
  updated_by = 0,
  is_deleted = 0;

INSERT INTO sys_role_permission
  (role_id, permission_id, status, created_by, updated_by, is_deleted, version)
SELECT r.id, p.id, 1, 0, 0, 0, 0
FROM sys_role r
JOIN sys_permission p
  ON p.perm_code = 'supplier_quote:view-all'
 AND p.is_deleted = 0
WHERE r.role_code = 'SUPPLIER_QUOTE_VIEWER'
  AND r.is_deleted = 0
ON DUPLICATE KEY UPDATE
  status = 1,
  updated_by = 0,
  is_deleted = 0;
