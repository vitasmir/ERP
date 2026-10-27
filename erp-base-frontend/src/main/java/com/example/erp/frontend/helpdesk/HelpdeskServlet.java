package com.example.erp.frontend.helpdesk;

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

@WebServlet("/helpdesk")
public class HelpdeskServlet extends HttpServlet {
    private static final String PAGE_VERSION = "20261001-2";
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final String backendUrl = System.getenv().getOrDefault("BACKEND_URL", "http://localhost:8080");

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if (!PAGE_VERSION.equals(request.getParameter("v"))) {
            response.sendRedirect("helpdesk?v=" + PAGE_VERSION);
            return;
        }
        try {
            HttpRequest backendRequest = com.example.erp.frontend.base.BackendRequests.newBuilder(URI.create(backendUrl + "/api/v1/helpdesk/overview"))
                    .timeout(Duration.ofSeconds(5)).GET().build();
            HttpResponse<String> backendResponse = client.send(backendRequest, HttpResponse.BodyHandlers.ofString());
            if (backendResponse.statusCode() != HttpServletResponse.SC_OK) {
                throw new IOException("Backend returned HTTP " + backendResponse.statusCode());
            }
            request.setAttribute("overview", mapper.readValue(backendResponse.body(), HelpdeskOverviewView.class));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            request.setAttribute("error", "Načítání helpdesku bylo přerušeno.");
        } catch (IOException exception) {
            request.setAttribute("error", "Backend pro helpdesk není dostupný: " + exception.getMessage());
        }
        request.getRequestDispatcher("/WEB-INF/views/helpdesk/index.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String id = request.getParameter("id");
        try {
            UUID.fromString(id);
            HttpRequest backendRequest = com.example.erp.frontend.base.BackendRequests.newBuilder(URI.create(backendUrl + "/api/v1/helpdesk/tickets/" + id + "/resolve"))
                    .method("PATCH", HttpRequest.BodyPublishers.noBody()).build();
            HttpResponse<Void> backendResponse = client.send(backendRequest, HttpResponse.BodyHandlers.discarding());
            String message = backendResponse.statusCode() == HttpServletResponse.SC_OK
                    ? "Požadavek byl označen jako vyřešený." : "Změnu stavu backend odmítl.";
            response.sendRedirect("helpdesk?v=" + PAGE_VERSION + "&message=" + URLEncoder.encode(message, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException exception) {
            response.sendRedirect("helpdesk?v=" + PAGE_VERSION + "&error=" + URLEncoder.encode("Neplatný požadavek.", StandardCharsets.UTF_8));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            response.sendRedirect("helpdesk?v=" + PAGE_VERSION + "&error=" + URLEncoder.encode("Vyřešení požadavku bylo přerušeno.", StandardCharsets.UTF_8));
        }
    }
}