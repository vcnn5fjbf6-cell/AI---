SET NAMES utf8mb4;

-- Manual business-data reset.
-- Keep master/configuration data:
--   sys_region, sys_user, sys_role, sys_permission, sys_user_role,
--   sys_role_permission, supplier, supplier_qualification,
--   platform_unit, platform_unit_app_scope, platform_user_app_access,
--   procurement_setting, procurement_department_limit,
--   procurement_approval_route, org_*, sys_user_subject, sys_user_scope,
--   sys_role_assignment, procurement_approval_policy/level/relation.
--
-- Clear transactional/runtime data:
--   purchase requests, request items, approvals/tasks, supplier quotes,
--   procurement after-sale records, monthly demand pool, AI supplement
--   sessions/candidates, order invoice submissions/attachments,
--   procurement message read states, and procurement
--   source workbench follow/copy rows.
--
-- This script is intentionally outside auto-applied migration folders.

DELIMITER $$

DROP PROCEDURE IF EXISTS truncate_if_exists$$
DROP PROCEDURE IF EXISTS delete_procurement_workbench_rows_if_exists$$

CREATE PROCEDURE truncate_if_exists(IN p_table_name VARCHAR(128))
BEGIN
  IF EXISTS (
    SELECT 1
    FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = p_table_name
  ) THEN
    SET @truncate_sql = CONCAT('TRUNCATE TABLE `', REPLACE(p_table_name, '`', '``'), '`');
    PREPARE truncate_stmt FROM @truncate_sql;
    EXECUTE truncate_stmt;
    DEALLOCATE PREPARE truncate_stmt;
    SELECT CONCAT('TRUNCATED ', p_table_name) AS reset_action;
  ELSE
    SELECT CONCAT('SKIPPED ', p_table_name, ' (table not found)') AS reset_action;
  END IF;
END$$

CREATE PROCEDURE delete_procurement_workbench_rows_if_exists(IN p_table_name VARCHAR(128))
BEGIN
  IF EXISTS (
    SELECT 1
    FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = p_table_name
  ) THEN
    SET @delete_sql = CONCAT(
      'DELETE FROM `', REPLACE(p_table_name, '`', '``'),
      '` WHERE source_type IN (''PURCHASE'', ''MONTHLY'', ''SUPPLIER'', ''AFTER_SALE'')'
    );
    PREPARE delete_stmt FROM @delete_sql;
    EXECUTE delete_stmt;
    DEALLOCATE PREPARE delete_stmt;
    SELECT CONCAT('DELETED procurement workbench rows from ', p_table_name) AS reset_action;
  ELSE
    SELECT CONCAT('SKIPPED ', p_table_name, ' (table not found)') AS reset_action;
  END IF;
END$$

DELIMITER ;

SET FOREIGN_KEY_CHECKS = 0;

CALL truncate_if_exists('order_invoice_attachment');
CALL truncate_if_exists('order_invoice_submission');
CALL truncate_if_exists('procurement_after_sale');
CALL truncate_if_exists('supplier_quote_item');
CALL truncate_if_exists('supplier_quote');
CALL truncate_if_exists('approval_task');
CALL truncate_if_exists('approval_record');
CALL truncate_if_exists('approval_instance');
CALL truncate_if_exists('purchase_request_item');
CALL truncate_if_exists('purchase_request');
CALL truncate_if_exists('monthly_procurement_demand');
CALL truncate_if_exists('procurement_ai_candidate');
CALL truncate_if_exists('procurement_ai_session');
CALL truncate_if_exists('procurement_message_read');
CALL delete_procurement_workbench_rows_if_exists('workbench_follow');
CALL delete_procurement_workbench_rows_if_exists('workbench_copy');
CALL truncate_if_exists('purchase_request_no_sequence');

SET FOREIGN_KEY_CHECKS = 1;

SET @reset_sequence_sql = (
  SELECT IF(
    EXISTS (
      SELECT 1
      FROM information_schema.TABLES
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'purchase_request_no_sequence'
    ),
    'INSERT INTO purchase_request_no_sequence(prefix, next_no) VALUES (''PTCG'', 100000), (''JJCG'', 100000), (''HZCG'', 0) ON DUPLICATE KEY UPDATE next_no = VALUES(next_no)',
    'SELECT ''SKIPPED purchase_request_no_sequence reset (table not found)'' AS reset_action'
  )
);

PREPARE reset_sequence_stmt FROM @reset_sequence_sql;
EXECUTE reset_sequence_stmt;
DEALLOCATE PREPARE reset_sequence_stmt;

DROP PROCEDURE IF EXISTS truncate_if_exists;
DROP PROCEDURE IF EXISTS delete_procurement_workbench_rows_if_exists;

SELECT 'BUSINESS_DATA_RESET_DONE' AS reset_status;
