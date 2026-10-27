package com.example.erp.frontend.base;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@WebServlet({"/login", "/logout"})
public class LoginServlet extends HttpServlet {
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final String backendUrl = System.getenv().getOrDefault("BACKEND_URL", "http://localhost:8080");

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setHeader("Cache-Control", "no-store");
        request.getRequestDispatcher("/WEB-INF/views/login.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        try {
            if ("/logout".equals(request.getServletPath())) {
                try {
                    client.send(BackendRequests.newBuilder(URI.create(backendUrl + "/api/v1/auth/logout"))
                            .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.discarding());
                } finally {
                    HttpSession session = request.getSession(false);
                    if (session != null) session.invalidate();
                }
                response.sendRedirect("login");
                return;
            }
            String username = request.getParameter("username");
            String password = request.getParameter("password");
            if (username == null || password == null) { response.sendError(400); return; }
            HttpResponse<String> result = client.send(BackendRequests.newBuilder(URI.create(backendUrl + "/api/v1/auth/login"))
                    .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(
                            mapper.writeValueAsString(Map.of("username", username, "password", password)))).build(), HttpResponse.BodyHandlers.ofString());
            if (result.statusCode() != 200) {
                request.setAttribute("error", "Přihlášení se nezdařilo.");
                doGet(request, response);
                return;
            }
            JsonNode user = mapper.readTree(result.body());
            HttpSession previous = request.getSession(false);
            if (previous != null) previous.invalidate();
            HttpSession session = request.getSession(true);
            session.setAttribute("backendToken", user.path("token").asText());
            session.setAttribute("userName", user.path("fullName").asText());
            session.setAttribute("roleName", user.path("roleName").asText());
            session.setAttribute("expiresAt", System.currentTimeMillis() + Duration.ofHours(8).toMillis());
            session.setMaxInactiveInterval(1800);
            response.sendRedirect("apps");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            response.sendError(503);
        } catch (IOException exception) {
            request.setAttribute("error", "Přihlašovací služba není dostupná.");
            doGet(request, response);
        }
    }
}