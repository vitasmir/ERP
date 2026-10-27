package com.example.erp.sales;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

        @PostMapping("/orders")
        @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.CREATED)
        public SalesOrderResponse create(@RequestBody CreateSalesOrderRequest request) {
                if (request == null || request.orderNumber() == null || request.orderNumber().isBlank()
                                || request.customerName() == null || request.customerName().isBlank()
                                || request.orderDate() == null || request.deliveryDate() == null
                                || request.deliveryDate().isBefore(request.orderDate()) || request.totalAmount() == null
                                || request.totalAmount().signum() < 0) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sales order contains invalid values.");
                }
                SalesOrder order = SalesOrder.create(request.orderNumber().trim(), request.customerName().trim(),
                                request.orderDate(), request.deliveryDate(), request.totalAmount());
                return SalesOrderResponse.from(orders.save(order));
        }

    @PatchMapping("/orders/{id}/confirm")
    public SalesOrderResponse confirm(@PathVariable UUID id) {
        SalesOrder order = orders.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sales order was not found."));
        order.confirm();
        return SalesOrderResponse.from(orders.save(order));
    }

        @PutMapping("/orders/{id}")
        public SalesOrderResponse update(@PathVariable UUID id, @RequestBody SalesOrderUpdateRequest request) {
                SalesOrder order = find(id);
                order.update(request.orderNumber(), request.customerName(), request.orderDate(), request.deliveryDate(),
                                request.totalAmount());
                return SalesOrderResponse.from(orders.save(order));
        }

        @DeleteMapping("/orders/{id}")
        public void delete(@PathVariable UUID id) {
                SalesOrder order = find(id);
                try {
                        orders.delete(order);
                        orders.flush();
                } catch (RuntimeException exception) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT,
                                        "Objednávku nelze smazat, protože je navázaná na účetní doklad.", exception);
                }
        }

        private SalesOrder find(UUID id) {
                return orders.findById(id)
                                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sales order was not found."));
        }

    public record SalesOverview(BigDecimal quoteValue, BigDecimal confirmedValue, long quoteCount,
            List<SalesOrderResponse> orders) { }

    public record SalesOrderUpdateRequest(String orderNumber, String customerName, LocalDate orderDate,
            LocalDate deliveryDate, BigDecimal totalAmount) { }

    public record CreateSalesOrderRequest(String orderNumber, String customerName, LocalDate orderDate,
            LocalDate deliveryDate, BigDecimal totalAmount) { }

    public record SalesOrderResponse(UUID id, String orderNumber, String customerName, LocalDate orderDate,
            LocalDate deliveryDate, BigDecimal totalAmount, SalesOrderStatus status) {
        static SalesOrderResponse from(SalesOrder order) {
            return new SalesOrderResponse(order.getId(), order.getOrderNumber(), order.getCustomerName(),
                    order.getOrderDate(), order.getDeliveryDate(), order.getTotalAmount(), order.getStatus());
        }
    }
}