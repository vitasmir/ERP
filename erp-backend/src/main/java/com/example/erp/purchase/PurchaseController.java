package com.example.erp.purchase;

import java.math.BigDecimal;
import java.time.LocalDate;
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
        private final ProductRepository products;
        private final InventoryStockService inventory;

        public PurchaseController(PurchaseOrderRepository orders, PurchaseOrderLineRepository lines, WarehouseRepository warehouses,
                        ProductRepository products, InventoryStockService inventory) {
                this.orders = orders;
                this.lines = lines;
                this.warehouses = warehouses;
                this.products = products;
                this.inventory = inventory;
        }

        @GetMapping("/warehouses")
        public List<WarehouseResponse> warehouses() {
                return warehouses.findAllByActiveTrueOrderByOwnerTypeAscNameAsc().stream()
                                .map(warehouse -> new WarehouseResponse(warehouse.getId(), warehouse.getName(), warehouse.getOwnerType()))
                                .toList();
        }

    @GetMapping("/overview")
    public PurchaseOverview overview() {
        List<PurchaseOrderResponse> items = orders.findAllByOrderByExpectedDeliveryDateAsc().stream()
                .map(this::response).toList();
        BigDecimal requestedValue = items.stream().filter(item -> item.status() == PurchaseOrderStatus.REQUESTED)
                .map(PurchaseOrderResponse::totalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal orderedValue = items.stream().filter(item -> item.status() == PurchaseOrderStatus.ORDERED)
                .map(PurchaseOrderResponse::totalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new PurchaseOverview(requestedValue, orderedValue,
                items.stream().filter(item -> item.status() == PurchaseOrderStatus.REQUESTED).count(), items);
    }

        @PostMapping("/orders")
        @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.CREATED)
        @org.springframework.transaction.annotation.Transactional
        public PurchaseOrderResponse create(@RequestBody CreatePurchaseOrderRequest request) {
                if (request == null || request.supplierName() == null || request.supplierName().isBlank()
                                || request.requestedOn() == null || request.expectedDeliveryDate() == null
                                || request.expectedDeliveryDate().isBefore(request.requestedOn()) || request.sourceWarehouseId() == null
                                                || request.destinationWarehouseId() == null || request.lines() == null
                                                || request.lines().isEmpty()) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Purchase order contains invalid values.");
                }
                List<PurchaseOrderLineRequest> requestedLines = request.lines().stream()
                                .filter(line -> line != null && line.productId() != null && line.quantity() > 0
                                                && line.unitPrice() != null && line.unitPrice().signum() >= 0)
                                .toList();
                if (requestedLines.size() != request.lines().size()) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Purchase order contains invalid product lines.");
                }
                Warehouse source = warehouse(request.sourceWarehouseId(), WarehouseOwnerType.SUPPLIER);
                Warehouse destination = warehouse(request.destinationWarehouseId(), WarehouseOwnerType.COMPANY);
                Map<UUID, com.example.erp.catalog.Product> catalog = requestedLines.stream()
                                .map(line -> products.findById(line.productId()).orElseThrow(() ->
                                                new ResponseStatusException(HttpStatus.BAD_REQUEST, "Product was not found.")))
                                .collect(Collectors.toMap(com.example.erp.catalog.Product::getId, Function.identity(), (first, second) -> first));
                BigDecimal totalAmount = requestedLines.stream()
                                .map(line -> line.unitPrice().multiply(BigDecimal.valueOf(line.quantity())))
                                .reduce(BigDecimal.ZERO, BigDecimal::add);
                PurchaseOrderLineRequest firstLine = requestedLines.get(0);
                int totalQuantity = requestedLines.stream().mapToInt(PurchaseOrderLineRequest::quantity).sum();
                PurchaseOrder purchaseOrder = PurchaseOrder.create("POZ-" + LocalDate.now().getYear() + "-"
                                + UUID.randomUUID().toString().substring(0, 8).toUpperCase(), request.supplierName().trim(),
                                request.requestedOn(), request.expectedDeliveryDate(), totalAmount, source.getId(),
                                destination.getId(), firstLine.productId(), totalQuantity);
                PurchaseOrder saved = orders.save(purchaseOrder);
                lines.saveAll(requestedLines.stream()
                                .map(line -> PurchaseOrderLine.create(saved.getId(), line.productId(), line.quantity(), line.unitPrice()))
                                .toList());
                return response(saved, catalog);
        }

        @org.springframework.web.bind.annotation.PutMapping("/orders/{id}")
        @org.springframework.transaction.annotation.Transactional
        public PurchaseOrderResponse update(@PathVariable UUID id, @RequestBody CreatePurchaseOrderRequest request) {
                if (request == null || request.supplierName() == null || request.supplierName().isBlank()
                                || request.requestedOn() == null || request.expectedDeliveryDate() == null
                                || request.expectedDeliveryDate().isBefore(request.requestedOn())
                                || request.sourceWarehouseId() == null || request.destinationWarehouseId() == null
                                || request.lines() == null || request.lines().isEmpty()) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Purchase order contains invalid values.");
                }
                List<PurchaseOrderLineRequest> requestedLines = request.lines().stream()
                                .filter(line -> line != null && line.productId() != null && line.quantity() > 0
                                                && line.unitPrice() != null && line.unitPrice().signum() >= 0)
                                .toList();
                if (requestedLines.size() != request.lines().size()) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Purchase order contains invalid product lines.");
                }
                Warehouse source = warehouse(request.sourceWarehouseId(), WarehouseOwnerType.SUPPLIER);
                Warehouse destination = warehouse(request.destinationWarehouseId(), WarehouseOwnerType.COMPANY);
                Map<UUID, com.example.erp.catalog.Product> catalog = requestedLines.stream()
                                .map(line -> products.findById(line.productId()).orElseThrow(() ->
                                                new ResponseStatusException(HttpStatus.BAD_REQUEST, "Product was not found.")))
                                .collect(Collectors.toMap(com.example.erp.catalog.Product::getId, Function.identity(), (first, second) -> first));
                BigDecimal totalAmount = requestedLines.stream()
                                .map(line -> line.unitPrice().multiply(BigDecimal.valueOf(line.quantity())))
                                .reduce(BigDecimal.ZERO, BigDecimal::add);
                int totalQuantity = requestedLines.stream().mapToInt(PurchaseOrderLineRequest::quantity).sum();
                PurchaseOrder order = orders.findForUpdate(id)
                                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Purchase order was not found."));
                order.updateRequested(request.supplierName().trim(), request.requestedOn(), request.expectedDeliveryDate(),
                                totalAmount, source.getId(), destination.getId(), requestedLines.get(0).productId(), totalQuantity);
                lines.deleteAllByPurchaseOrderId(order.getId());
                lines.saveAll(requestedLines.stream()
                                .map(line -> PurchaseOrderLine.create(order.getId(), line.productId(), line.quantity(), line.unitPrice()))
                                .toList());
                return response(order, catalog);
        }

    @PatchMapping("/orders/{id}/order")
    @org.springframework.transaction.annotation.Transactional
    public PurchaseOrderResponse order(@PathVariable UUID id) {
        PurchaseOrder purchaseOrder = orders.findForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Purchase order was not found."));
        purchaseOrder.order();
        return response(orders.save(purchaseOrder));
    }

        @org.springframework.transaction.annotation.Transactional
        @PatchMapping("/orders/{id}/receive")
        public PurchaseOrderResponse receive(@PathVariable UUID id, @RequestBody ReceivePurchaseRequest request,
                        @RequestAttribute("erpUser") ErpUser actor) {
                PurchaseOrder order = orders.findForUpdate(id)
                                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Purchase order was not found."));
                List<PurchaseOrderLine> orderLines = lines.findAllByPurchaseOrderIdOrderById(order.getId());
                if (request == null || (request.quantity() != null && request.quantity() <= 0) || order.getProductId() == null
                                || order.getDestinationWarehouseId() == null) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Purchase order has no receivable stock line.");
                }
                Warehouse destination = warehouse(order.getDestinationWarehouseId(), WarehouseOwnerType.COMPANY);
                if (order.getStatus() != PurchaseOrderStatus.ORDERED || order.getQuantity() == null
                                || (orderLines.isEmpty() && request.quantity() == null)
                                || (orderLines.isEmpty() && request.quantity() > order.getQuantity() - order.getReceivedQuantity())) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "Neplatné množství nebo stav nákupní objednávky.");
                }
                if (orderLines.isEmpty()) {
                        inventory.receivePurchase(order.getProductId(), destination.getId(), destination.getName(),
                                        request.quantity(), order.getOrderNumber(), actor);
                        order.receive(request.quantity());
                } else {
                        int totalRemaining = 0;
                        for (PurchaseOrderLine line : orderLines) {
                                inventory.receivePurchase(line.getProductId(), destination.getId(), destination.getName(),
                                                line.remainingQuantity(), order.getOrderNumber(), actor);
                                totalRemaining += line.remainingQuantity();
                                line.receive(line.remainingQuantity());
                        }
                        order.receive(totalRemaining);
                }
                return response(orders.save(order));
        }

        private Warehouse warehouse(UUID id, WarehouseOwnerType ownerType) {
                Warehouse warehouse = warehouses.findById(id)
                                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Warehouse was not found."));
                if (warehouse.getOwnerType() != ownerType || !warehouse.isActive()) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Warehouse has an invalid owner type.");
                }
                return warehouse;
        }

    public record PurchaseOverview(BigDecimal requestedValue, BigDecimal orderedValue, long requestedCount,
            List<PurchaseOrderResponse> orders) { }

    public record CreatePurchaseOrderRequest(String supplierName, LocalDate requestedOn,
            LocalDate expectedDeliveryDate, UUID sourceWarehouseId, UUID destinationWarehouseId,
            List<PurchaseOrderLineRequest> lines) { }

    public record PurchaseOrderLineRequest(UUID productId, int quantity, BigDecimal unitPrice) { }

    public record ReceivePurchaseRequest(Integer quantity) { }

    public record WarehouseResponse(UUID id, String name, WarehouseOwnerType ownerType) { }

    public record PurchaseOrderResponse(UUID id, String orderNumber, String supplierName, LocalDate requestedOn,
                        LocalDate expectedDeliveryDate, BigDecimal totalAmount, PurchaseOrderStatus status,
                        UUID sourceWarehouseId, UUID destinationWarehouseId, UUID productId, Integer quantity, int receivedQuantity,
                        List<PurchaseOrderLineResponse> lines) {
        static PurchaseOrderResponse from(PurchaseOrder order, List<PurchaseOrderLineResponse> lines) {
            return new PurchaseOrderResponse(order.getId(), order.getOrderNumber(), order.getSupplierName(),
                                        order.getRequestedOn(), order.getExpectedDeliveryDate(), order.getTotalAmount(), order.getStatus(),
                                        order.getSourceWarehouseId(), order.getDestinationWarehouseId(), order.getProductId(), order.getQuantity(), order.getReceivedQuantity(), lines);
        }
    }

    public record PurchaseOrderLineResponse(UUID productId, int quantity, BigDecimal unitPrice, int receivedQuantity) { }

    private PurchaseOrderResponse response(PurchaseOrder order) {
        if (order.getProductId() == null) return response(order, Map.of());
        return response(order, products.findById(order.getProductId()).map(product ->
                        Map.of(product.getId(), product)).orElse(Map.of()));
    }

    private PurchaseOrderResponse response(PurchaseOrder order, Map<UUID, com.example.erp.catalog.Product> catalog) {
        List<PurchaseOrderLine> storedLines = lines.findAllByPurchaseOrderIdOrderById(order.getId());
        List<PurchaseOrderLineResponse> orderLines = storedLines.isEmpty()
                        ? order.getProductId() == null || order.getQuantity() == null ? List.of()
                                : List.of(new PurchaseOrderLineResponse(order.getProductId(), order.getQuantity(),
                                        catalog.get(order.getProductId()) == null ? BigDecimal.ZERO
                                                        : catalog.get(order.getProductId()).getPurchasePrice(),
                                        order.getReceivedQuantity()))
                        : storedLines.stream().map(line -> new PurchaseOrderLineResponse(line.getProductId(),
                                        line.getQuantity(), line.getUnitPrice(), line.getReceivedQuantity())).toList();
        return PurchaseOrderResponse.from(order, orderLines);
    }
}