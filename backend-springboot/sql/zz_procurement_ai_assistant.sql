CREATE TABLE IF NOT EXISTS procurement_ai_session (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  session_no VARCHAR(64) NOT NULL,
  source_channel VARCHAR(32) NOT NULL DEFAULT 'WEB',
  source_user_key VARCHAR(128) NULL,
  source_message_id VARCHAR(128) NULL,
  applicant_user_id BIGINT NOT NULL,
  raw_text TEXT NOT NULL,
  image_key VARCHAR(255) NULL,
  image_url VARCHAR(500) NULL,
  req_title VARCHAR(255) NOT NULL,
  req_type VARCHAR(32) NOT NULL,
  plan_month VARCHAR(7) NOT NULL,
  dept_code VARCHAR(64) NULL,
  dept_name VARCHAR(128) NULL,
  budget_code VARCHAR(64) NULL,
  urgency_level TINYINT NOT NULL DEFAULT 1,
  emergency_flag TINYINT NOT NULL DEFAULT 0,
  need_date DATE NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'WAITING_CONFIRM',
  confirmed_candidate_id BIGINT NULL,
  monthly_demand_id BIGINT NULL,
  purchase_request_id BIGINT NULL,
  ai_summary VARCHAR(1000) NULL,
  ai_error VARCHAR(1000) NULL,
  dify_request_json MEDIUMTEXT NULL COMMENT 'Dify候选请求体，已脱敏',
  dify_response_text MEDIUMTEXT NULL COMMENT 'Dify候选原始响应',
  dify_parsed_json MEDIUMTEXT NULL COMMENT 'Dify候选解析结果',
  remark VARCHAR(500) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  created_by BIGINT NOT NULL DEFAULT 0,
  updated_by BIGINT NOT NULL DEFAULT 0,
  is_deleted TINYINT NOT NULL DEFAULT 0,
  version INT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_procurement_ai_session_no (session_no),
  KEY idx_procurement_ai_applicant (applicant_user_id),
  KEY idx_procurement_ai_source_user (source_user_key, status),
  KEY idx_procurement_ai_month_status (plan_month, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='procurement ai assistant sessions';

CREATE TABLE IF NOT EXISTS procurement_ai_candidate (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  session_id BIGINT NOT NULL,
  candidate_no INT NOT NULL,
  item_name VARCHAR(255) NOT NULL,
  spec_model VARCHAR(255) NULL,
  unit VARCHAR(32) NOT NULL DEFAULT '件',
  qty DECIMAL(18,2) NOT NULL DEFAULT 1.00,
  unit_price DECIMAL(18,2) NOT NULL DEFAULT 0.00,
  tax_rate DECIMAL(18,2) NOT NULL DEFAULT 0.00,
  line_amount DECIMAL(18,2) NOT NULL DEFAULT 0.00,
  req_type VARCHAR(32) NOT NULL DEFAULT '其他',
  purchase_reason VARCHAR(500) NOT NULL,
  ecommerce_platform VARCHAR(64) NULL,
  ecommerce_url VARCHAR(500) NULL,
  image_url VARCHAR(500) NULL,
  score DECIMAL(8,4) NULL,
  selected_flag TINYINT NOT NULL DEFAULT 0,
  source_json TEXT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  remark VARCHAR(500) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  created_by BIGINT NOT NULL DEFAULT 0,
  updated_by BIGINT NOT NULL DEFAULT 0,
  is_deleted TINYINT NOT NULL DEFAULT 0,
  version INT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_procurement_ai_candidate_no (session_id, candidate_no),
  KEY idx_procurement_ai_candidate_session (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='procurement ai product candidates';

INSERT INTO sys_permission
(perm_code, perm_name, module_code, perm_type, parent_id, path, http_method, sort_no, status, description, created_by, updated_by, is_deleted, version)
SELECT 'procurement_ai:start', 'AI采购助手发起', 'PROCUREMENT_AI', 'BUTTON', NULL, '/api/workflow/procurement-ai/sessions', 'POST', 130, 1, 'Create procurement AI recommendation session', 0, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'procurement_ai:start' AND is_deleted = 0);

INSERT INTO sys_permission
(perm_code, perm_name, module_code, perm_type, parent_id, path, http_method, sort_no, status, description, created_by, updated_by, is_deleted, version)
SELECT 'procurement_ai:view', 'AI采购助手查看', 'PROCUREMENT_AI', 'BUTTON', NULL, '/api/workflow/procurement-ai/sessions/**', 'GET', 131, 1, 'View procurement AI recommendation session', 0, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'procurement_ai:view' AND is_deleted = 0);

INSERT INTO sys_permission
(perm_code, perm_name, module_code, perm_type, parent_id, path, http_method, sort_no, status, description, created_by, updated_by, is_deleted, version)
SELECT 'procurement_ai:confirm', 'AI采购助手确认', 'PROCUREMENT_AI', 'BUTTON', NULL, '/api/workflow/procurement-ai/sessions/**', 'POST', 132, 1, 'Confirm procurement AI candidate into purchase request workflow', 0, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'procurement_ai:confirm' AND is_deleted = 0);

INSERT INTO sys_role_permission (role_id, permission_id, status, created_by, updated_by, is_deleted, version)
SELECT r.id, p.id, 1, 0, 0, 0, 0
FROM sys_role r
JOIN sys_permission p ON p.perm_code IN ('procurement_ai:start', 'procurement_ai:view', 'procurement_ai:confirm') AND p.is_deleted = 0
WHERE r.role_code IN ('ADMIN', 'SYS_ADMIN', 'SUPER_ADMIN', 'ENGINEER', 'PURCHASER', 'MANAGER', 'FLOOR_LEADER', 'REGION_MANAGER', 'SENIOR_MANAGER')
  AND r.is_deleted = 0
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permission rp
    WHERE rp.role_id = r.id AND rp.permission_id = p.id AND rp.is_deleted = 0
  );
