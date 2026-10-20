-- Align historical HZCG monthly aggregate requests with the latest process:
-- demand-pool aggregates enter supplier quotation directly, without V4/V5 demand approval.
-- Quote-review requests that already have supplier quote data are intentionally excluded.

SET @hzcg_direct_quote_admin_id := (
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

DROP TEMPORARY TABLE IF EXISTS tmp_hzcg_direct_supplier_quote_repair;
CREATE TEMPORARY TABLE tmp_hzcg_direct_supplier_quote_repair AS
SELECT
  pr.id AS request_id,
  ai.id AS instance_id,
  COALESCE(pr.submit_count, 1) AS submit_round
FROM purchase_request pr
LEFT JOIN approval_instance ai
  ON ai.purchase_request_id = pr.id
 AND ai.is_deleted = 0
WHERE pr.is_deleted = 0
  AND (pr.req_no LIKE 'HZCG-%' OR pr.req_reason = '需求池全国月度汇总生成')
  AND pr.status = 'PENDING_APPROVAL'
  AND pr.approval_status = 'PENDING'
  AND pr.current_node_no >= 4
  AND NOT EXISTS (
      SELECT 1
      FROM supplier_quote quote
      WHERE quote.purchase_request_id = pr.id
        AND quote.is_deleted = 0
  )
  AND NOT EXISTS (
      SELECT 1
      FROM supplier_quote_item quote_item
      WHERE quote_item.purchase_request_id = pr.id
        AND quote_item.is_deleted = 0
  );

UPDATE approval_task task
JOIN tmp_hzcg_direct_supplier_quote_repair repair
  ON repair.request_id = task.purchase_request_id
SET task.status = 'AUTO_CLOSED',
    task.closed_reason = 'HZCG_DIRECT_SUPPLIER_QUOTE_REPAIR',
    task.updated_at = NOW(),
    task.updated_by = COALESCE(@hzcg_direct_quote_admin_id, 0)
WHERE task.is_deleted = 0
  AND task.status = 'PENDING';

UPDATE approval_instance instance
JOIN tmp_hzcg_direct_supplier_quote_repair repair
  ON repair.instance_id = instance.id
SET instance.status = 'APPROVED',
    instance.current_node_no = 0,
    instance.current_level_no = NULL,
    instance.current_level_code = 'SUPPLIER_QUOTE',
    instance.current_pass_rule = NULL,
    instance.total_node_no = 0,
    instance.current_approver_id = NULL,
    instance.ended_at = COALESCE(instance.ended_at, NOW()),
    instance.last_action = 'SUBMIT',
    instance.last_action_by = @hzcg_direct_quote_admin_id,
    instance.last_action_at = COALESCE(instance.last_action_at, NOW()),
    instance.remark = '需求池全国汇总后直接进入供应商报价',
    instance.updated_at = NOW(),
    instance.updated_by = COALESCE(@hzcg_direct_quote_admin_id, 0)
WHERE instance.is_deleted = 0
  AND instance.status = 'PENDING';

INSERT INTO approval_record (
    instance_id,
    purchase_request_id,
    node_no,
    node_name,
    approver_user_id,
    approver_role_code,
    action,
    from_status,
    to_status,
    approval_comment,
    operated_at,
    submit_round,
    is_final_node,
    can_resubmit,
    transfer_to_user_id,
    status,
    created_at,
    updated_at,
    created_by,
    updated_by,
    is_deleted,
    version
)
SELECT repair.instance_id,
       repair.request_id,
       0,
       '需求池汇总直达报价',
       @hzcg_direct_quote_admin_id,
       'SYS_ADMIN',
       'SUBMIT',
       'PENDING',
       'APPROVED',
       '按最新采购流程图修正：月度汇总单无需经理复审，直接进入供应商报价',
       NOW(),
       repair.submit_round,
       1,
       0,
       NULL,
       'ACTIVE',
       NOW(),
       NOW(),
       COALESCE(@hzcg_direct_quote_admin_id, 0),
       COALESCE(@hzcg_direct_quote_admin_id, 0),
       0,
       0
FROM tmp_hzcg_direct_supplier_quote_repair repair
WHERE repair.instance_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM approval_record existing
      WHERE existing.purchase_request_id = repair.request_id
        AND existing.is_deleted = 0
        AND existing.node_name = '需求池汇总直达报价'
        AND existing.approval_comment LIKE '按最新采购流程图修正%'
  );

UPDATE purchase_request pr
JOIN tmp_hzcg_direct_supplier_quote_repair repair
  ON repair.request_id = pr.id
SET pr.status = 'QUOTING',
    pr.approval_status = 'APPROVED',
    pr.current_node_no = 0,
    pr.approved_at = COALESCE(pr.approved_at, NOW()),
    pr.leader_confirm_status = 'PENDING',
    pr.remark = LEFT(
        CONCAT_WS(
            '；',
            NULLIF(pr.remark, ''),
            '2026-07-02 按最新流程图修正：月度汇总单直接进入供应商报价'
        ),
        500
    ),
    pr.updated_at = NOW(),
    pr.updated_by = COALESCE(@hzcg_direct_quote_admin_id, 0)
WHERE pr.is_deleted = 0;

DROP TEMPORARY TABLE IF EXISTS tmp_hzcg_direct_supplier_quote_repair;
