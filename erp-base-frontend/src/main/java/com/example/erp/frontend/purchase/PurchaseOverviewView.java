package com.example.erp.frontend.purchase;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PurchaseOverviewView(BigDecimal requestedValue, BigDecimal orderedValue, long requestedCount,
        List<PurchaseOrderView> orders) {
    public record PurchaseOrderView(UUID id, String orderNumber, String supplierName, LocalDate requestedOn,
            LocalDate expectedDeliveryDate, BigDecimal totalAmount, String status, UUID sourceWarehouseId,
            UUID destinationWarehouseId, UUID productId, Integer quantity, int receivedQuantity,
            List<PurchaseLineView> lines) { }

        public record WarehouseView(UUID id, String name, String ownerType) { }
        public record PurchaseLineView(UUID productId, int quantity, BigDecimal unitPrice, int receivedQuantity) { }
        public record ProductView(UUID id, String sku, String name, String unit, String description,
                BigDecimal price, BigDecimal purchasePrice, BigDecimal vatRate,
                BigDecimal eshopMarginPercent, UUID categoryId, String imageUrl, boolean active,
                List<ProductImageView> images) { }
        public record ProductImageView(UUID id, String imageUrl, boolean active, int sortOrder) { }
}