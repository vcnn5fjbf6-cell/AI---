SET NAMES utf8mb4;

UPDATE procurement_setting
SET setting_value = '',
    updated_by = 0,
    is_deleted = 1
WHERE setting_key LIKE 'version_upgrade_github_%';
