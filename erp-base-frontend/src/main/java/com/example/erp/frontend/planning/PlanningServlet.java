package com.example.erp.frontend.planning;

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

@WebServlet("/planning")
public class PlanningServlet extends HttpServlet {
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final String backendUrl = System.getenv().getOrDefault("BACKEND_URL", "http://localhost:8080");

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        try {
            HttpRequest backendRequest = HttpRequest.newBuilder(URI.create(backendUrl + "/api/v1/planning/overview"))
                    .timeout(Duration.ofSeconds(5)).GET().build();
            HttpResponse<String> backendResponse = client.send(backendRequest, HttpResponse.BodyHandlers.ofString());
            if (backendResponse.statusCode() != HttpServletResponse.SC_OK) {
                throw new IOException("Backend returned HTTP " + backendResponse.statusCode());
            }
            request.setAttribute("overview", mapper.readValue(backendResponse.body(), PlanningOverviewView.class));
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
            if ("create".equals(request.getParameter("action"))) {
                String body = mapper.writeValueAsString(Map.of("employeeName", request.getParameter("employeeName"), "roleName", request.getParameter("roleName"),
                        "department", request.getParameter("department"), "startAt", request.getParameter("startAt"), "endAt", request.getParameter("endAt")));
                backendRequest = jsonRequest(backendUrl + "/api/v1/planning/shifts", "POST", body);
            } else if ("assign".equals(request.getParameter("action"))) {
                UUID.fromString(id);
                String body = mapper.writeValueAsString(Map.of("employeeName", request.getParameter("employeeName")));
                backendRequest = jsonRequest(backendUrl + "/api/v1/planning/shifts/" + id + "/assign", "PATCH", body);
            } else {
                UUID.fromString(id);
                backendRequest = HttpRequest.newBuilder(URI.create(backendUrl + "/api/v1/planning/shifts/" + id + "/publish"))
                        .method("PATCH", HttpRequest.BodyPublishers.noBody()).build();
            }
            HttpResponse<Void> backendResponse = client.send(backendRequest, HttpResponse.BodyHandlers.discarding());
            String message = backendResponse.statusCode() == HttpServletResponse.SC_OK
                    ? "Směna byla publikována." : "Publikaci směny backend odmítl.";
            response.sendRedirect("planning?message=" + URLEncoder.encode(message, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException exception) {
            response.sendRedirect("planning?error=" + URLEncoder.encode("Neplatná směna.", StandardCharsets.UTF_8));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            response.sendRedirect("planning?error=" + URLEncoder.encode("Publikace směny byla přerušena.", StandardCharsets.UTF_8));
        }
    }

    private HttpRequest jsonRequest(String url, String method, String body) {
        return HttpRequest.newBuilder(URI.create(url)).header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(body)).build();
    }
}