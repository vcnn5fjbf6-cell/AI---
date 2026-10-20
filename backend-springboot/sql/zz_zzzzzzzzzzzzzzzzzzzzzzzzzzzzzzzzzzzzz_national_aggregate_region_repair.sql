-- Reopen national aggregate requests that were incorrectly completed at V4.
-- Scope: national monthly aggregate, V4 approved, should have V5, no supplier quote data yet.

DROP TEMPORARY TABLE IF EXISTS tmp_national_aggregate_region_repair;
CREATE TEMPORARY TABLE tmp_national_aggregate_region_repair AS
SELECT
  pr.id AS request_id,
  ai.id AS instance_id,
  COALESCE(pr.submit_count, 1) AS submit_round,
  DATE_ADD(
    NOW(),
    INTERVAL CASE DAYOFWEEK(NOW())
      WHEN 1 THEN 4
      WHEN 2 THEN 4
      WHEN 3 THEN 6
      WHEN 4 THEN 6
      WHEN 5 THEN 6
      WHEN 6 THEN 6
      WHEN 7 THEN 5
      ELSE 4
    END DAY
  ) AS due_at
FROM purchase_request pr
JOIN approval_instance ai
  ON ai.purchase_request_id = pr.id
 AND ai.is_deleted = 0
LEFT JOIN supplier_quote q
  ON q.purchase_request_id = pr.id
 AND q.is_deleted = 0
LEFT JOIN supplier_quote_item qi
  ON qi.purchase_request_id = pr.id
 AND qi.is_deleted = 0
WHERE pr.is_deleted = 0
  AND pr.req_reason = '需求池全国月度汇总生成'
  AND pr.current_node_no = 4
  AND pr.approval_status = 'APPROVED'
  AND pr.status = 'QUOTING'
  AND ai.status = 'APPROVED'
  AND ai.total_node_no >= 5
  AND q.id IS NULL
  AND qi.id IS NULL
  AND EXISTS (
    SELECT 1
    FROM approval_record r
    WHERE r.purchase_request_id = pr.id
      AND r.is_deleted = 0
      AND r.status = 'ACTIVE'
      AND r.node_no = 4
      AND r.action = 'APPROVE'
      AND r.to_status = 'APPROVED'
      AND r.is_final_node = 1
  )
  AND NOT EXISTS (
    SELECT 1
    FROM approval_task t
    WHERE t.purchase_request_id = pr.id
      AND t.is_deleted = 0
      AND t.level_no >= 5
      AND t.status = 'PENDING'
  )
GROUP BY pr.id, ai.id, pr.submit_count;

DROP TEMPORARY TABLE IF EXISTS tmp_national_aggregate_region_candidate;
CREATE TEMPORARY TABLE tmp_national_aggregate_region_candidate (
  request_id BIGINT NOT NULL,
  instance_id BIGINT NOT NULL,
  candidate_user_id BIGINT NOT NULL,
  submit_round INT NOT NULL,
  due_at DATETIME NULL,
  PRIMARY KEY (request_id, candidate_user_id, submit_round)
) ENGINE=Memory;

INSERT IGNORE INTO tmp_national_aggregate_region_candidate (
  request_id,
  instance_id,
  candidate_user_id,
  submit_round,
  due_at
)
SELECT DISTINCT
  rep.request_id,
  rep.instance_id,
  lu.user_id,
  rep.submit_round,
  rep.due_at
FROM tmp_national_aggregate_region_repair rep
JOIN purchase_request_item item
  ON item.purchase_request_id = rep.request_id
 AND item.is_deleted = 0
 AND item.source_request_id IS NOT NULL
JOIN purchase_request src
  ON src.id = item.source_request_id
 AND src.is_deleted = 0
JOIN org_scope scope
  ON scope.status = 1
 AND scope.scope_type = 'BUILDING'
 AND (
      (scope.dept_code IS NOT NULL AND scope.dept_code <> '' AND scope.dept_code = src.dept_code)
   OR (scope.scope_code IS NOT NULL AND scope.scope_code <> '' AND src.dept_name LIKE CONCAT('%', scope.scope_code, '%'))
   OR (scope.dept_name IS NOT NULL AND scope.dept_name <> '' AND src.dept_name LIKE CONCAT('%', scope.dept_name, '%'))
 )
JOIN procurement_approval_policy policy
  ON policy.scope_id = scope.id
 AND policy.enabled = 1
JOIN procurement_approval_level level_cfg
  ON level_cfg.policy_id = policy.id
 AND level_cfg.level_no = 5
 AND level_cfg.enabled = 1
JOIN procurement_approval_level_user lu
  ON lu.level_id = level_cfg.id
 AND lu.status = 1;

DROP TEMPORARY TABLE IF EXISTS tmp_national_aggregate_region_has_candidate;
CREATE TEMPORARY TABLE tmp_national_aggregate_region_has_candidate AS
SELECT DISTINCT request_id
FROM tmp_national_aggregate_region_candidate;

INSERT IGNORE INTO tmp_national_aggregate_region_candidate (
  request_id,
  instance_id,
  candidate_user_id,
  submit_round,
  due_at
)
SELECT DISTINCT
  rep.request_id,
  rep.instance_id,
  lu.user_id,
  rep.submit_round,
  rep.due_at
