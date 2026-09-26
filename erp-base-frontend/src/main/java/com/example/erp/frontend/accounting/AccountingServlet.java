package com.example.erp.frontend.accounting;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet("/accounting")
public class AccountingServlet extends HttpServlet {
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final String backendUrl = System.getenv().getOrDefault("BACKEND_URL", "http://localhost:8080");

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        try {
            HttpRequest backendRequest = com.example.erp.frontend.base.BackendRequests.newBuilder(URI.create(backendUrl + "/api/v1/accounting/overview"))
                    .timeout(Duration.ofSeconds(5)).GET().build();
            HttpResponse<String> backendResponse = client.send(backendRequest, HttpResponse.BodyHandlers.ofString());
            if (backendResponse.statusCode() != HttpServletResponse.SC_OK) throw new IOException("Backend returned HTTP " + backendResponse.statusCode());
            request.setAttribute("overview", mapper.readValue(backendResponse.body(), AccountingOverviewView.class));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            request.setAttribute("error", "Načítání účetnictví bylo přerušeno.");
        } catch (IOException exception) {
            request.setAttribute("error", "Backend pro účetnictví není dostupný: " + exception.getMessage());
        }
        request.getRequestDispatcher("/WEB-INF/views/accounting/index.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String id = request.getParameter("id");
        try {
            HttpRequest backendRequest;
            if ("create".equals(request.getParameter("action"))) {
                String body = mapper.writeValueAsString(Map.of("invoiceNumber", request.getParameter("invoiceNumber"),
                        "partnerName", request.getParameter("partnerName"), "issueDate", request.getParameter("issueDate"),
                        "dueDate", request.getParameter("dueDate"), "totalAmount", request.getParameter("totalAmount")));
                backendRequest = jsonRequest(backendUrl + "/api/v1/accounting/invoices", "POST", body);
            } else if ("payment".equals(request.getParameter("action"))) {
                UUID.fromString(id);
                String body = mapper.writeValueAsString(Map.of("amount", request.getParameter("amount")));
                backendRequest = jsonRequest(backendUrl + "/api/v1/accounting/invoices/" + id + "/payment", "PATCH", body);
            } else {
                UUID.fromString(id);
                backendRequest = com.example.erp.frontend.base.BackendRequests.newBuilder(URI.create(backendUrl + "/api/v1/accounting/invoices/" + id + "/paid"))
                        .method("PATCH", HttpRequest.BodyPublishers.noBody()).build();
            }
            HttpResponse<Void> backendResponse = client.send(backendRequest, HttpResponse.BodyHandlers.discarding());
            String message = backendResponse.statusCode() >= 200 && backendResponse.statusCode() < 300 ? "Faktura byla uložena." : "Změnu backend odmítl.";
            response.sendRedirect("accounting?message=" + URLEncoder.encode(message, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException exception) {
            response.sendRedirect("accounting?error=" + URLEncoder.encode("Neplatná faktura.", StandardCharsets.UTF_8));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            response.sendRedirect("accounting?error=" + URLEncoder.encode("Změna stavu byla přerušena.", StandardCharsets.UTF_8));
        }
    }

    private HttpRequest jsonRequest(String url, String method, String body) {
        return com.example.erp.frontend.base.BackendRequests.newBuilder(URI.create(url)).header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(body)).build();
    }
}