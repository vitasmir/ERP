package com.example.erp.catalog;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.erp.inventory.InventoryItem;
import com.example.erp.inventory.InventoryItemRepository;

@RestController
@RequestMapping("/api/v1/catalog")
public class CatalogController {
    private final ProductRepository products;
    private final ProductCategoryRepository categories;
    private final HomepageSettingsRepository homepage;
    private final InventoryItemRepository inventory;
    private final DeliveryOptionRepository deliveryOptions;

    public CatalogController(ProductRepository products, ProductCategoryRepository categories,
            HomepageSettingsRepository homepage, InventoryItemRepository inventory,
            DeliveryOptionRepository deliveryOptions) {
        this.products = products;
        this.categories = categories;
        this.homepage = homepage;
        this.inventory = inventory;
        this.deliveryOptions = deliveryOptions;
    }

    @GetMapping("/homepage")
    public HomepageResponse getHomepage() {
        return homepage.findAll().stream().findFirst().map(HomepageResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Homepage settings were not found."));
    }

    @PutMapping("/homepage")
    public HomepageResponse updateHomepage(@RequestBody HomepageRequest request) {
        validateHomepage(request);
        HomepageSettings settings = homepage.findAll().stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Homepage settings were not found."));
        settings.update(request.design().trim().toUpperCase(Locale.ROOT), request.headline().trim(),
                request.subheadline().trim(), request.textX(), request.textY());
        return HomepageResponse.from(homepage.save(settings));
    }

    @GetMapping("/categories/tree")
    public List<CategoryResponse> categoryTree() {
        List<ProductCategory> all = categories.findAllByOrderBySortOrderAscNameAsc();
        Map<UUID, List<ProductCategory>> children = all.stream().filter(category -> category.getParentId() != null)
                .collect(Collectors.groupingBy(ProductCategory::getParentId));
        return all.stream().filter(category -> category.getParentId() == null)
                .map(category -> toCategoryResponse(category, children)).toList();
    }

    @PostMapping("/categories")
    public CategoryResponse createCategory(@RequestBody CategoryRequest request) {
        validateCategory(request, null);
        return toCategoryResponse(categories.save(ProductCategory.create(request.name().trim(), request.slug().trim(),
                request.parentId(), request.sortOrder(), request.active())), Map.of());
    }

    @PutMapping("/categories/{id}")
    public CategoryResponse updateCategory(@PathVariable UUID id, @RequestBody CategoryRequest request) {
        ProductCategory category = findCategory(id);
        validateCategory(request, id);
        category.update(request.name().trim(), request.slug().trim(), request.parentId(), request.sortOrder(), request.active());
        return toCategoryResponse(categories.save(category), Map.of());
    }

    @DeleteMapping("/categories/{id}")
    @Transactional
    public void deleteCategory(@PathVariable UUID id) {
        if (categories.existsByParentId(id) || products.countByCategoryId(id) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Category still contains child categories or products.");
        }
        categories.delete(findCategory(id));
    }

    @GetMapping("/products")
    public List<ProductResponse> listProducts() {
        return products.findAllByOrderByNameAsc().stream().map(ProductResponse::from).toList();
    }

    @PostMapping("/products")
    public ProductResponse createProduct(@RequestBody ProductRequest request) {
        validateProduct(request, null);
        return ProductResponse.from(products.save(Product.create(request.sku().trim(), request.name().trim(),
                request.unit().trim(), text(request.description()), request.price(), request.categoryId(),
                textOrNull(request.imageUrl()), request.active())));
    }

    @PutMapping("/products/{id}")
    public ProductResponse updateProduct(@PathVariable UUID id, @RequestBody ProductRequest request) {
        Product product = products.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product was not found."));
        validateProduct(request, id);
        product.update(request.sku().trim(), request.name().trim(), request.unit().trim(), text(request.description()),
                request.price(), request.categoryId(), textOrNull(request.imageUrl()), request.active());
        return ProductResponse.from(products.save(product));
    }

    @PostMapping("/products/import")
    @Transactional
    public ImportResponse importProducts(@RequestBody List<ProductRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Import must contain at least one product.");
        }
        int imported = 0;
        for (ProductRequest request : requests) {
            Product product = products.findBySku(request.sku() == null ? "" : request.sku().trim()).orElse(null);
            validateProduct(request, product == null ? null : product.getId());
            if (product == null) {
                product = Product.create(request.sku().trim(), request.name().trim(), request.unit().trim(),
                        text(request.description()), request.price(), request.categoryId(), textOrNull(request.imageUrl()), request.active());
            } else {
                product.update(request.sku().trim(), request.name().trim(), request.unit().trim(), text(request.description()),
                        request.price(), request.categoryId(), textOrNull(request.imageUrl()), request.active());
            }
            products.save(product);
            imported++;
        }
        return new ImportResponse(imported);
    }

    @GetMapping("/products/{id}/availability")
    public List<AvailabilityResponse> availability(@PathVariable UUID id) {
        Product product = products.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product was not found."));
        return inventory.findAllByProductIdOrderByQuantityDesc(product.getId()).stream()
                .map(item -> AvailabilityResponse.from(item, product.getName())).toList();
    }

