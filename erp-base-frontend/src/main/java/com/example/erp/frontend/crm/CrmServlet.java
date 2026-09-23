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
        if (request.getParameter("id") == null) {
            createLead(request, response);
            return;
        }
        try {
            String id = request.getParameter("id"); UUID.fromString(id);
            HttpResponse<Void> backendResponse = client.send(HttpRequest.newBuilder(URI.create(backendUrl + "/api/v1/crm/leads/" + id + "/won")).method("PATCH", HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.discarding());
            String message = backendResponse.statusCode() == HttpServletResponse.SC_OK ? "Příležitost byla označena jako vyhraná." : "Změnu stavu backend odmítl.";
            response.sendRedirect("crm?message=" + URLEncoder.encode(message, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException exception) { response.sendRedirect("crm?error=" + URLEncoder.encode("Neplatná příležitost.", StandardCharsets.UTF_8)); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); response.sendRedirect("crm?error=" + URLEncoder.encode("Změna stavu byla přerušena.", StandardCharsets.UTF_8)); }
    }

    private void createLead(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            String body = mapper.writeValueAsString(java.util.Map.of(
                    "name", request.getParameter("name"),
                    "customerName", request.getParameter("customerName"),
                    "expectedRevenue", new java.math.BigDecimal(request.getParameter("expectedRevenue")),
                    "probability", Integer.parseInt(request.getParameter("probability")),
                    "expectedCloseDate", request.getParameter("expectedCloseDate")));
            HttpResponse<Void> backendResponse = client.send(HttpRequest.newBuilder(URI.create(backendUrl + "/api/v1/crm/leads"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.discarding());
            String message = backendResponse.statusCode() == HttpServletResponse.SC_OK
                    ? "Příležitost byla vytvořena." : "Vytvoření příležitosti backend odmítl.";
            response.sendRedirect("crm?message=" + URLEncoder.encode(message, StandardCharsets.UTF_8));
        } catch (NumberFormatException | NullPointerException exception) {
            response.sendRedirect("crm?error=" + URLEncoder.encode("Vyplňte platné hodnoty příležitosti.", StandardCharsets.UTF_8));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            response.sendRedirect("crm?error=" + URLEncoder.encode("Vytvoření příležitosti bylo přerušeno.", StandardCharsets.UTF_8));
        }
    }

    @Override protected void doPut(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String id = request.getParameter("id");
        String stage = request.getParameter("stage");
        try {
            UUID.fromString(id);
            String body = mapper.writeValueAsString(java.util.Map.of("stage", stage));
            HttpResponse<String> backendResponse = client.send(HttpRequest.newBuilder(URI.create(backendUrl + "/api/v1/crm/leads/" + id + "/stage"))
                    .header("Content-Type", "application/json").method("PATCH", HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
            response.setStatus(backendResponse.statusCode());
            response.setContentType("application/json");
            response.getWriter().write(backendResponse.body());
        } catch (IllegalArgumentException exception) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Neplatná příležitost nebo fáze.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Změna fáze byla přerušena.");
        }
    }
}