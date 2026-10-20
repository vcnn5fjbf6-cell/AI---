SET NAMES utf8mb4;

INSERT INTO sys_permission
  (perm_code, perm_name, module_code, perm_type, parent_id, path, http_method, sort_no, status, description, created_by, updated_by, is_deleted, version)
SELECT 'procurement_order:supplier-read', '供应商已售管理查看', 'PROCUREMENT', 'BUTTON', NULL, '/api/procurement/orders/my/**', 'GET', 190, 1, '供应商账号按绑定供应商主档查看本公司已售订单', 0, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'procurement_order:supplier-read' AND is_deleted = 0);

INSERT INTO sys_permission
  (perm_code, perm_name, module_code, perm_type, parent_id, path, http_method, sort_no, status, description, created_by, updated_by, is_deleted, version)
SELECT 'procurement_after_sale:supplier-read', '供应商售后查看', 'PROCUREMENT', 'BUTTON', NULL, '/api/procurement/orders/after-sales/page', 'GET', 191, 1, '供应商账号按绑定供应商主档查看本公司售后记录', 0, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'procurement_after_sale:supplier-read' AND is_deleted = 0);

INSERT INTO sys_permission
  (perm_code, perm_name, module_code, perm_type, parent_id, path, http_method, sort_no, status, description, created_by, updated_by, is_deleted, version)
SELECT 'procurement_after_sale:supplier-approve', '供应商售后处理', 'PROCUREMENT', 'BUTTON', NULL, '/api/procurement/orders/after-sales/*/approval', 'POST', 192, 1, '供应商账号按绑定供应商主档处理本公司售后申请', 0, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'procurement_after_sale:supplier-approve' AND is_deleted = 0);

INSERT INTO sys_role_permission (role_id, permission_id, status, created_by, updated_by, is_deleted, version)
SELECT r.id, p.id, 1, 0, 0, 0, 0
FROM sys_role r
JOIN sys_permission p ON p.perm_code IN (
    'procurement_order:supplier-read',
    'procurement_after_sale:supplier-read',
    'procurement_after_sale:supplier-approve'
  ) AND p.is_deleted = 0
WHERE r.role_code = 'SUPPLIER'
  AND r.is_deleted = 0
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permission rp
    WHERE rp.role_id = r.id
      AND rp.permission_id = p.id
      AND rp.is_deleted = 0
  );
