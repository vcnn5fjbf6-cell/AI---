UPDATE sys_permission
SET perm_name = '飞书采购需求提交',
    description = '工程师在飞书窗口提交采购需求',
    updated_at = NOW(),
    version = version + 1
WHERE perm_code = 'monthly_procurement:submit'
  AND is_deleted = 0;

UPDATE sys_permission
SET perm_name = '查看月度采购池',
    description = '查看飞书采购需求池',
    updated_at = NOW(),
    version = version + 1
WHERE perm_code = 'monthly_procurement:view'
  AND is_deleted = 0;

UPDATE sys_permission
SET perm_name = '月度采购汇总',
    description = '采购员汇总月度采购需求',
    updated_at = NOW(),
    version = version + 1
WHERE perm_code = 'monthly_procurement:aggregate'
  AND is_deleted = 0;

UPDATE sys_permission
SET perm_name = '本月需求补充采购需求',
    description = '从本月需求池复制为普通采购补充需求',
    updated_at = NOW(),
    version = version + 1
WHERE perm_code = 'monthly_procurement:supplement'
  AND is_deleted = 0;

UPDATE sys_role_permission rp
JOIN sys_role r ON r.id = rp.role_id
SET rp.status = 0,
    rp.is_deleted = 1,
    rp.updated_at = NOW(),
    rp.version = rp.version + 1
WHERE r.role_code IN ('DUTY_LEADER', 'PROFESSIONAL_ENGINEER', 'APPROVAL_V7', 'APPROVAL_V8')
  AND rp.is_deleted = 0;

UPDATE sys_user_role ur
JOIN sys_role r ON r.id = ur.role_id
SET ur.status = 0,
    ur.is_deleted = 1,
    ur.updated_at = NOW(),
    ur.version = ur.version + 1
WHERE r.role_code IN ('DUTY_LEADER', 'PROFESSIONAL_ENGINEER', 'APPROVAL_V7', 'APPROVAL_V8')
  AND ur.is_deleted = 0;

UPDATE sys_role
SET status = 0,
    is_deleted = 1,
    updated_at = NOW(),
    version = version + 1
WHERE role_code IN ('DUTY_LEADER', 'PROFESSIONAL_ENGINEER', 'APPROVAL_V7', 'APPROVAL_V8')
  AND is_deleted = 0;
