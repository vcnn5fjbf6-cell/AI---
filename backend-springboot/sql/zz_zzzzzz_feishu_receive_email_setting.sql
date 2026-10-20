SET NAMES utf8mb4;

UPDATE procurement_setting
SET remark = '默认 email，使用系统用户邮箱发送；也可改为 open_id、user_id 或 union_id',
    updated_at = CURRENT_TIMESTAMP
WHERE setting_key = 'feishu_approval_card_receive_id_type'
  AND is_deleted = 0;
