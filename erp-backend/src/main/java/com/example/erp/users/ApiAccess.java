package com.example.erp.users;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class ApiAccess implements HandlerInterceptor {
    private final JdbcTemplate jdbc;
    private final UserRepository users;

    public ApiAccess(JdbcTemplate jdbc, UserRepository users) {
        this.jdbc = jdbc;
        this.users = users;
    }

    public String issueToken(UUID userId) {
        byte[] random = new byte[32];
        new SecureRandom().nextBytes(random);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        jdbc.update("DELETE FROM api_sessions WHERE expires_at < CURRENT_TIMESTAMP");
        jdbc.update("INSERT INTO api_sessions (token_hash, user_id, expires_at) VALUES (?, ?, ?)",
                hash(token), userId, LocalDateTime.now().plusHours(8));
        return token;
    }

    public void revoke(String authorization) {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            jdbc.update("DELETE FROM api_sessions WHERE token_hash = ?", hash(authorization.substring(7)));
        }
    }

    public void revokeUser(UUID userId) {
        jdbc.update("DELETE FROM api_sessions WHERE user_id = ?", userId);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if ("OPTIONS".equals(request.getMethod()) || "/api/v1/auth/login".equals(path)) return true;
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ") || authorization.length() > 100) unauthorized();
        UUID userId = jdbc.query("SELECT user_id FROM api_sessions WHERE token_hash = ? AND expires_at > CURRENT_TIMESTAMP",
                (row, index) -> row.getObject("user_id", UUID.class), hash(authorization.substring(7)))
                .stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session expired."));
        ErpUser user = users.findById(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (user.getStatus() != UserStatus.ACTIVE) unauthorized();
        String module = path.substring("/api/v1/".length()).split("/")[0];
        if (!"auth".equals(module) && !hasPermission(user.getRoleName(), module, requiredPermission(path, request.getMethod()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Role does not permit this operation.");
        }
        request.setAttribute("erpUser", user);
        return true;
    }

    private Permission requiredPermission(String path, String method) {
        if ("/api/v1/roles/matrix".equals(path) && !Set.of("GET", "HEAD").contains(method)) {
            return Permission.MANAGE;
        }
        if (path.startsWith("/api/v1/settings/") && !Set.of("GET", "HEAD").contains(method)) {
            return Permission.MANAGE;
        }
        return switch (method) {
            case "GET", "HEAD" -> Permission.READ;
            case "POST" -> Permission.INSERT;
            case "PUT", "PATCH" -> Permission.EDIT;
            case "DELETE" -> Permission.DELETE;
            default -> Permission.READ;
        };
    }

    private boolean hasPermission(String roleName, String module, Permission permission) {
        String column = permission.column;
        Integer matches = jdbc.queryForObject(
                "SELECT COUNT(*) FROM role_definitions rd "
                    + "JOIN role_module_permissions assignment ON assignment.role_id = rd.id "
                    + "WHERE lower(rd.name) = lower(?) AND assignment.module_key = ? AND rd." + column + " = TRUE",
                Integer.class, roleName, module);
        return matches != null && matches > 0;
    }

    private enum Permission {
        READ("can_read"), INSERT("can_insert"), EDIT("can_edit"), DELETE("can_delete"), MANAGE("can_manage");

        private final String column;

        Permission(String column) { this.column = column; }
    }

    public static boolean allowed(String role, String module, boolean read) {
        String normalized = normalize(role);
        if (isAdmin(role)) return true;
        return switch (module) {
            case "auth" -> true;
            case "accounting" -> Set.of("ucetni", "accountant").contains(normalized);
            case "hr" -> Set.of("hr", "personalista").contains(normalized)
                    || read && Set.of("vedouci tymu", "team lead", "zamestnanec", "employee").contains(normalized);
            case "planning" -> Set.of("hr", "personalista", "planovac", "planner", "vedouci tymu", "team lead").contains(normalized)
                    || read && Set.of("zamestnanec", "employee").contains(normalized);
            case "website" -> Set.of("editor", "schvalovatel", "approver").contains(normalized);
            case "marketing" -> Set.of("marketing", "marketer").contains(normalized);
            case "dashboard" -> true;
            case "users", "roles", "settings" -> false;
            case "sales", "crm", "promo", "catalog", "purchase" -> Set.of("nakupci", "sales", "marketing", "marketer", "ucetni", "accountant").contains(normalized);
            case "inventory", "planning-logistics" -> normalized.equals("logistika");
            default -> false;
        };
    }

    public static boolean isAdmin(String role) { return Set.of("administrator", "admin").contains(normalize(role)); }

    public static String normalize(String role) {
        return Normalizer.normalize(role == null ? "" : role, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void unauthorized() { throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required."); }
}