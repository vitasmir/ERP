package com.example.erp.frontend.inventory;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record InventoryMovementsView(List<MovementView> items, int page, int size, long totalElements) {
    public record MovementView(long id, UUID inventoryItemId, String productName, String sku, String unit,
            String locationName, String type, int quantity, int balanceAfter, BigDecimal unitCost,
            String reference, String note, String actorName, String createdAt) { }
}
