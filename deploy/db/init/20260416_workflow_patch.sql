SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS supplier_quote (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  quote_no VARCHAR(64) NOT NULL COMMENT '报价单号',
  purchase_request_id BIGINT NOT NULL COMMENT '采购申请ID',
  supplier_id BIGINT NOT NULL COMMENT '供应商ID',
  supplier_user_id BIGINT NULL COMMENT '供应商用户ID',
  total_amount DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '报价总金额',
  lead_time_days INT NOT NULL DEFAULT 0 COMMENT '交期天数',
  quote_remark VARCHAR(1000) NULL COMMENT '报价备注',
  status VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT '状态 DRAFT/SUBMITTED/ACCEPTED/REJECTED',
  submitted_at DATETIME NULL COMMENT '提交时间',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  created_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人',
  updated_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人',
  is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记 0未删 1已删',
  version INT NOT NULL DEFAULT 0 COMMENT '版本号',
  UNIQUE KEY uk_quote_no (quote_no),
  KEY idx_quote_request (purchase_request_id),
  KEY idx_quote_supplier (supplier_id),
  KEY idx_quote_status (status),
  KEY idx_quote_deleted (is_deleted),
  CONSTRAINT fk_quote_request FOREIGN KEY (purchase_request_id) REFERENCES purchase_request(id),
  CONSTRAINT fk_quote_supplier FOREIGN KEY (supplier_id) REFERENCES supplier(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='供应商报价表';

INSERT INTO sys_role
  (id, role_code, role_name, role_type, data_scope_type, approval_amount_min, approval_amount_max, status, description, created_by, updated_by, is_deleted, version)
SELECT 6, 'ENGINEER', '工程师', 'BUSINESS', 'REGION', 0.00, 50000.00, 1, '工程师发起采购需求', 0, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_code = 'ENGINEER');

INSERT INTO sys_role
  (id, role_code, role_name, role_type, data_scope_type, approval_amount_min, approval_amount_max, status, description, created_by, updated_by, is_deleted, version)
SELECT 7, 'FLOOR_LEADER', '楼长/现场负责人', 'BUSINESS', 'REGION', 0.00, 100000.00, 1, '采购需求初审节点', 0, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_code = 'FLOOR_LEADER');

INSERT INTO sys_role
  (id, role_code, role_name, role_type, data_scope_type, approval_amount_min, approval_amount_max, status, description, created_by, updated_by, is_deleted, version)
SELECT 8, 'SENIOR_MANAGER', '高级经理', 'BUSINESS', 'REGION', 0.00, 5000000.00, 1, '采购需求终审节点', 0, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_code = 'SENIOR_MANAGER');

INSERT INTO sys_user
  (username, password_hash, real_name, user_type, supplier_id, region_id, dept_code, dept_name, mobile, email, status, data_scope_type, must_change_password, remark, created_by, updated_by, is_deleted, version)
SELECT 'engineer01', CONCAT('DISABLED-', UUID()), '工程师王五', 'ENGINEER', NULL, 1, 'ENG', '技术部', '13800000004', 'demo.user004@example.invalid', 1, 'REGION', 0, '初始化工程师', 1, 1, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE username = 'engineer01');

INSERT INTO sys_user
  (username, password_hash, real_name, user_type, supplier_id, region_id, dept_code, dept_name, mobile, email, status, data_scope_type, must_change_password, remark, created_by, updated_by, is_deleted, version)
SELECT 'floorLeader01', CONCAT('DISABLED-', UUID()), '楼长赵六', 'FLOOR_LEADER', NULL, 1, 'FLR', '现场管理', '13800000005', 'demo.user005@example.invalid', 1, 'REGION', 0, '初始化楼长', 1, 1, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE username = 'floorLeader01');

INSERT INTO sys_user
  (username, password_hash, real_name, user_type, supplier_id, region_id, dept_code, dept_name, mobile, email, status, data_scope_type, must_change_password, remark, created_by, updated_by, is_deleted, version)
