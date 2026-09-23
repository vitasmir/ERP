package com.example.erp.purchase;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/purchase")
public class PurchaseController {
    private final PurchaseOrderRepository orders;

    public PurchaseController(PurchaseOrderRepository orders) { this.orders = orders; }

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

    @PatchMapping("/orders/{id}/order")
    public PurchaseOrderResponse order(@PathVariable UUID id) {
        PurchaseOrder purchaseOrder = orders.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Purchase order was not found."));
        purchaseOrder.order();
        return PurchaseOrderResponse.from(orders.save(purchaseOrder));
    }

    public record PurchaseOverview(BigDecimal requestedValue, BigDecimal orderedValue, long requestedCount,
            List<PurchaseOrderResponse> orders) { }

    public record PurchaseOrderResponse(UUID id, String orderNumber, String supplierName, LocalDate requestedOn,
            LocalDate expectedDeliveryDate, BigDecimal totalAmount, PurchaseOrderStatus status) {
        static PurchaseOrderResponse from(PurchaseOrder order) {
            return new PurchaseOrderResponse(order.getId(), order.getOrderNumber(), order.getSupplierName(),
                    order.getRequestedOn(), order.getExpectedDeliveryDate(), order.getTotalAmount(), order.getStatus());
        }
    }
}