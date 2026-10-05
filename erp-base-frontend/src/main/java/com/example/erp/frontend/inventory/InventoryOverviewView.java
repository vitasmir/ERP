package com.example.erp.frontend.inventory;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record InventoryOverviewView(long totalQuantity, BigDecimal stockValue, long lowStockCount,
        List<InventoryItemView> items, List<InventoryProductView> products, boolean canEdit) {
        public record InventoryItemView(UUID id, String productName, String imageUrl, String sku, String locationName, int quantity,
            int reorderLevel, BigDecimal unitCost, int orderedFromCentral, String unit, String categoryPath,
            int categoryDepth) { }

    public record InventoryProductView(UUID productId, String productName, String sku, String unit,
            String description, String imageUrl, String categoryPath, int categoryDepth, long centralQuantity,
            long warehouseQuantity, int locationCount, List<WarehouseStockView> warehouses) { }

    public record WarehouseStockView(UUID inventoryItemId, String locationName, int quantity,
            int orderedFromCentral) { }
}