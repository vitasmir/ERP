CREATE TABLE workforce_lock (id INTEGER PRIMARY KEY CHECK (id = 1));
INSERT INTO workforce_lock VALUES (1);

ALTER TABLE planning_shifts ADD COLUMN employee_id UUID REFERENCES employees(id);
ALTER TABLE planning_shifts ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
UPDATE planning_shifts shift SET employee_id = employee.id
FROM employees employee
WHERE shift.employee_name = employee.full_name
  AND (SELECT COUNT(*) FROM employees other WHERE other.full_name = employee.full_name) = 1;

CREATE TABLE planning_workplaces (
    name VARCHAR(150) PRIMARY KEY,
    capacity INTEGER NOT NULL CHECK (capacity BETWEEN 1 AND 10000)
);
INSERT INTO planning_workplaces (name, capacity)
SELECT DISTINCT department, 10 FROM planning_shifts;

CREATE TABLE employee_qualifications (
    employee_id UUID NOT NULL REFERENCES employees(id),
    role_name VARCHAR(150) NOT NULL,
    PRIMARY KEY (employee_id, role_name)
);
INSERT INTO employee_qualifications SELECT id, job_title FROM employees;

CREATE TABLE employee_absences (
    id UUID PRIMARY KEY,
    employee_id UUID NOT NULL REFERENCES employees(id),
    start_at TIMESTAMP NOT NULL,
    end_at TIMESTAMP NOT NULL CHECK (end_at > start_at),
    reason VARCHAR(240) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX employee_absences_employee_time ON employee_absences(employee_id, start_at, end_at);
CREATE INDEX planning_shifts_employee_time ON planning_shifts(employee_id, start_at, end_at);

CREATE TABLE planning_events (
    id UUID PRIMARY KEY,
    shift_id UUID NOT NULL REFERENCES planning_shifts(id),
    action VARCHAR(40) NOT NULL,
    details TEXT NOT NULL,
    actor VARCHAR(160) NOT NULL,
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE planning_notifications (
    id UUID PRIMARY KEY,
    shift_id UUID NOT NULL REFERENCES planning_shifts(id),
    employee_id UUID NOT NULL REFERENCES employees(id),
    message VARCHAR(500) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at TIMESTAMP
);