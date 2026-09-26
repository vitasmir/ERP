package com.example.erp.frontend.base;

import java.io.IOException;
import java.net.URI;
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
        if (!publicPage) response.setHeader("Cache-Control", "no-store");
        BackendRequests.TOKEN.set(token);
        try {
            chain.doFilter(request, response);
        } finally {
            BackendRequests.TOKEN.remove();
        }
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
}