SELECT 'seniorManager01', CONCAT('DISABLED-', UUID()), '高级经理孙七', 'SENIOR_MANAGER', NULL, 1, 'SMT', '高级管理层', '13800000006', 'demo.user006@example.invalid', 1, 'REGION', 0, '初始化高级经理', 1, 1, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE username = 'seniorManager01');

INSERT INTO sys_user
  (username, password_hash, real_name, user_type, supplier_id, region_id, dept_code, dept_name, mobile, email, status, data_scope_type, must_change_password, remark, created_by, updated_by, is_deleted, version)
SELECT 'supplier01', CONCAT('DISABLED-', UUID()), '供应商A账号', 'SUPPLIER', 1, 1, 'SUP', '供应商协同', '13800000008', 'demo.user008@example.invalid', 1, 'SELF', 0, '供应商A登录账号', 1, 1, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE username = 'supplier01');

UPDATE sys_user
SET region_id = 1,
    dept_code = 'SMT',
    dept_name = '高级管理层',
    user_type = 'SENIOR_MANAGER',
    data_scope_type = 'REGION',
    updated_by = 1
WHERE username = 'seniorManager01';

UPDATE sys_user
SET user_type = 'SUPPLIER',
    supplier_id = COALESCE(supplier_id, 1),
    data_scope_type = 'SELF',
    region_id = COALESCE(region_id, 1),
    updated_by = 1
WHERE username = 'haichaung';

INSERT INTO sys_user_role
  (user_id, role_id, status, effective_from, effective_to, created_by, updated_by, is_deleted, version)
SELECT u.id, r.id, 1, NOW(), NULL, 1, 1, 0, 0
FROM sys_user u
JOIN sys_role r ON r.role_code = 'ENGINEER'
WHERE u.username = 'engineer01'
  AND NOT EXISTS (
    SELECT 1 FROM sys_user_role ur WHERE ur.user_id = u.id AND ur.role_id = r.id
  );

INSERT INTO sys_user_role
  (user_id, role_id, status, effective_from, effective_to, created_by, updated_by, is_deleted, version)
SELECT u.id, r.id, 1, NOW(), NULL, 1, 1, 0, 0
FROM sys_user u
JOIN sys_role r ON r.role_code = 'FLOOR_LEADER'
WHERE u.username = 'floorLeader01'
  AND NOT EXISTS (
    SELECT 1 FROM sys_user_role ur WHERE ur.user_id = u.id AND ur.role_id = r.id
  );

INSERT INTO sys_user_role
  (user_id, role_id, status, effective_from, effective_to, created_by, updated_by, is_deleted, version)
SELECT u.id, r.id, 1, NOW(), NULL, 1, 1, 0, 0
FROM sys_user u
JOIN sys_role r ON r.role_code = 'SENIOR_MANAGER'
WHERE u.username = 'seniorManager01'
  AND NOT EXISTS (
    SELECT 1 FROM sys_user_role ur WHERE ur.user_id = u.id AND ur.role_id = r.id
  );

INSERT INTO sys_user_role
  (user_id, role_id, status, effective_from, effective_to, created_by, updated_by, is_deleted, version)
SELECT u.id, r.id, 1, NOW(), NULL, 1, 1, 0, 0
FROM sys_user u
JOIN sys_role r ON r.role_code = 'SUPPLIER'
WHERE u.username = 'supplier01'
  AND NOT EXISTS (
    SELECT 1 FROM sys_user_role ur WHERE ur.user_id = u.id AND ur.role_id = r.id
  );

INSERT INTO sys_role_permission
  (id, role_id, permission_id, status, created_by, updated_by, is_deleted, version)
SELECT 20, r.id, p.id, 1, 1, 1, 0, 0
FROM sys_role r
JOIN sys_permission p ON p.perm_code = 'approval:action'
WHERE r.role_code = 'FLOOR_LEADER'
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

INSERT INTO sys_role_permission
  (id, role_id, permission_id, status, created_by, updated_by, is_deleted, version)
SELECT 21, r.id, p.id, 1, 1, 1, 0, 0
FROM sys_role r
JOIN sys_permission p ON p.perm_code = 'approval:action'
WHERE r.role_code = 'SENIOR_MANAGER'
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
