package com.example.erp.frontend.inventory;

import java.io.IOException;
import java.math.BigDecimal;
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
            HttpRequest backendRequest = com.example.erp.frontend.base.BackendRequests.newBuilder(URI.create(backendUrl + "/api/v1/inventory/overview"))
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
        loadMovements(request);
        request.getRequestDispatcher("/WEB-INF/views/inventory/index.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if ("order".equals(request.getParameter("action"))) {
            saveOrder(request, response);
            return;
        }
        try {
            UUID id = UUID.fromString(request.getParameter("id"));
            int quantity = Integer.parseInt(request.getParameter("quantity"));
            String action = request.getParameter("action");
            if (action != null && !"receive".equals(action) && !"dispatch".equals(action)) {
                throw new IllegalArgumentException("Unknown stock action.");
            }
            String reference = request.getParameter("reference");
            String note = request.getParameter("note");
            if (quantity <= 0 || reference == null || reference.isBlank() || reference.length() > 120
                    || (note != null && note.length() > 500)) {
                throw new IllegalArgumentException();
            }
            boolean dispatch = "dispatch".equals(action);
            String requestBody;
            if (dispatch) {
                requestBody = mapper.writeValueAsString(new DispatchStockRequest(quantity, reference.trim(), note));
            } else {
                int minimumQuantity = Integer.parseInt(request.getParameter("reorderLevel"));
                String cost = request.getParameter("unitCost");
                if (cost == null || cost.isBlank()) {
                    throw new IllegalArgumentException();
                }
                BigDecimal receivedUnitCost = new BigDecimal(cost.replace(',', '.'));
                if (minimumQuantity < 0 || receivedUnitCost.signum() < 0 || receivedUnitCost.scale() > 2
                        || receivedUnitCost.compareTo(new BigDecimal("9999999999.99")) > 0) {
                    throw new IllegalArgumentException();
                }
                requestBody = mapper.writeValueAsString(
                        new ReceiveStockRequest(quantity, minimumQuantity, receivedUnitCost, reference.trim(), note));
            }
            HttpRequest backendRequest = com.example.erp.frontend.base.BackendRequests.newBuilder(
                    URI.create(backendUrl + "/api/v1/inventory/items/" + id + (dispatch ? "/dispatch" : "/receive")))
                    .header("Content-Type", "application/json")
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(requestBody)).build();
            HttpResponse<String> backendResponse = client.send(backendRequest, HttpResponse.BodyHandlers.ofString());
            if (backendResponse.statusCode() == HttpServletResponse.SC_OK) {
                redirect(response, "message", dispatch ? "Výdej zásoby byl zaevidován." : "Příjem zásoby byl zaevidován.");
            } else {
                redirect(response, "error", backendError(backendResponse, "Skladový pohyb backend odmítl."));
            }
        } catch (IllegalArgumentException exception) {
            redirect(response, "error", "Zadejte kladné celočíselné množství, doklad, platné minimum a cenu.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            redirect(response, "error", "Odesílání bylo přerušeno. Před opakováním ověřte historii pohybů.");
        } catch (IOException exception) {
            getServletContext().log("Inventory movement request failed.", exception);
            redirect(response, "error", "Spojení se skladem selhalo. Před opakováním ověřte historii pohybů.");
        }
    }

    private void saveOrder(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String productId = request.getParameter("productId");
        String locationName = request.getParameter("locationName");
        String quantity = request.getParameter("quantity");
        try {
            UUID.fromString(productId);
            int orderedQuantity = Integer.parseInt(quantity);
            if (orderedQuantity < 0 || locationName == null || locationName.isBlank()) {
                throw new IllegalArgumentException();
            }
            String requestBody = mapper.writeValueAsString(
                    new OrderStockRequest(UUID.fromString(productId), locationName, orderedQuantity));
            HttpRequest backendRequest = com.example.erp.frontend.base.BackendRequests.newBuilder(URI.create(backendUrl + "/api/v1/inventory/orders"))
                    .header("Content-Type", "application/json")
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(requestBody)).build();
            HttpResponse<String> backendResponse = client.send(backendRequest, HttpResponse.BodyHandlers.ofString());
            boolean success = backendResponse.statusCode() == HttpServletResponse.SC_OK;
            String message = success ? "Objednávka z hlavního skladu byla uložena."
                    : backendError(backendResponse, "Objednávku backend odmítl.");
            String location = URLEncoder.encode(locationName, StandardCharsets.UTF_8);
            response.sendRedirect("inventory?" + (success ? "message=" : "error=") + URLEncoder.encode(message, StandardCharsets.UTF_8)
                    + "&warehouseName=" + location + "&view=products");
        } catch (IllegalArgumentException exception) {
            response.sendRedirect("inventory?error="
                    + URLEncoder.encode("Zadejte nezáporné množství a platný sklad.", StandardCharsets.UTF_8));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            response.sendRedirect("inventory?error="
                    + URLEncoder.encode("Objednávka byla přerušena.", StandardCharsets.UTF_8));
        } catch (IOException exception) {
            getServletContext().log("Inventory order request failed.", exception);
            redirect(response, "error", "Objednávku se nepodařilo odeslat. Ověřte stav před opakováním.");
        }
    }

    private void loadMovements(HttpServletRequest request) {
        try {
            String itemId = request.getParameter("itemId");
            String pageParameter = request.getParameter("page");
            int page = pageParameter == null ? 0 : Integer.parseInt(pageParameter);
            if (page < 0) {
                throw new IllegalArgumentException();
            }
            String filter = itemId == null || itemId.isBlank() ? "" : "&itemId=" + UUID.fromString(itemId);
            if (!filter.isEmpty()) {
                request.setAttribute("selectedItemId", UUID.fromString(itemId));
            }
            HttpResponse<String> result = client.send(com.example.erp.frontend.base.BackendRequests.newBuilder(
                    URI.create(backendUrl + "/api/v1/inventory/movements?page=" + page + filter)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (result.statusCode() != HttpServletResponse.SC_OK) {
                throw new IOException(backendError(result, "Historii pohybů backend odmítl."));
            }
            request.setAttribute("movements", mapper.readValue(result.body(), InventoryMovementsView.class));
        } catch (IllegalArgumentException exception) {
            request.setAttribute("historyError", "Neplatný filtr nebo stránka historie.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            request.setAttribute("historyError", "Načítání historie bylo přerušeno.");
        } catch (IOException exception) {
            request.setAttribute("historyError", "Historii pohybů se nepodařilo načíst: " + exception.getMessage());
        }
    }

    private String backendError(HttpResponse<String> response, String fallback) throws IOException {
        if (response.statusCode() == HttpServletResponse.SC_UNAUTHORIZED) {
            return "Přihlášení vypršelo. Přihlaste se znovu.";
        }
        if (response.statusCode() == HttpServletResponse.SC_FORBIDDEN) {
            return "Nemáte oprávnění k této skladové operaci.";
        }
        String contentType = response.headers().firstValue("Content-Type").orElse("");
        if (contentType.contains("json") && response.body() != null && !response.body().isBlank()) {
            var body = mapper.readTree(response.body());
            String detail = body.path("detail").asText("");
            String message = body.path("message").asText("");
            if (!detail.isBlank()) return detail;
            if (!message.isBlank()) return message;
        }
        return fallback + " (HTTP " + response.statusCode() + ")";
    }

    private void redirect(HttpServletResponse response, String parameter, String message) throws IOException {
        response.sendRedirect("inventory?" + parameter + "=" + URLEncoder.encode(message, StandardCharsets.UTF_8));
    }

    private record ReceiveStockRequest(int quantity, int reorderLevel, BigDecimal unitCost,
            String reference, String note) { }

    private record DispatchStockRequest(int quantity, String reference, String note) { }

    private record OrderStockRequest(UUID productId, String locationName, int quantity) { }
}