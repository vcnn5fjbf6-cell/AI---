SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS procurement_approval_route (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'primary id',
  enabled TINYINT NOT NULL DEFAULT 1 COMMENT '1 enabled 0 disabled',
  route_key VARCHAR(255) NOT NULL COMMENT 'room/building/cost center route key',
  room_nature VARCHAR(64) NULL COMMENT 'room nature',
  room_name VARCHAR(128) NOT NULL COMMENT 'room name',
  building_name VARCHAR(128) NOT NULL COMMENT 'building name',
  cost_center_code VARCHAR(64) NULL COMMENT 'cost center code',
  dept_code VARCHAR(64) NULL COMMENT 'department code',
  dept_name VARCHAR(255) NULL COMMENT 'department name',
  floor_leader_names VARCHAR(255) NOT NULL COMMENT 'floor leader candidates, comma separated',
  floor_leader_rule VARCHAR(64) NOT NULL DEFAULT 'ANY_ONE' COMMENT 'floor leader pass rule',
  manager_names VARCHAR(255) NOT NULL COMMENT 'manager candidates, comma separated',
  manager_rule VARCHAR(64) NOT NULL DEFAULT 'ANY_ONE' COMMENT 'manager pass rule',
  assistant_user_ids VARCHAR(500) NULL COMMENT 'assistant user ids, comma separated',
  assistant_names VARCHAR(500) NULL COMMENT 'assistant names, comma separated',
  region_name VARCHAR(128) NULL COMMENT 'region name',
  region_manager_names VARCHAR(255) NULL COMMENT 'region manager candidates',
  region_manager_rule VARCHAR(64) NOT NULL DEFAULT 'ALL' COMMENT 'region manager pass rule',
  director_names VARCHAR(255) NULL COMMENT 'director candidates',
  director_rule VARCHAR(64) NOT NULL DEFAULT 'ALL' COMMENT 'director pass rule',
  pool_push_day INT NOT NULL DEFAULT 20 COMMENT 'monthly pool push day',
  region_threshold DECIMAL(18,2) NOT NULL DEFAULT 5000.00 COMMENT 'region manager threshold',
  director_threshold DECIMAL(18,2) NOT NULL DEFAULT 100000.00 COMMENT 'director threshold',
  over_price_rule VARCHAR(255) NOT NULL DEFAULT 'ITEM_ACTUAL_GT_REFERENCE_110' COMMENT 'over price review rule',
  replacement_confirm_names VARCHAR(255) NULL COMMENT 'replacement confirmation candidates',
  supplier_quote_scope VARCHAR(255) NULL COMMENT 'supplier quote data scope',
  remark VARCHAR(500) NULL COMMENT 'remark',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated time',
  created_by BIGINT NOT NULL DEFAULT 0 COMMENT 'created by',
  updated_by BIGINT NOT NULL DEFAULT 0 COMMENT 'updated by',
  is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT 'logical delete flag',
  version INT NOT NULL DEFAULT 0 COMMENT 'version',
  UNIQUE KEY uk_procurement_approval_route_key (route_key),
  KEY idx_procurement_approval_route_room (room_name, building_name),
  KEY idx_procurement_approval_route_region (region_name),
  KEY idx_procurement_approval_route_enabled (enabled),
  KEY idx_procurement_approval_route_deleted (is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='procurement approval route configuration';

INSERT INTO procurement_approval_route
  (enabled, route_key, room_nature, room_name, building_name, cost_center_code, dept_code, dept_name,
   floor_leader_names, floor_leader_rule, manager_names, manager_rule, region_name,
   region_manager_names, region_manager_rule, director_names, director_rule, pool_push_day,
   region_threshold, director_threshold, over_price_rule, replacement_confirm_names,
   supplier_quote_scope, remark, created_by, updated_by, is_deleted, version)
SELECT
  1,
  '酒仙桥|M6|N420N00077',
  '自建',
  '酒仙桥',
  'M6',
  'N420N00077',
  '224',
  '[N420N00077]世纪互联-业务共享中台-DC运维事业部-华北一部-酒仙桥-M6',
  '赵新,马金礼',
  '任意一人通过',
  '张强,刘燚',
  '任意一人通过',
  '华北一',
  '王郑建',
  '默认全部通过/可改任意一人',
  '杨磊',
  '默认全部通过/可改任意一人',
  20,
  5000.00,
  100000.00,
  '单项实际价 > 参考价 * 110%',
  '赵新,马金礼',
  '仅可见推送给自己的商品明细',
  'M6示例：楼长赵新/马金礼，经理张强/刘燚，区域经理王郑建，总监杨磊',
  0,
  0,
  0,
  0
WHERE NOT EXISTS (
  SELECT 1 FROM procurement_approval_route
  WHERE route_key = '酒仙桥|M6|N420N00077' AND is_deleted = 0
);

UPDATE procurement_setting
SET setting_value = '20',
    updated_at = NOW()
WHERE setting_key = 'monthly_aggregate_day'
  AND setting_value IN ('', '25');

INSERT INTO sys_permission
  (perm_code, perm_name, module_code, perm_type, parent_id, path, http_method, sort_no, status, description, created_by, updated_by, is_deleted, version)
SELECT 'procurement_route:manage', '采购审批路由配置', 'PROCUREMENT_ROUTE', 'BUTTON', NULL, '/api/admin/procurement/approval-routes/**', '*', 99, 1, '维护机房、楼栋、楼长、经理、区域经理、总监审批路由', 0, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'procurement_route:manage' AND is_deleted = 0);

INSERT INTO sys_role_permission (role_id, permission_id, status, created_by, updated_by, is_deleted, version)
SELECT r.id, p.id, 1, 0, 0, 0, 0
FROM sys_role r
JOIN sys_permission p ON p.perm_code = 'procurement_route:manage' AND p.is_deleted = 0
WHERE r.role_code IN ('ADMIN', 'SYS_ADMIN', 'SUPER_ADMIN')
  AND r.is_deleted = 0
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permission rp
    WHERE rp.role_id = r.id
      AND rp.permission_id = p.id
      AND rp.is_deleted = 0
  );
