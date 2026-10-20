-- Align historical national aggregates with the V4-final demand confirmation rule.
-- National aggregate demand confirmation now stops after all V4 manager tasks are done.
-- Quote review after supplier comparison still uses the amount thresholds for V5/V6.

DROP TEMPORARY TABLE IF EXISTS tmp_nested_national_aggregate_sources;
CREATE TEMPORARY TABLE tmp_nested_national_aggregate_sources AS
SELECT DISTINCT source.id AS request_id
FROM purchase_request_item item
JOIN purchase_request parent ON parent.id = item.purchase_request_id AND parent.is_deleted = 0
JOIN purchase_request source ON source.id = item.source_request_id AND source.is_deleted = 0
WHERE (parent.req_no LIKE 'HZCG-%' OR parent.req_reason = '需求池全国月度汇总生成')
  AND (source.req_no LIKE 'HZCG-%' OR source.req_reason = '需求池全国月度汇总生成');

DROP TEMPORARY TABLE IF EXISTS tmp_national_aggregate_v4_final_repair;
CREATE TEMPORARY TABLE tmp_national_aggregate_v4_final_repair AS
SELECT pr.id,
       CASE
           WHEN nested.request_id IS NULL THEN 'QUOTING'
           ELSE 'DEMAND_AGGREGATED'
       END AS repaired_status
FROM purchase_request pr
LEFT JOIN tmp_nested_national_aggregate_sources nested ON nested.request_id = pr.id
WHERE (pr.req_no LIKE 'HZCG-%' OR pr.req_reason = '需求池全国月度汇总生成')
  AND pr.status = 'PENDING_APPROVAL'
  AND pr.current_node_no >= 4
  AND NOT EXISTS (
      SELECT 1
      FROM approval_task task
      WHERE task.purchase_request_id = pr.id
        AND task.is_deleted = 0
        AND task.status = 'PENDING'
  )
  AND NOT EXISTS (
      SELECT 1
      FROM supplier_quote quote
      WHERE quote.purchase_request_id = pr.id
        AND quote.is_deleted = 0
  );

UPDATE approval_task task
JOIN tmp_national_aggregate_v4_final_repair repair ON repair.id = task.purchase_request_id
SET task.status = 'AUTO_CLOSED',
    task.closed_reason = 'NATIONAL_AGGREGATE_V4_FINAL_REPAIR',
    task.updated_at = NOW(),
    task.updated_by = 1
WHERE task.is_deleted = 0
  AND task.status = 'PENDING';

UPDATE approval_instance instance
JOIN tmp_national_aggregate_v4_final_repair repair ON repair.id = instance.purchase_request_id
SET instance.status = 'APPROVED',
    instance.current_node_no = 4,
    instance.current_level_no = 4,
    instance.total_node_no = 4,
    instance.current_approver_id = NULL,
    instance.ended_at = COALESCE(instance.ended_at, NOW()),
    instance.last_action = 'APPROVE',
    instance.last_action_by = 1,
    instance.last_action_at = COALESCE(instance.last_action_at, NOW()),
    instance.updated_at = NOW(),
    instance.updated_by = 1
WHERE instance.status = 'PENDING';

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
SELECT instance.id,
       pr.id,
       4,
       '经理审核',
       1,
       'SUPER_ADMIN',
       'APPROVE',
       'PENDING',
       'APPROVED',
       CASE
           WHEN repair.repaired_status = 'QUOTING' THEN '全国汇总需求确认阶段按 V4 终审规则修正，进入供应商报价'
           ELSE '历史嵌套全国汇总单已作为其他汇总来源，关闭异常审批状态'
       END,
       NOW(),
       1,
       1,
       0,
       NULL,
       'ACTIVE',
       NOW(),
       NOW(),
       1,
       1,
       0,
       0
FROM tmp_national_aggregate_v4_final_repair repair
JOIN purchase_request pr ON pr.id = repair.id
LEFT JOIN approval_instance instance ON instance.purchase_request_id = pr.id
WHERE NOT EXISTS (
    SELECT 1
    FROM approval_record existing
    WHERE existing.purchase_request_id = pr.id
      AND existing.is_deleted = 0
      AND existing.approval_comment IN (
          '全国汇总需求确认阶段按 V4 终审规则修正，进入供应商报价',
          '历史嵌套全国汇总单已作为其他汇总来源，关闭异常审批状态'
      )
);

UPDATE purchase_request pr
JOIN tmp_national_aggregate_v4_final_repair repair ON repair.id = pr.id
SET pr.status = repair.repaired_status,
    pr.approval_status = 'APPROVED',
    pr.current_node_no = 4,
    pr.approved_at = COALESCE(pr.approved_at, NOW()),
    pr.remark = CONCAT(COALESCE(pr.remark, ''), '\n', CASE
        WHEN repair.repaired_status = 'QUOTING' THEN '全国汇总需求确认阶段按 V4 终审规则修正，进入供应商报价'
        ELSE '历史嵌套全国汇总单已作为其他汇总来源，关闭异常审批状态'
    END),
    pr.updated_at = NOW(),
    pr.updated_by = 1;

DROP TEMPORARY TABLE IF EXISTS tmp_national_aggregate_v4_final_repair;
DROP TEMPORARY TABLE IF EXISTS tmp_nested_national_aggregate_sources;
