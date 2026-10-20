SET NAMES utf8mb4;

UPDATE system_error_log
SET status = 'FIXED',
    fix_remark = '已修复于 v20260710.1：采购明细短日期会统一转换为 yyyy-MM-dd，日期格式问题按表单校验处理，不再进入错误台账。',
    fixed_at = COALESCE(fixed_at, NOW()),
    updated_at = NOW()
WHERE status = 'OPEN'
  AND is_deleted = 0
  AND request_uri = '/api/workflow/purchase-requests'
  AND exception_class = 'org.springframework.http.converter.HttpMessageNotReadableException'
  AND stack_trace LIKE '%java.time.LocalDate%'
  AND stack_trace LIKE '%could not be parsed%';
