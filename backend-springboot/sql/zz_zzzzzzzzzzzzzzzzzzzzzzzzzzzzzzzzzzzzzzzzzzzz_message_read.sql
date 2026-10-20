CREATE TABLE IF NOT EXISTS procurement_message_read (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL COMMENT '读取消息的用户ID',
  message_key VARCHAR(255) NOT NULL COMMENT '前端生成的消息版本标识',
  action_type VARCHAR(32) NOT NULL DEFAULT 'READ' COMMENT '消息动作 READ已读 CLEARED已清除',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  created_by BIGINT NULL COMMENT '创建人',
  updated_by BIGINT NULL COMMENT '更新人',
  is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记 0未删 1已删',
  version INT NOT NULL DEFAULT 0 COMMENT '版本号',
  UNIQUE KEY uk_procurement_message_read_user_key (user_id, message_key, is_deleted),
  KEY idx_procurement_message_read_user (user_id, is_deleted),
  KEY idx_procurement_message_read_updated_at (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采购消息已读记录';
