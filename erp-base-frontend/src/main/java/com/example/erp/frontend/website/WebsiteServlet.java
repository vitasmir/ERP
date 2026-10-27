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
        String action = request.getParameter("action");
        try {
            HttpRequest backendRequest;
            if ("create".equals(action) || "edit".equals(action)) {
                String body = mapper.writeValueAsString(Map.of("title", request.getParameter("title"), "slug", request.getParameter("slug"),
                        "contentType", request.getParameter("contentType"), "ownerName", request.getParameter("ownerName"),
                        "content", request.getParameter("content")));
            String endpoint = backendUrl + "/api/v1/website/pages";
            if ("edit".equals(action)) endpoint += "/" + UUID.fromString(id);
            backendRequest = jsonRequest(endpoint, "edit".equals(action) ? "PUT" : "POST", body);
            } else if ("delete".equals(action)) {
            backendRequest = com.example.erp.frontend.base.BackendRequests.newBuilder(
                URI.create(backendUrl + "/api/v1/website/pages/" + UUID.fromString(id)))
                .DELETE().build();
            } else {
                UUID.fromString(id);
                backendRequest = com.example.erp.frontend.base.BackendRequests.newBuilder(URI.create(backendUrl + "/api/v1/website/pages/" + id + "/publish"))
                        .method("PATCH", HttpRequest.BodyPublishers.noBody()).build();
            }
            HttpResponse<String> backendResponse = client.send(backendRequest, HttpResponse.BodyHandlers.ofString());
            boolean success = "create".equals(action) ? backendResponse.statusCode() == HttpServletResponse.SC_CREATED
                : "edit".equals(action) ? backendResponse.statusCode() == HttpServletResponse.SC_OK
                : backendResponse.statusCode() == HttpServletResponse.SC_OK || backendResponse.statusCode() == HttpServletResponse.SC_NO_CONTENT;
            String parameter = success ? "message" : "error";
            String message = success ? successMessage(action) : failureMessage(action);
            response.sendRedirect("website?" + parameter + "=" + URLEncoder.encode(message, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException exception) {
            response.sendRedirect("website?error=" + URLEncoder.encode("Neplatná stránka.", StandardCharsets.UTF_8));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            response.sendRedirect("website?error=" + URLEncoder.encode("Publikace stránky byla přerušena.", StandardCharsets.UTF_8));
        }
    }

    private String successMessage(String action) {
        return switch (action) {
            case "create" -> "Stránka byla vytvořena.";
            case "edit" -> "Stránka byla upravena.";
            case "delete" -> "Stránka byla smazána.";
            default -> "Stránka byla publikována.";
        };
    }

    private String failureMessage(String action) {
        return switch (action) {
            case "create" -> "Vytvoření stránky backend odmítl.";
            case "edit" -> "Úpravu stránky backend odmítl.";
            case "delete" -> "Smazání stránky backend odmítl.";
            default -> "Publikaci backend odmítl.";
        };
    }

    private HttpRequest jsonRequest(String url, String method, String body) {
        return com.example.erp.frontend.base.BackendRequests.newBuilder(URI.create(url)).header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(body)).build();
    }
}