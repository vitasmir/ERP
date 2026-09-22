package com.example.erp.frontend.promo;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet("/promo")
public class PromoCampaignServlet extends HttpServlet {
    private static final Set<String> STATUSES = Set.of("PLANNED", "ACTIVE", "COMPLETED", "CANCELLED");
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final String backendUrl = System.getenv().getOrDefault("BACKEND_URL", "http://localhost:8080");

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        try {
            HttpRequest backendRequest = HttpRequest.newBuilder(URI.create(backendUrl + "/api/v1/promo-campaigns"))
                    .timeout(Duration.ofSeconds(5)).GET().build();
            HttpResponse<String> backendResponse = client.send(backendRequest, HttpResponse.BodyHandlers.ofString());
            if (backendResponse.statusCode() != HttpServletResponse.SC_OK) throw new IOException("Backend returned HTTP " + backendResponse.statusCode());
            List<PromoCampaignView> campaigns = mapper.readValue(backendResponse.body(), new TypeReference<>() { });
            request.setAttribute("campaigns", campaigns);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            request.setAttribute("error", "Načítání kampaní bylo přerušeno.");
        } catch (IOException exception) {
            request.setAttribute("error", "Backend pro promo kampaně není dostupný: " + exception.getMessage());
        }
        request.getRequestDispatcher("/WEB-INF/views/promo/index.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String id = request.getParameter("id");
        String status = request.getParameter("status");
        if (!isValidRequest(id, status)) {
            response.sendRedirect("promo?error=" + URLEncoder.encode("Neplatná změna stavu.", StandardCharsets.UTF_8));
            return;
        }
        HttpRequest backendRequest = HttpRequest.newBuilder(URI.create(backendUrl + "/api/v1/promo-campaigns/" + id + "/status"))
                .header("Content-Type", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString("{\"status\":\"" + status + "\"}"))
                .build();
        try {
            HttpResponse<Void> backendResponse = client.send(backendRequest, HttpResponse.BodyHandlers.discarding());
            String message = backendResponse.statusCode() == HttpServletResponse.SC_OK ? "Stav kampaně byl změněn." : "Změnu stavu backend odmítl.";
            response.sendRedirect("promo?message=" + URLEncoder.encode(message, StandardCharsets.UTF_8));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            response.sendRedirect("promo?error=" + URLEncoder.encode("Změna stavu byla přerušena.", StandardCharsets.UTF_8));
        }
    }

    private boolean isValidRequest(String id, String status) {
        try { UUID.fromString(id); return STATUSES.contains(status); }
        catch (IllegalArgumentException exception) { return false; }
    }
}