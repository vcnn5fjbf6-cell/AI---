SET NAMES utf8mb4;

SET @approval_record_actor_nullable := (
  SELECT IF(COUNT(*) > 0,
    'ALTER TABLE approval_record MODIFY COLUMN approver_user_id BIGINT NULL COMMENT ''审批人ID''',
    'SELECT 1'
  )
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'approval_record'
    AND COLUMN_NAME = 'approver_user_id'
    AND IS_NULLABLE = 'NO'
);
PREPARE stmt FROM @approval_record_actor_nullable;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE system_error_log
SET status = 'IGNORED',
    fix_remark = '密码过期或旧密码错误属于用户登录处理提示，不再进入错误台账告警。',
    fixed_at = COALESCE(fixed_at, NOW()),
    updated_at = NOW()
WHERE status = 'OPEN'
  AND error_code IN ('PASSWORD_EXPIRED', 'OLD_PASSWORD_INVALID')
  AND is_deleted = 0;

UPDATE system_error_log
SET status = 'FIXED',
    fix_remark = '已修复：放宽 approval_record.approver_user_id，需求池系统汇总记录补充触发人兜底。',
    fixed_at = COALESCE(fixed_at, NOW()),
    updated_at = NOW()
WHERE status = 'OPEN'
  AND is_deleted = 0
  AND request_uri = '/api/workflow/demand-pool/publish-test'
  AND error_code = 'SYSTEM_ERROR'
  AND exception_class = 'org.springframework.dao.DataIntegrityViolationException'
  AND stack_trace LIKE '%approver_user_id%';

UPDATE system_error_log
SET status = 'FIXED',
    fix_remark = '已修复：流程测试创建采购单改为使用启用中的采购部门，不再写死已失效测试部门。',
    fixed_at = COALESCE(fixed_at, NOW()),
    updated_at = NOW()
WHERE status = 'OPEN'
  AND is_deleted = 0
  AND request_uri IN (
    '/api/admin/procurement/settings/flow-tests/CREATE_REQUEST',
    '/api/admin/procurement/settings/flow-tests/CREATE_URGENT_REQUEST'
  )
  AND error_code = 'PROCUREMENT_DEPARTMENT_INVALID'
  AND stack_trace LIKE '%Selected procurement department is not enabled%';
