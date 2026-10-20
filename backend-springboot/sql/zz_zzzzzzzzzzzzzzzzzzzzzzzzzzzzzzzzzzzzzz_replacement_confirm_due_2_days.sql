-- Keep existing pending applicant replacement-confirmation tasks aligned with
-- the current 2-day timeout rule. The update only extends old shorter deadlines.
UPDATE approval_task task
JOIN purchase_request req
  ON req.id = task.purchase_request_id
SET task.due_at = DATE_ADD(COALESCE(task.created_at, task.updated_at, NOW()), INTERVAL 2 DAY),
    task.updated_at = NOW()
WHERE task.is_deleted = 0
  AND task.status = 'PENDING'
  AND task.level_no = 10
  AND req.is_deleted = 0
  AND req.current_node_no = 10
  AND req.approval_status = 'PENDING'
  AND (
      task.due_at IS NULL
      OR task.due_at < DATE_ADD(COALESCE(task.created_at, task.updated_at, NOW()), INTERVAL 2 DAY)
  );
