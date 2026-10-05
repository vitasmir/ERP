package com.example.erp.purchase;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

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
        private final WarehouseRepository warehouses;
        private final ProductRepository products;
        private final InventoryStockService inventory;

        public PurchaseController(PurchaseOrderRepository orders, WarehouseRepository warehouses,
                        ProductRepository products, InventoryStockService inventory) {
                this.orders = orders;
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
                .map(PurchaseOrderResponse::from).toList();
        BigDecimal requestedValue = items.stream().filter(item -> item.status() == PurchaseOrderStatus.REQUESTED)
                .map(PurchaseOrderResponse::totalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal orderedValue = items.stream().filter(item -> item.status() == PurchaseOrderStatus.ORDERED)
                .map(PurchaseOrderResponse::totalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new PurchaseOverview(requestedValue, orderedValue,
                items.stream().filter(item -> item.status() == PurchaseOrderStatus.REQUESTED).count(), items);
    }

        @PostMapping("/orders")
        @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.CREATED)
        public PurchaseOrderResponse create(@RequestBody CreatePurchaseOrderRequest request) {
                if (request == null || request.supplierName() == null || request.supplierName().isBlank()
                                || request.requestedOn() == null || request.expectedDeliveryDate() == null
                                || request.expectedDeliveryDate().isBefore(request.requestedOn()) || request.totalAmount() == null
                                                || request.totalAmount().signum() < 0 || request.sourceWarehouseId() == null
                                                || request.destinationWarehouseId() == null || request.productId() == null
                                                || request.quantity() <= 0) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Purchase order contains invalid values.");
                }
                Warehouse source = warehouse(request.sourceWarehouseId(), WarehouseOwnerType.SUPPLIER);
                Warehouse destination = warehouse(request.destinationWarehouseId(), WarehouseOwnerType.COMPANY);
                products.findById(request.productId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Product was not found."));
                PurchaseOrder purchaseOrder = PurchaseOrder.create("POZ-" + LocalDate.now().getYear() + "-"
                                + UUID.randomUUID().toString().substring(0, 8).toUpperCase(), request.supplierName().trim(),
                                request.requestedOn(), request.expectedDeliveryDate(), request.totalAmount(), source.getId(),
                                destination.getId(), request.productId(), request.quantity());
                return PurchaseOrderResponse.from(orders.save(purchaseOrder));
        }

    @PatchMapping("/orders/{id}/order")
    @org.springframework.transaction.annotation.Transactional
    public PurchaseOrderResponse order(@PathVariable UUID id) {
        PurchaseOrder purchaseOrder = orders.findForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Purchase order was not found."));
        purchaseOrder.order();
        return PurchaseOrderResponse.from(orders.save(purchaseOrder));
    }

        @org.springframework.transaction.annotation.Transactional
        @PatchMapping("/orders/{id}/receive")
        public PurchaseOrderResponse receive(@PathVariable UUID id, @RequestBody ReceivePurchaseRequest request,
                        @RequestAttribute("erpUser") ErpUser actor) {
                PurchaseOrder order = orders.findForUpdate(id)
                                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Purchase order was not found."));
                if (request == null || request.quantity() <= 0 || order.getProductId() == null
                                || order.getDestinationWarehouseId() == null) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Purchase order has no receivable stock line.");
                }
                Warehouse destination = warehouse(order.getDestinationWarehouseId(), WarehouseOwnerType.COMPANY);
                if (order.getStatus() != PurchaseOrderStatus.ORDERED || order.getQuantity() == null
                                || request.quantity() > order.getQuantity() - order.getReceivedQuantity()) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "Neplatné množství nebo stav nákupní objednávky.");
                }
                inventory.receivePurchase(order.getProductId(), destination.getId(), destination.getName(),
                                request.quantity(), order.getOrderNumber(), actor);
                order.receive(request.quantity());
                return PurchaseOrderResponse.from(orders.save(order));
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
            LocalDate expectedDeliveryDate, BigDecimal totalAmount, UUID sourceWarehouseId,
            UUID destinationWarehouseId, UUID productId, int quantity) { }

    public record ReceivePurchaseRequest(int quantity) { }

    public record WarehouseResponse(UUID id, String name, WarehouseOwnerType ownerType) { }

    public record PurchaseOrderResponse(UUID id, String orderNumber, String supplierName, LocalDate requestedOn,
                        LocalDate expectedDeliveryDate, BigDecimal totalAmount, PurchaseOrderStatus status,
                        UUID sourceWarehouseId, UUID destinationWarehouseId, UUID productId, Integer quantity, int receivedQuantity) {
        static PurchaseOrderResponse from(PurchaseOrder order) {
            return new PurchaseOrderResponse(order.getId(), order.getOrderNumber(), order.getSupplierName(),
                                        order.getRequestedOn(), order.getExpectedDeliveryDate(), order.getTotalAmount(), order.getStatus(),
                                        order.getSourceWarehouseId(), order.getDestinationWarehouseId(), order.getProductId(), order.getQuantity(), order.getReceivedQuantity());
        }
    }
}