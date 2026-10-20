SET NAMES utf8mb4;

-- 创建/启用平台管理员账号 admin。
-- 运行前需在同一数据库会话设置 @admin_initial_password_hash 为一次性密码的 60 位 BCrypt 哈希。
-- 未设置或格式不符时，不创建账号，也不覆盖禁用密码。明文密码和哈希均不存入仓库。
-- 脚本可重复执行：已有真实密码的账号不会被覆盖。

-- 如果 admin 账号被逻辑删除，先恢复为启用状态。
UPDATE sys_user
SET is_deleted = 0,
    status = 1,
    real_name = '系统管理员',
    user_type = 'SUPER_ADMIN',
    supplier_id = NULL,
    data_scope_type = 'ALL',
    must_change_password = 1,
    password_changed_at = NOW(),
    updated_by = 0,
    version = version + 1
WHERE username = 'admin';

-- 如果 admin 账号不存在，则创建。
INSERT INTO sys_user
  (username, password_hash, real_name, user_type, supplier_id, region_id, dept_code, dept_name,
   status, data_scope_type, must_change_password, password_changed_at, remark,
   created_by, updated_by, is_deleted, version)
SELECT 'admin', @admin_initial_password_hash,
       '系统管理员', 'SUPER_ADMIN', NULL, NULL, 'IT', '信息部',
       1, 'ALL', 1, NOW(), '平台管理员账号（随机初始密码，首次登录强制修改）',
       0, 0, 0, 0
