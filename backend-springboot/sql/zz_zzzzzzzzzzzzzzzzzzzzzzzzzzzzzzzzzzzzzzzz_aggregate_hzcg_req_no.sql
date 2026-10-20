-- 全国月度需求汇总单使用独立 HZCG 号段，和普通/紧急采购单号分开。

CREATE TABLE IF NOT EXISTS purchase_request_no_sequence (
  prefix VARCHAR(16) PRIMARY KEY COMMENT '采购单号前缀',
  next_no BIGINT NOT NULL COMMENT '下一个可分配流水号',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采购申请单号序列表';

INSERT IGNORE INTO purchase_request_no_sequence(prefix, next_no)
VALUES ('HZCG', 0);

DROP TEMPORARY TABLE IF EXISTS tmp_hzcg_aggregate_req_no_repair;
CREATE TEMPORARY TABLE tmp_hzcg_aggregate_req_no_repair (
  row_no BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  request_id BIGINT NOT NULL,
  old_req_no VARCHAR(64) NOT NULL,
  parsed_hzcg_no BIGINT NULL,
  new_req_no VARCHAR(64) NULL,
  UNIQUE KEY uk_tmp_hzcg_request_id (request_id)
) ENGINE=MEMORY;

INSERT INTO tmp_hzcg_aggregate_req_no_repair(request_id, old_req_no)
SELECT pr.id, pr.req_no
FROM purchase_request pr
WHERE pr.req_reason = '需求池全国月度汇总生成'
  AND pr.req_no NOT REGEXP '^HZCG-[0-9]{6,}$'
ORDER BY pr.created_at, pr.id;

UPDATE tmp_hzcg_aggregate_req_no_repair
SET parsed_hzcg_no = CAST(SUBSTRING_INDEX(SUBSTRING(old_req_no, 6), '.', 1) AS UNSIGNED)
WHERE old_req_no REGEXP '^HZCG-[0-9]+[.][0-9]+$';

SET @hzcg_base_no := (
  SELECT GREATEST(
    0,
    COALESCE((SELECT next_no FROM purchase_request_no_sequence WHERE prefix = 'HZCG'), 0),
    COALESCE((
      SELECT MAX(CAST(SUBSTRING(req_no, 6) AS UNSIGNED)) + 1
      FROM purchase_request
      WHERE req_no REGEXP '^HZCG-[0-9]+$'
    ), 0),
    COALESCE((SELECT MAX(parsed_hzcg_no) + 1 FROM tmp_hzcg_aggregate_req_no_repair WHERE parsed_hzcg_no IS NOT NULL), 0)
  )
);

SET @hzcg_seq_no := @hzcg_base_no - 1;

UPDATE tmp_hzcg_aggregate_req_no_repair
SET new_req_no = CASE
  WHEN parsed_hzcg_no IS NOT NULL THEN CONCAT('HZCG-', LPAD(CAST(parsed_hzcg_no AS CHAR), 6, '0'))
  ELSE CONCAT('HZCG-', LPAD(CAST((@hzcg_seq_no := @hzcg_seq_no + 1) AS CHAR), 6, '0'))
END
ORDER BY row_no;

UPDATE purchase_request pr
JOIN tmp_hzcg_aggregate_req_no_repair rep ON rep.request_id = pr.id
SET pr.req_no = rep.new_req_no,
    pr.updated_at = CURRENT_TIMESTAMP;

SET @hzcg_next_no := (
  SELECT GREATEST(
    @hzcg_base_no,
    COALESCE(MAX(CAST(SUBSTRING(new_req_no, 6) AS UNSIGNED)) + 1, @hzcg_base_no)
  )
  FROM tmp_hzcg_aggregate_req_no_repair
);

UPDATE purchase_request_no_sequence
SET next_no = GREATEST(next_no, @hzcg_next_no),
    updated_at = CURRENT_TIMESTAMP
WHERE prefix = 'HZCG';

DROP TEMPORARY TABLE IF EXISTS tmp_hzcg_aggregate_req_no_repair;
