package com.example.erp.catalog;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "products")
public class Product {
    @Id
    private UUID id;
    private String sku;
    private String name;
    private String unit;
    private String description;
    private BigDecimal price;

    @Column(name = "category_id")
    private UUID categoryId;

    @Column(name = "image_url")
    private String imageUrl;
    private boolean active;

    protected Product() { }

    public static Product create(String sku, String name, String unit, String description, BigDecimal price,
            UUID categoryId, String imageUrl, boolean active) {
        Product product = new Product();
        product.id = UUID.randomUUID();
        product.update(sku, name, unit, description, price, categoryId, imageUrl, active);
        return product;
    }

    public void update(String sku, String name, String unit, String description, BigDecimal price,
            UUID categoryId, String imageUrl, boolean active) {
        this.sku = sku;
        this.name = name;
        this.unit = unit;
        this.description = description;
        this.price = price;
        this.categoryId = categoryId;
        this.imageUrl = imageUrl;
        this.active = active;
    }

    public UUID getId() { return id; }
    public String getSku() { return sku; }
    public String getName() { return name; }
    public String getUnit() { return unit; }
    public String getDescription() { return description; }
    public BigDecimal getPrice() { return price; }
    public UUID getCategoryId() { return categoryId; }
    public String getImageUrl() { return imageUrl; }
    public boolean isActive() { return active; }
}