INSERT INTO sys_permission
(perm_code, perm_name, module_code, perm_type, parent_id, path, http_method, sort_no, status, description, created_by, updated_by, is_deleted, version)
SELECT 'procurement:demand-pool-publish', '需求池月度推送', 'PROCUREMENT', 'BUTTON', NULL, '/api/workflow/demand-pool/publish', 'POST', 170, 1, '按配置日期将需求池按机房汇总后推送经理审核', 0, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'procurement:demand-pool-publish' AND is_deleted = 0);

INSERT INTO sys_permission
(perm_code, perm_name, module_code, perm_type, parent_id, path, http_method, sort_no, status, description, created_by, updated_by, is_deleted, version)
SELECT 'procurement:demand-pool-publish-test', '需求池测试推送', 'PROCUREMENT', 'BUTTON', NULL, '/api/workflow/demand-pool/publish-test', 'POST', 171, 1, '测试专用：跳过日期限制，将需求池按机房汇总后推送经理审核', 0, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'procurement:demand-pool-publish-test' AND is_deleted = 0);

INSERT INTO sys_role_permission (role_id, permission_id, status, created_by, updated_by, is_deleted, version)
SELECT r.id, p.id, 1, 0, 0, 0, 0
FROM sys_role r
JOIN sys_permission p ON p.perm_code = 'procurement:demand-pool-publish' AND p.is_deleted = 0
WHERE r.role_code IN ('ADMIN', 'SYS_ADMIN', 'SUPER_ADMIN', 'PURCHASER')
  AND r.is_deleted = 0
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permission rp
    WHERE rp.role_id = r.id AND rp.permission_id = p.id AND rp.is_deleted = 0
  );

INSERT INTO sys_role_permission (role_id, permission_id, status, created_by, updated_by, is_deleted, version)
SELECT r.id, p.id, 1, 0, 0, 0, 0
FROM sys_role r
JOIN sys_permission p ON p.perm_code = 'procurement:demand-pool-publish-test' AND p.is_deleted = 0
WHERE r.role_code IN ('ADMIN', 'SYS_ADMIN', 'SUPER_ADMIN')
  AND r.is_deleted = 0
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permission rp
    WHERE rp.role_id = r.id AND rp.permission_id = p.id AND rp.is_deleted = 0
  );
