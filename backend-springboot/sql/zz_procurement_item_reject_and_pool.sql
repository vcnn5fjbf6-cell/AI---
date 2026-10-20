SET NAMES utf8mb4;

-- Correct normal requests that were previously sent to supplier quotation
-- immediately after floor-leader approval. Urgent requests remain in QUOTING.
UPDATE purchase_request pr
SET pr.status = 'DEMAND_POOL',
    pr.approval_status = 'APPROVED',
    pr.updated_at = NOW(),
    pr.updated_by = 0,
    pr.version = pr.version + 1
WHERE pr.status = 'QUOTING'
  AND pr.current_node_no = 1
  AND COALESCE(pr.urgency_level, 1) < 2
  AND pr.is_deleted = 0
  AND NOT EXISTS (
    SELECT 1
    FROM supplier_quote sq
    WHERE sq.purchase_request_id = pr.id
      AND sq.is_deleted = 0
  );

-- Region-manager approvers are resolved from department approval route
-- candidates. Do not reactivate historical demo account regionManager01.
