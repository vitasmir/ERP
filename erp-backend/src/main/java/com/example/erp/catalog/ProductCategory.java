package com.example.erp.catalog;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "product_categories")
public class ProductCategory {
    @Id
    private UUID id;

    @Column(name = "parent_id")
    private UUID parentId;
    private String name;
    private String slug;

    @Column(name = "sort_order")
    private int sortOrder;
    private boolean active;

    protected ProductCategory() { }

    public static ProductCategory create(String name, String slug, UUID parentId, int sortOrder, boolean active) {
        ProductCategory category = new ProductCategory();
        category.id = UUID.randomUUID();
        category.update(name, slug, parentId, sortOrder, active);
        return category;
    }

    public void update(String name, String slug, UUID parentId, int sortOrder, boolean active) {
        this.name = name;
        this.slug = slug;
        this.parentId = parentId;
        this.sortOrder = sortOrder;
        this.active = active;
    }

    public UUID getId() { return id; }
    public UUID getParentId() { return parentId; }
    public String getName() { return name; }
    public String getSlug() { return slug; }
    public int getSortOrder() { return sortOrder; }
    public boolean isActive() { return active; }
}