package com.example.erp.frontend.projects;

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

@WebServlet("/projects")
public class ProjectsServlet extends HttpServlet {
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final String backendUrl = System.getenv().getOrDefault("BACKEND_URL", "http://localhost:8080");

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        try {
            HttpRequest backendRequest = HttpRequest.newBuilder(URI.create(backendUrl + "/api/v1/projects/overview"))
                    .timeout(Duration.ofSeconds(5)).GET().build();
            HttpResponse<String> backendResponse = client.send(backendRequest, HttpResponse.BodyHandlers.ofString());
            if (backendResponse.statusCode() != HttpServletResponse.SC_OK) {
                throw new IOException("Backend returned HTTP " + backendResponse.statusCode());
            }
            request.setAttribute("overview", mapper.readValue(backendResponse.body(), ProjectsOverviewView.class));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            request.setAttribute("error", "Načítání projektů bylo přerušeno.");
        } catch (IOException exception) {
            request.setAttribute("error", "Backend pro projekty není dostupný: " + exception.getMessage());
        }
        request.getRequestDispatcher("/WEB-INF/views/projects/index.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String id = request.getParameter("id");
        try {
            UUID.fromString(id);
            HttpRequest backendRequest = HttpRequest.newBuilder(URI.create(backendUrl + "/api/v1/projects/" + id + "/complete"))
                    .method("PATCH", HttpRequest.BodyPublishers.noBody()).build();
            HttpResponse<Void> backendResponse = client.send(backendRequest, HttpResponse.BodyHandlers.discarding());
            String message = backendResponse.statusCode() == HttpServletResponse.SC_OK
                    ? "Projekt byl označen jako dokončený." : "Změnu stavu backend odmítl.";
            response.sendRedirect("projects?message=" + URLEncoder.encode(message, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException exception) {
            response.sendRedirect("projects?error=" + URLEncoder.encode("Neplatný projekt.", StandardCharsets.UTF_8));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            response.sendRedirect("projects?error=" + URLEncoder.encode("Dokončení projektu bylo přerušeno.", StandardCharsets.UTF_8));
        }
    }
}