WHERE CHAR_LENGTH(@admin_initial_password_hash) = 60
  AND LEFT(@admin_initial_password_hash, 4) IN ('$2a

-- 仅当 admin 密码仍为初始化禁用态时，写入可登录的 BCrypt 初始密码。
UPDATE sys_user
SET password_hash = @admin_initial_password_hash,
    must_change_password = 1,
    password_changed_at = NOW(),
    session_secret = NULL,
    updated_by = 0,
    version = version + 1
WHERE username = 'admin'
  AND is_deleted = 0
  AND password_hash LIKE 'DISABLED-%'
  AND CHAR_LENGTH(@admin_initial_password_hash) = 60
  AND LEFT(@admin_initial_password_hash, 4) IN ('$2a$', '$2b$', '$2y$');

-- 确保最高管理员角色存在。
UPDATE sys_role
SET is_deleted = 0,
    status = 1,
    role_name = '平台超管',
    role_type = 'ADMIN',
    data_scope_type = 'ALL',
    updated_by = 0,
    version = version + 1
WHERE role_code = 'SUPER_ADMIN';

INSERT INTO sys_role
  (role_code, role_name, role_type, data_scope_type, approval_amount_min, approval_amount_max,
   status, description, created_by, updated_by, is_deleted, version)
SELECT 'SUPER_ADMIN', '平台超管', 'ADMIN', 'ALL', NULL, NULL,
       1, '平台最高权限角色', 0, 0, 0, 0
WHERE NOT EXISTS (
  SELECT 1 FROM sys_role WHERE role_code = 'SUPER_ADMIN' AND is_deleted = 0
);

-- 绑定 ADMIN / SUPER_ADMIN 角色。
INSERT INTO sys_user_role
  (user_id, role_id, status, effective_from, effective_to, created_by, updated_by, is_deleted, version)
SELECT u.id, r.id, 1, NULL, NULL, 0, 0, 0, 0
FROM sys_user u
JOIN sys_role r ON r.role_code IN ('ADMIN', 'SUPER_ADMIN')
  AND r.is_deleted = 0
WHERE u.username = 'admin'
  AND u.is_deleted = 0
ON DUPLICATE KEY UPDATE
  status = 1,
  effective_from = NULL,
  effective_to = NULL,
  is_deleted = 0,
  updated_by = 0,
  version = sys_user_role.version + 1;

-- 补齐 SUPER_ADMIN 的全部已启用权限。
INSERT INTO sys_role_permission
  (role_id, permission_id, status, created_by, updated_by, is_deleted, version)
SELECT r.id, p.id, 1, 0, 0, 0, 0
FROM sys_role r
JOIN sys_permission p ON p.status = 1 AND p.is_deleted = 0
WHERE r.role_code = 'SUPER_ADMIN'
  AND r.is_deleted = 0
ON DUPLICATE KEY UPDATE
  status = 1,
  is_deleted = 0,
  updated_by = 0,
  version = sys_role_permission.version + 1;

-- 补齐平台、采购、维保系统入口授权。
INSERT INTO platform_user_app_access
  (user_id, app_code, status, granted_by, granted_at, remark,
   created_by, updated_by, is_deleted, version)
SELECT u.id, app.app_code, 1, 0, NOW(), '平台管理员账号初始化',
       0, 0, 0, 0
FROM sys_user u
JOIN (
  SELECT 'PLATFORM' AS app_code
  UNION ALL SELECT 'PROCUREMENT'
  UNION ALL SELECT 'MAINTENANCE'
) app
WHERE u.username = 'admin'
  AND u.is_deleted = 0
ON DUPLICATE KEY UPDATE
  status = 1,
  granted_by = 0,
  granted_at = NOW(),
  remark = '平台管理员账号初始化',
  is_deleted = 0,
  updated_by = 0,
  version = platform_user_app_access.version + 1;
, '$2b

-- 仅当 admin 密码仍为初始化禁用态时，写入可登录的 BCrypt 初始密码。
UPDATE sys_user
SET password_hash = @admin_initial_password_hash,
    must_change_password = 1,
    password_changed_at = NOW(),
    session_secret = NULL,
    updated_by = 0,
    version = version + 1
WHERE username = 'admin'
  AND is_deleted = 0
  AND password_hash LIKE 'DISABLED-%';

-- 确保最高管理员角色存在。
UPDATE sys_role
SET is_deleted = 0,
    status = 1,
    role_name = '平台超管',
    role_type = 'ADMIN',
    data_scope_type = 'ALL',
    updated_by = 0,
    version = version + 1
WHERE role_code = 'SUPER_ADMIN';

INSERT INTO sys_role
  (role_code, role_name, role_type, data_scope_type, approval_amount_min, approval_amount_max,
   status, description, created_by, updated_by, is_deleted, version)
SELECT 'SUPER_ADMIN', '平台超管', 'ADMIN', 'ALL', NULL, NULL,
       1, '平台最高权限角色', 0, 0, 0, 0
WHERE NOT EXISTS (
  SELECT 1 FROM sys_role WHERE role_code = 'SUPER_ADMIN' AND is_deleted = 0
);

-- 绑定 ADMIN / SUPER_ADMIN 角色。
INSERT INTO sys_user_role
  (user_id, role_id, status, effective_from, effective_to, created_by, updated_by, is_deleted, version)
SELECT u.id, r.id, 1, NULL, NULL, 0, 0, 0, 0
FROM sys_user u
JOIN sys_role r ON r.role_code IN ('ADMIN', 'SUPER_ADMIN')
  AND r.is_deleted = 0
WHERE u.username = 'admin'
  AND u.is_deleted = 0
ON DUPLICATE KEY UPDATE
  status = 1,
  effective_from = NULL,
  effective_to = NULL,
  is_deleted = 0,
  updated_by = 0,
  version = sys_user_role.version + 1;

-- 补齐 SUPER_ADMIN 的全部已启用权限。
INSERT INTO sys_role_permission
  (role_id, permission_id, status, created_by, updated_by, is_deleted, version)
SELECT r.id, p.id, 1, 0, 0, 0, 0
FROM sys_role r
JOIN sys_permission p ON p.status = 1 AND p.is_deleted = 0
WHERE r.role_code = 'SUPER_ADMIN'
  AND r.is_deleted = 0
ON DUPLICATE KEY UPDATE
  status = 1,
  is_deleted = 0,
  updated_by = 0,
  version = sys_role_permission.version + 1;

-- 补齐平台、采购、维保系统入口授权。
INSERT INTO platform_user_app_access
  (user_id, app_code, status, granted_by, granted_at, remark,
   created_by, updated_by, is_deleted, version)
SELECT u.id, app.app_code, 1, 0, NOW(), '平台管理员账号初始化',
       0, 0, 0, 0
FROM sys_user u
JOIN (
  SELECT 'PLATFORM' AS app_code
  UNION ALL SELECT 'PROCUREMENT'
  UNION ALL SELECT 'MAINTENANCE'
) app
WHERE u.username = 'admin'
  AND u.is_deleted = 0
ON DUPLICATE KEY UPDATE
  status = 1,
  granted_by = 0,
  granted_at = NOW(),
  remark = '平台管理员账号初始化',
  is_deleted = 0,
  updated_by = 0,
  version = platform_user_app_access.version + 1;
, '$2y

-- 仅当 admin 密码仍为初始化禁用态时，写入可登录的 BCrypt 初始密码。
UPDATE sys_user
SET password_hash = @admin_initial_password_hash,
    must_change_password = 1,
    password_changed_at = NOW(),
    session_secret = NULL,
    updated_by = 0,
    version = version + 1
WHERE username = 'admin'
  AND is_deleted = 0
  AND password_hash LIKE 'DISABLED-%';

-- 确保最高管理员角色存在。
UPDATE sys_role
SET is_deleted = 0,
    status = 1,
    role_name = '平台超管',
    role_type = 'ADMIN',
    data_scope_type = 'ALL',
    updated_by = 0,
    version = version + 1
WHERE role_code = 'SUPER_ADMIN';

INSERT INTO sys_role
  (role_code, role_name, role_type, data_scope_type, approval_amount_min, approval_amount_max,
   status, description, created_by, updated_by, is_deleted, version)
SELECT 'SUPER_ADMIN', '平台超管', 'ADMIN', 'ALL', NULL, NULL,
       1, '平台最高权限角色', 0, 0, 0, 0
WHERE NOT EXISTS (
  SELECT 1 FROM sys_role WHERE role_code = 'SUPER_ADMIN' AND is_deleted = 0
);

-- 绑定 ADMIN / SUPER_ADMIN 角色。
INSERT INTO sys_user_role
  (user_id, role_id, status, effective_from, effective_to, created_by, updated_by, is_deleted, version)
SELECT u.id, r.id, 1, NULL, NULL, 0, 0, 0, 0
FROM sys_user u
JOIN sys_role r ON r.role_code IN ('ADMIN', 'SUPER_ADMIN')
  AND r.is_deleted = 0
WHERE u.username = 'admin'
  AND u.is_deleted = 0
ON DUPLICATE KEY UPDATE
  status = 1,
  effective_from = NULL,
  effective_to = NULL,
  is_deleted = 0,
  updated_by = 0,
  version = sys_user_role.version + 1;

-- 补齐 SUPER_ADMIN 的全部已启用权限。
INSERT INTO sys_role_permission
  (role_id, permission_id, status, created_by, updated_by, is_deleted, version)
SELECT r.id, p.id, 1, 0, 0, 0, 0
FROM sys_role r
JOIN sys_permission p ON p.status = 1 AND p.is_deleted = 0
WHERE r.role_code = 'SUPER_ADMIN'
  AND r.is_deleted = 0
ON DUPLICATE KEY UPDATE
  status = 1,
  is_deleted = 0,
  updated_by = 0,
  version = sys_role_permission.version + 1;

-- 补齐平台、采购、维保系统入口授权。
INSERT INTO platform_user_app_access
  (user_id, app_code, status, granted_by, granted_at, remark,
   created_by, updated_by, is_deleted, version)
SELECT u.id, app.app_code, 1, 0, NOW(), '平台管理员账号初始化',
       0, 0, 0, 0
FROM sys_user u
JOIN (
  SELECT 'PLATFORM' AS app_code
  UNION ALL SELECT 'PROCUREMENT'
  UNION ALL SELECT 'MAINTENANCE'
) app
WHERE u.username = 'admin'
  AND u.is_deleted = 0
ON DUPLICATE KEY UPDATE
  status = 1,
  granted_by = 0,
  granted_at = NOW(),
  remark = '平台管理员账号初始化',
  is_deleted = 0,
  updated_by = 0,
  version = platform_user_app_access.version + 1;
)
  AND NOT EXISTS (
  SELECT 1 FROM sys_user WHERE username = 'admin' AND is_deleted = 0
);

