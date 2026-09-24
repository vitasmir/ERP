package com.example.erp.frontend.shop;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ShopView(List<Category> categories, List<Product> products, List<CartLine> cart,
        int cartCount, BigDecimal cartTotal) {
    public record Category(UUID id, UUID parentId, String name, String slug, int sortOrder,
            boolean active, List<Category> children) { }

    public record Product(UUID id, String sku, String name, String unit, String description,
            BigDecimal price, UUID categoryId, String imageUrl, int availableQuantity) { }

    public record CartLine(Product product, int quantity, BigDecimal lineTotal) { }
}
