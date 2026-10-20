-- Remove legacy POLICY-* approval policies when a newer imported department policy
-- exists for the same building department. This keeps approval routing aligned with
-- the Department Management configuration instead of the older generated policy.

DROP TEMPORARY TABLE IF EXISTS tmp_legacy_duplicate_policy;
CREATE TEMPORARY TABLE tmp_legacy_duplicate_policy AS
SELECT DISTINCT old_p.id AS policy_id, old_s.id AS scope_id
FROM org_scope old_s
JOIN procurement_approval_policy old_p
  ON old_p.scope_id = old_s.id
 AND old_p.is_deleted = 0
 AND old_p.enabled = 1
JOIN org_scope new_s
  ON new_s.id <> old_s.id
 AND new_s.is_deleted = 0
 AND new_s.status = 1
 AND new_s.scope_type = old_s.scope_type
 AND COALESCE(NULLIF(new_s.dept_code, ''), NULLIF(new_s.cost_center_code, '')) =
     COALESCE(NULLIF(old_s.dept_code, ''), NULLIF(old_s.cost_center_code, ''))
JOIN procurement_approval_policy new_p
  ON new_p.scope_id = new_s.id
 AND new_p.is_deleted = 0
 AND new_p.enabled = 1
 AND new_p.policy_code NOT LIKE 'POLICY-%'
WHERE old_s.is_deleted = 0
  AND old_s.status = 1
  AND old_s.scope_type = 'BUILDING'
  AND old_p.policy_code LIKE 'POLICY-%';

UPDATE procurement_approval_level_user lu
JOIN procurement_approval_level l ON l.id = lu.level_id
JOIN tmp_legacy_duplicate_policy d ON d.policy_id = l.policy_id
SET lu.status = 0,
    lu.is_deleted = 1,
    lu.updated_at = NOW()
WHERE lu.is_deleted = 0;

UPDATE procurement_approval_level l
JOIN tmp_legacy_duplicate_policy d ON d.policy_id = l.policy_id
SET l.enabled = 0,
    l.is_deleted = 1,
    l.updated_at = NOW()
WHERE l.is_deleted = 0;

UPDATE procurement_approval_policy p
JOIN tmp_legacy_duplicate_policy d ON d.policy_id = p.id
SET p.enabled = 0,
    p.is_deleted = 1,
    p.updated_at = NOW()
WHERE p.is_deleted = 0;

UPDATE org_scope s
JOIN tmp_legacy_duplicate_policy d ON d.scope_id = s.id
SET s.status = 0,
    s.is_deleted = 1,
    s.updated_at = NOW()
WHERE s.is_deleted = 0;

DROP TEMPORARY TABLE IF EXISTS tmp_expected_aggregate_manager_task;
CREATE TEMPORARY TABLE tmp_expected_aggregate_manager_task AS
SELECT
  t.instance_id,
  agg.id AS purchase_request_id,
  4 AS level_no,
  COALESCE(l.level_code, 'V4') AS level_code,
  COALESCE(l.level_name, '经理审核') AS level_name,
  'MANAGER' AS role_code,
  lu.user_id AS candidate_user_id,
  'ALL' AS pass_rule,
  COALESCE(agg.submit_count, 1) AS submit_round,
  MIN(t.due_at) AS due_at
FROM purchase_request agg
JOIN approval_task t
  ON t.purchase_request_id = agg.id
 AND t.level_no = 4
 AND t.status = 'PENDING'
JOIN purchase_request_item item
  ON item.purchase_request_id = agg.id
 AND item.source_request_id IS NOT NULL
JOIN purchase_request src
  ON src.id = item.source_request_id
 AND src.is_deleted = 0
JOIN org_scope s
  ON s.is_deleted = 0
 AND s.status = 1
 AND s.scope_type = 'BUILDING'
 AND COALESCE(NULLIF(s.dept_code, ''), NULLIF(s.cost_center_code, '')) = NULLIF(src.dept_code, '')
JOIN procurement_approval_policy p
  ON p.scope_id = s.id
 AND p.is_deleted = 0
 AND p.enabled = 1
 AND p.policy_code NOT LIKE 'POLICY-%'
JOIN procurement_approval_level l
  ON l.policy_id = p.id
 AND l.is_deleted = 0
 AND l.enabled = 1
 AND l.level_no = 4
JOIN procurement_approval_level_user lu
  ON lu.level_id = l.id
 AND lu.is_deleted = 0
 AND lu.status = 1
WHERE agg.req_reason = '需求池全国月度汇总生成'
  AND agg.status = 'PENDING_APPROVAL'
  AND agg.approval_status = 'PENDING'
  AND agg.current_node_no = 4
  AND agg.is_deleted = 0
GROUP BY
  t.instance_id,
  agg.id,
  COALESCE(l.level_code, 'V4'),
  COALESCE(l.level_name, '经理审核'),
  lu.user_id,
  COALESCE(agg.submit_count, 1);

UPDATE approval_task t
JOIN purchase_request agg
  ON agg.id = t.purchase_request_id
 AND agg.req_reason = '需求池全国月度汇总生成'
 AND agg.status = 'PENDING_APPROVAL'
 AND agg.approval_status = 'PENDING'
 AND agg.current_node_no = 4
LEFT JOIN tmp_expected_aggregate_manager_task expected
  ON expected.instance_id = t.instance_id
 AND expected.purchase_request_id = t.purchase_request_id
 AND expected.level_no = t.level_no
 AND expected.candidate_user_id = t.candidate_user_id
 AND expected.submit_round = t.submit_round
SET t.status = 'AUTO_CLOSED',
    t.closed_reason = 'LEGACY_POLICY_CLEANUP',
    t.updated_at = NOW()
WHERE t.status = 'PENDING'
  AND t.level_no = 4
  AND expected.candidate_user_id IS NULL;

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
  expected.instance_id,
  expected.purchase_request_id,
  expected.level_no,
  expected.level_code,
  expected.level_name,
  expected.role_code,
  expected.candidate_user_id,
  expected.pass_rule,
  'PENDING',
  NULL,
  NULL,
  NULL,
  NULL,
  COALESCE(expected.due_at, DATE_ADD(
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
  )),
  expected.submit_round,
  NOW(),
  NOW(),
  0,
  0,
  0,
  0
FROM tmp_expected_aggregate_manager_task expected
LEFT JOIN approval_task existing
  ON existing.instance_id = expected.instance_id
 AND existing.level_no = expected.level_no
 AND existing.candidate_user_id = expected.candidate_user_id
 AND existing.submit_round = expected.submit_round
WHERE existing.id IS NULL;

DROP TEMPORARY TABLE IF EXISTS tmp_expected_aggregate_manager_task;
DROP TEMPORARY TABLE IF EXISTS tmp_legacy_duplicate_policy;
