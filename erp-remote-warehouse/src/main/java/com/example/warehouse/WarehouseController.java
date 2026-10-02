package com.example.warehouse;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1")
public class WarehouseController {
    private static final List<Warehouse> WAREHOUSES = List.of(
            new Warehouse("WH-PRAHA", "Praha - centrální sklad", "8599999000006"),
            new Warehouse("WH-BRNO", "Brno - distribuční sklad", "8599999000013"));

    private static final List<Category> CATEGORIES = List.of(
            new Category("fresh-food", "Čerstvé potraviny", "fresh-food", null),
            new Category("meat", "Maso", "meat", "fresh-food"),
            new Category("dairy", "Mléčné výrobky", "dairy", "fresh-food"),
            new Category("bakery", "Pečivo", "bakery", "fresh-food"),
            new Category("drinks", "Nápoje", "drinks", null));

    private static final List<Product> PRODUCTS = List.of(
            new Product("PORK-NECK-01", "08590000000015", "Vepřová krkovice bez kosti", "meat", "kg",
                    "Čerstvé chlazené maso.", "CZK", decimal("129.90"), decimal("28.00"), decimal("12.00"),
                    decimal("180.42"), decimal("202.07")),
            new Product("MILK-1L", "08590000000022", "Mléko polotučné 1 l", "dairy", "ks",
                    "Trvanlivé mléko, obsah tuku 1,5 %.", "CZK", decimal("14.20"), decimal("22.00"), decimal("12.00"),
                    decimal("18.21"), decimal("20.40")),
            new Product("BREAD-750", "08590000000039", "Chléb konzumní 750 g", "bakery", "ks",
                    "Pšenično-žitný chléb.", "CZK", decimal("28.00"), decimal("30.00"), decimal("12.00"),
                    decimal("40.00"), decimal("44.80")),
            new Product("WATER-1.5L", "08590000000046", "Pramenitá voda 1,5 l", "drinks", "ks",
                    "Neperlivá pramenitá voda.", "CZK", decimal("8.50"), decimal("35.00"), decimal("21.00"),
                    decimal("13.08"), decimal("15.83")));

    private static final List<Availability> AVAILABILITY = List.of(
            new Availability("PORK-NECK-01", "WH-PRAHA", 82, 12),
            new Availability("PORK-NECK-01", "WH-BRNO", 36, 4),
            new Availability("MILK-1L", "WH-PRAHA", 480, 32),
            new Availability("MILK-1L", "WH-BRNO", 215, 20),
            new Availability("BREAD-750", "WH-PRAHA", 64, 8),
            new Availability("BREAD-750", "WH-BRNO", 0, 0),
            new Availability("WATER-1.5L", "WH-PRAHA", 720, 48),
            new Availability("WATER-1.5L", "WH-BRNO", 350, 30));

    @GetMapping("/health")
    public Health health() {
        return new Health("UP", "mock-remote-warehouse", "v1");
    }

    @GetMapping("/warehouses")
    public List<Warehouse> warehouses() {
        return WAREHOUSES;
    }

    @GetMapping("/categories")
    public List<Category> categories() {
        return CATEGORIES;
    }

    @GetMapping("/products")
    public List<Product> products(@RequestParam(required = false) String categoryId,
            @RequestParam(required = false) String q) {
        return PRODUCTS.stream()
                .filter(product -> categoryId == null || categoryId.isBlank() || product.categoryId().equals(categoryId))
                .filter(product -> q == null || q.isBlank() || contains(product, q))
                .toList();
    }

    @GetMapping("/products/{sku}")
    public Product product(@PathVariable String sku) {
        return findProduct(sku);
    }

    @GetMapping("/availability")
    public List<AvailabilityResponse> availability(@RequestParam(required = false) String warehouseCode,
            @RequestParam(required = false) String sku,
            @RequestParam(defaultValue = "false") boolean availableOnly) {
        return AVAILABILITY.stream()
                .filter(stock -> warehouseCode == null || warehouseCode.isBlank() || stock.warehouseCode().equalsIgnoreCase(warehouseCode))
                .filter(stock -> sku == null || sku.isBlank() || stock.sku().equalsIgnoreCase(sku))
                .filter(stock -> !availableOnly || stock.onHand() > stock.reserved())
                .map(this::toResponse)
                .toList();
    }

    @GetMapping("/products/{sku}/availability")
    public List<AvailabilityResponse> productAvailability(@PathVariable String sku) {
        findProduct(sku);
        return AVAILABILITY.stream()
                .filter(stock -> stock.sku().equalsIgnoreCase(sku))
                .map(this::toResponse)
                .toList();
    }

    private Product findProduct(String sku) {
        return PRODUCTS.stream()
                .filter(product -> product.sku().equalsIgnoreCase(sku))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product was not found."));
    }

    private AvailabilityResponse toResponse(Availability stock) {
        Warehouse warehouse = WAREHOUSES.stream()
                .filter(item -> item.code().equals(stock.warehouseCode()))
                .findFirst()
                .orElseThrow();
        Product product = findProduct(stock.sku());
        int availableQuantity = Math.max(0, stock.onHand() - stock.reserved());
        return new AvailabilityResponse(product.sku(), product.gtin(), warehouse.code(), warehouse.name(),
                stock.onHand(), stock.reserved(), availableQuantity, availableQuantity > 0, Instant.now());
    }

    private boolean contains(Product product, String query) {
        String normalized = query.toLowerCase(Locale.ROOT);
        return product.sku().toLowerCase(Locale.ROOT).contains(normalized)
                || product.gtin().contains(query)
                || product.name().toLowerCase(Locale.ROOT).contains(normalized);
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }

    public record Health(String status, String service, String apiVersion) { }

    public record Warehouse(String code, String name, String gln) { }

    public record Category(String id, String name, String slug, String parentId) { }

    public record Product(String sku, String gtin, String name, String categoryId, String unit,
            String description, String currency, BigDecimal purchasePriceNet, BigDecimal marginPercent,
            BigDecimal vatRatePercent, BigDecimal salePriceNet, BigDecimal salePriceGross) { }

    public record AvailabilityResponse(String sku, String gtin, String warehouseCode, String warehouseName,
            int onHand, int reserved, int availableQuantity, boolean available, Instant updatedAt) { }

    private record Availability(String sku, String warehouseCode, int onHand, int reserved) { }
}