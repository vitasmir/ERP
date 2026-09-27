package com.example.erp.frontend.manufacturing;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet("/manufacturing")
public class ManufacturingServlet extends HttpServlet {
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final String backendUrl = System.getenv().getOrDefault("BACKEND_URL", "http://localhost:8080");

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        try {
            HttpRequest backendRequest = com.example.erp.frontend.base.BackendRequests.newBuilder(URI.create(backendUrl + "/api/v1/manufacturing/overview"))
                    .timeout(Duration.ofSeconds(5)).GET().build();
            HttpResponse<String> backendResponse = client.send(backendRequest, HttpResponse.BodyHandlers.ofString());
            if (backendResponse.statusCode() != HttpServletResponse.SC_OK) {
                throw new IOException("Backend returned HTTP " + backendResponse.statusCode());
            }
            request.setAttribute("overview", mapper.readValue(backendResponse.body(), ManufacturingOverviewView.class));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            request.setAttribute("error", "Načítání výroby bylo přerušeno.");
        } catch (IOException exception) {
            request.setAttribute("error", "Backend pro výrobu není dostupný: " + exception.getMessage());
        }
        request.getRequestDispatcher("/WEB-INF/views/manufacturing/index.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String id = request.getParameter("id");
        try {
            UUID.fromString(id);
            String action = request.getParameter("action");
            HttpRequest backendRequest;
            String successMessage;
            if ("update".equals(action)) {
                int completedQuantity = Integer.parseInt(request.getParameter("completedQuantity"));
                String body = mapper.writeValueAsString(java.util.Map.of("completedQuantity", completedQuantity));
                backendRequest = com.example.erp.frontend.base.BackendRequests.newBuilder(URI.create(backendUrl + "/api/v1/manufacturing/orders/" + id + "/progress"))
                        .header("Content-Type", "application/json")
                        .method("PATCH", HttpRequest.BodyPublishers.ofString(body)).build();
                successMessage = "Počet vyrobených kusů byl upraven.";
            } else {
                backendRequest = com.example.erp.frontend.base.BackendRequests.newBuilder(URI.create(backendUrl + "/api/v1/manufacturing/orders/" + id + "/complete"))
                        .method("PATCH", HttpRequest.BodyPublishers.noBody()).build();
                successMessage = "Výrobní příkaz byl dokončen.";
            }
            HttpResponse<Void> backendResponse = client.send(backendRequest, HttpResponse.BodyHandlers.discarding());
            String message = backendResponse.statusCode() == HttpServletResponse.SC_OK
                    ? successMessage : "Změnu výroby backend odmítl.";
            response.sendRedirect("manufacturing?message=" + URLEncoder.encode(message, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException | NullPointerException exception) {
            response.sendRedirect("manufacturing?error=" + URLEncoder.encode("Neplatný výrobní příkaz.", StandardCharsets.UTF_8));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            response.sendRedirect("manufacturing?error=" + URLEncoder.encode("Změna stavu byla přerušena.", StandardCharsets.UTF_8));
        }
    }
}