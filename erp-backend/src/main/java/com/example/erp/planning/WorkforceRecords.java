package com.example.erp.planning;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class WorkforceRecords {
    private final JdbcTemplate jdbc;

    public WorkforceRecords(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void lock() {
        jdbc.queryForObject("SELECT id FROM workforce_lock WHERE id = 1 FOR UPDATE", Integer.class);
    }

    public List<Workplace> workplaces() {
        return jdbc.query("SELECT name, capacity FROM planning_workplaces ORDER BY name",
                (row, index) -> new Workplace(row.getString("name"), row.getInt("capacity")));
    }

    public void saveWorkplace(String name, int capacity) {
        jdbc.update("INSERT INTO planning_workplaces (name, capacity) VALUES (?, ?) ON CONFLICT (name) DO UPDATE SET capacity = EXCLUDED.capacity",
                name, capacity);
    }

    public List<String> qualifications(UUID employeeId) {
        return jdbc.query("SELECT role_name FROM employee_qualifications WHERE employee_id = ? ORDER BY role_name",
                (row, index) -> row.getString("role_name"), employeeId);
    }

    public void qualify(UUID employeeId, String roleName) {
        jdbc.update("INSERT INTO employee_qualifications (employee_id, role_name) VALUES (?, ?) ON CONFLICT DO NOTHING", employeeId, roleName);
    }

    public List<Absence> absences(UUID employeeId) {
        return jdbc.query("SELECT * FROM employee_absences WHERE employee_id = ? ORDER BY start_at",
                (row, index) -> new Absence(row.getObject("id", UUID.class), employeeId,
                        row.getObject("start_at", LocalDateTime.class), row.getObject("end_at", LocalDateTime.class),
                        row.getString("reason")), employeeId);
    }

    public Absence addAbsence(UUID employeeId, LocalDateTime startAt, LocalDateTime endAt, String reason) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO employee_absences (id, employee_id, start_at, end_at, reason) VALUES (?, ?, ?, ?, ?)",
                id, employeeId, startAt, endAt, reason);
        return new Absence(id, employeeId, startAt, endAt, reason);
    }

    public boolean removeAbsence(UUID employeeId, UUID absenceId) {
        return jdbc.update("DELETE FROM employee_absences WHERE employee_id = ? AND id = ?", employeeId, absenceId) == 1;
    }

    public void event(UUID shiftId, String action, String details, String actor) {
        jdbc.update("INSERT INTO planning_events (id, shift_id, action, details, actor) VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(), shiftId, action, details, actor);
    }

    public List<Event> events(UUID shiftId) {
        return jdbc.query("SELECT * FROM planning_events WHERE shift_id = ? ORDER BY occurred_at, id",
                (row, index) -> new Event(row.getString("action"), row.getString("details"), row.getString("actor"),
                        row.getObject("occurred_at", LocalDateTime.class)), shiftId);
    }

    public void notifyEmployee(UUID shiftId, UUID employeeId, String message) {
        if (employeeId != null) jdbc.update("INSERT INTO planning_notifications (id, shift_id, employee_id, message) VALUES (?, ?, ?, ?)",
                UUID.randomUUID(), shiftId, employeeId, message);
    }

    public List<Notification> notifications(UUID employeeId) {
        return jdbc.query("SELECT * FROM planning_notifications WHERE employee_id = ? ORDER BY created_at DESC, id",
                (row, index) -> new Notification(row.getObject("id", UUID.class), row.getObject("shift_id", UUID.class),
                        row.getString("message"), row.getObject("created_at", LocalDateTime.class),
                        row.getObject("read_at", LocalDateTime.class)), employeeId);
    }

    public boolean markRead(UUID employeeId, UUID notificationId) {
        return jdbc.update("UPDATE planning_notifications SET read_at = COALESCE(read_at, CURRENT_TIMESTAMP) WHERE employee_id = ? AND id = ?",
                employeeId, notificationId) == 1;
    }

    public void deleteDraftEvents(UUID shiftId) {
        jdbc.update("DELETE FROM planning_events WHERE shift_id = ?", shiftId);
    }

    public record Workplace(String name, int capacity) { }
    public record Absence(UUID id, UUID employeeId, LocalDateTime startAt, LocalDateTime endAt, String reason) { }
    public record Event(String action, String details, String actor, LocalDateTime occurredAt) { }
    public record Notification(UUID id, UUID shiftId, String message, LocalDateTime createdAt, LocalDateTime readAt) { }
}