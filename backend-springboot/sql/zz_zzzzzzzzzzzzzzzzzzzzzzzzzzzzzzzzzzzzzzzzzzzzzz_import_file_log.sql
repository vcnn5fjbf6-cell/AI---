SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS platform_import_file_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  source_channel VARCHAR(32) NOT NULL DEFAULT 'WEB' COMMENT '来源渠道：WEB/FEISHU_BOT',
  import_type VARCHAR(64) NOT NULL COMMENT '导入类型编码',
  import_type_name VARCHAR(128) NULL COMMENT '导入类型名称',
  file_name VARCHAR(255) NULL COMMENT '原始文件名',
  file_ext VARCHAR(32) NULL COMMENT '文件扩展名',
  content_type VARCHAR(128) NULL COMMENT 'MIME类型',
  file_size BIGINT NOT NULL DEFAULT 0 COMMENT '文件大小字节数',
  file_sha256 VARCHAR(64) NULL COMMENT '文件SHA-256摘要',
  storage_mode VARCHAR(32) NOT NULL DEFAULT 'NOT_STORED' COMMENT '文件留存方式：NOT_STORED表示只记录元数据',
  storage_path VARCHAR(512) NULL COMMENT '文件存储路径，当前默认不保存原文件',
  row_count INT NULL COMMENT '有效数据行数',
  success_count INT NULL COMMENT '成功条数',
  failure_count INT NULL COMMENT '失败条数',
  process_status VARCHAR(32) NOT NULL DEFAULT 'RECEIVED' COMMENT '处理状态：RECEIVED/SUCCESS/FAILED',
  uploader_user_id BIGINT NULL COMMENT '上传系统用户ID',
  uploader_username VARCHAR(64) NULL COMMENT '上传账号',
  uploader_real_name VARCHAR(128) NULL COMMENT '上传人姓名',
  source_user_key VARCHAR(128) NULL COMMENT '飞书来源用户标识',
  message_id VARCHAR(128) NULL COMMENT '飞书消息ID',
  message_log_id BIGINT NULL COMMENT 'AI助手消息日志ID',
  related_biz_type VARCHAR(64) NULL COMMENT '关联业务类型',
  related_biz_id BIGINT NULL COMMENT '关联业务ID',
  related_biz_no VARCHAR(64) NULL COMMENT '关联业务单号',
  result_summary TEXT NULL COMMENT '处理结果摘要',
  error_message TEXT NULL COMMENT '错误信息',
  occurred_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '导入发生时间',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  created_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人',
  updated_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人',
  is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  version INT NOT NULL DEFAULT 0 COMMENT '版本号',
  KEY idx_import_log_type_time (import_type, occurred_at),
  KEY idx_import_log_status (process_status),
  KEY idx_import_log_user_time (uploader_user_id, occurred_at),
  KEY idx_import_log_message (message_id),
  KEY idx_import_log_occurred (occurred_at),
  KEY idx_import_log_deleted (is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='导入文件记录';

INSERT INTO procurement_setting
  (setting_key, setting_value, setting_group, setting_label, value_type, sort_no, remark, created_by, updated_by, is_deleted, version)
VALUES
  ('import_file_log_retention_days', '90', 'SYSTEM', '导入文件记录保留天数', 'NUMBER', 5, '系统每天 03:30 清理早于该天数的导入文件记录；填 0 或负数表示不自动清理', 0, 0, 0, 0)
ON DUPLICATE KEY UPDATE
  setting_group = VALUES(setting_group),
  setting_label = VALUES(setting_label),
  value_type = VALUES(value_type),
  sort_no = VALUES(sort_no),
  remark = VALUES(remark),
  updated_at = CURRENT_TIMESTAMP;

INSERT INTO sys_permission
  (perm_code, perm_name, module_code, perm_type, parent_id, path, http_method, sort_no, status, description, created_by, updated_by, is_deleted, version)
SELECT 'platform_import_file_log:manage', '导入文件记录', 'PLATFORM', 'BUTTON', NULL, '/api/admin/import-file-logs/**', '*', 101, 1, '查询系统导入文件元数据和处理结果', 0, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'platform_import_file_log:manage' AND is_deleted = 0);

INSERT INTO sys_role_permission (role_id, permission_id, status, created_by, updated_by, is_deleted, version)
SELECT r.id, p.id, 1, 0, 0, 0, 0
FROM sys_role r
JOIN sys_permission p ON p.perm_code = 'platform_import_file_log:manage' AND p.is_deleted = 0
WHERE r.role_code IN ('ADMIN', 'SYS_ADMIN', 'SUPER_ADMIN')
  AND r.is_deleted = 0
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permission existing_perm
    WHERE existing_perm.role_id = r.id
      AND existing_perm.permission_id = p.id
      AND existing_perm.is_deleted = 0
  );
