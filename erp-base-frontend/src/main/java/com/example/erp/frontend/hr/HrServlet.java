package com.example.erp.frontend.hr;

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

@WebServlet("/hr")
public class HrServlet extends HttpServlet {
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final String backendUrl = System.getenv().getOrDefault("BACKEND_URL", "http://localhost:8080");

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        try {
            HttpRequest backendRequest = HttpRequest.newBuilder(URI.create(backendUrl + "/api/v1/hr/overview"))
                    .timeout(Duration.ofSeconds(5)).GET().build();
            HttpResponse<String> backendResponse = client.send(backendRequest, HttpResponse.BodyHandlers.ofString());
            if (backendResponse.statusCode() != HttpServletResponse.SC_OK) {
                throw new IOException("Backend returned HTTP " + backendResponse.statusCode());
            }
            request.setAttribute("overview", mapper.readValue(backendResponse.body(), HrOverviewView.class));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            request.setAttribute("error", "Načítání lidí bylo přerušeno.");
        } catch (IOException exception) {
            request.setAttribute("error", "Backend pro lidi není dostupný: " + exception.getMessage());
        }
        request.getRequestDispatcher("/WEB-INF/views/hr/index.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String id = request.getParameter("id");
        try {
            UUID.fromString(id);
            HttpRequest backendRequest = HttpRequest.newBuilder(URI.create(backendUrl + "/api/v1/hr/employees/" + id + "/activate"))
                    .method("PATCH", HttpRequest.BodyPublishers.noBody()).build();
            HttpResponse<Void> backendResponse = client.send(backendRequest, HttpResponse.BodyHandlers.discarding());
            String message = backendResponse.statusCode() == HttpServletResponse.SC_OK
                    ? "Nástup zaměstnance byl aktivován." : "Změnu stavu backend odmítl.";
            response.sendRedirect("hr?message=" + URLEncoder.encode(message, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException exception) {
            response.sendRedirect("hr?error=" + URLEncoder.encode("Neplatný zaměstnanec.", StandardCharsets.UTF_8));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            response.sendRedirect("hr?error=" + URLEncoder.encode("Aktivace nástupu byla přerušena.", StandardCharsets.UTF_8));
        }
    }
}