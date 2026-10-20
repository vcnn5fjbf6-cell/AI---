SET NAMES utf8mb4;

INSERT INTO procurement_setting
  (setting_key, setting_value, setting_group, setting_label, value_type, sort_no, remark, created_by, updated_by, is_deleted, version)
VALUES
  ('feishu_root_department_id', '', 'FEISHU', '飞书同步根部门 ID', 'TEXT', 25, '同步飞书组织时使用的根部门 ID；留空时使用服务器环境变量 FEISHU_ROOT_DEPARTMENT_ID，默认 0', 0, 0, 0, 0)
ON DUPLICATE KEY UPDATE
  setting_group = VALUES(setting_group),
  setting_label = VALUES(setting_label),
  value_type = VALUES(value_type),
  sort_no = VALUES(sort_no),
  remark = VALUES(remark),
  updated_at = CURRENT_TIMESTAMP;
