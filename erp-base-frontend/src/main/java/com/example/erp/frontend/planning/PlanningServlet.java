package com.example.erp.frontend.planning;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet("/planning")
public class PlanningServlet extends HttpServlet {
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final String backendUrl = System.getenv().getOrDefault("BACKEND_URL", "http://localhost:8080");

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        try {
            HttpRequest backendRequest = com.example.erp.frontend.base.BackendRequests.newBuilder(URI.create(backendUrl + "/api/v1/planning/overview"))
                    .timeout(Duration.ofSeconds(5)).GET().build();
            HttpResponse<String> backendResponse = client.send(backendRequest, HttpResponse.BodyHandlers.ofString());
            if (backendResponse.statusCode() != HttpServletResponse.SC_OK) {
                throw new IOException("Backend returned HTTP " + backendResponse.statusCode());
            }
            request.setAttribute("overview", mapper.readValue(backendResponse.body(), PlanningOverviewView.class));
            request.setAttribute("employees", get("/employees"));
            request.setAttribute("workplaces", get("/workplaces"));
            request.setAttribute("notifications", get("/notifications"));
            String auditId = request.getParameter("audit");
            if (auditId != null) {
                request.setAttribute("events", get("/shifts/" + UUID.fromString(auditId) + "/events"));
            }
        } catch (IllegalArgumentException exception) {
            request.setAttribute("error", "Neplatný identifikátor směny.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            request.setAttribute("error", "Načítání plánování bylo přerušeno.");
        } catch (IOException exception) {
            request.setAttribute("error", "Backend pro plánování není dostupný: " + exception.getMessage());
        }
        request.getRequestDispatcher("/WEB-INF/views/planning/index.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String id = request.getParameter("id");
        try {
            HttpRequest backendRequest;
            String action = request.getParameter("action");
            if ("create".equals(action) || "update".equals(action)) {
                Map<String, Object> values = new LinkedHashMap<>();
                String employeeId = request.getParameter("employeeId");
                values.put("employeeId", employeeId == null || employeeId.isBlank() ? null : UUID.fromString(employeeId));
                for (String field : java.util.List.of("roleName", "department", "startAt", "endAt")) values.put(field, request.getParameter(field));
                boolean update = "update".equals(action);
                if (update) values.put("version", Long.parseLong(request.getParameter("version")));
                String path = update ? "/shifts/" + UUID.fromString(id) : "/shifts";
                backendRequest = jsonRequest(backendUrl + "/api/v1/planning" + path, update ? "PUT" : "POST", mapper.writeValueAsString(values));
            } else if ("publishPlan".equals(action)) {
                String[] ids = request.getParameterValues("shiftIds");
                if (ids == null) throw new IllegalArgumentException("No shifts selected");
                backendRequest = jsonRequest(backendUrl + "/api/v1/planning/publish", "POST", mapper.writeValueAsString(
                        Map.of("shiftIds", java.util.Arrays.stream(ids).map(UUID::fromString).toList())));
            } else if ("workplace".equals(action)) {
                backendRequest = jsonRequest(backendUrl + "/api/v1/planning/workplaces", "POST", mapper.writeValueAsString(
                        Map.of("name", request.getParameter("name"), "capacity", Integer.parseInt(request.getParameter("capacity")))));
            } else if ("read".equals(action)) {
                backendRequest = jsonRequest(backendUrl + "/api/v1/planning/notifications/" + UUID.fromString(id) + "/read", "PATCH", "{}");
            } else if ("delete".equals(action)) {
                backendRequest = jsonRequest(backendUrl + "/api/v1/planning/shifts/" + UUID.fromString(id), "DELETE", "{}");
            } else if ("publish".equals(action)) {
                UUID.fromString(id);
                backendRequest = com.example.erp.frontend.base.BackendRequests.newBuilder(URI.create(backendUrl + "/api/v1/planning/shifts/" + id + "/publish"))
                        .method("PATCH", HttpRequest.BodyPublishers.noBody()).build();
            } else {
                throw new IllegalArgumentException("Unknown action");
            }
            HttpResponse<String> backendResponse = client.send(backendRequest, HttpResponse.BodyHandlers.ofString());
            check(backendResponse);
            response.sendRedirect("planning?message=" + URLEncoder.encode("Změna byla uložena.", StandardCharsets.UTF_8));
        } catch (IllegalArgumentException exception) {
            response.sendRedirect("planning?error=" + URLEncoder.encode("Neplatná směna.", StandardCharsets.UTF_8));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            response.sendRedirect("planning?error=" + URLEncoder.encode("Publikace směny byla přerušena.", StandardCharsets.UTF_8));
        } catch (IOException exception) {
            response.sendRedirect("planning?error=" + URLEncoder.encode(exception.getMessage(), StandardCharsets.UTF_8));
        }
    }

    private com.fasterxml.jackson.databind.JsonNode get(String path) throws IOException, InterruptedException {
        HttpResponse<String> result = client.send(com.example.erp.frontend.base.BackendRequests.newBuilder(
                URI.create(backendUrl + "/api/v1/planning" + path)).timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofString());
        check(result);
        return mapper.readTree(result.body());
    }

    private void check(HttpResponse<String> result) throws IOException {
        if (result.statusCode() >= 200 && result.statusCode() < 300) return;
        String detail = "Backend HTTP " + result.statusCode();
        if (result.body() != null && !result.body().isBlank()) detail = mapper.readTree(result.body()).path("detail").asText(detail);
        throw new IOException(detail);
    }

    private HttpRequest jsonRequest(String url, String method, String body) {
        return com.example.erp.frontend.base.BackendRequests.newBuilder(URI.create(url)).header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(body)).build();
    }
}