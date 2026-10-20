SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS personal_labor_item_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  user_id BIGINT NOT NULL COMMENT '领用人用户ID',
  username VARCHAR(64) NULL COMMENT '领用人账号',
  real_name VARCHAR(128) NULL COMMENT '领用人姓名',
  dept_code VARCHAR(128) NULL COMMENT '领用部门编码',
  dept_name VARCHAR(255) NULL COMMENT '领用部门名称',
  item_code VARCHAR(64) NOT NULL COMMENT '劳保用品编码',
  item_name VARCHAR(128) NOT NULL COMMENT '劳保用品名称',
  spec_model VARCHAR(255) NULL COMMENT '规格型号',
  unit VARCHAR(32) NOT NULL COMMENT '单位',
  quantity DECIMAL(18,4) NOT NULL DEFAULT 1 COMMENT '领用数量',
  last_received_date DATE NULL COMMENT '最后确认领用日期',
  next_due_date DATE NULL COMMENT '下一轮到期日期',
  status VARCHAR(32) NOT NULL DEFAULT 'REQUESTED' COMMENT '状态：REQUESTED申领中/RECEIVED已领用',
  purchase_request_id BIGINT NULL COMMENT '关联采购申请ID',
  purchase_request_no VARCHAR(64) NULL COMMENT '关联采购申请单号',
  purchase_request_item_id BIGINT NULL COMMENT '关联采购申请明细ID',
  emergency_flag TINYINT NOT NULL DEFAULT 0 COMMENT '是否紧急购买',
  remark VARCHAR(1000) NULL COMMENT '备注',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  created_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人',
  updated_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人',
  is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  version INT NOT NULL DEFAULT 0 COMMENT '版本号',
  KEY idx_labor_record_user_item_status (user_id, item_code, status),
  KEY idx_labor_record_due_date (next_due_date),
  KEY idx_labor_record_request (purchase_request_id),
  KEY idx_labor_record_deleted (is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='个人劳保用品领用台账';

INSERT INTO procurement_setting
(setting_key, setting_value, setting_group, setting_label, value_type, sort_no, remark, created_by, updated_by, is_deleted, version)
SELECT 'personal_labor_item_rules_json',
       '[{"itemCode":"LABOR_SHOES","itemName":"劳保鞋","unit":"双","enabled":true,"forced":false,"cycleDays":365,"quantityLimit":1,"advanceDays":30,"reminderDays":15,"emergencyEnabled":true,"managerApprovalRequired":false,"dailyUnclaimedReminderEnabled":true},{"itemCode":"LABOR_GLOVES","itemName":"劳保手套","unit":"副","enabled":true,"forced":false,"cycleDays":365,"quantityLimit":2,"advanceDays":30,"reminderDays":15,"emergencyEnabled":true,"managerApprovalRequired":false,"dailyUnclaimedReminderEnabled":true},{"itemCode":"INSULATED_GLOVES","itemName":"绝缘手套","unit":"副","enabled":true,"forced":true,"cycleDays":180,"quantityLimit":1,"advanceDays":30,"reminderDays":15,"emergencyEnabled":true,"managerApprovalRequired":true,"dailyUnclaimedReminderEnabled":true},{"itemCode":"FLASHLIGHT","itemName":"手电筒","unit":"个","enabled":true,"forced":false,"cycleDays":365,"quantityLimit":1,"advanceDays":30,"reminderDays":15,"emergencyEnabled":true,"managerApprovalRequired":false,"dailyUnclaimedReminderEnabled":true},{"itemCode":"TEST_PEN","itemName":"电笔","unit":"支","enabled":true,"forced":true,"cycleDays":365,"quantityLimit":1,"advanceDays":30,"reminderDays":15,"emergencyEnabled":true,"managerApprovalRequired":true,"dailyUnclaimedReminderEnabled":true},{"itemCode":"ANTI_STATIC_GLOVES","itemName":"防静电手套","unit":"副","enabled":true,"forced":false,"cycleDays":365,"quantityLimit":2,"advanceDays":30,"reminderDays":15,"emergencyEnabled":true,"managerApprovalRequired":false,"dailyUnclaimedReminderEnabled":true},{"itemCode":"ELECTRICAL_INSULATING_SHOES","itemName":"电绝缘鞋","unit":"双","enabled":true,"forced":true,"cycleDays":365,"quantityLimit":1,"advanceDays":30,"reminderDays":15,"emergencyEnabled":true,"managerApprovalRequired":true,"dailyUnclaimedReminderEnabled":true}]',
       'LABOR', '个人劳保用品规则', 'TEXT', 10,
       '个人劳保申领的品类、周期、数量、提前申领、提醒和强制监管规则；请优先通过采购规则设置页面维护',
       0, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM procurement_setting WHERE setting_key = 'personal_labor_item_rules_json' AND is_deleted = 0);
