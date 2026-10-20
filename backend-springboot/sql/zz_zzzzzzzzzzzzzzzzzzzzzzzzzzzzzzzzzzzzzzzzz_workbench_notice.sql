SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS workbench_notice (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  title VARCHAR(100) NOT NULL COMMENT '公告标题',
  content VARCHAR(2000) NOT NULL COMMENT '公告内容',
  pinned TINYINT NOT NULL DEFAULT 0 COMMENT '是否置顶',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
  sort_no INT NOT NULL DEFAULT 100 COMMENT '排序',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  created_by BIGINT NULL,
  updated_by BIGINT NULL,
  is_deleted TINYINT NOT NULL DEFAULT 0,
  version INT NOT NULL DEFAULT 0,
  KEY idx_workbench_notice_status (status, is_deleted),
  KEY idx_workbench_notice_order (pinned, sort_no, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作台公告';

INSERT INTO sys_permission
  (perm_code, perm_name, module_code, perm_type, parent_id, path, http_method, sort_no, status, description, created_by, updated_by, is_deleted, version)
SELECT 'workbench_notice:manage', '工作台公告维护', 'WORKBENCH', 'BUTTON', NULL, '/api/workbench/notices/manage/**', '*', 92, 1, '维护工作台公告栏内容、排序、启停和置顶', 0, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'workbench_notice:manage' AND is_deleted = 0);

INSERT INTO sys_role_permission (role_id, permission_id, status, created_by, updated_by, is_deleted, version)
SELECT r.id, p.id, 1, 0, 0, 0, 0
FROM sys_role r
JOIN sys_permission p ON p.perm_code = 'workbench_notice:manage' AND p.is_deleted = 0
WHERE r.role_code IN ('ADMIN', 'SYS_ADMIN', 'SUPER_ADMIN')
  AND r.is_deleted = 0
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permission existing_perm
    WHERE existing_perm.role_id = r.id
      AND existing_perm.permission_id = p.id
      AND existing_perm.is_deleted = 0
  );

INSERT INTO workbench_notice
  (title, content, pinned, status, sort_no, created_by, updated_by, is_deleted, version)
SELECT '采购流程提醒', '月度采购、供应商报价和中标确认的关键时间请以页面提示为准；如遇驳回，可按消息提醒重新提交采购需求。', 1, 1, 10, 0, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM workbench_notice WHERE is_deleted = 0);
