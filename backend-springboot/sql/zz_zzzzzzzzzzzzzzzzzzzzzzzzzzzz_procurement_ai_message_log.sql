SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS procurement_ai_message_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  source_channel VARCHAR(32) NOT NULL DEFAULT 'FEISHU_BOT' COMMENT '来源渠道',
  event_type VARCHAR(80) NULL COMMENT '飞书事件类型',
  message_id VARCHAR(128) NULL COMMENT '飞书消息ID',
  chat_id VARCHAR(128) NULL COMMENT '会话ID',
  chat_type VARCHAR(32) NULL COMMENT '会话类型',
  message_type VARCHAR(32) NULL COMMENT '消息类型',
  source_user_key VARCHAR(128) NULL COMMENT '来源用户标识',
  sender_open_id VARCHAR(128) NULL COMMENT '发送人open_id',
  sender_user_id VARCHAR(128) NULL COMMENT '发送人user_id',
  sender_union_id VARCHAR(128) NULL COMMENT '发送人union_id',
  applicant_user_id BIGINT NULL COMMENT '绑定的系统用户ID',
  username VARCHAR(64) NULL COMMENT '系统账号',
  real_name VARCHAR(128) NULL COMMENT '姓名',
  session_id BIGINT NULL COMMENT 'AI会话ID',
  session_no VARCHAR(64) NULL COMMENT 'AI会话编号',
  candidate_no INT NULL COMMENT '候选序号',
  monthly_demand_id BIGINT NULL COMMENT '月度需求ID',
  monthly_demand_no VARCHAR(64) NULL COMMENT '月度需求编号',
  purchase_request_id BIGINT NULL COMMENT '采购申请ID',
  purchase_request_no VARCHAR(64) NULL COMMENT '采购申请单号',
  intent_code VARCHAR(64) NULL COMMENT '识别意图编码',
  intent_name VARCHAR(128) NULL COMMENT '识别意图名称',
  process_status VARCHAR(32) NOT NULL DEFAULT 'RECEIVED' COMMENT '处理状态：RECEIVED/SUCCESS/FAILED/IGNORED',
  raw_text TEXT NULL COMMENT '用户消息文本',
  raw_content MEDIUMTEXT NULL COMMENT '飞书消息content',
  raw_payload MEDIUMTEXT NULL COMMENT '脱敏后的完整事件',
  dify_request_json MEDIUMTEXT NULL COMMENT 'Dify请求体，已脱敏',
  dify_response_text MEDIUMTEXT NULL COMMENT 'Dify原始响应',
  dify_parsed_json MEDIUMTEXT NULL COMMENT 'Dify解析结果',
  reply_text TEXT NULL COMMENT '系统回复内容',
  result_summary TEXT NULL COMMENT '处理结果摘要',
  error_code VARCHAR(128) NULL COMMENT '错误编码',
  error_message TEXT NULL COMMENT '错误信息',
  duration_ms BIGINT NULL COMMENT '处理耗时毫秒',
  occurred_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '接收时间',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  created_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人',
  updated_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人',
  is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  version INT NOT NULL DEFAULT 0 COMMENT '版本号',
  KEY idx_ai_msg_source_message (source_channel, message_id),
  KEY idx_ai_message_log_message (message_id),
  KEY idx_ai_message_log_status (process_status),
  KEY idx_ai_message_log_user (source_user_key, occurred_at),
  KEY idx_ai_message_log_session (session_no),
  KEY idx_ai_message_log_occurred (occurred_at),
  KEY idx_ai_message_log_deleted (is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采购AI助手消息日志';

INSERT INTO sys_permission
  (perm_code, perm_name, module_code, perm_type, parent_id, path, http_method, sort_no, status, description, created_by, updated_by, is_deleted, version)
SELECT 'platform_ai_message_log:manage', 'AI助手消息日志', 'PLATFORM', 'BUTTON', NULL, '/api/admin/procurement-ai/message-logs/**', '*', 100, 1, '查询采购AI助手飞书消息处理日志', 0, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'platform_ai_message_log:manage' AND is_deleted = 0);

INSERT INTO sys_role_permission (role_id, permission_id, status, created_by, updated_by, is_deleted, version)
SELECT r.id, p.id, 1, 0, 0, 0, 0
FROM sys_role r
JOIN sys_permission p ON p.perm_code = 'platform_ai_message_log:manage' AND p.is_deleted = 0
WHERE r.role_code IN ('ADMIN', 'SYS_ADMIN', 'SUPER_ADMIN')
  AND r.is_deleted = 0
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permission existing_perm
    WHERE existing_perm.role_id = r.id
      AND existing_perm.permission_id = p.id
      AND existing_perm.is_deleted = 0
  );
