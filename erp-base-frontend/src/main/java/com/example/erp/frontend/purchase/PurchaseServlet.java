package com.example.erp.frontend.purchase;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet("/purchase")
public class PurchaseServlet extends HttpServlet {
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final String backendUrl = System.getenv().getOrDefault("BACKEND_URL", "http://localhost:8080");

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        try {
            HttpRequest backendRequest = com.example.erp.frontend.base.BackendRequests.newBuilder(URI.create(backendUrl + "/api/v1/purchase/overview"))
                    .timeout(Duration.ofSeconds(5)).GET().build();
            HttpResponse<String> backendResponse = client.send(backendRequest, HttpResponse.BodyHandlers.ofString());
            if (backendResponse.statusCode() != HttpServletResponse.SC_OK) {
                throw new IOException("Backend returned HTTP " + backendResponse.statusCode());
            }
            request.setAttribute("overview", mapper.readValue(backendResponse.body(), PurchaseOverviewView.class));
                request.setAttribute("warehouses", getList("/api/v1/purchase/warehouses",
                    new TypeReference<List<PurchaseOverviewView.WarehouseView>>() { }));
                request.setAttribute("products", getList("/api/v1/catalog/products",
                    new TypeReference<List<PurchaseOverviewView.ProductView>>() { }));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            request.setAttribute("error", "Načítání nákupu bylo přerušeno.");
        } catch (IOException exception) {
            request.setAttribute("error", "Backend pro nákup není dostupný: " + exception.getMessage());
        }
        request.getRequestDispatcher("/WEB-INF/views/purchase/index.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String id = request.getParameter("id");
        try {
            if ("create".equals(request.getParameter("action")) || "update".equals(request.getParameter("action"))) {
            String[] productIds = request.getParameterValues("productId");
            String[] quantities = request.getParameterValues("quantity");
            String[] unitPrices = request.getParameterValues("unitPrice");
            if (productIds == null || quantities == null || unitPrices == null
                    || productIds.length != quantities.length || quantities.length != unitPrices.length) {
                throw new IllegalArgumentException("Missing purchase lines.");
            }
            List<PurchaseLineRequest> lines = new ArrayList<>();
            for (int index = 0; index < productIds.length; index++) {
                lines.add(new PurchaseLineRequest(UUID.fromString(productIds[index]), Integer.parseInt(quantities[index]),
                    new BigDecimal(unitPrices[index])));
            }
            String body = mapper.writeValueAsString(new CreatePurchaseOrderRequest(request.getParameter("supplierName"),
                LocalDate.parse(request.getParameter("requestedOn")), LocalDate.parse(request.getParameter("expectedDeliveryDate")),
                UUID.fromString(request.getParameter("sourceWarehouseId")),
                UUID.fromString(request.getParameter("destinationWarehouseId")), lines));
            boolean update = "update".equals(request.getParameter("action"));
            String endpoint = update ? backendUrl + "/api/v1/purchase/orders/" + UUID.fromString(id)
                : backendUrl + "/api/v1/purchase/orders";
            HttpRequest.Builder requestBuilder = com.example.erp.frontend.base.BackendRequests.newBuilder(URI.create(endpoint))
                .header("Content-Type", "application/json");
            HttpRequest backendRequest = update
                ? requestBuilder.PUT(HttpRequest.BodyPublishers.ofString(body)).build()
                : requestBuilder.POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<Void> backendResponse = client.send(backendRequest, HttpResponse.BodyHandlers.discarding());
                String message = (update ? backendResponse.statusCode() == HttpServletResponse.SC_OK
                    : backendResponse.statusCode() == HttpServletResponse.SC_CREATED)
                ? (update ? "Nákupní objednávka byla upravena." : "Nákupní objednávka byla vytvořena.")
                : "Objednávku se nepodařilo uložit.";
            response.sendRedirect("purchase?message=" + URLEncoder.encode(message, StandardCharsets.UTF_8));
            return;
            }
            UUID.fromString(id);
                String action = "receive".equals(request.getParameter("action")) ? "receive" : "order";
                HttpRequest.Builder requestBuilder = com.example.erp.frontend.base.BackendRequests.newBuilder(URI.create(backendUrl + "/api/v1/purchase/orders/" + id + "/" + action));
                HttpRequest backendRequest = "receive".equals(action)
                    ? requestBuilder.header("Content-Type", "application/json")
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(
                        new ReceivePurchaseRequest(request.getParameter("quantity") == null
                            ? null : Integer.parseInt(request.getParameter("quantity")))))).build()
                    : requestBuilder.method("PATCH", HttpRequest.BodyPublishers.noBody()).build();
            HttpResponse<Void> backendResponse = client.send(backendRequest, HttpResponse.BodyHandlers.discarding());
                String message = backendResponse.statusCode() == HttpServletResponse.SC_OK
                    ? ("receive".equals(action) ? "Zboží bylo přijato na sklad." : "Nákupní objednávka byla vystavena.")
                    : "Změnu stavu backend odmítl.";
            response.sendRedirect("purchase?message=" + URLEncoder.encode(message, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException exception) {
            response.sendRedirect("purchase?error=" + URLEncoder.encode("Neplatný nákupní požadavek.", StandardCharsets.UTF_8));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            response.sendRedirect("purchase?error=" + URLEncoder.encode("Změna stavu byla přerušena.", StandardCharsets.UTF_8));
        }
    }

    private record CreatePurchaseOrderRequest(String supplierName, LocalDate requestedOn,
            LocalDate expectedDeliveryDate, UUID sourceWarehouseId,
            UUID destinationWarehouseId, List<PurchaseLineRequest> lines) { }
        private record PurchaseLineRequest(UUID productId, int quantity, BigDecimal unitPrice) { }
        private record ReceivePurchaseRequest(Integer quantity) { }

        private <T> List<T> getList(String path, TypeReference<List<T>> type) throws IOException, InterruptedException {
        HttpRequest backendRequest = com.example.erp.frontend.base.BackendRequests.newBuilder(URI.create(backendUrl + path))
            .timeout(Duration.ofSeconds(5)).GET().build();
        HttpResponse<String> response = client.send(backendRequest, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != HttpServletResponse.SC_OK) throw new IOException("Backend returned HTTP " + response.statusCode());
        return mapper.readValue(response.body(), type);
        }
}