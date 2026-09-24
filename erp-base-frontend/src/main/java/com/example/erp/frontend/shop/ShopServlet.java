package com.example.erp.frontend.shop;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.example.erp.frontend.ecommerce.EcommerceView;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@WebServlet({"/eshop", "/shop"})
public class ShopServlet extends HttpServlet {
    private static final String CART_ATTRIBUTE = "eshop.cart";
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final String backendUrl = System.getenv().getOrDefault("BACKEND_URL", "http://localhost:8080");

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        try {
            List<EcommerceView.Category> sourceCategories = getList("/api/v1/catalog/categories/tree", new TypeReference<>() { });
            List<EcommerceView.Product> sourceProducts = getList("/api/v1/catalog/products", new TypeReference<>() { });
            List<ShopView.Category> categories = sourceCategories.stream().map(this::toCategory).toList();
            List<ShopView.Product> products = loadAvailableProducts(sourceProducts);
            ShopView shop = buildView(categories, products, cart(request.getSession()));
            request.setAttribute("shop", shop);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            request.setAttribute("error", "Načítání e-shopu bylo přerušeno.");
        } catch (IOException exception) {
            request.setAttribute("error", "E-shop není dostupný: " + exception.getMessage());
        }
        request.getRequestDispatcher("/WEB-INF/views/shop/index.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            String action = request.getParameter("action");
            String productIdParameter = request.getParameter("productId");
            if (action == null || productIdParameter == null || productIdParameter.isBlank()) {
                throw new IllegalArgumentException("Produkt nebyl zadán.");
            }
            UUID productId = UUID.fromString(productIdParameter);
            List<EcommerceView.Product> sourceProducts = getList("/api/v1/catalog/products", new TypeReference<>() { });
            List<ShopView.Product> products = loadAvailableProducts(sourceProducts);
            ShopView.Product product = products.stream().filter(item -> item.id().equals(productId)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Produkt není dostupný."));
            Map<UUID, Integer> cart = cart(request.getSession());
            int current = cart.getOrDefault(productId, 0);
            int requested = switch (action == null ? "" : action) {
                case "add" -> current + positiveQuantity(request.getParameter("quantity"));
                case "increase" -> current + 1;
                case "decrease" -> current - 1;
                case "set" -> positiveQuantity(request.getParameter("quantity"));
                case "remove" -> 0;
                default -> throw new IllegalArgumentException("Neznámá akce košíku.");
            };
            if (requested <= 0) cart.remove(productId);
            else cart.put(productId, Math.min(requested, product.availableQuantity()));
            response.sendRedirect("eshop");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            response.sendRedirect("eshop?error=Požadavek byl přerušen.");
        } catch (IllegalArgumentException exception) {
            response.sendRedirect("eshop?error=" + java.net.URLEncoder.encode(exception.getMessage(), java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    private List<ShopView.Product> loadAvailableProducts(List<EcommerceView.Product> sourceProducts)
            throws IOException, InterruptedException {
        List<ShopView.Product> products = new ArrayList<>();
        for (EcommerceView.Product product : sourceProducts) {
            if (!product.active()) continue;
            List<EcommerceView.Availability> availability = getList(
                    "/api/v1/catalog/products/" + product.id() + "/availability", new TypeReference<>() { });
            int availableQuantity = availability.stream().mapToInt(EcommerceView.Availability::quantity).sum();
            products.add(new ShopView.Product(product.id(), product.sku(), product.name(), product.unit(),
                    product.description(), product.price(), product.categoryId(), product.imageUrl(), availableQuantity));
        }
        return products;
    }

    private ShopView buildView(List<ShopView.Category> categories, List<ShopView.Product> products,
            Map<UUID, Integer> cart) {
        Map<UUID, ShopView.Product> productsById = products.stream()
                .collect(Collectors.toMap(ShopView.Product::id, product -> product));
        List<ShopView.CartLine> lines = new ArrayList<>();
        int cartCount = 0;
        BigDecimal cartTotal = BigDecimal.ZERO;
        for (Map.Entry<UUID, Integer> entry : new LinkedHashMap<>(cart).entrySet()) {
            ShopView.Product product = productsById.get(entry.getKey());
            if (product == null) continue;
            int quantity = Math.min(entry.getValue(), product.availableQuantity());
            if (quantity <= 0) continue;
            BigDecimal lineTotal = product.price().multiply(BigDecimal.valueOf(quantity));
            lines.add(new ShopView.CartLine(product, quantity, lineTotal));
            cartCount += quantity;
            cartTotal = cartTotal.add(lineTotal);
        }
        return new ShopView(categories, products, lines, cartCount, cartTotal.setScale(2));
    }

    @SuppressWarnings("unchecked")
    private Map<UUID, Integer> cart(HttpSession session) {
        Object value = session.getAttribute(CART_ATTRIBUTE);
        if (value instanceof Map<?, ?> existing) return (Map<UUID, Integer>) existing;
        Map<UUID, Integer> created = new LinkedHashMap<>();
        session.setAttribute(CART_ATTRIBUTE, created);
        return created;
    }

    private int positiveQuantity(String value) {
        int quantity = Integer.parseInt(value);
        if (quantity <= 0) throw new IllegalArgumentException("Množství musí být kladné.");
        return quantity;
    }

    private ShopView.Category toCategory(EcommerceView.Category category) {
        return new ShopView.Category(category.id(), category.parentId(), category.name(), category.slug(),
                category.sortOrder(), category.active(), category.children().stream().map(this::toCategory).toList());
    }

    private <T> T getList(String path, TypeReference<T> type) throws IOException, InterruptedException {
        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create(backendUrl + path)).timeout(Duration.ofSeconds(5)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != HttpServletResponse.SC_OK) {
            throw new IOException("Backend returned HTTP " + response.statusCode());
        }
        return mapper.readValue(response.body(), type);
    }
}