    @PostMapping("/delivery-estimates")
    public DeliveryEstimateResponse deliveryEstimate(@RequestBody DeliveryEstimateRequest request) {
        if (request == null || request.productId() == null || request.quantity() <= 0
                || request.postalCode() == null || !request.postalCode().trim().matches("\\d{5}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Product, positive quantity, and five-digit postal code are required.");
        }
        String method = request.method() == null ? "HOME" : request.method().trim().toUpperCase(Locale.ROOT);
        DeliveryOption option = deliveryOptions.findByCodeAndEnabledTrue(method)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown delivery method."));
        Product product = products.findById(request.productId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product was not found."));
        int stock = inventory.findAllByProductIdOrderByQuantityDesc(product.getId()).stream()
                .mapToInt(InventoryItem::getQuantity).sum();
        if (stock < request.quantity()) {
            return new DeliveryEstimateResponse(method, option.getLabel(), false, null, "Nedostatečná zásoba pro objednávku.");
        }
        if ("BOX".equals(method) && request.postalCode().startsWith("000")) {
            return new DeliveryEstimateResponse(method, option.getLabel(), false, null, "Pro zadané PSČ není dostupný výdejní box.");
        }
        return new DeliveryEstimateResponse(method, option.getLabel(), true,
                LocalDate.now().plusDays(option.getPreparationDays()), "Dostupné z aktuální zásoby.");
    }

    private CategoryResponse toCategoryResponse(ProductCategory category, Map<UUID, List<ProductCategory>> children) {
        List<CategoryResponse> nested = children.getOrDefault(category.getId(), List.of()).stream()
                .map(child -> toCategoryResponse(child, children)).toList();
        return new CategoryResponse(category.getId(), category.getParentId(), category.getName(), category.getSlug(),
                category.getSortOrder(), category.isActive(), nested);
    }

    private ProductCategory findCategory(UUID id) {
        return categories.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category was not found."));
    }

    private void validateHomepage(HomepageRequest request) {
        if (request == null || blank(request.design()) || blank(request.headline()) || blank(request.subheadline())
                || request.textX() == null || request.textY() == null || !inside(request.textX()) || !inside(request.textY())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Homepage text and position between 0 and 100 are required.");
        }
    }

    private void validateCategory(CategoryRequest request, UUID currentId) {
        if (request == null || blank(request.name()) || blank(request.slug())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Category name and slug are required.");
        }
        categories.findBySlug(request.slug().trim()).filter(category -> !category.getId().equals(currentId)).ifPresent(category -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Category slug is already in use.");
        });
        if (currentId != null && currentId.equals(request.parentId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Category cannot be its own parent.");
        }
        if (request.parentId() != null && !categories.existsById(request.parentId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Parent category was not found.");
        }
    }

    private void validateProduct(ProductRequest request, UUID currentId) {
        if (request == null || blank(request.sku()) || blank(request.name()) || blank(request.unit())
                || request.price() == null || request.price().signum() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Product SKU, name, unit, and non-negative price are required.");
        }
        products.findBySku(request.sku().trim()).filter(product -> !product.getId().equals(currentId)).ifPresent(product -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Product SKU is already in use.");
        });
        if (request.categoryId() != null && !categories.existsById(request.categoryId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Category was not found.");
        }
    }

    private boolean inside(BigDecimal value) { return value.compareTo(BigDecimal.ZERO) >= 0 && value.compareTo(BigDecimal.valueOf(100)) <= 0; }
    private boolean blank(String value) { return value == null || value.isBlank(); }
    private String text(String value) { return value == null ? "" : value.trim(); }
    private String textOrNull(String value) { return blank(value) ? null : value.trim(); }

    public record HomepageRequest(String design, String headline, String subheadline, BigDecimal textX, BigDecimal textY) { }
    public record HomepageResponse(UUID id, String design, String headline, String subheadline, BigDecimal textX, BigDecimal textY) {
        static HomepageResponse from(HomepageSettings settings) {
            return new HomepageResponse(settings.getId(), settings.getDesign(), settings.getHeadline(), settings.getSubheadline(), settings.getTextX(), settings.getTextY());
        }
    }
    public record CategoryRequest(String name, String slug, UUID parentId, int sortOrder, boolean active) { }
    public record CategoryResponse(UUID id, UUID parentId, String name, String slug, int sortOrder, boolean active, List<CategoryResponse> children) { }
    public record ProductRequest(String sku, String name, String unit, String description, BigDecimal price, UUID categoryId, String imageUrl, boolean active) { }
    public record ProductResponse(UUID id, String sku, String name, String unit, String description, BigDecimal price, UUID categoryId, String imageUrl, boolean active) {
        static ProductResponse from(Product product) {
            return new ProductResponse(product.getId(), product.getSku(), product.getName(), product.getUnit(), product.getDescription(), product.getPrice(), product.getCategoryId(), product.getImageUrl(), product.isActive());
        }
    }
    public record ImportResponse(int imported) { }
    public record AvailabilityResponse(String productName, String locationName, int quantity, boolean available, String stockStatus) {
        static AvailabilityResponse from(InventoryItem item, String productName) {
            String status = item.getQuantity() == 0 ? "OUT_OF_STOCK" : item.getQuantity() < item.getReorderLevel() ? "LOW" : "AVAILABLE";
            return new AvailabilityResponse(productName, item.getLocationName(), item.getQuantity(), item.getQuantity() > 0, status);
        }
    }
    public record DeliveryEstimateRequest(UUID productId, int quantity, String postalCode, String method) { }
    public record DeliveryEstimateResponse(String method, String label, boolean available, LocalDate estimatedDate, String reason) { }
}