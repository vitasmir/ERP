package com.example.erp.frontend.purchase;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record PurchaseOverviewView(BigDecimal requestedValue, BigDecimal orderedValue, long requestedCount,
        List<PurchaseOrderView> orders) {
    public record PurchaseOrderView(UUID id, String orderNumber, String supplierName, String requestedOn,
            String expectedDeliveryDate, BigDecimal totalAmount, String status, UUID sourceWarehouseId,
            UUID destinationWarehouseId, UUID productId, Integer quantity, int receivedQuantity) { }

    public record WarehouseView(UUID id, String name, String ownerType) { }
    public record ProductView(UUID id, String name, String sku, String unit) { }
}