SET NAMES utf8mb4;

INSERT INTO sys_region
(id, region_code, region_name, parent_id, level_no, status, created_by, updated_by, is_deleted, version)
VALUES
(1, 'CN-NORTH', '华北区域', NULL, 1, 1, 0, 0, 0, 0),
(2, 'CN-EAST', '华东区域', NULL, 1, 1, 0, 0, 0, 0)
ON DUPLICATE KEY UPDATE region_name = VALUES(region_name);

INSERT INTO sys_role
(id, role_code, role_name, role_type, data_scope_type, approval_amount_min, approval_amount_max, status, description, created_by, updated_by, is_deleted, version)
VALUES
(1, 'ADMIN', '系统管理员', 'ADMIN', 'ALL', NULL, NULL, 1, '平台最高权限管理员', 0, 0, 0, 0),
(2, 'PURCHASER', '采购员', 'BUSINESS', 'REGION', 0.00, 500000.00, 1, '采购申请及询价处理', 0, 0, 0, 0),
(3, 'MANAGER', '部门经理', 'BUSINESS', 'REGION', 0.00, 1000000.00, 1, '部门采购审批', 0, 0, 0, 0),
(4, 'REGION_MANAGER', '区域经理', 'BUSINESS', 'REGION', 0.00, 99999999.99, 1, '区域采购审批', 0, 0, 0, 0),
(5, 'SUPPLIER', '供应商账号', 'SUPPLIER', 'SELF', NULL, NULL, 1, '供应商协同门户账号', 0, 0, 0, 0)
ON DUPLICATE KEY UPDATE role_name = VALUES(role_name), status = VALUES(status);

INSERT INTO sys_permission
(id, perm_code, perm_name, module_code, perm_type, parent_id, path, http_method, sort_no, status, description, created_by, updated_by, is_deleted, version)
VALUES
(1, 'sys_user:create', '新增用户', 'SYSTEM_USER', 'BUTTON', NULL, '/api/admin/users', 'POST', 1, 1, NULL, 0, 0, 0, 0),
(2, 'sys_user:update', '编辑用户', 'SYSTEM_USER', 'BUTTON', NULL, '/api/admin/users/*', 'PUT', 2, 1, NULL, 0, 0, 0, 0),
(3, 'sys_user:delete', '删除用户', 'SYSTEM_USER', 'BUTTON', NULL, '/api/admin/users/*', 'DELETE', 3, 1, NULL, 0, 0, 0, 0),
(4, 'sys_role:create', '新增角色', 'SYSTEM_ROLE', 'BUTTON', NULL, '/api/admin/roles', 'POST', 4, 1, NULL, 0, 0, 0, 0),
(5, 'sys_role:update', '编辑角色', 'SYSTEM_ROLE', 'BUTTON', NULL, '/api/admin/roles/*', 'PUT', 5, 1, NULL, 0, 0, 0, 0),
(6, 'sys_role:delete', '删除角色', 'SYSTEM_ROLE', 'BUTTON', NULL, '/api/admin/roles/*', 'DELETE', 6, 1, NULL, 0, 0, 0, 0),
(7, 'supplier:create', '新增供应商', 'SUPPLIER', 'BUTTON', NULL, '/api/admin/suppliers', 'POST', 7, 1, NULL, 0, 0, 0, 0),
(8, 'supplier:update', '编辑供应商', 'SUPPLIER', 'BUTTON', NULL, '/api/admin/suppliers/*', 'PUT', 8, 1, NULL, 0, 0, 0, 0),
(9, 'supplier:delete', '删除供应商', 'SUPPLIER', 'BUTTON', NULL, '/api/admin/suppliers/*', 'DELETE', 9, 1, NULL, 0, 0, 0, 0),
(10, 'purchase_request:create', '新增采购申请', 'PURCHASE_REQUEST', 'BUTTON', NULL, '/api/admin/purchase-requests', 'POST', 10, 1, NULL, 0, 0, 0, 0),
(11, 'purchase_request:update', '编辑采购申请', 'PURCHASE_REQUEST', 'BUTTON', NULL, '/api/admin/purchase-requests/*', 'PUT', 11, 1, NULL, 0, 0, 0, 0),
(12, 'purchase_request:submit', '提交采购申请', 'PURCHASE_REQUEST', 'BUTTON', NULL, '/api/admin/purchase-requests/*/submit', 'POST', 12, 1, NULL, 0, 0, 0, 0),
(13, 'purchase_request:delete', '删除采购申请', 'PURCHASE_REQUEST', 'BUTTON', NULL, '/api/admin/purchase-requests/*', 'DELETE', 13, 1, NULL, 0, 0, 0, 0),
(14, 'approval:action', '审批操作', 'APPROVAL', 'BUTTON', NULL, '/api/admin/approval-records', 'POST', 14, 1, NULL, 0, 0, 0, 0)
ON DUPLICATE KEY UPDATE perm_name = VALUES(perm_name), status = VALUES(status);

