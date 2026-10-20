SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS emergency_receipt_state_backup_20260812 LIKE supplier_quote_item;

INSERT IGNORE INTO emergency_receipt_state_backup_20260812
SELECT *
FROM supplier_quote_item
WHERE receipt_status = 'RECEIVED';

UPDATE supplier_quote_item
SET receipt_status = 'PENDING',
    receipt_due_at = NULL,
    received_by = NULL,
    received_at = NULL,
    receipt_remark = NULL,
    auto_received = 0,
    updated_at = CURRENT_TIMESTAMP
WHERE receipt_status = 'RECEIVED';

UPDATE supplier_quote_item
SET receipt_due_at = NULL,
    auto_received = 0,
    updated_at = CURRENT_TIMESTAMP
WHERE receipt_due_at IS NOT NULL
   OR COALESCE(auto_received, 0) <> 0;

DELETE FROM procurement_setting
WHERE setting_key = 'order_auto_receipt_days';
