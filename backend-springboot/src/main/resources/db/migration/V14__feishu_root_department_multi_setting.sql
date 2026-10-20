SET NAMES utf8mb4;

UPDATE procurement_setting
SET setting_label = '飞书同步根部门 ID 列表',
    remark = '同步飞书组织时使用的根部门 ID，可选择多个；留空时使用服务器环境变量 FEISHU_ROOT_DEPARTMENT_ID，默认 0',
    updated_at = CURRENT_TIMESTAMP
WHERE setting_key = 'feishu_root_department_id'
  AND is_deleted = 0;
