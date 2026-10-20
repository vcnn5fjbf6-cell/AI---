SET NAMES utf8mb4;

SET @urgent_direct_award_enabled = COALESCE(
  (SELECT setting_value FROM procurement_setting
   WHERE setting_key = 'urgent_direct_award_enabled' AND is_deleted = 0
   LIMIT 1),
  'true'
);

SET @urgent_direct_award_threshold = COALESCE(
  (SELECT CAST(setting_value AS DECIMAL(18,2)) FROM procurement_setting
   WHERE setting_key = 'urgent_direct_award_threshold'
     AND is_deleted = 0
     AND setting_value REGEXP '^[0-9]+(\\.[0-9]+)?$'
   LIMIT 1),
  1000.00
);

UPDATE purchase_request pr
JOIN (
  SELECT
    sq.purchase_request_id,
    CAST(SUBSTRING_INDEX(
      GROUP_CONCAT(sq.supplier_id ORDER BY sq.total_amount ASC, sq.submitted_at ASC, sq.id ASC),
      ',',
      1
    ) AS UNSIGNED) AS supplier_id
  FROM supplier_quote sq
  JOIN supplier s ON s.id = sq.supplier_id AND s.is_deleted = 0
  WHERE sq.is_deleted = 0
    AND sq.status = 'SUBMITTED'
    AND sq.total_amount > 0
    AND s.status = 'ACTIVE'
    AND COALESCE(s.blacklist_flag, 0) = 0
    AND (s.bid_suspended_until IS NULL OR s.bid_suspended_until <= NOW())
  GROUP BY sq.purchase_request_id
) q ON q.purchase_request_id = pr.id
JOIN supplier s ON s.id = q.supplier_id AND s.is_deleted = 0
SET
  pr.direct_award_applied = 1,
  pr.direct_award_supplier_id = s.id,
  pr.direct_award_supplier_name = s.supplier_name,
  pr.direct_award_threshold_amount = @urgent_direct_award_threshold,
  pr.direct_award_reason = CONCAT(
    '低额紧急采购历史数据修复：金额 ',
    CAST(pr.total_amount AS CHAR),
    ' 低于阈值 ',
    CAST(@urgent_direct_award_threshold AS CHAR),
    '，已按有效报价定向供应商：',
    s.supplier_name
  ),
  pr.quote_countdown_started_at = NULL,
  pr.quote_countdown_due_at = NULL,
  pr.updated_at = NOW()
WHERE LOWER(@urgent_direct_award_enabled) = 'true'
  AND pr.is_deleted = 0
  AND pr.status = 'QUOTING'
  AND COALESCE(pr.direct_award_applied, 0) = 0
  AND pr.urgency_level >= 2
  AND pr.total_amount < @urgent_direct_award_threshold;