FROM tmp_national_aggregate_region_repair rep
JOIN purchase_request agg
  ON agg.id = rep.request_id
JOIN org_scope scope
  ON scope.status = 1
 AND scope.scope_type = 'BUILDING'
 AND (
      (scope.dept_code IS NOT NULL AND scope.dept_code <> '' AND scope.dept_code = agg.dept_code)
   OR (scope.scope_code IS NOT NULL AND scope.scope_code <> '' AND agg.dept_name LIKE CONCAT('%', scope.scope_code, '%'))
   OR (scope.dept_name IS NOT NULL AND scope.dept_name <> '' AND agg.dept_name LIKE CONCAT('%', scope.dept_name, '%'))
 )
JOIN procurement_approval_policy policy
  ON policy.scope_id = scope.id
 AND policy.enabled = 1
JOIN procurement_approval_level level_cfg
  ON level_cfg.policy_id = policy.id
 AND level_cfg.level_no = 5
 AND level_cfg.enabled = 1
JOIN procurement_approval_level_user lu
  ON lu.level_id = level_cfg.id
 AND lu.status = 1
LEFT JOIN tmp_national_aggregate_region_has_candidate existing
  ON existing.request_id = rep.request_id
WHERE existing.request_id IS NULL;

DROP TEMPORARY TABLE IF EXISTS tmp_national_aggregate_region_assignment;
CREATE TEMPORARY TABLE tmp_national_aggregate_region_assignment AS
SELECT
  request_id,
  instance_id,
  CASE
    WHEN COUNT(DISTINCT candidate_user_id) = 1 THEN MIN(candidate_user_id)
    ELSE NULL
  END AS current_approver_id,
  MIN(due_at) AS due_at
FROM tmp_national_aggregate_region_candidate
GROUP BY request_id, instance_id;

UPDATE approval_record r
JOIN tmp_national_aggregate_region_assignment assignment
  ON assignment.request_id = r.purchase_request_id
SET r.to_status = 'PENDING',
    r.is_final_node = 0,
    r.transfer_to_user_id = assignment.current_approver_id,
    r.remark = CONCAT_WS('；', NULLIF(r.remark, ''), '全国汇总单补充进入V5区域经理审核'),
    r.updated_at = NOW()
WHERE r.is_deleted = 0
  AND r.status = 'ACTIVE'
  AND r.node_no = 4
  AND r.action = 'APPROVE'
  AND r.to_status = 'APPROVED'
  AND r.is_final_node = 1;

INSERT INTO approval_task (
  instance_id,
  purchase_request_id,
  level_no,
  level_code,
  level_name,
  role_code,
  candidate_user_id,
  pass_rule,
  status,
  action,
  action_comment,
  action_at,
  closed_reason,
  due_at,
  submit_round,
  created_at,
  updated_at,
  created_by,
  updated_by,
  is_deleted,
  version
)
SELECT
  candidate.instance_id,
  candidate.request_id,
  5,
  'V5',
  '区域经理审核',
  'REGION_MANAGER',
  candidate.candidate_user_id,
  'ALL',
  'PENDING',
  NULL,
  NULL,
  NULL,
  NULL,
  candidate.due_at,
  candidate.submit_round,
  NOW(),
  NOW(),
  0,
  0,
  0,
  0
FROM tmp_national_aggregate_region_candidate candidate
LEFT JOIN approval_task existing
  ON existing.instance_id = candidate.instance_id
 AND existing.level_no = 5
 AND existing.candidate_user_id = candidate.candidate_user_id
 AND existing.submit_round = candidate.submit_round
WHERE existing.id IS NULL;

UPDATE purchase_request pr
JOIN tmp_national_aggregate_region_assignment assignment
  ON assignment.request_id = pr.id
SET pr.status = 'PENDING_APPROVAL',
    pr.approval_status = 'PENDING',
    pr.current_node_no = 5,
    pr.approved_at = NULL,
    pr.updated_at = NOW()
WHERE pr.is_deleted = 0;

UPDATE approval_instance ai
JOIN tmp_national_aggregate_region_assignment assignment
  ON assignment.instance_id = ai.id
SET ai.status = 'PENDING',
    ai.current_node_no = 5,
    ai.current_level_no = 5,
    ai.current_level_code = 'V5',
    ai.current_pass_rule = 'ALL',
    ai.current_approver_id = assignment.current_approver_id,
    ai.total_node_no = GREATEST(COALESCE(ai.total_node_no, 5), 5),
    ai.ended_at = NULL,
    ai.updated_at = NOW()
WHERE ai.is_deleted = 0;

DROP TEMPORARY TABLE IF EXISTS tmp_national_aggregate_region_assignment;
DROP TEMPORARY TABLE IF EXISTS tmp_national_aggregate_region_has_candidate;
DROP TEMPORARY TABLE IF EXISTS tmp_national_aggregate_region_candidate;
DROP TEMPORARY TABLE IF EXISTS tmp_national_aggregate_region_repair;
