UPDATE employees AS e
SET job_title = u.role_name
FROM system_users AS u
WHERE u.employee_id = e.id;

ALTER TABLE system_users DROP COLUMN role_name;