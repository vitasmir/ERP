package com.example.erp.manufacturing;

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
@RequestMapping("/api/v1/manufacturing")
public class ManufacturingController {
    private final ManufacturingOrderRepository orders;

    public ManufacturingController(ManufacturingOrderRepository orders) { this.orders = orders; }

    @GetMapping("/overview")
    public ManufacturingOverview overview() {
        List<ManufacturingOrderResponse> items = orders.findAllByOrderByPlannedDateAsc().stream()
                .map(ManufacturingOrderResponse::from).toList();
        int plannedQuantity = items.stream().filter(item -> item.status() != ManufacturingOrderStatus.COMPLETED)
                .mapToInt(ManufacturingOrderResponse::plannedQuantity).sum();
        int completedQuantity = items.stream().mapToInt(ManufacturingOrderResponse::completedQuantity).sum();
        long activeOrderCount = items.stream().filter(item -> item.status() == ManufacturingOrderStatus.IN_PROGRESS).count();
        return new ManufacturingOverview(plannedQuantity, completedQuantity, activeOrderCount, items);
    }

    @PatchMapping("/orders/{id}/complete")
    public ManufacturingOrderResponse complete(@PathVariable UUID id) {
        ManufacturingOrder order = orders.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Manufacturing order was not found."));
        order.complete();
        return ManufacturingOrderResponse.from(orders.save(order));
    }

    public record ManufacturingOverview(int plannedQuantity, int completedQuantity, long activeOrderCount,
            List<ManufacturingOrderResponse> orders) { }

    public record ManufacturingOrderResponse(UUID id, String orderNumber, String productName, String workCenter,
            int plannedQuantity, int completedQuantity, LocalDate plannedDate, ManufacturingOrderStatus status) {
        static ManufacturingOrderResponse from(ManufacturingOrder order) {
            return new ManufacturingOrderResponse(order.getId(), order.getOrderNumber(), order.getProductName(),
                    order.getWorkCenter(), order.getPlannedQuantity(), order.getCompletedQuantity(),
                    order.getPlannedDate(), order.getStatus());
        }
    }
}