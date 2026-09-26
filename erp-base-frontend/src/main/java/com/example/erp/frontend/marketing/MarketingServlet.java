package com.example.erp.frontend.marketing;

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

@WebServlet("/marketing")
public class MarketingServlet extends HttpServlet {
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final String backendUrl = System.getenv().getOrDefault("BACKEND_URL", "http://localhost:8080");

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        try {
            HttpRequest backendRequest = com.example.erp.frontend.base.BackendRequests.newBuilder(URI.create(backendUrl + "/api/v1/marketing/overview"))
                    .timeout(Duration.ofSeconds(5)).GET().build();
            HttpResponse<String> backendResponse = client.send(backendRequest, HttpResponse.BodyHandlers.ofString());
            if (backendResponse.statusCode() != HttpServletResponse.SC_OK) {
                throw new IOException("Backend returned HTTP " + backendResponse.statusCode());
            }
            request.setAttribute("overview", mapper.readValue(backendResponse.body(), MarketingOverviewView.class));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            request.setAttribute("error", "Načítání marketingu bylo přerušeno.");
        } catch (IOException exception) {
            request.setAttribute("error", "Backend pro marketing není dostupný: " + exception.getMessage());
        }
        request.getRequestDispatcher("/WEB-INF/views/marketing/index.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String id = request.getParameter("id");
        try {
            HttpRequest backendRequest;
            if ("create".equals(request.getParameter("action"))) {
                String body = mapper.writeValueAsString(Map.of("name", request.getParameter("name"), "audience", request.getParameter("audience"),
                        "channel", request.getParameter("channel"), "ownerName", request.getParameter("ownerName"),
                        "budget", request.getParameter("budget"), "plannedStartDate", request.getParameter("plannedStartDate")));
                backendRequest = jsonRequest(backendUrl + "/api/v1/marketing/campaigns", "POST", body);
            } else {
                UUID.fromString(id);
                String operation = "complete".equals(request.getParameter("action")) ? "complete" : "launch";
                backendRequest = com.example.erp.frontend.base.BackendRequests.newBuilder(URI.create(backendUrl + "/api/v1/marketing/campaigns/" + id + "/" + operation))
                        .method("PATCH", HttpRequest.BodyPublishers.noBody()).build();
            }
            HttpResponse<Void> backendResponse = client.send(backendRequest, HttpResponse.BodyHandlers.discarding());
            String message = backendResponse.statusCode() == HttpServletResponse.SC_OK
                    ? "Kampaň byla spuštěna." : "Spuštění kampaně backend odmítl.";
            response.sendRedirect("marketing?message=" + URLEncoder.encode(message, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException exception) {
            response.sendRedirect("marketing?error=" + URLEncoder.encode("Neplatná kampaň.", StandardCharsets.UTF_8));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            response.sendRedirect("marketing?error=" + URLEncoder.encode("Spuštění kampaně bylo přerušeno.", StandardCharsets.UTF_8));
        }
    }

    private HttpRequest jsonRequest(String url, String method, String body) {
        return com.example.erp.frontend.base.BackendRequests.newBuilder(URI.create(url)).header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(body)).build();
    }
}