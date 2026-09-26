package com.example.erp.frontend.base;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet({"/roles", "/role-modules"})
public class RolesServlet extends HttpServlet {
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final String backendUrl = System.getenv().getOrDefault("BACKEND_URL", "http://localhost:8080");

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        boolean moduleRoles = "/role-modules".equals(request.getServletPath());
        try {
            HttpRequest backendRequest = com.example.erp.frontend.base.BackendRequests.newBuilder(URI.create(backendUrl + "/api/v1/roles"))
                    .timeout(Duration.ofSeconds(5)).GET().build();
            HttpResponse<String> backendResponse = client.send(backendRequest, HttpResponse.BodyHandlers.ofString());
            if (backendResponse.statusCode() != HttpServletResponse.SC_OK) {
                throw new IOException("Backend returned HTTP " + backendResponse.statusCode());
            }
            request.setAttribute("roles", Arrays.asList(mapper.readValue(backendResponse.body(), RoleView[].class)));
            HttpRequest matrixRequest = com.example.erp.frontend.base.BackendRequests.newBuilder(URI.create(backendUrl + "/api/v1/roles/matrix"))
                    .timeout(Duration.ofSeconds(5)).GET().build();
            HttpResponse<String> matrixResponse = client.send(matrixRequest, HttpResponse.BodyHandlers.ofString());
            if (matrixResponse.statusCode() != HttpServletResponse.SC_OK) {
                throw new IOException("Backend returned HTTP " + matrixResponse.statusCode());
            }
            request.setAttribute("matrix", mapper.readValue(matrixResponse.body(), MatrixView.class));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            request.setAttribute("error", "Načítání rolí bylo přerušeno.");
        } catch (IOException exception) {
            request.setAttribute("error", "Backend pro role není dostupný: " + exception.getMessage());
        }
        request.setAttribute("message", request.getParameter("message"));
        request.setAttribute("error", request.getParameter("error"));
        request.setAttribute("activePage", moduleRoles ? "role-modules" : "roles");
        request.setAttribute("pageTitle", moduleRoles ? "Role pro moduly" : "Role a oprávnění");
        request.setAttribute("breadcrumb", moduleRoles ? "BASE / MODULE ACCESS" : "BASE / ACCESS");
        request.getRequestDispatcher(moduleRoles
            ? "/WEB-INF/views/base/role-modules/index.jsp"
            : "/WEB-INF/views/base/roles/index.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        request.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String action = request.getParameter("action");
        try {
            if ("save-module-permissions".equals(action)) {
                saveModulePermissions(request, response);
                return;
            }
            String body = mapper.writeValueAsString(Map.of(
                    "name", request.getParameter("name"),
                    "initial", request.getParameter("initial"),
                    "description", request.getParameter("description"),
                    "canRead", request.getParameter("canRead") != null,
                    "canInsert", request.getParameter("canInsert") != null,
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

    private void saveModulePermissions(HttpServletRequest request, HttpServletResponse response)
            throws IOException, InterruptedException {
        List<PermissionRequest> permissions = new ArrayList<>();
        for (String parameter : request.getParameterMap().keySet()) {
            if (!parameter.startsWith("permission_")) continue;
            String[] parts = parameter.substring("permission_".length()).split("_", 2);
            if (parts.length == 2) permissions.add(new PermissionRequest(UUID.fromString(parts[0]), parts[1]));
        }
        String body = mapper.writeValueAsString(Map.of("permissions", permissions));
        HttpRequest backendRequest = com.example.erp.frontend.base.BackendRequests
                .newBuilder(URI.create(backendUrl + "/api/v1/roles/matrix"))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body)).build();
        HttpResponse<String> backendResponse = client.send(backendRequest, HttpResponse.BodyHandlers.ofString());
        String parameter = backendResponse.statusCode() >= 200 && backendResponse.statusCode() < 300 ? "message" : "error";
        String message = "message".equals(parameter) ? "Oprávnění modulů byla uložena." : "Oprávnění se nepodařilo uložit.";
        response.sendRedirect("role-modules?" + parameter + "=" + encode(message));
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
            boolean canRead, boolean canInsert, boolean canEdit, boolean canManage, String color, long userCount) { }

    public record MatrixView(List<ModuleView> modules, List<PermissionView> permissions) { }
    public record ModuleView(String key, String name) { }
    public record PermissionView(UUID roleId, String moduleKey) { }
    public record PermissionRequest(UUID roleId, String moduleKey) { }
}
