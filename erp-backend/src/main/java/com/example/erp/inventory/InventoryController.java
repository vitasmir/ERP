package com.example.erp.inventory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.erp.catalog.Product;
import com.example.erp.catalog.ProductCategory;
import com.example.erp.catalog.ProductCategoryRepository;
import com.example.erp.catalog.ProductRepository;
import com.example.erp.users.ApiAccess;
import com.example.erp.users.ErpUser;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {
    private final InventoryItemRepository items;
    private final ProductRepository products;
    private final ProductCategoryRepository categories;
    private final InventoryStockService stock;
    private final InventoryRecords records;
    private final ApiAccess access;

    public InventoryController(InventoryItemRepository items, ProductRepository products,
            ProductCategoryRepository categories, InventoryStockService stock, InventoryRecords records, ApiAccess access) {
        this.items = items;
        this.products = products;
        this.categories = categories;
        this.stock = stock;
        this.records = records;
        this.access = access;
    }

    @GetMapping("/overview")
    public InventoryOverview overview(@RequestAttribute("erpUser") ErpUser actor) {
        CategoryTree categoryTree = categoryTree();
        List<InventoryItem> inventoryItems = items.findAllByOrderByQuantityAsc();
        Map<UUID, Product> productsById = new HashMap<>();
        List<InventoryItemResponse> rows = inventoryItems.stream()
                .map(item -> {
                    Product product = productsById.computeIfAbsent(item.getProductId(), this::product);
                    return InventoryItemResponse.from(item, product, categoryTree.detailsFor(product.getCategoryId()));
                }).sorted(Comparator.comparing(InventoryItemResponse::categoryPath)
                        .thenComparing(InventoryItemResponse::productName)
                        .thenComparing(InventoryItemResponse::locationName)).toList();
        List<InventoryProductResponse> productRows = productRows(inventoryItems, productsById, categoryTree);
        long totalQuantity = rows.stream().mapToLong(InventoryItemResponse::quantity).sum();
        long lowStockCount = rows.stream().filter(row -> row.quantity() < row.reorderLevel()).count();
        BigDecimal stockValue = rows.stream().map(row -> row.unitCost().multiply(BigDecimal.valueOf(row.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2);
        return new InventoryOverview(totalQuantity, stockValue, lowStockCount, rows, productRows,
                access.canEdit(actor, "inventory"));
    }

    @PatchMapping("/items/{id}/receive")
    public InventoryItemResponse receive(@PathVariable UUID id, @Valid @RequestBody ReceiveStockRequest request,
            @RequestAttribute("erpUser") ErpUser actor) {
        if (request == null || request.quantity() <= 0 || request.reorderLevel() < 0
                || request.unitCost() == null || request.unitCost().signum() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Received quantity, minimum, and unit cost must be valid non-negative values.");
        }
        InventoryItem saved = stock.receive(id, request.quantity(), request.reorderLevel(), request.unitCost(),
                request.reference(), request.note(), actor);
        Product product = product(saved.getProductId());
        return InventoryItemResponse.from(saved, product, categoryTree().detailsFor(product.getCategoryId()));
    }

    @PatchMapping("/items/{id}/dispatch")
    public InventoryItemResponse dispatch(@PathVariable UUID id, @Valid @RequestBody DispatchStockRequest request,
            @RequestAttribute("erpUser") ErpUser actor) {
        InventoryItem saved = stock.dispatch(id, request.quantity(), request.reference(), request.note(), actor);
        Product product = product(saved.getProductId());
        return InventoryItemResponse.from(saved, product, categoryTree().detailsFor(product.getCategoryId()));
    }

    @GetMapping("/movements")
    public InventoryRecords.MovementPage movements(@RequestParam(required = false) UUID itemId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        if (itemId != null && !items.existsById(itemId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Skladová položka nebyla nalezena.");
        }
        return records.movements(itemId, page, size);
    }

    @PatchMapping("/orders")
    @Transactional
    public InventoryItemResponse orderFromCentral(@RequestBody OrderStockRequest request) {
        if (request == null || request.productId() == null || request.locationName() == null
                || request.locationName().isBlank() || request.quantity() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid warehouse order request.");
        }
        String locationName = request.locationName().trim();
        if (isCentralLocation(locationName)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Orders can only be created for a non-central warehouse.");
        }
        Product product = products.findForUpdate(request.productId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product was not found."));
        boolean knownWarehouse = items.findAll().stream()
                .anyMatch(item -> locationName.equals(item.getLocationName()) && !isCentralLocation(item.getLocationName()));
        if (!knownWarehouse) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Warehouse was not found.");
        }
        InventoryItem item = items.findByProductIdAndLocationName(product.getId(), locationName)
                .orElseGet(() -> InventoryItem.create(product.getId(), locationName));
        item.orderFromCentral(request.quantity());
        InventoryItem saved = items.save(item);
        return InventoryItemResponse.from(saved, product, categoryTree().detailsFor(product.getCategoryId()));
    }

    private Product product(UUID id) {
        return products.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product was not found."));
    }

        private List<InventoryProductResponse> productRows(List<InventoryItem> inventoryItems,
            Map<UUID, Product> productsById, CategoryTree categoryTree) {
            List<String> warehouseNames = inventoryItems.stream()
                .map(InventoryItem::getLocationName)
                .filter(locationName -> !isCentralLocation(locationName))
                .distinct()
                .sorted()
                .toList();
        Map<UUID, ProductStock> stockByProductId = new LinkedHashMap<>();
        for (InventoryItem item : inventoryItems) {
            Product product = productsById.computeIfAbsent(item.getProductId(), this::product);
            ProductStock stock = stockByProductId.computeIfAbsent(product.getId(), id ->
                new ProductStock(product, categoryTree.detailsFor(product.getCategoryId())));
            stock.add(item);
        }
        return stockByProductId.values().stream()
            .map(stock -> stock.toResponse(warehouseNames))
            .sorted(Comparator.comparing(InventoryProductResponse::categoryPath)
                .thenComparing(InventoryProductResponse::productName)
                .thenComparing(InventoryProductResponse::sku))
            .toList();
        }

        private static boolean isCentralLocation(String locationName) {
        return locationName != null
            && locationName.toLowerCase(Locale.ROOT).startsWith("centrální sklad");
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

    public record ReceiveStockRequest(@Min(1) int quantity, @Min(0) int reorderLevel,
            @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal unitCost,
            @Size(max = 120) String reference, @Size(max = 500) String note) { }

    public record DispatchStockRequest(@Min(1) int quantity, @NotBlank @Size(max = 120) String reference,
            @Size(max = 500) String note) { }

    public record OrderStockRequest(UUID productId, String locationName, int quantity) { }

    public record InventoryOverview(long totalQuantity, BigDecimal stockValue, long lowStockCount,
            List<InventoryItemResponse> items, List<InventoryProductResponse> products, boolean canEdit) { }

    public record InventoryItemResponse(UUID id, String productName, String imageUrl, String sku, String locationName, int quantity,
            int reorderLevel, BigDecimal unitCost, int orderedFromCentral, String unit, String categoryPath,
            int categoryDepth) {
        static InventoryItemResponse from(InventoryItem item, Product product, CategoryDetails category) {
            return new InventoryItemResponse(item.getId(), product.getName(), product.getImageUrl(), product.getSku(), item.getLocationName(),
                item.getQuantity(), item.getReorderLevel(), item.getUnitCost(), item.getOrderedFromCentral(), product.getUnit(),
                category.path(), category.depth());
        }
    }

    public record InventoryProductResponse(UUID productId, String productName, String sku, String unit,
            String description, String imageUrl, String categoryPath, int categoryDepth, long centralQuantity,
            long warehouseQuantity, int locationCount, List<WarehouseStockResponse> warehouses) { }

    public record WarehouseStockResponse(UUID inventoryItemId, String locationName, int quantity,
            int orderedFromCentral) { }

    private static final class ProductStock {
        private final Product product;
        private final CategoryDetails category;
        private long centralQuantity;
        private long warehouseQuantity;
        private int locationCount;

        private ProductStock(Product product, CategoryDetails category) {
            this.product = product;
            this.category = category;
        }

        private void add(InventoryItem item) {
            if (isCentralLocation(item.getLocationName())) {
                centralQuantity += item.getQuantity();
            } else {
                warehouseQuantity += item.getQuantity();
                warehouseItems.put(item.getLocationName(), item);
            }
            locationCount++;
        }

        private InventoryProductResponse toResponse(List<String> warehouseNames) {
            List<WarehouseStockResponse> warehouses = warehouseNames.stream()
                    .map(locationName -> {
                        InventoryItem item = warehouseItems.get(locationName);
                        return new WarehouseStockResponse(item == null ? null : item.getId(), locationName,
                                item == null ? 0 : item.getQuantity(),
                                item == null ? 0 : item.getOrderedFromCentral());
                    }).toList();
            return new InventoryProductResponse(product.getId(), product.getName(), product.getSku(), product.getUnit(),
                    product.getDescription(), product.getImageUrl(), category.path(), category.depth(), centralQuantity,
                    warehouseQuantity, locationCount, warehouses);
        }

        private final Map<String, InventoryItem> warehouseItems = new LinkedHashMap<>();
    }

    private record CategoryDetails(String path, int depth) { }
}