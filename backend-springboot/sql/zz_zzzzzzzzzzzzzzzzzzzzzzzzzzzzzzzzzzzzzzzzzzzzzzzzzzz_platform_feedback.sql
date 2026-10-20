SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS platform_feedback (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  feedback_no VARCHAR(64) NOT NULL COMMENT '反馈编号',
  category VARCHAR(32) NOT NULL DEFAULT 'SUGGESTION' COMMENT '反馈类型：BUG/SUGGESTION/DATA/OTHER',
  title VARCHAR(200) NOT NULL COMMENT '反馈标题',
  content TEXT NULL COMMENT '反馈内容',
  links TEXT NULL COMMENT '相关链接，换行分隔',
  page_url VARCHAR(1000) NULL COMMENT '提交时所在页面',
  submitter_user_id BIGINT NULL COMMENT '提交人用户ID',
  submitter_username VARCHAR(64) NULL COMMENT '提交人账号',
  submitter_real_name VARCHAR(128) NULL COMMENT '提交人姓名',
  submitter_user_type VARCHAR(32) NULL COMMENT '提交人类型',
  submitter_dept_name VARCHAR(128) NULL COMMENT '提交人部门',
  submitter_supplier_id BIGINT NULL COMMENT '提交人供应商ID',
  attachment_count INT NOT NULL DEFAULT 0 COMMENT '附件数量',
  status VARCHAR(32) NOT NULL DEFAULT 'OPEN' COMMENT '处理状态：OPEN/PROCESSING/RESOLVED/IGNORED',
  handler_user_id BIGINT NULL COMMENT '处理人用户ID',
  handler_name VARCHAR(128) NULL COMMENT '处理人姓名',
  handle_comment VARCHAR(1000) NULL COMMENT '处理说明',
  handled_at DATETIME NULL COMMENT '处理时间',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  created_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人',
  updated_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人',
  is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  version INT NOT NULL DEFAULT 0 COMMENT '版本号',
  UNIQUE KEY uk_platform_feedback_no (feedback_no),
  KEY idx_platform_feedback_status_time (status, created_at),
  KEY idx_platform_feedback_category_time (category, created_at),
  KEY idx_platform_feedback_submitter (submitter_user_id, created_at),
  KEY idx_platform_feedback_deleted (is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台意见反馈';

CREATE TABLE IF NOT EXISTS platform_feedback_attachment (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  feedback_id BIGINT NOT NULL COMMENT '反馈ID',
  file_name VARCHAR(255) NOT NULL COMMENT '原始文件名',
  file_ext VARCHAR(32) NULL COMMENT '文件扩展名',
  content_type VARCHAR(128) NULL COMMENT 'MIME类型',
  file_size BIGINT NOT NULL DEFAULT 0 COMMENT '文件大小字节数',
  file_sha256 VARCHAR(64) NULL COMMENT '文件SHA-256摘要',
  storage_path VARCHAR(512) NOT NULL COMMENT '服务端相对存储路径',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  created_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人',
  updated_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人',
  is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  version INT NOT NULL DEFAULT 0 COMMENT '版本号',
  KEY idx_feedback_attachment_feedback (feedback_id),
  KEY idx_feedback_attachment_deleted (is_deleted),
  CONSTRAINT fk_feedback_attachment_feedback FOREIGN KEY (feedback_id) REFERENCES platform_feedback(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台意见反馈附件';

INSERT INTO sys_permission
  (perm_code, perm_name, module_code, perm_type, parent_id, path, http_method, sort_no, status, description, created_by, updated_by, is_deleted, version)
SELECT 'platform_feedback:manage', '意见反馈管理', 'PLATFORM', 'BUTTON', NULL, '/api/admin/platform/feedbacks/**', '*', 103, 1, '查看并处理全员提交的意见反馈、图片和链接', 0, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'platform_feedback:manage' AND is_deleted = 0);

INSERT INTO sys_role_permission (role_id, permission_id, status, created_by, updated_by, is_deleted, version)
SELECT r.id, p.id, 1, 0, 0, 0, 0
FROM sys_role r
JOIN sys_permission p ON p.perm_code = 'platform_feedback:manage' AND p.is_deleted = 0
WHERE r.role_code IN ('ADMIN', 'SYS_ADMIN', 'SUPER_ADMIN')
  AND r.is_deleted = 0
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permission existing_perm
    WHERE existing_perm.role_id = r.id
      AND existing_perm.permission_id = p.id
      AND existing_perm.is_deleted = 0
  );
