ALTER TABLE system_users ADD COLUMN employee_id UUID;

UPDATE system_users AS users
SET employee_id = employees.id
FROM employees
WHERE users.full_name = employees.full_name;

ALTER TABLE system_users
    ADD CONSTRAINT system_users_employee_fk FOREIGN KEY (employee_id) REFERENCES employees (id);

CREATE UNIQUE INDEX system_users_employee_unique ON system_users (employee_id)
    WHERE employee_id IS NOT NULL;