INSERT INTO sys_user
(id, username, password_hash, real_name, user_type, supplier_id, region_id, dept_code, dept_name, mobile, email, status, data_scope_type, must_change_password, remark, created_by, updated_by, is_deleted, version)
VALUES
(1, 'admin', CONCAT('DISABLED-', UUID()), '系统管理员', 'ADMIN', NULL, 1, 'IT', '信息部', '13800000001', 'demo.user001@example.invalid', 1, 'ALL', 0, '初始化管理员', 0, 0, 0, 0),
(2, 'purchaser01', CONCAT('DISABLED-', UUID()), '采购员张三', 'PURCHASER', NULL, 1, 'PUR', '采购部', '13800000002', 'demo.user002@example.invalid', 1, 'REGION', 0, '初始化采购员', 1, 1, 0, 0),
(3, 'manager01', CONCAT('DISABLED-', UUID()), '经理李四', 'MANAGER', NULL, 1, 'MGT', '管理部', '13800000004', 'demo.user003@example.invalid', 1, 'REGION', 0, '初始化经理', 1, 1, 0, 0)
ON DUPLICATE KEY UPDATE real_name = VALUES(real_name), status = VALUES(status);

INSERT INTO sys_user_role
(id, user_id, role_id, status, effective_from, effective_to, created_by, updated_by, is_deleted, version)
VALUES
(1, 1, 1, 1, NOW(), NULL, 0, 0, 0, 0),
(2, 2, 2, 1, NOW(), NULL, 1, 1, 0, 0),
(3, 3, 3, 1, NOW(), NULL, 1, 1, 0, 0)
ON DUPLICATE KEY UPDATE status = VALUES(status);

INSERT INTO sys_role_permission
(id, role_id, permission_id, status, created_by, updated_by, is_deleted, version)
VALUES
(1, 1, 1, 1, 0, 0, 0, 0),
(2, 1, 2, 1, 0, 0, 0, 0),
(3, 1, 3, 1, 0, 0, 0, 0),
(4, 1, 4, 1, 0, 0, 0, 0),
(5, 1, 5, 1, 0, 0, 0, 0),
(6, 1, 6, 1, 0, 0, 0, 0),
(7, 1, 7, 1, 0, 0, 0, 0),
(8, 1, 8, 1, 0, 0, 0, 0),
(9, 1, 9, 1, 0, 0, 0, 0),
(10, 1, 10, 1, 0, 0, 0, 0),
(11, 1, 11, 1, 0, 0, 0, 0),
(12, 1, 12, 1, 0, 0, 0, 0),
(13, 1, 13, 1, 0, 0, 0, 0),
(14, 1, 14, 1, 0, 0, 0, 0),
(15, 2, 10, 1, 1, 1, 0, 0),
(16, 2, 11, 1, 1, 1, 0, 0),
(17, 2, 12, 1, 1, 1, 0, 0),
(18, 2, 13, 1, 1, 1, 0, 0),
(19, 3, 14, 1, 1, 1, 0, 0)
ON DUPLICATE KEY UPDATE status = VALUES(status);

INSERT INTO supplier
(id, supplier_code, supplier_name, supplier_short_name, tax_no, supplier_type, region_id, contact_name, contact_phone, contact_email, address, onboarding_status, status, blacklist_flag, data_isolation_flag, created_by, updated_by, is_deleted, version)
VALUES
(1, 'SUP0001', '华北设备供应商A', '供应商A', '91110000123456789A', 'DISTRIBUTOR', 1, '王供应', '13800000008', 'demo.user008@example.invalid', '北京市朝阳区', 'APPROVED', 'ACTIVE', 0, 1, 1, 1, 0, 0)
ON DUPLICATE KEY UPDATE supplier_name = VALUES(supplier_name), status = VALUES(status);

INSERT INTO supplier_qualification
(id, supplier_id, qualification_type, qualification_no, issue_date, expiry_date, status, remark, created_by, updated_by, is_deleted, version)
VALUES
(1, 1, '营业执照', 'LIC-2026-0001', '2026-01-01', '2036-12-31', 'ACTIVE', '初始资质', 1, 1, 0, 0)
ON DUPLICATE KEY UPDATE status = VALUES(status);

INSERT INTO purchase_request
(id, req_no, req_title, req_type, applicant_user_id, purchaser_user_id, region_id, dept_code, dept_name, budget_code, currency, total_amount, urgency_level, need_date, req_reason, submit_count, current_node_no, approval_status, status, created_by, updated_by, is_deleted, version)
VALUES
(1, 'PR202604030001', '机房服务器采购', 'NORMAL', 2, 2, 1, 'OPS', '运维部', 'BUD-2026-01', 'CNY', 120000.00, 2, '2026-04-15', '扩容采购', 1, 1, 'PENDING', 'PENDING_APPROVAL', 2, 2, 0, 0)
ON DUPLICATE KEY UPDATE req_title = VALUES(req_title), approval_status = VALUES(approval_status), status = VALUES(status);

INSERT INTO purchase_request_item
(id, purchase_request_id, line_no, item_code, item_name, unit, qty, unit_price, tax_rate, line_amount, need_date, purpose, status, created_by, updated_by, is_deleted, version)
VALUES
(1, 1, 1, 'SRV-001', '2U服务器', '台', 4.00, 30000.00, 0.13, 120000.00, '2026-04-15', '机房扩容', 'ACTIVE', 2, 2, 0, 0);

INSERT INTO approval_instance
(id, instance_no, biz_type, biz_id, purchase_request_id, current_node_no, total_node_no, current_approver_id, status, started_at, created_by, updated_by, is_deleted, version)
VALUES
(1, 'AP202604030001', 'PURCHASE_REQUEST', 1, 1, 1, 2, 3, 'APPROVING', NOW(), 2, 2, 0, 0)
ON DUPLICATE KEY UPDATE status = VALUES(status), current_node_no = VALUES(current_node_no);
