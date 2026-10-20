-- Keep workflow records aligned with the approval task that was actually closed.
-- This repairs administrator proxy actions where the task belongs to a business approver.
UPDATE approval_record r
JOIN approval_task t
  ON t.purchase_request_id = r.purchase_request_id
 AND t.level_no = r.node_no
 AND t.action = r.action
 AND t.action_at = r.operated_at
 AND t.is_deleted = 0
SET r.approver_user_id = t.candidate_user_id,
    r.updated_at = NOW()
WHERE r.is_deleted = 0
  AND r.node_no > 0
  AND r.action IN ('APPROVE', 'REJECT', 'RETURN')
  AND t.candidate_user_id IS NOT NULL
  AND (
      r.approver_user_id IS NULL
      OR r.approver_user_id <> t.candidate_user_id
  );

UPDATE approval_instance ai
JOIN approval_record r
  ON r.instance_id = ai.id
 AND r.action = ai.last_action
 AND r.operated_at = ai.last_action_at
 AND r.is_deleted = 0
SET ai.last_action_by = r.approver_user_id,
    ai.updated_at = NOW()
WHERE ai.is_deleted = 0
  AND r.node_no > 0
  AND r.action IN ('APPROVE', 'REJECT', 'RETURN')
  AND r.approver_user_id IS NOT NULL
  AND (
      ai.last_action_by IS NULL
      OR ai.last_action_by <> r.approver_user_id
  );
