package com.example.erp.inventory;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "inventory_items")
public class InventoryItem {
    @Id
    private UUID id;

    @Column(name = "product_id")
    private UUID productId;

    @Column(name = "location_name")
    private String locationName;

    private int quantity;

    @Column(name = "reorder_level")
    private int reorderLevel;

    @Column(name = "unit_cost")
    private BigDecimal unitCost;

    @Column(name = "ordered_from_central")
    private int orderedFromCentral;

    protected InventoryItem() { }

    public static InventoryItem create(UUID productId, String locationName) {
        InventoryItem item = new InventoryItem();
        item.id = UUID.randomUUID();
        item.productId = productId;
        item.locationName = locationName;
        item.quantity = 0;
        item.reorderLevel = 0;
        item.unitCost = BigDecimal.ZERO;
        item.orderedFromCentral = 0;
        return item;
    }

    public UUID getId() { return id; }
    public UUID getProductId() { return productId; }
    public String getLocationName() { return locationName; }
    public int getQuantity() { return quantity; }
    public int getReorderLevel() { return reorderLevel; }
    public BigDecimal getUnitCost() { return unitCost; }
    public int getOrderedFromCentral() { return orderedFromCentral; }

    public void receive(int receivedQuantity) {
        quantity += receivedQuantity;
    }

    public void updateStockSettings(int newReorderLevel, BigDecimal newUnitCost) {
        reorderLevel = newReorderLevel;
        unitCost = newUnitCost;
    }

    public void orderFromCentral(int requestedQuantity) {
        orderedFromCentral = requestedQuantity;
    }
}