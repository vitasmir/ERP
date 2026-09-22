package com.example.erp.frontend.crm;

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

@WebServlet("/crm")
public class CrmServlet extends HttpServlet {
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final String backendUrl = System.getenv().getOrDefault("BACKEND_URL", "http://localhost:8080");

    @Override protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        try {
            HttpResponse<String> backendResponse = client.send(HttpRequest.newBuilder(URI.create(backendUrl + "/api/v1/crm/overview")).timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofString());
            if (backendResponse.statusCode() != HttpServletResponse.SC_OK) throw new IOException("Backend returned HTTP " + backendResponse.statusCode());
            request.setAttribute("overview", mapper.readValue(backendResponse.body(), CrmOverviewView.class));
        } catch (InterruptedException exception) { Thread.currentThread().interrupt(); request.setAttribute("error", "Načítání CRM bylo přerušeno."); }
        catch (IOException exception) { request.setAttribute("error", "Backend pro CRM není dostupný: " + exception.getMessage()); }
        request.getRequestDispatcher("/WEB-INF/views/crm/index.jsp").forward(request, response);
    }

    @Override protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            String id = request.getParameter("id"); UUID.fromString(id);
            HttpResponse<Void> backendResponse = client.send(HttpRequest.newBuilder(URI.create(backendUrl + "/api/v1/crm/leads/" + id + "/won")).method("PATCH", HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.discarding());
            String message = backendResponse.statusCode() == HttpServletResponse.SC_OK ? "Příležitost byla označena jako vyhraná." : "Změnu stavu backend odmítl.";
            response.sendRedirect("crm?message=" + URLEncoder.encode(message, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException exception) { response.sendRedirect("crm?error=" + URLEncoder.encode("Neplatná příležitost.", StandardCharsets.UTF_8)); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); response.sendRedirect("crm?error=" + URLEncoder.encode("Změna stavu byla přerušena.", StandardCharsets.UTF_8)); }
    }
}