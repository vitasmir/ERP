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

import com.example.erp.catalog.Product;
import com.example.erp.catalog.ProductRepository;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {
    private final InventoryItemRepository items;
    private final ProductRepository products;

    public InventoryController(InventoryItemRepository items, ProductRepository products) {
        this.items = items;
        this.products = products;
    }

    @GetMapping("/overview")
    public InventoryOverview overview() {
        List<InventoryItemResponse> rows = items.findAllByOrderByQuantityAsc().stream()
                .map(item -> InventoryItemResponse.from(item, product(item.getProductId()))).toList();
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
        InventoryItem saved = items.save(item);
        return InventoryItemResponse.from(saved, product(saved.getProductId()));
    }

    private Product product(UUID id) {
        return products.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product was not found."));
    }

    public record ReceiveStockRequest(int quantity) { }

    public record InventoryOverview(int totalQuantity, BigDecimal stockValue, long lowStockCount,
            List<InventoryItemResponse> items) { }

    public record InventoryItemResponse(UUID id, String productName, String sku, String locationName, int quantity,
            int reorderLevel, BigDecimal unitCost, String unit) {
        static InventoryItemResponse from(InventoryItem item, Product product) {
            return new InventoryItemResponse(item.getId(), product.getName(), product.getSku(), item.getLocationName(),
                item.getQuantity(), item.getReorderLevel(), item.getUnitCost(), product.getUnit());
        }
    }
}