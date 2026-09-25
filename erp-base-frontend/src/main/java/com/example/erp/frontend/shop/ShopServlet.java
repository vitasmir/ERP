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
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
    private static final String DELIVERY_ATTRIBUTE = "eshop.delivery";
    private static final String PAYMENT_ATTRIBUTE = "eshop.payment";
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
            SettingsResponse settings = get("/api/v1/settings", SettingsResponse.class);
            UUID selectedCategoryId = categoryId(request.getParameter("categoryId"));
            HttpSession session = request.getSession();
                ShopView.DeliveryDetails delivery = delivery(session);
                boolean paymentOpen = "payment".equals(request.getParameter("checkout")) && delivery != null;
                boolean checkoutOpen = "delivery".equals(request.getParameter("checkout"))
                    || (delivery != null && !paymentOpen);
                ShopView shop = buildView(categories, products, selectedCategoryId, cart(session), checkoutOpen,
                        paymentOpen, settings.deliveryFee(), delivery);
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
            if ("delivery".equals(action)) {
                saveDelivery(request, response);
                return;
            }
            if ("payment".equals(action)) {
                savePayment(request, response);
                return;
            }
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
            response.sendRedirect("eshop" + categoryRedirect(request.getParameter("categoryId")));
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

        private ShopView buildView(List<ShopView.Category> categories, List<ShopView.Product> allProducts,
            UUID selectedCategoryId, Map<UUID, Integer> cart, boolean checkoutOpen, boolean paymentOpen,
            BigDecimal deliveryFee, ShopView.DeliveryDetails delivery) {
        List<ShopView.Product> visibleProducts = selectedCategoryId == null ? allProducts : allProducts.stream()
            .filter(product -> selectedCategoryId.equals(product.categoryId())).toList();
        Map<UUID, ShopView.Product> productsById = allProducts.stream()
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
        List<ShopView.CategoryOption> categoryOptions = new ArrayList<>();
        flattenCategories(categories, 0, categoryOptions);
        return new ShopView(categories, categoryOptions, visibleProducts, allProducts.size(), selectedCategoryId,
            lines, cartCount, cartTotal.setScale(2), checkoutOpen, paymentOpen, deliveryFee, delivery);
    }

    private void flattenCategories(List<ShopView.Category> categories, int depth,
            List<ShopView.CategoryOption> result) {
        for (ShopView.Category category : categories) {
            result.add(new ShopView.CategoryOption(category.id(), category.name(), depth));
            flattenCategories(category.children(), depth + 1, result);
        }
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

    private UUID categoryId(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private String categoryRedirect(String value) {
        UUID selectedCategoryId = categoryId(value);
        return selectedCategoryId == null ? "" : "?categoryId=" + selectedCategoryId;
    }

    private ShopView.DeliveryDetails delivery(HttpSession session) {
        Object value = session.getAttribute(DELIVERY_ATTRIBUTE);
        return value instanceof ShopView.DeliveryDetails details ? details : null;
    }

    private void saveDelivery(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String firstName = text(request.getParameter("firstName"));
        String lastName = text(request.getParameter("lastName"));
        String phone = text(request.getParameter("phone"));
        String street = text(request.getParameter("street"));
        String city = text(request.getParameter("city"));
        String postalCode = text(request.getParameter("postalCode"));
        if (firstName.isBlank() || lastName.isBlank() || phone.isBlank() || street.isBlank()
                || city.isBlank() || postalCode.isBlank() || !phone.matches("[+0-9 ()-]{9,20}")
                || !postalCode.matches("\\d{3} ?\\d{2}")) {
            response.sendRedirect("eshop?checkout=delivery&error=" + java.net.URLEncoder.encode(
                    "Vyplňte jméno, telefon a úplnou adresu zákazníka.", java.nio.charset.StandardCharsets.UTF_8));
            return;
        }
        request.getSession().setAttribute(DELIVERY_ATTRIBUTE,
                new ShopView.DeliveryDetails(firstName, lastName, phone, street, city, postalCode));
        response.sendRedirect("eshop?checkout=payment#payment-step");
    }

    private void savePayment(HttpServletRequest request, HttpServletResponse response) throws IOException, InterruptedException {
        String paymentMethod = text(request.getParameter("paymentMethod"));
        if (!"card".equals(paymentMethod) && !"cod".equals(paymentMethod)) {
            response.sendRedirect("eshop?checkout=payment&error=" + java.net.URLEncoder.encode(
                    "Vyberte způsob platby.", java.nio.charset.StandardCharsets.UTF_8));
            return;
        }
        if ("card".equals(paymentMethod)) {
            String cardNumber = text(request.getParameter("cardNumber1")) + text(request.getParameter("cardNumber2"))
                    + text(request.getParameter("cardNumber3")) + text(request.getParameter("cardNumber4"));
            String cardExpiry = text(request.getParameter("cardExpiry"));
            String cardCvc = text(request.getParameter("cardCvc"));
            if (!cardNumber.matches("\\d{16}") || !cardExpiry.matches("(0[1-9]|1[0-2])/\\d{2}")
                    || !cardCvc.matches("\\d{3,4}")) {
                response.sendRedirect("eshop?checkout=payment&error=" + java.net.URLEncoder.encode(
                        "Zkontrolujte číslo karty, platnost a CVV.", java.nio.charset.StandardCharsets.UTF_8));
                return;
            }
        }
        HttpSession session = request.getSession();
            Map<UUID, Integer> cart = cart(session);
            if (cart.isEmpty()) {
                response.sendRedirect("eshop?error=" + java.net.URLEncoder.encode(
                    "Košík je prázdný.", java.nio.charset.StandardCharsets.UTF_8));
                return;
            }
            List<EcommerceView.Product> sourceProducts = getList("/api/v1/catalog/products", new TypeReference<>() { });
            Map<UUID, EcommerceView.Product> products = sourceProducts.stream()
                .collect(Collectors.toMap(EcommerceView.Product::id, product -> product));
            BigDecimal total = BigDecimal.ZERO;
            int itemCount = 0;
            for (Map.Entry<UUID, Integer> entry : cart.entrySet()) {
                EcommerceView.Product product = products.get(entry.getKey());
                if (product == null || entry.getValue() <= 0) continue;
                itemCount += entry.getValue();
                total = total.add(product.price().multiply(BigDecimal.valueOf(entry.getValue())));
            }
            if (itemCount <= 0) {
                response.sendRedirect("eshop?error=" + java.net.URLEncoder.encode(
                    "Produkty v košíku již nejsou dostupné.", java.nio.charset.StandardCharsets.UTF_8));
                return;
            }
            boolean paid = "card".equals(paymentMethod);
            PosTransactionRequest posTransaction = new PosTransactionRequest(itemCount, total, paid ? "CARD" : "CASH", paid);
            HttpResponse<String> posResponse = send("POST", "/api/v1/pos/transactions", mapper.writeValueAsString(posTransaction));
            if (posResponse.statusCode() != HttpServletResponse.SC_CREATED) {
                response.sendRedirect("eshop?checkout=payment&error=" + java.net.URLEncoder.encode(
                    "Prodej se nepodařilo předat do Pokladny.", java.nio.charset.StandardCharsets.UTF_8));
                return;
            }
        session.removeAttribute(CART_ATTRIBUTE);
        session.removeAttribute(DELIVERY_ATTRIBUTE);
        session.removeAttribute(PAYMENT_ATTRIBUTE);
        response.sendRedirect("eshop?order=completed");
    }

    private record PosTransactionRequest(int itemCount, BigDecimal totalAmount, String paymentMethod, boolean paid) { }

    private String text(String value) {
        return value == null ? "" : value.trim();
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

    private <T> T get(String path, Class<T> type) throws IOException, InterruptedException {
        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create(backendUrl + path)).timeout(Duration.ofSeconds(5)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != HttpServletResponse.SC_OK) {
            throw new IOException("Backend returned HTTP " + response.statusCode());
        }
        return mapper.readValue(response.body(), type);
    }

    private HttpResponse<String> send(String method, String path, String body) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(backendUrl + path))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(body))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SettingsResponse(BigDecimal deliveryFee) { }
}
