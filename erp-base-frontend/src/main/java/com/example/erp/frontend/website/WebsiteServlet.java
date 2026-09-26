package com.example.erp.frontend.website;

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

@WebServlet("/website")
public class WebsiteServlet extends HttpServlet {
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final String backendUrl = System.getenv().getOrDefault("BACKEND_URL", "http://localhost:8080");

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        try {
            HttpRequest backendRequest = com.example.erp.frontend.base.BackendRequests.newBuilder(URI.create(backendUrl + "/api/v1/website/overview"))
                    .timeout(Duration.ofSeconds(5)).GET().build();
            HttpResponse<String> backendResponse = client.send(backendRequest, HttpResponse.BodyHandlers.ofString());
            if (backendResponse.statusCode() != HttpServletResponse.SC_OK) {
                throw new IOException("Backend returned HTTP " + backendResponse.statusCode());
            }
            request.setAttribute("overview", mapper.readValue(backendResponse.body(), WebsiteOverviewView.class));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            request.setAttribute("error", "Načítání webu bylo přerušeno.");
        } catch (IOException exception) {
            request.setAttribute("error", "Backend pro web není dostupný: " + exception.getMessage());
        }
        request.getRequestDispatcher("/WEB-INF/views/website/index.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String id = request.getParameter("id");
        try {
            HttpRequest backendRequest;
            if ("create".equals(request.getParameter("action"))) {
                String body = mapper.writeValueAsString(Map.of("title", request.getParameter("title"), "slug", request.getParameter("slug"),
                        "contentType", request.getParameter("contentType"), "ownerName", request.getParameter("ownerName"),
                        "content", request.getParameter("content")));
                backendRequest = jsonRequest(backendUrl + "/api/v1/website/pages", "POST", body);
            } else {
                UUID.fromString(id);
                backendRequest = com.example.erp.frontend.base.BackendRequests.newBuilder(URI.create(backendUrl + "/api/v1/website/pages/" + id + "/publish"))
                        .method("PATCH", HttpRequest.BodyPublishers.noBody()).build();
            }
            HttpResponse<Void> backendResponse = client.send(backendRequest, HttpResponse.BodyHandlers.discarding());
            String message = backendResponse.statusCode() == HttpServletResponse.SC_OK
                    ? "Stránka byla publikována." : "Publikaci backend odmítl.";
            response.sendRedirect("website?message=" + URLEncoder.encode(message, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException exception) {
            response.sendRedirect("website?error=" + URLEncoder.encode("Neplatná stránka.", StandardCharsets.UTF_8));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            response.sendRedirect("website?error=" + URLEncoder.encode("Publikace stránky byla přerušena.", StandardCharsets.UTF_8));
        }
    }

    private HttpRequest jsonRequest(String url, String method, String body) {
        return com.example.erp.frontend.base.BackendRequests.newBuilder(URI.create(url)).header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(body)).build();
    }
}