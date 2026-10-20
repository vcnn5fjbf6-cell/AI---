SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS org_subject (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'primary id',
  subject_code VARCHAR(64) NOT NULL COMMENT 'subject code',
  subject_name VARCHAR(255) NOT NULL COMMENT 'subject name',
  subject_type VARCHAR(32) NOT NULL DEFAULT 'BUSINESS_UNIT' COMMENT 'BUSINESS_UNIT/LEGAL_ENTITY/VENDOR/OTHER',
  parent_id BIGINT NULL COMMENT 'parent subject id',
  tax_no VARCHAR(64) NULL COMMENT 'tax number',
  invoice_address VARCHAR(500) NULL COMMENT 'invoice address',
  invoice_phone VARCHAR(64) NULL COMMENT 'invoice phone',
  bank_name VARCHAR(255) NULL COMMENT 'bank name',
  bank_account VARCHAR(128) NULL COMMENT 'bank account',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '1 active 0 inactive',
  remark VARCHAR(500) NULL COMMENT 'remark',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated time',
  created_by BIGINT NOT NULL DEFAULT 0 COMMENT 'created by',
  updated_by BIGINT NOT NULL DEFAULT 0 COMMENT 'updated by',
  is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT 'logical delete flag',
  version INT NOT NULL DEFAULT 0 COMMENT 'version',
  UNIQUE KEY uk_org_subject_code (subject_code),
  KEY idx_org_subject_name (subject_name),
  KEY idx_org_subject_deleted (is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='organization subjects';

CREATE TABLE IF NOT EXISTS org_scope (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'primary id',
  scope_code VARCHAR(128) NOT NULL COMMENT 'scope code',
  scope_name VARCHAR(255) NOT NULL COMMENT 'scope name',
  scope_type VARCHAR(32) NOT NULL COMMENT 'REGION/ROOM/BUILDING',
  parent_id BIGINT NULL COMMENT 'parent scope id',
  path VARCHAR(1000) NULL COMMENT 'materialized path',
  region_name VARCHAR(128) NULL COMMENT 'region name',
  room_name VARCHAR(128) NULL COMMENT 'room name',
  building_name VARCHAR(128) NULL COMMENT 'building name',
  room_nature VARCHAR(64) NULL COMMENT 'room nature',
  cost_center_code VARCHAR(64) NULL COMMENT 'cost center code',
  dept_code VARCHAR(64) NULL COMMENT 'department code',
  dept_name VARCHAR(255) NULL COMMENT 'department name',
  province VARCHAR(64) NULL COMMENT 'province',
  city VARCHAR(64) NULL COMMENT 'city',
  district_name VARCHAR(128) NULL COMMENT 'district/county',
  town_name VARCHAR(128) NULL COMMENT 'town/street',
  address VARCHAR(500) NULL COMMENT 'address',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '1 active 0 inactive',
  remark VARCHAR(500) NULL COMMENT 'remark',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated time',
  created_by BIGINT NOT NULL DEFAULT 0 COMMENT 'created by',
  updated_by BIGINT NOT NULL DEFAULT 0 COMMENT 'updated by',
  is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT 'logical delete flag',
  version INT NOT NULL DEFAULT 0 COMMENT 'version',
  UNIQUE KEY uk_org_scope_code (scope_code),
  KEY idx_org_scope_parent (parent_id),
  KEY idx_org_scope_type (scope_type),
  KEY idx_org_scope_room_building (room_name, building_name),
  KEY idx_org_scope_dept (dept_code, dept_name(120)),
  KEY idx_org_scope_deleted (is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='organization resource scopes';

CREATE TABLE IF NOT EXISTS org_subject_scope (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'primary id',
  subject_id BIGINT NOT NULL COMMENT 'subject id',
  scope_id BIGINT NOT NULL COMMENT 'scope id',
  relation_type VARCHAR(32) NOT NULL DEFAULT 'RESIDENT' COMMENT 'MANAGER/RESIDENT/SERVICE/COST_OWNER',
  is_primary_manager TINYINT NOT NULL DEFAULT 0 COMMENT '1 primary manager 0 no',
  effective_from DATETIME NULL COMMENT 'effective from',
  effective_to DATETIME NULL COMMENT 'effective to',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '1 active 0 inactive',
  remark VARCHAR(500) NULL COMMENT 'remark',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated time',
  created_by BIGINT NOT NULL DEFAULT 0 COMMENT 'created by',
  updated_by BIGINT NOT NULL DEFAULT 0 COMMENT 'updated by',
  is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT 'logical delete flag',
  version INT NOT NULL DEFAULT 0 COMMENT 'version',
  UNIQUE KEY uk_org_subject_scope (subject_id, scope_id, relation_type),
  KEY idx_org_subject_scope_subject (subject_id),
  KEY idx_org_subject_scope_scope (scope_id),
  KEY idx_org_subject_scope_deleted (is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='subject and scope relation';

CREATE TABLE IF NOT EXISTS sys_user_subject (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'primary id',
  user_id BIGINT NOT NULL COMMENT 'user id',
  subject_id BIGINT NOT NULL COMMENT 'subject id',
  is_primary TINYINT NOT NULL DEFAULT 1 COMMENT '1 primary 0 secondary',
  position_name VARCHAR(128) NULL COMMENT 'display position',
  effective_from DATETIME NULL COMMENT 'effective from',
  effective_to DATETIME NULL COMMENT 'effective to',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '1 active 0 inactive',
  remark VARCHAR(500) NULL COMMENT 'remark',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated time',
  created_by BIGINT NOT NULL DEFAULT 0 COMMENT 'created by',
  updated_by BIGINT NOT NULL DEFAULT 0 COMMENT 'updated by',
  is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT 'logical delete flag',
  version INT NOT NULL DEFAULT 0 COMMENT 'version',
  UNIQUE KEY uk_sys_user_subject (user_id, subject_id),
  KEY idx_sys_user_subject_user (user_id),
  KEY idx_sys_user_subject_subject (subject_id),
  KEY idx_sys_user_subject_deleted (is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='user subject relation';

CREATE TABLE IF NOT EXISTS sys_user_scope (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'primary id',
  user_id BIGINT NOT NULL COMMENT 'user id',
  scope_id BIGINT NOT NULL COMMENT 'scope id',
  subject_id BIGINT NULL COMMENT 'subject id',
  relation_type VARCHAR(32) NOT NULL DEFAULT 'DEMANDER' COMMENT 'DEMANDER/VIEWER/MAINTAINER',
  effective_from DATETIME NULL COMMENT 'effective from',
  effective_to DATETIME NULL COMMENT 'effective to',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '1 active 0 inactive',
  remark VARCHAR(500) NULL COMMENT 'remark',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated time',
  created_by BIGINT NOT NULL DEFAULT 0 COMMENT 'created by',
  updated_by BIGINT NOT NULL DEFAULT 0 COMMENT 'updated by',
  is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT 'logical delete flag',
  version INT NOT NULL DEFAULT 0 COMMENT 'version',
  UNIQUE KEY uk_sys_user_scope (user_id, scope_id, relation_type),
  KEY idx_sys_user_scope_user (user_id),
  KEY idx_sys_user_scope_scope (scope_id),
  KEY idx_sys_user_scope_deleted (is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='user scope relation';

CREATE TABLE IF NOT EXISTS sys_role_assignment (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'primary id',
  principal_type VARCHAR(32) NOT NULL DEFAULT 'USER' COMMENT 'USER/GROUP/SUBJECT',
  principal_id BIGINT NOT NULL COMMENT 'principal id',
  role_code VARCHAR(64) NOT NULL COMMENT 'role code',
  scope_type VARCHAR(32) NOT NULL DEFAULT 'BUILDING' COMMENT 'GLOBAL/REGION/ROOM/BUILDING/SUBJECT',
  scope_id BIGINT NULL COMMENT 'scope id',
  subject_id BIGINT NULL COMMENT 'subject id',
  effect VARCHAR(16) NOT NULL DEFAULT 'ALLOW' COMMENT 'ALLOW/DENY',
  effective_from DATETIME NULL COMMENT 'effective from',
  effective_to DATETIME NULL COMMENT 'effective to',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '1 active 0 inactive',
  remark VARCHAR(500) NULL COMMENT 'remark',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated time',
  created_by BIGINT NOT NULL DEFAULT 0 COMMENT 'created by',
  updated_by BIGINT NOT NULL DEFAULT 0 COMMENT 'updated by',
  is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT 'logical delete flag',
  version INT NOT NULL DEFAULT 0 COMMENT 'version',
  UNIQUE KEY uk_role_assignment (principal_type, principal_id, role_code, scope_type, scope_id, subject_id),
  KEY idx_role_assignment_principal (principal_type, principal_id),
  KEY idx_role_assignment_scope (scope_type, scope_id),
  KEY idx_role_assignment_role (role_code),
  KEY idx_role_assignment_deleted (is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='scoped role assignments';

CREATE TABLE IF NOT EXISTS procurement_approval_policy (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'primary id',
  policy_code VARCHAR(128) NOT NULL COMMENT 'policy code',
  policy_name VARCHAR(255) NOT NULL COMMENT 'policy name',
  scope_id BIGINT NULL COMMENT 'building scope id',
  enabled TINYINT NOT NULL DEFAULT 1 COMMENT '1 enabled 0 disabled',
  pool_push_day INT NOT NULL DEFAULT 20 COMMENT 'monthly pool push day',
  region_threshold DECIMAL(18,2) NOT NULL DEFAULT 5000.00 COMMENT 'region manager threshold',
  director_threshold DECIMAL(18,2) NOT NULL DEFAULT 100000.00 COMMENT 'director threshold',
  skip_self_approval TINYINT NOT NULL DEFAULT 1 COMMENT 'skip applicant self approval',
  skip_duplicate_approver TINYINT NOT NULL DEFAULT 1 COMMENT 'skip duplicate approver',
  skip_lower_when_higher_approved TINYINT NOT NULL DEFAULT 1 COMMENT 'skip lower levels when higher approved',
  allow_same_user_multi_node TINYINT NOT NULL DEFAULT 0 COMMENT 'allow same user approve multiple nodes',
  four_eye_required TINYINT NOT NULL DEFAULT 0 COMMENT 'four-eye required',
  over_price_rule VARCHAR(255) NOT NULL DEFAULT 'ITEM_ACTUAL_GT_REFERENCE_110' COMMENT 'over price review rule',
  remark VARCHAR(500) NULL COMMENT 'remark',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated time',
  created_by BIGINT NOT NULL DEFAULT 0 COMMENT 'created by',
  updated_by BIGINT NOT NULL DEFAULT 0 COMMENT 'updated by',
  is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT 'logical delete flag',
  version INT NOT NULL DEFAULT 0 COMMENT 'version',
  UNIQUE KEY uk_procurement_approval_policy_code (policy_code),
  KEY idx_procurement_approval_policy_scope (scope_id),
  KEY idx_procurement_approval_policy_enabled (enabled),
  KEY idx_procurement_approval_policy_deleted (is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='procurement approval policy';

CREATE TABLE IF NOT EXISTS procurement_approval_level (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'primary id',
  policy_id BIGINT NOT NULL COMMENT 'policy id',
  level_no INT NOT NULL COMMENT '1-8',
  level_code VARCHAR(32) NOT NULL COMMENT 'V1-V8',
  level_name VARCHAR(128) NOT NULL COMMENT 'level display name',
  role_code VARCHAR(64) NULL COMMENT 'optional role code',
  enabled TINYINT NOT NULL DEFAULT 1 COMMENT '1 enabled 0 disabled',
  trigger_mode VARCHAR(32) NOT NULL DEFAULT 'ALWAYS' COMMENT 'ALWAYS/BY_SPECIALTY/BY_AMOUNT/AFTER_POOL/MANUAL',
  pass_rule VARCHAR(32) NOT NULL DEFAULT 'ANY_ONE' COMMENT 'ANY_ONE/ALL',
  amount_min DECIMAL(18,2) NULL COMMENT 'amount min',
  amount_max DECIMAL(18,2) NULL COMMENT 'amount max',
  skip_if_empty TINYINT NOT NULL DEFAULT 1 COMMENT 'skip if no approver',
  sort_no INT NOT NULL DEFAULT 0 COMMENT 'sort number',
  remark VARCHAR(500) NULL COMMENT 'remark',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated time',
  created_by BIGINT NOT NULL DEFAULT 0 COMMENT 'created by',
  updated_by BIGINT NOT NULL DEFAULT 0 COMMENT 'updated by',
  is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT 'logical delete flag',
  version INT NOT NULL DEFAULT 0 COMMENT 'version',
  UNIQUE KEY uk_procurement_approval_level (policy_id, level_no),
  KEY idx_procurement_approval_level_policy (policy_id),
  KEY idx_procurement_approval_level_deleted (is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='procurement approval levels';

CREATE TABLE IF NOT EXISTS procurement_approval_level_user (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'primary id',
  level_id BIGINT NOT NULL COMMENT 'approval level id',
  user_id BIGINT NOT NULL COMMENT 'approver user id',
  sort_no INT NOT NULL DEFAULT 0 COMMENT 'sort number',
  effective_from DATETIME NULL COMMENT 'effective from',
  effective_to DATETIME NULL COMMENT 'effective to',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '1 active 0 inactive',
  remark VARCHAR(500) NULL COMMENT 'remark',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated time',
  created_by BIGINT NOT NULL DEFAULT 0 COMMENT 'created by',
  updated_by BIGINT NOT NULL DEFAULT 0 COMMENT 'updated by',
  is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT 'logical delete flag',
  version INT NOT NULL DEFAULT 0 COMMENT 'version',
  UNIQUE KEY uk_procurement_approval_level_user (level_id, user_id),
  KEY idx_procurement_approval_level_user_level (level_id),
  KEY idx_procurement_approval_level_user_user (user_id),
  KEY idx_procurement_approval_level_user_deleted (is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='procurement approval level users';

CREATE TABLE IF NOT EXISTS procurement_approval_relation (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'primary id',
  policy_id BIGINT NOT NULL COMMENT 'policy id',
  child_level_no INT NOT NULL COMMENT 'child level no',
  parent_level_no INT NOT NULL COMMENT 'parent level no',
  relation_type VARCHAR(32) NOT NULL DEFAULT 'APPROVAL_REPORT_TO' COMMENT 'APPROVAL_REPORT_TO',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '1 active 0 inactive',
  remark VARCHAR(500) NULL COMMENT 'remark',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated time',
  created_by BIGINT NOT NULL DEFAULT 0 COMMENT 'created by',
  updated_by BIGINT NOT NULL DEFAULT 0 COMMENT 'updated by',
  is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT 'logical delete flag',
  version INT NOT NULL DEFAULT 0 COMMENT 'version',
  UNIQUE KEY uk_procurement_approval_relation (policy_id, child_level_no, parent_level_no, relation_type),
  KEY idx_procurement_approval_relation_policy (policy_id),
  KEY idx_procurement_approval_relation_deleted (is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='procurement approval level relation';

CREATE TABLE IF NOT EXISTS approval_task (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'primary id',
  instance_id BIGINT NOT NULL COMMENT 'approval instance id',
  purchase_request_id BIGINT NOT NULL COMMENT 'purchase request id',
  level_no INT NOT NULL COMMENT 'approval level no',
  level_code VARCHAR(32) NOT NULL COMMENT 'approval level code',
  level_name VARCHAR(128) NOT NULL COMMENT 'approval level name',
  role_code VARCHAR(64) NULL COMMENT 'role code',
  candidate_user_id BIGINT NOT NULL COMMENT 'candidate user id',
  pass_rule VARCHAR(32) NOT NULL DEFAULT 'ANY_ONE' COMMENT 'ANY_ONE/ALL',
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED/RETURNED/AUTO_CLOSED/SKIPPED',
  action VARCHAR(32) NULL COMMENT 'APPROVE/REJECT/RETURN',
  action_comment VARCHAR(1000) NULL COMMENT 'comment',
  action_at DATETIME NULL COMMENT 'action time',
  closed_reason VARCHAR(500) NULL COMMENT 'closed reason',
  due_at DATETIME NULL COMMENT 'approval task due time',
  reminder_stage INT NOT NULL DEFAULT 0 COMMENT 'over-price review reminder stage',
  submit_round INT NOT NULL DEFAULT 1 COMMENT 'submit round',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated time',
  created_by BIGINT NOT NULL DEFAULT 0 COMMENT 'created by',
  updated_by BIGINT NOT NULL DEFAULT 0 COMMENT 'updated by',
  is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT 'logical delete flag',
  version INT NOT NULL DEFAULT 0 COMMENT 'version',
  UNIQUE KEY uk_approval_task_candidate (instance_id, level_no, candidate_user_id, submit_round),
  KEY idx_approval_task_instance (instance_id),
  KEY idx_approval_task_request (purchase_request_id),
  KEY idx_approval_task_candidate_user (candidate_user_id),
  KEY idx_approval_task_status (status),
  KEY idx_approval_task_due (status, due_at),
  KEY idx_approval_task_deleted (is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='approval candidate tasks';

INSERT INTO sys_role (role_code, role_name, role_type, data_scope_type, status, description, created_by, updated_by, is_deleted, version)
SELECT role_code, role_name, 'BUSINESS', 'REGION', 1, description, 0, 0, 0, 0
FROM (
  SELECT 'DUTY_LEADER' AS role_code, '值班长' AS role_name, 'V1值班长审核，默认关闭' AS description
  UNION ALL SELECT 'PROFESSIONAL_ENGINEER', '专业工程师', 'V2专业工程师审核，按专业采购触发'
  UNION ALL SELECT 'APPROVAL_V7', 'V7预留审批', '预留审批级别V7'
  UNION ALL SELECT 'APPROVAL_V8', 'V8预留审批', '预留审批级别V8'
) seed
WHERE NOT EXISTS (SELECT 1 FROM sys_role r WHERE r.role_code = seed.role_code);

SET @ddl = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE purchase_request ADD COLUMN building_scope_id BIGINT NULL COMMENT ''building scope id'' AFTER region_id',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchase_request' AND COLUMN_NAME = 'building_scope_id'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE purchase_request ADD COLUMN applicant_subject_id BIGINT NULL COMMENT ''applicant subject id'' AFTER building_scope_id',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchase_request' AND COLUMN_NAME = 'applicant_subject_id'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE purchase_request ADD COLUMN approval_policy_id BIGINT NULL COMMENT ''approval policy id'' AFTER applicant_subject_id',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchase_request' AND COLUMN_NAME = 'approval_policy_id'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE purchase_request ADD COLUMN approval_snapshot_json JSON NULL COMMENT ''approval chain snapshot'' AFTER approval_policy_id',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchase_request' AND COLUMN_NAME = 'approval_snapshot_json'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE purchase_request ADD COLUMN specialty_code VARCHAR(64) NULL COMMENT ''purchase specialty code'' AFTER approval_snapshot_json',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchase_request' AND COLUMN_NAME = 'specialty_code'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE purchase_request ADD COLUMN professional_review_required TINYINT NOT NULL DEFAULT 0 COMMENT ''1 professional review required'' AFTER specialty_code',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchase_request' AND COLUMN_NAME = 'professional_review_required'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE approval_instance ADD COLUMN current_level_no INT NULL COMMENT ''current approval level no'' AFTER current_node_no',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'approval_instance' AND COLUMN_NAME = 'current_level_no'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE approval_instance ADD COLUMN current_level_code VARCHAR(32) NULL COMMENT ''current approval level code'' AFTER current_level_no',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'approval_instance' AND COLUMN_NAME = 'current_level_code'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE approval_instance ADD COLUMN current_pass_rule VARCHAR(32) NULL COMMENT ''current pass rule'' AFTER current_level_code',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'approval_instance' AND COLUMN_NAME = 'current_pass_rule'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

INSERT INTO org_scope
  (scope_code, scope_name, scope_type, region_name, room_name, building_name, room_nature,
   cost_center_code, dept_code, dept_name, status, remark, created_by, updated_by, is_deleted, version)
SELECT
  COALESCE(NULLIF(r.dept_code, ''), NULLIF(r.cost_center_code, ''), r.route_key) AS scope_code,
  CONCAT_WS('-', NULLIF(r.room_name, ''), NULLIF(r.building_name, '')) AS scope_name,
  'BUILDING',
  r.region_name,
  r.room_name,
  r.building_name,
  r.room_nature,
  r.cost_center_code,
  r.dept_code,
  r.dept_name,
  r.enabled,
  r.remark,
  0,
  0,
  0,
  0
FROM procurement_approval_route r
WHERE r.is_deleted = 0
ON DUPLICATE KEY UPDATE
  scope_name = VALUES(scope_name),
  scope_type = VALUES(scope_type),
  region_name = VALUES(region_name),
  room_name = VALUES(room_name),
  building_name = VALUES(building_name),
  room_nature = VALUES(room_nature),
  cost_center_code = VALUES(cost_center_code),
  dept_code = VALUES(dept_code),
  dept_name = VALUES(dept_name),
  status = VALUES(status),
  remark = VALUES(remark),
  updated_by = VALUES(updated_by);

INSERT INTO procurement_approval_policy
  (policy_code, policy_name, scope_id, enabled, pool_push_day, region_threshold, director_threshold,
   over_price_rule, remark, created_by, updated_by, is_deleted, version)
SELECT
  CONCAT('POLICY-', s.scope_code),
  CONCAT(s.scope_name, '采购审批策略'),
  s.id,
  r.enabled,
  COALESCE(r.pool_push_day, 20),
  COALESCE(r.region_threshold, 5000.00),
  COALESCE(r.director_threshold, 100000.00),
  COALESCE(r.over_price_rule, 'ITEM_ACTUAL_GT_REFERENCE_110'),
  r.remark,
  0,
  0,
  0,
  0
FROM procurement_approval_route r
JOIN org_scope s
  ON s.scope_code = COALESCE(NULLIF(r.dept_code, ''), NULLIF(r.cost_center_code, ''), r.route_key)
 AND s.is_deleted = 0
WHERE r.is_deleted = 0
ON DUPLICATE KEY UPDATE
  policy_name = VALUES(policy_name),
  scope_id = VALUES(scope_id),
  enabled = VALUES(enabled),
  pool_push_day = VALUES(pool_push_day),
  region_threshold = VALUES(region_threshold),
  director_threshold = VALUES(director_threshold),
  over_price_rule = VALUES(over_price_rule),
  remark = VALUES(remark),
  updated_by = VALUES(updated_by);

INSERT INTO procurement_approval_level
  (policy_id, level_no, level_code, level_name, role_code, enabled, trigger_mode, pass_rule,
   amount_min, amount_max, skip_if_empty, sort_no, remark, created_by, updated_by, is_deleted, version)
SELECT p.id, seed.level_no, seed.level_code, seed.level_name, seed.role_code, seed.enabled, seed.trigger_mode, seed.pass_rule,
       seed.amount_min, seed.amount_max, seed.skip_if_empty, seed.level_no, seed.remark, 0, 0, 0, 0
FROM procurement_approval_policy p
JOIN (
  SELECT 1 AS level_no, 'V1' AS level_code, '值班长审核' AS level_name, 'DUTY_LEADER' AS role_code, 0 AS enabled, 'MANUAL' AS trigger_mode, 'ANY_ONE' AS pass_rule, NULL AS amount_min, NULL AS amount_max, 1 AS skip_if_empty, '默认关闭，按楼栋策略启用' AS remark
  UNION ALL SELECT 2, 'V2', '专业工程师审核', 'PROFESSIONAL_ENGINEER', 1, 'BY_SPECIALTY', 'ANY_ONE', NULL, NULL, 1, '专业采购时触发'
  UNION ALL SELECT 3, 'V3', '楼长审核', 'FLOOR_LEADER', 1, 'ALWAYS', 'ANY_ONE', NULL, NULL, 0, '常规采购首审'
  UNION ALL SELECT 4, 'V4', '经理审核', 'MANAGER', 1, 'AFTER_POOL', 'ANY_ONE', NULL, NULL, 0, '需求池汇总后经理审核'
  UNION ALL SELECT 5, 'V5', '区域经理审核', 'REGION_MANAGER', 1, 'BY_AMOUNT', 'ANY_ONE', 5000.00, NULL, 1, '超过区域经理金额门槛触发'
  UNION ALL SELECT 6, 'V6', '总监审核', 'SENIOR_MANAGER', 1, 'BY_AMOUNT', 'ANY_ONE', 100000.00, NULL, 1, '超过总监金额门槛触发'
  UNION ALL SELECT 7, 'V7', '预留审批V7', 'APPROVAL_V7', 0, 'MANUAL', 'ANY_ONE', NULL, NULL, 1, '预留'
  UNION ALL SELECT 8, 'V8', '预留审批V8', 'APPROVAL_V8', 0, 'MANUAL', 'ANY_ONE', NULL, NULL, 1, '预留'
) seed
WHERE p.is_deleted = 0
  AND NOT EXISTS (
    SELECT 1 FROM procurement_approval_level l
    WHERE l.policy_id = p.id
      AND l.level_no = seed.level_no
      AND l.is_deleted = 0
  );

INSERT INTO procurement_approval_level_user
  (level_id, user_id, sort_no, status, remark, created_by, updated_by, is_deleted, version)
SELECT DISTINCT l.id, u.id, u.id, 1, '旧审批路由迁移：楼长', 0, 0, 0, 0
FROM procurement_approval_policy p
JOIN org_scope s ON s.id = p.scope_id AND s.is_deleted = 0
JOIN procurement_approval_route r
  ON COALESCE(NULLIF(r.dept_code, ''), NULLIF(r.cost_center_code, ''), r.route_key) = s.scope_code
 AND r.is_deleted = 0
JOIN procurement_approval_level l ON l.policy_id = p.id AND l.level_no = 3 AND l.is_deleted = 0
JOIN sys_user u ON u.status = 1 AND u.is_deleted = 0
WHERE FIND_IN_SET(u.real_name, REPLACE(REPLACE(COALESCE(r.floor_leader_names, ''), '，', ','), '、', ',')) > 0
  AND NOT EXISTS (
    SELECT 1 FROM procurement_approval_level_user lu
    WHERE lu.level_id = l.id AND lu.user_id = u.id AND lu.is_deleted = 0
  );

INSERT INTO procurement_approval_level_user
  (level_id, user_id, sort_no, status, remark, created_by, updated_by, is_deleted, version)
SELECT DISTINCT l.id, u.id, u.id, 1, '旧审批路由迁移：经理', 0, 0, 0, 0
FROM procurement_approval_policy p
JOIN org_scope s ON s.id = p.scope_id AND s.is_deleted = 0
JOIN procurement_approval_route r
  ON COALESCE(NULLIF(r.dept_code, ''), NULLIF(r.cost_center_code, ''), r.route_key) = s.scope_code
 AND r.is_deleted = 0
JOIN procurement_approval_level l ON l.policy_id = p.id AND l.level_no = 4 AND l.is_deleted = 0
JOIN sys_user u ON u.status = 1 AND u.is_deleted = 0
WHERE FIND_IN_SET(u.real_name, REPLACE(REPLACE(COALESCE(r.manager_names, ''), '，', ','), '、', ',')) > 0
  AND NOT EXISTS (
    SELECT 1 FROM procurement_approval_level_user lu
    WHERE lu.level_id = l.id AND lu.user_id = u.id AND lu.is_deleted = 0
  );

INSERT INTO procurement_approval_level_user
  (level_id, user_id, sort_no, status, remark, created_by, updated_by, is_deleted, version)
SELECT DISTINCT l.id, u.id, u.id, 1, '旧审批路由迁移：区域经理', 0, 0, 0, 0
FROM procurement_approval_policy p
JOIN org_scope s ON s.id = p.scope_id AND s.is_deleted = 0
JOIN procurement_approval_route r
  ON COALESCE(NULLIF(r.dept_code, ''), NULLIF(r.cost_center_code, ''), r.route_key) = s.scope_code
 AND r.is_deleted = 0
JOIN procurement_approval_level l ON l.policy_id = p.id AND l.level_no = 5 AND l.is_deleted = 0
JOIN sys_user u ON u.status = 1 AND u.is_deleted = 0
WHERE FIND_IN_SET(u.real_name, REPLACE(REPLACE(COALESCE(r.region_manager_names, ''), '，', ','), '、', ',')) > 0
  AND NOT EXISTS (
    SELECT 1 FROM procurement_approval_level_user lu
    WHERE lu.level_id = l.id AND lu.user_id = u.id AND lu.is_deleted = 0
  );

INSERT INTO procurement_approval_level_user
  (level_id, user_id, sort_no, status, remark, created_by, updated_by, is_deleted, version)
SELECT DISTINCT l.id, u.id, u.id, 1, '旧审批路由迁移：总监', 0, 0, 0, 0
FROM procurement_approval_policy p
JOIN org_scope s ON s.id = p.scope_id AND s.is_deleted = 0
JOIN procurement_approval_route r
  ON COALESCE(NULLIF(r.dept_code, ''), NULLIF(r.cost_center_code, ''), r.route_key) = s.scope_code
 AND r.is_deleted = 0
JOIN procurement_approval_level l ON l.policy_id = p.id AND l.level_no = 6 AND l.is_deleted = 0
JOIN sys_user u ON u.status = 1 AND u.is_deleted = 0
WHERE FIND_IN_SET(u.real_name, REPLACE(REPLACE(COALESCE(r.director_names, ''), '，', ','), '、', ',')) > 0
  AND NOT EXISTS (
    SELECT 1 FROM procurement_approval_level_user lu
    WHERE lu.level_id = l.id AND lu.user_id = u.id AND lu.is_deleted = 0
  );

INSERT INTO sys_role_assignment
  (principal_type, principal_id, role_code, scope_type, scope_id, effect, status, remark, created_by, updated_by, is_deleted, version)
SELECT 'USER', lu.user_id, l.role_code, 'BUILDING', p.scope_id, 'ALLOW', 1, '审批级别人员自动授权', 0, 0, 0, 0
FROM procurement_approval_level_user lu
JOIN procurement_approval_level l ON l.id = lu.level_id AND l.is_deleted = 0
JOIN procurement_approval_policy p ON p.id = l.policy_id AND p.is_deleted = 0
WHERE lu.is_deleted = 0
  AND l.role_code IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_assignment a
    WHERE a.principal_type = 'USER'
      AND a.principal_id = lu.user_id
      AND a.role_code = l.role_code
      AND a.scope_type = 'BUILDING'
      AND a.scope_id = p.scope_id
      AND a.is_deleted = 0
  );

UPDATE procurement_approval_level l
JOIN procurement_approval_policy p ON p.id = l.policy_id
SET l.amount_min = p.region_threshold
WHERE l.level_no = 5
  AND l.is_deleted = 0;

UPDATE procurement_approval_level l
JOIN procurement_approval_policy p ON p.id = l.policy_id
SET l.amount_min = p.director_threshold
WHERE l.level_no = 6
  AND l.is_deleted = 0;
