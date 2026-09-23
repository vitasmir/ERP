package com.example.erp.sales;

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
@RequestMapping("/api/v1/sales")
public class SalesController {
    private final SalesOrderRepository orders;

    public SalesController(SalesOrderRepository orders) { this.orders = orders; }

    @GetMapping("/overview")
    public SalesOverview overview() {
        List<SalesOrderResponse> items = orders.findAllByOrderByDeliveryDateAsc().stream()
                .map(SalesOrderResponse::from).toList();
        BigDecimal quoteValue = items.stream().filter(item -> item.status() == SalesOrderStatus.QUOTE)
                .map(SalesOrderResponse::totalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal confirmedValue = items.stream().filter(item -> item.status() == SalesOrderStatus.CONFIRMED)
                .map(SalesOrderResponse::totalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new SalesOverview(quoteValue, confirmedValue,
                items.stream().filter(item -> item.status() == SalesOrderStatus.QUOTE).count(), items);
    }

    @PatchMapping("/orders/{id}/confirm")
    public SalesOrderResponse confirm(@PathVariable UUID id) {
        SalesOrder order = orders.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sales order was not found."));
        order.confirm();
        return SalesOrderResponse.from(orders.save(order));
    }

    public record SalesOverview(BigDecimal quoteValue, BigDecimal confirmedValue, long quoteCount,
            List<SalesOrderResponse> orders) { }

    public record SalesOrderResponse(UUID id, String orderNumber, String customerName, LocalDate orderDate,
            LocalDate deliveryDate, BigDecimal totalAmount, SalesOrderStatus status) {
        static SalesOrderResponse from(SalesOrder order) {
            return new SalesOrderResponse(order.getId(), order.getOrderNumber(), order.getCustomerName(),
                    order.getOrderDate(), order.getDeliveryDate(), order.getTotalAmount(), order.getStatus());
        }
    }
}