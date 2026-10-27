package com.example.erp.frontend.base;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet({"", "/apps"})
public class AppPageServlet extends HttpServlet {
    private static final Map<String, String> MODULE_KEYS = Map.ofEntries(
            Map.entry("accounting", "accounting"), Map.entry("crm", "crm"), Map.entry("sales", "sales"),
            Map.entry("purchase", "purchase"), Map.entry("inventory", "inventory"),
            Map.entry("manufacturing", "manufacturing"), Map.entry("promotions", "promo-campaigns"),
            Map.entry("pos", "pos"), Map.entry("hr", "hr"), Map.entry("documents", "documents"),
            Map.entry("project", "projects"), Map.entry("helpdesk", "helpdesk"), Map.entry("website", "website"),
            Map.entry("ecommerce", "catalog"), Map.entry("marketing", "marketing"),
            Map.entry("planning", "planning"), Map.entry("dashboard", "dashboard"));
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final String backendUrl = System.getenv().getOrDefault("BACKEND_URL", "http://localhost:8080");
    private static final Map<String, Page> PAGES = Map.of(
            "", new Page("apps", "Přehled systému", "BASE / ADMINISTRATION", "apps/index.jsp"),
            "/apps", new Page("apps", "Přehled systému", "BASE / ADMINISTRATION", "apps/index.jsp"));

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        Page page = PAGES.get(request.getServletPath());
        if (page == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        request.setAttribute("activePage", page.activePage());
        request.setAttribute("pageTitle", page.title());
        request.setAttribute("breadcrumb", page.breadcrumb());
        request.setAttribute("allowedModules", allowedModules());
        request.getRequestDispatcher("/WEB-INF/views/" + page.view()).forward(request, response);
    }

    private Set<String> allowedModules() {
        try {
            HttpRequest backendRequest = BackendRequests.newBuilder(URI.create(backendUrl + "/api/v1/auth/me"))
                    .timeout(Duration.ofSeconds(5)).GET().build();
            HttpResponse<String> response = client.send(backendRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != HttpServletResponse.SC_OK) return Set.of();
            AccessView access = mapper.readValue(response.body(), AccessView.class);
            Set<String> allowed = new HashSet<>();
            if (access.administrator()) {
                allowed.addAll(MODULE_KEYS.keySet());
                allowed.add("base");
                return allowed;
            }
            access.modules().forEach(module -> MODULE_KEYS.forEach((tile, key) -> {
                if (key.equals(module)) allowed.add(tile);
            }));
            if (access.modules().stream().anyMatch(Set.of("companies", "users", "roles", "settings")::contains)) {
                allowed.add("base");
            }
            return allowed;
        } catch (IOException exception) {
            return Set.of();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return Set.of();
        }
    }

    private record Page(String activePage, String title, String breadcrumb, String view) { }
    private record AccessView(UUID id, String roleName, boolean administrator, List<String> modules) { }
}