package com.example.erp.inventory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import com.example.erp.catalog.ProductCategory;
import com.example.erp.catalog.ProductCategoryRepository;
import com.example.erp.catalog.ProductRepository;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {
    private final InventoryItemRepository items;
    private final ProductRepository products;
    private final ProductCategoryRepository categories;

    public InventoryController(InventoryItemRepository items, ProductRepository products,
            ProductCategoryRepository categories) {
        this.items = items;
        this.products = products;
        this.categories = categories;
    }

    @GetMapping("/overview")
    public InventoryOverview overview() {
        CategoryTree categoryTree = categoryTree();
        List<InventoryItemResponse> rows = items.findAllByOrderByQuantityAsc().stream()
                .map(item -> {
                    Product product = product(item.getProductId());
                    return InventoryItemResponse.from(item, product, categoryTree.detailsFor(product.getCategoryId()));
                }).sorted(Comparator.comparing(InventoryItemResponse::categoryPath)
                        .thenComparing(InventoryItemResponse::productName)
                        .thenComparing(InventoryItemResponse::locationName)).toList();
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
        Product product = product(saved.getProductId());
        return InventoryItemResponse.from(saved, product, categoryTree().detailsFor(product.getCategoryId()));
    }

    private Product product(UUID id) {
        return products.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product was not found."));
    }

    private CategoryTree categoryTree() {
        Map<UUID, CategoryNode> nodesById = new HashMap<>();
        for (ProductCategory category : categories.findAllByOrderBySortOrderAscNameAsc()) {
            nodesById.put(category.getId(), new CategoryNode(category));
        }

        List<CategoryNode> roots = new ArrayList<>();
        for (CategoryNode child : nodesById.values()) {
            CategoryNode parent = nodesById.get(child.category().getParentId());
            if (parent == null) {
                roots.add(child);
            } else {
                parent.children().add(child);
            }
        }

        Map<UUID, CategoryDetails> detailsByCategoryId = new HashMap<>();
        for (CategoryNode root : roots) {
            addCategoryDetails(root, "", 0, detailsByCategoryId, new HashSet<>());
        }
        return new CategoryTree(detailsByCategoryId);
    }

    private void addCategoryDetails(CategoryNode node, String parentPath, int depth,
            Map<UUID, CategoryDetails> detailsByCategoryId, Set<UUID> visited) {
        UUID categoryId = node.category().getId();
        if (!visited.add(categoryId)) {
            return;
        }
        String path = parentPath.isEmpty() ? node.category().getName() : parentPath + " / " + node.category().getName();
        detailsByCategoryId.put(categoryId, new CategoryDetails(path, depth));
        for (CategoryNode child : node.children()) {
            addCategoryDetails(child, path, depth + 1, detailsByCategoryId, visited);
        }
    }

    private static final class CategoryTree {
        private final Map<UUID, CategoryDetails> detailsByCategoryId;

        private CategoryTree(Map<UUID, CategoryDetails> detailsByCategoryId) {
            this.detailsByCategoryId = detailsByCategoryId;
        }

        CategoryDetails detailsFor(UUID categoryId) {
            return detailsByCategoryId.getOrDefault(categoryId, new CategoryDetails("Bez kategorie", 0));
        }
    }

    private record CategoryNode(ProductCategory category, List<CategoryNode> children) {
        CategoryNode(ProductCategory category) {
            this(category, new ArrayList<>());
        }
    }

    public record ReceiveStockRequest(int quantity) { }

    public record InventoryOverview(int totalQuantity, BigDecimal stockValue, long lowStockCount,
            List<InventoryItemResponse> items) { }

    public record InventoryItemResponse(UUID id, String productName, String sku, String locationName, int quantity,
            int reorderLevel, BigDecimal unitCost, String unit, String categoryPath, int categoryDepth) {
        static InventoryItemResponse from(InventoryItem item, Product product, CategoryDetails category) {
            return new InventoryItemResponse(item.getId(), product.getName(), product.getSku(), item.getLocationName(),
                item.getQuantity(), item.getReorderLevel(), item.getUnitCost(), product.getUnit(),
                category.path(), category.depth());
        }
    }

    private record CategoryDetails(String path, int depth) { }
}