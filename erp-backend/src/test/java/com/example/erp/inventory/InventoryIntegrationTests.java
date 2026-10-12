package com.example.erp.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.example.erp.catalog.Product;
import com.example.erp.catalog.ProductRepository;
import com.example.erp.hr.EmployeeRepository;
import com.example.erp.support.AbstractPostgresIntegrationTest;
import com.example.erp.users.ApiAccess;
import com.example.erp.users.ErpUser;
import com.example.erp.users.UserStatus;
import com.example.erp.users.UserRepository;

import tools.jackson.databind.json.JsonMapper;

class InventoryIntegrationTests extends AbstractPostgresIntegrationTest {
    @Autowired private ApiAccess access;
    @Autowired private ProductRepository products;
    @Autowired private InventoryItemRepository inventory;
    @Autowired private EmployeeRepository employees;
    @Autowired private UserRepository users;

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final UUID employeeId = UUID.randomUUID();
    private UUID userId;
    private final UUID roleId = UUID.randomUUID();
    private final String role = "Sklad test " + roleId;
    private String token;
    private UUID productId;
    private UUID itemId;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.update("""
                INSERT INTO role_definitions (id, name, initial, description, can_read, can_edit, can_manage, can_insert, can_delete)
                VALUES (?, ?, 'S', 'Inventory test', TRUE, TRUE, FALSE, TRUE, TRUE)
                """, roleId, role);
        jdbc.update("INSERT INTO role_module_permissions (role_id, module_key) VALUES (?, 'inventory'), (?, 'catalog'), (?, 'purchase')",
                roleId, roleId, roleId);
        jdbc.update("""
                INSERT INTO employees (id, full_name, team_name, team_id, job_title, employment_start_date, status)
                VALUES (?, 'Inventory tester', 'Logistika', 'd4200000-0000-0000-0000-000000000002',
                    ?, CURRENT_DATE, 'ACTIVE')
                """, employeeId, role);
        userId = users.save(ErpUser.create(employees.findById(employeeId).orElseThrow(), "Inventory tester",
                "inventory-" + employeeId, "test-password", "Test", UserStatus.ACTIVE, "#ffffff")).getId();
        token = access.issueToken(userId);
        Product product = products.save(Product.create("TEST-" + UUID.randomUUID().toString().substring(0, 8),
                "Inventory product", "ks", "", new BigDecimal("100.00"), new BigDecimal("80.00"),
                new BigDecimal("21"), BigDecimal.ZERO, null, null, true));
        productId = product.getId();
        InventoryItem item = inventory.save(InventoryItem.create(productId, "Test warehouse"));
        itemId = item.getId();
        assertEquals(200, send("PATCH", "/inventory/items/" + itemId + "/receive", receipt(10)).statusCode());
    }

    @AfterEach
    void cleanUp() {
        if (productId != null) {
            jdbc.update("DELETE FROM inventory_movements WHERE product_id = ?", productId);
            jdbc.update("""
                    DELETE FROM purchase_receipt_lines WHERE receipt_id IN
                        (SELECT receipt.id FROM purchase_receipts receipt JOIN purchase_orders purchase
                         ON purchase.id = receipt.purchase_order_id WHERE purchase.product_id = ?)
                    """, productId);
            jdbc.update("""
                    DELETE FROM purchase_receipts WHERE purchase_order_id IN
                        (SELECT id FROM purchase_orders WHERE product_id = ?)
                    """, productId);
            jdbc.update("DELETE FROM purchase_orders WHERE product_id = ?", productId);
            jdbc.update("DELETE FROM inventory_items WHERE product_id = ?", productId);
            jdbc.update("DELETE FROM products WHERE id = ?", productId);
        }
        jdbc.update("DELETE FROM api_sessions WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM system_users WHERE id = ?", userId);
        jdbc.update("DELETE FROM employees WHERE id = ?", employeeId);
        jdbc.update("DELETE FROM role_module_permissions WHERE role_id = ?", roleId);
        jdbc.update("DELETE FROM role_definitions WHERE id = ?", roleId);
    }

    @Test
    void dispatchAuditAndOverdrawWorkOverHttp() throws Exception {
        HttpResponse<String> issued = send("PATCH", "/inventory/items/" + itemId + "/dispatch", issue(4));
        assertEquals(200, issued.statusCode(), issued.body());
        assertEquals(6, mapper.readTree(issued.body()).path("quantity").asInt());
        HttpResponse<String> rejected = send("PATCH", "/inventory/items/" + itemId + "/dispatch", issue(7));
        assertEquals(409, rejected.statusCode(), rejected.body());
        var history = mapper.readTree(send("GET", "/inventory/movements?itemId=" + itemId, null).body());
        assertEquals(2, history.path("totalElements").asInt());
        var movement = history.path("items").get(0);
        assertEquals("ISSUE", movement.path("type").asText());
        assertEquals(6, movement.path("balanceAfter").asInt());
        assertEquals("Inventory tester", movement.path("actorName").asText());
        assertEquals("TEST-DOC", movement.path("reference").asText());
        assertTrue(movement.path("createdAt").asText().endsWith("Z"));
    }

    @Test
    void concurrentIssuesCannotCreateNegativeStock() throws Exception {
        HttpRequest request = request("PATCH", "/inventory/items/" + itemId + "/dispatch", issue(7));
        var first = client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        var second = client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        List<Integer> statuses = List.of(first.get(15, TimeUnit.SECONDS).statusCode(),
                second.get(15, TimeUnit.SECONDS).statusCode()).stream().sorted().toList();
        assertEquals(List.of(200, 409), statuses);
        assertEquals(3, inventory.findById(itemId).orElseThrow().getQuantity());
        assertEquals(2L, jdbc.queryForObject(
                "SELECT COUNT(*) FROM inventory_movements WHERE inventory_item_id = ?", Long.class, itemId));
    }

    @Test
    void invalidMovementAndOverflowDoNotWrite() throws Exception {
        for (Map<String, Object> body : List.<Map<String, Object>>of(issue(0), issue(-1),
                Map.of("quantity", 1, "reference", " "))) {
            assertEquals(400, send("PATCH", "/inventory/items/" + itemId + "/dispatch", body).statusCode());
        }
        assertEquals(400, send("PATCH", "/inventory/items/" + itemId + "/receive",
                Map.of("quantity", 1, "reorderLevel", 0, "unitCost", "1.001")).statusCode());
        assertEquals(409, send("PATCH", "/inventory/items/" + itemId + "/receive", receipt(Integer.MAX_VALUE)).statusCode());
        assertEquals(10, inventory.findById(itemId).orElseThrow().getQuantity());
        assertEquals(1L, jdbc.queryForObject(
                "SELECT COUNT(*) FROM inventory_movements WHERE inventory_item_id = ?", Long.class, itemId));
        assertEquals(400, send("GET", "/inventory/movements?page=-1", null).statusCode());
        assertEquals(400, send("GET", "/inventory/movements?size=101", null).statusCode());
        assertEquals(404, send("GET", "/inventory/movements?itemId=" + UUID.randomUUID(), null).statusCode());
    }

    @Test
    void historyIsPaginatedAndRetainsProductSnapshot() throws Exception {
        jdbc.update("UPDATE products SET name = 'Renamed product', sku = 'RENAMED-' || id WHERE id = ?", productId);
        var history = mapper.readTree(send("GET", "/inventory/movements?itemId=" + itemId + "&size=1", null).body());
        assertEquals("Inventory product", history.path("items").get(0).path("productName").asText());
        var next = mapper.readTree(send("GET", "/inventory/movements?itemId=" + itemId + "&size=1&page=1", null).body());
        assertEquals(0, next.path("items").size());
        assertEquals(1, next.path("totalElements").asInt());
        assertTrue(jdbc.queryForObject(
                "SELECT COUNT(*) FROM inventory_movements WHERE movement_type = 'OPENING'", Long.class) > 0);
    }

    @Test
    void readOnlyRoleCannotMoveStockAndAnonymousCannotRead() throws Exception {
        jdbc.update("UPDATE role_definitions SET can_edit = FALSE WHERE id = ?", roleId);
        var overview = send("GET", "/inventory/overview", null);
        assertEquals(200, overview.statusCode(), overview.body());
        assertTrue(!mapper.readTree(overview.body()).path("canEdit").asBoolean());
        assertEquals(403, send("PATCH", "/inventory/items/" + itemId + "/dispatch", issue(1)).statusCode());
        assertEquals(403, send("PATCH", "/inventory/items/" + itemId + "/receive", receipt(1)).statusCode());
        HttpResponse<String> anonymous = client.send(HttpRequest.newBuilder(
                URI.create("http://localhost:" + port + "/api/v1/inventory/movements")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(401, anonymous.statusCode(), anonymous.body());
        assertEquals(10, inventory.findById(itemId).orElseThrow().getQuantity());
    }

    @Test
    void productWithHistoryCannotBeDeletedOrHaveItsUnitChanged() throws Exception {
        assertEquals(200, send("PATCH", "/inventory/items/" + itemId + "/dispatch", issue(10)).statusCode());
        HttpResponse<String> deletion = send("DELETE", "/catalog/products/" + productId, null);
        assertEquals(409, deletion.statusCode(), deletion.body());
        assertTrue(products.existsById(productId));
        var update = Map.of("sku", "NEW-SKU", "name", "Product", "unit", "kg", "price", "100.00",
                "purchasePrice", "80.00", "vatRate", "21", "eshopMarginPercent", "0", "active", true);
        HttpResponse<String> changed = send("PUT", "/catalog/products/" + productId, update);
        assertEquals(409, changed.statusCode(), changed.body());
        assertEquals("ks", products.findById(productId).orElseThrow().getUnit());
    }

    @Test
    void purchaseReceiptAlsoRecordsMovementInSameTransaction() throws Exception {
        UUID purchaseId = UUID.randomUUID();
        String purchaseNumber = "INV-TEST-" + purchaseId.toString().substring(0, 8);
        UUID destination = UUID.fromString("b0000000-0000-0000-0000-000000000002");
        jdbc.update("""
                INSERT INTO purchase_orders (id, order_number, supplier_name, requested_on, expected_delivery_date,
                    total_amount, status, destination_warehouse_id, product_id, quantity, received_quantity)
                VALUES (?, ?, 'Test supplier', ?, ?, 100, 'ORDERED', ?, ?, 5, 0)
                """, purchaseId, purchaseNumber, LocalDate.now(), LocalDate.now(), destination, productId);
        HttpResponse<String> received = send("PATCH", "/purchase/orders/" + purchaseId + "/receive", Map.of("quantity", 3));
        assertEquals(200, received.statusCode(), received.body());
        assertEquals(3, inventory.findByProductIdAndWarehouseId(productId, destination).orElseThrow().getQuantity());
        assertEquals(new BigDecimal("20.00"),
                inventory.findByProductIdAndWarehouseId(productId, destination).orElseThrow().getUnitCost());
        assertEquals(1L, jdbc.queryForObject(
                "SELECT COUNT(*) FROM inventory_movements WHERE reference = ?", Long.class, purchaseNumber));
        assertEquals(409, send("PATCH", "/purchase/orders/" + purchaseId + "/receive", Map.of("quantity", 3)).statusCode());
        assertEquals(3, inventory.findByProductIdAndWarehouseId(productId, destination).orElseThrow().getQuantity());
    }

    @Test
    void partialReceiptsValueStockPreserveHistoryAndDoNotRepeat() throws Exception {
        UUID purchaseId = purchaseOrder(7, new BigDecimal("170.00"));
        UUID first = purchaseLine(purchaseId, 4, new BigDecimal("20.00"));
        UUID second = purchaseLine(purchaseId, 3, new BigDecimal("30.00"));
        UUID destination = UUID.fromString("b0000000-0000-0000-0000-000000000002");
        InventoryItem existing = InventoryItem.create(productId, destination, "Praha Vinohrady");
        existing.receivePurchase(2, new BigDecimal("10.00"));
        inventory.saveAndFlush(existing);

        UUID receiptId = UUID.randomUUID();
        var partial = Map.of("receiptId", receiptId, "lines", List.of(
                Map.of("lineId", first, "quantity", 2), Map.of("lineId", second, "quantity", 1)));
        BigDecimal orderedBefore = new BigDecimal(mapper.readTree(send("GET", "/purchase/overview", null).body())
                .path("orderedValue").asText());
        var result = send("PATCH", "/purchase/orders/" + purchaseId + "/receive", partial);
        assertEquals(200, result.statusCode(), result.body());
        var response = mapper.readTree(result.body());
        assertEquals("ORDERED", response.path("status").asText());
        assertEquals(3, response.path("receivedQuantity").asInt());
        assertEquals(1, response.path("receipts").size());
        assertEquals(5, inventory.findById(existing.getId()).orElseThrow().getQuantity());
        assertEquals(new BigDecimal("18.00"), inventory.findById(existing.getId()).orElseThrow().getUnitCost());
        assertEquals(0, orderedBefore.subtract(new BigDecimal("70.00")).compareTo(
                new BigDecimal(mapper.readTree(send("GET", "/purchase/overview", null).body())
                        .path("orderedValue").asText())));

        var reordered = Map.of("receiptId", receiptId, "lines", List.of(
                Map.of("lineId", second, "quantity", 1), Map.of("lineId", first, "quantity", 2)));
        assertEquals(200, send("PATCH", "/purchase/orders/" + purchaseId + "/receive", reordered).statusCode());
        assertEquals(5, inventory.findById(existing.getId()).orElseThrow().getQuantity());
        assertEquals(409, send("PATCH", "/purchase/orders/" + purchaseId + "/order", null).statusCode());
        var changed = Map.of("receiptId", receiptId, "lines", List.of(Map.of("lineId", first, "quantity", 1)));
        assertEquals(409, send("PATCH", "/purchase/orders/" + purchaseId + "/receive", changed).statusCode());

        var finalReceipt = Map.of("receiptId", UUID.randomUUID(), "lines", List.of(
                Map.of("lineId", first, "quantity", 2), Map.of("lineId", second, "quantity", 2)));
        result = send("PATCH", "/purchase/orders/" + purchaseId + "/receive", finalReceipt);
        assertEquals(200, result.statusCode(), result.body());
        assertEquals("RECEIVED", mapper.readTree(result.body()).path("status").asText());
        assertEquals(409, send("PATCH", "/purchase/orders/" + purchaseId + "/order", null).statusCode());
        assertEquals(9, inventory.findById(existing.getId()).orElseThrow().getQuantity());
        assertEquals(new BigDecimal("21.11"), inventory.findById(existing.getId()).orElseThrow().getUnitCost());
        assertEquals(200, send("PATCH", "/purchase/orders/" + purchaseId + "/receive", finalReceipt).statusCode());
        assertEquals(2L, jdbc.queryForObject(
                "SELECT COUNT(*) FROM purchase_receipts WHERE purchase_order_id = ?", Long.class, purchaseId));
    }

    @Test
    void receiptRoundsCombinedProductValueOnlyOnce() throws Exception {
        UUID purchaseId = purchaseOrder(2, new BigDecimal("0.01"));
        UUID first = purchaseLine(purchaseId, 1, BigDecimal.ZERO);
        UUID second = purchaseLine(purchaseId, 1, new BigDecimal("0.01"));
        UUID destination = UUID.fromString("b0000000-0000-0000-0000-000000000002");
        InventoryItem existing = InventoryItem.create(productId, destination, "Praha Vinohrady");
        existing.receivePurchase(1, BigDecimal.ZERO);
        inventory.saveAndFlush(existing);

        var receipt = Map.of("receiptId", UUID.randomUUID(), "lines", List.of(
                Map.of("lineId", first, "quantity", 1), Map.of("lineId", second, "quantity", 1)));
        var response = send("PATCH", "/purchase/orders/" + purchaseId + "/receive", receipt);
        assertEquals(200, response.statusCode(), response.body());
        assertEquals(3, inventory.findById(existing.getId()).orElseThrow().getQuantity());
        assertEquals(new BigDecimal("0.00"), inventory.findById(existing.getId()).orElseThrow().getUnitCost());
        assertEquals(1L, jdbc.queryForObject(
                "SELECT COUNT(*) FROM inventory_movements WHERE inventory_item_id = ?", Long.class, existing.getId()));
        assertEquals(new BigDecimal("0.01"), jdbc.queryForObject("""
                SELECT SUM(line.unit_price * line.quantity) FROM purchase_receipt_lines line
                JOIN purchase_receipts receipt ON receipt.id = line.receipt_id WHERE receipt.purchase_order_id = ?
                """, BigDecimal.class, purchaseId));
    }

    @Test
    void invalidRowsAndFailedStockChangeRollBackEntireReceipt() throws Exception {
        UUID purchaseId = purchaseOrder(4, new BigDecimal("80.00"));
        UUID first = purchaseLine(purchaseId, 2, new BigDecimal("20.00"));
        UUID second = purchaseLine(purchaseId, 2, new BigDecimal("20.00"));
        String endpoint = "/purchase/orders/" + purchaseId + "/receive";
        for (var invalid : List.of(
                List.of(Map.of("lineId", first, "quantity", 1), Map.of("lineId", second, "quantity", 3)),
                List.of(Map.of("lineId", first, "quantity", 1), Map.of("lineId", UUID.randomUUID(), "quantity", 1)),
                List.of(Map.of("lineId", first, "quantity", 1), Map.of("lineId", first, "quantity", 1)),
                List.of(Map.of("lineId", first, "quantity", 0)))) {
            int status = send("PATCH", endpoint, Map.of("receiptId", UUID.randomUUID(), "lines", invalid)).statusCode();
            assertTrue(status == 400 || status == 409);
        }
        assertEquals(400, send("PATCH", endpoint, Map.of("quantity", 1)).statusCode());
        UUID destination = UUID.fromString("b0000000-0000-0000-0000-000000000002");
        InventoryItem existing = InventoryItem.create(productId, destination, "Praha Vinohrady");
        existing.receivePurchase(Integer.MAX_VALUE - 1, BigDecimal.ONE);
        inventory.saveAndFlush(existing);
        var receipt = Map.of("receiptId", UUID.randomUUID(), "lines", List.of(
                Map.of("lineId", first, "quantity", 1), Map.of("lineId", second, "quantity", 1)));
        assertEquals(409, send("PATCH", endpoint, receipt).statusCode());
        assertEquals(Integer.MAX_VALUE - 1, inventory.findById(existing.getId()).orElseThrow().getQuantity());
        assertEquals(0L, jdbc.queryForObject(
                "SELECT COUNT(*) FROM purchase_receipts WHERE purchase_order_id = ?", Long.class, purchaseId));
        assertEquals(0, jdbc.queryForObject(
                "SELECT received_quantity FROM purchase_orders WHERE id = ?", Integer.class, purchaseId));
        assertEquals(0L, jdbc.queryForObject(
                "SELECT SUM(received_quantity) FROM purchase_order_lines WHERE purchase_order_id = ?", Long.class, purchaseId));
    }

    @Test
    void concurrentRetryReceivesStockOnlyOnce() throws Exception {
        UUID purchaseId = purchaseOrder(4, new BigDecimal("80.00"));
        UUID lineId = purchaseLine(purchaseId, 4, new BigDecimal("20.00"));
        var receipt = Map.of("receiptId", UUID.randomUUID(),
                "lines", List.of(Map.of("lineId", lineId, "quantity", 2)));
        HttpRequest request = request("PATCH", "/purchase/orders/" + purchaseId + "/receive", receipt);
        var first = client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        var second = client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, first.get(30, TimeUnit.SECONDS).statusCode());
        assertEquals(200, second.get(30, TimeUnit.SECONDS).statusCode());
        assertEquals(2, jdbc.queryForObject(
                "SELECT received_quantity FROM purchase_orders WHERE id = ?", Integer.class, purchaseId));
        assertEquals(1L, jdbc.queryForObject(
                "SELECT COUNT(*) FROM purchase_receipts WHERE purchase_order_id = ?", Long.class, purchaseId));
    }

    @Test
    void concurrentReceiptsCannotExceedOrderedQuantity() throws Exception {
        UUID purchaseId = purchaseOrder(4, new BigDecimal("80.00"));
        UUID lineId = purchaseLine(purchaseId, 4, new BigDecimal("20.00"));
        String endpoint = "/purchase/orders/" + purchaseId + "/receive";
        var first = client.sendAsync(request("PATCH", endpoint, Map.of("receiptId", UUID.randomUUID(),
                "lines", List.of(Map.of("lineId", lineId, "quantity", 3)))), HttpResponse.BodyHandlers.ofString());
        var second = client.sendAsync(request("PATCH", endpoint, Map.of("receiptId", UUID.randomUUID(),
                "lines", List.of(Map.of("lineId", lineId, "quantity", 3)))), HttpResponse.BodyHandlers.ofString());
        List<Integer> statuses = List.of(first.get(30, TimeUnit.SECONDS).statusCode(),
                second.get(30, TimeUnit.SECONDS).statusCode()).stream().sorted().toList();
        assertEquals(List.of(200, 409), statuses);
        UUID destination = UUID.fromString("b0000000-0000-0000-0000-000000000002");
        assertEquals(3, inventory.findByProductIdAndWarehouseId(productId, destination).orElseThrow().getQuantity());
        assertEquals(3, jdbc.queryForObject(
                "SELECT received_quantity FROM purchase_orders WHERE id = ?", Integer.class, purchaseId));
        assertEquals(3, jdbc.queryForObject(
                "SELECT received_quantity FROM purchase_order_lines WHERE id = ?", Integer.class, lineId));
        assertEquals(1L, jdbc.queryForObject(
                "SELECT COUNT(*) FROM purchase_receipts WHERE purchase_order_id = ?", Long.class, purchaseId));
    }

    private UUID purchaseOrder(int quantity, BigDecimal total) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO purchase_orders (id, order_number, supplier_name, requested_on, expected_delivery_date,
                    total_amount, status, destination_warehouse_id, product_id, quantity, received_quantity)
                VALUES (?, ?, 'Test supplier', CURRENT_DATE, CURRENT_DATE, ?, 'ORDERED',
                    'b0000000-0000-0000-0000-000000000002', ?, ?, 0)
                """, id, "PART-" + id.toString().substring(0, 8), total, productId, quantity);
        return id;
    }

    private UUID purchaseLine(UUID purchaseId, int quantity, BigDecimal price) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO purchase_order_lines (id, purchase_order_id, product_id, quantity, unit_price)
                VALUES (?, ?, ?, ?, ?)
                """, id, purchaseId, productId, quantity, price);
        return id;
    }

    private Map<String, Object> receipt(int quantity) {
        return Map.of("quantity", quantity, "reorderLevel", 2, "unitCost", "12.50", "reference", "TEST-RECEIPT");
    }

    private Map<String, Object> issue(int quantity) {
        return Map.of("quantity", quantity, "reference", "TEST-DOC", "note", "Test issue");
    }

    private HttpRequest request(String method, String path, Object body) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1" + path))
                .header("Authorization", "Bearer " + token).header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build();
    }

    private HttpResponse<String> send(String method, String path, Object body) throws Exception {
        return client.send(request(method, path, body), HttpResponse.BodyHandlers.ofString());
    }
}