-- 仅当 admin 密码仍为初始化禁用态时，写入可登录的 BCrypt 初始密码。
UPDATE sys_user
SET password_hash = @admin_initial_password_hash,
    must_change_password = 1,
    password_changed_at = NOW(),
    session_secret = NULL,
    updated_by = 0,
    version = version + 1
WHERE username = 'admin'
  AND is_deleted = 0
  AND password_hash LIKE 'DISABLED-%';

-- 确保最高管理员角色存在。
UPDATE sys_role
SET is_deleted = 0,
    status = 1,
    role_name = '平台超管',
    role_type = 'ADMIN',
    data_scope_type = 'ALL',
    updated_by = 0,
    version = version + 1
WHERE role_code = 'SUPER_ADMIN';

INSERT INTO sys_role
  (role_code, role_name, role_type, data_scope_type, approval_amount_min, approval_amount_max,
   status, description, created_by, updated_by, is_deleted, version)
SELECT 'SUPER_ADMIN', '平台超管', 'ADMIN', 'ALL', NULL, NULL,
       1, '平台最高权限角色', 0, 0, 0, 0
WHERE NOT EXISTS (
  SELECT 1 FROM sys_role WHERE role_code = 'SUPER_ADMIN' AND is_deleted = 0
);

-- 绑定 ADMIN / SUPER_ADMIN 角色。
INSERT INTO sys_user_role
  (user_id, role_id, status, effective_from, effective_to, created_by, updated_by, is_deleted, version)
