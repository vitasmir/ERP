package com.example.erp.inventory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
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

    @Column(name = "warehouse_id")
    private UUID warehouseId;

    @Column(name = "location_name")
    private String locationName;

    private int quantity;

    @Column(name = "reorder_level")
    private int reorderLevel;

    @Column(name = "unit_cost")
    private BigDecimal unitCost;

    @Column(name = "ordered_from_central")
    private int orderedFromCentral;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    protected InventoryItem() { }

    public static InventoryItem create(UUID productId, String locationName) {
        return create(productId, null, locationName);
    }

    public static InventoryItem create(UUID productId, UUID warehouseId, String locationName) {
        InventoryItem item = new InventoryItem();
        item.id = UUID.randomUUID();
        item.productId = productId;
        item.warehouseId = warehouseId;
        item.locationName = locationName;
        item.quantity = 0;
        item.reorderLevel = 0;
        item.unitCost = BigDecimal.ZERO;
        item.orderedFromCentral = 0;
        item.updatedAt = LocalDateTime.now();
        return item;
    }

    public UUID getId() { return id; }
    public UUID getProductId() { return productId; }
    public UUID getWarehouseId() { return warehouseId; }
    public String getLocationName() { return locationName; }
    public int getQuantity() { return quantity; }
    public int getReorderLevel() { return reorderLevel; }
    public BigDecimal getUnitCost() { return unitCost; }
    public int getOrderedFromCentral() { return orderedFromCentral; }

    public void receive(int receivedQuantity) {
        if (receivedQuantity <= 0) {
            throw new IllegalArgumentException("Received quantity must be positive.");
        }
        quantity = Math.addExact(quantity, receivedQuantity);
        updatedAt = LocalDateTime.now();
    }

    public void receivePurchase(int receivedQuantity, BigDecimal purchaseUnitCost) {
        if (receivedQuantity <= 0 || purchaseUnitCost == null || purchaseUnitCost.signum() < 0) {
            throw new IllegalArgumentException("Purchase quantity and unit cost must be valid.");
        }
        receivePurchaseValue(receivedQuantity, purchaseUnitCost.multiply(BigDecimal.valueOf(receivedQuantity)));
    }

    void receivePurchaseValue(int receivedQuantity, BigDecimal purchaseValue) {
        if (receivedQuantity <= 0 || purchaseValue == null || purchaseValue.signum() < 0) {
            throw new IllegalArgumentException("Purchase quantity and value must be valid.");
        }
        int balance = Math.addExact(quantity, receivedQuantity);
        BigDecimal value = unitCost.multiply(BigDecimal.valueOf(quantity))
                .add(purchaseValue);
        unitCost = value.divide(BigDecimal.valueOf(balance), 2, RoundingMode.HALF_UP);
        receive(receivedQuantity);
    }

    public void dispatch(int dispatchedQuantity) {
        if (dispatchedQuantity <= 0 || dispatchedQuantity > quantity) {
            throw new IllegalArgumentException("Dispatched quantity exceeds available stock.");
        }
        quantity -= dispatchedQuantity;
        updatedAt = LocalDateTime.now();
    }

    public void updateStockSettings(int newReorderLevel, BigDecimal newUnitCost) {
        reorderLevel = newReorderLevel;
        unitCost = newUnitCost;
        updatedAt = LocalDateTime.now();
    }

    public void orderFromCentral(int requestedQuantity) {
        orderedFromCentral = requestedQuantity;
        updatedAt = LocalDateTime.now();
    }
}