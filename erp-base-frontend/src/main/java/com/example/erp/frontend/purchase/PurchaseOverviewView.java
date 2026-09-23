package com.example.erp.frontend.purchase;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record PurchaseOverviewView(BigDecimal requestedValue, BigDecimal orderedValue, long requestedCount,
        List<PurchaseOrderView> orders) {
    public record PurchaseOrderView(UUID id, String orderNumber, String supplierName, String requestedOn,
            String expectedDeliveryDate, BigDecimal totalAmount, String status) { }
}