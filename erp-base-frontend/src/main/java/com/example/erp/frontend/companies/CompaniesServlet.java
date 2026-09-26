package com.example.erp.frontend.companies;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet("/companies")
public class CompaniesServlet extends HttpServlet {
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final String backendUrl = System.getenv().getOrDefault("BACKEND_URL", "http://localhost:8080");

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        try {
            HttpRequest backendRequest = HttpRequest.newBuilder(URI.create(backendUrl + "/api/v1/companies"))
                    .timeout(Duration.ofSeconds(5)).GET().build();
            HttpResponse<String> backendResponse = client.send(backendRequest, HttpResponse.BodyHandlers.ofString());
            if (backendResponse.statusCode() != HttpServletResponse.SC_OK) {
                throw new IOException("Backend returned HTTP " + backendResponse.statusCode());
            }
            request.setAttribute("companies", mapper.readValue(backendResponse.body(), CompanyView[].class));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            request.setAttribute("error", "Načítání společností bylo přerušeno.");
        } catch (IOException exception) {
            request.setAttribute("error", "Backend pro společnosti není dostupný: " + exception.getMessage());
        }
        request.setAttribute("message", request.getParameter("message"));
        request.setAttribute("error", request.getParameter("error") != null
                ? request.getParameter("error") : request.getAttribute("error"));
        request.setAttribute("activePage", "companies");
        request.setAttribute("pageTitle", "Společnosti");
        request.setAttribute("breadcrumb", "BASE / ENTITIES");
        request.getRequestDispatcher("/WEB-INF/views/base/companies/index.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        request.setCharacterEncoding(StandardCharsets.UTF_8.name());
        try {
            String body = mapper.writeValueAsString(Map.of(
                    "name", value(request, "name"),
                    "type", value(request, "type"),
                    "currency", value(request, "currency"),
                    "status", value(request, "status"),
                    "color", value(request, "color")));
            HttpRequest backendRequest = HttpRequest.newBuilder(URI.create(backendUrl + "/api/v1/companies"))
                    .timeout(Duration.ofSeconds(5)).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> backendResponse = client.send(backendRequest, HttpResponse.BodyHandlers.ofString());
            if (backendResponse.statusCode() >= 200 && backendResponse.statusCode() < 300) {
                response.sendRedirect("companies?message=" + encode("Společnost byla vytvořena."));
            } else if (backendResponse.statusCode() == HttpServletResponse.SC_CONFLICT) {
                response.sendRedirect("companies?error=" + encode("Společnost s tímto názvem již existuje."));
            } else {
                response.sendRedirect("companies?error=" + encode("Backend změnu společnosti odmítl (HTTP "
                        + backendResponse.statusCode() + ")."));
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            response.sendRedirect("companies?error=" + encode("Vytvoření společnosti bylo přerušeno."));
        }
    }

    private String value(HttpServletRequest request, String name) {
        String value = request.getParameter(name);
        return value == null ? "" : value.trim();
    }

    private String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }

    public record CompanyView(UUID id, String name, String type, String currency, String status, String color) {
        public String initial() { return name.substring(0, 1).toUpperCase(Locale.ROOT); }
        public String statusLabel() { return "ACTIVE".equals(status) ? "aktivní" : "neaktivní"; }
        public String statusClass() { return status.toLowerCase(Locale.ROOT); }
    }
}