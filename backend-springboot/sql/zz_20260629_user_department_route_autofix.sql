CREATE TABLE IF NOT EXISTS sys_department (
  id BIGINT NOT NULL AUTO_INCREMENT,
  code VARCHAR(64) NOT NULL COMMENT '标准部门编码',
  full_name VARCHAR(255) NOT NULL COMMENT '部门全称',
  short_name VARCHAR(128) NULL COMMENT '部门简称',
  dept_type VARCHAR(32) NOT NULL COMMENT '部门层级类型',
  parent_id BIGINT NULL COMMENT '上级部门ID',
  sort_order INT NOT NULL DEFAULT 0 COMMENT '排序',
  is_active TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用',
  remark VARCHAR(500) NULL COMMENT '备注',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  created_by BIGINT NOT NULL DEFAULT 0,
  updated_by BIGINT NOT NULL DEFAULT 0,
  is_deleted TINYINT NOT NULL DEFAULT 0,
  version INT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_sys_dept_code (code),
  KEY idx_sys_dept_parent (parent_id),
  KEY idx_sys_dept_type (dept_type),
  KEY idx_sys_dept_active (is_active),
  KEY idx_sys_dept_deleted (is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='标准部门字典';

CREATE TABLE IF NOT EXISTS sys_user_dept_route_fix_bak_20260629 (
  user_id BIGINT NOT NULL,
  username VARCHAR(64) NULL,
  real_name VARCHAR(64) NULL,
  employee_no VARCHAR(64) NULL,
  old_region_id BIGINT NULL,
  old_dept_code VARCHAR(128) NULL,
  old_dept_name VARCHAR(255) NULL,
  backed_up_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户部门路由自动纠正前备份';

INSERT IGNORE INTO sys_user_dept_route_fix_bak_20260629
  (user_id, username, real_name, employee_no, old_region_id, old_dept_code, old_dept_name)
SELECT id, username, real_name, employee_no, region_id, dept_code, dept_name
FROM sys_user
WHERE is_deleted = 0;

DROP TEMPORARY TABLE IF EXISTS tmp_user_dept_route_best;
CREATE TEMPORARY TABLE tmp_user_dept_route_best AS
SELECT user_id, matched_dept_code, matched_dept_name
FROM (
  SELECT c.*,
         ROW_NUMBER() OVER (PARTITION BY c.user_id ORDER BY c.priority ASC, c.specificity DESC, c.route_id ASC) AS rn,
         COUNT(*) OVER (PARTITION BY c.user_id, c.priority, c.specificity) AS tie_count
  FROM (
    SELECT
      u.id AS user_id,
      r.id AS route_id,
      COALESCE(NULLIF(r.dept_code, ''), NULLIF(r.cost_center_code, ''), NULLIF(r.route_key, '')) AS matched_dept_code,
      COALESCE(NULLIF(r.dept_name, ''), NULLIF(CONCAT_WS('-', NULLIF(r.room_name, ''), NULLIF(r.building_name, '')), '')) AS matched_dept_name,
      CASE
        WHEN NULLIF(u.dept_code, '') IS NOT NULL AND NULLIF(u.dept_code, '') IN (NULLIF(r.dept_code, ''), NULLIF(r.cost_center_code, '')) THEN 1
        WHEN NULLIF(u.dept_code, '') IS NOT NULL AND NULLIF(u.dept_code, '') = NULLIF(r.route_key, '') THEN 2
        WHEN NULLIF(u.dept_name, '') IS NOT NULL AND NULLIF(u.dept_name, '') = NULLIF(r.dept_name, '') THEN 3
        WHEN NULLIF(u.dept_name, '') IS NOT NULL AND NULLIF(u.dept_name, '') = NULLIF(r.building_name, '') THEN 4
        WHEN NULLIF(u.dept_name, '') IS NOT NULL AND CHAR_LENGTH(COALESCE(NULLIF(r.dept_name, ''), '')) >= 6 AND u.dept_name LIKE CONCAT('%', r.dept_name, '%') THEN 5
        WHEN NULLIF(u.dept_name, '') IS NOT NULL AND NULLIF(r.cost_center_code, '') IS NOT NULL AND u.dept_name LIKE CONCAT('%', r.cost_center_code, '%') THEN 6
        WHEN NULLIF(u.dept_name, '') IS NOT NULL AND NULLIF(r.building_name, '') IS NOT NULL AND u.dept_name LIKE CONCAT('%', r.building_name, '%') THEN 7
        ELSE 8
      END AS priority,
      CASE
        WHEN NULLIF(u.dept_code, '') IS NOT NULL AND NULLIF(u.dept_code, '') IN (NULLIF(r.dept_code, ''), NULLIF(r.cost_center_code, '')) THEN GREATEST(CHAR_LENGTH(COALESCE(NULLIF(r.dept_code, ''), '')), CHAR_LENGTH(COALESCE(NULLIF(r.cost_center_code, ''), '')))
        WHEN NULLIF(u.dept_code, '') IS NOT NULL AND NULLIF(u.dept_code, '') = NULLIF(r.route_key, '') THEN CHAR_LENGTH(r.route_key)
        WHEN NULLIF(u.dept_name, '') IS NOT NULL AND NULLIF(u.dept_name, '') = NULLIF(r.dept_name, '') THEN CHAR_LENGTH(r.dept_name)
        WHEN NULLIF(u.dept_name, '') IS NOT NULL AND NULLIF(u.dept_name, '') = NULLIF(r.building_name, '') THEN CHAR_LENGTH(r.building_name)
        WHEN NULLIF(u.dept_name, '') IS NOT NULL AND CHAR_LENGTH(COALESCE(NULLIF(r.dept_name, ''), '')) >= 6 AND u.dept_name LIKE CONCAT('%', r.dept_name, '%') THEN CHAR_LENGTH(r.dept_name)
        WHEN NULLIF(u.dept_name, '') IS NOT NULL AND NULLIF(r.cost_center_code, '') IS NOT NULL AND u.dept_name LIKE CONCAT('%', r.cost_center_code, '%') THEN CHAR_LENGTH(r.cost_center_code)
        WHEN NULLIF(u.dept_name, '') IS NOT NULL AND NULLIF(r.building_name, '') IS NOT NULL AND u.dept_name LIKE CONCAT('%', r.building_name, '%') THEN CHAR_LENGTH(r.building_name)
        ELSE CHAR_LENGTH(COALESCE(NULLIF(r.room_name, ''), ''))
      END AS specificity
    FROM sys_user u
    JOIN procurement_approval_route r
      ON r.is_deleted = 0
     AND r.enabled = 1
     AND (
       (NULLIF(u.dept_code, '') IS NOT NULL AND NULLIF(u.dept_code, '') IN (NULLIF(r.dept_code, ''), NULLIF(r.cost_center_code, ''), NULLIF(r.route_key, '')))
       OR (NULLIF(u.dept_name, '') IS NOT NULL AND NULLIF(u.dept_name, '') IN (NULLIF(r.dept_name, ''), NULLIF(r.building_name, '')))
       OR (NULLIF(u.dept_name, '') IS NOT NULL AND CHAR_LENGTH(COALESCE(NULLIF(r.dept_name, ''), '')) >= 6 AND u.dept_name LIKE CONCAT('%', r.dept_name, '%'))
       OR (NULLIF(u.dept_name, '') IS NOT NULL AND NULLIF(r.cost_center_code, '') IS NOT NULL AND u.dept_name LIKE CONCAT('%', r.cost_center_code, '%'))
       OR (NULLIF(u.dept_name, '') IS NOT NULL AND NULLIF(r.building_name, '') IS NOT NULL AND u.dept_name LIKE CONCAT('%', r.building_name, '%'))
       OR (NULLIF(u.dept_name, '') IS NOT NULL AND NULLIF(r.room_name, '') IS NOT NULL AND u.dept_name LIKE CONCAT('%', r.room_name, '%'))
     )
    WHERE u.is_deleted = 0
      AND (NULLIF(u.dept_code, '') IS NOT NULL OR NULLIF(u.dept_name, '') IS NOT NULL)
  ) c
) ranked
WHERE rn = 1
  AND tie_count = 1
  AND matched_dept_code IS NOT NULL
  AND matched_dept_name IS NOT NULL;

UPDATE sys_user u
JOIN tmp_user_dept_route_best b ON b.user_id = u.id
SET u.dept_code = b.matched_dept_code,
    u.dept_name = b.matched_dept_name,
    u.updated_at = NOW(),
    u.updated_by = COALESCE(u.updated_by, 0),
    u.version = u.version + 1
WHERE u.is_deleted = 0
  AND (COALESCE(u.dept_code, '') <> b.matched_dept_code OR COALESCE(u.dept_name, '') <> b.matched_dept_name);

DROP TEMPORARY TABLE IF EXISTS tmp_user_dept_dict_best;
CREATE TEMPORARY TABLE tmp_user_dept_dict_best AS
SELECT user_id, code, full_name, short_name
FROM (
  SELECT c.*,
         ROW_NUMBER() OVER (PARTITION BY c.user_id ORDER BY c.priority ASC, c.specificity DESC, c.department_id ASC) AS rn,
         COUNT(*) OVER (PARTITION BY c.user_id, c.priority, c.specificity) AS tie_count
  FROM (
    SELECT
      u.id AS user_id,
      d.id AS department_id,
      d.code,
      d.full_name,
      d.short_name,
      CASE
        WHEN NULLIF(u.dept_code, '') IS NOT NULL AND NULLIF(u.dept_code, '') = NULLIF(d.code, '') THEN 1
        WHEN NULLIF(u.dept_name, '') IS NOT NULL AND NULLIF(u.dept_name, '') = NULLIF(d.full_name, '') THEN 2
        WHEN NULLIF(u.dept_name, '') IS NOT NULL AND NULLIF(u.dept_name, '') = NULLIF(d.short_name, '') THEN 3
        WHEN NULLIF(u.dept_name, '') IS NOT NULL AND CHAR_LENGTH(COALESCE(NULLIF(d.full_name, ''), '')) >= 6 AND u.dept_name LIKE CONCAT('%', d.full_name, '%') THEN 4
        ELSE 5
      END AS priority,
      CASE
        WHEN NULLIF(u.dept_code, '') IS NOT NULL AND NULLIF(u.dept_code, '') = NULLIF(d.code, '') THEN CHAR_LENGTH(d.code)
        WHEN NULLIF(u.dept_name, '') IS NOT NULL AND NULLIF(u.dept_name, '') = NULLIF(d.full_name, '') THEN CHAR_LENGTH(d.full_name)
        WHEN NULLIF(u.dept_name, '') IS NOT NULL AND NULLIF(u.dept_name, '') = NULLIF(d.short_name, '') THEN CHAR_LENGTH(d.short_name)
        WHEN NULLIF(u.dept_name, '') IS NOT NULL AND CHAR_LENGTH(COALESCE(NULLIF(d.full_name, ''), '')) >= 6 AND u.dept_name LIKE CONCAT('%', d.full_name, '%') THEN CHAR_LENGTH(d.full_name)
        ELSE CHAR_LENGTH(COALESCE(NULLIF(d.short_name, ''), ''))
      END AS specificity
    FROM sys_user u
    JOIN sys_department d
      ON d.is_deleted = 0
     AND COALESCE(d.is_active, 1) = 1
     AND (
       (NULLIF(u.dept_code, '') IS NOT NULL AND NULLIF(u.dept_code, '') = NULLIF(d.code, ''))
       OR (NULLIF(u.dept_name, '') IS NOT NULL AND NULLIF(u.dept_name, '') IN (NULLIF(d.full_name, ''), NULLIF(d.short_name, '')))
       OR (NULLIF(u.dept_name, '') IS NOT NULL AND CHAR_LENGTH(COALESCE(NULLIF(d.full_name, ''), '')) >= 6 AND u.dept_name LIKE CONCAT('%', d.full_name, '%'))
       OR (NULLIF(u.dept_name, '') IS NOT NULL AND NULLIF(d.short_name, '') IS NOT NULL AND u.dept_name LIKE CONCAT('%', d.short_name, '%'))
     )
    WHERE u.is_deleted = 0
      AND NOT EXISTS (SELECT 1 FROM tmp_user_dept_route_best rb WHERE rb.user_id = u.id)
      AND (NULLIF(u.dept_code, '') IS NOT NULL OR NULLIF(u.dept_name, '') IS NOT NULL)
  ) c
) ranked
WHERE rn = 1
  AND tie_count = 1;

DROP TEMPORARY TABLE IF EXISTS tmp_user_dept_dict_route_best;
CREATE TEMPORARY TABLE tmp_user_dept_dict_route_best AS
SELECT user_id, matched_dept_code, matched_dept_name
FROM (
  SELECT c.*,
         ROW_NUMBER() OVER (PARTITION BY c.user_id ORDER BY c.priority ASC, c.specificity DESC, c.route_id ASC) AS rn,
         COUNT(*) OVER (PARTITION BY c.user_id, c.priority, c.specificity) AS tie_count
  FROM (
    SELECT
      d.user_id,
      r.id AS route_id,
      COALESCE(NULLIF(r.dept_code, ''), NULLIF(r.cost_center_code, ''), NULLIF(r.route_key, '')) AS matched_dept_code,
      COALESCE(NULLIF(r.dept_name, ''), NULLIF(CONCAT_WS('-', NULLIF(r.room_name, ''), NULLIF(r.building_name, '')), '')) AS matched_dept_name,
      CASE
        WHEN NULLIF(d.code, '') IS NOT NULL AND NULLIF(d.code, '') IN (NULLIF(r.dept_code, ''), NULLIF(r.cost_center_code, ''), NULLIF(r.route_key, '')) THEN 1
        WHEN NULLIF(d.full_name, '') IS NOT NULL AND NULLIF(d.full_name, '') = NULLIF(r.dept_name, '') THEN 2
        WHEN NULLIF(d.short_name, '') IS NOT NULL AND NULLIF(d.short_name, '') = NULLIF(r.building_name, '') THEN 3
        WHEN NULLIF(d.full_name, '') IS NOT NULL AND CHAR_LENGTH(COALESCE(NULLIF(r.dept_name, ''), '')) >= 6 AND d.full_name LIKE CONCAT('%', r.dept_name, '%') THEN 4
        WHEN NULLIF(d.full_name, '') IS NOT NULL AND NULLIF(r.cost_center_code, '') IS NOT NULL AND d.full_name LIKE CONCAT('%', r.cost_center_code, '%') THEN 5
        WHEN NULLIF(d.full_name, '') IS NOT NULL AND NULLIF(r.building_name, '') IS NOT NULL AND d.full_name LIKE CONCAT('%', r.building_name, '%') THEN 6
        ELSE 7
      END AS priority,
      CASE
        WHEN NULLIF(d.code, '') IS NOT NULL AND NULLIF(d.code, '') IN (NULLIF(r.dept_code, ''), NULLIF(r.cost_center_code, ''), NULLIF(r.route_key, '')) THEN GREATEST(CHAR_LENGTH(COALESCE(NULLIF(r.dept_code, ''), '')), CHAR_LENGTH(COALESCE(NULLIF(r.cost_center_code, ''), '')), CHAR_LENGTH(COALESCE(NULLIF(r.route_key, ''), '')))
        WHEN NULLIF(d.full_name, '') IS NOT NULL AND NULLIF(d.full_name, '') = NULLIF(r.dept_name, '') THEN CHAR_LENGTH(r.dept_name)
        WHEN NULLIF(d.short_name, '') IS NOT NULL AND NULLIF(d.short_name, '') = NULLIF(r.building_name, '') THEN CHAR_LENGTH(r.building_name)
        WHEN NULLIF(d.full_name, '') IS NOT NULL AND CHAR_LENGTH(COALESCE(NULLIF(r.dept_name, ''), '')) >= 6 AND d.full_name LIKE CONCAT('%', r.dept_name, '%') THEN CHAR_LENGTH(r.dept_name)
        WHEN NULLIF(d.full_name, '') IS NOT NULL AND NULLIF(r.cost_center_code, '') IS NOT NULL AND d.full_name LIKE CONCAT('%', r.cost_center_code, '%') THEN CHAR_LENGTH(r.cost_center_code)
        WHEN NULLIF(d.full_name, '') IS NOT NULL AND NULLIF(r.building_name, '') IS NOT NULL AND d.full_name LIKE CONCAT('%', r.building_name, '%') THEN CHAR_LENGTH(r.building_name)
        ELSE CHAR_LENGTH(COALESCE(NULLIF(r.room_name, ''), ''))
      END AS specificity
    FROM tmp_user_dept_dict_best d
    JOIN procurement_approval_route r
      ON r.is_deleted = 0
     AND r.enabled = 1
     AND (
       (NULLIF(d.code, '') IS NOT NULL AND NULLIF(d.code, '') IN (NULLIF(r.dept_code, ''), NULLIF(r.cost_center_code, ''), NULLIF(r.route_key, '')))
       OR (NULLIF(d.full_name, '') IS NOT NULL AND NULLIF(d.full_name, '') = NULLIF(r.dept_name, ''))
       OR (NULLIF(d.short_name, '') IS NOT NULL AND NULLIF(d.short_name, '') = NULLIF(r.building_name, ''))
       OR (NULLIF(d.full_name, '') IS NOT NULL AND CHAR_LENGTH(COALESCE(NULLIF(r.dept_name, ''), '')) >= 6 AND d.full_name LIKE CONCAT('%', r.dept_name, '%'))
       OR (NULLIF(d.full_name, '') IS NOT NULL AND NULLIF(r.cost_center_code, '') IS NOT NULL AND d.full_name LIKE CONCAT('%', r.cost_center_code, '%'))
       OR (NULLIF(d.full_name, '') IS NOT NULL AND NULLIF(r.building_name, '') IS NOT NULL AND d.full_name LIKE CONCAT('%', r.building_name, '%'))
       OR (NULLIF(d.full_name, '') IS NOT NULL AND NULLIF(r.room_name, '') IS NOT NULL AND d.full_name LIKE CONCAT('%', r.room_name, '%'))
     )
  ) c
) ranked
WHERE rn = 1
  AND tie_count = 1
  AND matched_dept_code IS NOT NULL
  AND matched_dept_name IS NOT NULL;

UPDATE sys_user u
JOIN tmp_user_dept_dict_route_best b ON b.user_id = u.id
SET u.dept_code = b.matched_dept_code,
    u.dept_name = b.matched_dept_name,
    u.updated_at = NOW(),
    u.updated_by = COALESCE(u.updated_by, 0),
    u.version = u.version + 1
WHERE u.is_deleted = 0
  AND (COALESCE(u.dept_code, '') <> b.matched_dept_code OR COALESCE(u.dept_name, '') <> b.matched_dept_name);

DROP TEMPORARY TABLE IF EXISTS tmp_user_dept_dict_route_best;
DROP TEMPORARY TABLE IF EXISTS tmp_user_dept_dict_best;
DROP TEMPORARY TABLE IF EXISTS tmp_user_dept_route_best;
