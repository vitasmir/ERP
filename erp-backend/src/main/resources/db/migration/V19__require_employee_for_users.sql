INSERT INTO employees (id, full_name, team_name, job_title, employment_start_date, status)
SELECT md5('system-user:' || users.id::text)::uuid, users.full_name, 'Správa systému', users.role_name,
       CURRENT_DATE, 'ACTIVE'
FROM system_users AS users
WHERE users.employee_id IS NULL;

UPDATE system_users
SET employee_id = md5('system-user:' || id::text)::uuid
WHERE employee_id IS NULL;

ALTER TABLE system_users ALTER COLUMN employee_id SET NOT NULL;

DROP INDEX system_users_employee_unique;

ALTER TABLE system_users ADD CONSTRAINT system_users_employee_unique UNIQUE (employee_id);