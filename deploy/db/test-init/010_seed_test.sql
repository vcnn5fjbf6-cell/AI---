SET NAMES utf8mb4;

DELIMITER $$

DROP PROCEDURE IF EXISTS assert_empty_test_database$$

CREATE PROCEDURE assert_empty_test_database()
BEGIN
  IF EXISTS (SELECT 1 FROM sys_user LIMIT 1)
     OR EXISTS (SELECT 1 FROM purchase_request LIMIT 1) THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'Refusing to initialize test environment from a database containing users or business data';
  END IF;
END$$

CALL assert_empty_test_database()$$
DROP PROCEDURE IF EXISTS assert_empty_test_database$$

DELIMITER ;

INSERT INTO procurement_setting
  (setting_key, setting_value, setting_group, setting_label, value_type, sort_no, remark, created_by, updated_by, is_deleted, version)
VALUES
  ('test_environment_marker', 'procurement-test', 'SYSTEM', '测试环境标识', 'TEXT', 0,
   '独立测试数据库安全标识，请勿用于生产环境', 0, 0, 0, 0)
ON DUPLICATE KEY UPDATE
  setting_value = 'procurement-test',
  updated_by = 0,
  is_deleted = 0;

INSERT INTO sys_region
  (id, region_code, region_name, parent_id, level_no, status, created_by, updated_by, is_deleted, version)
VALUES
  (9000001, 'TEST', '测试区域', NULL, 1, 1, 0, 0, 0, 0)
ON DUPLICATE KEY UPDATE
  region_name = VALUES(region_name),
  status = 1,
  is_deleted = 0;

INSERT INTO sys_role
  (id, role_code, role_name, role_type, data_scope_type, approval_amount_min, approval_amount_max, status, description, created_by, updated_by, is_deleted, version)
VALUES
  (9000001, 'SUPER_ADMIN', '测试环境管理员', 'ADMIN', 'ALL', NULL, NULL, 1, '仅用于独立测试环境', 0, 0, 0, 0)
ON DUPLICATE KEY UPDATE
  role_name = VALUES(role_name),
  status = 1,
  is_deleted = 0;

INSERT INTO sys_user
  (id, username, password_hash, real_name, employee_no, user_type, supplier_id, region_id, dept_code, dept_name, mobile, email,
   feishu_user_id, feishu_open_id, feishu_union_id, status, data_scope_type, session_secret, must_change_password,
   password_changed_at, remark, created_by, updated_by, is_deleted, version)
VALUES
  (9000001, 'test-admin', CONCAT('DISABLED-', UUID()), '测试管理员', 'TEST-ADMIN-001', 'SUPER_ADMIN', NULL, 9000001,
   'TEST', '测试部门', NULL, 'demo.user010@example.invalid', NULL, NULL, NULL, 1, 'ALL', NULL, 1, NOW(),
   '仅用于独立测试环境，不绑定任何真实身份', 0, 0, 0, 0)
ON DUPLICATE KEY UPDATE
  real_name = VALUES(real_name),
  user_type = VALUES(user_type),
  region_id = VALUES(region_id),
  dept_code = VALUES(dept_code),
  dept_name = VALUES(dept_name),
  mobile = NULL,
  email = VALUES(email),
  feishu_user_id = NULL,
  feishu_open_id = NULL,
  feishu_union_id = NULL,
  status = 1,
  data_scope_type = 'ALL',
  session_secret = NULL,
  must_change_password = 1,
  password_changed_at = NOW(),
  is_deleted = 0;

INSERT INTO sys_user_role
  (user_id, role_id, status, effective_from, effective_to, created_by, updated_by, is_deleted, version)
SELECT 9000001, 9000001, 1, NOW(), NULL, 0, 0, 0, 0
WHERE NOT EXISTS (
  SELECT 1
  FROM sys_user_role
  WHERE user_id = 9000001 AND role_id = 9000001 AND is_deleted = 0
);

INSERT INTO platform_user_app_access
  (user_id, app_code, status, granted_by, granted_at, remark, created_by, updated_by, is_deleted, version)
SELECT 9000001, app.app_code, 1, 9000001, NOW(), '测试环境初始化授权', 0, 0, 0, 0
FROM (
  SELECT 'PLATFORM' AS app_code
  UNION ALL SELECT 'PROCUREMENT'
  UNION ALL SELECT 'MAINTENANCE'
) app
WHERE NOT EXISTS (
  SELECT 1
  FROM platform_user_app_access existing_access
  WHERE existing_access.user_id = 9000001
    AND existing_access.app_code = app.app_code
    AND existing_access.is_deleted = 0
);
