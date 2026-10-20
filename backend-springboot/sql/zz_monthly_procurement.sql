CREATE TABLE IF NOT EXISTS monthly_procurement_demand (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  demand_no VARCHAR(64) NOT NULL COMMENT '飞书采购需求号',
  plan_month VARCHAR(7) NOT NULL COMMENT '计划月份 yyyy-MM',
  applicant_user_id BIGINT NOT NULL COMMENT '申请人ID',
  region_id BIGINT NULL COMMENT '区域ID',
  dept_code VARCHAR(64) NULL COMMENT '部门编码',
  dept_name VARCHAR(128) NULL COMMENT '部门名称',
  budget_code VARCHAR(64) NULL COMMENT '预算编码',
  req_type VARCHAR(32) NOT NULL COMMENT '采购类型 低值易耗/资产/其他',
  urgency_level TINYINT NOT NULL DEFAULT 1 COMMENT '紧急程度 1普通 2紧急',
  emergency_flag TINYINT NOT NULL DEFAULT 0 COMMENT '是否紧急采购',
  req_title VARCHAR(255) NOT NULL COMMENT '申请标题',
  item_code VARCHAR(64) NOT NULL COMMENT '自动物料编码',
  item_name VARCHAR(255) NOT NULL COMMENT '商品名称',
  spec_model VARCHAR(255) NULL COMMENT '规格型号',
  unit VARCHAR(32) NOT NULL COMMENT '单位',
  qty DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '数量',
  unit_price DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '单价',
  tax_rate DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '税率',
  line_amount DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '行金额',
  need_date DATE NULL COMMENT '需求日期',
  purchase_reason VARCHAR(500) NOT NULL COMMENT '采购原因',
  ecommerce_url VARCHAR(500) NULL COMMENT '电商链接',
  source_channel VARCHAR(32) NOT NULL DEFAULT 'FEISHU_WINDOW' COMMENT '来源渠道',
  status VARCHAR(32) NOT NULL DEFAULT 'POOLING' COMMENT '状态 POOLING/REQUEST_CREATED/EMERGENCY_SUBMITTED',
  purchase_request_id BIGINT NULL COMMENT '生成的采购申请ID',
  remark VARCHAR(500) NULL COMMENT '备注',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  created_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人',
  updated_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人',
  is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记 0未删 1已删',
  version INT NOT NULL DEFAULT 0 COMMENT '版本号',
  UNIQUE KEY uk_monthly_demand_no (demand_no),
  KEY idx_monthly_plan_status (plan_month, status),
  KEY idx_monthly_applicant (applicant_user_id),
  KEY idx_monthly_request (purchase_request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='月度采购需求池';

INSERT INTO sys_permission
(perm_code, perm_name, module_code, perm_type, parent_id, path, http_method, sort_no, status, description, created_by, updated_by, is_deleted, version)
SELECT 'monthly_procurement:submit', '飞书采购需求提交', 'MONTHLY_PROCUREMENT', 'BUTTON', NULL, '/api/workflow/monthly-procurement/demands', 'POST', 120, 1, '工程师在飞书窗口提交采购需求', 0, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'monthly_procurement:submit' AND is_deleted = 0);

INSERT INTO sys_permission
(perm_code, perm_name, module_code, perm_type, parent_id, path, http_method, sort_no, status, description, created_by, updated_by, is_deleted, version)
SELECT 'monthly_procurement:view', '查看月度采购池', 'MONTHLY_PROCUREMENT', 'BUTTON', NULL, '/api/workflow/monthly-procurement/demands/**', 'GET', 121, 1, '查看飞书采购需求池', 0, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'monthly_procurement:view' AND is_deleted = 0);

INSERT INTO sys_permission
(perm_code, perm_name, module_code, perm_type, parent_id, path, http_method, sort_no, status, description, created_by, updated_by, is_deleted, version)
SELECT 'monthly_procurement:aggregate', '月度采购汇总', 'MONTHLY_PROCUREMENT', 'BUTTON', NULL, '/api/workflow/monthly-procurement/aggregate', 'POST', 122, 1, '采购员汇总月度采购需求', 0, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'monthly_procurement:aggregate' AND is_deleted = 0);

INSERT INTO sys_role_permission (role_id, permission_id, status, created_by, updated_by, is_deleted, version)
SELECT r.id, p.id, 1, 0, 0, 0, 0
FROM sys_role r
JOIN sys_permission p ON p.perm_code IN ('monthly_procurement:submit', 'monthly_procurement:view') AND p.is_deleted = 0
WHERE r.role_code IN ('ADMIN', 'SYS_ADMIN', 'SUPER_ADMIN', 'ENGINEER', 'PURCHASER', 'MANAGER', 'FLOOR_LEADER', 'REGION_MANAGER', 'SENIOR_MANAGER')
  AND r.is_deleted = 0
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permission rp
    WHERE rp.role_id = r.id AND rp.permission_id = p.id AND rp.is_deleted = 0
  );

INSERT INTO sys_role_permission (role_id, permission_id, status, created_by, updated_by, is_deleted, version)
SELECT r.id, p.id, 1, 0, 0, 0, 0
FROM sys_role r
JOIN sys_permission p ON p.perm_code = 'monthly_procurement:aggregate' AND p.is_deleted = 0
WHERE r.role_code IN ('ADMIN', 'SYS_ADMIN', 'SUPER_ADMIN', 'PURCHASER')
  AND r.is_deleted = 0
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permission rp
    WHERE rp.role_id = r.id AND rp.permission_id = p.id AND rp.is_deleted = 0
  );
