UPDATE sys_user
SET mobile = NULL
WHERE mobile = '';

UPDATE sys_user
SET email = NULL
WHERE email = '';
