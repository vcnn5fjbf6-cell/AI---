UPDATE purchase_request_item pri
JOIN procurement_after_sale pas ON pas.purchase_request_item_id = pri.id
SET pri.status = 'RETURNED',
    pri.remark = CASE
        WHEN pri.remark IS NULL OR pri.remark = '' THEN CONCAT('售后退货通过：', pas.after_sale_no, '，原因：', COALESCE(pas.reason, '-'))
        WHEN pri.remark LIKE CONCAT('%', pas.after_sale_no, '%') THEN pri.remark
        ELSE CONCAT(pri.remark, '；售后退货通过：', pas.after_sale_no, '，原因：', COALESCE(pas.reason, '-'))
    END,
    pri.updated_at = NOW()
WHERE pri.is_deleted = 0
  AND pas.is_deleted = 0
  AND pas.status = 'APPROVED'
  AND pas.after_sale_type IN ('NO_REASON_RETURN', 'RETURN_EXCHANGE')
  AND (pri.status IS NULL OR pri.status NOT IN ('REJECTED', 'RETURNED'));

UPDATE purchase_request pr
LEFT JOIN (
    SELECT
        purchase_request_id,
        COALESCE(SUM(COALESCE(line_amount, 0)), 0) AS active_amount
    FROM purchase_request_item
    WHERE is_deleted = 0
      AND (status IS NULL OR status NOT IN ('REJECTED', 'RETURNED'))
    GROUP BY purchase_request_id
) sums ON sums.purchase_request_id = pr.id
SET pr.total_amount = COALESCE(sums.active_amount, 0),
    pr.updated_at = NOW()
WHERE pr.is_deleted = 0
  AND EXISTS (
      SELECT 1
      FROM procurement_after_sale pas
      WHERE pas.purchase_request_id = pr.id
        AND pas.is_deleted = 0
        AND pas.status = 'APPROVED'
        AND pas.after_sale_type IN ('NO_REASON_RETURN', 'RETURN_EXCHANGE')
  );
