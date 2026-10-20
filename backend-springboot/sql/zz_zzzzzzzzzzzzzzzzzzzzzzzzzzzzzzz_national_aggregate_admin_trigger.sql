-- Keep national aggregate submit records owned by the platform administrator,
-- not by the first manager candidate in the next approval node.
SET @workflow_admin_id := (
    SELECT u.id
    FROM sys_user u
    WHERE u.is_deleted = 0
      AND u.status = 1
      AND (
          u.real_name = '系统管理员'
          OR u.username IN ('admin', '系统管理员')
      )
    ORDER BY
      CASE
          WHEN u.real_name = '系统管理员' THEN 0
          WHEN u.username = 'admin' THEN 1
          ELSE 2
      END,
      u.id
    LIMIT 1
);

UPDATE approval_record r
JOIN purchase_request pr ON pr.id = r.purchase_request_id
SET r.approver_user_id = @workflow_admin_id,
    r.approver_role_code = 'SYS_ADMIN',
    r.updated_at = NOW()
WHERE @workflow_admin_id IS NOT NULL
  AND r.is_deleted = 0
  AND pr.is_deleted = 0
  AND r.node_no = 0
  AND r.action = 'SUBMIT'
  AND EXISTS (
      SELECT 1
      FROM purchase_request_item item
      WHERE item.purchase_request_id = pr.id
        AND item.source_request_id IS NOT NULL
        AND item.is_deleted = 0
      LIMIT 1
  )
  AND (
      r.approver_user_id IS NULL
      OR r.approver_user_id <> @workflow_admin_id
      OR r.approver_role_code <> 'SYS_ADMIN'
  );

UPDATE approval_instance ai
JOIN purchase_request pr ON pr.id = ai.purchase_request_id
SET ai.last_action_by = @workflow_admin_id,
    ai.updated_at = NOW()
WHERE @workflow_admin_id IS NOT NULL
  AND ai.is_deleted = 0
  AND pr.is_deleted = 0
  AND ai.last_action = 'SUBMIT'
  AND EXISTS (
      SELECT 1
      FROM purchase_request_item item
      WHERE item.purchase_request_id = pr.id
        AND item.source_request_id IS NOT NULL
        AND item.is_deleted = 0
      LIMIT 1
  )
  AND (
      ai.last_action_by IS NULL
      OR ai.last_action_by <> @workflow_admin_id
  );

UPDATE purchase_request pr
SET pr.applicant_user_id = @workflow_admin_id,
    pr.updated_by = @workflow_admin_id,
    pr.updated_at = NOW()
WHERE @workflow_admin_id IS NOT NULL
  AND pr.is_deleted = 0
  AND EXISTS (
      SELECT 1
      FROM purchase_request_item item
      WHERE item.purchase_request_id = pr.id
        AND item.source_request_id IS NOT NULL
        AND item.is_deleted = 0
      LIMIT 1
  )
  AND pr.applicant_user_id <> @workflow_admin_id;
