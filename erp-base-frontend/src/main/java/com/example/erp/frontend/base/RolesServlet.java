package com.example.erp.frontend.base;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet("/roles")
public class RolesServlet extends HttpServlet {
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final String backendUrl = System.getenv().getOrDefault("BACKEND_URL", "http://localhost:8080");

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        try {
            HttpRequest backendRequest = com.example.erp.frontend.base.BackendRequests.newBuilder(URI.create(backendUrl + "/api/v1/roles"))
                    .timeout(Duration.ofSeconds(5)).GET().build();
            HttpResponse<String> backendResponse = client.send(backendRequest, HttpResponse.BodyHandlers.ofString());
            if (backendResponse.statusCode() != HttpServletResponse.SC_OK) {
                throw new IOException("Backend returned HTTP " + backendResponse.statusCode());
            }
            request.setAttribute("roles", Arrays.asList(mapper.readValue(backendResponse.body(), RoleView[].class)));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            request.setAttribute("error", "Načítání rolí bylo přerušeno.");
        } catch (IOException exception) {
            request.setAttribute("error", "Backend pro role není dostupný: " + exception.getMessage());
        }
        request.setAttribute("message", request.getParameter("message"));
        request.setAttribute("error", request.getParameter("error"));
        request.setAttribute("activePage", "roles");
        request.setAttribute("pageTitle", "Role a oprávnění");
        request.setAttribute("breadcrumb", "BASE / ACCESS");
        request.getRequestDispatcher("/WEB-INF/views/base/roles/index.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        request.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String action = request.getParameter("action");
        try {
            String body = mapper.writeValueAsString(Map.of(
                    "name", request.getParameter("name"),
                    "initial", request.getParameter("initial"),
                    "description", request.getParameter("description"),
                    "canRead", request.getParameter("canRead") != null,
                    "canEdit", request.getParameter("canEdit") != null,
                    "canManage", request.getParameter("canManage") != null,
                    "color", request.getParameter("color")));
            HttpRequest.Builder builder = com.example.erp.frontend.base.BackendRequests.newBuilder(roleUri(action, request.getParameter("id")))
                    .header("Content-Type", "application/json");
            HttpRequest backendRequest = "update".equals(action)
                    ? builder.PUT(HttpRequest.BodyPublishers.ofString(body)).build()
                    : builder.POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> backendResponse = client.send(backendRequest, HttpResponse.BodyHandlers.ofString());
            boolean successful = backendResponse.statusCode() >= 200 && backendResponse.statusCode() < 300;
            String parameter = successful ? "message" : "error";
            String message = successful ? ("update".equals(action) ? "Role byla aktualizována." : "Role byla vytvořena.")
                    : "Backend změnu role odmítl (HTTP " + backendResponse.statusCode() + ").";
            response.sendRedirect("roles?" + parameter + "=" + encode(message));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            response.sendRedirect("roles?error=" + encode("Změna role byla přerušena."));
        } catch (IllegalArgumentException exception) {
            response.sendRedirect("roles?error=" + encode("Vyplňte platné údaje role."));
        }
    }

    private URI roleUri(String action, String id) {
        if ("update".equals(action)) {
            UUID roleId = UUID.fromString(id);
            return URI.create(backendUrl + "/api/v1/roles/" + roleId);
        }
        return URI.create(backendUrl + "/api/v1/roles");
    }

    private String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }

    public static String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    public record RoleView(UUID id, String name, String initial, String description,
            boolean canRead, boolean canEdit, boolean canManage, String color, long userCount) { }
}
