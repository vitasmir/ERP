package com.example.erp.frontend.sales;

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

@WebServlet("/sales")
public class SalesServlet extends HttpServlet {
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final String backendUrl = System.getenv().getOrDefault("BACKEND_URL", "http://localhost:8080");

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        try {
            HttpRequest backendRequest = com.example.erp.frontend.base.BackendRequests.newBuilder(URI.create(backendUrl + "/api/v1/sales/overview"))
                    .timeout(Duration.ofSeconds(5)).GET().build();
            HttpResponse<String> backendResponse = client.send(backendRequest, HttpResponse.BodyHandlers.ofString());
            if (backendResponse.statusCode() != HttpServletResponse.SC_OK) {
                throw new IOException("Backend returned HTTP " + backendResponse.statusCode());
            }
            request.setAttribute("overview", mapper.readValue(backendResponse.body(), SalesOverviewView.class));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            request.setAttribute("error", "Načítání prodeje bylo přerušeno.");
        } catch (IOException exception) {
            request.setAttribute("error", "Backend pro prodej není dostupný: " + exception.getMessage());
        }
        request.getRequestDispatcher("/WEB-INF/views/sales/index.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String id = request.getParameter("id");
        String action = request.getParameter("action");
        try {
            HttpRequest backendRequest;
            if ("create".equals(action)) {
                SalesOrderRequest create = new SalesOrderRequest(request.getParameter("orderNumber"),
                    request.getParameter("customerName"), request.getParameter("orderDate"),
                    request.getParameter("deliveryDate"), request.getParameter("totalAmount"));
                backendRequest = com.example.erp.frontend.base.BackendRequests.newBuilder(URI.create(backendUrl + "/api/v1/sales/orders"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(create))).build();
            } else {
                UUID.fromString(id);
                if ("update".equals(action)) {
            SalesOrderUpdateRequest update = new SalesOrderUpdateRequest(request.getParameter("orderNumber"),
                request.getParameter("customerName"), request.getParameter("orderDate"),
                request.getParameter("deliveryDate"), request.getParameter("totalAmount"));
            backendRequest = com.example.erp.frontend.base.BackendRequests.newBuilder(URI.create(backendUrl + "/api/v1/sales/orders/" + id))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(update))).build();
            } else if ("delete".equals(action)) {
            backendRequest = com.example.erp.frontend.base.BackendRequests.newBuilder(URI.create(backendUrl + "/api/v1/sales/orders/" + id))
                .DELETE().build();
            } else {
            backendRequest = com.example.erp.frontend.base.BackendRequests.newBuilder(URI.create(backendUrl + "/api/v1/sales/orders/" + id + "/confirm"))
                .method("PATCH", HttpRequest.BodyPublishers.noBody()).build();
            }
            }
            HttpResponse<Void> backendResponse = client.send(backendRequest, HttpResponse.BodyHandlers.discarding());
            boolean successful = backendResponse.statusCode() == HttpServletResponse.SC_OK
                || backendResponse.statusCode() == HttpServletResponse.SC_NO_CONTENT
                || backendResponse.statusCode() == HttpServletResponse.SC_CREATED;
            String message = successful ? switch (action == null ? "confirm" : action) {
            case "create" -> "Objednávka byla vytvořena.";
            case "update" -> "Dokument byl upraven.";
            case "delete" -> "Dokument byl smazán.";
            default -> "Nabídka byla potvrzena jako objednávka.";
            } : "Změnu dokumentu backend odmítl.";
            response.sendRedirect("sales?message=" + URLEncoder.encode(message, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException exception) {
            response.sendRedirect("sales?error=" + URLEncoder.encode("Vyplňte platné údaje objednávky.", StandardCharsets.UTF_8));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            response.sendRedirect("sales?error=" + URLEncoder.encode("Změna stavu byla přerušena.", StandardCharsets.UTF_8));
        }
    }

        private record SalesOrderRequest(String orderNumber, String customerName, String orderDate,
            String deliveryDate, String totalAmount) { }

        private record SalesOrderUpdateRequest(String orderNumber, String customerName, String orderDate,
            String deliveryDate, String totalAmount) { }
}