CREATE TABLE IF NOT EXISTS workbench_copy (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL COMMENT 'copied user id',
  source_type VARCHAR(32) NOT NULL COMMENT 'source type: PURCHASE/PLATFORM/MONTHLY/SUPPLIER',
  source_id BIGINT NOT NULL COMMENT 'source business id',
  copied_by BIGINT NULL COMMENT 'operator user id',
  copied_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'copied time',
  remark VARCHAR(500) NULL COMMENT 'copy remark',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  created_by BIGINT NULL,
  updated_by BIGINT NULL,
  is_deleted TINYINT NOT NULL DEFAULT 0,
  version INT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_workbench_copy_user_source (user_id, source_type, source_id, is_deleted),
  KEY idx_workbench_copy_user (user_id, is_deleted),
  KEY idx_workbench_copy_source (source_type, source_id, is_deleted),
  KEY idx_workbench_copy_at (copied_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='workbench copied requests';
