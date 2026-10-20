UPDATE purchase_request
SET req_title = REPLACE(req_title, '全国月度需求汇总', '酒仙桥区域月度需求汇总')
WHERE req_title LIKE '%全国月度需求汇总%'
  AND is_deleted = 0;

