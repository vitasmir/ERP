package com.example.erp.frontend.hr;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet("/hr")
public class HrServlet extends HttpServlet {
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final String backendUrl = System.getenv().getOrDefault("BACKEND_URL", "http://localhost:8080");

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        try {
            HttpRequest backendRequest = com.example.erp.frontend.base.BackendRequests.newBuilder(URI.create(backendUrl + "/api/v1/hr/overview"))
                    .timeout(Duration.ofSeconds(5)).GET().build();
            HttpResponse<String> backendResponse = client.send(backendRequest, HttpResponse.BodyHandlers.ofString());
            if (backendResponse.statusCode() != HttpServletResponse.SC_OK) {
                throw new IOException("Backend returned HTTP " + backendResponse.statusCode());
            }
            request.setAttribute("overview", mapper.readValue(backendResponse.body(), HrOverviewView.class));
            String employeeId = request.getParameter("employeeId");
            if (employeeId != null) {
                HttpResponse<String> availability = client.send(com.example.erp.frontend.base.BackendRequests.newBuilder(
                        URI.create(backendUrl + "/api/v1/hr/employees/" + UUID.fromString(employeeId) + "/availability"))
                        .timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofString());
                check(availability);
                request.setAttribute("availability", mapper.readTree(availability.body()));
                request.setAttribute("selectedEmployeeId", UUID.fromString(employeeId));
            }
        } catch (IllegalArgumentException exception) {
            request.setAttribute("error", "Neplatný identifikátor zaměstnance.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            request.setAttribute("error", "Načítání lidí bylo přerušeno.");
        } catch (IOException exception) {
            request.setAttribute("error", "Backend pro lidi není dostupný: " + exception.getMessage());
        }
        request.getRequestDispatcher("/WEB-INF/views/hr/index.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String id = request.getParameter("id");
        try {
            HttpRequest backendRequest;
            String action = request.getParameter("action");
            if ("create".equals(action) || "update".equals(action)) {
                String body = mapper.writeValueAsString(Map.of("fullName", request.getParameter("fullName"), "teamName", request.getParameter("teamName"),
                        "jobTitle", request.getParameter("jobTitle"), "employmentStartDate", request.getParameter("employmentStartDate")));
                boolean update = "update".equals(action);
                backendRequest = jsonRequest(backendUrl + "/api/v1/hr/employees" + (update ? "/" + UUID.fromString(id) : ""), update ? "PUT" : "POST", body);
            } else if ("absence".equals(action)) {
                backendRequest = jsonRequest(backendUrl + "/api/v1/hr/employees/" + UUID.fromString(id) + "/absences", "POST",
                        mapper.writeValueAsString(Map.of("startAt", request.getParameter("startAt"), "endAt", request.getParameter("endAt"), "reason", request.getParameter("reason"))));
            } else if ("qualification".equals(action)) {
                backendRequest = jsonRequest(backendUrl + "/api/v1/hr/employees/" + UUID.fromString(id) + "/qualifications", "POST",
                        mapper.writeValueAsString(Map.of("roleName", request.getParameter("roleName"))));
            } else if ("removeAbsence".equals(action)) {
                backendRequest = jsonRequest(backendUrl + "/api/v1/hr/employees/" + UUID.fromString(id) + "/absences/"
                        + UUID.fromString(request.getParameter("absenceId")), "DELETE", "{}");
            } else if ("activate".equals(action) || "deactivate".equals(action)) {
                UUID.fromString(id);
                String operation = "deactivate".equals(request.getParameter("action")) ? "deactivate" : "activate";
                backendRequest = com.example.erp.frontend.base.BackendRequests.newBuilder(URI.create(backendUrl + "/api/v1/hr/employees/" + id + "/" + operation))
                        .method("PATCH", HttpRequest.BodyPublishers.noBody()).build();
            } else {
                throw new IllegalArgumentException("Unknown action");
            }
            HttpResponse<String> backendResponse = client.send(backendRequest, HttpResponse.BodyHandlers.ofString());
            check(backendResponse);
            response.sendRedirect("hr?message=" + URLEncoder.encode("Změna byla uložena.", StandardCharsets.UTF_8)
                    + (id == null ? "" : "&employeeId=" + UUID.fromString(id)));
        } catch (IllegalArgumentException exception) {
            response.sendRedirect("hr?error=" + URLEncoder.encode("Neplatný zaměstnanec.", StandardCharsets.UTF_8));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            response.sendRedirect("hr?error=" + URLEncoder.encode("Aktivace nástupu byla přerušena.", StandardCharsets.UTF_8));
        } catch (IOException exception) {
            String message = exception.getMessage();
            response.sendRedirect("hr?error=" + URLEncoder.encode(message == null ? "Uložení zaměstnance selhalo." : message, StandardCharsets.UTF_8));
        }
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