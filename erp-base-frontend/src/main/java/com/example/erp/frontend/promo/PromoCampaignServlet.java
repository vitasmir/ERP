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
import java.util.Map;
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
                HttpResponse<String> optionsResponse = client.send(HttpRequest.newBuilder(
                    URI.create(backendUrl + "/api/v1/promo-campaigns/options")).timeout(Duration.ofSeconds(5)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
                if (optionsResponse.statusCode() != HttpServletResponse.SC_OK) throw new IOException("Backend returned HTTP " + optionsResponse.statusCode());
                request.setAttribute("options", mapper.readValue(optionsResponse.body(), PromoOptionsView.class));
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
        if ("create".equals(request.getParameter("action"))) {
            createCampaign(request, response);
            return;
        }
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

    private void createCampaign(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            String body = mapper.writeValueAsString(Map.of(
                    "name", request.getParameter("name"),
                    "productId", UUID.fromString(request.getParameter("productId")),
                    "supplierId", UUID.fromString(request.getParameter("supplierId")),
                    "startsOn", request.getParameter("startsOn"),
                    "endsOn", request.getParameter("endsOn"),
                    "regularPrice", new java.math.BigDecimal(request.getParameter("regularPrice")),
                    "promoPrice", new java.math.BigDecimal(request.getParameter("promoPrice")),
                    "supplierPurchasePrice", new java.math.BigDecimal(request.getParameter("supplierPurchasePrice")),
                    "plannedQuantity", Integer.parseInt(request.getParameter("plannedQuantity")),
                    "marketingContribution", new java.math.BigDecimal(request.getParameter("marketingContribution"))));
            HttpResponse<Void> backendResponse = client.send(HttpRequest.newBuilder(URI.create(backendUrl + "/api/v1/promo-campaigns"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.discarding());
            String parameter = backendResponse.statusCode() == HttpServletResponse.SC_CREATED ? "message" : "error";
            String message = backendResponse.statusCode() == HttpServletResponse.SC_CREATED
                    ? "Promo kampaň byla přidána." : "Backend odmítl vytvoření kampaně.";
            response.sendRedirect("promo?" + parameter + "=" + URLEncoder.encode(message, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException | NullPointerException exception) {
            response.sendRedirect("promo?error=" + URLEncoder.encode("Vyplňte platné údaje kampaně.", StandardCharsets.UTF_8));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            response.sendRedirect("promo?error=" + URLEncoder.encode("Vytvoření kampaně bylo přerušeno.", StandardCharsets.UTF_8));
        }
    }

    private boolean isValidRequest(String id, String status) {
        try { UUID.fromString(id); return STATUSES.contains(status); }
        catch (IllegalArgumentException exception) { return false; }
    }
}