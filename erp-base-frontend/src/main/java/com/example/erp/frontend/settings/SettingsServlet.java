package com.example.erp.frontend.settings;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet("/settings")
public class SettingsServlet extends HttpServlet {
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final String backendUrl = System.getenv().getOrDefault("BACKEND_URL", "http://localhost:8080");

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        try {
            HttpRequest backendRequest = HttpRequest.newBuilder(URI.create(backendUrl + "/api/v1/settings"))
                    .timeout(Duration.ofSeconds(5)).GET().build();
            HttpResponse<String> backendResponse = client.send(backendRequest, HttpResponse.BodyHandlers.ofString());
            if (backendResponse.statusCode() != HttpServletResponse.SC_OK) {
                throw new IOException("Backend returned HTTP " + backendResponse.statusCode());
            }
            request.setAttribute("settings", mapper.readValue(backendResponse.body(), SettingsView.class));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            request.setAttribute("error", "Načítání nastavení bylo přerušeno.");
        } catch (IOException exception) {
            request.setAttribute("error", "Backend pro nastavení není dostupný: " + exception.getMessage());
        }
        request.getRequestDispatcher("/WEB-INF/views/settings.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        UpdateSettingsRequest body = new UpdateSettingsRequest(request.getParameter("companyName"),
                request.getParameter("companyEmail"), request.getParameter("currencyCode"), request.getParameter("timezone"),
                Integer.parseInt(request.getParameter("fiscalYearStartMonth")),
                Integer.parseInt(request.getParameter("defaultPaymentTermsDays")),
                new BigDecimal(request.getParameter("deliveryFee")));
        try {
            HttpRequest backendRequest = HttpRequest.newBuilder(URI.create(backendUrl + "/api/v1/settings"))
                    .header("Content-Type", "application/json")
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build();
            HttpResponse<Void> backendResponse = client.send(backendRequest, HttpResponse.BodyHandlers.discarding());
            String message = backendResponse.statusCode() == HttpServletResponse.SC_OK
                    ? "Nastavení bylo uloženo." : "Uložení nastavení backend odmítl.";
            response.sendRedirect("settings?message=" + URLEncoder.encode(message, StandardCharsets.UTF_8));
        } catch (NumberFormatException exception) {
            response.sendRedirect("settings?error=" + URLEncoder.encode("Neplatné číselné hodnoty.", StandardCharsets.UTF_8));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            response.sendRedirect("settings?error=" + URLEncoder.encode("Uložení nastavení bylo přerušeno.", StandardCharsets.UTF_8));
        }
    }

    private record UpdateSettingsRequest(String companyName, String companyEmail, String currencyCode, String timezone,
            int fiscalYearStartMonth, int defaultPaymentTermsDays, BigDecimal deliveryFee) { }
}