package com.example.erp.frontend.shop;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ShopView(List<Category> categories, List<CategoryOption> categoryOptions, List<Product> products,
                int totalProductCount, UUID selectedCategoryId, List<CartLine> cart, int cartCount, BigDecimal cartTotal,
                boolean checkoutOpen, boolean paymentOpen, BigDecimal deliveryFee, DeliveryDetails delivery) {
    public record Category(UUID id, UUID parentId, String name, String slug, int sortOrder,
            boolean active, List<Category> children) { }

        public record CategoryOption(UUID id, String name, int depth, long productCount) { }

    public record Product(UUID id, String sku, String name, String unit, String description,
            BigDecimal price, UUID categoryId, String imageUrl, int availableQuantity) { }

    public record CartLine(Product product, int quantity, BigDecimal lineTotal) { }

    public record DeliveryDetails(String firstName, String lastName, String phone, String street,
            String city, String postalCode) { }
}
