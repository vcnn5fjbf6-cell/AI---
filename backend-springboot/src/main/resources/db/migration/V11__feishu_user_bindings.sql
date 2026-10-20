SET NAMES utf8mb4;

SET @db_name = DATABASE();

SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'feishu_user_id') = 0,
  'ALTER TABLE sys_user ADD COLUMN feishu_user_id VARCHAR(128) NULL COMMENT ''飞书 user_id'' AFTER email',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'feishu_open_id') = 0,
  'ALTER TABLE sys_user ADD COLUMN feishu_open_id VARCHAR(128) NULL COMMENT ''飞书 open_id'' AFTER feishu_user_id',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'feishu_union_id') = 0,
  'ALTER TABLE sys_user ADD COLUMN feishu_union_id VARCHAR(128) NULL COMMENT ''飞书 union_id'' AFTER feishu_open_id',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE sys_user
SET feishu_user_id = NULLIF(SUBSTRING_INDEX(SUBSTRING_INDEX(remark, 'user_id=', -1), ';', 1), '-')
WHERE (feishu_user_id IS NULL OR feishu_user_id = '')
  AND remark LIKE '%user_id=%';

UPDATE sys_user
SET feishu_open_id = NULLIF(SUBSTRING_INDEX(SUBSTRING_INDEX(remark, 'open_id=', -1), ';', 1), '-')
WHERE (feishu_open_id IS NULL OR feishu_open_id = '')
  AND remark LIKE '%open_id=%';

UPDATE sys_user
SET feishu_union_id = NULLIF(SUBSTRING_INDEX(SUBSTRING_INDEX(remark, 'union_id=', -1), ';', 1), '-')
WHERE (feishu_union_id IS NULL OR feishu_union_id = '')
  AND remark LIKE '%union_id=%';
