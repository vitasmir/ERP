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
import java.util.HashMap;
import java.util.Map;
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
                request.setAttribute("suppliers", getList("/api/v1/purchase/suppliers",
                    new TypeReference<List<PurchaseOverviewView.SupplierView>>() { }));
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
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        String id = request.getParameter("id");
        try {
            if ("receive".equals(request.getParameter("action"))) {
                UUID orderId = UUID.fromString(id);
                UUID receiptId = UUID.fromString(request.getParameter("receiptId"));
                String[] lineIds = request.getParameterValues("lineId");
                String[] quantities = request.getParameterValues("receiveQuantity");
                List<ReceivePurchaseLine> lines = new ArrayList<>();
                if (lineIds != null) {
                    if (quantities == null || quantities.length != lineIds.length) {
                        throw new IllegalArgumentException("Missing receipt quantities.");
                    }
                    for (int index = 0; index < lineIds.length; index++) {
                        int quantity = Integer.parseInt(quantities[index]);
                        if (quantity < 0) throw new IllegalArgumentException("Negative receipt quantity.");
                        if (quantity > 0) lines.add(new ReceivePurchaseLine(UUID.fromString(lineIds[index]), quantity));
                    }
                    if (lines.isEmpty()) throw new IllegalArgumentException("Empty receipt.");
                }
                Integer legacyQuantity = lineIds == null ? Integer.valueOf(request.getParameter("quantity")) : null;
                String body = mapper.writeValueAsString(new ReceivePurchaseRequest(legacyQuantity, receiptId,
                        lineIds == null ? null : lines));
                HttpRequest backendRequest = com.example.erp.frontend.base.BackendRequests
                        .newBuilder(URI.create(backendUrl + "/api/v1/purchase/orders/" + orderId + "/receive"))
                        .header("Content-Type", "application/json")
                        .method("PATCH", HttpRequest.BodyPublishers.ofString(body)).build();
                HttpResponse<String> result = client.send(backendRequest, HttpResponse.BodyHandlers.ofString());
                if (result.statusCode() != HttpServletResponse.SC_OK) {
                    var error = mapper.readTree(result.body());
                    String detail = error.hasNonNull("detail") ? error.get("detail").asText()
                            : error.path("message").asText();
                    receiptFailure(request, response, detail.isBlank()
                            ? "Příjem byl odmítnut (HTTP " + result.statusCode() + ")." : detail);
                    return;
                }
                response.sendRedirect("purchase?message=" + URLEncoder.encode(
                        "Příjemka byla uložena a zásoba přepočtena.", StandardCharsets.UTF_8));
                return;
            }
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
            String body = mapper.writeValueAsString(new CreatePurchaseOrderRequest(UUID.fromString(request.getParameter("supplierId")),
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
                boolean succeeded = update ? backendResponse.statusCode() == HttpServletResponse.SC_OK
                    : backendResponse.statusCode() == HttpServletResponse.SC_CREATED;
                String message = succeeded
                    ? (update ? "Nákupní objednávka byla upravena." : "Nákupní objednávka byla vytvořena.")
                    : "Objednávku se nepodařilo uložit.";
                response.sendRedirect("purchase?" + (succeeded ? "message=" : "error=")
                    + URLEncoder.encode(message, StandardCharsets.UTF_8));
            return;
            }
            UUID.fromString(id);
                String action = "order";
                HttpRequest.Builder requestBuilder = com.example.erp.frontend.base.BackendRequests.newBuilder(URI.create(backendUrl + "/api/v1/purchase/orders/" + id + "/" + action));
                HttpRequest backendRequest = requestBuilder.method("PATCH", HttpRequest.BodyPublishers.noBody()).build();
            HttpResponse<Void> backendResponse = client.send(backendRequest, HttpResponse.BodyHandlers.discarding());
                boolean succeeded = backendResponse.statusCode() == HttpServletResponse.SC_OK;
                String message = succeeded
                    ? ("receive".equals(action) ? "Zboží bylo přijato na sklad." : "Nákupní objednávka byla vystavena.")
                    : "Změnu stavu backend odmítl.";
                response.sendRedirect("purchase?" + (succeeded ? "message=" : "error=")
                    + URLEncoder.encode(message, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException exception) {
            if ("receive".equals(request.getParameter("action"))) {
                receiptFailure(request, response, "Zadejte kladné množství alespoň jednoho řádku příjmu.");
                return;
            }
            response.sendRedirect("purchase?error=" + URLEncoder.encode("Neplatný nákupní požadavek.", StandardCharsets.UTF_8));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            response.sendRedirect("purchase?error=" + URLEncoder.encode("Změna stavu byla přerušena.", StandardCharsets.UTF_8));
        } catch (IOException exception) {
            getServletContext().log("Purchase request failed", exception);
            if ("receive".equals(request.getParameter("action"))) {
                receiptFailure(request, response,
                        "Výsledek příjmu nelze ověřit. Zkontrolujte příjemky nebo odešlete znovu stejný formulář.");
                return;
            }
            response.sendRedirect("purchase?error=" + URLEncoder.encode(
                    "Výsledek operace nelze ověřit. Zkontrolujte stav objednávky.", StandardCharsets.UTF_8));
        }
    }

    private void receiptFailure(HttpServletRequest request, HttpServletResponse response, String error)
            throws ServletException, IOException {
        request.setAttribute("receiptError", error);
        Map<String, String> quantities = new HashMap<>();
        String[] ids = request.getParameterValues("lineId");
        String[] values = request.getParameterValues("receiveQuantity");
        if (ids != null && values != null && ids.length == values.length) {
            for (int index = 0; index < ids.length; index++) quantities.put(ids[index], values[index]);
        }
        request.setAttribute("receiptQuantities", quantities);
        doGet(request, response);
    }

    private record CreatePurchaseOrderRequest(UUID supplierId, LocalDate requestedOn,
            LocalDate expectedDeliveryDate, UUID sourceWarehouseId,
            UUID destinationWarehouseId, List<PurchaseLineRequest> lines) { }
        private record PurchaseLineRequest(UUID productId, int quantity, BigDecimal unitPrice) { }
        private record ReceivePurchaseRequest(Integer quantity, UUID receiptId, List<ReceivePurchaseLine> lines) { }
        private record ReceivePurchaseLine(UUID lineId, int quantity) { }

        private <T> List<T> getList(String path, TypeReference<List<T>> type) throws IOException, InterruptedException {
        HttpRequest backendRequest = com.example.erp.frontend.base.BackendRequests.newBuilder(URI.create(backendUrl + path))
            .timeout(Duration.ofSeconds(5)).GET().build();
        HttpResponse<String> response = client.send(backendRequest, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != HttpServletResponse.SC_OK) throw new IOException("Backend returned HTTP " + response.statusCode());
        return mapper.readValue(response.body(), type);
        }
}