SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS order_invoice_submission (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  quote_item_id BIGINT NOT NULL COMMENT '中标报价明细ID/订单ID',
  purchase_request_id BIGINT NOT NULL COMMENT '来源采购申请ID',
  supplier_id BIGINT NOT NULL COMMENT '供应商ID',
  version_no INT NOT NULL COMMENT '订单内发票版本号',
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING/ACCEPTED/RETURNED/SUPERSEDED',
  invoice_no VARCHAR(128) NULL COMMENT '发票号码',
  invoice_date DATE NULL COMMENT '开票日期',
  total_amount DECIMAL(18,2) NULL COMMENT '发票金额',
  invoice_type VARCHAR(32) NULL COMMENT '发票类型',
  remark VARCHAR(1000) NULL COMMENT '供应商备注',
  attachment_count INT NOT NULL DEFAULT 0 COMMENT '附件数量',
  submitted_by BIGINT NOT NULL COMMENT '提交人用户ID',
  submitted_by_name VARCHAR(128) NULL COMMENT '提交人姓名快照',
  submitted_at DATETIME NOT NULL COMMENT '提交时间',
  reviewed_by BIGINT NULL COMMENT '确认或退回人用户ID',
  reviewed_by_name VARCHAR(128) NULL COMMENT '确认或退回人姓名快照',
  reviewed_at DATETIME NULL COMMENT '确认或退回时间',
  review_remark VARCHAR(1000) NULL COMMENT '确认备注或退回原因',
  supersedes_submission_id BIGINT NULL COMMENT '本版本替代的提交ID',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  created_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人',
  updated_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人',
  is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  UNIQUE KEY uk_order_invoice_quote_version (quote_item_id, version_no),
  KEY idx_order_invoice_quote_status (quote_item_id, status, version_no),
  KEY idx_order_invoice_supplier_time (supplier_id, submitted_at),
  KEY idx_order_invoice_request (purchase_request_id),
  KEY idx_order_invoice_supersedes (supersedes_submission_id),
  KEY idx_order_invoice_deleted (is_deleted),
  CONSTRAINT fk_order_invoice_quote_item FOREIGN KEY (quote_item_id) REFERENCES supplier_quote_item(id),
  CONSTRAINT fk_order_invoice_supplier FOREIGN KEY (supplier_id) REFERENCES supplier(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单发票提交版本';

CREATE TABLE IF NOT EXISTS order_invoice_attachment (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  submission_id BIGINT NOT NULL COMMENT '发票提交ID',
  file_name VARCHAR(255) NOT NULL COMMENT '原始文件名',
  file_ext VARCHAR(32) NOT NULL COMMENT '文件扩展名',
  content_type VARCHAR(128) NOT NULL COMMENT 'MIME类型',
  file_size BIGINT NOT NULL COMMENT '文件大小字节数',
  file_sha256 VARCHAR(64) NOT NULL COMMENT '文件SHA-256摘要',
  storage_path VARCHAR(512) NOT NULL COMMENT '服务端随机相对路径',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  created_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人',
  updated_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人',
  is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  UNIQUE KEY uk_order_invoice_attachment_path (storage_path),
  KEY idx_order_invoice_attachment_submission (submission_id, id),
  KEY idx_order_invoice_attachment_sha (file_sha256),
  KEY idx_order_invoice_attachment_deleted (is_deleted),
  CONSTRAINT fk_order_invoice_attachment_submission FOREIGN KEY (submission_id) REFERENCES order_invoice_submission(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单发票附件';

INSERT INTO sys_permission
  (perm_code, perm_name, module_code, perm_type, parent_id, path, http_method, sort_no, status, description, created_by, updated_by, is_deleted, version)
VALUES
  ('procurement_order:invoice-read', '订单发票查看下载', 'PROCUREMENT', 'BUTTON', NULL, '/api/procurement/orders/my/*/invoices/**', 'GET', 193, 1, '按订单关系查看和下载发票，服务层校验供应商、采购人、助理及楼长范围', 0, 0, 0, 0),
  ('procurement_order:invoice-upload', '订单发票上传重传', 'PROCUREMENT', 'BUTTON', NULL, '/api/procurement/orders/my/*/invoices', 'POST', 194, 1, '供应商为本公司已确认中标订单上传或重新上传发票', 0, 0, 0, 0),
  ('procurement_order:invoice-review', '订单发票确认退回', 'PROCUREMENT', 'BUTTON', NULL, '/api/procurement/orders/my/*/invoices/*/**', 'POST', 195, 1, '采购人、来源部门助理、当前V3楼长或最高管理员确认收票或退回重传', 0, 0, 0, 0)
ON DUPLICATE KEY UPDATE
  perm_name = VALUES(perm_name),
  path = VALUES(path),
  http_method = VALUES(http_method),
  status = 1,
  description = VALUES(description),
  updated_by = 0,
  is_deleted = 0;

INSERT INTO sys_role_permission (role_id, permission_id, status, created_by, updated_by, is_deleted, version)
SELECT r.id, p.id, 1, 0, 0, 0, 0
FROM sys_role r
JOIN sys_permission p ON p.perm_code = 'procurement_order:invoice-upload' AND p.is_deleted = 0
WHERE r.role_code = 'SUPPLIER' AND r.is_deleted = 0
ON DUPLICATE KEY UPDATE status = 1, updated_by = 0, is_deleted = 0;

INSERT INTO sys_role_permission (role_id, permission_id, status, created_by, updated_by, is_deleted, version)
SELECT r.id, p.id, 1, 0, 0, 0, 0
FROM sys_role r
JOIN sys_permission p ON p.perm_code = 'procurement_order:invoice-read' AND p.is_deleted = 0
WHERE r.role_code IN ('SUPPLIER', 'ENGINEER', 'PURCHASER', 'FLOOR_LEADER', 'MANAGER', 'REGION_MANAGER', 'SENIOR_MANAGER')
  AND r.is_deleted = 0
ON DUPLICATE KEY UPDATE status = 1, updated_by = 0, is_deleted = 0;

INSERT INTO sys_role_permission (role_id, permission_id, status, created_by, updated_by, is_deleted, version)
SELECT r.id, p.id, 1, 0, 0, 0, 0
FROM sys_role r
JOIN sys_permission p ON p.perm_code = 'procurement_order:invoice-review' AND p.is_deleted = 0
WHERE r.role_code IN ('ENGINEER', 'PURCHASER', 'FLOOR_LEADER', 'MANAGER', 'REGION_MANAGER', 'SENIOR_MANAGER')
  AND r.is_deleted = 0
ON DUPLICATE KEY UPDATE status = 1, updated_by = 0, is_deleted = 0;
