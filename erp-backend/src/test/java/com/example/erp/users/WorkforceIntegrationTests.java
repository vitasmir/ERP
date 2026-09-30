package com.example.erp.users;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.example.erp.support.AbstractPostgresIntegrationTest;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class WorkforceIntegrationTests extends AbstractPostgresIntegrationTest {
    @Autowired private ApiAccess access;
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final List<UUID> employeeIds = new ArrayList<>();
    private final List<UUID> userIds = new ArrayList<>();
    private final UUID employeeRoleId = UUID.randomUUID();
    private final String employeeRole = "Test employee " + employeeRoleId;
    private final String workplace = "Test-" + UUID.randomUUID();
    private final LocalDateTime start = LocalDate.now().plusDays(10).atTime(8, 0);
    private String adminToken;
    private String employeeToken;
    private UUID employeeId;

    @BeforeEach
    void setUp() {
        adminToken = createAccount("Administrátor");
        createEmployeeRole();
        employeeToken = createAccount(employeeRole);
        employeeId = employeeIds.getLast();
        jdbc.update("INSERT INTO planning_workplaces (name, capacity) VALUES (?, 2)", workplace);
    }

    private void createEmployeeRole() {
        jdbc.update("INSERT INTO role_definitions (id, name, initial, description, can_read, can_edit, can_manage, can_insert, can_delete) "
                        + "VALUES (?, ?, 'T', 'Test employee role', TRUE, TRUE, FALSE, FALSE, FALSE)",
                employeeRoleId, employeeRole);
        jdbc.update("INSERT INTO role_module_permissions (role_id, module_key) VALUES (?, 'planning'), (?, 'hr')",
                employeeRoleId, employeeRoleId);
    }

    private String createAccount(String role) {
        UUID personId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        employeeIds.add(personId);
        userIds.add(userId);
        jdbc.update("INSERT INTO employees (id, full_name, team_name, job_title, employment_start_date, status) VALUES (?, ?, ?, 'Cashier', ?, 'ACTIVE')",
                personId, "Test employee " + personId, workplace, LocalDate.now());
        jdbc.update("INSERT INTO system_users (id, employee_id, full_name, role_name, company_name, status, username, password_hash, color) VALUES (?, ?, 'Test user', ?, 'Test company', 'ACTIVE', ?, ?, '#ffffff')",
                userId, personId, role, "test-" + userId, PasswordHasher.hash("test-password"));
        return access.issueToken(userId);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM planning_notifications WHERE shift_id IN (SELECT id FROM planning_shifts WHERE department = ?)", workplace);
        jdbc.update("DELETE FROM planning_events WHERE shift_id IN (SELECT id FROM planning_shifts WHERE department = ?)", workplace);
        jdbc.update("DELETE FROM planning_shifts WHERE department = ?", workplace);
        for (UUID userId : userIds) {
            jdbc.update("DELETE FROM api_sessions WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM system_users WHERE id = ?", userId);
        }
        for (UUID personId : employeeIds) {
            jdbc.update("DELETE FROM employee_absences WHERE employee_id = ?", personId);
            jdbc.update("DELETE FROM employee_qualifications WHERE employee_id = ?", personId);
            jdbc.update("DELETE FROM employees WHERE id = ?", personId);
        }
        jdbc.update("DELETE FROM role_definitions WHERE id = ?", employeeRoleId);
        jdbc.update("DELETE FROM planning_workplaces WHERE name = ?", workplace);
    }

    @Test
    void publicationVisibilityNotificationsAndDepartureWorkOverHttp() throws Exception {
        HttpResponse<String> created = send(adminToken, "POST", "/planning/shifts", shiftBody(start, start.plusHours(8)));
        assertEquals(200, created.statusCode(), created.body());
        JsonNode shift = mapper.readTree(created.body());
        String shiftPath = "/planning/shifts/" + shift.path("id").asText();
        assertEquals(0, mapper.readTree(send(employeeToken, "GET", "/planning/overview", null).body()).path("shifts").size());
        assertEquals(403, send(employeeToken, "PATCH", shiftPath + "/publish", Map.of()).statusCode());
        HttpResponse<String> published = send(adminToken, "PATCH", shiftPath + "/publish", Map.of());
        assertEquals(200, published.statusCode(), published.body());
        JsonNode ownShifts = mapper.readTree(send(employeeToken, "GET", "/planning/overview", null).body()).path("shifts");
        assertEquals(1, ownShifts.size());
        assertEquals(1, mapper.readTree(send(employeeToken, "GET", "/hr/overview", null).body()).path("employees").size());
        assertEquals(403, send(employeeToken, "GET", "/hr/employees/" + employeeIds.getFirst() + "/availability", null).statusCode());
        HttpResponse<String> notificationResponse = send(employeeToken, "GET", "/planning/notifications", null);
        assertEquals(200, notificationResponse.statusCode(), notificationResponse.body());
        JsonNode notices = mapper.readTree(notificationResponse.body());
        assertEquals(1, notices.size());
        assertEquals(200, send(employeeToken, "PATCH", "/planning/notifications/" + notices.get(0).path("id").asText() + "/read", Map.of()).statusCode());
        assertEquals(404, send(adminToken, "PATCH", "/planning/notifications/" + notices.get(0).path("id").asText() + "/read", Map.of()).statusCode());

        assertEquals(409, send(adminToken, "PATCH", "/hr/employees/" + employeeId + "/deactivate", Map.of()).statusCode());
        Map<String, Object> update = new java.util.LinkedHashMap<>(shiftBody(start, start.plusHours(8)));
        update.put("employeeId", null);
        update.put("version", mapper.readTree(published.body()).path("version").asLong());
        HttpResponse<String> updated = send(adminToken, "PUT", shiftPath, update);
        assertEquals(200, updated.statusCode(), updated.body());
        assertTrue(send(adminToken, "GET", shiftPath + "/events", null).body().contains("CHANGED_AFTER_PUBLICATION"));
        assertEquals(200, send(adminToken, "PATCH", "/hr/employees/" + employeeId + "/deactivate", Map.of()).statusCode());
        assertEquals(401, send(employeeToken, "GET", "/planning/overview", null).statusCode());
        assertEquals(200, send(adminToken, "PATCH", "/hr/employees/" + employeeId + "/activate", Map.of()).statusCode());
        assertEquals(401, send(employeeToken, "GET", "/planning/overview", null).statusCode());
    }

    @Test
    void absenceBlocksAssignmentAndConcurrentRequestsCannotDoubleBook() throws Exception {
        String absencePath = "/hr/employees/" + employeeId + "/absences";
        HttpResponse<String> absence = send(adminToken, "POST", absencePath,
                Map.of("startAt", start.toString(), "endAt", start.plusHours(8).toString(), "reason", "Leave"));
        assertEquals(200, absence.statusCode(), absence.body());
        assertEquals(409, send(adminToken, "POST", "/planning/shifts", shiftBody(start, start.plusHours(8))).statusCode());
        assertEquals(200, send(adminToken, "DELETE", absencePath + "/" + mapper.readTree(absence.body()).path("id").asText(), null).statusCode());

        HttpRequest request = request(adminToken, "POST", "/planning/shifts", shiftBody(start, start.plusHours(8)));
        var first = client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        var second = client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        List<Integer> statuses = java.util.stream.Stream.of(first.get().statusCode(), second.get().statusCode()).sorted().toList();
        assertEquals(List.of(200, 409), statuses);
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM planning_shifts WHERE department = ?", Integer.class, workplace));
    }

    private Map<String, Object> shiftBody(LocalDateTime from, LocalDateTime to) {
        return Map.of("employeeId", employeeId.toString(), "roleName", "Cashier", "department", workplace,
                "startAt", from.toString(), "endAt", to.toString());
    }

        @Test
        void batchPublicationRollsBackAllShiftsAndNotificationsOnConflict() throws Exception {
        JsonNode assigned = mapper.readTree(send(adminToken, "POST", "/planning/shifts", shiftBody(start, start.plusHours(8))).body());
        Map<String, Object> unassignedBody = new java.util.LinkedHashMap<>(shiftBody(start.plusDays(1), start.plusDays(1).plusHours(8)));
        unassignedBody.put("employeeId", null);
        JsonNode unassigned = mapper.readTree(send(adminToken, "POST", "/planning/shifts", unassignedBody).body());
        HttpResponse<String> result = send(adminToken, "POST", "/planning/publish", Map.of("shiftIds",
            List.of(assigned.path("id").asText(), unassigned.path("id").asText())));
        assertEquals(409, result.statusCode(), result.body());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM planning_shifts WHERE id IN (?, ?) AND status = 'PUBLISHED'",
            Integer.class, UUID.fromString(assigned.path("id").asText()), UUID.fromString(unassigned.path("id").asText())));
        assertEquals(0, mapper.readTree(send(employeeToken, "GET", "/planning/notifications", null).body()).size());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM planning_events WHERE action = 'PUBLISHED' AND shift_id = ?",
            Integer.class, UUID.fromString(assigned.path("id").asText())));
        }

    private HttpResponse<String> send(String token, String method, String path, Object body) throws Exception {
        return client.send(request(token, method, path, body), HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest request(String token, String method, String path, Object body) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1" + path))
                .timeout(java.time.Duration.ofSeconds(15)).header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build();
    }
}