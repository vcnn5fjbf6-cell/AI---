CREATE TABLE IF NOT EXISTS purchase_request_no_sequence (
  prefix VARCHAR(16) PRIMARY KEY COMMENT '采购单号前缀',
  next_no BIGINT NOT NULL COMMENT '下一个可分配流水号',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采购申请单号序列表';

INSERT INTO purchase_request_no_sequence(prefix, next_no)
VALUES
  (
    'PTCG',
    GREATEST(
      100000,
      COALESCE((
        SELECT MAX(CAST(SUBSTRING(req_no, 6) AS UNSIGNED)) + 1
        FROM purchase_request
        WHERE req_no REGEXP '^PTCG-[0-9]+$'
      ), 100000)
    )
  ),
  (
    'JJCG',
    GREATEST(
      100000,
      COALESCE((
        SELECT MAX(CAST(SUBSTRING(req_no, 6) AS UNSIGNED)) + 1
        FROM purchase_request
        WHERE req_no REGEXP '^JJCG-[0-9]+$'
      ), 100000)
    )
  ),
  (
    'HZCG',
    GREATEST(
      0,
      COALESCE((
        SELECT MAX(CAST(SUBSTRING(req_no, 6) AS UNSIGNED)) + 1
        FROM purchase_request
        WHERE req_no REGEXP '^HZCG-[0-9]+$'
      ), 0)
    )
  )
ON DUPLICATE KEY UPDATE next_no = GREATEST(next_no, VALUES(next_no));
