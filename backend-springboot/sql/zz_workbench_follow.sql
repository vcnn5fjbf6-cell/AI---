CREATE TABLE IF NOT EXISTS workbench_follow (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL COMMENT '关注人ID',
  source_type VARCHAR(32) NOT NULL COMMENT '来源类型：PURCHASE/PLATFORM/MONTHLY/SUPPLIER',
  source_id BIGINT NOT NULL COMMENT '来源业务ID',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  created_by BIGINT NULL,
  updated_by BIGINT NULL,
  is_deleted TINYINT NOT NULL DEFAULT 0,
  version INT NOT NULL DEFAULT 0,
  KEY idx_workbench_follow_user (user_id, is_deleted),
  KEY idx_workbench_follow_source (source_type, source_id, is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作台关注请求';
