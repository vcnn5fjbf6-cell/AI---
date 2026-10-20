SET NAMES utf8mb4;

SET @db_name := DATABASE();

SET @ddl := IF(
  EXISTS (
    SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = @db_name
      AND TABLE_NAME = 'approval_instance'
      AND CONSTRAINT_NAME = 'fk_instance_req'
  ),
  'ALTER TABLE approval_instance DROP FOREIGN KEY fk_instance_req',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := IF(
  EXISTS (
    SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = @db_name
      AND TABLE_NAME = 'approval_record'
      AND CONSTRAINT_NAME = 'fk_record_req'
  ),
  'ALTER TABLE approval_record DROP FOREIGN KEY fk_record_req',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE approval_instance
  MODIFY COLUMN purchase_request_id BIGINT NULL COMMENT '采购申请ID，非采购业务为空';

ALTER TABLE approval_record
  MODIFY COLUMN purchase_request_id BIGINT NULL COMMENT '采购申请ID，非采购业务为空';

ALTER TABLE approval_task
  MODIFY COLUMN purchase_request_id BIGINT NULL COMMENT '采购申请ID，非采购业务为空';

SET @ddl := IF(
  NOT EXISTS (
    SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = @db_name
      AND TABLE_NAME = 'approval_instance'
      AND CONSTRAINT_NAME = 'fk_instance_req'
  ),
  'ALTER TABLE approval_instance ADD CONSTRAINT fk_instance_req FOREIGN KEY (purchase_request_id) REFERENCES purchase_request(id)',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := IF(
  NOT EXISTS (
    SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = @db_name
      AND TABLE_NAME = 'approval_record'
      AND CONSTRAINT_NAME = 'fk_record_req'
  ),
  'ALTER TABLE approval_record ADD CONSTRAINT fk_record_req FOREIGN KEY (purchase_request_id) REFERENCES purchase_request(id)',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS ehs_registry_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  record_no VARCHAR(64) NOT NULL COMMENT '台账编号',
  module_type VARCHAR(32) NOT NULL COMMENT 'HAZARD/WASTE/PPE/INCIDENT',
  title VARCHAR(255) NOT NULL COMMENT '名称或事件标题',
  category VARCHAR(128) NULL COMMENT '分类',
  sub_type VARCHAR(64) NULL COMMENT '子类型',
  building VARCHAR(128) NULL COMMENT '楼栋',
  location VARCHAR(255) NULL COMMENT '区域或位置',
  risk_level VARCHAR(32) NULL COMMENT '风险或事件等级',
  status VARCHAR(32) NOT NULL COMMENT '业务状态',
  owner_user_id BIGINT NULL COMMENT '责任人用户ID',
  owner_name VARCHAR(128) NULL COMMENT '责任人',
  handler_name VARCHAR(128) NULL COMMENT '经办人或报告人',
  quantity DECIMAL(18,3) NULL COMMENT '数量',
  unit VARCHAR(32) NULL COMMENT '单位',
  occurred_at DATETIME NULL COMMENT '发生时间',
  deadline_at DATETIME NULL COMMENT '整改/处理期限',
  completed_at DATETIME NULL COMMENT '完成时间',
  detail_json JSON NULL COMMENT '模块扩展字段',
  remark VARCHAR(1000) NULL COMMENT '备注',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  created_by BIGINT NOT NULL DEFAULT 0,
  updated_by BIGINT NOT NULL DEFAULT 0,
  is_deleted TINYINT NOT NULL DEFAULT 0,
  version INT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_ehs_registry_no (record_no),
  KEY idx_ehs_registry_module_status (module_type, status, is_deleted),
  KEY idx_ehs_registry_deadline (deadline_at, is_deleted),
  KEY idx_ehs_registry_owner (owner_user_id, is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='EHS业务台账';

CREATE TABLE IF NOT EXISTS ehs_permit (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  permit_no VARCHAR(64) NOT NULL COMMENT '作业票编号',
  parent_id BIGINT NULL COMMENT '总作业票ID，NULL表示总票',
  permit_type VARCHAR(32) NOT NULL COMMENT 'WORK_PERMIT及六类专项票',
  title VARCHAR(255) NOT NULL COMMENT '作业名称',
  applicant_user_id BIGINT NOT NULL COMMENT '申请人用户ID',
  applicant_name VARCHAR(128) NOT NULL COMMENT '申请人姓名',
  contractor_name VARCHAR(255) NULL COMMENT '施工单位',
  region VARCHAR(128) NULL COMMENT '作业区域',
  location VARCHAR(255) NOT NULL COMMENT '作业地点',
  work_content TEXT NOT NULL COMMENT '作业内容',
  planned_start_at DATETIME NOT NULL COMMENT '计划开始时间',
  planned_end_at DATETIME NOT NULL COMMENT '计划结束时间',
  actual_start_at DATETIME NULL COMMENT '实际开始时间',
  actual_end_at DATETIME NULL COMMENT '实际结束时间',
  status VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT '作业生命周期状态',
  approval_status VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT '审批状态',
  current_level_no INT NULL COMMENT '当前审批级别',
  approver_user_ids_json JSON NULL COMMENT '顺序审批人用户ID',
  special_approver_user_ids_json JSON NULL COMMENT '专项票顺序审批人用户ID',
  current_approver_id BIGINT NULL COMMENT '当前审批人ID',
  current_approver_name VARCHAR(128) NULL COMMENT '当前审批人',
  approval_instance_id BIGINT NULL COMMENT '统一审批实例ID',
  submit_round INT NOT NULL DEFAULT 0 COMMENT '提交轮次',
  required_permit_types_json JSON NULL COMMENT '总票要求的专项票类型',
  risk_items_json JSON NULL COMMENT '风险辨识与控制措施',
  safety_notice_json JSON NULL COMMENT '安全行为告知书',
  operation_steps_json JSON NULL COMMENT '关键设备操作步骤',
  signatures_json JSON NULL COMMENT '角色签名',
  attachments_json JSON NULL COMMENT '附件',
  samples_json JSON NULL COMMENT '受限空间气体采样',
  closure_json JSON NULL COMMENT '关单资料',
  closed_by BIGINT NULL COMMENT '关单人',
  closed_at DATETIME NULL COMMENT '关单时间',
  close_outcome VARCHAR(32) NULL COMMENT 'NORMAL/CANCELLED/ABNORMAL',
  close_note VARCHAR(1000) NULL COMMENT '关单说明',
  remark VARCHAR(1000) NULL COMMENT '备注',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  created_by BIGINT NOT NULL DEFAULT 0,
  updated_by BIGINT NOT NULL DEFAULT 0,
  is_deleted TINYINT NOT NULL DEFAULT 0,
  version INT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_ehs_permit_no (permit_no),
  KEY idx_ehs_permit_parent (parent_id, is_deleted),
  KEY idx_ehs_permit_status (status, approval_status, is_deleted),
  KEY idx_ehs_permit_applicant (applicant_user_id, is_deleted),
  KEY idx_ehs_permit_approver (current_approver_id, approval_status, is_deleted),
  KEY idx_ehs_permit_instance (approval_instance_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='EHS作业许可与专项票';

CREATE TABLE IF NOT EXISTS ehs_knowledge_document (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  title VARCHAR(255) NOT NULL COMMENT '知识标题',
  category VARCHAR(128) NULL COMMENT '知识分类',
  file_name VARCHAR(255) NULL COMMENT '原文件名',
  file_type VARCHAR(32) NULL COMMENT '文件类型',
  content MEDIUMTEXT NOT NULL COMMENT '可检索正文',
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  uploaded_by BIGINT NULL,
  uploaded_by_name VARCHAR(128) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  created_by BIGINT NOT NULL DEFAULT 0,
  updated_by BIGINT NOT NULL DEFAULT 0,
  is_deleted TINYINT NOT NULL DEFAULT 0,
  version INT NOT NULL DEFAULT 0,
  KEY idx_ehs_knowledge_status (status, is_deleted),
  FULLTEXT KEY ft_ehs_knowledge (title, content)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='EHS知识库';

CREATE TABLE IF NOT EXISTS ehs_ai_session (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  title VARCHAR(255) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  created_by BIGINT NOT NULL DEFAULT 0,
  updated_by BIGINT NOT NULL DEFAULT 0,
  is_deleted TINYINT NOT NULL DEFAULT 0,
  version INT NOT NULL DEFAULT 0,
  KEY idx_ehs_ai_session_user (user_id, status, is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='EHS助手会话';

CREATE TABLE IF NOT EXISTS ehs_ai_message (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  session_id BIGINT NOT NULL,
  role VARCHAR(16) NOT NULL,
  content MEDIUMTEXT NOT NULL,
  source_refs_json JSON NULL COMMENT '数据或知识来源',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  created_by BIGINT NOT NULL DEFAULT 0,
  updated_by BIGINT NOT NULL DEFAULT 0,
  is_deleted TINYINT NOT NULL DEFAULT 0,
  version INT NOT NULL DEFAULT 0,
  KEY idx_ehs_ai_message_session (session_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='EHS助手消息';

CREATE TABLE IF NOT EXISTS ehs_notification_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  biz_type VARCHAR(64) NOT NULL,
  biz_id BIGINT NOT NULL,
  recipient_user_id BIGINT NULL,
  channel VARCHAR(32) NOT NULL DEFAULT 'SYSTEM',
  event_type VARCHAR(64) NOT NULL,
  title VARCHAR(255) NOT NULL,
  content TEXT NULL,
  status VARCHAR(32) NOT NULL,
  external_message_id VARCHAR(128) NULL,
  error_message VARCHAR(1000) NULL,
  sent_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  created_by BIGINT NOT NULL DEFAULT 0,
  updated_by BIGINT NOT NULL DEFAULT 0,
  is_deleted TINYINT NOT NULL DEFAULT 0,
  version INT NOT NULL DEFAULT 0,
  KEY idx_ehs_notice_biz (biz_type, biz_id, is_deleted),
  KEY idx_ehs_notice_recipient (recipient_user_id, status, is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='EHS通知发送结果';

INSERT INTO sys_role (
  role_code, role_name, role_type, data_scope_type, status, description,
  created_by, updated_by, is_deleted, version
)
SELECT 'EHS_APPROVER', 'EHS审批人', 'BUSINESS', 'REGION', 1, 'EHS作业许可审批角色', 0, 0, 0, 0
WHERE NOT EXISTS (
  SELECT 1 FROM sys_role WHERE role_code = 'EHS_APPROVER' AND is_deleted = 0
);
