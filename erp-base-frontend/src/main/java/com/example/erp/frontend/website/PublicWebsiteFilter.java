package com.example.erp.frontend.website;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebFilter("/*")
public class PublicWebsiteFilter implements Filter {
    private static final Set<String> APPLICATION_PATHS = Set.of("", "/", "/apps", "/dashboard", "/roles", "/role-modules",
            "/companies", "/settings", "/users", "/accounting", "/crm", "/documents", "/ecommerce", "/helpdesk",
            "/hr", "/inventory", "/manufacturing", "/marketing", "/planning", "/pos", "/projects", "/promo",
            "/purchase", "/sales", "/website", "/login", "/logout", "/shop", "/eshop");
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final String backendUrl = System.getenv().getOrDefault("BACKEND_URL", "http://localhost:8080");

    @Override
    public void doFilter(ServletRequest source, ServletResponse target, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) source;
        HttpServletResponse response = (HttpServletResponse) target;
        if (!"GET".equals(request.getMethod()) || request.getDispatcherType() != jakarta.servlet.DispatcherType.REQUEST) {
            chain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (path.startsWith("/assets/") || path.endsWith(".jsp") || APPLICATION_PATHS.contains(path)) {
            chain.doFilter(request, response);
            return;
        }

        HttpRequest pageRequest = com.example.erp.frontend.base.BackendRequests.newBuilder(
                URI.create(backendUrl + "/api/v1/website/pages/public?slug="
                        + URLEncoder.encode(path, StandardCharsets.UTF_8)))
                .timeout(Duration.ofSeconds(5)).GET().build();
        HttpResponse<String> pageResponse;
        try {
            pageResponse = client.send(pageRequest, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            chain.doFilter(request, response);
            return;
        }
        if (pageResponse.statusCode() != HttpServletResponse.SC_OK) {
            chain.doFilter(request, response);
            return;
        }

        request.setAttribute("page", mapper.readValue(pageResponse.body(), PublicPageView.class));
        recordVisit(path);
        request.getRequestDispatcher("/WEB-INF/views/website/public.jsp").forward(request, response);
    }

    private void recordVisit(String slug) throws IOException {
        HttpRequest visitRequest = com.example.erp.frontend.base.BackendRequests.newBuilder(
                URI.create(backendUrl + "/api/v1/website/pages/visit?slug="
                        + URLEncoder.encode(slug, StandardCharsets.UTF_8)))
                .POST(HttpRequest.BodyPublishers.noBody()).build();
        try {
            client.send(visitRequest, HttpResponse.BodyHandlers.discarding());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}