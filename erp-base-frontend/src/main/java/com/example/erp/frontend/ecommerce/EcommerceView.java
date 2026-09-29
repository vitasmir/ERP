package com.example.erp.frontend.ecommerce;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record EcommerceView(Homepage homepage, List<Category> categories, List<Product> products) {
    public record Homepage(UUID id, String design, String headline, String subheadline, BigDecimal textX, BigDecimal textY) { }
    public record Category(UUID id, UUID parentId, String name, String slug, int sortOrder, boolean active, List<Category> children) { }
    public record Product(UUID id, String sku, String name, String unit, String description, BigDecimal price,
            BigDecimal purchasePrice, BigDecimal vatRate, BigDecimal eshopMarginPercent, UUID categoryId, String imageUrl, boolean active,
            List<Image> images, List<Availability> availability) { }
        public record Image(UUID id, String imageUrl, boolean active, int sortOrder) { }
    public record Availability(String productName, String locationName, int quantity, boolean available, String stockStatus) { }
}