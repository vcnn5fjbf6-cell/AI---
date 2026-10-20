SET NAMES utf8mb4;

INSERT INTO sys_permission
  (perm_code, perm_name, module_code, perm_type, parent_id, path, http_method, sort_no, status, description, created_by, updated_by, is_deleted, version)
VALUES
  ('procurement_order:supplier-manage-all', '管理全部供应商已售订单', 'PROCUREMENT', 'BUTTON', NULL, '/api/procurement/orders/**', '*', 197, 1,
   '供应商侧最高监督账号可跨供应商查看、导出已售订单，上传发票和送货单，并处理供应商侧售后；不授予采购审批、系统管理或原始报价修改权限', 0, 0, 0, 0)
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

UPDATE sys_role
SET role_name = '供应商全量监督管理',
    description = '供应商侧最高监督账号，可查看全部供应商报价并管理全部供应商已售订单；不授予采购审批、系统管理或原始报价修改权限',
    updated_by = 0,
    is_deleted = 0
WHERE role_code = 'SUPPLIER_QUOTE_VIEWER';

INSERT INTO sys_role_permission
  (role_id, permission_id, status, created_by, updated_by, is_deleted, version)
SELECT r.id, p.id, 1, 0, 0, 0, 0
FROM sys_role r
JOIN sys_permission p
  ON p.perm_code = 'procurement_order:supplier-manage-all'
 AND p.is_deleted = 0
WHERE r.role_code = 'SUPPLIER_QUOTE_VIEWER'
  AND r.is_deleted = 0
ON DUPLICATE KEY UPDATE
  status = 1,
  updated_by = 0,
  is_deleted = 0;
