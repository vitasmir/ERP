package com.example.erp.frontend.inventory;

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

@WebServlet("/inventory")
public class InventoryServlet extends HttpServlet {
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final String backendUrl = System.getenv().getOrDefault("BACKEND_URL", "http://localhost:8080");

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        try {
            HttpRequest backendRequest = HttpRequest.newBuilder(URI.create(backendUrl + "/api/v1/inventory/overview"))
                    .timeout(Duration.ofSeconds(5)).GET().build();
            HttpResponse<String> backendResponse = client.send(backendRequest, HttpResponse.BodyHandlers.ofString());
            if (backendResponse.statusCode() != HttpServletResponse.SC_OK) {
                throw new IOException("Backend returned HTTP " + backendResponse.statusCode());
            }
            request.setAttribute("overview", mapper.readValue(backendResponse.body(), InventoryOverviewView.class));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            request.setAttribute("error", "Načítání skladu bylo přerušeno.");
        } catch (IOException exception) {
            request.setAttribute("error", "Backend pro sklad není dostupný: " + exception.getMessage());
        }
        request.getRequestDispatcher("/WEB-INF/views/inventory/index.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String id = request.getParameter("id");
        String quantity = request.getParameter("quantity");
        try {
            UUID.fromString(id);
            int receivedQuantity = Integer.parseInt(quantity);
            if (receivedQuantity <= 0) throw new IllegalArgumentException();
            String requestBody = mapper.writeValueAsString(new ReceiveStockRequest(receivedQuantity));
            HttpRequest backendRequest = HttpRequest.newBuilder(URI.create(backendUrl + "/api/v1/inventory/items/" + id + "/receive"))
                    .header("Content-Type", "application/json")
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(requestBody)).build();
            HttpResponse<Void> backendResponse = client.send(backendRequest, HttpResponse.BodyHandlers.discarding());
            String message = backendResponse.statusCode() == HttpServletResponse.SC_OK
                    ? "Příjem zásoby byl zaevidován." : "Příjem zásoby backend odmítl.";
            response.sendRedirect("inventory?message=" + URLEncoder.encode(message, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException exception) {
            response.sendRedirect("inventory?error=" + URLEncoder.encode("Zadejte kladné množství příjmu.", StandardCharsets.UTF_8));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            response.sendRedirect("inventory?error=" + URLEncoder.encode("Příjem zásoby byl přerušen.", StandardCharsets.UTF_8));
        }
    }

    private record ReceiveStockRequest(int quantity) { }
}