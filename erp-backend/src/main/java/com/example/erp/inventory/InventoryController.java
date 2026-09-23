package com.example.erp.inventory;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {
    private static final String PRODUCT_NAME = "Vepřová krkovice bez kosti";
    private static final String PRODUCT_SKU = "PORK-NECK-01";
    private static final String UNIT = "kg";

    private final InventoryItemRepository items;

    public InventoryController(InventoryItemRepository items) { this.items = items; }

    @GetMapping("/overview")
    public InventoryOverview overview() {
        List<InventoryItemResponse> rows = items.findAllByOrderByQuantityAsc().stream()
                .map(InventoryItemResponse::from).toList();
        int totalQuantity = rows.stream().mapToInt(InventoryItemResponse::quantity).sum();
        long lowStockCount = rows.stream().filter(row -> row.quantity() < row.reorderLevel()).count();
        BigDecimal stockValue = rows.stream().map(row -> row.unitCost().multiply(BigDecimal.valueOf(row.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2);
        return new InventoryOverview(totalQuantity, stockValue, lowStockCount, rows);
    }

    @PatchMapping("/items/{id}/receive")
    public InventoryItemResponse receive(@PathVariable UUID id, @RequestBody ReceiveStockRequest request) {
        if (request.quantity() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Received quantity must be positive.");
        }
        InventoryItem item = items.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Inventory item was not found."));
        item.receive(request.quantity());
        return InventoryItemResponse.from(items.save(item));
    }

    public record ReceiveStockRequest(int quantity) { }

    public record InventoryOverview(int totalQuantity, BigDecimal stockValue, long lowStockCount,
            List<InventoryItemResponse> items) { }

    public record InventoryItemResponse(UUID id, String productName, String sku, String locationName, int quantity,
            int reorderLevel, BigDecimal unitCost, String unit) {
        static InventoryItemResponse from(InventoryItem item) {
            return new InventoryItemResponse(item.getId(), PRODUCT_NAME, PRODUCT_SKU, item.getLocationName(),
                    item.getQuantity(), item.getReorderLevel(), item.getUnitCost(), UNIT);
        }
    }
}