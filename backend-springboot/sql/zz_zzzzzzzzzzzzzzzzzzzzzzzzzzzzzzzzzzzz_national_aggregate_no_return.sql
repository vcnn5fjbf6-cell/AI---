-- 系统发起的全国月度需求汇总单不允许退回。
-- 修复已经被误退回的历史流程：撤销退回记录，并回到被退回前的最高审批节点继续处理。

DROP TEMPORARY TABLE IF EXISTS tmp_national_aggregate_return_repair;
CREATE TEMPORARY TABLE tmp_national_aggregate_return_repair AS
SELECT pr.id AS request_id, MAX(r.node_no) AS repair_node_no
FROM purchase_request pr
JOIN approval_record r ON r.purchase_request_id = pr.id
  AND r.is_deleted = 0
  AND r.status IN ('ACTIVE', 'RETURN_DISABLED_REPAIRED')
  AND r.action = 'RETURN'
  AND r.node_no > 0
WHERE pr.is_deleted = 0
  AND pr.req_reason = '需求池全国月度汇总生成'
GROUP BY pr.id;

DROP TEMPORARY TABLE IF EXISTS tmp_national_aggregate_repair_task;
CREATE TEMPORARY TABLE tmp_national_aggregate_repair_task AS
SELECT t.*
FROM approval_task t
JOIN tmp_national_aggregate_return_repair rep
  ON rep.request_id = t.purchase_request_id
 AND rep.repair_node_no = t.level_no
WHERE t.is_deleted = 0
  AND t.status IN ('RETURNED', 'PENDING');

DROP TEMPORARY TABLE IF EXISTS tmp_national_aggregate_repair_pick;
CREATE TEMPORARY TABLE tmp_national_aggregate_repair_pick AS
SELECT t.instance_id, MIN(t.id) AS task_id
FROM approval_task t
JOIN tmp_national_aggregate_return_repair rep
  ON rep.request_id = t.purchase_request_id
 AND rep.repair_node_no = t.level_no
WHERE t.is_deleted = 0
  AND t.status IN ('RETURNED', 'PENDING')
GROUP BY t.instance_id;

DROP TEMPORARY TABLE IF EXISTS tmp_national_aggregate_repair_current;
CREATE TEMPORARY TABLE tmp_national_aggregate_repair_current AS
SELECT t.instance_id,
       t.purchase_request_id,
       t.level_code,
       t.pass_rule,
       t.candidate_user_id
FROM approval_task t
JOIN tmp_national_aggregate_repair_pick picked ON picked.task_id = t.id;

UPDATE approval_record r
JOIN tmp_national_aggregate_return_repair rep ON rep.request_id = r.purchase_request_id
SET r.status = 'RETURN_DISABLED_REPAIRED',
    r.remark = CONCAT_WS('；', NULLIF(r.remark, ''), '系统发起的全国月度汇总单不支持退回，发布修正隐藏此记录'),
    r.updated_at = NOW()
WHERE r.is_deleted = 0
  AND r.status = 'ACTIVE'
  AND r.action = 'RETURN'
  AND r.node_no > 0;

UPDATE approval_task t
JOIN tmp_national_aggregate_return_repair rep ON rep.request_id = t.purchase_request_id
SET t.status = 'AUTO_CLOSED',
    t.closed_reason = 'RETURN_DISABLED_REPAIRED',
    t.updated_at = NOW()
WHERE t.is_deleted = 0
  AND t.status = 'RETURNED'
  AND t.level_no <> rep.repair_node_no;

UPDATE approval_task t
JOIN tmp_national_aggregate_repair_task picked ON picked.id = t.id
SET t.status = 'PENDING',
    t.action = NULL,
    t.action_comment = NULL,
    t.action_at = NULL,
    t.closed_reason = NULL,
    t.updated_at = NOW()
WHERE t.is_deleted = 0;

UPDATE purchase_request pr
JOIN tmp_national_aggregate_return_repair rep ON rep.request_id = pr.id
SET pr.status = 'PENDING_APPROVAL',
    pr.approval_status = 'PENDING',
    pr.current_node_no = rep.repair_node_no,
    pr.returned_at = NULL,
    pr.updated_at = NOW()
WHERE pr.is_deleted = 0;

UPDATE approval_instance ai
JOIN tmp_national_aggregate_return_repair rep ON rep.request_id = ai.purchase_request_id
LEFT JOIN tmp_national_aggregate_repair_current current_task ON current_task.instance_id = ai.id
SET ai.status = 'PENDING',
    ai.current_node_no = rep.repair_node_no,
    ai.current_level_no = rep.repair_node_no,
    ai.current_level_code = COALESCE(current_task.level_code, ai.current_level_code),
    ai.current_pass_rule = COALESCE(current_task.pass_rule, ai.current_pass_rule),
    ai.current_approver_id = COALESCE(current_task.candidate_user_id, ai.current_approver_id),
    ai.ended_at = NULL,
    ai.last_action = COALESCE((
        SELECT r.action
        FROM approval_record r
        WHERE r.instance_id = ai.id
          AND r.is_deleted = 0
          AND r.status = 'ACTIVE'
        ORDER BY r.operated_at DESC, r.id DESC
        LIMIT 1
    ), ai.last_action),
    ai.last_action_by = COALESCE((
        SELECT r.approver_user_id
        FROM approval_record r
        WHERE r.instance_id = ai.id
          AND r.is_deleted = 0
          AND r.status = 'ACTIVE'
        ORDER BY r.operated_at DESC, r.id DESC
        LIMIT 1
    ), ai.last_action_by),
    ai.last_action_at = COALESCE((
        SELECT r.operated_at
        FROM approval_record r
        WHERE r.instance_id = ai.id
          AND r.is_deleted = 0
          AND r.status = 'ACTIVE'
        ORDER BY r.operated_at DESC, r.id DESC
        LIMIT 1
    ), ai.last_action_at),
    ai.updated_at = NOW()
WHERE ai.is_deleted = 0;

DROP TEMPORARY TABLE IF EXISTS tmp_national_aggregate_repair_current;
DROP TEMPORARY TABLE IF EXISTS tmp_national_aggregate_repair_pick;
DROP TEMPORARY TABLE IF EXISTS tmp_national_aggregate_repair_task;
DROP TEMPORARY TABLE IF EXISTS tmp_national_aggregate_return_repair;
