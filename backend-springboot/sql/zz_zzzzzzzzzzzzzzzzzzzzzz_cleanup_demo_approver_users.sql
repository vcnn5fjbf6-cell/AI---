SET NAMES utf8mb4;

UPDATE sys_user_role ur
JOIN sys_user u ON u.id = ur.user_id
SET ur.status = 0,
    ur.is_deleted = 1,
    ur.updated_at = NOW(),
    ur.updated_by = 0,
    ur.version = ur.version + 1
WHERE u.username IN ('manager01', 'floorLeader01', 'seniorManager01', 'regionManager01')
  AND (
    u.remark LIKE '初始化%'
    OR u.real_name IN ('经理李四', '楼长赵六', '高级经理孙七', '区域经理周八')
  );

UPDATE sys_user
SET status = 0,
    is_deleted = 1,
    updated_at = NOW(),
    updated_by = 0,
    version = version + 1
WHERE username IN ('manager01', 'floorLeader01', 'seniorManager01', 'regionManager01')
  AND (
    remark LIKE '初始化%'
    OR real_name IN ('经理李四', '楼长赵六', '高级经理孙七', '区域经理周八')
  );
