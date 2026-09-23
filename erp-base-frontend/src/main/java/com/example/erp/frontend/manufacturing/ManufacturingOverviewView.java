package com.example.erp.frontend.manufacturing;

import java.util.List;
import java.util.UUID;

public record ManufacturingOverviewView(int plannedQuantity, int completedQuantity, long activeOrderCount,
        List<ManufacturingOrderView> orders) {
    public record ManufacturingOrderView(UUID id, String orderNumber, String productName, String workCenter,
            int plannedQuantity, int completedQuantity, String plannedDate, String status) { }
}