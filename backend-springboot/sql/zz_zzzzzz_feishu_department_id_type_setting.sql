SET NAMES utf8mb4;

INSERT INTO procurement_setting
  (setting_key, setting_value, setting_group, setting_label, value_type, sort_no, remark, created_by, updated_by, is_deleted, version)
VALUES
  ('feishu_department_id_type', '', 'FEISHU', '飞书部门 ID 类型', 'TEXT', 26, '同步飞书组织时根部门 ID 的类型：department_id 或 open_department_id；留空时使用服务器环境变量 FEISHU_DEPARTMENT_ID_TYPE', 0, 0, 0, 0)
ON DUPLICATE KEY UPDATE
  setting_group = VALUES(setting_group),
  setting_label = VALUES(setting_label),
  value_type = VALUES(value_type),
  sort_no = VALUES(sort_no),
  remark = VALUES(remark),
  updated_at = CURRENT_TIMESTAMP;
