package com.example.erp.frontend.ecommerce;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.example.erp.frontend.settings.SettingsView;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet("/ecommerce")
public class EcommerceServlet extends HttpServlet {
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final String backendUrl = System.getenv().getOrDefault("BACKEND_URL", "http://localhost:8080");

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        try {
            EcommerceView.Homepage homepage = get("/api/v1/catalog/homepage", EcommerceView.Homepage.class);
            SettingsView settings = get("/api/v1/settings", SettingsView.class);
            List<EcommerceView.Category> categories = getList("/api/v1/catalog/categories/tree", new TypeReference<>() { });
            List<EcommerceView.Product> products = getList("/api/v1/catalog/products", new TypeReference<>() { });
            List<EcommerceView.Product> enriched = new ArrayList<>();
            for (EcommerceView.Product product : products) {
                List<EcommerceView.Availability> availability = getList("/api/v1/catalog/products/" + product.id() + "/availability", new TypeReference<>() { });
                enriched.add(new EcommerceView.Product(product.id(), product.sku(), product.name(), product.unit(), product.description(),
                    product.price(), product.purchasePrice(), product.vatRate(), product.eshopMarginPercent(), product.categoryId(), product.imageUrl(),
                    product.active(), product.images(), availability));
            }
                UUID selectedCategoryId = categoryId(request.getParameter("categoryId"));
                List<EcommerceView.Product> visibleProducts = selectedCategoryId == null ? enriched : enriched.stream()
                    .filter(product -> selectedCategoryId.equals(product.categoryId())).toList();
                request.setAttribute("selectedCategoryId", selectedCategoryId);
                request.setAttribute("eshopMarginPercent", settings.eshopMarginPercent());
                request.setAttribute("eshopDefaultVatRate", settings.eshopDefaultVatRate());
                request.setAttribute("ecommerce", new EcommerceView(homepage, categories, visibleProducts));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            request.setAttribute("error", "Načítání eCommerce bylo přerušeno.");
        } catch (IOException exception) {
            request.setAttribute("error", "Backend pro eCommerce není dostupný: " + exception.getMessage());
        }
        request.getRequestDispatcher("/WEB-INF/views/ecommerce/index.jsp").forward(request, response);
    }

    private UUID categoryId(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            String action = request.getParameter("action");
            switch (action) {
                case "homepage" -> { updateHomepage(request); redirect(response, "Homepage byla uložena.", null); }
                case "category" -> { createCategory(request); redirect(response, "Kategorie byla přidána.", null); }
                case "updateCategory" -> { updateCategory(request); redirect(response, "Kategorie byla přejmenována.", null); }
                case "deleteCategory" -> { deleteCategory(request); redirect(response, "Kategorie byla smazána.", null); }
                case "product" -> {
                    ProductSaveResponse saved = saveProduct(request);
                    redirect(response, "Produkt byl uložen.", null, saved.categoryId(), saved.id());
                }
                case "toggleProduct" -> { toggleProduct(request); redirect(response, "Stav produktu byl změněn.", null); }
                case "addImage" -> { addImage(request); redirect(response, "Obrázek byl přidán.", null); }
                case "activateImage" -> { activateImage(request); redirect(response, "Aktivní obrázek byl změněn.", null); }
                case "deleteImage" -> { deleteImage(request); redirect(response, "Obrázek byl odstraněn.", null); }
                case "removeFromCategory" -> { removeFromCategory(request); redirect(response, "Produkt byl odebrán z kategorie.", null); }
                case "deleteProduct" -> { deleteProduct(request); redirect(response, "Produkt byl smazán.", null); }
                case "import" -> { importProducts(request); redirect(response, "Produkty byly naimportovány.", null); }
                case "estimate" -> redirect(response, estimate(request), null);
                default -> redirect(response, null, "Neznámá eCommerce akce.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            redirect(response, null, "Požadavek byl přerušen.");
        } catch (IllegalArgumentException exception) {
            redirect(response, null, "Zkontrolujte zadané hodnoty.");
        }
    }

    private void updateHomepage(HttpServletRequest request) throws IOException, InterruptedException {
        HomepageRequest body = new HomepageRequest(request.getParameter("design"), request.getParameter("headline"),
                request.getParameter("subheadline"), new BigDecimal(request.getParameter("textX")), new BigDecimal(request.getParameter("textY")));
        sendMutation("PUT", "/api/v1/catalog/homepage", mapper.writeValueAsString(body));
    }

    private void createCategory(HttpServletRequest request) throws IOException, InterruptedException {
        String parent = request.getParameter("parentId");
        CategoryRequest body = new CategoryRequest(request.getParameter("name"), request.getParameter("slug"),
                parent == null || parent.isBlank() ? null : UUID.fromString(parent), 10, true);
        sendMutation("POST", "/api/v1/catalog/categories", mapper.writeValueAsString(body));
    }

    private void updateCategory(HttpServletRequest request) throws IOException, InterruptedException {
        String categoryId = request.getParameter("categoryId");
        CategoryRequest body = new CategoryRequest(request.getParameter("name"), request.getParameter("slug"),
                request.getParameter("parentId") == null || request.getParameter("parentId").isBlank()
                        ? null : UUID.fromString(request.getParameter("parentId")), 10, true);
        sendMutation("PUT", "/api/v1/catalog/categories/" + UUID.fromString(categoryId), mapper.writeValueAsString(body));
    }

    private void deleteCategory(HttpServletRequest request) throws IOException, InterruptedException {
        sendMutation("DELETE", "/api/v1/catalog/categories/" + UUID.fromString(request.getParameter("categoryId")), "");
    }

    private void importProducts(HttpServletRequest request) throws IOException, InterruptedException {
        List<ProductRequest> body = mapper.readValue(request.getParameter("products"), new TypeReference<>() { });
        sendMutation("POST", "/api/v1/catalog/products/import", mapper.writeValueAsString(body));
    }

    private ProductSaveResponse saveProduct(HttpServletRequest request) throws IOException, InterruptedException {
        String productId = request.getParameter("productId");
        String category = request.getParameter("categoryId");
        String price = request.getParameter("price");
        String purchasePrice = request.getParameter("purchasePrice");
        String vatRate = request.getParameter("vatRate");
        String margin = request.getParameter("eshopMarginPercent");
        ProductRequest body = new ProductRequest(request.getParameter("sku"), request.getParameter("name"),
            request.getParameter("unit"), request.getParameter("description"), price == null || price.isBlank() ? null : new BigDecimal(price),
            purchasePrice == null || purchasePrice.isBlank() ? null : new BigDecimal(purchasePrice),
            vatRate == null || vatRate.isBlank() ? null : new BigDecimal(vatRate),
            margin == null || margin.isBlank() ? null : new BigDecimal(margin),
            category == null || category.isBlank() ? null : UUID.fromString(category), request.getParameter("imageUrl"),
                "on".equals(request.getParameter("active")));
        String path = productId == null || productId.isBlank() ? "/api/v1/catalog/products" : "/api/v1/catalog/products/" + UUID.fromString(productId);
        HttpResponse<String> response = sendMutation(productId == null || productId.isBlank() ? "POST" : "PUT", path,
            mapper.writeValueAsString(body));
        return mapper.readValue(response.body(), ProductSaveResponse.class);
    }

    private void removeFromCategory(HttpServletRequest request) throws IOException, InterruptedException {
        sendMutation("PUT", "/api/v1/catalog/products/" + UUID.fromString(request.getParameter("productId")) + "/category", "");
    }

    private void toggleProduct(HttpServletRequest request) throws IOException, InterruptedException {
        UUID productId = UUID.fromString(request.getParameter("productId"));
        ActiveRequest body = new ActiveRequest("on".equals(request.getParameter("active")));
        sendMutation("PUT", "/api/v1/catalog/products/" + productId + "/active", mapper.writeValueAsString(body));
    }

    private void addImage(HttpServletRequest request) throws IOException, InterruptedException {
        UUID productId = UUID.fromString(request.getParameter("productId"));
        ImageRequest body = new ImageRequest(request.getParameter("imageUrl"), "on".equals(request.getParameter("active")));
        sendMutation("POST", "/api/v1/catalog/products/" + productId + "/images", mapper.writeValueAsString(body));
    }

    private void activateImage(HttpServletRequest request) throws IOException, InterruptedException {
        sendMutation("PUT", "/api/v1/catalog/products/" + UUID.fromString(request.getParameter("productId"))
                + "/images/" + UUID.fromString(request.getParameter("imageId")) + "/active", "");
    }

    private void deleteImage(HttpServletRequest request) throws IOException, InterruptedException {
        sendMutation("DELETE", "/api/v1/catalog/products/" + UUID.fromString(request.getParameter("productId"))
                + "/images/" + UUID.fromString(request.getParameter("imageId")), "");
    }

    private void deleteProduct(HttpServletRequest request) throws IOException, InterruptedException {
        sendMutation("DELETE", "/api/v1/catalog/products/" + UUID.fromString(request.getParameter("productId")), "");
    }

    private String estimate(HttpServletRequest request) throws IOException, InterruptedException {
        DeliveryRequest body = new DeliveryRequest(UUID.fromString(request.getParameter("productId")),
                Integer.parseInt(request.getParameter("quantity")), request.getParameter("postalCode"), request.getParameter("method"));
        HttpResponse<String> result = send("POST", "/api/v1/catalog/delivery-estimates", mapper.writeValueAsString(body));
        if (result.statusCode() != HttpServletResponse.SC_OK) return "Odhad doručení backend odmítl.";
        DeliveryResponse estimate = mapper.readValue(result.body(), DeliveryResponse.class);
        return estimate.available() ? estimate.label() + ": doručení do " + estimate.estimatedDate() : estimate.reason();
    }

    private <T> T get(String path, Class<T> type) throws IOException, InterruptedException {
        HttpResponse<String> response = client.send(com.example.erp.frontend.base.BackendRequests.newBuilder(URI.create(backendUrl + path)).GET().build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != HttpServletResponse.SC_OK) throw new IOException("Backend returned HTTP " + response.statusCode());
        return mapper.readValue(response.body(), type);
    }

    private <T> T getList(String path, TypeReference<T> type) throws IOException, InterruptedException {
        HttpResponse<String> response = client.send(com.example.erp.frontend.base.BackendRequests.newBuilder(URI.create(backendUrl + path)).GET().build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != HttpServletResponse.SC_OK) throw new IOException("Backend returned HTTP " + response.statusCode());
        return mapper.readValue(response.body(), type);
    }

    private HttpResponse<String> send(String method, String path, String body) throws IOException, InterruptedException {
        HttpRequest request = com.example.erp.frontend.base.BackendRequests.newBuilder(URI.create(backendUrl + path)).header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(body)).build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> sendMutation(String method, String path, String body) throws IOException, InterruptedException {
        HttpResponse<String> response = send(method, path, body);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalArgumentException("Backend mutation failed: " + response.statusCode());
        }
        return response;
    }

    private void redirect(HttpServletResponse response, String message, String error) throws IOException {
        String parameter = message != null ? "message=" + encode(message) : "error=" + encode(error);
        response.sendRedirect("ecommerce?" + parameter);
    }

    private void redirect(HttpServletResponse response, String message, String error,
            UUID categoryId, UUID productId) throws IOException {
        String parameter = message != null ? "message=" + encode(message) : "error=" + encode(error);
        if (categoryId != null) parameter += "&categoryId=" + categoryId;
        String fragment = productId == null ? "" : "#product-" + productId;
        response.sendRedirect("ecommerce?" + parameter + fragment);
    }

    private String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }

    private record HomepageRequest(String design, String headline, String subheadline, BigDecimal textX, BigDecimal textY) { }
    private record CategoryRequest(String name, String slug, UUID parentId, int sortOrder, boolean active) { }
    private record ActiveRequest(boolean active) { }
        private record ProductRequest(String sku, String name, String unit, String description, BigDecimal price,
            BigDecimal purchasePrice, BigDecimal vatRate, BigDecimal eshopMarginPercent, UUID categoryId,
            String imageUrl, boolean active) { }
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ProductSaveResponse(UUID id, UUID categoryId) { }
    private record ImageRequest(String imageUrl, boolean active) { }
    private record DeliveryRequest(UUID productId, int quantity, String postalCode, String method) { }
    private record DeliveryResponse(String method, String label, boolean available, String estimatedDate, String reason) { }
}