CREATE TABLE hr_teams (
    id UUID PRIMARY KEY,
    name VARCHAR(150) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO hr_teams (id, name) VALUES
    ('d4200000-0000-0000-0000-000000000001', 'Nákup'),
    ('d4200000-0000-0000-0000-000000000002', 'Logistika'),
    ('d4200000-0000-0000-0000-000000000003', 'Prodej'),
    ('d4200000-0000-0000-0000-000000000004', 'Výroba');

INSERT INTO hr_teams (id, name)
SELECT gen_random_uuid(), employee_teams.team_name
FROM (SELECT DISTINCT team_name FROM employees WHERE team_name IS NOT NULL AND btrim(team_name) <> '') employee_teams
WHERE NOT EXISTS (SELECT 1 FROM hr_teams team WHERE lower(team.name) = lower(employee_teams.team_name));

ALTER TABLE employees
    ADD COLUMN team_id UUID,
    ADD COLUMN deputy_employee_id UUID;

UPDATE employees SET team_id = teams.id
FROM hr_teams teams
WHERE employees.team_name = teams.name;

UPDATE employees SET deputy_employee_id = CASE
    WHEN id = 'd0000000-0000-0000-0000-000000000001' THEN 'd0000000-0000-0000-0000-000000000002'::uuid
    WHEN id = 'd0000000-0000-0000-0000-000000000002' THEN 'd0000000-0000-0000-0000-000000000001'::uuid
    WHEN id = 'd0000000-0000-0000-0000-000000000003' THEN 'd0000000-0000-0000-0000-000000000004'::uuid
    WHEN id = 'd0000000-0000-0000-0000-000000000004' THEN 'd0000000-0000-0000-0000-000000000003'::uuid
END;

UPDATE employees employee SET deputy_employee_id = deputy.id
FROM employees deputy
WHERE employee.deputy_employee_id IS NULL
    AND deputy.id <> employee.id
    AND deputy.id = (SELECT candidate.id FROM employees candidate
                                     WHERE candidate.id <> employee.id ORDER BY candidate.id LIMIT 1);

ALTER TABLE employees
    ALTER COLUMN team_id SET NOT NULL,
    ALTER COLUMN deputy_employee_id SET NOT NULL,
    ADD CONSTRAINT employees_team_fk FOREIGN KEY (team_id) REFERENCES hr_teams(id),
    ADD CONSTRAINT employees_deputy_fk FOREIGN KEY (deputy_employee_id) REFERENCES employees(id),
    ADD CONSTRAINT employees_deputy_not_self CHECK (deputy_employee_id <> id);