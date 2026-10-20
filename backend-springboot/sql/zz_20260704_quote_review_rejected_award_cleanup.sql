-- Clean historical award markers left on supplier quote items whose purchase items
-- were rejected by item-level approval during quote review.

UPDATE supplier_quote_item sqi
JOIN purchase_request_item pri ON pri.id = sqi.purchase_request_item_id
SET sqi.awarded = 0,
    sqi.replacement_review_status = NULL,
    sqi.replacement_reviewed_by = NULL,
    sqi.replacement_reviewed_at = NULL,
    sqi.replacement_review_remark = NULL,
    sqi.updated_at = CURRENT_TIMESTAMP
WHERE sqi.is_deleted = 0
  AND pri.is_deleted = 0
  AND sqi.awarded = 1
  AND pri.status = 'REJECTED';

UPDATE supplier_quote sq
LEFT JOIN supplier_quote_item awarded_item
  ON awarded_item.quote_id = sq.id
 AND awarded_item.is_deleted = 0
 AND awarded_item.status = 'ACTIVE'
 AND awarded_item.awarded = 1
SET sq.supplier_confirm_status = 'UNSELECTED',
    sq.supplier_confirm_remark = NULL,
    sq.supplier_confirmed_by = NULL,
    sq.supplier_confirmed_at = NULL,
    sq.supplier_confirm_due_at = NULL,
    sq.supplier_confirm_notified_at = NULL,
    sq.updated_at = CURRENT_TIMESTAMP
WHERE sq.is_deleted = 0
  AND sq.supplier_confirm_status = 'PENDING'
  AND awarded_item.id IS NULL;
