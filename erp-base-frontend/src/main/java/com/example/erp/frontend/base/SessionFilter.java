package com.example.erp.frontend.base;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Set;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@WebFilter("/*")
public class SessionFilter implements Filter {
    private static final Map<String, ModuleCheck> MODULE_CHECKS = Map.ofEntries(
            Map.entry("/accounting", new ModuleCheck("/api/v1/accounting/overview", "Účetnictví")),
            Map.entry("/crm", new ModuleCheck("/api/v1/crm/overview", "CRM")),
            Map.entry("/documents", new ModuleCheck("/api/v1/documents/overview", "Dokumenty")),
            Map.entry("/ecommerce", new ModuleCheck("/api/v1/catalog/homepage", "eCommerce")),
            Map.entry("/helpdesk", new ModuleCheck("/api/v1/helpdesk/overview", "Helpdesk")),
            Map.entry("/hr", new ModuleCheck("/api/v1/hr/overview", "Lidé")),
            Map.entry("/inventory", new ModuleCheck("/api/v1/inventory/overview", "Sklad")),
            Map.entry("/manufacturing", new ModuleCheck("/api/v1/manufacturing/overview", "Výroba")),
            Map.entry("/marketing", new ModuleCheck("/api/v1/marketing/overview", "Marketing")),
            Map.entry("/planning", new ModuleCheck("/api/v1/planning/overview", "Plánování")),
            Map.entry("/pos", new ModuleCheck("/api/v1/pos/overview", "Pokladna")),
            Map.entry("/projects", new ModuleCheck("/api/v1/projects/overview", "Projekty")),
            Map.entry("/promo", new ModuleCheck("/api/v1/promo-campaigns", "Promo kampaně")),
            Map.entry("/purchase", new ModuleCheck("/api/v1/purchase/overview", "Nákup")),
            Map.entry("/sales", new ModuleCheck("/api/v1/sales/overview", "Prodej")),
            Map.entry("/website", new ModuleCheck("/api/v1/website/overview", "Web")));
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final String backendUrl = System.getenv().getOrDefault("BACKEND_URL", "http://localhost:8080");

    @Override
    public void doFilter(ServletRequest source, ServletResponse target, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) source;
        HttpServletResponse response = (HttpServletResponse) target;
        request.setCharacterEncoding("UTF-8");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Referrer-Policy", "same-origin");
        String path = request.getServletPath();
        boolean publicPage = path.startsWith("/assets/") || Set.of("/login", "/shop", "/eshop").contains(path);
        if (!Set.of("GET", "HEAD", "OPTIONS").contains(request.getMethod()) && !sameOrigin(request)) {
            response.sendError(403, "Request origin could not be verified.");
            return;
        }
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute("expiresAt") instanceof Long expiresAt && expiresAt < System.currentTimeMillis()) {
            session.invalidate();
            session = null;
        }
        String token = session == null ? null : (String) session.getAttribute("backendToken");
        if (!publicPage && token == null) {
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }
        if (!path.startsWith("/assets/")) {
            response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
            response.setHeader("Pragma", "no-cache");
            response.setDateHeader("Expires", 0);
        }
        BackendRequests.TOKEN.set(token);
        try {
            if ("GET".equals(request.getMethod()) && !checkModuleAccess(request, response)) return;
            chain.doFilter(request, response);
        } finally {
            BackendRequests.TOKEN.remove();
        }
    }

    private boolean checkModuleAccess(HttpServletRequest request, HttpServletResponse response) throws IOException {
        ModuleCheck module = MODULE_CHECKS.get(request.getServletPath());
        if (module == null) return true;
        try {
            HttpResponse<Void> backendResponse = client.send(
                    BackendRequests.newBuilder(URI.create(backendUrl + module.endpoint())).GET().build(),
                    HttpResponse.BodyHandlers.discarding());
            if (backendResponse.statusCode() == HttpServletResponse.SC_FORBIDDEN) {
                String message = "Uživatel nemá oprávnění k modulu " + module.name() + ".";
                response.sendRedirect(request.getContextPath() + "/apps?error="
                        + URLEncoder.encode(message, StandardCharsets.UTF_8));
                return false;
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (IOException exception) { }
        return true;
    }

    private boolean sameOrigin(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (origin == null) origin = request.getHeader("Referer");
        if (origin == null) return false;
        try {
            URI actual = URI.create(origin);
            URI expected = URI.create(request.getRequestURL().toString());
            return actual.getScheme() != null && actual.getScheme().equals(expected.getScheme())
                    && actual.getHost() != null && actual.getHost().equalsIgnoreCase(expected.getHost())
                    && port(actual) == port(expected);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private int port(URI uri) { return uri.getPort() != -1 ? uri.getPort() : "https".equals(uri.getScheme()) ? 443 : 80; }

    private record ModuleCheck(String endpoint, String name) { }
}