SELECT u.id, r.id, 1, NULL, NULL, 0, 0, 0, 0
FROM sys_user u
JOIN sys_role r ON r.role_code IN ('ADMIN', 'SUPER_ADMIN')
  AND r.is_deleted = 0
WHERE u.username = 'admin'
  AND u.is_deleted = 0
ON DUPLICATE KEY UPDATE
  status = 1,
  effective_from = NULL,
  effective_to = NULL,
  is_deleted = 0,
  updated_by = 0,
  version = sys_user_role.version + 1;

-- 补齐 SUPER_ADMIN 的全部已启用权限。
INSERT INTO sys_role_permission
  (role_id, permission_id, status, created_by, updated_by, is_deleted, version)
SELECT r.id, p.id, 1, 0, 0, 0, 0
FROM sys_role r
JOIN sys_permission p ON p.status = 1 AND p.is_deleted = 0
WHERE r.role_code = 'SUPER_ADMIN'
  AND r.is_deleted = 0
ON DUPLICATE KEY UPDATE
  status = 1,
  is_deleted = 0,
  updated_by = 0,
  version = sys_role_permission.version + 1;

-- 补齐平台、采购、维保系统入口授权。
INSERT INTO platform_user_app_access
  (user_id, app_code, status, granted_by, granted_at, remark,
   created_by, updated_by, is_deleted, version)
SELECT u.id, app.app_code, 1, 0, NOW(), '平台管理员账号初始化',
       0, 0, 0, 0
FROM sys_user u
JOIN (
  SELECT 'PLATFORM' AS app_code
  UNION ALL SELECT 'PROCUREMENT'
  UNION ALL SELECT 'MAINTENANCE'
) app
WHERE u.username = 'admin'
  AND u.is_deleted = 0
ON DUPLICATE KEY UPDATE
  status = 1,
  granted_by = 0,
  granted_at = NOW(),
  remark = '平台管理员账号初始化',
  is_deleted = 0,
  updated_by = 0,
  version = platform_user_app_access.version + 1;
