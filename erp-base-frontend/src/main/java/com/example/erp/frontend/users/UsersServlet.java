package com.example.erp.frontend.users;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet("/users")
public class UsersServlet extends HttpServlet {
    private static final DateTimeFormatter LAST_ACCESS_FORMAT = DateTimeFormatter.ofPattern("d. M. yyyy HH:mm");
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final String backendUrl = System.getenv().getOrDefault("BACKEND_URL", "http://localhost:8080");

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        try {
            HttpRequest backendRequest = com.example.erp.frontend.base.BackendRequests.newBuilder(URI.create(backendUrl + "/api/v1/users"))
                    .timeout(Duration.ofSeconds(5)).GET().build();
            HttpResponse<String> backendResponse = client.send(backendRequest, HttpResponse.BodyHandlers.ofString());
            if (backendResponse.statusCode() != HttpServletResponse.SC_OK) {
                throw new IOException("Backend returned HTTP " + backendResponse.statusCode());
            }
            request.setAttribute("users", mapper.readValue(backendResponse.body(), UserView[].class));
            HttpRequest employeeRequest = com.example.erp.frontend.base.BackendRequests.newBuilder(URI.create(backendUrl + "/api/v1/users/employee-options"))
                    .timeout(Duration.ofSeconds(5)).GET().build();
            HttpResponse<String> employeeResponse = client.send(employeeRequest, HttpResponse.BodyHandlers.ofString());
            if (employeeResponse.statusCode() != HttpServletResponse.SC_OK) {
                throw new IOException("Backend returned HTTP " + employeeResponse.statusCode());
            }
            request.setAttribute("employeeOptions", mapper.readValue(employeeResponse.body(), EmployeeOption[].class));
            HttpRequest roleRequest = com.example.erp.frontend.base.BackendRequests.newBuilder(URI.create(backendUrl + "/api/v1/roles"))
                    .timeout(Duration.ofSeconds(5)).GET().build();
            HttpResponse<String> roleResponse = client.send(roleRequest, HttpResponse.BodyHandlers.ofString());
            if (roleResponse.statusCode() != HttpServletResponse.SC_OK) {
                throw new IOException("Backend returned HTTP " + roleResponse.statusCode());
            }
            request.setAttribute("roleOptions", mapper.readValue(roleResponse.body(), RoleOption[].class));
            HttpRequest companyRequest = com.example.erp.frontend.base.BackendRequests.newBuilder(URI.create(backendUrl + "/api/v1/companies"))
                    .timeout(Duration.ofSeconds(5)).GET().build();
            HttpResponse<String> companyResponse = client.send(companyRequest, HttpResponse.BodyHandlers.ofString());
            if (companyResponse.statusCode() != HttpServletResponse.SC_OK) {
                throw new IOException("Backend returned HTTP " + companyResponse.statusCode());
            }
            request.setAttribute("companyOptions", mapper.readValue(companyResponse.body(), CompanyOption[].class));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            request.setAttribute("error", "Načítání uživatelů bylo přerušeno.");
        } catch (IOException exception) {
            request.setAttribute("error", "Backend pro uživatele není dostupný: " + exception.getMessage());
        }
        request.setAttribute("message", request.getParameter("message"));
        request.setAttribute("error", request.getParameter("error"));
        request.setAttribute("activePage", "users");
        request.setAttribute("pageTitle", "Uživatelé");
        request.setAttribute("breadcrumb", "BASE / USERS");
        request.getRequestDispatcher("/WEB-INF/views/base/users/index.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        request.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String action = request.getParameter("action");
        try {
            HttpResponse<String> backendResponse;
            if ("delete".equals(action)) {
                backendResponse = sendDelete(request.getParameter("id"));
            } else {
                backendResponse = sendSave(action, request);
            }
            boolean successful = backendResponse.statusCode() >= 200 && backendResponse.statusCode() < 300;
            String message = successful ? successMessage(action) : "Backend změnu uživatele odmítl (HTTP " + backendResponse.statusCode() + ").";
            String parameter = successful ? "message" : "error";
            response.sendRedirect("users?" + parameter + "=" + encode(message));
        } catch (IllegalArgumentException exception) {
            response.sendRedirect("users?error=" + encode("Vyplňte platné údaje uživatele."));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            response.sendRedirect("users?error=" + encode("Změna uživatele byla přerušena."));
        }
    }

    private HttpResponse<String> sendSave(String action, HttpServletRequest request)
            throws IOException, InterruptedException {
        Map<String, String> values = new HashMap<>();
        values.put("employeeId", request.getParameter("employeeId"));
        values.put("fullName", request.getParameter("fullName"));
        values.put("roleName", request.getParameter("roleName"));
        values.put("companyName", request.getParameter("companyName"));
        values.put("status", request.getParameter("status"));
        values.put("color", request.getParameter("color"));
        String body = mapper.writeValueAsString(values);
        HttpRequest.Builder builder = com.example.erp.frontend.base.BackendRequests.newBuilder(userUri(action, request.getParameter("id")))
                .header("Content-Type", "application/json");
        HttpRequest backendRequest = "update".equals(action)
                ? builder.PUT(HttpRequest.BodyPublishers.ofString(body)).build()
                : builder.POST(HttpRequest.BodyPublishers.ofString(body)).build();
        return client.send(backendRequest, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> sendDelete(String id) throws IOException, InterruptedException {
        UUID.fromString(id);
        HttpRequest backendRequest = com.example.erp.frontend.base.BackendRequests.newBuilder(userUri("delete", id))
                .DELETE().build();
        return client.send(backendRequest, HttpResponse.BodyHandlers.ofString());
    }

    private URI userUri(String action, String id) {
        if ("update".equals(action) || "delete".equals(action)) {
            UUID.fromString(id);
            return URI.create(backendUrl + "/api/v1/users/" + id);
        }
        return URI.create(backendUrl + "/api/v1/users");
    }

    private String successMessage(String action) {
        return switch (action) {
            case "update" -> "Uživatel byl aktualizován.";
            case "delete" -> "Uživatel byl smazán.";
            default -> "Uživatel byl přidán.";
        };
    }

    private String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }

        public record UserView(UUID id, String fullName, String roleName, String companyName,
            String status, String lastAccessAt, UUID employeeId, String username, String color) {
        public String initials() {
            String[] words = fullName.trim().split("\\s+");
            return words.length > 1
                    ? (words[0].substring(0, 1) + words[words.length - 1].substring(0, 1)).toUpperCase(Locale.ROOT)
                    : fullName.substring(0, Math.min(2, fullName.length())).toUpperCase(Locale.ROOT);
        }

        public String statusLabel() {
            return switch (status) {
                case "INVITED" -> "Pozvánka čeká";
                case "SUSPENDED" -> "Pozastavený";
                default -> "Aktivní";
            };
        }

        public String statusClass() { return status.toLowerCase(Locale.ROOT); }

        public String lastAccessLabel() {
            if (lastAccessAt == null || lastAccessAt.isBlank()) return "Nikdy";
            try {
                return LocalDateTime.parse(lastAccessAt).format(LAST_ACCESS_FORMAT);
            } catch (DateTimeParseException exception) {
                return lastAccessAt;
            }
        }
    }

    public record EmployeeOption(UUID id, String fullName, String teamName, boolean hasAccount) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RoleOption(UUID id, String name) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CompanyOption(UUID id, String name) { }
}