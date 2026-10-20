SET @db_name := DATABASE();

SET @sql := IF(
  (SELECT COUNT(*)
   FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = @db_name
     AND TABLE_NAME = 'purchase_request'
     AND COLUMN_NAME = 'ai_review_opinion') = 0,
  'ALTER TABLE purchase_request ADD COLUMN ai_review_opinion VARCHAR(1000) NULL COMMENT ''AI审核意见'' AFTER req_reason',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
