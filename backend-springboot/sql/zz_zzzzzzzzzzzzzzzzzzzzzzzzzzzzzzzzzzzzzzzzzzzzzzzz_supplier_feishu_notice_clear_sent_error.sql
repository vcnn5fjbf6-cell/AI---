UPDATE supplier_feishu_quote_notice
SET last_error = ''
WHERE status = 'SENT'
  AND last_error IS NOT NULL
  AND last_error <> '';

UPDATE feishu_approval_card_notification
SET last_error = ''
WHERE status IN ('SENT', 'APPROVED', 'RETURNED', 'CLOSED')
  AND last_error IS NOT NULL
  AND last_error <> '';
