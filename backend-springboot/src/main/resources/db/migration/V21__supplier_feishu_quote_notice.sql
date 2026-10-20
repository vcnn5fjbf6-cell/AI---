SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS supplier_feishu_binding (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  supplier_id BIGINT NOT NULL COMMENT '供应商ID',
  supplier_code VARCHAR(64) NULL COMMENT '供应商编码',
  supplier_name VARCHAR(255) NULL COMMENT '供应商名称',
  bind_status VARCHAR(32) NOT NULL DEFAULT 'BOUND' COMMENT '绑定状态 BOUND/DISABLED',
  receive_id_type VARCHAR(32) NOT NULL DEFAULT 'chat_id' COMMENT '飞书接收ID类型 open_id/chat_id',
  receive_id VARCHAR(128) NOT NULL COMMENT '飞书接收ID',
  open_id VARCHAR(128) NULL COMMENT '飞书 open_id',
  user_id VARCHAR(128) NULL COMMENT '飞书 user_id',
  union_id VARCHAR(128) NULL COMMENT '飞书 union_id',
  chat_id VARCHAR(128) NULL COMMENT '飞书群聊ID',
  chat_type VARCHAR(32) NULL COMMENT '飞书会话类型',
  tenant_key VARCHAR(128) NULL COMMENT '飞书租户标识',
  source_user_key VARCHAR(128) NULL COMMENT '消息来源用户标识',
  last_message_id VARCHAR(128) NULL COMMENT '最近绑定消息ID',
  bound_at DATETIME NULL COMMENT '绑定时间',
  disabled_at DATETIME NULL COMMENT '停用时间',
  remark VARCHAR(512) NULL COMMENT '备注',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  created_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人',
  updated_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人',
  is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记 0未删 1已删',
  version INT NOT NULL DEFAULT 0 COMMENT '版本号',
  UNIQUE KEY uk_supplier_feishu_binding_target (supplier_id, receive_id_type, receive_id, is_deleted),
  KEY idx_supplier_feishu_binding_supplier (supplier_id, bind_status),
  KEY idx_supplier_feishu_binding_chat (chat_id),
  KEY idx_supplier_feishu_binding_open (open_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='供应商飞书外部会话绑定';

CREATE TABLE IF NOT EXISTS supplier_feishu_quote_notice (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  purchase_request_id BIGINT NOT NULL COMMENT '采购申请ID',
  request_no VARCHAR(64) NULL COMMENT '采购申请单号',
  supplier_id BIGINT NOT NULL COMMENT '供应商ID',
  binding_id BIGINT NULL COMMENT '供应商飞书绑定ID',
  receive_id_type VARCHAR(32) NOT NULL COMMENT '飞书接收ID类型',
  receive_id VARCHAR(128) NOT NULL COMMENT '飞书接收ID',
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '发送状态 PENDING/SENT/FAILED/SKIPPED',
  message_id VARCHAR(128) NULL COMMENT '飞书消息ID',
  last_error TEXT NULL COMMENT '最近错误',
  sent_at DATETIME NULL COMMENT '发送时间',
  retry_count INT NOT NULL DEFAULT 0 COMMENT '重试次数',
  card_version VARCHAR(32) NULL COMMENT '卡片版本',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  created_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人',
  updated_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人',
  is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记 0未删 1已删',
  version INT NOT NULL DEFAULT 0 COMMENT '版本号',
  UNIQUE KEY uk_supplier_quote_notice_target (purchase_request_id, supplier_id, receive_id_type, receive_id, is_deleted),
  KEY idx_supplier_quote_notice_request (purchase_request_id, status),
  KEY idx_supplier_quote_notice_supplier (supplier_id, status),
  KEY idx_supplier_quote_notice_binding (binding_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='供应商飞书报价任务通知记录';

INSERT INTO procurement_setting
(setting_key, setting_value, setting_group, setting_label, value_type, sort_no, remark, created_by, updated_by, is_deleted, version)
SELECT 'feishu_supplier_quote_notice_enabled', '', 'FEISHU', '启用供应商报价通知', 'BOOLEAN', 110,
       '开启后，系统会扫描已进入报价中的采购申请，并向已绑定的供应商外部群或单聊推送报价任务卡片；报价仍走原供应商报价流程',
       0, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM procurement_setting WHERE setting_key = 'feishu_supplier_quote_notice_enabled' AND is_deleted = 0);

INSERT INTO procurement_setting
(setting_key, setting_value, setting_group, setting_label, value_type, sort_no, remark, created_by, updated_by, is_deleted, version)
SELECT 'feishu_supplier_quote_notice_scan_batch_size', '50', 'FEISHU', '供应商通知扫描批量', 'NUMBER', 120,
       '每次心跳最多扫描的报价中采购申请数量，范围 10-500',
       0, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM procurement_setting WHERE setting_key = 'feishu_supplier_quote_notice_scan_batch_size' AND is_deleted = 0);
