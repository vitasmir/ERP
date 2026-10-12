package com.example.erp.purchase;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.TreeMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.erp.catalog.ProductRepository;
import com.example.erp.inventory.InventoryStockService;
import com.example.erp.inventory.Warehouse;
import com.example.erp.inventory.WarehouseOwnerType;
import com.example.erp.inventory.WarehouseRepository;
import com.example.erp.users.ErpUser;

@RestController
@RequestMapping("/api/v1/purchase")
public class PurchaseController {
    private final PurchaseOrderRepository orders;
        private final PurchaseOrderLineRepository lines;
        private final WarehouseRepository warehouses;
        private final SupplierRepository suppliers;
        private final ProductRepository products;
        private final InventoryStockService inventory;
        private final PurchaseReceipts receipts;

        public PurchaseController(PurchaseOrderRepository orders, PurchaseOrderLineRepository lines, WarehouseRepository warehouses,
                        SupplierRepository suppliers, ProductRepository products, InventoryStockService inventory,
                        PurchaseReceipts receipts) {
                this.orders = orders;
                this.lines = lines;
                this.warehouses = warehouses;
                this.suppliers = suppliers;
                this.products = products;
                this.inventory = inventory;
                this.receipts = receipts;
        }

        @GetMapping("/warehouses")
        public List<WarehouseResponse> warehouses() {
                return warehouses.findAllByActiveTrueOrderByOwnerTypeAscNameAsc().stream()
                                .map(warehouse -> new WarehouseResponse(warehouse.getId(), warehouse.getName(),
                                                warehouse.getOwnerType(), warehouse.getSupplierId()))
                                .toList();
        }

        @GetMapping("/suppliers")
        public List<SupplierResponse> suppliers() {
                return suppliers.findAllByOrderByNameAsc().stream()
                                .map(supplier -> new SupplierResponse(supplier.getId(), supplier.getName()))
                                .toList();
        }

