package com.example.erp.catalog;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "product_images")
public class ProductImage {
    @Id
    private UUID id;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "image_url", nullable = false)
    private String imageUrl;

    private boolean active;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected ProductImage() { }

    public static ProductImage create(UUID productId, String imageUrl, boolean active, int sortOrder) {
        ProductImage image = new ProductImage();
        image.id = UUID.randomUUID();
        image.productId = productId;
        image.imageUrl = imageUrl;
        image.active = active;
        image.sortOrder = sortOrder;
        return image;
    }

    public void activate() { this.active = true; }
    public void deactivate() { this.active = false; }
    public UUID getId() { return id; }
    public UUID getProductId() { return productId; }
    public String getImageUrl() { return imageUrl; }
    public boolean isActive() { return active; }
    public int getSortOrder() { return sortOrder; }
}
