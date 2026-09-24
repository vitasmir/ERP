package com.example.erp.frontend.inventory;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record InventoryOverviewView(int totalQuantity, BigDecimal stockValue, long lowStockCount,
        List<InventoryItemView> items) {
    public record InventoryItemView(UUID id, String productName, String sku, String locationName, int quantity,
            int reorderLevel, BigDecimal unitCost, String unit, String categoryPath, int categoryDepth) { }
}