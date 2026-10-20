-- Repair partial item approval requests that were incorrectly closed as fully rejected
-- when the remaining approved items had zero reference amount.

UPDATE purchase_request pr
JOIN (
    SELECT
        purchase_request_id,
        SUM(CASE WHEN status = 'REJECTED' THEN 1 ELSE 0 END) AS rejected_count,
        SUM(CASE WHEN status <> 'REJECTED' OR status IS NULL THEN 1 ELSE 0 END) AS active_count,
        COALESCE(SUM(CASE WHEN status <> 'REJECTED' OR status IS NULL THEN line_amount ELSE 0 END), 0) AS active_amount
    FROM purchase_request_item
    GROUP BY purchase_request_id
) item_stat ON item_stat.purchase_request_id = pr.id
SET
    pr.approval_status = 'APPROVED',
    pr.status = CASE WHEN COALESCE(pr.urgency_level, 0) >= 2 THEN 'QUOTING' ELSE 'DEMAND_POOL' END,
    pr.total_amount = item_stat.active_amount,
    pr.approved_at = COALESCE(pr.approved_at, pr.rejected_at, pr.updated_at, NOW()),
    pr.rejected_at = NULL,
    pr.remark = CASE
        WHEN pr.remark IS NULL OR pr.remark = '' THEN '部分商品通过，已恢复有效商品继续流转'
        WHEN pr.remark LIKE '%部分商品通过%' THEN pr.remark
        ELSE CONCAT(pr.remark, '；部分商品通过，已恢复有效商品继续流转')
    END
WHERE pr.status = 'REJECTED'
  AND pr.approval_status = 'REJECTED'
  AND item_stat.rejected_count > 0
  AND item_stat.active_count > 0;
