-- Sync rejected lines from national aggregate requests back to their source requests.
-- This keeps the original applicant list and message bell aligned with aggregate-stage item rejection.

CREATE TEMPORARY TABLE IF NOT EXISTS tmp_national_aggregate_rejected_sources (
    source_request_id BIGINT PRIMARY KEY
);

TRUNCATE TABLE tmp_national_aggregate_rejected_sources;

INSERT IGNORE INTO tmp_national_aggregate_rejected_sources (source_request_id)
SELECT DISTINCT agg_item.source_request_id
FROM purchase_request agg
JOIN purchase_request_item agg_item
  ON agg_item.purchase_request_id = agg.id
 AND agg_item.is_deleted = 0
WHERE agg.is_deleted = 0
  AND (agg.req_reason = '需求池全国月度汇总生成' OR agg.req_no LIKE 'HZCG-%')
  AND agg_item.status = 'REJECTED'
  AND agg_item.source_request_id IS NOT NULL
  AND agg_item.source_request_item_id IS NOT NULL;

UPDATE purchase_request_item source_item
JOIN purchase_request_item agg_item
  ON agg_item.source_request_item_id = source_item.id
 AND agg_item.is_deleted = 0
JOIN purchase_request agg
  ON agg.id = agg_item.purchase_request_id
 AND agg.is_deleted = 0
 AND (agg.req_reason = '需求池全国月度汇总生成' OR agg.req_no LIKE 'HZCG-%')
SET
  source_item.status = 'REJECTED',
  source_item.remark = CASE
    WHEN agg_item.remark IS NOT NULL AND agg_item.remark <> '' THEN agg_item.remark
    ELSE source_item.remark
  END,
  source_item.updated_at = NOW()
WHERE source_item.is_deleted = 0
  AND agg_item.status = 'REJECTED'
  AND agg_item.source_request_id IS NOT NULL
  AND agg_item.source_request_item_id IS NOT NULL
  AND source_item.purchase_request_id = agg_item.source_request_id
  AND (source_item.status IS NULL OR source_item.status <> 'REJECTED');

UPDATE purchase_request source_request
JOIN tmp_national_aggregate_rejected_sources affected
  ON affected.source_request_id = source_request.id
JOIN (
    SELECT
      purchase_request_id,
      SUM(CASE WHEN status = 'REJECTED' THEN 1 ELSE 0 END) AS rejected_count,
      SUM(CASE WHEN status <> 'REJECTED' OR status IS NULL THEN 1 ELSE 0 END) AS active_count,
      COALESCE(SUM(CASE WHEN status <> 'REJECTED' OR status IS NULL THEN line_amount ELSE 0 END), 0) AS active_amount
    FROM purchase_request_item
    WHERE is_deleted = 0
    GROUP BY purchase_request_id
) item_stat
  ON item_stat.purchase_request_id = source_request.id
SET
  source_request.total_amount = item_stat.active_amount,
  source_request.approval_status = CASE
    WHEN item_stat.active_count = 0 THEN 'REJECTED'
    ELSE 'APPROVED'
  END,
  source_request.status = CASE
    WHEN item_stat.active_count = 0 THEN 'REJECTED'
    WHEN source_request.status = 'REJECTED' OR source_request.approval_status = 'REJECTED' THEN 'DEMAND_AGGREGATED'
    ELSE source_request.status
  END,
  source_request.rejected_at = CASE
    WHEN item_stat.active_count = 0 THEN COALESCE(source_request.rejected_at, NOW())
    ELSE NULL
  END,
  source_request.updated_at = NOW()
WHERE source_request.is_deleted = 0
  AND item_stat.rejected_count > 0;

DROP TEMPORARY TABLE IF EXISTS tmp_national_aggregate_rejected_sources;
