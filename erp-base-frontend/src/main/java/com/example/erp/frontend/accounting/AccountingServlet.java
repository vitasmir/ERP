package com.example.erp.frontend.accounting;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.example.erp.frontend.companies.CompaniesServlet.CompanyView;
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
        String pdfId = request.getParameter("pdf");
        if (pdfId != null && !pdfId.isBlank()) {
            downloadPdf(pdfId, response);
            return;
        }
        try {
            HttpRequest backendRequest = com.example.erp.frontend.base.BackendRequests.newBuilder(URI.create(backendUrl + "/api/v1/accounting/overview"))
                    .timeout(Duration.ofSeconds(5)).GET().build();
            HttpResponse<String> backendResponse = client.send(backendRequest, HttpResponse.BodyHandlers.ofString());
            if (backendResponse.statusCode() != HttpServletResponse.SC_OK) throw new IOException("Backend returned HTTP " + backendResponse.statusCode());
            request.setAttribute("overview", mapper.readValue(backendResponse.body(), AccountingOverviewView.class));
            HttpRequest companiesRequest = com.example.erp.frontend.base.BackendRequests
                    .newBuilder(URI.create(backendUrl + "/api/v1/companies"))
                    .timeout(Duration.ofSeconds(5)).GET().build();
            HttpResponse<String> companiesResponse = client.send(companiesRequest, HttpResponse.BodyHandlers.ofString());
            if (companiesResponse.statusCode() == HttpServletResponse.SC_OK) {
                request.setAttribute("companies", mapper.readValue(companiesResponse.body(), CompanyView[].class));
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            request.setAttribute("error", "Načítání účetnictví bylo přerušeno.");
        } catch (IOException exception) {
            request.setAttribute("error", "Backend pro účetnictví není dostupný: " + exception.getMessage());
        }
        request.getRequestDispatcher("/WEB-INF/views/accounting/index.jsp").forward(request, response);
    }

    private void downloadPdf(String invoiceId, HttpServletResponse response) throws IOException {
        try {
            UUID id = UUID.fromString(invoiceId);
            HttpRequest backendRequest = com.example.erp.frontend.base.BackendRequests
                    .newBuilder(URI.create(backendUrl + "/api/v1/accounting/invoices/" + id + "/pdf"))
                    .timeout(Duration.ofSeconds(10)).GET().build();
            HttpResponse<byte[]> backendResponse = client.send(backendRequest, HttpResponse.BodyHandlers.ofByteArray());
            if (backendResponse.statusCode() != HttpServletResponse.SC_OK) {
                response.sendError(backendResponse.statusCode(), "PDF faktury není dostupné.");
                return;
            }
            response.setContentType("application/pdf");
            backendResponse.headers().firstValue("Content-Disposition").ifPresent(value -> response.setHeader("Content-Disposition", value));
            response.setContentLength(backendResponse.body().length);
            response.getOutputStream().write(backendResponse.body());
        } catch (IllegalArgumentException exception) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Neplatné číslo faktury.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Stahování PDF bylo přerušeno.");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String id = request.getParameter("id");
        try {
            HttpRequest backendRequest;
            if ("create".equals(request.getParameter("action"))) {
                String invoiceNumber = request.getParameter("invoiceNumber");
                BigDecimal totalAmount = new BigDecimal(request.getParameter("totalAmount"));
                String body = mapper.writeValueAsString(Map.of("invoiceNumber", invoiceNumber,
                        "partnerName", request.getParameter("partnerName"), "issueDate", request.getParameter("issueDate"),
                    "dueDate", request.getParameter("dueDate"),
                    "lines", List.of(Map.of("description", "Faktura " + invoiceNumber,
                        "quantity", BigDecimal.ONE, "unitPrice", totalAmount, "vatRate", BigDecimal.ZERO))));
                backendRequest = jsonRequest(backendUrl + "/api/v1/accounting/invoices", "POST", body);
                } else if ("update".equals(request.getParameter("action"))) {
                UUID.fromString(id);
                String invoiceNumber = request.getParameter("invoiceNumber");
                BigDecimal totalAmount = new BigDecimal(request.getParameter("totalAmount"));
                String body = mapper.writeValueAsString(Map.of("version", Long.parseLong(request.getParameter("version")),
                    "invoice", Map.of("invoiceNumber", invoiceNumber,
                        "partnerName", request.getParameter("partnerName"),
                        "issueDate", request.getParameter("issueDate"),
                        "dueDate", request.getParameter("dueDate"),
                        "lines", List.of(Map.of("description", "Faktura " + invoiceNumber,
                            "quantity", BigDecimal.ONE, "unitPrice", totalAmount, "vatRate", BigDecimal.ZERO)))));
                backendRequest = jsonRequest(backendUrl + "/api/v1/accounting/invoices/" + id, "PUT", body);
            } else if ("payment".equals(request.getParameter("action"))) {
                UUID.fromString(id);
                String body = mapper.writeValueAsString(Map.of("amount", request.getParameter("amount")));
                backendRequest = jsonRequest(backendUrl + "/api/v1/accounting/invoices/" + id + "/payment", "PATCH", body);
            } else {
                UUID.fromString(id);
                backendRequest = com.example.erp.frontend.base.BackendRequests.newBuilder(URI.create(backendUrl + "/api/v1/accounting/invoices/" + id + "/paid"))
                        .method("PATCH", HttpRequest.BodyPublishers.noBody()).build();
            }
            HttpResponse<String> backendResponse = client.send(backendRequest, HttpResponse.BodyHandlers.ofString());
            boolean successful = backendResponse.statusCode() >= 200 && backendResponse.statusCode() < 300;
            String message = successful ? "Faktura byla uložena." : "Backend odmítl změnu: " + backendResponse.body();
            String parameter = successful ? "message" : "error";
            response.sendRedirect("accounting?" + parameter + "=" + URLEncoder.encode(message, StandardCharsets.UTF_8));
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