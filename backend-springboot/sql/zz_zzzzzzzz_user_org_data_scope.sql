SET NAMES utf8mb4;

UPDATE sys_user
SET data_scope_type = 'ALL',
    updated_at = NOW()
WHERE is_deleted = 0
  AND user_type IN ('ADMIN', 'SYS_ADMIN', 'SUPER_ADMIN')
  AND data_scope_type <> 'ALL';

UPDATE sys_user
SET data_scope_type = 'SELF',
    updated_at = NOW()
WHERE is_deleted = 0
  AND user_type = 'SUPPLIER'
  AND data_scope_type <> 'SELF';

UPDATE sys_user
SET data_scope_type = 'ORG',
    updated_at = NOW()
WHERE is_deleted = 0
  AND user_type NOT IN ('ADMIN', 'SYS_ADMIN', 'SUPER_ADMIN', 'SUPPLIER')
  AND data_scope_type <> 'ORG';

UPDATE sys_role
SET data_scope_type = 'ALL',
    updated_at = NOW()
WHERE is_deleted = 0
  AND role_type = 'ADMIN'
  AND data_scope_type <> 'ALL';

UPDATE sys_role
SET data_scope_type = 'SELF',
    updated_at = NOW()
WHERE is_deleted = 0
  AND role_type = 'SUPPLIER'
  AND data_scope_type <> 'SELF';

UPDATE sys_role
SET data_scope_type = 'ORG',
    updated_at = NOW()
WHERE is_deleted = 0
  AND role_type NOT IN ('ADMIN', 'SUPPLIER')
  AND data_scope_type <> 'ORG';