    @GetMapping("/overview")
    public PurchaseOverview overview() {
        List<PurchaseOrderResponse> items = orders.findAllByOrderByExpectedDeliveryDateAsc().stream()
                .map(this::response).toList();
        BigDecimal requestedValue = items.stream().filter(item -> item.status() == PurchaseOrderStatus.REQUESTED)
                .map(PurchaseOrderResponse::totalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal orderedValue = items.stream().filter(item -> item.status() == PurchaseOrderStatus.ORDERED)
                .map(item -> item.lines().isEmpty() ? item.totalAmount() : item.lines().stream()
                        .map(line -> line.unitPrice().multiply(BigDecimal.valueOf(line.quantity() - line.receivedQuantity())))
                        .reduce(BigDecimal.ZERO, BigDecimal::add))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new PurchaseOverview(requestedValue, orderedValue,
                items.stream().filter(item -> item.status() == PurchaseOrderStatus.REQUESTED).count(), items);
    }

        @PostMapping("/orders")
        @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.CREATED)
        @org.springframework.transaction.annotation.Transactional
        public PurchaseOrderResponse create(@RequestBody CreatePurchaseOrderRequest request) {
                if (request == null || request.supplierId() == null
                                || request.requestedOn() == null || request.expectedDeliveryDate() == null
                                || request.expectedDeliveryDate().isBefore(request.requestedOn()) || request.sourceWarehouseId() == null
                                                || request.destinationWarehouseId() == null || request.lines() == null
                                                || request.lines().isEmpty()) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Purchase order contains invalid values.");
                }
                List<PurchaseOrderLineRequest> requestedLines = request.lines().stream()
                                .filter(line -> line != null && line.productId() != null && line.quantity() > 0
                                                && validPrice(line.unitPrice()))
                                .toList();
                if (requestedLines.size() != request.lines().size()) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Purchase order contains invalid product lines.");
                }
                Supplier supplier = supplier(request.supplierId());
                Warehouse source = supplierWarehouse(request.sourceWarehouseId(), supplier.getId());
                Warehouse destination = warehouse(request.destinationWarehouseId(), WarehouseOwnerType.COMPANY);
                validateProducts(requestedLines);
                BigDecimal totalAmount = requestedLines.stream()
                                .map(line -> line.unitPrice().multiply(BigDecimal.valueOf(line.quantity())))
                                .reduce(BigDecimal.ZERO, BigDecimal::add);
                validateTotal(totalAmount);
                PurchaseOrderLineRequest firstLine = requestedLines.get(0);
                int totalQuantity = totalQuantity(requestedLines);
                PurchaseOrder purchaseOrder = PurchaseOrder.create("POZ-" + LocalDate.now().getYear() + "-"
                                + UUID.randomUUID().toString().substring(0, 8).toUpperCase(), supplier.getName(),
                                request.requestedOn(), request.expectedDeliveryDate(), totalAmount, source.getId(),
                                destination.getId(), supplier.getId(), firstLine.productId(), totalQuantity);
                PurchaseOrder saved = orders.save(purchaseOrder);
                lines.saveAll(requestedLines.stream()
                                .map(line -> PurchaseOrderLine.create(saved.getId(), line.productId(), line.quantity(), line.unitPrice()))
                                .toList());
                return response(saved);
        }

        @org.springframework.web.bind.annotation.PutMapping("/orders/{id}")
        @org.springframework.transaction.annotation.Transactional
        public PurchaseOrderResponse update(@PathVariable UUID id, @RequestBody CreatePurchaseOrderRequest request) {
                if (request == null || request.supplierId() == null
                                || request.requestedOn() == null || request.expectedDeliveryDate() == null
                                || request.expectedDeliveryDate().isBefore(request.requestedOn())
                                || request.sourceWarehouseId() == null || request.destinationWarehouseId() == null
                                || request.lines() == null || request.lines().isEmpty()) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Purchase order contains invalid values.");
                }
                List<PurchaseOrderLineRequest> requestedLines = request.lines().stream()
                                .filter(line -> line != null && line.productId() != null && line.quantity() > 0
                                                && validPrice(line.unitPrice()))
                                .toList();
                if (requestedLines.size() != request.lines().size()) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Purchase order contains invalid product lines.");
                }
                Supplier supplier = supplier(request.supplierId());
                Warehouse source = supplierWarehouse(request.sourceWarehouseId(), supplier.getId());
                Warehouse destination = warehouse(request.destinationWarehouseId(), WarehouseOwnerType.COMPANY);
                validateProducts(requestedLines);
                BigDecimal totalAmount = requestedLines.stream()
                                .map(line -> line.unitPrice().multiply(BigDecimal.valueOf(line.quantity())))
                                .reduce(BigDecimal.ZERO, BigDecimal::add);
                validateTotal(totalAmount);
                int totalQuantity = totalQuantity(requestedLines);
                PurchaseOrder order = orders.findForUpdate(id)
                                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Purchase order was not found."));
                if (order.getStatus() != PurchaseOrderStatus.REQUESTED) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "Upravit lze pouze nákupní požadavek.");
                }
                order.updateRequested(supplier.getName(), supplier.getId(), request.requestedOn(),
                                request.expectedDeliveryDate(), totalAmount, source.getId(), destination.getId(),
                                requestedLines.get(0).productId(), totalQuantity);
                lines.deleteAllByPurchaseOrderId(order.getId());
                lines.saveAll(requestedLines.stream()
                                .map(line -> PurchaseOrderLine.create(order.getId(), line.productId(), line.quantity(), line.unitPrice()))
                                .toList());
                return response(order);
        }

    @PatchMapping("/orders/{id}/order")
    @org.springframework.transaction.annotation.Transactional
    public PurchaseOrderResponse order(@PathVariable UUID id) {
        PurchaseOrder purchaseOrder = orders.findForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Purchase order was not found."));
        if (purchaseOrder.getStatus() != PurchaseOrderStatus.REQUESTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Vystavit lze pouze nákupní požadavek.");
        }
        purchaseOrder.order();
        return response(orders.save(purchaseOrder));
    }

    @org.springframework.transaction.annotation.Transactional
    @PatchMapping("/orders/{id}/receive")
    public PurchaseOrderResponse receive(@PathVariable UUID id, @RequestBody ReceivePurchaseRequest request,
            @RequestAttribute("erpUser") ErpUser actor) {
        if (request == null || (request.quantity() != null && request.quantity() <= 0)
                || (request.lines() != null && (request.quantity() != null || request.lines().isEmpty()
                    || request.receiptId() == null))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Neplatný požadavek příjmu.");
        }
        String signature = receiptSignature(request);
        PurchaseOrder order = orders.findForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Purchase order was not found."));
        if (request.receiptId() != null && receipts.alreadyReceived(request.receiptId(), id, signature)) {
            return response(order);
        }
        if (order.getStatus() != PurchaseOrderStatus.ORDERED || order.getQuantity() == null
                || order.getProductId() == null || order.getDestinationWarehouseId() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Objednávka není připravena k příjmu.");
        }
        Warehouse destination = warehouse(order.getDestinationWarehouseId(), WarehouseOwnerType.COMPANY);
        List<PurchaseOrderLine> orderLines = lines.findAllByPurchaseOrderIdOrderById(id);
        Map<UUID, PurchaseOrderLine> byId = orderLines.stream()
                .collect(Collectors.toMap(PurchaseOrderLine::getId, Function.identity()));
        List<PurchaseReceipts.ReceiptLine> received = new ArrayList<>();
        if (request.lines() != null) {
            for (ReceivePurchaseLine requested : request.lines()) {
                PurchaseOrderLine line = byId.get(requested.lineId());
                if (line == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Řádek nepatří této objednávce.");
                }
                if (requested.quantity() > line.remainingQuantity()) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Příjem převyšuje zbývající množství řádku.");
                }
                received.add(new PurchaseReceipts.ReceiptLine(line.getId(), line.getProductId(),
                        requested.quantity(), line.getUnitPrice()));
            }
        } else if (orderLines.isEmpty()) {
            if (request.quantity() == null || request.quantity() > order.getQuantity() - order.getReceivedQuantity()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Neplatné množství příjmu.");
            }
            BigDecimal price = order.getTotalAmount().divide(BigDecimal.valueOf(order.getQuantity()),
                    2, RoundingMode.HALF_UP);
            received.add(new PurchaseReceipts.ReceiptLine(null, order.getProductId(), request.quantity(), price));
        } else {
            int remaining = orderLines.stream().mapToInt(PurchaseOrderLine::remainingQuantity).sum();
            if (request.quantity() != null && request.quantity() != remaining) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Částečný příjem musí uvádět množství jednotlivých řádků.");
            }
            for (PurchaseOrderLine line : orderLines) {
                if (line.remainingQuantity() > 0) {
                    received.add(new PurchaseReceipts.ReceiptLine(line.getId(), line.getProductId(),
                            line.remainingQuantity(), line.getUnitPrice()));
                }
            }
        }
        long total = received.stream().mapToLong(PurchaseReceipts.ReceiptLine::quantity).sum();
        if (total <= 0 || total > order.getQuantity() - order.getReceivedQuantity()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Neplatné množství příjmu.");
        }
        UUID receiptId = request.receiptId() == null ? UUID.randomUUID() : request.receiptId();
        receipts.record(receiptId, id, signature, received, actor);
        Map<UUID, List<PurchaseReceipts.ReceiptLine>> byProduct = received.stream()
                .collect(Collectors.groupingBy(PurchaseReceipts.ReceiptLine::productId, TreeMap::new, Collectors.toList()));
        for (var entry : byProduct.entrySet()) {
            int quantity = entry.getValue().stream().mapToInt(PurchaseReceipts.ReceiptLine::quantity).sum();
            BigDecimal value = entry.getValue().stream()
                    .map(line -> line.unitPrice().multiply(BigDecimal.valueOf(line.quantity())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            inventory.receivePurchase(entry.getKey(), destination.getId(), destination.getName(),
                    quantity, value, order.getOrderNumber(), actor);
        }
        for (PurchaseReceipts.ReceiptLine line : received) {
            if (line.lineId() != null) byId.get(line.lineId()).receive(line.quantity());
        }
        order.receive(Math.toIntExact(total));
        return response(orders.save(order));
    }

    private String receiptSignature(ReceivePurchaseRequest request) {
        if (request.lines() == null) return "legacy:" + request.quantity();
        HashSet<UUID> ids = new HashSet<>();
        for (ReceivePurchaseLine line : request.lines()) {
            if (line == null || line.lineId() == null || line.quantity() <= 0 || !ids.add(line.lineId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Neplatný nebo opakovaný řádek příjmu.");
            }
        }
        return request.lines().stream().sorted(Comparator.comparing(ReceivePurchaseLine::lineId))
                .map(line -> line.lineId() + ":" + line.quantity()).collect(Collectors.joining(","));
    }

    private static boolean validPrice(BigDecimal price) {
        return price != null && price.signum() >= 0 && price.compareTo(new BigDecimal("10000000000")) < 0
                && price.stripTrailingZeros().scale() <= 2;
    }

    private void validateProducts(List<PurchaseOrderLineRequest> lines) {
        for (PurchaseOrderLineRequest line : lines) {
            if (!products.existsById(line.productId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Product was not found.");
            }
        }
    }

    private static void validateTotal(BigDecimal total) {
        if (!validPrice(total)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Celková částka objednávky je příliš vysoká.");
        }
    }

    private static int totalQuantity(List<PurchaseOrderLineRequest> lines) {
        long total = lines.stream().mapToLong(PurchaseOrderLineRequest::quantity).sum();
        if (total > Integer.MAX_VALUE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Celkové množství objednávky je příliš vysoké.");
        }
        return Math.toIntExact(total);
    }

        private Warehouse warehouse(UUID id, WarehouseOwnerType ownerType) {
                Warehouse warehouse = warehouses.findById(id)
                                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Warehouse was not found."));
                if (warehouse.getOwnerType() != ownerType || !warehouse.isActive()) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Warehouse has an invalid owner type.");
                }
                return warehouse;
        }

        private Warehouse supplierWarehouse(UUID id, UUID supplierId) {
                Warehouse source = warehouse(id, WarehouseOwnerType.SUPPLIER);
                if (!supplierId.equals(source.getSupplierId())) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                        "Dodavatelský sklad nepatří vybranému dodavateli.");
                }
                return source;
        }

        private Supplier supplier(UUID id) {
                return suppliers.findById(id)
                                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                                "Dodavatel nebyl nalezen."));
        }

    public record PurchaseOverview(BigDecimal requestedValue, BigDecimal orderedValue, long requestedCount,
            List<PurchaseOrderResponse> orders) { }

    public record CreatePurchaseOrderRequest(UUID supplierId, LocalDate requestedOn,
            LocalDate expectedDeliveryDate, UUID sourceWarehouseId, UUID destinationWarehouseId,
            List<PurchaseOrderLineRequest> lines) { }

    public record PurchaseOrderLineRequest(UUID productId, int quantity, BigDecimal unitPrice) { }

    public record ReceivePurchaseRequest(Integer quantity, UUID receiptId, List<ReceivePurchaseLine> lines) { }

    public record ReceivePurchaseLine(UUID lineId, int quantity) { }

    public record WarehouseResponse(UUID id, String name, WarehouseOwnerType ownerType, UUID supplierId) { }

    public record SupplierResponse(UUID id, String name) { }

    public record PurchaseOrderResponse(UUID id, String orderNumber, String supplierName, UUID supplierId, LocalDate requestedOn,
                        LocalDate expectedDeliveryDate, BigDecimal totalAmount, PurchaseOrderStatus status,
                        UUID sourceWarehouseId, UUID destinationWarehouseId, UUID productId, Integer quantity, int receivedQuantity,
                        List<PurchaseOrderLineResponse> lines, List<PurchaseReceipts.Receipt> receipts) {
        static PurchaseOrderResponse from(PurchaseOrder order, List<PurchaseOrderLineResponse> lines,
                List<PurchaseReceipts.Receipt> receipts) {
            return new PurchaseOrderResponse(order.getId(), order.getOrderNumber(), order.getSupplierName(),
                                        order.getSupplierId(),
                                        order.getRequestedOn(), order.getExpectedDeliveryDate(), order.getTotalAmount(), order.getStatus(),
                                        order.getSourceWarehouseId(), order.getDestinationWarehouseId(), order.getProductId(), order.getQuantity(), order.getReceivedQuantity(), lines, receipts);
        }
    }

    public record PurchaseOrderLineResponse(UUID id, UUID productId, int quantity, BigDecimal unitPrice, int receivedQuantity) { }

    private PurchaseOrderResponse response(PurchaseOrder order) {
        List<PurchaseOrderLine> storedLines = lines.findAllByPurchaseOrderIdOrderById(order.getId());
        List<PurchaseOrderLineResponse> orderLines = storedLines.isEmpty()
                        ? order.getProductId() == null || order.getQuantity() == null ? List.of()
                                : List.of(new PurchaseOrderLineResponse(null, order.getProductId(), order.getQuantity(),
                                        order.getTotalAmount().divide(BigDecimal.valueOf(order.getQuantity()), 2, RoundingMode.HALF_UP),
                                        order.getReceivedQuantity()))
                        : storedLines.stream().map(line -> new PurchaseOrderLineResponse(line.getId(), line.getProductId(),
                                        line.getQuantity(), line.getUnitPrice(), line.getReceivedQuantity())).toList();
        return PurchaseOrderResponse.from(order, orderLines, receipts.forOrder(order.getId()));
    }
}